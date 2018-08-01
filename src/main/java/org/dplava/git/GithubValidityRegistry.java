package org.dplava.git;

import org.apache.commons.io.IOUtils;
import org.apache.commons.mail.*;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Interacts with github using the github API to store custom
 * status values for commits.
 */
public class GithubValidityRegistry implements ValidityRegistry, ReportPersistence {

    public static final String CONTEXT = "validation/dplava";

    private static final String BASE_URL = "https://api.github.com";

    private static final String BRANCH = "dpla-harvest";

    private String owner = "dplava";

    /**
     * This flag indicates that an unauthorized response has been received.  To avoid
     * making repeated requests, and potentically causing the account to get locked
     * out, methods that make requests requiring authentication will fail while this
     * is set to true (requiring an application restart).
     */
    private boolean unauthorized = false;

    private static final Logger LOGGER = LoggerFactory.getLogger(GithubValidityRegistry.class);

    public boolean checkAuthentication() throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpHead head = new HttpHead(BASE_URL + "/user");
            head.setHeader("Accept", "application/vnd.github.v3+json");
            try (CloseableHttpResponse response = client.execute(head, basicAuth(BASE_URL))) {
                return isValidStatus(response.getStatusLine().getStatusCode());
            }
        }
    }

    @Override
    public String getCommitStatus(URI repo, String commitHash) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(BASE_URL + "/repos" + repo.getPath() + "/commits/" + commitHash + "/statuses");
            get.setHeader("Accept", "application/vnd.github.v3+json");
            try (CloseableHttpResponse response = client.execute(get)) {
                JsonArray a = Json.createReader(response.getEntity().getContent()).readArray();
                for (int i = 0; i < a.size(); i ++) {
                    JsonObject status = (JsonObject) a.get(i);
                    final String state = status.getString("state");
                    final String context = status.getString("context");
                    if (context.equals(CONTEXT)) {
                        return state;
                    }
                }
            }
            // TODO: paging might be necessary in some contexts, but not in our initial design, assuming github will
            // always return a single status on a single page
        }
        return null;
    }

    public static String getSMTPHostname() {
        return System.getenv("SMTP_HOST");
    }

    public static int getSMTPPort() {
        final String portString = System.getenv("SMTP_PORT");
        if (portString == null) {
            return 25;
        }
        try {
            return Integer.parseInt(portString);
        } catch (NumberFormatException e) {
            LOGGER.debug("Unparsible SMTP_PORT value of \"" + portString + "\" defaulted to 25.");
            return 25;
        }
    }

    public static String getContactInfo() {
        final String contactInfo = System.getenv("CONTACT_INFO");
        if (contactInfo == null) {
            return "your DPLAVA metadata or technical liaison";
        } else {
            return contactInfo;
        }
    }

    @Override
    public void reportCommitInvalid(GithubPayload payload, String url) throws IOException {
        postStatus(payload, FAILURE, url);
        
        //notify pusher via email
        try {
            Email email = new SimpleEmail();
            email.setHostName(getSMTPHostname());
            email.setSmtpPort(getSMTPPort());
            email.setFrom(payload.getErrorEmail());
            email.setSubject("DPLAVA Metadata Validation Report");
            
            String message = "This is an automatic message to inform you that the most recent batch "
                    + "of records you provided for inclusion in DPLA through the git-based submission "
                    + "process did not pass all of the metadata checks.\n\nUntil the issues have been "
                    + "addressed and a subsequent submission (push) has been made, only the last "
                    + "valid set of records will be included in DPLA.\n\nA full report of the failed "
                    + "metadata checks is available at " + url + ".\n\nPlease contact "
                    + getContactInfo() + " if you have further questions.";
            email.setMsg(message);
            email.addTo(payload.getEmail());
            email.send();
            LOGGER.info("Sent email about invalid records to " + payload.getEmail());
        } catch (EmailException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void reportCommitValid(GithubPayload payload) throws IOException {
        postStatus(payload, SUCCESS, null);
        mergeToHarvestBranch(payload);
    }

    @Override
    public void reportCommitPending(GithubPayload payload) throws IOException {
        postStatus(payload, PENDING, null);
    }

    @Override
    public void reportSystemError(GithubPayload payload) throws IOException {
        postStatus(payload, ERROR, null);
    }

    private void postStatus(GithubPayload payload, final String state, final String url) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            final String posturl = BASE_URL + "/repos" + payload.getRepository().getPath() + "/statuses/" + payload.getCommitHash();
            LOGGER.debug("Posting status \"" + state + "\", to " + posturl);
            HttpPost post = new HttpPost(posturl);
            JsonObjectBuilder statusJson = Json.createObjectBuilder().add("state", state).add("context", CONTEXT);
            if (url != null) {
                statusJson.add("target_url", url);
            }
            post.setEntity(new StringEntity(statusJson.build().toString()));
            try (CloseableHttpResponse response = client.execute(post, basicAuth(posturl))) {
                if (!isValidStatus(response.getStatusLine().getStatusCode())) {
                    throw new RuntimeException("Unexpected status code! " + response.getStatusLine().getStatusCode());
                }
            }
        }
    }


    private void mergeToHarvestBranch(GithubPayload payload) throws IOException {
        LOGGER.debug("Checking for " + BRANCH + " branch...");
        if (checkRef(payload.getRepository())) {
            LOGGER.debug("Replacing ref " + BRANCH + " with ref to commit " + payload.getCommitHash() + ".");
            updateRef(payload);
        } else {
            LOGGER.debug("Creating ref " + BRANCH + " with ref to commit " + payload.getCommitHash() + ".");
            createRef(payload);
        }
    }

    private boolean checkRef(URI repo) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            final String headurl = BASE_URL + "/repos" + repo.getPath() + "/git/refs/heads/" + BRANCH;
            final HttpHead head = new HttpHead(headurl);
            try (CloseableHttpResponse response = client.execute(head, basicAuth(headurl))) {
                final int code = response.getStatusLine().getStatusCode();
                if (code == 404) {
                    return false;
                } else if (isValidStatus(response.getStatusLine().getStatusCode())) {
                    return true;
                } else {
                    logUnexpectedResponse(head, response);
                    return false;
                }
            }
        }
    }

    private void updateRef(GithubPayload payload) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            final String patchurl = BASE_URL + "/repos" + payload.getRepository().getPath() + "/git/refs/heads/" + BRANCH;
            final HttpPatch updateRefPatch = new HttpPatch(patchurl);
            updateRefPatch.setEntity(new StringEntity(Json.createObjectBuilder().add("sha", payload.getCommitHash()).add("force", true).build().toString()));
            try (CloseableHttpResponse response = client.execute(updateRefPatch, basicAuth(patchurl))) {
                if (!isValidStatus(response.getStatusLine().getStatusCode())) {
                    logUnexpectedResponse(updateRefPatch, response);
                }
            }
        }
    }

    private void createRef(GithubPayload payload) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            final String posturl = BASE_URL + "/repos" + payload.getRepository().getPath() + "/git/refs";
            final HttpPost createRefPost = new HttpPost(posturl);
            createRefPost.setEntity(new StringEntity(Json.createObjectBuilder().add("ref", "refs/heads/" + BRANCH).add("sha", payload.getCommitHash()).build().toString()));
            try (CloseableHttpResponse response = client.execute(createRefPost, basicAuth(posturl))) {
                if (!isValidStatus(response.getStatusLine().getStatusCode())) {
                    logUnexpectedResponse(createRefPost, response);
                }
            }
        }
    }

    private static void logUnexpectedResponse(HttpRequestBase request, HttpResponse response) throws IOException {
        LOGGER.error("Unexpected status code for " + request.getMethod() + " to " + request.getURI() + "! " + response.getStatusLine().getStatusCode() + "payload=" + (response.getEntity() == null ? "" : IOUtils.toString(response.getEntity().getContent(), "UTF-8")));
        throw new RuntimeException("Unexpected status code: " + response.getStatusLine().getStatusCode());
    }

    @Override
    public boolean isReportStorageAccessible() {
        try {
            return checkAuthentication();
        } catch (IOException e) {
            LOGGER.error("Exception while checking authentication to Github API.", e);
            return false;
        }

    }

    @Override
    public String writeFailureReport(GithubPayload payload, String report) throws IOException {
        final String filename = "report.txt";
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            final String posturl = BASE_URL + "/gists";
            final HttpPost createGistPost = new HttpPost(posturl);
            createGistPost.setEntity(new StringEntity(Json.createObjectBuilder()
                    .add("description", "automatically generated validation report")
                    .add("public", true)
                    .add("files", Json.createObjectBuilder().add(filename, Json.createObjectBuilder().add("content", report).build()).build()).build().toString()));
            try (CloseableHttpResponse response = client.execute(createGistPost, basicAuth(posturl))) {
                if (!isValidStatus(response.getStatusLine().getStatusCode())) {
                    logUnexpectedResponse(createGistPost, response);
                } else {
                    JsonObject responseObject = Json.createReader(response.getEntity().getContent()).readObject();
                    return responseObject.getJsonObject("files").getJsonObject(filename).getString("raw_url");
                }
            }
        }
        return null;
    }

    private HttpClientContext basicAuth(final String urlStr) throws MalformedURLException {
        if (unauthorized) {
            LOGGER.warn("Excluding previously failed authorization credentials in request to avoid lock-out.");
            return HttpClientContext.create();
        }
        URL url = new URL(urlStr);
        HttpHost targetHost = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(getUser(), getToken()));
        AuthCache authCache = new BasicAuthCache();
        authCache.put(targetHost, new BasicScheme());
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);
        context.setAuthCache(authCache);
        return context;
    }

    private String getUser() {
        return System.getenv("GITHUB_USERNAME");
    }

    private String getToken() {
        return System.getenv("GITHUB_TOKEN");
    }

    private boolean isValidStatus(int status) {
        if (status == 401) {
            // prevent subsequent attempts
            LOGGER.error("Unauthorized response received: you must update the GITHUB_USERNAME or GITHUB_TOKEN and restart the application!");
            unauthorized = true;
        }
        return status >= 200 && status <=300;
    }
}
