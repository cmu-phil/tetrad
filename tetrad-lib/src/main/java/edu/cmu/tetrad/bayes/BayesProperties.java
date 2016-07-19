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
public final class BayesProperties {
    private DataSet dataSet;
    private BayesPm bayesPm;
    private Graph graph;
    private MlBayesIm blankBayesIm;
    private int dof;
    private double chisq;
    private MlBayesEstimator estimator;

    // Indices of variables.
    private Map<String, Integer> nodesHash;

    // Discrete data only.
    private int[][] discreteData;

    public BayesProperties(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;
        List<Node> variables = dataSet.getVariables();

        discreteData = new int[dataSet.getNumColumns()][];

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Node v = dataSet.getVariable(j);

            if (v instanceof DiscreteVariable) {
                int[] col = new int[dataSet.getNumRows()];

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    col[i] = dataSet.getInt(i, j);
                }

                discreteData[j] = col;
            }
        }

        nodesHash = new HashMap<>();

        for (int j = 0; j < variables.size(); j++) {
            Node v = variables.get(j);
            nodesHash.put(v.getName(), j);
        }
    }

    public final void setGraph(Graph graph) {
        if (graph == null) {
            throw new NullPointerException();
        }

        List<Node> vars = dataSet.getVariables();
        Map<String, DiscreteVariable> nodesToVars =
                new HashMap<>();
        for (int i = 0; i < dataSet.getNumColumns(); i++) {
            DiscreteVariable var = (DiscreteVariable) vars.get(i);
            String name = var.getName();
            Node node = new GraphNode(name);
            nodesToVars.put(node.getName(), var);
        }

        Dag dag = new Dag(graph);
        BayesPm bayesPm = new BayesPm(dag);

        List<Node> nodes = bayesPm.getDag().getNodes();

        for (Node node1 : nodes) {
            Node var = nodesToVars.get(node1.getName());

            if (var != null) {
                DiscreteVariable var2 = (DiscreteVariable) var;
                List<String> categories = var2.getCategories();
                bayesPm.setCategories(node1, categories);
            }
        }

        this.graph = graph;
        this.blankBayesIm = new MlBayesIm(bayesPm);
        this.dof = -1;
        this.chisq = Double.NaN;
        this.bayesPm = bayesPm;
        this.estimator = new MlBayesEstimator();

    }

    /**
     * Calculates the BIC (Bayes Information Criterion) score for a BayesPM with
     * respect to a given discrete data set. Following formulas of Andrew Moore,
     * www.cs.cmu.edu/~awm.
     */
    public final double getBic() {
        return -(2 * getLikelihood(graph) - getDof(graph));
    }

    public double getLikelihood(Graph graph) {
        BayesPm pm = new BayesPm(graph);
        BayesIm im = new MlBayesEstimator().estimate(pm, dataSet);
        double lik = 0.0;

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                int[] parents = im.getParents(j);
                int[] values = new int[parents.length];

                for (int k = 0; i < parents.length; i++) {
                    values[k] = dataSet.getInt(i, parents[k]);
                }

                double p = im.getProbability(j, im.getRowIndex(j, values), dataSet.getInt(i, j));
                lik += Math.log(p);
            }
        }

        return lik;
    }

    /**
     * Calculates the p-value of the graph with respect to the given data.
     */
    public final double getLikelihoodRatioP() {
        Graph graph1 = getGraph();
        List<Node> nodes = getGraph().getNodes();

        // Null hypothesis = complete graph.
        Graph graph0 = new Dag();

        for (Node node : nodes) {
            graph0.addNode(node);
        }

//        for (int i = 0; i < nodes.size() - 1; i++) {
//            for (int j = i + 1; j <= nodes.size() - 1; j++)
//                graph0.addDirectedEdge(nodes.get(i), nodes.get(j));
//        }

        double l0 = getLikelihood(graph0);
        int n0 = getDof(graph0);

        double l1 = getLikelihood(graph1);
        int n1 = getDof(graph1);

        double chisq = 2.0 * Math.abs(l0 - l1);

        int dof = Math.abs(n1 - n0);
        double p = (1.0 - ProbUtils.chisqCdf(chisq, dof));

        System.out.println(p + " chisq = " + chisq);

        this.dof = dof;
        this.chisq = chisq;
        return p;
    }

    public int getDof(Graph graph) {
        BayesPm pm = new BayesPm(graph);
        BayesIm im = new MlBayesEstimator().estimate(pm, dataSet);

        setGraph(getGraph());
        int numParams = 0;

        for (int j = 0; j < im.getNumNodes(); j++) {
            int numColumns = im.getNumColumns(j);
            int numRows = im.getNumRows(j);

            if (numColumns > 1) {
                numParams += (numColumns - 1) * numRows;
            }
        }

        return numParams;
    }


    public final BayesPm getBayesPm() {
        return bayesPm;
    }

    public final int getDof() {
        return dof;
    }

    public final double getChisq() {
        return chisq;
    }

    public MlBayesEstimator getEstimator() {
        return estimator;
    }

    public void setEstimator(MlBayesEstimator estimator) {
        this.estimator = estimator;
    }

    //=========================================PRIVATE METHODS===================================//

    private double logProbDataGivenStructure() {
        BayesPm pm = new BayesPm(getGraph(), bayesPm);
        BayesIm bayesIm = this.estimator.estimate(pm, dataSet);
        BayesImProbs probs = new BayesImProbs(bayesIm);
        List<Node> variables = bayesIm.getVariables();

        int n = dataSet.getNumRows();
        int m = dataSet.getNumColumns();

        double score = 0.0;
        int[] _case = new int[m];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                int j1 = nodesHash.get(variables.get(j).getName());
                _case[j] = discreteData[j1][i];
            }

            score += Math.log(probs.getCellProb(_case));
        }

        return score;
    }

    private double parameterPenalty() {
        int numParams = getDof(getGraph());
        double r = dataSet.getNumRows();
        return (double) numParams * Math.log(r) / 2.;
    }

    private Graph getGraph() {
        return graph;
    }
}





