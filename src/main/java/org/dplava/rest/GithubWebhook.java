package org.dplava.rest;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.dplava.git.GithubValidityRegistry;
import org.dplava.git.RepositoryCommitValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;


/**
 * Created by md5wz on 3/1/18.
 */
@Path("/")
public class GithubWebhook {

    @Context
    private UriInfo uri;

    private RepositoryCommitValidator validator;

    private GithubValidityRegistry gitStatus;

    private static final Logger LOGGER = LoggerFactory.getLogger(GithubWebhook.class);

    public GithubWebhook() {
        gitStatus = new GithubValidityRegistry();
        validator = new RepositoryCommitValidator(4, gitStatus);
    }

    private String getSecret() {
        return System.getenv("GITHUB_SECRET");
    }

    @Path("version")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response version() {
        try {
            Properties p = new Properties();
            p.load(this.getClass().getClassLoader().getResourceAsStream("version.properties"));
            return Response.status(200).entity(Json.createObjectBuilder().add("version", p.getProperty("version")).build()).build();
        } catch (IOException e) {
            LOGGER.error("Error serving /status.", e);
            return Response.status(500).build();
        }
    }

    @Path("healthcheck")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response healthcheck() {
        return Response.status(200).entity(Json.createObjectBuilder().add("github_api",
                Json.createObjectBuilder().add("healthy", gitStatus.isReportStorageAccessible())).build()).build();
    }

    @Path("status")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response status() {
        LOGGER.debug("Served a request to " + "/status");
        return Response.status(200).entity(Json.createObjectBuilder().add("status", "ok").build()).build();
    }

    @Path("webhook/push")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response push(final byte[] payloadBytes, @HeaderParam("X-Hub-Signature") String signature, @HeaderParam("X-GitHub-Event") final String eventType) throws URISyntaxException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        if (signature == null) {
            return Response.status(400).entity("Missing X-Hub-Signature header!").build();
        }
        signature = signature.substring("sha1=".length());

        final JsonObject payload;
        SecretKeySpec keySpec = new SecretKeySpec(getSecret().getBytes(), "HmacSHA1");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(keySpec);
        byte[] result = mac.doFinal(payloadBytes);
        final String computedDigest = Hex.encodeHexString(result);
        if (!computedDigest.equalsIgnoreCase(signature)) {
            LOGGER.warn("Signature mismatch: " + computedDigest + " != " + signature);
            File dump = new File("payload-dump.bin");
            try (FileOutputStream fos = new FileOutputStream(dump)) {
                IOUtils.write(payloadBytes, fos);
            }
            return Response.status(400).entity("Signature mismatch.").build();
        }

        if (eventType.equals("ping")) {
            return Response.status(200).build();
        } else if (!eventType.equals("push")) {
            return Response.status(400).entity("This service only supports \"push\" and \"ping\" events!").build();
        }

        try (JsonReader reader = Json.createReader(new ByteArrayInputStream(payloadBytes))) {
            payload = reader.readObject();
        }

        // for each commit in the push, check the status
        // if there's none for this context, queue a validation of that commit
        if (payload == null) {
            return Response.status(400).build();
        }

        if (!payload.getString("ref").equals("refs/heads/master")) {
            return Response.status(200).entity("This hook only validates the master branch.").build();
        }

        final URI repo = new URI(payload.getJsonObject("repository").getString("url"));
        final String commitHash = payload.getString("after");

        try {
            validator.queueForValidation(repo, commitHash, gitStatus, System.getenv("REPORT_URL"));
        } catch (IOException e) {
            return Response.status(500).build();
        }
        return Response.status(201).build();
    }

}
