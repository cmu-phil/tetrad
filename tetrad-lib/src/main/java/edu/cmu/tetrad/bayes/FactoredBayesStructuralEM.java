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
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>Implements the procedure Factored-Bayesian-SEM found on page 6 of "The
 * Bayesian Structural EM Algorithm" by Nir Friedman.&gt; 0 <p>The initial implementation simplifies the algorithm
 * somewhat by computing the score of each model by using the BdeMetric score, which is implemented in the bayes package
 * of Tetrad-4.2.&gt; 0
 *
 * @author Frank Wimberly
 * @author Robert Tillman (changes since 5-12-2008)
 * @version $Id: $Id
 */
public final class FactoredBayesStructuralEM {

    // Previous comment when there were three iterate methods. iterate2() has been
    // renamed to iterate(), and the former iterate() and iterate1() have been
    // commented out, below, all in the interests of simplifying the public API.
    // -jdramsey 2/2/2004
    //
    // * <p>There are two obsolete methods in this class. The iterate method uses the
    // * BdeMetric class in an implementation of the Procedure Factored-Bayesian-SEM
    // * in the Friedman paper. The iterate1 method uses the BdeMetricCache class
    // * instead.  The latter class factors each of the models it searches over
    // * exploiting the fact that different models may share factors whose score only
    // * has to be computed and stored once.  Hence, the iterate and iterate1 methods
    // * should return the same model with the same score but iterate1 should be more
    // * efficient.&gt; 0
    // *
    // * <p>The iterate and iterate1 methods have been replaced by iterate2.&gt; 0

    /**
     * The BayesPm that is used to initialize the iterative procedure.
     */
    private final BayesPm bayesPmM0;

    /**
     * The data set that the iterative procedure is based on.
     */
    private final DataSet dataSet;

    /**
     * The number of categories for each variable in the data set.
     */
    private final int[] ncategories;

    /**
     * The tolerance parameter used in Bayes EM estimation.
     */
    private double tolerance;

    /**
     * <p>Constructor for FactoredBayesStructuralEM.</p>
     *
     * @param dataSet   a {@link edu.cmu.tetrad.data.DataSet} object
     * @param bayesPmM0 a {@link edu.cmu.tetrad.bayes.BayesPm} object
     */
    public FactoredBayesStructuralEM(DataSet dataSet,
                                     BayesPm bayesPmM0) {

        this.dataSet = dataSet;
        this.bayesPmM0 = bayesPmM0;

        List<Node> datasetVars = dataSet.getVariables();
        this.ncategories = new int[datasetVars.size()];

        // Store the number of categories for each variable in an array which will be used
        // to define the Bayes nets searched over in the iterative procedure.
        for (int i = 0; i < this.ncategories.length; i++) {
            this.ncategories[i] =
                    ((DiscreteVariable) datasetVars.get(i)).getNumCategories();
        }

    }

    private static double factorScoreMD(Dag dag, BdeMetricCache bdeMetricCache,
                                        BayesPm bayesPm, BayesIm bayesIm) {
        List<Node> nodes = dag.getNodes();

        double score = 0.0;   //Fast test 11/29/04

        for (Node node1 : nodes) {
            List<Node> parents = dag.getParents(node1);
            Set<Node> parentsSet = new HashSet<>(parents);
            double fScore = bdeMetricCache.scoreLnGam(node1, parentsSet,
                    bayesPm, bayesIm);

            String message = "Score for factor " + node1.getName() + " = " + fScore;
            TetradLogger.getInstance().forceLogMessage(message);

            score += fScore;
        }
        return score;
    }

    /**
     * This method allows specification of the tolerance parameter used in Bayes EM estimation.
     *
     * @param tolerance a double
     * @return a {@link edu.cmu.tetrad.bayes.BayesIm} object
     */
    public BayesIm maximization(double tolerance) {
        TetradLogger.getInstance().forceLogMessage("FactoredBayesStructuralEM.maximization()");
        this.tolerance = tolerance;
        return iterate();
    }

