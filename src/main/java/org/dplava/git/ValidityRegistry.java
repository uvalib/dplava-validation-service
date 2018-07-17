package org.dplava.git;

import java.io.IOException;

public interface ValidityRegistry {

    public static String SUCCESS = "success";
    public static String ERROR = "error";
    public static String FAILURE = "failure";
    public static String PENDING = "pending";

    public String getCommitStatus(GithubPayload payload) throws IOException;

    public void reportCommitInvalid(GithubPayload payload, String url) throws IOException;

    public void reportCommitValid(GithubPayload payload) throws IOException;

    public void reportCommitPending(GithubPayload payload) throws IOException;

    public void reportSystemError(GithubPayload payload) throws IOException;

}
