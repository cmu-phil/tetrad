///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.util;

import java.io.Serializable;

/**
 * Describes a parameter.
 *
 * @author josephramsey
 * @author Zhou Yuan zhy19@pitt.edu
 * @version $Id: $Id
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
    private long lowerBoundLong = Long.MIN_VALUE;
    private long upperBoundLong = Long.MAX_VALUE;

    /**
     * <p>Constructor for ParamDescription.</p>
     *
     * @param paramName        a {@link java.lang.String} object
     * @param shortDescription a {@link java.lang.String} object
     * @param longDescription  a {@link java.lang.String} object
     * @param defaultValue     a {@link java.io.Serializable} object
     */
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

    /**
     * <p>Constructor for ParamDescription.</p>
     *
     * @param paramName        a {@link java.lang.String} object
     * @param shortDescription a {@link java.lang.String} object
     * @param longDescription  a {@link java.lang.String} object
     * @param defaultValue     a {@link java.io.Serializable} object
     * @param lowerBound       a int
     * @param upperBound       a int
     */
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

    /**
     * <p>Constructor for ParamDescription.</p>
     *
     * @param paramName        a {@link java.lang.String} object
     * @param shortDescription a {@link java.lang.String} object
     * @param longDescription  a {@link java.lang.String} object
     * @param defaultValue     a {@link java.io.Serializable} object
     * @param lowerBound       a double
     * @param upperBound       a double
     */
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

    /**
     * <p>Getter for the field <code>paramName</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getParamName() {
        return this.paramName;
    }

    /**
     * <p>Setter for the field <code>paramName</code>.</p>
     *
     * @param paramName a {@link java.lang.String} object
     */
    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    /**
     * <p>Getter for the field <code>shortDescription</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getShortDescription() {
        return this.shortDescription;
    }

    /**
     * <p>Setter for the field <code>shortDescription</code>.</p>
     *
     * @param shortDescription a {@link java.lang.String} object
     */
    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    /**
     * <p>Getter for the field <code>longDescription</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getLongDescription() {
        return this.longDescription;
    }

    /**
     * <p>Setter for the field <code>longDescription</code>.</p>
     *
     * @param longDescription a {@link java.lang.String} object
     */
    public void setLongDescription(String longDescription) {
        this.longDescription = longDescription;
    }

    /**
     * <p>Getter for the field <code>defaultValue</code>.</p>
     *
     * @return a {@link java.io.Serializable} object
     */
    public Serializable getDefaultValue() {
        return this.defaultValue;
    }

    /**
     * <p>Setter for the field <code>defaultValue</code>.</p>
     *
     * @param defaultValue a {@link java.io.Serializable} object
     */
    public void setDefaultValue(Serializable defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * <p>Getter for the field <code>lowerBoundDouble</code>.</p>
     *
     * @return a double
     */
    public double getLowerBoundDouble() {
        return this.lowerBoundDouble;
    }

    /**
     * <p>Setter for the field <code>lowerBoundDouble</code>.</p>
     *
     * @param lowerBoundDouble a double
     */
    public void setLowerBoundDouble(double lowerBoundDouble) {
        this.lowerBoundDouble = lowerBoundDouble;
    }

    /**
     * <p>Getter for the field <code>upperBoundDouble</code>.</p>
     *
     * @return a double
     */
    public double getUpperBoundDouble() {
        return this.upperBoundDouble;
    }

    /**
     * <p>Setter for the field <code>upperBoundDouble</code>.</p>
     *
     * @param upperBoundDouble a double
     */
    public void setUpperBoundDouble(double upperBoundDouble) {
        this.upperBoundDouble = upperBoundDouble;
    }

    /**
     * <p>Getter for the field <code>lowerBoundInt</code>.</p>
     *
     * @return a int
     */
    public int getLowerBoundInt() {
        return this.lowerBoundInt;
    }

    /**
     * <p>Setter for the field <code>lowerBoundInt</code>.</p>
     *
     * @param lowerBoundInt a int
     */
    public void setLowerBoundInt(int lowerBoundInt) {
        this.lowerBoundInt = lowerBoundInt;
    }

    /**
     * <p>Getter for the field <code>upperBoundInt</code>.</p>
     *
     * @return a int
     */
    public int getUpperBoundInt() {
        return this.upperBoundInt;
    }

    /**
     * <p>Setter for the field <code>upperBoundInt</code>.</p>
     *
     * @param upperBoundInt a int
     */
    public void setUpperBoundInt(int upperBoundInt) {
        this.upperBoundInt = upperBoundInt;
    }

    /**
     * <p>Getter for the field <code>lowerBoundLong</code>.</p>
     *
     * @return a long
     */
    public long getLowerBoundLong() {
        return lowerBoundLong;
    }

    /**
     * <p>Setter for the field <code>lowerBoundLong</code>.</p>
     *
     * @param lowerBoundLong a long
     */
    public void setLowerBoundLong(long lowerBoundLong) {
        this.lowerBoundLong = lowerBoundLong;
    }

    /**
     * <p>Getter for the field <code>upperBoundLong</code>.</p>
     *
     * @return a long
     */
    public long getUpperBoundLong() {
        return upperBoundLong;
    }

    /**
     * <p>Setter for the field <code>upperBoundLong</code>.</p>
     *
     * @param upperBoundLong a long
     */
    public void setUpperBoundLong(long upperBoundLong) {
        this.upperBoundLong = upperBoundLong;
    }

}

