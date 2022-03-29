///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.regression;


/**
 * Stores the various components of a regression result in a single class so
 * that they can be passed together as an argument or return value.
 *
 * @author Joseph Ramsey
 */
class RegressionPlane {

    /**
     * Summary string.
     */
    private final String summary;

    /**
     * Variable names.
     */
    private final String[] varNames;

    /**
     * The number of data points.
     */
    private final int sampleSize;

    /**
     * The number of regressors.
     */
    private final int numRegressors;

    /**
     * The array of regression coefficients.
     */
    private final double[] coefs;

    /**
     * The array of coefT-statistics for the regression coefficients.
     */
    private final double[] coefT;

    /**
     * The array of coefP-values for the regression coefficients.
     */
    private final double[] coefP;

    /**
     * Standard errors of the coefficients
     */
    private final double[] coefSE;

    /**
     * True iff this model assumes a zero intercept.
     */
    private final boolean zeroIntercept;

    /**
     * R square value.
     */
    private final double rSquare;

    /**
     * Constructs a new LinRegrResult.
     *
     * @param sampleSize    the number of spectra.
     * @param numRegressors the number of regressors
     * @param coefs         the array of regression coefficients.
     * @param coefT         the array of coefT-statistics for the regression
     *                      coefficients.
     * @param coefP         the array of coefP-values for the regression
     *                      coefficients.
     */
    public RegressionPlane(final boolean zeroIntercept, final String[] varNames,
                           final int numRegressors, final int sampleSize, final double[] coefs, final double[] coefT,
                           final double[] coefP, final double rsquare, final double[] coefSE, final String summary) {
        this.zeroIntercept = zeroIntercept;

        final int error = zeroIntercept ? 0 : 1;

        if (varNames.length != numRegressors + error) {
            throw new IllegalArgumentException();
        }

        if (coefs.length != numRegressors + error) {
            throw new IllegalArgumentException();
        }

        if (coefT.length != numRegressors + error) {
            throw new IllegalArgumentException();
        }

        if (coefP.length != numRegressors + error) {
            throw new IllegalArgumentException();
        }

        this.varNames = varNames;
        this.numRegressors = numRegressors;
        this.sampleSize = sampleSize;
        this.coefs = coefs;
        this.coefT = coefT;
        this.coefP = coefP;
        this.coefSE = coefSE;
        this.rSquare = rsquare;
        this.summary = summary;
    }

    public double[] getCoefSE() {
        return this.coefSE;
    }

    public double getRSquare() {
        return this.rSquare;
    }

    /**
     * @return the number of data points.
     */
    public int getSampleSize() {
        return this.sampleSize;
    }

    /**
     * @return the number of regressors.
     */
    public int getNumRegressors() {
        return this.numRegressors;
    }

    /**
     * @return the array of regression coeffients.
     */
    public double[] getCoef() {
        return this.coefs;
    }

    /**
     * @return the array of coefT-statistics for the regression coefficients.
     */
    public double[] getCoefT() {
        return this.coefT;
    }

    /**
     * @return the array of coefP-values for the regression coefficients.
     */
    public double[] getCoefP() {
        return this.coefP;
    }


    public String[] getVarNames() {
        return this.varNames;
    }

    public double getPredictedValue(final double[] x) {
        double yHat = 0.0;

        final int offset = this.zeroIntercept ? 0 : 1;

        for (int i = offset; i < this.numRegressors; i++) {
            yHat += this.coefs[i + offset] * x[i];
        }

        if (!this.zeroIntercept) {
            yHat += this.coefs[0]; // error term.
        }

        return yHat;
    }

    public String toString() {
        return this.summary;
    }
}






