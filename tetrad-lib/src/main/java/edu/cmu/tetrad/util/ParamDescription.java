package edu.cmu.tetrad.util;

import java.io.Serializable;

/**
 * Describes a parameter.
 *
 * @author jdramsey
 */
public class ParamDescription {

    private String description;
    private String longDescription;
    private Serializable defaultValue;
    private double lowerBoundDouble = Double.NEGATIVE_INFINITY;
    private double upperBoundDouble = Double.POSITIVE_INFINITY;
    private int lowerBoundInt = Integer.MIN_VALUE;
    private int upperBoundInt = Integer.MAX_VALUE;

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

    public ParamDescription(String description, Serializable defaultValue, double lowerBound, double upperBound) {
        if (description == null) {
            throw new NullPointerException("Description is null.");
        }

        if (defaultValue == null) {
            throw new NullPointerException("No default value for " + description);
        }

        this.description = description;
        this.defaultValue = defaultValue;
        this.lowerBoundDouble = lowerBound;
        this.upperBoundDouble = upperBound;
    }

    public ParamDescription(String description, Serializable defaultValue, int lowerBound, int upperBound) {
        if (description == null) {
            throw new NullPointerException("Description is null.");
        }

        if (defaultValue == null) {
            throw new NullPointerException("No default value for " + description);
        }

        this.description = description;
        this.defaultValue = defaultValue;
        this.lowerBoundInt = lowerBound;
        this.upperBoundInt = upperBound;
    }

    public String getDescription() {
        return description;
    }

    public Serializable getDefaultValue() {
        return defaultValue;
    }

    public double getLowerBoundDouble() {
        return lowerBoundDouble;
    }

    public double getUpperBoundDouble() {
        return upperBoundDouble;
    }

    public int getLowerBoundInt() {
        return lowerBoundInt;
    }

    public int getUpperBoundInt() {
        return upperBoundInt;
    }

    public String getLongDescription() {
        return longDescription;
    }

    public void setLongDescription(String longDescription) {
        this.longDescription = longDescription;
    }
}
