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
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;

/**
 * Implements a regression model from tabular continuous data.
 *
 * @author Joseph Ramsey
 */
public class RegressionDataset implements Regression {

    /**
     * The number formatter used for all numbers.
     */
    private NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    /**
     * The data set.
     */
    private TetradMatrix data;

    /**
     * The variables.
     */
    private List<Node> variables;

    /**
     * The significance level for determining which regressors are significant
     * based on their p values.
     */
    private double alpha = 0.05;

    /**
     * The graph of significant regressors into the target.
     */
    private Graph graph = null;

    private int[] rows;
    private TetradVector res2;

    //============================CONSTRUCTORS==========================//

    /**
     * Constructs a linear regression model for the given tabular data set.
     *
     * @param data A rectangular data set, the relevant variables of which
     *             are continuous.
     */
    public RegressionDataset(DataSet data) {
        this.data = data.getDoubleData();
        this.variables = data.getVariables();
        setRows(new int[data.getNumRows()]);
        for (int i = 0; i < getRows().length; i++) getRows()[i] = i;
    }

    public RegressionDataset(TetradMatrix data, List<Node> variables) {
        this.data = data;
        this.variables = variables;
        setRows(new int[data.rows()]);
        for (int i = 0; i < getRows().length; i++) getRows()[i] = i;
    }


    //===========================PUBLIC METHODS========================//

