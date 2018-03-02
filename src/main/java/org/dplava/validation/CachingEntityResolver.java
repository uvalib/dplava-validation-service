package org.dplava.validation;

import org.apache.commons.io.IOUtils;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class CachingEntityResolver implements EntityResolver {

    private int timeout;

    private Collection<String> allowedDomains;

    private Map<String, String> cachedSchemas;

    /**
     * Instantiates and initializes a CachingEntityResolver that maintains a perpetual
     * in-memory cache for all entities resovled from the given allowed domains.
     * @param allowedDomains domains from which scheme files will be cached.  Others will be
     *                       fetched new each time.
     */
    public CachingEntityResolver(String ... allowedDomains) {
        this.allowedDomains = Arrays.asList(allowedDomains);
        this.cachedSchemas = new HashMap<>();
        timeout = 10000;
    }

    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        URL url = new URL(systemId);
        synchronized (allowedDomains) {
            if (allowedDomains.contains(url.getHost())) {
                if (cachedSchemas.containsKey(systemId)) {
                    return new InputSource(new StringReader(cachedSchemas.get(systemId)));
                } else {
                    URLConnection c = url.openConnection();
                    c.setConnectTimeout(timeout);
                    c.setReadTimeout(timeout);
                    cachedSchemas.put(systemId, IOUtils.toString(c.getInputStream(), c.getContentEncoding()));
                    return new InputSource(new StringReader(cachedSchemas.get(systemId)));
                }
            } else {
                URLConnection c = url.openConnection();
                c.setConnectTimeout(timeout);
                c.setReadTimeout(timeout);
                return new InputSource(c.getInputStream());
            }
        }
    }
}
