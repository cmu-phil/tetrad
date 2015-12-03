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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.List;

/**
 * Optimizes a SEM by randomly selecting points in cubes of decreasing size about
 * a given point.
 *
 * @author Joseph Ramsey
 */
public class SemOptimizerScattershot implements SemOptimizer {
    static final long serialVersionUID = 23L;
    private int numRestarts;

    //=============================CONSTRUCTORS=========================//

    /**
     * Blank constructor.
     */
    public SemOptimizerScattershot() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static SemOptimizerScattershot serializableInstance() {
        return new SemOptimizerScattershot();
    }

    //==============================PUBLIC METHODS========================//

    /**
     * Optimizes the fitting function of the given Sem using the Powell method
     * from Numerical Recipes by adjusting the freeParameters of the Sem.
     */
    public void optimize(SemIm semIm) {
        TetradMatrix sampleCovar = semIm.getSampleCovar();

        if (sampleCovar == null) {
            throw new NullPointerException("Sample covar has not been set.");
        }

        if (DataUtils.containsMissingValue(sampleCovar)) {
            throw new IllegalArgumentException("Please remove or impute missing values.");
        }

        if (DataUtils.containsMissingValue(sampleCovar)) {
            throw new IllegalArgumentException("Please remove or impute missing values.");
        }

        if (numRestarts < 1) numRestarts = 1;

        TetradLogger.getInstance().log("info", "Trying EM...");
        TetradLogger.getInstance().log("info", "Trying scattershot...");

        double min = Double.POSITIVE_INFINITY;
        SemIm _sem = null;

        // With local search on points in the width 1 iteration, multiple iterations of the whole search
        // doesn't seem necessary.
        for (int i = 0; i < numRestarts + 1; i++) {
            TetradLogger.getInstance().log("details", "Trial " + (i + 1));
//            System.out.println("Trial " + (i + 1));
            SemIm _sem2 = new SemIm(semIm);
            optimize2(_sem2);
            double chisq = _sem2.getChiSquare();

            if (Math.abs(chisq) < min) {
                min = Math.abs(chisq);
                _sem = _sem2;
            }
        }

        if (_sem == null) {
            throw new NullPointerException("Minimal score SEM could not be found.");
        }

//        new SemOptimizerPalCds().optimize2(_sem);

        for (Parameter param : semIm.getFreeParameters()) {
            Node nodeA = param.getNodeA();
            Node nodeB = param.getNodeB();

            Node _nodeA = _sem.getVariableNode(nodeA.getName());
            Node _nodeB = _sem.getVariableNode(nodeB.getName());

            double value = _sem.getParamValue(_nodeA, _nodeB);
            semIm.setParamValue(param, value);
        }


//        optimize2(semIm);
    }

    @Override
    public void setNumRestarts(int numRestarts) {
        this.numRestarts = numRestarts;
    }

    @Override
    public int getNumRestarts() {
        return numRestarts;
    }

    public String toString() {
        return "Sem Optimizer Scattershot";
    }

    private void optimize2(SemIm semIm) {
        FittingFunction f = new SemFittingFunction(semIm);

        double[] p = semIm.getFreeParamValues();

        f.setAvoidNegativeVariances(true);
        iterateFindLowerRandom(f, p, 1.0, 1500);
        iterateFindLowerRandom(f, p, 0.5, 500);
        iterateFindLowerRandom(f, p, 0.25, 500);
        iterateFindLowerRandom(f, p, 0.1, 500);
        iterateFindLowerRandom(f, p, 0.1, 500);
        iterateFindLowerRandom(f, p, 0.05, 500);
        iterateFindLowerRandom(f, p, 0.01, 500);
        iterateFindLowerRandom(f, p, 0.005, 50);
        iterateFindLowerRandom(f, p, 0.001, 50);
        iterateFindLowerRandom(f, p, 0.0005, 50);
        iterateFindLowerRandom(f, p, 0.0001, 50);

        semIm.setFreeParamValues(p);
    }

    private void iterateFindLowerRandom(FittingFunction fcn, double[] p,
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
     * @return true iff a new point was found with a lower score.
     */
    private boolean findLowerRandom(FittingFunction fcn, double[] p,
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
            double f = fcn.evaluate(pTemp);

            if (f == Double.POSITIVE_INFINITY) {
                i--;
                continue;
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

    private boolean findLowerRandomLocal(FittingFunction fcn, double[] p,
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
            double f = fcn.evaluate(pTemp);

            if (f == Double.POSITIVE_INFINITY) {
                i++;
                continue;
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
     * Evaluates a fitting function for an array of freeParameters.
     *
     * @author Joseph Ramsey
     */
    interface FittingFunction {

        /**
         * @return the value of the function for the given array of parameter
         * values.
         */
        double evaluate(double[] argument);

        void setAvoidNegativeVariances(boolean avoidNegativeVariances);
    }

    /**
     * Wraps a Sem for purposes of calculating its fitting function for given
     * parameter values.
     *
     * @author Joseph Ramsey
     */
    static class SemFittingFunction implements SemOptimizerScattershot.FittingFunction {

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
         * freeParameters values as given by the optimizer. These values are mapped
         * to parameter values.
         */
        public double evaluate(double[] parameters) {
            sem.setFreeParamValues(parameters);

            for (double parameter : parameters) {
                if (Double.isNaN(parameter) || Double.isInfinite(parameter)) {
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

        @Override
        public void setAvoidNegativeVariances(boolean avoidNegativeVariances) {
            this.avoidNegativeVariances = avoidNegativeVariances;
        }
    }

    private RandomUtil getRandom() {
        return RandomUtil.getInstance();
    }
}



