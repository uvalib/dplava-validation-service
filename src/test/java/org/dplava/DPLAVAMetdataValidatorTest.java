package org.dplava;

import org.dplava.validation.DPLAVAMetadataValidator;
import org.dplava.validation.ErrorAggregator;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class DPLAVAMetdataValidatorTest {

    static DPLAVAMetadataValidator V;

    public DPLAVAMetdataValidatorTest() throws Exception {
        if (V == null) {
            V = new DPLAVAMetadataValidator();
        }
    }

    @Test
    public void testValidatorAgainstValidFile() throws IOException, SAXException, ParserConfigurationException, TransformerException {
        ErrorAggregator e = new ErrorAggregator();
        V.validateFile(new File("src/test/resources/sample-valid.xml"), e);
        System.out.println(e.getErrors());
        assertEquals(true, e.isValid());
    }

    @Test
    public void testValidatorAgainstNotWellFormeFile() throws IOException, SAXException, ParserConfigurationException, TransformerException {
        ErrorAggregator e = new ErrorAggregator();
        V.validateFile(new File("src/test/resources/sample-not-well-formed.xml"), e);
        assertEquals(false, e.isValid());
    }

    @Test
    public void testValidatorAgainstWrongFile() throws IOException, SAXException, ParserConfigurationException, TransformerException {
        ErrorAggregator e = new ErrorAggregator();
        V.validateFile(new File("src/test/resources/sample-no-schema.xml"), e);
        assertEquals(false, e.isValid());
    }

    @Test
    public void testValidatorAgainstMissingTitleField() throws IOException, SAXException, ParserConfigurationException, TransformerException {
        ErrorAggregator e = new ErrorAggregator();
        V.validateFile(new File("src/test/resources/sample-missing-title.xml"), e);
        assertEquals(false, e.isValid());
        assertEquals("Error: sample-missing-title.xml - At least one title element is required.", e.getErrors());
    }

    @Test
    public void testValidatorAgainstExtraField() throws IOException, SAXException, ParserConfigurationException, TransformerException {
        ErrorAggregator e = new ErrorAggregator();
        V.validateFile(new File("src/test/resources/sample-unapproved-extension.xml"), e);
        assertEquals(false, e.isValid());
    }


}
