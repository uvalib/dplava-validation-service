package org.dplava.git;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * Created by md5wz on 3/7/18.
 */
public class RepositoryCommitValidatorTest {

    @Test
    public void testValidRepository() throws GitAPIException, IOException, InterruptedException {
        // set up the repository with one valid file
        final File gitDir = new File("target/" + UUID.randomUUID().toString());
        URI gitUrl = gitDir.toURI();
        Git git = Git.init().setDirectory(gitDir).call();
        FileUtils.copyFile(new File("src/test/resources/sample-valid.xml"), new File(gitDir, "sample.xml"));
        git.add().addFilepattern(".").call();
        RevCommit c = git.commit().setAuthor("test", "test@fake.fake")
                .setMessage("Initial commit")
                .setCommitter("committer", "committer@fake.fake").call();

        InMemoryReportPersistence reports = new InMemoryReportPersistence();
        RepositoryCommitValidator v = new RepositoryCommitValidator(1, reports);
        MockValidityRegistry r = new MockValidityRegistry();
        v.queueForValidation(gitUrl, c.getName(), r);
        v.waitFor(gitUrl, c.getName());
        assertEquals("success", r.getCommitStatus(gitUrl, c.getName()));

        // add an invalid file
        FileUtils.copyFile(new File("src/test/resources/sample-missing-title.xml"), new File(gitDir, "sample1.xml"));
        git.add().addFilepattern(".").call();
        c = git.commit().setAuthor("test", "test@fake.fake")
                .setMessage("Added invalid file")
                .setCommitter("committer", "committer@fake.fake").call();
        v.queueForValidation(gitUrl, c.getName(), r);
        v.waitFor(gitUrl, c.getName());
        assertEquals("failure", r.getCommitStatus(gitUrl, c.getName()));
        assertEquals("Error: sample1.xml - At least one title element is required.", reports.getFailureReport(gitUrl, c.getName()));

        // now test the ability to skip dozens of irrelevant files

        //test unique IDs
        FileUtils.copyFile(new File("src/test/resources/sample-valid.xml"), new File(gitDir, "sample1.xml"));
        git.add().addFilepattern(".").call();
        c = git.commit().setAuthor("test", "test@fake.fake")
                .setMessage("Added files with duplicate IDs")
                .setCommitter("committer", "committer@fake.fake").call();
        v.queueForValidation(gitUrl, c.getName(), r);
        v.waitFor(gitUrl, c.getName());
        assertEquals("Error: Files \"sample.xml\" and \"sample1.xml\" have the same id.", reports.getFailureReport(gitUrl, c.getName()));
    }

    private static class InMemoryReportPersistence implements ReportPersistence {

        Map<URI, Map<String, String>> reports = new HashMap<URI, Map<String, String>>();

        @Override
        public boolean isReportStorageAccessible() {
            return true;
        }

        @Override
        public String writeFailureReport(URI repo, String commit, String report) throws IOException {
            Map<String, String> m = reports.get(repo);
            if (m == null) {
                m = new HashMap<String, String>();
                reports.put(repo, m);
            }
            m.put(commit, report);
            return null;
        }

        public String getFailureReport(URI repo, String commit) {
            try {
                return reports.get(repo).get(commit);
            } catch (NullPointerException e) {
                return null;
            }
        }
    }

    private static class MockValidityRegistry implements ValidityRegistry {


        private Map<String, String> hashToStatusMap = new HashMap<String, String>();

        @Override
        public String getCommitStatus(URI repo, String commitHash) {
            return hashToStatusMap.get(commitHash);
        }

        @Override
        public void reportCommitInvalid(URI repo, String commitHash, String url) {
            hashToStatusMap.put(commitHash, "failure");
        }

        @Override
        public void reportCommitValid(URI repo, String commitHash) {
            hashToStatusMap.put(commitHash, "success");
        }

        @Override
        public void reportCommitPending(URI repo, String commitHash) {
            hashToStatusMap.put(commitHash, "pending");
        }

        @Override
        public void reportSystemError(URI repo, String commitHash) {
            throw new RuntimeException("Error reported for " + repo + " commit " + commitHash);
        }
    }
}
