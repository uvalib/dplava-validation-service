package org.dplava.rest;

import org.dplava.git.GithubValidityRegistry;
import org.dplava.git.RepositoryCommitValidator;
import org.dplava.git.ValidityRegistry;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;


/**
 * Created by md5wz on 3/1/18.
 */
public class GithubWebhook {

    @Context
    private UriInfo uri;

    private RepositoryCommitValidator validator;

    private ValidityRegistry gitStatus;

    public GithubWebhook() {
        validator = new RepositoryCommitValidator(4, (System.getProperty("STATUS_REPORT_DIRECTORY") == null ? new File("commit-status-reports") : new File(System.getProperty("STATUS_REPORT_DIRECTORY"))));
        gitStatus = new GithubValidityRegistry();
    }

    @Path("webhook/push")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response push(final JsonObject payload) throws URISyntaxException {
        // for each commit in the push, check the status
        // if there's none for this context, queue a validation of that commit

        final URI repo = new URI(payload.getJsonObject("repository").getString("url"));
        final String commitHash = payload.getString("after");

        try {
            validator.queueForValidation(repo, commitHash, gitStatus, uri.getBaseUri().toString() + "/report/");
        } catch (IOException e) {
            return Response.status(500).build();
        }
        return Response.status(201).build();
    }

    @Path("report/{repo}/{commit}")
    @GET
    @Produces("text/plain")
    public Response getReport(@PathParam("repo") String repo, @PathParam("commit") String commitHash) throws IOException, URISyntaxException {
        final String status = validator.getFailureReport(new URI("https://github.com/" + repo), commitHash);
        if (status == null) {
            return Response.status(404).build();
        } else {
            return Response.status(200).entity(status).build();
        }
    }

}