    /**
     * Sets the alpha level for deciding which regressors are significant
     * based on their p values.
     */
    public void setAlpha(double alpha) {
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
    public RegressionResult regress(Node target, List<Node> regressors) {
        int n = getRows().length;
        int k = regressors.size() + 1;

        int _target = variables.indexOf(target);
        int[] _regressors = new int[regressors.size()];

        for (int i = 0; i < regressors.size(); i++) {
            _regressors[i] = variables.indexOf(regressors.get(i));
            if (_regressors[i] == -1) {
                System.out.println();
            }
        }

        if (_target == -1) {
            System.out.println();
        }

        TetradMatrix y = data.getSelection(getRows(), new int[]{_target}).copy();
        TetradMatrix xSub = data.getSelection(getRows(), _regressors);

        TetradMatrix x;

        if (regressors.size() > 0) {
            x = new TetradMatrix(xSub.rows(), xSub.columns() + 1);

            for (int i = 0; i < x.rows(); i++) {
                for (int j = 0; j < x.columns(); j++) {
                    if (j == 0) {
                        x.set(i, j, 1);
                    } else {
                        x.set(i, j, xSub.get(i, j - 1));
                    }
                }
            }
        } else {
            x = new TetradMatrix(xSub.rows(), xSub.columns());

            for (int i = 0; i < x.rows(); i++) {
                for (int j = 0; j < x.columns(); j++) {
                    x.set(i, j, xSub.get(i, j));
                }
            }
        }

        TetradMatrix xT = x.transpose();
        TetradMatrix xTx = xT.times(x);
        TetradMatrix xTxInv = xTx.inverse();
        TetradMatrix xTy = xT.times(y);
        TetradMatrix b = xTxInv.times(xTy);

        TetradMatrix yHat = x.times(b);
        if (yHat.columns() == 0) yHat = y.like();

        TetradMatrix res = y.minus(yHat); //  y.copy().assign(yHat, PlusMult.plusMult(-1));

        TetradVector _yHat = yHat.getColumn(0);
        TetradVector _res = res.getColumn(0);

        TetradMatrix b2 = b.copy();
        TetradMatrix yHat2 = x.times(b2);
        if (yHat.columns() == 0) yHat2 = y.like();

        TetradMatrix res2 = y.minus(yHat2); //  y.copy().assign(yHat, PlusMult.plusMult(-1));
        this.res2 = res2.getColumn(0);

        double rss = rss(x, y, b);
        double se = Math.sqrt(rss / (n - k));
        double tss = tss(y);
        double r2 = 1.0 - (rss / tss);

        TetradVector sqErr = new TetradVector(x.columns());
        TetradVector t = new TetradVector(x.columns());
        TetradVector p = new TetradVector(x.columns());

        for (int i = 0; i < x.columns(); i++) {
            double _s = se * se * xTxInv.get(i, i);
            double _se = Math.sqrt(_s);
            double _t = b.get(i, 0) / _se;
            double _p = 2 * (1.0 - ProbUtils.tCdf(Math.abs(_t), n - k));

            sqErr.set(i, _se);
            t.set(i, _t);
            p.set(i, _p);
        }

        this.graph = createOutputGraph(target.getName(), x, regressors, p);

        String[] vNames = new String[regressors.size()];

        for (int i = 0; i < regressors.size(); i++) {
            vNames[i] = regressors.get(i).getName();
        }

        double[] bArray = b.columns() == 0 ? new double[0] : b.getColumn(0).toArray();
        double[] tArray = t.toArray();
        double[] pArray = p.toArray();
        double[] seArray = sqErr.toArray();


        return new RegressionResult(regressors.size() == 0, vNames, n,
                bArray, tArray, pArray, seArray, r2, rss, alpha, _yHat, _res);
    }

    public RegressionResult regress(Node target, Node... regressors) {
        List<Node> _regressors = Arrays.asList(regressors);
        return regress(target, _regressors);
    }

    //=======================PRIVATE METHODS================================//

    private Graph createOutputGraph(String target, TetradMatrix x,
                                    List<Node> regressors, TetradVector p) {
        // Create output graph.
        Node targetNode = new GraphNode(target);

        Graph graph = new EdgeListGraph();
        graph.addNode(targetNode);

        for (int i = 0; i < x.columns(); i++) {
            String variableName = (i > 0) ? regressors.get(i - 1).getName() : "const";

            //Add a node and edge to the output graph for significant predictors:
            if (p.get(i) < alpha) {
                Node predictorNode = new GraphNode(variableName);
                graph.addNode(predictorNode);
                Edge newEdge = new Edge(predictorNode, targetNode,
                        Endpoint.TAIL, Endpoint.ARROW);
                graph.addEdge(newEdge);
            }
        }

        return graph;
    }

    private String createResultString(int n, int k, double rss, double r2,
                                      TetradMatrix x, List<Node> regressors,
                                      TetradMatrix b, TetradVector se,
                                      TetradVector t, TetradVector p) {
        // Create result string.
        String rssString = nf.format(rss);
        String r2String = nf.format(r2);
        String summary = "\n REGRESSION RESULT";
        summary += "\n n = " + n + ", k = " + k + ", alpha = " + alpha + "\n";
        summary += " SSE = " + rssString + "\n";
        summary += " R^2 = " + r2String + "\n\n";
        summary += " VAR\tCOEF\tSE\tT\tP\n";

        for (int i = 0; i < x.columns(); i++) {
            // Note: the first column contains the regression constants.
            String variableName = (i > 0) ? regressors.get(i - 1).getName() : "const";

            summary += " " + variableName + "\t" + nf.format(b.get(i, 0)) +
                    "\t" + nf.format(se.get(i)) + "\t" + nf.format(t.get(i)) +
                    "\t" + nf.format(p.get(i)) + "\t" +
                    ((p.get(i) < alpha) ? "significant " : "") + "\n";
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
    private double rss(TetradMatrix x, TetradMatrix y, TetradMatrix b) {
        double rss = 0.0;

        for (int i = 0; i < x.rows(); i++) {
            double yH = 0.0;

            for (int j = 0; j < x.columns(); j++) {
                yH += b.get(j, 0) * x.get(i, j);
            }

            double d = y.get(i, 0) - yH;

            rss += d * d;
        }

        return rss;
    }

    private double tss(TetradMatrix y) {
        // first calculate the mean
        double mean = 0.0;

        for (int i = 0; i < y.rows(); i++) {
            mean += y.get(i, 0);
        }

        mean /= (double) (y.rows());

        double ssm = 0.0;

        for (int i = 0; i < y.rows(); i++) {
            double d = mean - y.get(i, 0);
            ssm += d * d;
        }

        return ssm;
    }

    /**
     * The rows in the data used for the regression.
     */
    public int[] getRows() {
        return rows;
    }

    public void setRows(int[] rows) {
        this.rows = rows;
    }

    public TetradVector getResidualsWithoutFirstRegressor() {
        return res2;
    }
}



