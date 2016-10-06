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

package edu.cmu.tetrad.gene.tetrad.gene.algorithm.biolingua;

import edu.cmu.tetrad.gene.tetrad.gene.algorithm.util.SymMatrixF;

/**
 * Implements an algorithm for revising regulatory models with expression data.
 * This implementation is based on the description of the "BioLingua" tools
 * in:<p> </p> <a href="http://www.smi.stanford.edu/projects/helix/psb02/shrager.pdf"
 * target="_TOP"> <i>"Guiding Revision of Regulatory Models with Expression
 * Data"</i></a><br> by J.Shrager, P.Langley, A. Pohorille, published in
 * PSB-2002
 *
 * @author <a href="http://www.eecs.tulane.edu/Saavedra" target="_TOP">Raul
 *         Saavedra</a> (<a href="mailto:rsaavedr@ai.uwf.edu">rsaavedr@ai.uwf.edu</A>)
 */

public class Biolingua {
    private static final float ALMOST_ZERO = (float) 0.00001;
    //    private static final double LOG_2 = Math.log(2);

    // TODO: consider making the signif level another parameter of the algorithm
    private static final float SIGNIF_LEVEL = (float) 0.05;

    // TODO: these values are part of what is rather ad hoc and
    // questionable in the paper, I will report on different
    // behaviors of the algorithm having different values here.
    private static float bitsAnnotat = (float) .1;
    private static float bitsErrors = (float) 3;
    private static float bitsLinks = (float) 4;
    private static float bitsPredic = (float) 3;

    // TODO: add comments describing each of these static vars
    private static int nvars;
    //    private static BiolinguaDigraph origG;
    private static BiolinguaDigraph g;
    private static SymMatrixF cm;
    private static SymMatrixF sm;
    private static int[] path;
    private static boolean[] visited;
    private static boolean cycle;
    private static int targetParent;

    private static float emtempG;
    private static float emCurrentModel = 0;
    private static float em1StepBest = 0;

    private static int bestEnode1 = 0;
    private static int bestEnode2 = 0;
    private static int bestChange = 0;

    private static int pos;
    private static int neg;

    private Biolingua() {
        // Constructor method private just to prevent from instantiation of this class
    }

    /**
     * Runs the biolingua algorithm using the given correlation matrix (all
     * values are assumed significant) and the initial graph, and uses some
     * default values for the coefficients in the evaluation metric for
     * annotations, errors, links, and predictions. Returns the graph found
     * after the search stopped improving the evaluation metric. TODO: include
     * Javadoc explanations of k*
     */
    public static synchronized BiolinguaDigraph BiolinguaAlgorithm(
            SymMatrixF correlMatrix, BiolinguaDigraph initGraph) {
        // Run with some (quite arbitrary so far) default coefficients
        // TODO:  Make these coefficients a function of the # of vars
        float ka = (float) 0.1;
        float ke = (float) 3.0;
        float kl = (float) 4.0;
        float kp = (float) 3.0;
        return doBiolinguaAlgorithm(correlMatrix, null, initGraph, ka, ke, kl,
                kp);
    }

    /**
     * Runs the biolingua algorithm using the given correlation matrix (all
     * values are assumed significant), an initial graph, and the coefficients
     * in the evaluation metric for annotations, errors, links, and predictions.
     * Returns the graph found after the search stopped improving the evaluation
     * metric.
     */
    public static synchronized BiolinguaDigraph BiolinguaAlgorithm(
            SymMatrixF correlMatrix, BiolinguaDigraph initGraph,
            float vBitsAnnotat, float vBitsErrors, float vbitsLinks,
            float vBitsPredic) {
        return doBiolinguaAlgorithm(correlMatrix, null, initGraph, vbitsLinks,
                vBitsPredic, vBitsAnnotat, vBitsErrors);
    }

    /**
     * Runs the biolingua algorithm using the given correlation matrix,
     * significance matrix, the initial graph, and the coefficients in the
     * evaluation metric for annotations, errors, links, and predictions.
     * Returns the graph found after the search stopped improving the evaluation
     * metric.
     */
    public static synchronized BiolinguaDigraph BiolinguaAlgorithm(
            SymMatrixF correlMatrix, SymMatrixF signifMatrix,
            BiolinguaDigraph initGraph, float vBitsAnnotat, float vBitsErrors,
            float vbitsLinks, float vBitsPredic) {
        return doBiolinguaAlgorithm(correlMatrix, signifMatrix, initGraph,
                vbitsLinks, vBitsPredic, vBitsAnnotat, vBitsErrors);
    }

