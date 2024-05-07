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

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.util.FastMath;

import java.util.Arrays;
import java.util.List;

/**
 * Implements a regression model from correlations--that is, from a correlation matrix, a list of standard deviations,
 * and a list of means.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class RegressionCovariance implements Regression {

    /**
     * The correlation matrix.
     */
    private final CorrelationMatrix correlations;

    /**
     * 2 The standard deviations for the variable in <code>correlations</code>.
     */
    private final Vector sd;

    /**
     * The means for the variables in <code>correlations</code>. May be null.
     */
    private final Vector means;

    /**
     * The alpha level, determining which coefficients will be considered significant.
     */
    private double alpha = 0.05;

    /**
     * The graph of significant regressors into the target
     */
    private Graph graph;

    //=========================CONSTRUCTORS===========================//

    /**
     * Constructs a covariance-based regression model using the given covariance matrix, assuming that no means are
     * specified.
     *
     * @param covariances The covariance matrix.
     */
    public RegressionCovariance(ICovarianceMatrix covariances) {
        this(covariances, RegressionCovariance.zeroMeans(covariances.getDimension()));
    }

    /**
     * Constructs a covariance-based regression model, assuming that the covariance matrix and the means are as
     * specified.
     *
     * @param covariances The covariance matrix, for variables <V1,...,Vn>
     * @param means       A vector of means, for variables <V1,...,Vn>. May be null.
     */
    private RegressionCovariance(ICovarianceMatrix covariances, Vector means) {
        this(new CorrelationMatrix(covariances), RegressionCovariance.standardDeviations(covariances),
                means);
    }

    /**
     * Constructs a new covariance-based regression model, assuming the given correlations, standard deviations, and
     * means are all specified.
     *
     * @param correlations       The correlation matrix, for variables <V1,...,Vn>.
     * @param standardDeviations Standard deviations for variables <V1,..,Vn>. Must not be null.
     * @param means              3 for variables <V1,...,Vn>. May be null.
     */
    private RegressionCovariance(CorrelationMatrix correlations,
                                 Vector standardDeviations,
                                 Vector means) {
        if (correlations == null) {
            throw new NullPointerException();
        }

        if (standardDeviations == null || standardDeviations.size() != correlations.getDimension()) {
            throw new IllegalArgumentException();
        }

        if (means != null && means.size() != correlations.getDimension()) {
            throw new IllegalArgumentException();
        }

        this.correlations = correlations;
        this.sd = standardDeviations;
        this.means = means;
    }

    //===========================PUBLIC METHODS==========================//

    private static Vector zeroMeans(int numVars) {
        return new Vector(numVars);
    }

    private static Vector standardDeviations(ICovarianceMatrix covariances) {
        Vector standardDeviations = new Vector(covariances.getDimension());

        for (int i = 0; i < covariances.getDimension(); i++) {
            standardDeviations.set(i, FastMath.sqrt(covariances.getValue(i, i)));
        }

        return standardDeviations;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the cutoff for significance. Parameters with p values less than this will be labeled as significant.
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

    //===========================PRIVATE METHODS==========================//

    /**
     * Regresses the given target on the given regressors, yielding a regression plane, in which coefficients are given
     * for each regressor plus the constant (if means have been specified, that is, for the last), and se, t, and p
     * values are given for each regressor.
     *
     * @param target     The variable being regressed.
     * @param regressors The list of regressors.
     * @return the regression plane.
     */
    public RegressionResult regress(Node target, List<Node> regressors) {
        try {

            Matrix allCorrelations = this.correlations.getMatrix();

            List<Node> variables = this.correlations.getVariables();

            int yIndex = variables.indexOf(target);

            int[] xIndices = new int[regressors.size()];

            for (int i = 0; i < regressors.size(); i++) {
                xIndices[i] = variables.indexOf(regressors.get(i));

                if (xIndices[i] == -1) {
                    throw new NullPointerException("Can't find variable " + regressors.get(i) + " in this list: " + variables);
                }
            }

            Matrix rX = allCorrelations.getSelection(xIndices, xIndices);
            Matrix rY = allCorrelations.getSelection(xIndices, new int[]{yIndex});

            Matrix bStar = rX.inverse().times(rY);

            Vector b = new Vector(bStar.getNumRows() + 1);

            for (int k = 1; k < b.size(); k++) {
                double sdY = this.sd.get(yIndex);
                double sdK = this.sd.get(xIndices[k - 1]);
                b.set(k, bStar.get(k - 1, 0) * (sdY / sdK));
            }

            b.set(0, Double.NaN);

            if (this.means != null) {
                double b0 = this.means.get(yIndex);

                for (int i = 0; i < xIndices.length; i++) {
                    b0 -= b.get(i + 1) * this.means.get(xIndices[i]);
                }

                b.set(0, b0);
            }

            int[] allIndices = new int[1 + regressors.size()];
            allIndices[0] = yIndex;

            for (int i = 1; i < allIndices.length; i++) {
                allIndices[i] = variables.indexOf(regressors.get(i - 1));
            }

            Matrix r = allCorrelations.getSelection(allIndices, allIndices);
            Matrix rInv = r.inverse();

            int n = this.correlations.getSampleSize();
            int k = regressors.size() + 1;

            double vY = rInv.get(0, 0);
            double r2 = 1.0 - (1.0 / vY);
            double tss = n * this.sd.get(yIndex) * this.sd.get(yIndex); // Book says n - 1.
            double rss = tss * (1.0 - r2);
            double seY = FastMath.sqrt(rss / (double) (n - k));

            Vector sqErr = new Vector(allIndices.length);
            Vector t = new Vector(allIndices.length);
            Vector p = new Vector(allIndices.length);

            sqErr.set(0, Double.NaN);
            t.set(0, Double.NaN);
            p.set(0, Double.NaN);

            Matrix rxInv = rX.inverse();

            for (int i = 0; i < regressors.size(); i++) {
                double _r2 = 1.0 - (1.0 / rxInv.get(i, i));
                double _tss = n * this.sd.get(xIndices[i]) * this.sd.get(xIndices[i]);
                double _se = seY / FastMath.sqrt(_tss * (1.0 - _r2));

                double _t = b.get(i + 1) / _se;
                double _p = (1.0 - ProbUtils.tCdf(FastMath.abs(_t), n - k));

                sqErr.set(i + 1, _se);
                t.set(i + 1, _t);
                p.set(i + 1, _p);
            }

            // Graph
            this.graph = createGraph(target, allIndices, regressors, p);

            String[] vNames = createVarNamesArray(regressors);
            double[] bArray = b.toArray();
            double[] tArray = t.toArray();
            double[] pArray = p.toArray();
            double[] seArray = sqErr.toArray();

            return new RegressionResult(false, vNames, n,
                    bArray, tArray, pArray, seArray, r2, rss, this.alpha, null);
        } catch (SingularMatrixException e) {
            throw new RuntimeException("Singularity encountered when regressing " +
                                       LogUtilsSearch.getScoreFact(target, regressors));
        }
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

    private String[] createVarNamesArray(List<Node> regressors) {
        String[] vNames = getVarNamesArray(regressors);

        for (int i = 0; i < regressors.size(); i++) {
            vNames[i] = regressors.get(i).getName();
        }
        return vNames;
    }

    private String[] getVarNamesArray(List<Node> regressors) {
        return new String[regressors.size()];
    }

    private Graph createGraph(Node target, int[] allIndices, List<Node> regressors, Vector p) {
        Graph graph = new EdgeListGraph();
        graph.addNode(target);

        for (int i = 0; i < allIndices.length; i++) {
            String variableName = (i > 0) ? regressors.get(i - 1).getName() : "const";

            //Add a node and edge to the output graph for significant predictors:
            if (p.get(i) < this.alpha) {
                Node predictorNode = new GraphNode(variableName);
                graph.addNode(predictorNode);
                Edge newEdge = new Edge(predictorNode, target,
                        Endpoint.TAIL, Endpoint.ARROW);
                graph.addEdge(newEdge);
            }
        }

        return graph;
    }
}




