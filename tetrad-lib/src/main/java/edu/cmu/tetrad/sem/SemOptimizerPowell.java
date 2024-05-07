///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;

import java.io.Serial;
import java.util.List;

/**
 * Optimizes a SEM using Powell's method from the Apache library.
 *
 * @author Ricardo Silva
 * @author josephramsey
 * @version $Id: $Id
 */
public class SemOptimizerPowell implements SemOptimizer {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The number of restarts.
     */
    private int numRestarts;

    /**
     * Blank constructor.
     */
    public SemOptimizerPowell() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.sem.SemOptimizerPowell} object
     */
    public static SemOptimizerPowell serializableInstance() {
        return new SemOptimizerPowell();
    }


    /**
     * {@inheritDoc}
     */
    public void optimize(SemIm semIm) {
        double min = Double.POSITIVE_INFINITY;
        double[] point = null;

        for (int count = 0; count < this.numRestarts + 1; count++) {
//            System.out.println("Trial " + (count + 1));
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

            MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);
            PointValuePair pair = search.optimize(
                    new InitialGuess(_sem2.getFreeParamValues()),
                    new ObjectiveFunction(fittingFunction(semIm)),
                    GoalType.MINIMIZE,
                    new MaxEval(100000)
            );

            double chisq = _sem2.getChiSquare();
//            System.out.println("chisq = " + chisq);

            if (chisq < min) {
                min = chisq;
                point = pair.getPoint();
            }
        }

        if (point == null) {
            throw new NullPointerException("Point could not be found.");
        }

        System.arraycopy(point, 0, semIm.getFreeParamValues(), 0, point.length);
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "Sem Optimizer PAL Powell";
    }

    private FittingFunction fittingFunction(SemIm sem) {
        return new FittingFunction(sem);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumRestarts() {
        return this.numRestarts;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNumRestarts(int numRestarts) {
        this.numRestarts = numRestarts;
    }

    /**
     * Wraps the SEM maximum likelihood fitting function for purposes of being evaluated using the PAL
     * ConjugateDirection optimizer.
     *
     * @author josephramsey
     */
    static class FittingFunction implements MultivariateFunction {

        /**
         * The wrapped Sem.
         */
        private final SemIm sem;

        private final List<Parameter> freeParameters;

        /**
         * Constructs a new CoefFittingFunction for the given Sem.
         */
        public FittingFunction(SemIm sem) {
            this.sem = sem;
            this.freeParameters = sem.getFreeParameters();
        }


        /**
         * Computes the maximum likelihood function value for the given parameter values as given by the optimizer.
         * These values are mapped to parameter values.
         */

        @Override
        public double value(double[] parameters) {
            for (double parameter : parameters) {
                if (Double.isNaN(parameter) || Double.isInfinite(parameter)) {
                    return 100000;
                }
            }

            for (int i = 0; i < parameters.length; i++) {
                if (this.freeParameters.get(i).getType() == ParamType.VAR && parameters[i] <= 0.0) {
                    return 100000;
                }
            }

            this.sem.setFreeParamValues(parameters);

            double fml = this.sem.getScore();

            if (Double.isNaN(fml) || Double.isInfinite(fml)) {
                return 100000;
            }

            if (fml < 0) {
                return 100000;
            }

            return fml;
        }
    }

}











