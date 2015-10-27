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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.sem.ParamType;
import edu.cmu.tetrad.sem.Parameter;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import pal.math.MultivariateFunction;
import pal.math.MultivariateMinimum;

import java.util.List;

/**
 * @author Joseph Ramsey
 */
public class RandomSearch extends MultivariateMinimum {
    static final long serialVersionUID = 23L;

    //=============================CONSTRUCTORS=========================//

    @Override
    public void optimize(MultivariateFunction f, double[] values, double v, double v1) {
        iterateFindLowerRandom(f, values, 5.0, 1500);
        iterateFindLowerRandom(f, values, 1.0, 500);
        iterateFindLowerRandom(f, values, 0.5, 500);
        iterateFindLowerRandom(f, values, 0.25, 500);
        iterateFindLowerRandom(f, values, 0.1, 500);
        iterateFindLowerRandom(f, values, 0.1, 500);
        iterateFindLowerRandom(f, values, 0.05, 500);
        iterateFindLowerRandom(f, values, 0.01, 500);
        iterateFindLowerRandom(f, values, 0.005, 50);
        iterateFindLowerRandom(f, values, 0.001, 50);
        iterateFindLowerRandom(f, values, 0.0005, 50);
        iterateFindLowerRandom(f, values, 0.0001, 50);
    }

    private void iterateFindLowerRandom(MultivariateFunction fcn, double[] p,
                                        double range, int iterations) {
        int t = 0;

        while (++t < 2000) {
            boolean found;

            try {
                found = findLowerRandom(fcn, p, range, iterations);
            } catch (Exception e) {
                return;
            }

            if (!found) {
                return;
            }
        }
    }

    /**
     * Returns true iff a new point was found with a lower score.
     */
    private boolean findLowerRandom(MultivariateFunction fcn, double[] p,
                                    double width, int numPoints) {
        double fP = fcn.evaluate(p);

        if (Double.isNaN(fP)) {
            throw new IllegalArgumentException("Center point must evaluate!");
        }

        // This point will remain fixed, the center of the search.
        double[] fixedP = new double[p.length];
        System.arraycopy(p, 0, fixedP, 0, p.length);

        // This point will move around randomly. If it ever has a lower
        // score than p, it will be copied into p (and returned).
        double[] pTemp = new double[p.length];
        System.arraycopy(p, 0, pTemp, 0, p.length);

        for (int i = 0; i < numPoints; i++) {
            randomPointAboutCenter(pTemp, fixedP, width);

            for (int h = 0; h < pTemp.length; h++) {
                if (pTemp[h] < fcn.getLowerBound(h)) continue;
                if (pTemp[h] > fcn.getUpperBound(h)) continue;
            }

            double f = fcn.evaluate(pTemp);

            if (f == Double.POSITIVE_INFINITY) {
                i--; continue;
            }

            // Try to find the lowest reachable spot for each of the trial points at width 1.
            if (width == 1) {
                int t = 0;
                while (++t < 2000) {
                    if (!findLowerRandomLocal(fcn, pTemp, width / 5, 10)) break;
                }
            }

            if (f < fP) {
                System.arraycopy(pTemp, 0, p, 0, pTemp.length);
                TetradLogger.getInstance().log("optimization", "Cube width = " + width + " FML = " + f);
                return true;
            }
        }

        return false;
    }

    private boolean findLowerRandomLocal(MultivariateFunction fcn, double[] p,
                                         double width, int numPoints) {
        double fP = fcn.evaluate(p);

        if (Double.isNaN(fP)) {
            throw new IllegalArgumentException("Center point must evaluate!");
        }

        // This point will remain fixed, the center of the search.
        double[] fixedP = new double[p.length];
        System.arraycopy(p, 0, fixedP, 0, p.length);

        // This point will move around randomly. If it ever has a lower
        // score than p, it will be copied into p (and returned).
        double[] pTemp = new double[p.length];
        System.arraycopy(p, 0, pTemp, 0, p.length);

        for (int i = 0; i < numPoints; i++) {
            randomPointAboutCenter(pTemp, fixedP, width);

            for (int h = 0; h < pTemp.length; h++) {
                if (pTemp[h] < fcn.getLowerBound(h)) continue;
                if (pTemp[h] > fcn.getUpperBound(h)) continue;
            }

            double f = fcn.evaluate(pTemp);

            if (f == Double.POSITIVE_INFINITY) {
                i++; continue;
            }

            if (f < fP) {
                System.arraycopy(pTemp, 0, p, 0, pTemp.length);
                TetradLogger.getInstance().log("optimization", "Cube width = " + width + " FML = " + f);
                return true;
            }
        }

        return false;
    }

    private void randomPointAboutCenter(double[] pTemp, double[] fixedP, double width) {
        for (int j = 0; j < pTemp.length; j++) {
            double v = getRandom().nextDouble();
            pTemp[j] = fixedP[j] + (-width / 2.0 + width * v);
        }
    }

    /**
     * Evaluates a fitting function for an array of parameters.
     *
     * @author Joseph Ramsey
     */
    static interface FittingFunction {

        /**
         * Returns the value of the function for the given array of parameter
         * values.
         */
        double evaluate(double[] argument);

        /**
         * Returns the number of parameters.
         */
        int getNumParameters();

        void setAvoidNegativeVariances(boolean avoidNegativeVariances);
    }

    /**
     * Wraps a Sem for purposes of calculating its fitting function for given
     * parameter values.
     *
     * @author Joseph Ramsey
     */
    static class SemFittingFunction implements RandomSearch.FittingFunction {

        /**
         * The wrapped Sem.
         */
        private final SemIm sem;
        private List<Parameter> freeParameters;
        private boolean avoidNegativeVariances = false;

        /**
         * Constructs a new CoefFittingFunction for the given Sem.
         */
        public SemFittingFunction(SemIm sem) {
            this.sem = sem;
            this.freeParameters = sem.getFreeParameters();
        }

        /**
         * Computes the maximum likelihood function value for the given                                   G
         * parameters values as given by the optimizer. These values are mapped
         * to parameter values.
         */
        public double evaluate(double[] parameters) {
            sem.setFreeParamValues(parameters);

            for (int i = 0; i < parameters.length; i++) {
                if (Double.isNaN(parameters[i]) || Double.isInfinite(parameters[i])) {
                    return Double.POSITIVE_INFINITY;
                }
            }

            double fml = sem.getScore();

            if (Double.isNaN(fml) || Double.isInfinite(fml)) {
                return Double.POSITIVE_INFINITY;
            }

            if (avoidNegativeVariances) {
                for (int i = 0; i < parameters.length; i++) {
                    if (freeParameters.get(i).getType() == ParamType.VAR && parameters[i] <= 0.0) {
                        return Double.POSITIVE_INFINITY;
                    }
                }
            }

            if (Double.isNaN(fml)) {
                return Double.POSITIVE_INFINITY;
            }

            if (fml < 0) {
                return Double.POSITIVE_INFINITY;
            }

            return fml;
        }

        /**
         * Returns the number of arguments. Required by the MultivariateFunction
         * interface.
         */
        public int getNumParameters() {
            return this.sem.getNumFreeParams();
        }

        @Override
        public void setAvoidNegativeVariances(boolean avoidNegativeVariances) {
            this.avoidNegativeVariances = avoidNegativeVariances;
        }
    }

    private RandomUtil getRandom() {
        return RandomUtil.getInstance();
    }
}



