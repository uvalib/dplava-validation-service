package org.dplava.validation;

import net.sf.saxon.dom.DocumentBuilderImpl;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Iterator;

public class EmbeddedSchematronValidator {

    private Transformer schematron;
    
    private Transformer w3cdtfValidationTransformer;

    private XPath xpath;

    public EmbeddedSchematronValidator(final String xsdUrl) throws TransformerException, IOException, SAXException {
        final URL xsd = new URL(xsdUrl);

        URIResolver r = new URIResolver() {
            @Override
            public Source resolve(String href, String base) throws TransformerException {
                try {
                    // fetch the URL
                    URL url = new URL(href);
                    return new StreamSource(url.openStream());
                } catch (MalformedURLException e) {
                    // check the classpath
                    final InputStream s = getClass().getClassLoader().getResourceAsStream(href);
                    if (s != null) {
                        return new StreamSource(s);
                    } else {
                        try {
                            URL relativeUrl = new URL(xsd.getProtocol(), xsd.getHost(), xsd.getPort(), xsd.getFile().contains("/") ? xsd.getFile().substring(0, xsd.getFile().lastIndexOf('/') + 1) + href : href);
                            return new StreamSource(relativeUrl.openStream());
                        } catch (Exception e1) {
                            throw new RuntimeException(e1);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                if (prefix.equals("svrl")) {
                    return "http://purl.oclc.org/dsdl/svrl";
                } else {
                    return null;
                }
            }

            @Override
            public String getPrefix(String namespaceURI) {
                if (namespaceURI.equals("http://purl.oclc.org/dsdl/svr")) {
                    return "svrl";
                } else {
                    return null;
                }
            }

            @Override
            public Iterator getPrefixes(String namespaceURI) {
                if (namespaceURI.equals("http://purl.oclc.org/dsdl/svrl")) {
                    return Collections.singleton(getPrefix(namespaceURI)).iterator();
                }
                return null;
            }
        });

        /*
         * Performing schematron validation using rules embedded in an XSD schema involves
         * running that schema through 4 transformations and using the resulting XSLT to
         * transform the file to validate.
         */
        SAXTransformerFactory f = (SAXTransformerFactory) TransformerFactory.newInstance("net.sf.saxon.TransformerFactoryImpl", null);
        f.setURIResolver(r);
        Templates t1 = f.newTemplates(new StreamSource(getClass().getClassLoader().getResourceAsStream("ExtractSchFromXSD-2.xsl")));
        Templates t2 = f.newTemplates(new StreamSource(getClass().getClassLoader().getResourceAsStream("iso_dsdl_include.xsl")));
        Templates t3 = f.newTemplates(new StreamSource(getClass().getClassLoader().getResourceAsStream("iso_abstract_expand.xsl")));
        Templates t4 = f.newTemplates(new StreamSource(getClass().getClassLoader().getResourceAsStream("iso_svrl_for_xslt2.xsl")));

        TransformerHandler th1 = f.newTransformerHandler(t1);
        TransformerHandler th2 = f.newTransformerHandler(t2);
        TransformerHandler th3 = f.newTransformerHandler(t3);
        TransformerHandler th4 = f.newTransformerHandler(t4);
        DOMResult domResult = new DOMResult();

        th1.setResult(new SAXResult(th2));
        th2.setResult(new SAXResult(th3));
        th3.setResult(new SAXResult(th4));
        th4.setResult(domResult);
        Transformer t = f.newTransformer();
        t.setURIResolver(r);

        DocumentBuilder b = new DocumentBuilderImpl();
        t.transform(new DOMSource(b.parse(xsd.openStream())), new SAXResult(th1));

        schematron = f.newTemplates(new DOMSource(domResult.getNode())).newTransformer();
        
        /*
         * Performing W3CDTF validation involves running the original document through a
         * single transform and parsing the output.
         */
        w3cdtfValidationTransformer = f.newTemplates(new StreamSource(getClass().getClassLoader().getResourceAsStream("checkEDTF_3.xsl"))).newTransformer();
    }

    public void validateXmlDocument(String filename, Document d, ErrorAggregator errors) {
        try {
            DOMResult result = new DOMResult();
            schematron.transform(new DOMSource(d), result);
            NodeList nl = (NodeList) xpath.evaluate("svrl:schematron-output/svrl:failed-assert/svrl:text", result.getNode(), XPathConstants.NODESET);
            for (int i = 0; i < nl.getLength() ; i ++) {
                errors.error(filename + " - " + (String) xpath.evaluate("text()", nl.item(i), XPathConstants.STRING));
            }
        } catch (TransformerException e) {
            errors.error(filename + " - " + (e.getLocalizedMessage() == null ? "Error performing schematron validation." : e.getLocalizedMessage()));
        } catch (XPathExpressionException e) {
            e.printStackTrace();
            errors.error(filename + " - " + "Error parsing schematron validation result.");
        }
        
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            StreamResult result = new StreamResult(os);
            w3cdtfValidationTransformer.transform(new DOMSource(d), result);
            String errorMessage = new String(os.toByteArray(), "UTF-8");
            if (errorMessage.length() > 0) {
                errors.error(filename + " - " + errorMessage.replace("--", "").replace("\n", " "));
            }
        } catch (TransformerException e) {
            errors.error(filename + " - " + (e.getLocalizedMessage() == null ? "Error performing schematron validation." : e.getLocalizedMessage()));
        } catch (UnsupportedEncodingException e) {
            errors.error("System Error: " + e.getLocalizedMessage());
        }

    }
}
