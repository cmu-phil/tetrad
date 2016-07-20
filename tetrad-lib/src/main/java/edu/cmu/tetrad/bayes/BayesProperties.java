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
import edu.cmu.tetrad.util.ProbUtils;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

import java.util.List;

/**
 * Calculates some scores for Bayes nets as a whole.
 *
 * @author Joseph Ramsey
 */
public final class BayesProperties {
    private DataSet dataSet;
    private double chisq;
    private double dof;
    private double bic;
    private double likelihood;
    private List<Node> variables;
    private int[][] data;
    private int sampleSize;
    private int[] numCategories;

    public BayesProperties(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;

        if (dataSet instanceof BoxDataSet) {
            DataBox dataBox = ((BoxDataSet) dataSet).getDataBox();

            this.variables = dataSet.getVariables();

            if (!(((BoxDataSet) dataSet).getDataBox() instanceof VerticalIntDataBox)) {
                throw new IllegalArgumentException();
            }

            VerticalIntDataBox box = (VerticalIntDataBox) dataBox;

            data = box.getVariableVectors();
            this.sampleSize = dataSet.getNumRows();
        } else {
            data = new int[dataSet.getNumColumns()][];
            this.variables = dataSet.getVariables();

            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                data[j] = new int[dataSet.getNumRows()];

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    data[j][i] = dataSet.getInt(i, j);
                }
            }