    private static BiolinguaDigraph doBiolinguaAlgorithm(
            SymMatrixF correlMatrix, SymMatrixF signifMatrix,
            BiolinguaDigraph initGraph, float vBitsAnnotat, float vBitsErrors,
            float vbitsLinks, float vBitsPredic) {
        /*
        // A null pointer exception will be thrown anyway if some of the
        // arguments are null, so this test is really not that necessary
        if ((correlMatrix==null) || (initGraph==null))
            throw new IllegalArgumentException ("Null argument");
        */
        nvars = correlMatrix.getSize();
        bitsAnnotat = vBitsAnnotat;
        bitsErrors = vBitsErrors;
        bitsLinks = vbitsLinks;
        bitsPredic = vBitsPredic;

        if (nvars != initGraph.getSize()) {
            throw new IllegalArgumentException("Incompatible # vars.: " +
                    nvars + " in Correl.Matrix, " + initGraph.getSize() +
                    " in initial graph.");
        }
        if ((signifMatrix != null) && (signifMatrix.getSize() != nvars)) {
            throw new IllegalArgumentException("Incompatible # vars.: " +
                    nvars + " in Correl.Matrix, " + signifMatrix.getSize() +
                    " in Significance Matrix.");
        }

        path = new int[nvars];
        visited = new boolean[nvars];
        //        origG = initGraph;
        g = new BiolinguaDigraph(initGraph);
        cm = correlMatrix;
        sm = signifMatrix;

        emCurrentModel = 0;
        em1StepBest = 0;
        bestEnode1 = 0;
        bestEnode2 = 0;
        bestChange = 0;

        emCurrentModel = Biolingua.evalCurrentModel();
        if (cycle) {
            throw new IllegalArgumentException("Starting graph has a cycle");
        }

        System.out.println("Initial eval metric = " + emCurrentModel);

        while (true) {
            // Go through all 1 step changes on getModel model,
            // computing the evaluation metric for each resulting model,
            // and keeping track of which change yields the best eval metric
            // One-step changes happen in the edges.  Given
            // two nodes A and B, the relationship between them can be
            // of 5 different kinds (index of A <= index of B): <p>
            //
            // A "one step change" in the model is a change of value
            // in one single edge (pair of variables) in the model. <p>
            //
            // Given that the underlying graph has n variables, there
            // are (n-1)*n/2 possible edges.  Since each pair of variables
            // is always in one of the 5 states mentioned above,
            // there are 4 possible changes to try for each pair, so in total
            // there are (n-1)*n*2 one-step changes to try.
            em1StepBest = emCurrentModel;
            //            int w = nvars - 1;
            for (int vi = 0; vi < nvars; vi++) {
                for (int vj = 0; vj < nvars; vj++) {
                    if (vi == vj) {
                        continue;
                    }
                    int origState = (int) g.getEdge(vi, vj);

                    // Try all 2 possible changes for this edge:
                    //
                    // vi ----(-1,0,+1)---> vj
                    //
                    for (int state = -1; state <= 1; state++) {
                        if (state == origState) {
                            continue;
                        }
                        g.setEdge(vi, vj, state);

                        //if (g.getNumParents (vj) > 1) {
                        //    System.out.println ("Node "+vj+" being tested with "+g.getNumParents(vj)+" parents");
                        //}

                        emtempG = Biolingua.evalCurrentModel();

                        // Check whether there is improvement to
                        // update our currently best one-step change
                        if ((!cycle) && (emtempG < em1StepBest)) {
                            bestEnode1 = vi;
                            bestEnode2 = vj;
                            bestChange = state;
                            em1StepBest = emtempG;
                        }
                    }

                    // Restore original edge state
                    g.setEdge(vi, vj, origState);
                }
            }

            // Stop condition: attempting all possible 1 step
            // changes on getModel model, we find no improvement in the
            // evaluation metric
            //
            if (em1StepBest >= emCurrentModel) {
                break;
            }

            // If code reaches this point then there was at least one
            // one-step change in the getModel Model that improved
            // the evaluation metric.  Let's change the model applying
            // that one step change, and let's update its eval metric
            // accordingly
            g.setEdge(bestEnode1, bestEnode2, bestChange);
            emCurrentModel = em1StepBest;
            System.out.println("Best eval metric so far = " + emCurrentModel);
        }
        // at this point G has best model found according to this
        // greedy search, and given the chosen eval. metric, the initial
        // graph, and the correlational data
        g.setGraphName("Biolingua result");
        return g;
    }

