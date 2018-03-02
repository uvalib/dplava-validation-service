package org.dplava.validation;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * A class that encapsulates the validation rules for individual metadata
 * XML files for submission into the Virginia DPLA Hub.  This includes
 * some file naming conventions, as well as the rules encoded in the public
 * version of the dplava schema at http://dplava.lib.virginia.edu/dplava.xsd
 *
 * This class only fetches the remote schema at construction time, so if used
 * in a long-running application, DPLAVAMetdataValidators should be periodically
 * replaced with a new instance to reflect any published changes.  On the
 * other hand, the overhead of the constructor should be avoided when unecessary
 * as it takes longer than hundreds of individual calls to validateFile().
 */
public class DPLAVAMetadataValidator {

    final static String SCHEMA_URL = "http://dplava.lib.virginia.edu/dplava.xsd";

    private DocumentBuilderFactory factory;

    EmbeddedSchematronValidator validator;

    public DPLAVAMetadataValidator() throws ParserConfigurationException, TransformerException, IOException, SAXException {
        factory = DocumentBuilderFactory.newInstance();
        factory.setSchema(SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(new URL(SCHEMA_URL)));
        factory.setNamespaceAware(true);

        validator = new EmbeddedSchematronValidator(SCHEMA_URL);
    }

    private DocumentBuilder getDocumentBuilder(ErrorAggregator errors) throws ParserConfigurationException {
        DocumentBuilder b = factory.newDocumentBuilder();
        b.setErrorHandler(errors);
        b.setEntityResolver(new CachingEntityResolver("dplava.lib.virginia.edu", "www.w3.org"));
        return b;
    }

    public void validateFile(File file, ErrorAggregator errors) throws ParserConfigurationException, IOException, SAXException, TransformerException {
        try {
            // perform schema validation
            Document d = getDocumentBuilder(errors).parse(file);

            // perform schematron validation
            validator.validateXmlDocument(d, errors);

        } catch (SAXParseException ex) {
            errors.fatalError(ex);
        }
    }
}
