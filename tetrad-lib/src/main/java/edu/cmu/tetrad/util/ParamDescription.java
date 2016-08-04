package edu.cmu.tetrad.util;

/**
 * Describes a parameter.
 *
 * @author jdramsey
 */
public class ParamDescription {
    private String description;
    private Object defaultValue;

    public ParamDescription(String description, Object defaultValue) {
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

    public Object getDefaultValue() {
        return defaultValue;
    }
}
