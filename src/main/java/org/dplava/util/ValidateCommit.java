package org.dplava.util;

import org.apache.commons.codec.binary.Hex;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.json.Json;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * POST a payload to the validation service to trigger validation of a single arbitrary commit.
 */
public class ValidateCommit {

    public static void main(String [] args) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        if (args.length != 4) {
            System.out.println("Four parameters required: validation hook url, repository owner/name, commit hash, secret");
            System.exit(1);
        }
        final String validationHookUrl = args[0];
        final String repoName = args[1];
        final String commitHash = args[1];
        final String secret = args[2];
        final String jsonPayload = Json.createObjectBuilder()
                .add("ref", "refs/heads/master")
                .add("repository", Json.createObjectBuilder().add("url", "https://github.com/" + repoName).build())
                .add("after", commitHash).build().toString();
        final byte[] payloadBytes = jsonPayload.getBytes("UTF-8");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA1");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(keySpec);
        byte[] result = mac.doFinal(payloadBytes);
        final String computedDigest = Hex.encodeHexString(result);
        String signature = "sha1=" + computedDigest;

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(validationHookUrl);
            post.setEntity(new ByteArrayEntity(payloadBytes));
            post.setHeader("content-type", "application/json");
            post.setHeader("X-Github-Event", "push");
            post.setHeader("X-Hub-Signature", signature);
            try (CloseableHttpResponse response = client.execute(post)) {
                if (response.getStatusLine().getStatusCode() == 201) {
                    System.out.println("Triggered validation of  commit " + commitHash + " for repo " + repoName + ".");
                } else {
                    System.err.println(response.getStatusLine().getStatusCode() + " status response!");
                }
            }
        }

    }
}
