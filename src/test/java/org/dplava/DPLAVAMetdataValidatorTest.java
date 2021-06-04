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
    
    @Test
    public void testValidatorAgainstInvalidDate() throws IOException, SAXException, ParserConfigurationException, TransformerException {
        ErrorAggregator e = new ErrorAggregator();
        V.validateFile(new File("src/test/resources/sample-invalid-date.xml"), e);
        assertEquals(false, e.isValid());
     }

    @Test
    public void testValidatorAgainstValidDateSet() throws IOException, SAXException, ParserConfigurationException, TransformerException {
        ErrorAggregator e = new ErrorAggregator();
        V.validateFile(new File("src/test/resources/sample-valid-dateset.xml"), e);
        assertEquals("", e.getErrors());
        assertEquals(true, e.isValid());
    }

    @Test
    public void testValidatorAgainstInvalidDateSet() throws IOException, SAXException, ParserConfigurationException, TransformerException {
        ErrorAggregator e = new ErrorAggregator();
        V.validateFile(new File("src/test/resources/sample-invalid-dateset.xml"), e);
        assertEquals(false, e.isValid());
        assertEquals("Error: sample-invalid-dateset.xml - A date set must contain either \",\" or \"..\". Date sets containing spaces, more than\n"
                + "                2 successive dots, more than 1 successive comma, adjoining dots and commas, commas\n"
                + "                at beginning or end of the expression, dots at beginning and end of the expression\n"
                + "                without an intervening comma, and successive dots without an intervening comma are\n"
                + "                invalid.\n"
                + "Error: sample-invalid-dateset.xml - Warning: Suspect value.", e.getErrors());
    }
    
    @Test
    public void testValidatorAgainstValidCNERS() throws IOException, SAXException, ParserConfigurationException, TransformerException {
        ErrorAggregator e = new ErrorAggregator();
        assertEquals(true, e.isValid());
    }

}
