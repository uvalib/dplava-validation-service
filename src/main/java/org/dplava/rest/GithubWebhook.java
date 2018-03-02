package org.dplava.rest;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.dplava.git.GithubValidityRegistry;
import org.dplava.git.RepositoryCommitValidator;
import org.dplava.git.ValidityRegistry;
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
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.DigestInputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;


/**
 * Created by md5wz on 3/1/18.
 */
@Path("/")
public class GithubWebhook {

    @Context
    private UriInfo uri;

    private RepositoryCommitValidator validator;

    private ValidityRegistry gitStatus;

    private static final Logger LOGGER = LoggerFactory.getLogger(GithubWebhook.class);

    public GithubWebhook() {
        validator = new RepositoryCommitValidator(4, (System.getProperty("STATUS_REPORT_DIRECTORY") == null ? new File("commit-status-reports") : new File(System.getProperty("STATUS_REPORT_DIRECTORY"))));
        gitStatus = new GithubValidityRegistry();
    }

    private String getSecret() {
        return System.getenv("GITHUB_SECRET");
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

    @Path("report/{owner}/{repo}/{commit}")
    @GET
    @Produces("text/plain")
    public Response getReport(@PathParam("owner") String owner, @PathParam("repo") String repo, @PathParam("commit") String commitHash) throws IOException, URISyntaxException {
        final String status = validator.getFailureReport(new URI("https://github.com/" + owner + '/' + repo), commitHash);
        if (status == null) {
            return Response.status(404).build();
        } else {
            return Response.status(200).entity(status).build();
        }
    }

}
