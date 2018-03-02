package org.dplava.git;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.io.IOException;
import java.net.URI;

/**
 * Interacts with github using the github API to store custom
 * status values for commits.
 */
public class GithubValidityRegistry implements ValidityRegistry {

    public static final String CONTEXT = "validation/dplava";

    private static final String BASE_URL = "https://api.github.com";

    private String owner = "dplava";

    @Override
    public String getCommitStatus(URI repo, String commitHash) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(BASE_URL + "/repos/" + repo.getPath() + "/commits/" + commitHash + "/statuses");
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

    @Override
    public void reportCommitInvalid(URI repo, String commitHash, String url) throws IOException {
        postStatus(repo, commitHash, FAILURE, url);
    }

    @Override
    public void reportCommitValid(URI repo, String commitHash) throws IOException {
        postStatus(repo, commitHash, SUCCESS, null);
    }

    @Override
    public void reportCommitPending(URI repo, String commitHash) throws IOException {
        postStatus(repo, commitHash, PENDING, null);
    }

    @Override
    public void reportSystemError(URI repo, String commitHash) throws IOException {
        postStatus(repo, commitHash, ERROR, null);
    }

    private void postStatus(final URI repo, final String commitHash, final String state, final String url) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(BASE_URL + "/repos/" + repo.getPath() + "/commits/" + commitHash + "/statuses/" + commitHash);
            JsonObjectBuilder statusJson = Json.createObjectBuilder().add("state", state).add("context", CONTEXT);
            if (url != null) {
                statusJson.add("target_url", url);
            }
            post.setEntity(new StringEntity(statusJson.build().toString()));
            try (CloseableHttpResponse response = client.execute(post)) {
                if (response.getStatusLine().getStatusCode() != 201) {
                    throw new RuntimeException("Unexpected status code! " + response.getStatusLine().getStatusCode());
                }
            }
        }
    }
}