            this.sampleSize = dataSet.getNumRows();
        }

        final List<Node> variables = dataSet.getVariables();
        numCategories = new int[variables.size()];
        for (int i = 0; i < variables.size(); i++) {
            DiscreteVariable variable = getVariable(i);

            if (variable != null) {
                numCategories[i] = variable.getNumCategories();
            }
        }

    }

    /**
     * Calculates the p-value of the graph with respect to the given data.
     */
    public final double getLikelihoodRatioP(Graph graph) {

        // Null hypothesis = complete graph.
        List<Node> nodes = graph.getNodes();

        Graph graph0 = new EdgeListGraph(nodes);

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++)
                graph0.addDirectedEdge(nodes.get(i), nodes.get(j));
        }

        Ret r0 = getLikelihood2(graph0);
        Ret r1 = getLikelihood2(graph);

        this.likelihood = r1.getLik();

        double lDiff = r0.getLik() - r1.getLik();
        System.out.println("lDiff = " + lDiff);

        int nDiff = r0.getDof() - r1.getDof();
        System.out.println("nDiff = " + nDiff);

        double chisq = 2.0 * lDiff;
        double dof = nDiff;

        this.chisq = chisq;
        this.dof = dof;

        int N = dataSet.getNumRows();
        this.bic = 2 * r1.getLik() - r1.getDof() * Math.log(N);
        System.out.println("bic = " + bic);

        System.out.println("chisq = " + chisq);
        System.out.println("dof = " + dof);

        double p = 1.0 - new ChiSquaredDistribution(dof).cumulativeProbability(chisq);

        System.out.println("p = " + p);

        return p;
    }


    /**
     * Call after calling getLikelihoodP().
     */
    public double getChisq() {
        return chisq;
    }

    /**
     * Call after calling getLikelihoodP().
     */
    public double getDof() {
        return dof;
    }

    /**
     * Call after calling getLikelihoodP().
     */
    public double getBic() {
        return bic;
    }

    /**
     * Call after calling getLikelihoodP().
     */
    public double getLikelihood() {
        return likelihood;
    }

    private int getDof(Graph graph) {
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());
        BayesPm pm = new BayesPm(graph);
        BayesIm im = new MlBayesEstimator().estimate(pm, dataSet);

        int numParams = 0;

        for (int j = 0; j < im.getNumNodes(); j++) {
            int numColumns = im.getNumColumns(j);
            int numRows = im.getNumRows(j);
            numParams += (numColumns - 1) * numRows;
        }

        return numParams;
    }

    private double getLikelihood(Graph graph) {
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());
        BayesPm pm = new BayesPm(graph);
        BayesIm im = new MlBayesEstimator().estimate(pm, dataSet);
        double lik = 0.0;

        ROW:
        for (int i = 0; i < dataSet.getNumRows(); i++) {
            double lik0 = 0.0;

            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                int[] parents = im.getParents(j);
                int[] parentValues = new int[parents.length];

                for (int k = 0; k < parents.length; k++) {
                    parentValues[k] = dataSet.getInt(i, parents[k]);
                }

                int dataValue = dataSet.getInt(i, j);
                double p = im.getProbability(j, im.getRowIndex(j, parentValues), dataValue);

                if (p == 0) continue ROW;

                lik0 += Math.log(p);
            }

            lik += lik0;
        }

        return lik;
    }

    private Ret getLikelihood2(Graph graph) {
        double lik = 0.0;
        int dof = 0;

        for (Node node : graph.getNodes()) {
            List<Node> parents = graph.getParents(node);

            int i = variables.indexOf(getVariable(node.getName()));

            int[] z = new int[parents.size()];

            for (int j = 0; j < parents.size(); j++) {
                z[j] = variables.indexOf(getVariable(parents.get(j).getName()));
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
            List<Node> parents = graph.getParents(node);

            int i = variables.indexOf(getVariable(node.getName()));

            int[] z = new int[parents.size()];

            for (int j = 0; j < parents.size(); j++) {
                z[j] = variables.indexOf(getVariable(parents.get(j).getName()));
            }

            dof += getDofNode(i, z);
        }

        return dof;
    }

    private Ret getLikelihoodNode(int node, int parents[]) {

        // Number of categories for node.
        int c = numCategories[node];

        // Numbers of categories of parents.
        int[] dims = new int[parents.length];

        for (int p = 0; p < parents.length; p++) {
            dims[p] = numCategories[parents[p]];
        }

        // Number of parent states.
        int r = 1;

        for (int p = 0; p < parents.length; p++) {
            r *= dims[p];
        }

        // Conditional cell coefs of data for node given parents(node).
        int n_jk[][] = new int[r][c];
        int n_j[] = new int[r];

        int[] parentValues = new int[parents.length];

        int[][] myParents = new int[parents.length][];
        for (int i = 0; i < parents.length; i++) {
            myParents[i] = data[parents[i]];
        }

        int[] myChild = data[node];

        for (int i = 0; i < sampleSize; i++) {
            for (int p = 0; p < parents.length; p++) {
                parentValues[p] = myParents[p][i];
            }

            int childValue = myChild[i];

            if (childValue == -99) {
                throw new IllegalStateException("Please remove or impute missing " +
                        "values (record " + i + " column " + i + ")");
            }

            int rowIndex = getRowIndex(dims, parentValues);

            n_jk[rowIndex][childValue]++;
            n_j[rowIndex]++;
        }

        //Finally, compute the score
        double lik = 0.0;
        int dof = 0;

        for (int rowIndex = 0; rowIndex < r; rowIndex++) {
            int d = 0;

            for (int childValue = 0; childValue < c; childValue++) {
                int cellCount = n_jk[rowIndex][childValue];
                int rowCount = n_j[rowIndex];

                if (cellCount == 0) continue;
                lik += cellCount * Math.log(cellCount / (double) rowCount);
                d++;
            }

            if (d > 0) dof += c - 1;
        }

        return new Ret(lik, dof);
    }

    private class Ret {
        private double lik;
        private int dof;

        public Ret(double lik, int dof) {
            this.lik = lik;
            this.dof = dof;
        }

        public double getLik() {
            return lik;
        }

        public int getDof() {
            return dof;
        }
    }

    private double getDofNode(int node, int parents[]) {

        // Number of categories for node.
        int c = numCategories[node];

        // Numbers of categories of parents.
        int[] dims = new int[parents.length];

        for (int p = 0; p < parents.length; p++) {
            dims[p] = numCategories[parents[p]];
        }

        // Number of parent states.
        int r = 1;

        for (int p = 0; p < parents.length; p++) {
            r *= dims[p];
        }

        return r * c;
    }


    private static int getRowIndex(int[] dim, int[] values) {
        int rowIndex = 0;
        for (int i = 0; i < dim.length; i++) {
            rowIndex *= dim[i];
            rowIndex += values[i];
        }
        return rowIndex;
    }

    public int getSampleSize() {
        return sampleSize;
    }

    public Node getVariable(String targetName) {
        for (Node node : variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }

        return null;
    }

    private DiscreteVariable getVariable(int i) {
        if (variables.get(i) instanceof DiscreteVariable) {
            return (DiscreteVariable) variables.get(i);
        } else {
            return null;
        }
    }
}





