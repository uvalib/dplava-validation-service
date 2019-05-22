package org.dplava.validation;

import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class CachingEntityResolver implements EntityResolver {

    private Collection<String> allowedDomains;

    private Map<String, String> cachedSchemas;

    /**
     * Instantiates and initializes a CachingEntityResolver that maintains a perpetual
     * in-memory cache for all entities resolved from the given allowed domains.
     * @param allowedDomains domains from which scheme files will be cached.  Others will be
     *                       fetched new each time.
     */
    public CachingEntityResolver(String ... allowedDomains) {
        this.allowedDomains = Arrays.asList(allowedDomains);
        this.cachedSchemas = new HashMap<>();
    }

    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        URL url = new URL(systemId);
        synchronized (allowedDomains) {
            if (allowedDomains.contains(url.getHost())) {
                if (cachedSchemas.containsKey(systemId)) {
                    return new InputSource(new StringReader(cachedSchemas.get(systemId)));
                } else {
                    cachedSchemas.put(systemId, getSmallBodyFollowRedirects(url));
                    return new InputSource(new StringReader(cachedSchemas.get(systemId)));
                }
            } else {
                return new InputSource(getSmallBodyFollowRedirects(url));
            }
        }
    }
    
    public static String getSmallBodyFollowRedirects(final URL url) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(url.toString());
            try (CloseableHttpResponse response = client.execute(get)) {
                final int status = response.getStatusLine().getStatusCode();
                if (status < 200 || status >= 300) {
                    throw new RuntimeException("Unexpected status (" + response.getStatusLine().getReasonPhrase() + ") when fetching " + url + "!");
                } else {
                    return EntityUtils.toString(response.getEntity());
                }
            }
            
        } 
    }
}
