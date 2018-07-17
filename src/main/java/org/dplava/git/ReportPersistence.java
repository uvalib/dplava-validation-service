package org.dplava.git;

import java.io.IOException;
import java.net.URI;

/**
 * Astraction around validation report persistence.
 */
public interface ReportPersistence {

    /**
     * Checks whether the storage that is used to store failure reports is accessible (read/write access).
     */
    public boolean isReportStorageAccessible();

    /**
     * Writes a failure report and returns a URL from which it may later be retrieved.
     * @param repo the repository whose commit validation failed
     * @param commit the commit whose validation failed
     * @param report the text of the report
     * @return the URL at which the report can henceforth be retrieved
     * @throws IOException if an error occurs while persisting the report.
     */
    public String writeFailureReport(GithubPayload payload, final String report) throws IOException;

}
