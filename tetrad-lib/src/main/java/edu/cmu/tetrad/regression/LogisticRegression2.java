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

import edu.cmu.tetrad.data.ColtDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradSerializable;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

/**
 * Implements a logistic regression algorithm based on a Javascript
 * implementation by John Pezzullo.  That implementation together with a
 * description of logistic regression and some examples appear on his web page
 * http://members.aol.com/johnp71/logistic.html
 * <p>
 * See also  Applied Logistic Regression, by D.W. Hosmer and S. Lemeshow. 1989,
 * John Wiley & Sons, New York which Pezzullo references.  In particular see
 * pages 27-29.
 *
 * @author Joseph Ramsey
 */
public class LogisticRegression2 implements TetradSerializable {
    static final long serialVersionUID = 23L;
    private int[] rows;
    private String targetName;
    private List<String> regressorNames;
    private double likelihood;

    /**
     * A mixed data set. The targets of regresson must be binary. Regressors must be continuous or binary.
     * Other variables don't matter.
     */
    public LogisticRegression2(DataSet dataSet) {
        setRows(new int[dataSet.getNumRows()]);
        for (int i = 0; i < getRows().length; i++) getRows()[i] = i;
    }

    public LogisticRegression2() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static LogisticRegression2 serializableInstance() {
        return new LogisticRegression2(ColtDataSet.serializableInstance());
    }

    // I am going to try to maximize the liklehood function directly using the Powell Estimator.
    public void regress(int[] target, int numValues, double[][] regressors) {
        try {
            int numParams = regressors.length + 1;

            double[] coefficients = new double[(numValues - 1) * numParams];

            // Apparently this needs to be fairly loose.
            int tolerance = 250;
            MultivariateOptimizer search = new PowellOptimizer(tolerance, tolerance);

            PointValuePair pair = search.optimize(
                    new InitialGuess(coefficients),
                    new ObjectiveFunction(new FittingFunction(target, regressors)),
                    GoalType.MAXIMIZE,
                    new MaxEval(1000000)
            );

            this.likelihood = pair.getValue();
        } catch (TooManyEvaluationsException e) {
            e.printStackTrace();
            this.likelihood = Double.NaN;
        }
    }

    /**
     * Wraps the SEM maximum likelihood fitting function for purposes of being
     * evaluated using the PAL ConjugateDirection optimizer.
     *
     * @author Joseph Ramsey
     */
    private static class FittingFunction implements MultivariateFunction {

        private int[] target;
        private double[][] regressors;

        /**
         * Constructs a new CoefFittingFunction for the given Sem.
         */
        public FittingFunction(int[] target, double[][] regressors) {
            this.target = target;
            this.regressors = regressors;
        }

        public double value(double[] parameters) {
            double likelihood = 0.0;

            for (int i = 0; i < target.length; i++) {
                if (parameters.length % (regressors.length + 1) != 0) {
                    throw new IllegalArgumentException("# params should be a multiple of # regressors + 1");
                }

                int v = parameters.length / (regressors.length + 1);

                double[] e = new double[v];
                double sum = 0;

                for (int k = 0; k < v; k++) {
                    e[k] = getE(i, k, parameters, regressors);
                    sum += e[k];
                }

                double logprob = 0;

                for (int k = 0; k < v; k++) {
                    if (target[i] == k) {
                        logprob += e[k];
                    }
                }

                logprob -= Math.log(1 + Math.exp(sum));
                likelihood += logprob;
            }

            return likelihood;
        }

        private double getE(int i, int g, double[] parameters, double[][] X) {
            int offset = g * (X.length + 1);

            double e = 0.0;

            for (int j = 0; j < X.length; j++) {
                e += parameters[offset + j] * X[j][i];
            }

            e += parameters[X.length];

            return e;
        }
    }

    public double getLikelihood() {
        return this.likelihood;
    }

    /**
     * The rows in the data used for regression.
     */
    private int[] getRows() {
        return rows;
    }

    public void setRows(int[] rows) {
        this.rows = rows;
    }

    //================================== Public Methods =======================================//

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public List<String> getRegressorNames() {
        return regressorNames;
    }

    public void setRegressorNames(List<String> regressorNames) {
        this.regressorNames = regressorNames;
    }
}







