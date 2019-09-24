package edu.cmu.tetrad.util;

import java.io.Serializable;

/**
 * Describes a parameter.
 *
 * @author jdramsey
 * @author Zhou Yuan <zhy19@pitt.edu>
 */
public class ParamDescription {

    // New
    private String paramName;
    private String shortDescription;
    private String longDescription;
    private Serializable defaultValue;
    private double lowerBoundDouble = Double.NEGATIVE_INFINITY;
    private double upperBoundDouble = Double.POSITIVE_INFINITY;
    private int lowerBoundInt = Integer.MIN_VALUE;
    private int upperBoundInt = Integer.MAX_VALUE;

    public ParamDescription(String paramName, String shortDescription, String longDescription, Serializable defaultValue) {
        if (paramName == null) {
            throw new NullPointerException("Target parameter name is null.");
        }

        if (shortDescription == null) {
            throw new NullPointerException("Target parameter short description is null.");
        }

        if (defaultValue == null) {
            throw new NullPointerException("No default value for " + paramName);
        }

        this.paramName = paramName;
        this.shortDescription = shortDescription;
        this.longDescription = longDescription;
        this.defaultValue = defaultValue;
    }

    public ParamDescription(String paramName, String shortDescription, String longDescription, Serializable defaultValue, int lowerBound, int upperBound) {
        if (paramName == null) {
            throw new NullPointerException("Target parameter name is null.");
        }

        if (shortDescription == null) {
            throw new NullPointerException("Target parameter short description is null.");
        }

        if (defaultValue == null) {
            throw new NullPointerException("No default value for " + paramName);
        }

        this.paramName = paramName;
        this.shortDescription = shortDescription;
        this.longDescription = longDescription;
        this.defaultValue = defaultValue;
        this.lowerBoundInt = lowerBound;
        this.upperBoundInt = upperBound;
    }

    public ParamDescription(String paramName, String shortDescription, String longDescription, Serializable defaultValue, double lowerBound, double upperBound) {
        if (paramName == null) {
            throw new NullPointerException("Target parameter name is null.");
        }

        if (shortDescription == null) {
            throw new NullPointerException("Target parameter short description is null.");
        }

        if (defaultValue == null) {
            throw new NullPointerException("No default value for " + paramName);
        }

        this.paramName = paramName;
        this.shortDescription = shortDescription;
        this.longDescription = longDescription;
        this.defaultValue = defaultValue;
        this.lowerBoundDouble = lowerBound;
        this.upperBoundDouble = upperBound;
    }

    public String getParamName() {
        return paramName;
    }

    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getLongDescription() {
        return longDescription;
    }

    public void setLongDescription(String longDescription) {
        this.longDescription = longDescription;
    }

    public Serializable getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Serializable defaultValue) {
        this.defaultValue = defaultValue;
    }

    public double getLowerBoundDouble() {
        return lowerBoundDouble;
    }

    public void setLowerBoundDouble(double lowerBoundDouble) {
        this.lowerBoundDouble = lowerBoundDouble;
    }

    public double getUpperBoundDouble() {
        return upperBoundDouble;
    }

    public void setUpperBoundDouble(double upperBoundDouble) {
        this.upperBoundDouble = upperBoundDouble;
    }

    public int getLowerBoundInt() {
        return lowerBoundInt;
    }

    public void setLowerBoundInt(int lowerBoundInt) {
        this.lowerBoundInt = lowerBoundInt;
    }

    public int getUpperBoundInt() {
        return upperBoundInt;
    }

    public void setUpperBoundInt(int upperBoundInt) {
        this.upperBoundInt = upperBoundInt;
    }

}
