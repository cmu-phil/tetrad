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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ProbUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Calculates some scores for Bayes nets as a whole.
 *
 * @author Joseph Ramsey
 */
public final class EmBayesProperties {
    public interface Estimator {
        BayesIm estimate(BayesPm bayesPm, DataSet dataSet);
    }

    private DataSet dataSet;
    private BayesPm bayesPm;
    private Graph graph;
    private MlBayesIm blankBayesIm;
    private int pValueDf;
    private double chisq;
    private Estimator estimator = (bayesPm, dataSet) -> {
        EmBayesEstimator estimator = new EmBayesEstimator(bayesPm, dataSet);
        this.dataSet = estimator.getMixedDataSet();

        try {
            double tolerance = 0.0001;
            estimator.maximization(tolerance);
            return estimator.getEstimatedIm();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            throw new RuntimeException(
                    "Please specify the search tolerance first.");
        }
    };

    public EmBayesProperties(DataSet dataSet, Graph graph) {
        setDataSet(dataSet);
        setGraph(graph);
    }

    public void setGraph(Graph graph) {
        if (graph == null) {
            throw new NullPointerException();
        }

        List<Node> vars = this.dataSet.getVariables();
        Map<String, DiscreteVariable> nodesToVars =
                new HashMap<>();
        for (int i = 0; i < this.dataSet.getNumColumns(); i++) {
            DiscreteVariable var = (DiscreteVariable) vars.get(i);
            String name = var.getName();
            Node node = new GraphNode(name);
            nodesToVars.put(node.getName(), var);
        }

        Dag dag = new Dag(graph);
        BayesPm bayesPm = new BayesPm(dag);

        List<Node> nodes = bayesPm.getDag().getNodes();

        for (Node node1 : nodes) {
            DiscreteVariable var = nodesToVars.get(node1.getName());

            if (var != null) {
                List<String> categories = var.getCategories();
                bayesPm.setCategories(node1, categories);
            }
        }

        this.graph = graph;
        this.bayesPm = bayesPm;
        this.blankBayesIm = new MlBayesIm(bayesPm);
    }

    /**
     * Calculates the BIC (Bayes Information Criterion) score for a BayesPM with
     * respect to a given discrete data set. Following formulas of Andrew Moore,
     * www.cs.cmu.edu/~awm.
     */
    public double getBic() {
        return logProbDataGivenStructure() - parameterPenalty();
    }

    /**
     * Calculates the p-value of the graph with respect to the given data.
     */
    public double getLikelihoodRatioP() {
        Graph graph1 = getGraph();
        List<Node> nodes = getGraph().getNodes();

        // Null hypothesis = no edges.
        Graph graph0 = new Dag();

        for (Node node : nodes) {
            graph0.addNode(node);
        }

        EmBayesProperties scorer1 = new EmBayesProperties(getDataSet(), graph1);
        EmBayesProperties scorer0 = new EmBayesProperties(getDataSet(), graph0);

        double l1 = scorer1.logProbDataGivenStructure();
        double l0 = scorer0.logProbDataGivenStructure();

        System.out.println("l1 = " + l1);
        System.out.println("l0 = " + l0);

        double chisq = -2.0 * (l0 - l1);
        int n1 = scorer1.numNonredundantParams();
        int n0 = scorer0.numNonredundantParams();

        int df = n1 - n0;
        double pValue = (1.0 - ProbUtils.chisqCdf(chisq, df));

        this.pValueDf = df;
        this.chisq = chisq;
        return pValue;
    }

    public BayesPm getBayesPm() {
        return this.bayesPm;
    }

    public int getPValueDf() {
        return this.pValueDf;
    }

    public double getPValueChisq() {
        return this.chisq;
    }

    public Estimator getEstimator() {
        return this.estimator;
    }

    public void setEstimator(Estimator estimator) {
        this.estimator = estimator;
    }

    //=========================================PRIVATE METHODS===================================//

    private double logProbDataGivenStructure() {
        BayesIm bayesIm = this.estimator.estimate(this.bayesPm, this.dataSet);
        BayesImProbs probs = new BayesImProbs(bayesIm);
        List<Node> variables = bayesIm.getVariables();

        System.out.println("E1 bayesIm : " + variables);
        System.out.println("E2 data set : " + this.dataSet.getVariables());

        DataSet reorderedDataSet = this.dataSet.subsetColumns(variables);

        int n = reorderedDataSet.getNumRows();
        int m = reorderedDataSet.getNumColumns();

        double score = 0.0;
        int[] _case = new int[m];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                _case[j] = reorderedDataSet.getInt(i, j);
            }

            score += Math.log(probs.getCellProb(_case));
        }

        return score;
    }

    private int numNonredundantParams() {
        setGraph(getGraph());
        int numParams = 0;

        for (int j = 0; j < this.blankBayesIm.getNumNodes(); j++) {
            int numColumns = this.blankBayesIm.getNumColumns(j);
            int numRows = this.blankBayesIm.getNumRows(j);

            if (numColumns > 1) {
                numParams += (numColumns - 1) * numRows;
            }
        }

        return numParams;
    }

    private double parameterPenalty() {
        int numParams = numNonredundantParams();
        double r = this.dataSet.getNumRows();
        return (double) numParams * Math.log(r) / 2.;
    }

    private Graph getGraph() {
        return this.graph;
    }

    private DataSet getDataSet() {
        return this.dataSet;
    }

    private void setDataSet(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.bayesPm = null;
        this.blankBayesIm = null;
        this.graph = null;
        this.pValueDf = -1;
        this.chisq = Double.NaN;

        this.dataSet = dataSet;
    }

}


