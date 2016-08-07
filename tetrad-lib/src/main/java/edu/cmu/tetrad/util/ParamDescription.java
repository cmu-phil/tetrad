package edu.cmu.tetrad.util;

import java.io.Serializable;

/**
 * Describes a parameter.
 *
 * @author jdramsey
 */
public class ParamDescription {
    private String description;
    private Serializable defaultValue;

    public ParamDescription(String description, Serializable defaultValue) {
        if (description == null) {
            throw new NullPointerException("Description is null.");
        }

        if (defaultValue == null) {
            throw new NullPointerException("No default value for " + description);
        }

        this.description = description;
        this.defaultValue = defaultValue;
    }

    public String getDescription() {
        return description;
    }

    public Serializable getDefaultValue() {
        return defaultValue;
    }
}
