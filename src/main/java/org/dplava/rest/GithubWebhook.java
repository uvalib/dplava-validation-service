package org.dplava.rest;

import org.dplava.git.GithubPayload;
import org.dplava.git.GithubValidityRegistry;
import org.dplava.git.RepositoryCommitValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
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
import java.io.IOException;
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

        GithubPayload githubPayload = new GithubPayload(payloadBytes, signature);
        
        if (eventType.equals("ping")) {
            return Response.status(200).build();
        } else if (!eventType.equals("push")) {
            return Response.status(400).entity("This service only supports \"push\" and \"ping\" events!").build();
        }
        
        // for each commit in the push, check the status
        // if there's none for this context, queue a validation of that commit
        if (githubPayload.getPayload() == null) {
            return Response.status(400).build();
        }

        if (!githubPayload.getRef().equals("refs/heads/master")) {
            return Response.status(200).entity("This hook only validates the master branch.").build();
        }

        try {
            validator.queueForValidation(githubPayload, gitStatus);
        } catch (IOException e) {
            return Response.status(500).build();
        }
        return Response.status(201).build();
    }

}
