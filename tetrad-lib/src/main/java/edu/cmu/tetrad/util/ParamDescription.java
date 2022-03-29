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

    public ParamDescription(final String paramName, final String shortDescription, final String longDescription, final Serializable defaultValue) {
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

    public ParamDescription(final String paramName, final String shortDescription, final String longDescription, final Serializable defaultValue, final int lowerBound, final int upperBound) {
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

    public ParamDescription(final String paramName, final String shortDescription, final String longDescription, final Serializable defaultValue, final double lowerBound, final double upperBound) {
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
        return this.paramName;
    }

    public void setParamName(final String paramName) {
        this.paramName = paramName;
    }

    public String getShortDescription() {
        return this.shortDescription;
    }

    public void setShortDescription(final String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getLongDescription() {
        return this.longDescription;
    }

    public void setLongDescription(final String longDescription) {
        this.longDescription = longDescription;
    }

    public Serializable getDefaultValue() {
        return this.defaultValue;
    }

    public void setDefaultValue(final Serializable defaultValue) {
        this.defaultValue = defaultValue;
    }

    public double getLowerBoundDouble() {
        return this.lowerBoundDouble;
    }

    public void setLowerBoundDouble(final double lowerBoundDouble) {
        this.lowerBoundDouble = lowerBoundDouble;
    }

    public double getUpperBoundDouble() {
        return this.upperBoundDouble;
    }

    public void setUpperBoundDouble(final double upperBoundDouble) {
        this.upperBoundDouble = upperBoundDouble;
    }

    public int getLowerBoundInt() {
        return this.lowerBoundInt;
    }

    public void setLowerBoundInt(final int lowerBoundInt) {
        this.lowerBoundInt = lowerBoundInt;
    }

    public int getUpperBoundInt() {
        return this.upperBoundInt;
    }

    public void setUpperBoundInt(final int upperBoundInt) {
        this.upperBoundInt = upperBoundInt;
    }

}
