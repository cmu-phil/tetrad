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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.DiscreteBicScore;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates some scores for Bayes nets as a whole.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class BayesProperties {

    /**
     * The data set.
     */
    private final DataSet dataSet;

    /**
     * The variables.
     */
    private final List<Node> variables;

    /**
     * The sample size.
     */
    private final int sampleSize;

    /**
     * The number of categories for each variable.
     */
    private final int[] numCategories;

    /**
     * The chi-squared statistic.
     */
    private double chisq;

    /**
     * The degrees of freedom.
     */
    private double dof;

    /**
     * The BIC.
     */
    private double bic;

    /**
     * The likelihood.
     */
    private double likelihood;

    /**
     * Constructs a new BayesProperties object for the given data set.
     *
     * @param dataSet The data set.
     */
    public BayesProperties(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;

        int[][] data;
        if (dataSet instanceof BoxDataSet) {
            DataBox dataBox = ((BoxDataSet) dataSet).getDataBox();

            this.variables = dataSet.getVariables();

            VerticalIntDataBox box = new VerticalIntDataBox(dataBox);

            box.getVariableVectors();
        } else {
            data = new int[dataSet.getNumColumns()][];
            this.variables = dataSet.getVariables();

            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                data[j] = new int[dataSet.getNumRows()];

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    data[j][i] = dataSet.getInt(i, j);
                }
            }
        }
        this.sampleSize = dataSet.getNumRows();

        List<Node> variables = dataSet.getVariables();
        this.numCategories = new int[variables.size()];
        for (int i = 0; i < variables.size(); i++) {
            DiscreteVariable variable = getVariable(i);

            if (variable != null) {
                this.numCategories[i] = variable.getNumCategories();
            }
        }
    }

    private static int getRowIndex(int[] dim, int[] values) {
        int rowIndex = 0;
        for (int i = 0; i < dim.length; i++) {
            rowIndex *= dim[i];
            rowIndex += values[i];
        }
        return rowIndex;
    }

    /**
     * Calculates the p-value of the graph with respect to the given data, against
     * the complete model as an alternative.
     *
     * @param graph0 The model to test.
     * @return The p-value.
     */
    public LikelihoodRet getLikelihoodRatioP(Graph graph0) {
        Graph graph1 = GraphUtils.completeGraph(graph0);

        Ret r1 = getLikelihood(graph1);
        Ret r0 = getLikelihood(graph0);

        this.likelihood = r0.getLik();

        double lDiff = r0.getLik() - r1.getLik();
        System.out.println("lDiff = " + lDiff);

        int nDiff = r0.getDof() - r1.getDof();
        System.out.println("nDiff = " + nDiff);

        double chisq = 2.0 * lDiff;

        this.chisq = chisq;
        this.dof = nDiff;

        int N = this.dataSet.getNumRows();
        this.bic = 2 * r0.getLik() - r0.getDof() * FastMath.log(N);
        System.out.println("bic = " + this.bic);

        System.out.println("chisq = " + chisq);
        System.out.println("dof = " + (double) nDiff);

        double p = 1.0 - new ChiSquaredDistribution(nDiff).cumulativeProbability(chisq);

        System.out.println("p = " + p);

        LikelihoodRet _ret = new LikelihoodRet();
        _ret.p = p;
        _ret.bic = bic;
        _ret.chiSq = chisq;
        _ret.dof = dof;

        return _ret;
    }

    /**
     * Call after calling getLikelihoodP().
     *
     * @return The chi-squared statistic.
     */
    public double getChisq() {
        return this.chisq;
    }

    /**
     * Call after calling getLikelihoodP().
     *
     * @return The degrees of freedom.
     */
    public double getDof() {
        return this.dof;
    }

    /**
     * Call after calling getLikelihoodP().
     *
     * @return The BIC.
     */
    public double getBic() {
        return this.bic;
    }

    /**
     * Call after calling getLikelihoodP().
     *
     * @return The likelihood.
     */
    public double getLikelihood() {
        return this.likelihood;
    }

    /**
     * Returns the sample size.
     *
     * @return The sample size.
     */
    public int getSampleSize() {
        return this.sampleSize;
    }

    /**
     * Returns the variable with the given name (assumed the target).
     *
     * @param targetName The name of the target variable.
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getVariable(String targetName) {
        for (Node node : this.variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }

        return null;
    }

    private int getDof(Graph graph) {
        graph = GraphUtils.replaceNodes(graph, this.dataSet.getVariables());
        BayesPm pm = new BayesPm(graph);
        BayesIm im = new MlBayesEstimator().estimate(pm, this.dataSet);

        int numParams = 0;

        for (int j = 0; j < im.getNumNodes(); j++) {
            int numColumns = im.getNumColumns(j);
            int numRows = im.getNumRows(j);
            numParams += (numColumns - 1) * numRows;
        }

        return numParams;
    }

    private Ret getLikelihood(Graph graph) {
        double lik = 0.0;
        int dof = 0;

        for (Node node : graph.getNodes()) {
            List<Node> parents = new ArrayList<>(graph.getParents(node));

            int i = this.variables.indexOf(getVariable(node.getName()));

            int[] z = new int[parents.size()];

            for (int j = 0; j < parents.size(); j++) {
                z[j] = this.variables.indexOf(getVariable(parents.get(j).getName()));
            }

            Ret ret = getLikelihoodNode(i, z);
            lik += ret.getLik();
            dof += ret.getDof();
        }

        return new Ret(lik, dof);
    }

    private int getDof2(Graph graph) {
        int dof = 0;

        for (Node node : graph.getNodes()) {
            List<Node> parents = new ArrayList<>(graph.getParents(node));

            int i = this.variables.indexOf(getVariable(node.getName()));

            int[] z = new int[parents.size()];

            for (int j = 0; j < parents.size(); j++) {
                z[j] = this.variables.indexOf(getVariable(parents.get(j).getName()));
            }

            dof += getDofNode(i, z);
        }

        return dof;
    }

    private Ret getLikelihoodNode(int node, int[] parents) {
        DiscreteBicScore bic = new DiscreteBicScore(dataSet);
        double lik = bic.localScore(node, parents);

        int dof = (numCategories[node] - 1) * parents.length;

        return new Ret(lik, dof);
    }

    private double getDofNode(int node, int[] parents) {

        // Number of categories for node.
        int c = this.numCategories[node];

        // Numbers of categories of parents.
        int[] dims = new int[parents.length];

        for (int p = 0; p < parents.length; p++) {
            dims[p] = this.numCategories[parents[p]];
        }

        // Number of parent states.
        int r = 1;

        for (int p = 0; p < parents.length; p++) {
            r *= dims[p];
        }

        return r * c;
    }

    private DiscreteVariable getVariable(int i) {
        if (this.variables.get(i) instanceof DiscreteVariable) {
            return (DiscreteVariable) this.variables.get(i);
        } else {
            return null;
        }
    }

    /**
     * Returns the likelihood ratio test statistic for the given graph and its degrees of freedom.
     */
    private static class Ret {
        private final double lik;
        private final int dof;

        /**
         * Constructs a new Ret object.
         *
         * @param lik The likelihood.
         * @param dof The degrees of freedom.
         */
        public Ret(double lik, int dof) {
            this.lik = lik;
            this.dof = dof;
        }

        /**
         * Returns the likelihood.
         *
         * @return The likelihood.
         */
        public double getLik() {
            return this.lik;
        }

        /**
         * Returns the degrees of freedom.
         *
         * @return The degrees of freedom.
         */
        public int getDof() {
            return this.dof;
        }
    }

    /**
     * Returns the number of categories for the given variable.
     */
    public static class LikelihoodRet {

        /**
         * The p-value.
         */
        public double p;

        /**
         * The BIC.
         */
        public double bic;

        /**
         * The chi-squared statistic.
         */
        public double chiSq;

        /**
         * The degrees of freedom.
         */
        public double dof;
    }
}





