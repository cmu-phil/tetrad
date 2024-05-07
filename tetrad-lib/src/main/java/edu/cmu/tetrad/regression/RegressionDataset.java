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

package edu.cmu.tetrad.regression;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.util.FastMath;

import java.util.Arrays;
import java.util.List;

/**
 * Implements a regression model from tabular continuous data.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class RegressionDataset implements Regression {

    /**
     * The data set.
     */
    private final Matrix data;

    /**
     * The variables.
     */
    private final List<Node> variables;

    /**
     * The significance level for determining which regressors are significant based on their p values.
     */
    private double alpha = 0.05;

    /**
     * The graph of significant regressors into the target.
     */
    private Graph graph;

    private int[] rows;
    private Vector res2;


    //============================CONSTRUCTORS==========================//

    /**
     * Constructs a linear regression model for the given tabular data set.
     *
     * @param data A rectangular data set, the relevant variables of which are continuous.
     */
    public RegressionDataset(DataSet data) {
        this.data = data.getDoubleData();
        this.variables = data.getVariables();
        setRows(new int[data.getNumRows()]);
        for (int i = 0; i < getRows().length; i++) getRows()[i] = i;
    }

    /**
     * <p>Constructor for RegressionDataset.</p>
     *
     * @param data      a {@link edu.cmu.tetrad.util.Matrix} object
     * @param variables a {@link java.util.List} object
     */
    public RegressionDataset(Matrix data, List<Node> variables) {
        this.data = data;
        this.variables = variables;
        setRows(new int[data.getNumRows()]);
        for (int i = 0; i < getRows().length; i++) getRows()[i] = i;
    }


    //===========================PUBLIC METHODS========================//

    /**
     * <p>regress.</p>
     *
     * @param target     an array of {@link double} objects
     * @param regressors an array of {@link double} objects
     * @return a {@link edu.cmu.tetrad.regression.RegressionResult} object
     */
    public static RegressionResult regress(double[] target, double[][] regressors) {
        int n = target.length;
        int k = regressors.length + 1;

        String[] regressorNames = new String[regressors.length];
        for (int i = 0; i < regressors.length; i++) {
            regressorNames[i] = "X" + (i + 1);
        }

        Matrix y = new Matrix(new double[][]{target}).transpose();
        Matrix x = new Matrix(regressors).transpose();

        Matrix xT = x.transpose();
        Matrix xTx = xT.times(x);
        Matrix xTxInv = xTx.inverse();
        Matrix xTy = xT.times(y);
        Matrix b = xTxInv.times(xTy);

        Matrix yHat = x.times(b);
        if (yHat.getNumColumns() == 0) yHat = y.like();

        Matrix res = y.minus(yHat); //  y.copy().assign(yHat, PlusMult.plusMult(-1));

        Vector _yHat = yHat.getColumn(0);
        Vector _res = res.getColumn(0);

        yHat.getNumColumns();

        //  y.copy().assign(yHat, PlusMult.plusMult(-1));

        double rss = RegressionDataset.rss(x, y, b);
        double se = FastMath.sqrt(rss / (n - k));
        double tss = RegressionDataset.tss(y);
        double r2 = 1.0 - (rss / tss);

        Vector sqErr = new Vector(x.getNumColumns());
        Vector t = new Vector(x.getNumColumns());
        Vector p = new Vector(x.getNumColumns());

        for (int i = 0; i < x.getNumColumns(); i++) {
            double _s = se * se * xTxInv.get(i, i);
            double _se = FastMath.sqrt(_s);
            double _t = b.get(i, 0) / _se;
            double _p = (1.0 - ProbUtils.tCdf(FastMath.abs(_t), n - k));

            sqErr.set(i, _se);
            t.set(i, _t);
            p.set(i, _p);
        }

        double[] bArray = b.getNumColumns() == 0 ? new double[0] : b.getColumn(0).toArray();
        double[] tArray = t.toArray();
        double[] pArray = p.toArray();
        double[] seArray = sqErr.toArray();


        return new RegressionResult(true, regressorNames, n,
                bArray, tArray, pArray, seArray, r2, rss, 0.05, _res);
    }

    /**
     * Calculates the residual sum of squares for parameter data x, actual values y, and regression coefficients
     * b--i.e., for each point in the data, the predicted value for that point is calculated, and then it is subtracted
     * from the actual value. The sum of the squares of these difference values over all points in the data is
     * calculated and returned.
     *
     * @param x the array of data.
     * @param y the target vector.
     * @param b the regression coefficients.
     * @return the residual sum of squares.
     */
    private static double rss(Matrix x, Matrix y, Matrix b) {
        double rss = 0.0;

        for (int i = 0; i < x.getNumRows(); i++) {
            double yH = 0.0;

            for (int j = 0; j < x.getNumColumns(); j++) {
                yH += b.get(j, 0) * x.get(i, j);
            }

            double d = y.get(i, 0) - yH;

            rss += d * d;
        }

        return rss;
    }

    private static double tss(Matrix y) {
        // first calculate the mean
        double mean = 0.0;

        for (int i = 0; i < y.getNumRows(); i++) {
            mean += y.get(i, 0);
        }

        mean /= y.getNumRows();

        double ssm = 0.0;

        for (int i = 0; i < y.getNumRows(); i++) {
            double d = mean - y.get(i, 0);
            ssm += d * d;
        }

        return ssm;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the alpha level for deciding which regressors are significant based on their p values.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * <p>Getter for the field <code>graph</code>.</p>
     *
     * @return This graph.
     */
    public Graph getGraph() {
        return this.graph;
    }

    //=======================PRIVATE METHODS================================//

    /**
     * Regresses the target on the given regressors.
     *
     * @param target     The target variable.
     * @param regressors The regressor variables.
     * @return The regression plane, specifying for each regressors its coefficeint, se, t, and p values, and specifying
     * the same for the constant.
     */
    public RegressionResult regress(Node target, List<Node> regressors) {
        int n = getRows().length;
        int k = regressors.size() + 1;

        int _target = this.variables.indexOf(target);
        int[] _regressors = new int[regressors.size()];

        for (int i = 0; i < regressors.size(); i++) {
            _regressors[i] = this.variables.indexOf(regressors.get(i));
            if (_regressors[i] == -1) {
                System.out.println();
            }
        }

        if (_target == -1) {
            System.out.println();
        }

        Matrix y = this.data.getSelection(getRows(), new int[]{_target}).copy();
        Matrix xSub = this.data.getSelection(getRows(), _regressors);

        Matrix x;

        if (regressors.size() > 0) {
            x = new Matrix(xSub.getNumRows(), xSub.getNumColumns() + 1);

            for (int i = 0; i < x.getNumRows(); i++) {
                for (int j = 0; j < x.getNumColumns(); j++) {
                    if (j == 0) {
                        x.set(i, 0, 1);
                    } else {
                        x.set(i, j, xSub.get(i, j - 1));
                    }
                }
            }
        } else {
            x = new Matrix(xSub.getNumRows(), xSub.getNumColumns());

            for (int i = 0; i < x.getNumRows(); i++) {
                for (int j = 0; j < x.getNumColumns(); j++) {
                    x.set(i, j, xSub.get(i, j));
                }
            }
        }

        Matrix xT = x.transpose();
        Matrix xTx = xT.times(x);
        Matrix xTxInv = xTx.inverse();
        Matrix xTy = xT.times(y);
        Matrix b = xTxInv.times(xTy);

        Matrix yHat = x.times(b);
        if (yHat.getNumColumns() == 0) yHat = y.like();

        Matrix res = y.minus(yHat); //  y.copy().assign(yHat, PlusMult.plusMult(-1));

        Vector _yHat = yHat.getColumn(0);
        Vector _res = res.getColumn(0);

        Matrix b2 = b.copy();
        Matrix yHat2 = x.times(b2);
        if (yHat.getNumColumns() == 0) yHat2 = y.like();

        Matrix res2 = y.minus(yHat2); //  y.copy().assign(yHat, PlusMult.plusMult(-1));
        this.res2 = res2.getColumn(0);

        double rss = RegressionDataset.rss(x, y, b);
        double se = FastMath.sqrt(rss / (n - k));
        double tss = RegressionDataset.tss(y);
        double r2 = 1.0 - (rss / tss);

        Vector sqErr = new Vector(x.getNumColumns());
        Vector t = new Vector(x.getNumColumns());
        Vector p = new Vector(x.getNumColumns());

        for (int i = 0; i < x.getNumColumns(); i++) {
            double _s = se * se * xTxInv.get(i, i);
            double _se = FastMath.sqrt(_s);
            double _t = b.get(i, 0) / _se;
            double _p = (1.0 - ProbUtils.tCdf(FastMath.abs(_t), n - k));

            sqErr.set(i, _se);
            t.set(i, _t);
            p.set(i, _p);
        }

        this.graph = createOutputGraph(target.getName(), x, regressors, p);

        String[] vNames = new String[regressors.size()];

        for (int i = 0; i < regressors.size(); i++) {
            vNames[i] = regressors.get(i).getName();
        }

        double[] bArray = b.getNumColumns() == 0 ? new double[0] : b.getColumn(0).toArray();
        double[] tArray = t.toArray();
        double[] pArray = p.toArray();
        double[] seArray = sqErr.toArray();


        return new RegressionResult(regressors.size() == 0, vNames, n,
                bArray, tArray, pArray, seArray, r2, rss, this.alpha, _res);
    }

    /**
     * <p>regress.</p>
     *
     * @param target     a {@link edu.cmu.tetrad.graph.Node} object
     * @param regressors a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link edu.cmu.tetrad.regression.RegressionResult} object
     */
    public RegressionResult regress(Node target, Node... regressors) {
        List<Node> _regressors = Arrays.asList(regressors);
        return regress(target, _regressors);
    }

    private Graph createOutputGraph(String target, Matrix x,
                                    List<Node> regressors, Vector p) {
        // Create output graph.
        Node targetNode = new GraphNode(target);

        Graph graph = new EdgeListGraph();
        graph.addNode(targetNode);

        for (int i = 0; i < x.getNumColumns(); i++) {
            String variableName = (i > 0) ? regressors.get(i - 1).getName() : "const";

            //Add a node and edge to the output graph for significant predictors:
            if (p.get(i) < this.alpha) {
                Node predictorNode = new GraphNode(variableName);
                graph.addNode(predictorNode);
                Edge newEdge = new Edge(predictorNode, targetNode,
                        Endpoint.TAIL, Endpoint.ARROW);
                graph.addEdge(newEdge);
            }
        }

        return graph;
    }

    /**
     * The rows in the data used for the regression.
     */
    private int[] getRows() {
        return this.rows;
    }

    /**
     * <p>Setter for the field <code>rows</code>.</p>
     *
     * @param rows an array of {@link int} objects
     */
    public void setRows(int[] rows) {
        this.rows = rows;
    }

    /**
     * <p>getResidualsWithoutFirstRegressor.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Vector} object
     */
    public Vector getResidualsWithoutFirstRegressor() {
        return this.res2;
    }
}



