package org.dplava.validation;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by md5wz on 3/1/18.
 */
public class ErrorAggregator implements ErrorHandler {

    List<String> warnings = new ArrayList<>();

    List<String> errors = new ArrayList<>();

    List<String> fatals = new ArrayList<>();

    public boolean isValid() {
        return errors.isEmpty() && fatals.isEmpty();
    }

    public String getErrors() {
        StringBuffer sb = new StringBuffer();
        for (String fatal : fatals) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("Fatal: " + fatal);
        }
        for (String error : errors) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("Error: " + error);
        }
        return sb.toString();
    }

    @Override
    public void warning(SAXParseException exception) throws SAXException {
        addError(exception, warnings);
    }

    @Override
    public void error(SAXParseException exception) throws SAXException {
        addError(exception, errors);
    }

    @Override
    public void fatalError(SAXParseException exception) throws SAXException {
        addError(exception, fatals);
    }

    public void error(String message) {
        this.errors.add(message);
    }

    private void addError(SAXParseException exception, List<String> list) {
        list.add(exception.getLocalizedMessage() != null
                ? exception.getLocalizedMessage()
                : exception.getCause() != null
                    ? exception.getCause().getLocalizedMessage() == null
                        ? exception.getCause().getClass().getName()
                        : exception.getCause().getLocalizedMessage()
                : exception.getClass().getName());
    }
}