    /**
     * This iterate2 method also uses BdeMetricCache but it uses the factorScoreMD method which can handle missing data
     * and latent variables. Ths method iteratively score models and finds that which contains the graph of the highest
     * scoring model (via its BaysPm) as well as parameters which yield the best score given the dataset by using the
     * EmBayesEstimator class.
     *
     * @return the instantiated Bayes net (BayesIm)
     */
    public BayesIm iterate() {

        double start = MillisecondTimes.timeMillis();

        BdeMetricCache bdeMetricCache = new BdeMetricCache(this.dataSet, this.bayesPmM0);

        BayesPm bayesPmMnplus1 = this.bayesPmM0;

        BayesPm bayesPmMn;
        final double oldBestScore = Double.NEGATIVE_INFINITY;
        final int iteration = 0;

        //Loop for n = 0,1,... until convergence or timeout has been exceeded
        TimedIterate ti = new TimedIterate(bdeMetricCache, bayesPmMnplus1, oldBestScore, iteration, start);
        Thread tithread = new Thread(ti);
        tithread.start();
        try {
            tithread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        bayesPmMnplus1 = ti.bayesPmMnplus1;
        bayesPmMn = bayesPmMnplus1;

        EmBayesEstimator emBayesEst = new EmBayesEstimator(bayesPmMn, this.dataSet);
        return emBayesEst.maximization(this.tolerance);

    }

    /*
     * This scoring method uses factor caching and the log gamma scoring function that
     * handles missing data and latent variables.  The Bayes PM contains a graph which
     * indicates which variables are latent.
     */

    /**
     * <p>scoreTest.</p>
     */
    public void scoreTest() {
        TetradLogger.getInstance().forceLogMessage("scoreTest");
        //System.out.println(bayesPmM0.getGraph());
        BdeMetricCache bdeMetricCache;

        BayesPm bayesPmMn = this.bayesPmM0;
        EmBayesEstimator emBayesEst = new EmBayesEstimator(bayesPmMn, this.dataSet);
        emBayesEst.maximization(0.0001);

        Dag dag0 = new Dag(bayesPmMn.getDag());

        Node L1 = dag0.getNode("L1");
        Node X1 = dag0.getNode("X1");

        Dag dag1 = new Dag(dag0);
        dag1.addDirectedEdge(X1, L1);

        BayesPm bayesPm0 = new BayesPm(dag0);
        EmBayesEstimator emBayesEst0 = new EmBayesEstimator(bayesPm0, this.dataSet);
        BayesIm bayesImMn0 = emBayesEst0.maximization(0.0001);

        BayesPm bayesPmTest0 = new BayesPm(dag0);

        TetradLogger.getInstance().forceLogMessage("Observed conts for nodes of L1,X1,X2,X3 (no edges) " +
                                                   "using the MAP parameters based on that same graph");

        TetradLogger.getInstance().forceLogMessage("Graph of PM:  ");
        TetradLogger.getInstance().forceLogMessage("" + bayesPmTest0.getDag());

        TetradLogger.getInstance().forceLogMessage("Graph of IM:  ");
        TetradLogger.getInstance().forceLogMessage("" + bayesImMn0.getBayesPm().getDag());

        bdeMetricCache = new BdeMetricCache(this.dataSet, bayesPmTest0);

        List<Node> nodes0 = dag0.getNodes();

        for (Node aNodes0 : nodes0) {
            double[][] counts0 = bdeMetricCache.getObservedCounts(aNodes0,
                    bayesPmTest0, bayesImMn0);
            for (double[] aCounts0 : counts0) {
                for (int j = 0; j < counts0[0].length; j++) {
                    System.out.print(" " + aCounts0[j]);
                }
                TetradLogger.getInstance().forceLogMessage("\n");
            }
            TetradLogger.getInstance().forceLogMessage("\n");
        }

        double score0 =
                FactoredBayesStructuralEM.factorScoreMD(dag0, bdeMetricCache, bayesPmTest0, bayesImMn0);

        TetradLogger.getInstance().forceLogMessage("Score of L1,X1,X2,X3 (no edges) for itself = " + score0);

        TetradLogger.getInstance().forceLogMessage("===============\n\n");

        TetradLogger.getInstance().forceLogMessage("Score of X1-->L1 for L1,X1,X2,X3 (no edges) = " + score0);


        BayesPm bayesPmTest1 = new BayesPm(dag1);

        TetradLogger.getInstance().forceLogMessage("Observed counts for nodes of X1-->L1 for L1,X1,X2,X3 (no edges)");

        TetradLogger.getInstance().forceLogMessage("Graph of PM :  ");
        TetradLogger.getInstance().forceLogMessage("" + bayesPmTest1.getDag());

        TetradLogger.getInstance().forceLogMessage("Graph of IM:  ");
        TetradLogger.getInstance().forceLogMessage("" + bayesImMn0.getBayesPm().getDag());

        bdeMetricCache = new BdeMetricCache(this.dataSet, bayesPmTest1);

        List<Node> nodes1 = dag0.getNodes();

        for (Node aNodes1 : nodes1) {

            double[][] counts1 = bdeMetricCache.getObservedCounts(aNodes1,
                    bayesPmTest1, bayesImMn0);
            for (double[] aCounts1 : counts1) {
                for (int j = 0; j < counts1[0].length; j++) {
                    TetradLogger.getInstance().forceLogMessage(" " + aCounts1[j]);
                }
                TetradLogger.getInstance().forceLogMessage("\n");
            }
            TetradLogger.getInstance().forceLogMessage("\n");
        }

        double score1 =
                FactoredBayesStructuralEM.factorScoreMD(dag1, bdeMetricCache, bayesPmTest1, bayesImMn0);

        TetradLogger.getInstance().forceLogMessage("Score of X1-->L1 for L1,X1,X2,X3 (no edges) = " + score1);


    }

    /**
     * <p>Getter for the field <code>dataSet</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet getDataSet() {
        return this.dataSet;
    }

    private class TimedIterate implements Runnable {

        final BdeMetricCache bdeMetricCache;
        final double start;
        BayesPm bayesPmMnplus1;
        BayesPm bayesPmMn;
        double oldBestScore;
        int iteration;

        public TimedIterate(BdeMetricCache bdeMetricCache, BayesPm bayesPmMnplus1, double oldBestScore, int iteration, double start) {
            this.bdeMetricCache = bdeMetricCache;
            this.bayesPmMnplus1 = bayesPmMnplus1;
            this.bayesPmMn = null;
            this.oldBestScore = oldBestScore;
            this.iteration = iteration;
            this.start = start;
        }

        public void run() {
            while (!this.bayesPmMnplus1.equals(this.bayesPmMn)) {

                this.iteration++;

                this.bayesPmMn = this.bayesPmMnplus1;
                TetradLogger.getInstance().forceLogMessage("In Factored Bayes Struct EM Iteration number " +
                                                           this.iteration);

                //Compute the MAP parameters for Mn given o.
                TetradLogger.getInstance().forceLogMessage("Starting EM Bayes estimator to get MAP parameters of Mn");
                EmBayesEstimator emBayesEst =
                        new EmBayesEstimator(this.bayesPmMn, FactoredBayesStructuralEM.this.dataSet);
                BayesIm bayesImMn = emBayesEst.maximization(FactoredBayesStructuralEM.this.tolerance);
                TetradLogger.getInstance().forceLogMessage("Estimation of MAP parameters of Mn complete. \n\n");

                //Perform search over models...
                Graph graphMn = this.bayesPmMn.getDag();
                Dag dagMn = new Dag(graphMn);
                List<Graph> models = ModelGenerator.generate(graphMn);

                double bestScore =
                        FactoredBayesStructuralEM.factorScoreMD(dagMn, this.bdeMetricCache, this.bayesPmMn, bayesImMn);

                EdgeListGraph edges = new EdgeListGraph(dagMn);

                TetradLogger.getInstance().forceLogMessage("Initial graph Mn = ");
                String message = edges.toString();
                TetradLogger.getInstance().forceLogMessage(message);
                TetradLogger.getInstance().forceLogMessage("Its score = " + bestScore);

                for (Graph model : models) {
                    Dag dag = new Dag(model);

                    BayesPm bayesPmTest = new BayesPm(dag);

                    //Having instantiated the BayesPm, set the number of categories correctly.
                    for (int i = 0; i < FactoredBayesStructuralEM.this.dataSet.getVariables().size(); i++) {
                        String varName = FactoredBayesStructuralEM.this.dataSet.getVariableNames().get(i);
                        Node node = dag.getNode(varName);
                        bayesPmTest.setNumCategories(node, FactoredBayesStructuralEM.this.ncategories[i]);
                    }

                    double score = FactoredBayesStructuralEM.factorScoreMD(dag, this.bdeMetricCache, bayesPmTest,
                            bayesImMn);

                    EdgeListGraph edgesTest = new EdgeListGraph(dag);
                    TetradLogger.getInstance().forceLogMessage("For the model with graph \n" + edgesTest);
                    TetradLogger.getInstance().forceLogMessage("Model Score = " + score);

                    if (score <= bestScore) {
                        continue;    //This is not better than the best to date.
                    }
                    bestScore = score;

                    //Let M sub n+1 be the model with the highest score amonth those encountered
                    //during the search.
                    this.bayesPmMnplus1 = bayesPmTest;
                }

                TetradLogger.getInstance().forceLogMessage("In iteration:  " + this.iteration);
                TetradLogger.getInstance().forceLogMessage("bestScore, oldBestScore " + bestScore + " " +
                                                           this.oldBestScore);
                EdgeListGraph edgesBest =
                        new EdgeListGraph(this.bayesPmMnplus1.getDag());
                TetradLogger.getInstance().forceLogMessage("Graph of model:  \n" + edgesBest);
                TetradLogger.getInstance().forceLogMessage("====================================");
                this.oldBestScore = bestScore;

            }
        }
    }
}





