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
import edu.cmu.tetrad.util.RandomUtil;

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
    private Estimator estimator = new Estimator() {
        public BayesIm estimate(BayesPm bayesPm, DataSet dataSet) {
            EmBayesEstimator estimator = new EmBayesEstimator(bayesPm, dataSet);
//            this.dataSet = estimator.getMixedDataSet();
            estimator.getMixedDataSet();

            try {
                double tolerance = 0.0001;
                estimator.maximization(tolerance);
                return estimator.getEstimatedIm();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                throw new RuntimeException(
                        "Please specify the search tolerance first.");
            }
        }
    };

    public EmBayesProperties(DataSet dataSet, Graph graph) {
        setDataSet(dataSet);
        setGraph(graph);
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
        this.bayesPm = bayesPm;
        this.blankBayesIm = new MlBayesIm(bayesPm);
    }

    /**
     * Calculates the BIC (Bayes Information Criterion) score for a BayesPM with
     * respect to a given discrete data set. Following formulas of Andrew Moore,
     * www.cs.cmu.edu/~awm.
     */
    public final double getBic() {
        return logProbDataGivenStructure() - parameterPenalty();
    }

    /**
     * Calculates the p-value of the graph with respect to the given data.
     */
    public final double getLikelihoodRatioP() {
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

        //        System.out.println("\n*** P Value Calculation ***");
        //        System.out.println("l1 = " + l1 + " l0 = " + l0 + " l0 - l1 = " + (l0 - l1));
        //        System.out.println("n1 = " + n1 + " n0 = " + n0 + " n1 - n0 = " + (n1 - n0));
        //        System.out.println("chisq = " + chisq + " pvalue = " + pValue);

        this.pValueDf = df;
        this.chisq = chisq;
        return pValue;
    }

    public final double getVuongP() {
        Graph gg = getGraph();
        List<Node> nodes = getGraph().getNodes();

        // Null hypothesis = no edges.
        Graph gf = new Dag();

        for (Node node : nodes) {
            gf.addNode(node);
        }

        EmBayesProperties scorerf = new EmBayesProperties(getDataSet(), gg);
        EmBayesProperties scorerg = new EmBayesProperties(getDataSet(), gf);

        double[] scoresf = scorerf.logsProbDataGivenStructure();
        double[] scoresg = scorerg.logsProbDataGivenStructure();

        int n = getDataSet().getNumRows();

        double v1 = 0.0;

        for (int i = 0; i < n; i++) {
            double v = scoresg[i] - scoresf[i];
            v1 += (v * v);
        }

        double lf = 0.0;

        for (int i = 0; i < n; i++) {
            lf += scoresg[i];
        }

        double lg = 0.0;

        for (int i = 0; i < n; i++) {
            lg += scoresf[i];
        }

        double lr = 0.0;

        for (int i = 0; i < n; i++) {
            lr += scoresg[i] - scoresf[i];
        }

//        double lr = lf - lf;

        double w2 = v1 / n - Math.pow(lr / n, 2);


        double stat = (lr) / (Math.pow((double) n * w2, 0.5));

        System.out.println("v1 = " + v1 + " lr = " + lr + " w2 = " + w2 + " stat = " + stat);

        return 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, Math.abs(stat)));
    }

    /**
     * Calculates  log(P(Data | structure)) using Andrew Moore's formula.
     */
    public final double logProbDataGivenStructure2() {
        DataSetProbs probs = new DataSetProbs(getDataSet());

        double r = dataSet.getNumRows();
        double score = 0.0;

        List<String> dataVarNames = dataSet.getVariableNames();

        for (int j = 0; j < blankBayesIm.getNumNodes(); j++) {

            rows:
            for (int k = 0; k < blankBayesIm.getNumRows(j); k++) {

                // Calculate probability of this combination of parent
                // values.
                Proposition condition = Proposition.tautology(blankBayesIm);

                int[] parents = blankBayesIm.getParents(j);
                int[] parentValues = blankBayesIm.getParentValues(j, k);

                for (int v = 0; v < blankBayesIm.getNumParents(j); v++) {
                    int parent = parents[v];
                    int dataVar = translate(parent, dataVarNames);
                    condition.setCategory(dataVar, parentValues[v]);
                }

                double p1 = probs.getProb(condition);

                for (int v = 0; v < blankBayesIm.getNumColumns(j); v++) {
                    Proposition assertion = Proposition.tautology(blankBayesIm);

                    int _j = translate(j, dataVarNames);
                    assertion.setCategory(_j, v);
                    double p2 = probs.getConditionalProb(assertion, condition);

                    if (Double.isNaN(p2) || p2 == 0.) {
                        continue rows;
                    }

                    double numCases = r * p1 * p2;
                    score += numCases * Math.log(p2);
                }
            }
        }

        return score;
    }

    public final BayesPm getBayesPm() {
        return bayesPm;
    }

    public final int getPValueDf() {
        return pValueDf;
    }

    public final double getPValueChisq() {
        return chisq;
    }

    public Estimator getEstimator() {
        return estimator;
    }

    public void setEstimator(Estimator estimator) {
        this.estimator = estimator;
    }

    //=========================================PRIVATE METHODS===================================//

    private double logProbDataGivenStructure() {
        BayesIm bayesIm = this.estimator.estimate(bayesPm, dataSet);
        BayesImProbs probs = new BayesImProbs(bayesIm);
        List<Node> variables = bayesIm.getVariables();

        System.out.println("E1 bayesIm : " + variables);
        System.out.println("E2 data set : " + dataSet.getVariables());

        DataSet reorderedDataSet = dataSet.subsetColumns(variables);

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

    private double[] logsProbDataGivenStructure() {
        BayesIm bayesIm = this.estimator.estimate(bayesPm, dataSet);
        BayesImProbs probs = new BayesImProbs(bayesIm);
        List<Node> variables = bayesIm.getVariables();
        DataSet reorderedDataSet = dataSet.subsetColumns(variables);

        int n = reorderedDataSet.getNumRows();
        int m = reorderedDataSet.getNumColumns();

        double[] scores = new double[n];
        int[] _case = new int[m];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                _case[j] = reorderedDataSet.getInt(i, j);
            }

            scores[i] = Math.log(probs.getCellProb(_case));
        }

        return scores;
    }

    private int numNonredundantParams() {
        setGraph(getGraph());
        int numParams = 0;

        for (int j = 0; j < blankBayesIm.getNumNodes(); j++) {
            int numColumns = blankBayesIm.getNumColumns(j);
            int numRows = blankBayesIm.getNumRows(j);

            if (numColumns > 1) {
                numParams += (numColumns - 1) * numRows;
            }
        }

        return numParams;
    }

    private double parameterPenalty() {
        int numParams = numNonredundantParams();
        double r = dataSet.getNumRows();
        return (double) numParams * Math.log(r) / 2.;
    }

    private Graph getGraph() {
        return graph;
    }

    private int translate(int parent, List<String> dataVarNames) {
        String imName = blankBayesIm.getNode(parent).getName();
        return dataVarNames.indexOf(imName);
    }

    private DataSet getDataSet() {
        return dataSet;
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