    /**
     * For each pair of variables (nodes) in the model check all undirectedPaths that
     * connect them and predict the sign of the correlation.  Each path is just
     * transformed into a sign by multiplying the signs on its links.  When the
     * predictions of all undirectedPaths between two nodes agree (sign-wise) then choose
     * that sign as the predicted correlation.<p> </p> When the signs predicted
     * by two or more undirectedPaths disagree, then find out which sign is "dominant"
     * (checking whether there are more + than - undirectedPaths, or the other way
     * around), and predict accordingly.<p> </p> Count the # of predictions that
     * agree with the input correlation matrix given to Biolingua, as well as
     * the # of errors (erroneous predictions) </p> After checking all undirectedPaths,
     * compute the evaluation metric
     */
    private static float evalCurrentModel() {
        int annotations = 0;
        int predictions = 0;
        int errors = 0;
        cycle = false;
        //        int w = nvars - 1;
        for (int i = 0; i < nvars; i++) {
            visited[i] = false;
        }
        for (int vi = 0; vi < nvars; vi++) {
            for (int vj = 0; vj < nvars; vj++) {
                if (vi == vj) {
                    continue;
                }

                // Find all undirectedPaths from vi to vj, counting the number of undirectedPaths
                // with a positive and negative predicted signs
                targetParent = vi;
                pos = 0;
                neg = 0;
                Biolingua.findPaths(vj, 0);

                // A cycle was found is this graph, return
                if (cycle) {
                    return -1;
                }

                // Predict correlation
                if (pos + neg > 0) {
                    // There is at least one path between the target parent
                    // and variable vj.

                    // If there are positive as well as negative undirectedPaths,
                    // increment the # of annotations by 1
                    if ((pos > 0) && (neg > 0)) {
                        annotations++;
                    }

                    // Determine the predicted sign according to all undirectedPaths
                    // between these variables
                    int predictedSign =
                            ((pos == neg) ? 0 : (pos > neg ? 1 : -1));

                    // Value of edge between vi and vj in the correlation matrix
                    float correlValue = Biolingua.cm.getValue(vi, vj);

                    if (sm != null) {
                        // Significance matrix is not null, so use the value
                        // from the correlation matrix if it's significant,
                        // otherwise use a zero
                        if (Biolingua.sm.getValue(vi, vj) > SIGNIF_LEVEL) {
                            correlValue = 0;
                        }
                    }

                    // Sign of that value
                    int correlMSign = (Biolingua.isZero(correlValue) ? 0 : (
                            correlValue > 0 ? 1 : -1));

                    if (correlMSign == predictedSign) {
                        predictions++;
                    }
                    else {
                        errors++;
                    }

                }
                else {
                    // No path found between those 2 vars in this graph.
                    // If there is a non-zero correlation among those 2
                    // variables, then increment the # of errors in this
                    // model by 1
                    if (!isZero(Biolingua.cm.getValue(vi, vj))) {
                        errors++;
                    }
                }

            }
        }
        // Compute evaluation metric for this model and return that value
        int nEdges = Biolingua.g.getNumEdges();
        float evalMetric = bitsLinks * nEdges + bitsAnnotat * annotations +
                bitsErrors * errors - bitsPredic * predictions;

        return evalMetric;
    }

    // Think about a new name for this method, since it not only "finds"
    // undirectedPaths, but updates all these counters used  in the eval. metric.
    private static void findPaths(int vj, int pathLen) {

        if (visited[vj]) {
            // That node is already in the path,
            // so there is a cycle in this graph
            cycle = true;
            return;
        }
        path[pathLen] = vj;
        if (vj == targetParent) {
            // Debugged: 01/14/2002
            // This check is still needed to see whether there is a
            // closed loop between targetParent and path[0]
            if (Biolingua.g.isParent(path[0], targetParent)) {
                cycle = true;
                return;
            }

            // ==========================
            // Path Found, process path
            // ==========================

            // We have a path from vj ( == targetParent) to visited[0].
            // Count the number of negative edges
            int negEdges = 0;
            for (int i = 1; i <= pathLen; i++) {
                if (Biolingua.g.getEdge(path[i], path[i - 1]) < 0) {
                    negEdges++;
                }
            }
            // Update the appropriate counter (pos, or neg) depending on
            // whether the whole path ends up positive or negative
            if (negEdges % 2 == 0) {
                pos++;  // It's a positive path (even # of negative edges)
            }
            else {
                neg++;  // It's a negative path
            }
        }
        else {
            // Get parents of this node
            int[] parent = Biolingua.g.getParents(vj);
            visited[vj] = true;
            int np = parent.length;
            for (int p = 0; p < np; p++) {
                // Recursive call to findPaths()
                Biolingua.findPaths(parent[p], pathLen + 1);
                if (cycle) {
                    return;
                }
            }
            visited[vj] = false;
        }
    }

    //    private static void dumpPath() {
    //        int i = 0;
    //        while (path[i] != targetParent) {
    //            System.out.print("" + path[i++] + " ");
    //        }
    //        System.out.println(path[i]);
    //    }

    //    private static String pathToStr(int j) {
    //        String s = "";
    //        for (int i = 0; i <= j; i++) s = s + path[i] + " ";
    //        return s;
    //    }


    private static boolean isZero(float x) {
        return (Math.abs(x) <= ALMOST_ZERO);
    }

}




