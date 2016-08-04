package edu.cmu.tetrad.algcomparison.utils;

/**
 * Describes a parameter.
 *
 * @author jdramsey
 */
public class ParamDescription {
    private String description;
    private Object defaultValue;

    public ParamDescription(String description, Object defaultValue) {
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
