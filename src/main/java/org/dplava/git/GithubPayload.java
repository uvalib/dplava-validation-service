package org.dplava.git;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.ws.rs.core.Response;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;

public class GithubPayload {

    private JsonObject payload;
    private URI repository;
    
    public GithubPayload(byte[] payloadBytes, String signature) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(getSecret().getBytes(), "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(keySpec);
            byte[] result = mac.doFinal(payloadBytes);
            final String computedDigest = Hex.encodeHexString(result);

            if (!computedDigest.equalsIgnoreCase(signature)) {
                //LOGGER.warn("Signature mismatch: " + computedDigest + " != " + signature);
                File dump = new File("payload-dump.bin");
                try (FileOutputStream fos = new FileOutputStream(dump)) {
                    IOUtils.write(payloadBytes, fos);
                }
                //return Response.status(400).entity("Signature mismatch.").build();
                throw new RuntimeException("Signature mismatch.");
            }

            try (JsonReader reader = Json.createReader(new ByteArrayInputStream(payloadBytes))) {
                payload = reader.readObject();
            }
            
            repository = new URI(payload.getString("repo"));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    
    public JsonObject getPayload() {
        return payload;
    }
    
    public URI getRepository() {
        return repository;
    }
    
    public String getRef() {
        return payload.getJsonObject("repository").getString("url");
    }
    
    public String getCommitHash() {
        return payload.getString("after");
    }
    
    public String getCommitId() {
        return payload.getJsonObject("head_commit").getString("id");
    }
    
    public String getEmail() {
        return payload.getJsonObject("pusher").getString("email");
    }
    
    private String getSecret() {
        return System.getenv("GITHUB_SECRET");
    }
}
