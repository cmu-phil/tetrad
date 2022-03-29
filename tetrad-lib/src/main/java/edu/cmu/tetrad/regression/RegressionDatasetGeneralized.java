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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Vector;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;

/**
 * Implements a regression model from tabular continuous data.
 *
 * @author Joseph Ramsey
 */
public class RegressionDatasetGeneralized implements Regression {

    /**
     * The number formatter used for all numbers.
     */
    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    /**
     * The data set.
     */
    private final Matrix data;

    /**
     * The variables.
     */

    private final List<Node> variables;

    /**
     * The significance level for determining which regressors are significant
     * based on their p values.
     */
    private double alpha = 0.05;

    /**
     * The graph of significant regressors into the target.
     */
    private final Graph graph = null;

    //============================CONSTRUCTORS==========================//

    /**
     * Constructs a linear regression model for the given tabular data set.
     *
     * @param data A rectangular data set, the relevant variables of which
     *             are continuous.
     */
    public RegressionDatasetGeneralized(final DataSet data) {
        this.data = data.getDoubleData();
        this.variables = data.getVariables();
    }

    public RegressionDatasetGeneralized(final Matrix data, final List<Node> variables) {
        this.data = data;
        this.variables = variables;
    }

    //===========================PUBLIC METHODS========================//

    /**
     * Sets the alpha level for deciding which regressors are significant
     * based on their p values.
     */
    public void setAlpha(final double alpha) {
        this.alpha = alpha;
    }

    /**
     * @return This graph.
     */
    public Graph getGraph() {
        return this.graph;
    }

    /**
     * Regresses the target on the given regressors.
     *
     * @param target     The target variable.
     * @param regressors The regressor variables.
     * @return The regression plane, specifying for each regressors its
     * coefficeint, se, t, and p values, and specifying the same for the
     * constant.
     */
    public RegressionResult regress(final Node target, final List<Node> regressors) {
        final int n = this.data.rows();
        final int k = regressors.size() + 1;

        final int _target = this.variables.indexOf(target);
        final int[] _regressors = new int[regressors.size()];

        for (int i = 0; i < regressors.size(); i++) {
            _regressors[i] = this.variables.indexOf(regressors.get(i));
        }

        final int[] rows = new int[this.data.rows()];
        for (int i = 0; i < rows.length; i++) rows[i] = i;

//        TetradMatrix y = data.viewSelection(rows, new int[]{_target}).copy();
        final Matrix xSub = this.data.getSelection(rows, _regressors);


//        TetradMatrix y = data.subsetColumns(Arrays.asList(target)).getDoubleData();
//        RectangularDataSet rectangularDataSet = data.subsetColumns(regressors);
//        TetradMatrix xSub = rectangularDataSet.getDoubleData();
        final Matrix X = new Matrix(xSub.rows(), xSub.columns() + 1);

        for (int i = 0; i < X.rows(); i++) {
            for (int j = 0; j < X.columns(); j++) {
                if (j == 0) {
                    X.set(i, 0, 1);
                } else {
                    X.set(i, j, xSub.get(i, j - 1));
                }
            }
        }

//        for (int i = 0; i < zList.size(); i++) {
//            zCols[i] = getVariable().indexOf(zList.get(i));
//        }

//        int[] zRows = new int[data.rows()];
//        for (int i = 0; i < data.rows(); i++) {
//            zRows[i] = i;
//        }

        final Vector y = this.data.getColumn(_target);
        final Matrix Xt = X.transpose();
        final Matrix XtX = Xt.times(X);
        final Matrix G = XtX.inverse();

        final Matrix GXt = G.times(Xt);

        final Vector b = GXt.times(y);

        final Vector yPred = X.times(b);

//        TetradVector xRes = yPred.copy().assign(y, Functions.minus);
        final Vector xRes = yPred.minus(y);

        final double rss = rss(X, y, b);
        final double se = Math.sqrt(rss / (n - k));
        final double tss = tss(y);
        final double r2 = 1.0 - (rss / tss);

//        TetradVector sqErr = TetradVector.instance(y.columns());
//        TetradVector t = TetradVector.instance(y.columns());
//        TetradVector p = TetradVector.instance(y.columns());
//
//        for (int i = 0; i < 1; i++) {
//            double _s = se * se * xTxInv.get(i, i);
//            double _se = Math.sqrt(_s);
//            double _t = b.get(i) / _se;
//            double _p = (1.0 - ProbUtils.tCdf(Math.abs(_t), n - k));
//
//            sqErr.set(i, _se);
//            t.set(i, _t);
//            p.set(i, _p);
//        }
//
//        this.graph = createOutputGraph(target.getNode(), y, regressors, p);
//
        final String[] vNames = new String[regressors.size()];

        for (int i = 0; i < regressors.size(); i++) {
            vNames[i] = regressors.get(i).getName();
        }
//
//        double[] bArray = b.toArray();
//        double[] tArray = t.toArray();
//        double[] pArray = p.toArray();
//        double[] seArray = sqErr.toArray();


        return new RegressionResult(false, vNames, n,
                b.toArray(), new double[0], new double[0], new double[0], r2, rss, this.alpha, yPred, xRes);
    }

