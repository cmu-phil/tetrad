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
    private String summary;

    /**
     * Variable names.
     */
    private String[] varNames;

    /**
     * The number of data points.
     */
    private int sampleSize;

    /**
     * The number of regressors.
     */
    private int numRegressors;

    /**
     * The array of regression coefficients.
     */
    private double[] coefs;

    /**
     * The array of coefT-statistics for the regression coefficients.
     */
    private double[] coefT;

    /**
     * The array of coefP-values for the regression coefficients.
     */
    private double[] coefP;

    /**
     * Standard errors of the coefficients
     */
    private double[] coefSE;

    /**
     * True iff this model assumes a zero intercept.
     */
    private boolean zeroIntercept;

    /**
     * R square value.
     */
    private double rSquare;

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
    public RegressionPlane(boolean zeroIntercept, String[] varNames,
                           int numRegressors, int sampleSize, double[] coefs, double[] coefT,
                           double[] coefP, double rsquare, double[] coefSE, String summary) {
        this.zeroIntercept = zeroIntercept;

        int error = zeroIntercept ? 0 : 1;

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
        return coefSE;
    }

    public double getRSquare() {
        return rSquare;
    }

    /**
     * @return the number of data points.
     */
    public int getSampleSize() {
        return sampleSize;
    }

    /**
     * @return the number of regressors.
     */
    public int getNumRegressors() {
        return numRegressors;
    }

    /**
     * @return the array of regression coeffients.
     */
    public double[] getCoef() {
        return coefs;
    }

    /**
     * @return the array of coefT-statistics for the regression coefficients.
     */
    public double[] getCoefT() {
        return coefT;
    }

    /**
     * @return the array of coefP-values for the regression coefficients.
     */
    public double[] getCoefP() {
        return coefP;
    }


    public String[] getVarNames() {
        return varNames;
    }

    public double getPredictedValue(double[] x) {
        double yHat = 0.0;

        int offset = zeroIntercept ? 0 : 1;

        for (int i = offset; i < numRegressors; i++) {
            yHat += coefs[i + offset] * x[i];
        }

        if (!zeroIntercept) {
            yHat += coefs[0]; // error term.
        }

        return yHat;
    }

    public String toString() {
        return summary;
    }
}






