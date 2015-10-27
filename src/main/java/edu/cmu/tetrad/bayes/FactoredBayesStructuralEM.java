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
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * <p>Implements the procedure Factored-Bayesian-SEM found on page 6 of "The
 * Bayesian Structural EM Algorithm" by Nir Friedman.</p> <p>The initial
 * implementation simplifies the algorithm somewhat by computing the score of
 * each model by using the BdeMetric score, which is implemented in the bayes
 * package of Tetrad-4.2.</p>
 *
 * @author Frank Wimberly
 * @author Robert Tillman (changes since 5-12-2008)
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
    // * efficient.</p>
    // *
    // * <p>The iterate and iterate1 methods have been replaced by iterate2.</p>

    private final BayesPm bayesPmM0;
    private final DataSet dataSet;
    private double tolerance;

    private final int[] ncategories;

    private double resultScore = -1;
    private Graph resultGraph = new EdgeListGraph();

    private int timeout = -1;
    private double start = -1;
    private int totaliterations = 0;

    public FactoredBayesStructuralEM(DataSet dataSet,
                                     BayesPm bayesPmM0) {

        this.dataSet = dataSet;
        this.bayesPmM0 = bayesPmM0;

        List<Node> datasetVars = dataSet.getVariables();
        this.ncategories = new int[datasetVars.size()];

        // Store the number of categories for each variable in an array which will be used
        // to define the Bayes nets searched over in the iterative procedure.
        for (int i = 0; i < ncategories.length; i++) {
            ncategories[i] =
                    ((DiscreteVariable) datasetVars.get(i)).getNumCategories();
        }

    }

    //    /**
    //     * This method is obsolete and is an early development version.
    //     *
    //     * @return a Bayes PM containing the graph of the highest score model
    //     */
    //    public BayesPm iterate() {
    //
    //        //boolean convergence = false;           //vestige
    //
    //        BayesPm bayesPmMnplus1 = bayesPmM0;
    //        //Graph graphPmMnplus1 = bayesPmMnplus1.getGraph();
    //
    //        //BayesIm bayesImMn = null;             //vestige
    //        BayesPm bayesPmMn = null;
    //        double oldBestScore = 0.0;
    //        int iteration = 0;
    //
    //        //Loop for n = 0,1,... until convergence
    //        while (!bayesPmMnplus1.equals(bayesPmMn)) {
    //            iteration++;
    //
    //            bayesPmMn = bayesPmMnplus1;
    //            System.out.println("In Factored Bayes Struct EM Iteration number " + iteration);
    //
    //            //Compute the MAP parameters for Mn given o.
    //            //EmBayesEstimator emBayesEst = new EmBayesEstimator(bayesPmMn, dataSet);
    //            //bayesImMn = emBayesEst.maximization(0.0001);
    //
    //            //Perform search over models...
    //            List models = ModelGenerator.generate(bayesPmMn.getGraph());
    //
    //            //double bestScore = 0.0;
    //            //Initialize bestScore to the score of bayesPmMn
    //            //(whose graph is varied in the for loop).
    //            BdeMetric bdeMetricMn = new BdeMetric(dataSet, bayesPmMn);
    //
    //            double bestScore = bdeMetricMn.scoreLnGam();
    //
    //            for (Iterator itm = models.iterator(); itm.hasNext();) {
    //                Graph graph = (Graph) itm.next();
    //                Dag dag = new Dag(graph);
    //
    //                BayesPm bayesPmTest = new BayesPm(dag);
    //
    //                //Having instantiated the BayesPm, set the number of categories correctly.
    //                for (int i = 0; i < dataSet.getVariables().size(); i++) {
    //                    String varName = (String) dataSet.getVariableNames().get(i);
    //                    Node node = dag.getNode(varName);
    //                    bayesPmTest.setNumSplits(node, ncategories[i]);
    //                }
    //
    //
    //                BdeMetric bdeMetric = new BdeMetric(dataSet, bayesPmTest);
    //
    //                double score = bdeMetric.scoreLnGam();
    //
    //                System.out.println("For the model with graph \n" + dag);
    //                System.out.println("Score = " + score);
    //
    //                if (score <= bestScore) continue;  //This is not better than the best to date.
    //                bestScore = score;
    //                //oldBestScore = bestScore;
    //
    //                //Let M sub n+1 be the model with the highest score amonth those encountered
    //                //during the search.
    //                bayesPmMnplus1 = bayesPmTest;
    //            }
    //
    //            System.out.println("bestScore, oldBestScore " + bestScore + " " + oldBestScore);
    //
    //            oldBestScore = bestScore;
    //        }
    //
    //
    //        //Returns the model (a BayesPm) with the best score.
    //        return bayesPmMn;
    //    }

    //    /**
    //     * This method uses BdeMetricCache instead of BdeMetric.  That is a score is computed
    //     * per factor and then stored.  It can be accessed if it occurs during evaluating
    //     * a different model.  This method is obsolete and has been replaced by iterate2()
    //     * (below) since it does not deal with missing data nor latent variables.
    //     */
    //    public BayesPm iterate1() {
    //
    //        //boolean convergence = false;        //Vestige
    //        BdeMetricCache bdeMetricCache = new BdeMetricCache(dataSet, bayesPmM0);
    //
    //        BayesPm bayesPmMnplus1 = bayesPmM0;
    //        Graph graphPmMnplus1 = bayesPmMnplus1.getGraph();
    //
    //        //BayesIm bayesImMn = null;         //Vestige
    //        BayesPm bayesPmMn = null;
    //        double oldBestScore = Double.NEGATIVE_INFINITY;
    //        int iteration = 0;
    //
    //        //Loop for n = 0,1,... until convergence
    //        while (!bayesPmMnplus1.equals(bayesPmMn)) {
    //            iteration++;
    //
    //            bayesPmMn = bayesPmMnplus1;
    //            System.out.println("In Factored Bayes Struct EM Iteration number " + iteration);
    //
    //            //Compute the MAP parameters for Mn given o.
    //            //EmBayesEstimator emBayesEst = new EmBayesEstimator(bayesPmMn, dataSet);
    //            //bayesImMn = emBayesEst.maximization(0.0001);
    //
    //            //Perform search over models...
    //            Graph graphMn = bayesPmMn.getGraph();
    //            Dag dagMn = new Dag(graphMn);
    //            List models = ModelGenerator.generate(graphMn);
    //
    //            //double bestScore = 0.0;
    //            //Initialize bestScore to the score of bayesPmMn
    //            //(whose graph is varied in the for loop).
    //
    //            double bestScore = factorScore(dagMn, bdeMetricCache);
    //
    //            for (Iterator itm = models.iterator(); itm.hasNext();) {
    //                Graph graph = (Graph) itm.next();
    //                //System.out.println("In iterate1 of FBSEM" + graph);
    //                Dag dag = new Dag(graph);
    //
    //
    //                BayesPm bayesPmTest = new BayesPm(dag);
    //
    //                //Having instantiated the BayesPm, set the number of categories correctly.
    //                for (int i = 0; i < dataSet.getVariables().size(); i++) {
    //                    String varName = (String) dataSet.getVariableNames().get(i);
    //                    Node node = dag.getNode(varName);
    //                    bayesPmTest.setNumSplits(node, ncategories[i]);
    //                }
    //
    //
    //                double score = factorScore(dag, bdeMetricCache);
    //
    //                System.out.println("For the model with graph \n" + dag);
    //                System.out.println("Score = " + score);
    //
    //                if (score <= bestScore) continue;  //This is not better than the best to date.
    //                bestScore = score;
    //                //oldBestScore = bestScore;
    //
    //                //Let M sub n+1 be the model with the highest score amonth those encountered
    //                //during the search.
    //                bayesPmMnplus1 = bayesPmTest;
    //            }
    //
    //            System.out.println("bestScore, oldBestScore " + bestScore + " " + oldBestScore);
    //
    //            //Test equality of graphs instead of scores.
    //
    //            //if(Math.abs(bestScore - oldBestScore) < 1.0e-40) convergence = true;
    //            oldBestScore = bestScore;
    //        }
    //
    //
    //        return bayesPmMn;
    //    }

    /**
     * Sets maximum time for iterations
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /**
     * This method allows specification of the tolerance parameter used in Bayes
     * EM estimation.
     */
    public BayesIm maximization(double tolerance) {
        TetradLogger.getInstance().log("details", "FactoredBayesStructuralEM.maximization()");
        this.tolerance = tolerance;
        return iterate();
    }

    /**
     * This iterate2 method also uses BdeMetricCache but it uses the
     * factorScoreMD method which can handle missing data and latent variables.
     * Ths method iteratively score models and finds that which contains the
     * graph of the highest scoring model (via its BaysPm) as well as parameters
     * which yield the best score given the dataset by using the
     * EmBayesEstimator class.
     *
     * @return the instantiated Bayes net (BayesIm)
     */
    public BayesIm iterate() {

        start = System.currentTimeMillis();

        //boolean convergence = false;        //Vestige
        BdeMetricCache bdeMetricCache = new BdeMetricCache(dataSet, bayesPmM0);

        BayesPm bayesPmMnplus1 = bayesPmM0;

        //BayesIm bayesImMn = null;         //Vestige
        BayesPm bayesPmMn = null;
        double oldBestScore = Double.NEGATIVE_INFINITY;
        int iteration = 0;

        //stop iterating if timeout has exceeded
        Timer timer = new Timer();

        //Loop for n = 0,1,... until convergence or timeout has been exceeded

        TimedIterate ti = new TimedIterate(bdeMetricCache, bayesPmMnplus1, bayesPmMn, oldBestScore, iteration, start);
        Thread tithread = new Thread(ti);
        tithread.start();
        if (timeout > 0) {
            //    timer.schedule(new InterruptScheduler(Thread.currentThread()), timeout);
        }
        try {
            tithread.join();
        }
        catch (InterruptedException e) {
        }

        bdeMetricCache = ti.bdeMetricCache;
        bayesPmMnplus1 = ti.bayesPmMnplus1;
        bayesPmMn = ti.bayesPmMn;
        oldBestScore = ti.oldBestScore;
        iteration = ti.iteration;
        bayesPmMn = bayesPmMnplus1;
        resultScore = oldBestScore;
        totaliterations = ti.iteration;

        EmBayesEstimator emBayesEst = new EmBayesEstimator(bayesPmMn, dataSet);
        return emBayesEst.maximization(tolerance);

    }

    public double returnScore() {
        return resultScore;
    }

    public Graph returnGraph() {
        return resultGraph;
    }

    public int returnIterations() {
        return totaliterations;
    }

    public void scoreTest() {
        TetradLogger.getInstance().log("details", "scoreTest");
        //System.out.println(bayesPmM0.getGraph());
        BdeMetricCache bdeMetricCache;

        BayesPm bayesPmMn = bayesPmM0;
        EmBayesEstimator emBayesEst = new EmBayesEstimator(bayesPmMn, dataSet);
        emBayesEst.maximization(0.0001);

        Dag dag0 = new Dag(bayesPmMn.getDag());

        //double score = factorScoreMD(dag0, bdeMetricCache, bayesPmMn, bayesImMn);

        //System.out.println("Score of dag0 = " + score);

        Node L1 = dag0.getNode("L1");
        Node X1 = dag0.getNode("X1");

        Dag dag1 = new Dag(dag0);
        dag1.addDirectedEdge(X1, L1);

        //BayesPm bayesPmTest = new BayesPm(dag0);
        //score = factorScoreMD(dag0, bdeMetricCache, bayesPmTest, bayesImMn);

        //System.out.println("Score of dag1 = " + score);

        BayesPm bayesPm0 = new BayesPm(dag0);
        EmBayesEstimator emBayesEst0 = new EmBayesEstimator(bayesPm0, dataSet);
        BayesIm bayesImMn0 = emBayesEst0.maximization(0.0001);

        BayesPm bayesPmTest0 = new BayesPm(dag0);

        TetradLogger.getInstance().log("details", "Observed conts for nodes of L1,X1,X2,X3 (no edges) " +
                "using the MAP parameters based on that same graph");

        TetradLogger.getInstance().log("details", "Graph of PM:  ");
        TetradLogger.getInstance().log("details", "" + bayesPmTest0.getDag());

        TetradLogger.getInstance().log("details", "Graph of IM:  ");
        TetradLogger.getInstance().log("details", "" + bayesImMn0.getBayesPm().getDag());

        bdeMetricCache = new BdeMetricCache(dataSet, bayesPmTest0);

        List<Node> nodes0 = dag0.getNodes();

        for (Node aNodes0 : nodes0) {
            double[][] counts0 = bdeMetricCache.getObservedCounts(aNodes0,
                    bayesPmTest0, bayesImMn0);
            for (double[] aCounts0 : counts0) {
                for (int j = 0; j < counts0[0].length; j++) {
                    System.out.print(" " + aCounts0[j]);
                }
                TetradLogger.getInstance().log("details", "\n");
            }
            TetradLogger.getInstance().log("details", "\n");
        }

        double score0 =
                factorScoreMD(dag0, bdeMetricCache, bayesPmTest0, bayesImMn0);

        TetradLogger.getInstance().log("details", "Score of L1,X1,X2,X3 (no edges) for itself = " + score0);

        TetradLogger.getInstance().log("details", "===============\n\n");

        TetradLogger.getInstance().log("details", "Score of X1-->L1 for L1,X1,X2,X3 (no edges) = " + score0);


        BayesPm bayesPmTest1 = new BayesPm(dag1);

        TetradLogger.getInstance().log("details", "Observed counts for nodes of X1-->L1 for L1,X1,X2,X3 (no edges)");

        TetradLogger.getInstance().log("details", "Graph of PM :  ");
        TetradLogger.getInstance().log("details", "" + bayesPmTest1.getDag());

        TetradLogger.getInstance().log("details", "Graph of IM:  ");
        TetradLogger.getInstance().log("details", "" + bayesImMn0.getBayesPm().getDag());

        bdeMetricCache = new BdeMetricCache(dataSet, bayesPmTest1);

        List<Node> nodes1 = dag0.getNodes();

        for (Node aNodes1 : nodes1) {

            double[][] counts1 = bdeMetricCache.getObservedCounts(aNodes1,
                    bayesPmTest1, bayesImMn0);
            for (double[] aCounts1 : counts1) {
                for (int j = 0; j < counts1[0].length; j++) {
                    TetradLogger.getInstance().log("details", " " + aCounts1[j]);
                }
                TetradLogger.getInstance().log("details", "\n");
            }
            TetradLogger.getInstance().log("details", "\n");
        }

        double score1 =
                factorScoreMD(dag1, bdeMetricCache, bayesPmTest1, bayesImMn0);

        TetradLogger.getInstance().log("details", "Score of X1-->L1 for L1,X1,X2,X3 (no edges) = " + score1);


    }