    public RegressionResult regress(final Node target, final Node... regressors) {
        final List<Node> _regressors = Arrays.asList(regressors);
        return regress(target, _regressors);
    }

    //=======================PRIVATE METHODS================================//

    private Graph createOutputGraph(final String target, final Matrix x,
                                    final List<Node> regressors, final Vector p) {
        // Create output graph.
        final Node targetNode = new GraphNode(target);

        final Graph graph = new EdgeListGraph();
        graph.addNode(targetNode);

        for (int i = 0; i < x.columns(); i++) {
            final String variableName = (i > 0) ? regressors.get(i - 1).getName() : "const";

            //Add a node and edge to the output graph for significant predictors:
            if (p.get(i) < this.alpha) {
                final Node predictorNode = new GraphNode(variableName);
                graph.addNode(predictorNode);
                final Edge newEdge = new Edge(predictorNode, targetNode,
                        Endpoint.TAIL, Endpoint.ARROW);
                graph.addEdge(newEdge);
            }
        }

        return graph;
    }

    private String createResultString(final int n, final int k, final double rss, final double r2,
                                      final Matrix x, final List<Node> regressors,
                                      final Matrix b, final Vector se,
                                      final Vector t, final Vector p) {
        // Create result string.
        final String rssString = this.nf.format(rss);
        final String r2String = this.nf.format(r2);
        String summary = "\n REGRESSION RESULT";
        summary += "\n n = " + n + ", k = " + k + ", alpha = " + this.alpha + "\n";
        summary += " SSE = " + rssString + "\n";
        summary += " R^2 = " + r2String + "\n\n";
        summary += " VAR\tCOEF\tSE\tT\tP\n";

        for (int i = 0; i < x.columns(); i++) {
            // Note: the first column contains the regression constants.
            final String variableName = (i > 0) ? regressors.get(i - 1).getName() : "const";

            summary += " " + variableName + "\t" + this.nf.format(b.get(i, 0)) +
                    "\t" + this.nf.format(se.get(i)) + "\t" + this.nf.format(t.get(i)) +
                    "\t" + this.nf.format(p.get(i)) + "\t" +
                    ((p.get(i) < this.alpha) ? "significant " : "") + "\n";
        }
        return summary;
    }

    /**
     * Calculates the residual sum of squares for parameter data x, actual
     * values y, and regression coefficients b--i.e., for each point in the
     * data, the predicted value for that point is calculated, and then it is
     * subtracted from the actual value. The sum of the squares of these
     * difference values over all points in the data is calculated and
     * returned.
     *
     * @param x the array of data.
     * @param y the target vector.
     * @param b the regression coefficients.
     * @return the residual sum of squares.
     */
    private double rss(final Matrix x, final Vector y, final Vector b) {
        double rss = 0.0;

        for (int i = 0; i < x.rows(); i++) {
            double yH = 0.0;

            for (int j = 0; j < x.columns(); j++) {
                yH += b.get(j) * x.get(i, j);
            }

            final double d = y.get(i) - yH;

            rss += d * d;
        }

        return rss;
    }

    private double tss(final Vector y) {
        // first calculate the mean
        double mean = 0.0;

        for (int i = 0; i < y.size(); i++) {
            mean += y.get(i);
        }

        mean /= y.size();

        double ssm = 0.0;

        for (int i = 0; i < y.size(); i++) {
            final double d = mean - y.get(i);
            ssm += d * d;
        }

        return ssm;
    }
}



