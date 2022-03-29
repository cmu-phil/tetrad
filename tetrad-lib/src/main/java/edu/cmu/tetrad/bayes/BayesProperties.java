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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

import java.util.List;

/**
 * Calculates some scores for Bayes nets as a whole.
 *
 * @author Joseph Ramsey
 */
public final class BayesProperties {
    private final DataSet dataSet;
    private double chisq;
    private double dof;
    private double bic;
    private double likelihood;
    private final List<Node> variables;
    private final int[][] data;
    private final int sampleSize;
    private final int[] numCategories;

    public BayesProperties(final DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;

        if (dataSet instanceof BoxDataSet) {
            final DataBox dataBox = ((BoxDataSet) dataSet).getDataBox();

            this.variables = dataSet.getVariables();

            final VerticalIntDataBox box = new VerticalIntDataBox(dataBox);

            this.data = box.getVariableVectors();
            this.sampleSize = dataSet.getNumRows();
        } else {
            this.data = new int[dataSet.getNumColumns()][];
            this.variables = dataSet.getVariables();

            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                this.data[j] = new int[dataSet.getNumRows()];

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    this.data[j][i] = dataSet.getInt(i, j);
                }
            }

            this.sampleSize = dataSet.getNumRows();
        }

        final List<Node> variables = dataSet.getVariables();
        this.numCategories = new int[variables.size()];
        for (int i = 0; i < variables.size(); i++) {
            final DiscreteVariable variable = getVariable(i);

            if (variable != null) {
                this.numCategories[i] = variable.getNumCategories();
            }
        }

    }

    /**
     * Calculates the p-value of the graph with respect to the given data.
     */
    public final double getLikelihoodRatioP(final Graph graph) {

        // Null hypothesis = complete graph.
        final List<Node> nodes = graph.getNodes();

        final Graph graph0 = new EdgeListGraph(nodes);

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++)
                graph0.addDirectedEdge(nodes.get(i), nodes.get(j));
        }

        final Ret r0 = getLikelihood2(graph0);
        final Ret r1 = getLikelihood2(graph);

        this.likelihood = r1.getLik();

        final double lDiff = r0.getLik() - r1.getLik();
        System.out.println("lDiff = " + lDiff);

        final int nDiff = r0.getDof() - r1.getDof();
        System.out.println("nDiff = " + nDiff);

        final double chisq = 2.0 * lDiff;
        final double dof = nDiff;

        this.chisq = chisq;
        this.dof = dof;

        final int N = this.dataSet.getNumRows();
        this.bic = 2 * r1.getLik() - r1.getDof() * Math.log(N);
        System.out.println("bic = " + this.bic);

        System.out.println("chisq = " + chisq);
        System.out.println("dof = " + dof);

        final double p = 1.0 - new ChiSquaredDistribution(dof).cumulativeProbability(chisq);

        System.out.println("p = " + p);

        return p;
    }


    /**
     * Call after calling getLikelihoodP().
     */
    public double getChisq() {
        return this.chisq;
    }

    /**
     * Call after calling getLikelihoodP().
     */
    public double getDof() {
        return this.dof;
    }

    /**
     * Call after calling getLikelihoodP().
     */
    public double getBic() {
        return this.bic;
    }

    /**
     * Call after calling getLikelihoodP().
     */
    public double getLikelihood() {
        return this.likelihood;
    }

    private int getDof(Graph graph) {
        graph = GraphUtils.replaceNodes(graph, this.dataSet.getVariables());
        final BayesPm pm = new BayesPm(graph);
        final BayesIm im = new MlBayesEstimator().estimate(pm, this.dataSet);

        int numParams = 0;

        for (int j = 0; j < im.getNumNodes(); j++) {
            final int numColumns = im.getNumColumns(j);
            final int numRows = im.getNumRows(j);
            numParams += (numColumns - 1) * numRows;
        }

        return numParams;
    }

    private double getLikelihood(Graph graph) {
        graph = GraphUtils.replaceNodes(graph, this.dataSet.getVariables());
        final BayesPm pm = new BayesPm(graph);
        final BayesIm im = new MlBayesEstimator().estimate(pm, this.dataSet);
        double lik = 0.0;

        ROW:
        for (int i = 0; i < this.dataSet.getNumRows(); i++) {
            double lik0 = 0.0;

            for (int j = 0; j < this.dataSet.getNumColumns(); j++) {
                final int[] parents = im.getParents(j);
                final int[] parentValues = new int[parents.length];

                for (int k = 0; k < parents.length; k++) {
                    parentValues[k] = this.dataSet.getInt(i, parents[k]);
                }

                final int dataValue = this.dataSet.getInt(i, j);
                final double p = im.getProbability(j, im.getRowIndex(j, parentValues), dataValue);

                if (p == 0) continue ROW;

                lik0 += Math.log(p);
            }

            lik += lik0;
        }

        return lik;
    }

    private Ret getLikelihood2(final Graph graph) {
        double lik = 0.0;
        int dof = 0;

        for (final Node node : graph.getNodes()) {
            final List<Node> parents = graph.getParents(node);

            final int i = this.variables.indexOf(getVariable(node.getName()));

            final int[] z = new int[parents.size()];

            for (int j = 0; j < parents.size(); j++) {
                z[j] = this.variables.indexOf(getVariable(parents.get(j).getName()));
            }

            final Ret ret = getLikelihoodNode(i, z);
            lik += ret.getLik();
            dof += ret.getDof();
        }

        return new Ret(lik, dof);
    }

    private int getDof2(final Graph graph) {
        int dof = 0;

        for (final Node node : graph.getNodes()) {
            final List<Node> parents = graph.getParents(node);

            final int i = this.variables.indexOf(getVariable(node.getName()));

            final int[] z = new int[parents.size()];

            for (int j = 0; j < parents.size(); j++) {
                z[j] = this.variables.indexOf(getVariable(parents.get(j).getName()));
            }

            dof += getDofNode(i, z);
        }

        return dof;
    }

    private Ret getLikelihoodNode(final int node, final int[] parents) {

        // Number of categories for node.
        final int c = this.numCategories[node];

        // Numbers of categories of parents.
        final int[] dims = new int[parents.length];

        for (int p = 0; p < parents.length; p++) {
            dims[p] = this.numCategories[parents[p]];
        }

        // Number of parent states.
        int r = 1;

        for (int p = 0; p < parents.length; p++) {
            r *= dims[p];
        }

        // Conditional cell coefs of data for node given parents(node).
        final int[][] n_jk = new int[r][c];
        final int[] n_j = new int[r];

        final int[] parentValues = new int[parents.length];

        final int[][] myParents = new int[parents.length][];
        for (int i = 0; i < parents.length; i++) {
            myParents[i] = this.data[parents[i]];
        }

        final int[] myChild = this.data[node];

        for (int i = 0; i < this.sampleSize; i++) {
            for (int p = 0; p < parents.length; p++) {
                parentValues[p] = myParents[p][i];
            }

            final int childValue = myChild[i];

            if (childValue == -99) {
                throw new IllegalStateException("Please remove or impute missing " +
                        "values (record " + i + " column " + i + ")");
            }

            final int rowIndex = BayesProperties.getRowIndex(dims, parentValues);

            n_jk[rowIndex][childValue]++;
            n_j[rowIndex]++;
        }

        //Finally, compute the score
        double lik = 0.0;
        int dof = 0;

        for (int rowIndex = 0; rowIndex < r; rowIndex++) {
            int d = 0;

            for (int childValue = 0; childValue < c; childValue++) {
                final int cellCount = n_jk[rowIndex][childValue];
                final int rowCount = n_j[rowIndex];

                if (cellCount == 0) continue;
                lik += cellCount * Math.log(cellCount / (double) rowCount);
                d++;
            }

            if (d > 0) dof += c - 1;
        }

        return new Ret(lik, dof);
    }

    private class Ret {
        private final double lik;
        private final int dof;

        public Ret(final double lik, final int dof) {
            this.lik = lik;
            this.dof = dof;
        }

        public double getLik() {
            return this.lik;
        }

        public int getDof() {
            return this.dof;
        }
    }

    private double getDofNode(final int node, final int[] parents) {

        // Number of categories for node.
        final int c = this.numCategories[node];

        // Numbers of categories of parents.
        final int[] dims = new int[parents.length];

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


    private static int getRowIndex(final int[] dim, final int[] values) {
        int rowIndex = 0;
        for (int i = 0; i < dim.length; i++) {
            rowIndex *= dim[i];
            rowIndex += values[i];
        }
        return rowIndex;
    }

    public int getSampleSize() {
        return this.sampleSize;
    }

    public Node getVariable(final String targetName) {
        for (final Node node : this.variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }

        return null;
    }

    private DiscreteVariable getVariable(final int i) {
        if (this.variables.get(i) instanceof DiscreteVariable) {
            return (DiscreteVariable) this.variables.get(i);
        } else {
            return null;
        }
    }
}