//    private static double factorScore(Dag dag, BdeMetricCache bdeMetricCache) {
//        List nodes = dag.getNodes();
//        double score = 1.0;
//        for (Iterator itn = nodes.iterator(); itn.hasNext();) {
//            Node node = (Node) itn.next();
//            List parents = dag.getParents(node);
//            Set parentsSet = new HashSet(parents);
//
//            //The null values of the last two arguments causes the score method
//            //which doesn't handle missing data and latent variables.
//            score += bdeMetricCache.scoreLnGam(node, parentsSet, null, null);
//        }
//        return score;
//    }

    /*
     * This scoring method uses factor caching and the log gamma scoring function that
     * handles missing data and latent variables.  The Bayes PM contains a graph which
     * indicates which variables are latent.
     */

    private static double factorScoreMD(Dag dag, BdeMetricCache bdeMetricCache,
                                        BayesPm bayesPm, BayesIm bayesIm) {
        List<Node> nodes = dag.getNodes();
        //double score = 1.0;

        double score = 0.0;   //Fast test 11/29/04

        for (Node node1 : nodes) {
            List<Node> parents = dag.getParents(node1);
            Set<Node> parentsSet = new HashSet<Node>(parents);
            double fScore = bdeMetricCache.scoreLnGam(node1, parentsSet,
                    bayesPm, bayesIm);

            //Debug print:
            TetradLogger.getInstance().log("details", "Score for factor " + node1.getName() + " = " + fScore);

            score += fScore;
        }
        return score;
    }

    public DataSet getDataSet() {
        return this.dataSet;
    }

    private class InterruptScheduler extends TimerTask {
        Thread target = null;

        public InterruptScheduler(Thread target) {
            this.target = target;
        }

        @Override
        public void run() {
            target.interrupt();
        }

    }

    private class TimedIterate implements Runnable {

        BdeMetricCache bdeMetricCache;
        BayesPm bayesPmMnplus1;
        BayesPm bayesPmMn;
        double oldBestScore;
        int iteration;
        double start;

        public TimedIterate(BdeMetricCache bdeMetricCache, BayesPm bayesPmMnplus1, BayesPm bayesPmMn, double oldBestScore, int iteration, double start) {
            this.bdeMetricCache = bdeMetricCache;
            this.bayesPmMnplus1 = bayesPmMnplus1;
            this.bayesPmMn = bayesPmMn;
            this.oldBestScore = oldBestScore;
            this.iteration = iteration;
            this.start = start;
        }

        public void run() {
            while (!bayesPmMnplus1.equals(bayesPmMn)) {

                if (System.currentTimeMillis() - this.start > timeout && timeout > 0) {
                    bayesPmMn = bayesPmMnplus1;
                    break;
                }

                iteration++;

                bayesPmMn = bayesPmMnplus1;
                TetradLogger.getInstance().log("details", "In Factored Bayes Struct EM Iteration number " +
                                iteration);

                //Compute the MAP parameters for Mn given o.
                TetradLogger.getInstance().log("details", "Starting EM Bayes estimator to get MAP parameters of Mn");
                EmBayesEstimator emBayesEst =
                        new EmBayesEstimator(bayesPmMn, dataSet);
                BayesIm bayesImMn = emBayesEst.maximization(tolerance);
                //System.out.println("Result:  ");
                //System.out.println(bayesImMn.getBayesPm().getGraph());
                //System.out.println(bayesImMn);
                TetradLogger.getInstance().log("details", "Estimation of MAP parameters of Mn complete. \n\n");

                //Perform search over models...
                Graph graphMn = bayesPmMn.getDag();
                Dag dagMn = new Dag(graphMn);
                List<Graph> models = ModelGenerator.generate(graphMn);

                //double bestScore = 0.0;
                //Initialize bestScore to the score of bayesPmMn
                //(whose graph is varied in the for loop).

                double bestScore =
                        factorScoreMD(dagMn, bdeMetricCache, bayesPmMn, bayesImMn);

                EdgeListGraph edges = new EdgeListGraph(dagMn);

                TetradLogger.getInstance().log("details", "Initial graph Mn = ");
                TetradLogger.getInstance().log("details", edges.toString());
                TetradLogger.getInstance().log("details", "Its score = " + bestScore);

                for (Graph model : models) {

                    //System.out.println("In iterate2() of FBSEM" + graph);
                    Dag dag = new Dag(model);


                    BayesPm bayesPmTest = new BayesPm(dag);

                    //Having instantiated the BayesPm, set the number of categories correctly.
                    for (int i = 0; i < dataSet.getVariables().size(); i++) {
                        String varName = dataSet.getVariableNames().get(i);
                        Node node = dag.getNode(varName);
                        bayesPmTest.setNumCategories(node, ncategories[i]);
                    }

                    //Create a BayesIm here?
                    //EmBayesEstimator embTest = new EmBayesEstimator(bayesPmTest, dataSet);
                    //BayesIm bayesImTest = embTest.maximization(0.0001);

                    double score = factorScoreMD(dag, bdeMetricCache, bayesPmTest,
                            bayesImMn);

                    EdgeListGraph edgesTest = new EdgeListGraph(dag);
                    TetradLogger.getInstance().log("details", "For the model with graph \n" + edgesTest);
                    TetradLogger.getInstance().log("details", "Model Score = " + score);

                    if (score <= bestScore) {
                        continue;    //This is not better than the best to date.
                    }
                    bestScore = score;
                    //oldBestScore = bestScore;

                    //Let M sub n+1 be the model with the highest score amonth those encountered
                    //during the search.
                    bayesPmMnplus1 = bayesPmTest;
                }

                TetradLogger.getInstance().log("details", "In iteration:  " + iteration);
                TetradLogger.getInstance().log("details", "bestScore, oldBestScore " + bestScore + " " +
                                oldBestScore);
                EdgeListGraph edgesBest =
                        new EdgeListGraph(bayesPmMnplus1.getDag());
                TetradLogger.getInstance().log("details", "Graph of model:  \n" + edgesBest);
                TetradLogger.getInstance().log("details", "====================================");

                //Test equality of graphs instead of scores.

                //if(   Math.abs(bestScore - oldBestScore) < 1.0e-40) convergence = true;
                oldBestScore = bestScore;
                resultGraph = edgesBest;

                //if(iteration == 1) System.exit(0);  //Temporary during development
            }
        }
        //boolean convergence = false;        //Vestige
    }
}





