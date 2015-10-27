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
import pal.math.ConjugateDirectionSearch;
import pal.math.MultivariateFunction;
import pal.math.OrthogonalHints;

import java.util.List;

/**
 * Optimizes a SEM using the ConjugateDirectionSearch class in the PAL library.
 *
 * @author Ricardo Silva
 * @author Joseph Ramsey
 */
public class SemOptimizerPalCdsGls implements SemOptimizer {
    static final long serialVersionUID = 23L;

    /**
     * Absolute tolerance of function value.
     */
    private static final double FUNC_TOLERANCE = 1.0e-4;

    /**
     * Absolute tolerance of each parameter.
     */
    private static final double PARAM_TOLERANCE = 1.0e-3;
    private int numRestarts = 1;

    //=========================CONSTRUCTORS============================//

    /**
     * Blank constructor.
     */
    public SemOptimizerPalCdsGls() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see edu.cmu.tetradapp.util.TetradSerializableUtils
     */
    public static SemOptimizerCds serializableInstance() {
        return new SemOptimizerCds();
    }

    //=========================PUBLIC METHODS==========================//

    /**
     * Optimizes the fitting function for the SEM.
     */
    public void optimize(SemIm semIm) {
        if (DataUtils.containsMissingValue(semIm.getSampleCovar())) {
            throw new IllegalArgumentException("Please remove or impute missing values.");
        }

        if (numRestarts < 1) numRestarts = 1;


//        new SemOptimizerEm().optimize(semIm);

        // Optimize the semIm. Note that the the covariance matrix of the
        // sample data is made available to the following CoefFittingFunction.
        double min = Double.POSITIVE_INFINITY;
        SemIm _sem = semIm;

        for (int count = 0; count < numRestarts + 1; count++) {
            System.out.println("Trial " + (count + 1));
            SemIm _sem2 = new SemIm(semIm);

            List<Parameter> freeParameters = _sem2.getFreeParameters();

            double[] p = new double[freeParameters.size()];

            for (int i = 0; i < freeParameters.size(); i++) {
                if (freeParameters.get(i).getType() == ParamType.VAR) {
                    p[i] = RandomUtil.getInstance().nextUniform(0, 1);
                } else {
                    p[i] = RandomUtil.getInstance().nextUniform(-1, 1);
                }
            }

            _sem2.setFreeParamValues(p);

            optimize2(_sem2);

            double chisq = _sem2.getChiSquare();
            System.out.println("chisq = " + chisq);

            if (chisq < min) {
                min = chisq;
                _sem = _sem2;
            }
        }

        for (Parameter param : semIm.getFreeParameters()) {
            try {
                Node nodeA = param.getNodeA();
                Node nodeB = param.getNodeB();

                Node _nodeA = _sem.getVariableNode(nodeA.getName());
                Node _nodeB = _sem.getVariableNode(nodeB.getName());

                double value = _sem.getParamValue(_nodeA, _nodeB);
                semIm.setParamValue(param, value);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void setNumRestarts(int numRestarts) {
        this.numRestarts = numRestarts;
    }

    @Override
    public int getNumRestarts() {
        return numRestarts;
    }

    public void optimize2(SemIm semIm) {
        ConjugateDirectionSearch search = new ConjugateDirectionSearch();
//        search.step = 10.0;
        search.optimize(fittingFunction(semIm),
                semIm.getFreeParamValues(), FUNC_TOLERANCE, PARAM_TOLERANCE);
    }

    public String toString() {
        return "Sem Optimizer PAL CDS";
    }

    private PalFittingFunction fittingFunction(SemIm sem) {
        return new PalFittingFunction(sem);
    }

    /**
     * Wraps the SEM maximum likelihood fitting function for purposes of being
     * evaluated using the PAL ConjugateDirection optimizer.
     *
     * @author Joseph Ramsey
     */
    static class PalFittingFunction implements MultivariateFunction {

        /**
         * The wrapped Sem.
         */
        private final SemIm sem;

        private List<Parameter> freeParameters;

        /**
         * Constructs a new CoefFittingFunction for the given Sem.
         */
        public PalFittingFunction(SemIm sem) {
            this.sem = sem;
            this.freeParameters = sem.getFreeParameters();
        }

        /**
         * Computes the maximum likelihood function value for the given
         * parameter values as given by the optimizer. These values are mapped to
         * parameter values.
         */
        public double evaluate(final double[] parameters) {
            sem.setFreeParamValues(parameters);

            for (int i = 0; i < parameters.length; i++) {
                if (Double.isNaN(parameters[i]) || Double.isInfinite(parameters[i])) {
                    return 100000;
                }
            }

            double fml = sem.getScore();

            if (Double.isNaN(fml) || Double.isInfinite(fml)) {
                return 100000;
            }

            if (true) {
                for (int i = 0; i < parameters.length; i++) {
                    if (freeParameters.get(i).getType() == ParamType.VAR && parameters[i] <= 0.0) {
                        return 100000;
                    }
                }
            }

            if (Double.isNaN(fml)) {
                return 100000;
            }

            if (fml < 0) {
                return 100000;
            }

            return fml;
        }

        /**
         * Returns the number of arguments. Required by the MultivariateFunction
         * interface.
         */
        public int getNumArguments() {
            return this.sem.getNumFreeParams();
        }

        /**
         * Returns the lower bound of argument n. Required by the
         * MultivariateFunction interface.
         */
        public double getLowerBound(final int n) {
            Parameter param = this.sem.getFreeParameters().get(n);
            return (param.getType() == ParamType.COEF ||
                    param.getType() == ParamType.COVAR) ? -10000.0 : 0.0001;
        }

        /**
         * Returns the upper bound of argument n. Required by the
         * MultivariateFunction interface.
         */
        public double getUpperBound(final int n) {
            return 10000.0;
        }

        public OrthogonalHints getOrthogonalHints() {
            return null;
        }
    }
}










