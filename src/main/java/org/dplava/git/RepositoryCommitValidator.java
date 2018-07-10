package org.dplava.git;

import org.apache.commons.io.FileUtils;
import org.dplava.validation.DPLAVAMetadataValidator;
import org.dplava.validation.ErrorAggregator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Encapsulates logic to perform validation on the necessary files for commits
 * to a single github repository.
 *
 * This class is thread-safe
 */
public class RepositoryCommitValidator {

    /**
     * Synchronize on this in order to access either queuedCommits or runningCommits.
     */
    private Queue<CommitValidator> queuedCommits;

    private List<CommitValidator> runningCommits;

    private int maxWorkerCount;

    private List<Worker> workers;

    private ReportPersistence reports;
    
    private DocumentBuilder builder;

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryCommitValidator.class);

    public RepositoryCommitValidator(final int maxWorkerCount, final ReportPersistence reports) {
        queuedCommits = new LinkedList<>();
        runningCommits = new ArrayList<>();
        workers = new ArrayList<>();
        this.maxWorkerCount = maxWorkerCount;
        if (maxWorkerCount < 1) {
            throw new IllegalArgumentException("maxWorkerCount must be greater than 0");
        }
        this.reports = reports;
        
        try {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Error instantiating DocumentBuilder.");
        }
    }



    /**
     * Asynchronously validates an individual commit and then runs the passed ResultReporter with the
     * result of the validation operation.
     * @param repo the URI for a given repository
     * @param commitHash the sha1 of the commit to validate
     * @param registry the ValidityRegistry to receive notifications about the validity status of the commit
     */
    public void queueForValidation(GithubPayload payload, final ValidityRegistry registry) throws IOException {
        LOGGER.trace(payload.getCommitHash() + " " + payload.getRepository());
        final CommitValidator v = new CommitValidator(payload, registry);
        synchronized (queuedCommits) {
            queuedCommits.add(v);
        }
        considerAddingWorker();
    }

    private void considerAddingWorker() {
        synchronized (workers) {
            if (workers.size() < maxWorkerCount) {
                Worker w = new Worker();
                workers.add(w);
                w.start();
            }
        }
    }

    public void waitFor(final URI repo, final String commitHash) throws InterruptedException {
        while (isValidatingCommit(repo, commitHash)) {
            Thread.sleep(100);
        }
    }

    private boolean isValidatingCommit(URI repo, String commitHash) {
        synchronized (queuedCommits) {
            return Stream.concat(queuedCommits.stream(), runningCommits.stream()).anyMatch(v -> {
                return v.payload.getRepository().equals(repo) && v.payload.getCommitHash().equals(commitHash);
            });
        }
    }

    private class Worker extends Thread {

        public void run() {
            LOGGER.info("Worker started (" + Thread.currentThread().getName() + ")");
            for (CommitValidator v = takeWork() ; v != null ; v = takeWork()) {
                v.run();
                synchronized (queuedCommits) {
                   runningCommits.remove(v);
                }
            }
            synchronized(workers) {
                workers.remove(this);
            }

            LOGGER.info("Worker stopped (" + Thread.currentThread().getName() + ")");
        }

        private CommitValidator takeWork() {
            synchronized (queuedCommits) {
                final CommitValidator v =  queuedCommits.poll();
                if (v != null)
                    runningCommits.add(v);
                return v;
            }
        }

    }

    public class CommitValidator implements Runnable {

        private ValidityRegistry registry;
        
        private GithubPayload payload;

        public CommitValidator(GithubPayload payload, ValidityRegistry registry) throws IOException {
            this.payload = payload;
            this.registry = registry;
            registry.reportCommitPending(payload);
        }

        @Override
        public void run() {
            File gitDir = null;
            try {
                // clone the repo
                long start = System.currentTimeMillis();
                gitDir = File.createTempFile(URLEncoder.encode(payload.getRepository().getPath(), "UTF-8"), "cloned-git-repo");
                gitDir.delete();
                Git git = Git.cloneRepository()
                        .setURI(payload.getRepository().toString())
                        .setDirectory(gitDir)
                        .call();
                LOGGER.debug("Cloned " + payload.getRepository().toString() + " in " + timeSince(start) + ".");

                // check out the commit
                git.checkout().setName(payload.getCommitHash()).call();

                // determine the last valid commit
                Iterator<RevCommit> revisions = git.log().call().iterator();
                final RevCommit current = revisions.next();
                RevCommit previous = null;
                while (revisions.hasNext()) {
                    previous = revisions.next();
                    final String previousCommitStatus = registry.getCommitStatus(payload);
                    if (previousCommitStatus != null && previousCommitStatus.equals(ValidityRegistry.SUCCESS)) {
                        break;
                    } else {
                        previous = null;
                    }
                }

                final ErrorAggregator errors = new ErrorAggregator();

                if (previous == null) {
                    // no valid commit in the history: validate every file (YUCK!)
                    start = System.currentTimeMillis();
                    final long count = validateFileFromGitDir(gitDir, new DPLAVAMetadataValidator(), errors);
                    LOGGER.debug("Validated every XML file (" + count + ") in " + timeSince(start) + ".");
                } else {
                    start = System.currentTimeMillis();
                    // if previous valid commit was found, just validate the changes
                    DPLAVAMetadataValidator v = new DPLAVAMetadataValidator();

                    RevWalk rw = new RevWalk(git.getRepository());
                    DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
                    df.setRepository(git.getRepository());
                    df.setDiffComparator(RawTextComparator.DEFAULT);
                    df.setDetectRenames(true);
                    long count = 0;
                    List<DiffEntry> diffs = df.scan(previous.getTree(), current.getTree());
                    for (DiffEntry diff : diffs) {
                        if (diff.getChangeType().equals(DiffEntry.ChangeType.ADD) || diff.getChangeType().equals(DiffEntry.ChangeType.MODIFY)) {
                            final File file = new File(gitDir, diff.getNewPath());
                            LOGGER.trace("Validating " + file.getAbsolutePath());
                            v.validateFile(file, errors);
                            count ++;
                        }
                    }
                    LOGGER.debug("Validated changed XML files (" + count + ") since last valid commit in " + timeSince(start) + ".");
                }

                //make sure all files have unique IDs
                if (errors.isValid()) {
                    start = System.currentTimeMillis();
                    checkIdentifiers(new HashMap<String, String>(), null, gitDir, errors);
                    LOGGER.debug("Checked XML files for duplicate IDs in " + timeSince(start) + ".");
                }
                
                if (errors.isValid()) {
                    registry.reportCommitValid(payload);

                } else {
                    final String reportUrl = reports.writeFailureReport(payload, errors.getErrors());
                    registry.reportCommitInvalid(payload, reportUrl);
                }
                return;

            } catch (Throwable t) {
                try {
                    LOGGER.error("Error validating xml files!", t);
                    registry.reportSystemError(payload);
                } catch (IOException e) {
                    LOGGER.error("Unable to post error to validator!", e);

                }
                LOGGER.error("Unexpected error validating " + payload.getRepository().getPath() + " commit " + payload.getCommitHash(), t);
            } finally {
                try {
                    FileUtils.deleteDirectory(gitDir);
                } catch (IOException e) {
                    LOGGER.warn("Unable to delete temporary git directory: " + gitDir.getAbsolutePath());
                }
            }
        }

        private long validateFileFromGitDir(File file, DPLAVAMetadataValidator v, ErrorAggregator errors) {
            if (file.isHidden()) {
                return 0;
            }
            if (file.getName().equalsIgnoreCase("readme.md")) {
                return 0;
            }
            if (file.isDirectory()) {
                long count = 0;
                for (File f : file.listFiles()) {
                    count += validateFileFromGitDir(f, v, errors);
                }
                return count;
            } else {
                try {
                    LOGGER.trace("Validating " + file.getName());
                    v.validateFile(file, errors);
                    return 1;
                } catch (Throwable t) {
                    errors.error("System Error (" + t.getMessage() == null ? t.getClass().getName() : t.getLocalizedMessage() + ")");
                    return 1;
                }
            }
        }
    }

    /**
     * Pretty prints the time since the given time (indicated in ms since epoc).
     */
    public static String timeSince(final long time) {
        final long interval = System.currentTimeMillis() - time;
        if (interval > 60 * 60 * 1000 * 1.5) {
            return "about " + (interval / 3600000) + " hours";
        } else if (interval > 90000) {
            return "about " + (interval / 60000) + " minutes";
        } else if (interval > 1500) {
            return "about " + (interval / 1000) + " seconds";
        } else {
            return interval + " ms";
        }
    }

    /**
     * Checks the dcterms:identifier of every xml file for duplicates, adding entries
     * to the ErrorAggregator if found.
     */
    private void checkIdentifiers(HashMap<String, String> ids, Document doc, File directory, ErrorAggregator errors) {
        try {
            LOGGER.trace("Checking " + directory.getName() + " for unique ID.");
            for (File file : directory.listFiles()) {
                if (file.isDirectory() && !file.getName().startsWith(".")) {
                    checkIdentifiers(ids, doc, file, errors);
                } else if (file.getName().endsWith(".xml")) {
                    doc = builder.parse(file);
                    doc.getDocumentElement().normalize();
                    
                    String id = doc.getElementsByTagName("dcterms:identifier").item(0).getTextContent();
                    if (ids.get(id) == null)
                        ids.put(id, file.getName());
                    else
                        errors.error("Files \"" + ids.get(id) + "\" and \"" + file.getName() + "\" have the same id.");
                }
            }
        } catch (SAXException | IOException e) {
            errors.error("Unable to parse " + directory.getName());
        }
    }
}
