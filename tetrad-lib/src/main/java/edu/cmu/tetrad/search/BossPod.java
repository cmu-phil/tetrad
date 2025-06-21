/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //i
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
package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.MagToPag;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.List;

/**
 * BOSS-POD is a class that implements the IGraphSearch interface. The BOSS-POD algorithm finds the BOSS DAG for
 * the dataset and then simply reports the PAG (Partially Ancestral Graph) structure of the BOSS DAG, without
 * doing any further latent variable reasoning.
 *
 * @author josephramsey
 */
public final class BossPod implements IGraphSearch {
    /**
     * The score.
     */
    private final Score score;
    /**
     * The background knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * The number of starts for GRaSP.
     */
    private int numStarts = 1;
    /**
     * Whether to use data order.
     */
    private boolean useDataOrder = true;
    /**
     * This flag represents whether the Bes algorithm should be used in the search.
     * <p>
     * If set to true, the Bes algorithm will be used. If set to false, the Bes algorithm will not be used.
     * <p>
     * By default, the value of this flag is false.
     */
    private boolean useBes = false;
    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose;
    private int depth = -1;
    private boolean completeRuleSetUsed = true;

    /**
     * BOSS-POD constructor. Initializes a new object of FCIT search algorithm with the given IndependenceTest and
     * Score object.
     *
     * @param score The Score object to be used for scoring DAGs.
     * @throws NullPointerException if score is null.
     */
    public BossPod(Score score) {
        if (score == null) {
            throw new NullPointerException();
        }

        this.score = score;
    }

    /**
     * Run the search and return s a PAG.
     *
     * @return The PAG.
     */
    public Graph search() throws InterruptedException {
        List<Node> nodes = this.score.getVariables();

        if (nodes == null) {
            throw new NullPointerException("Nodes from test were null.");
        }

        if (verbose) {
            TetradLogger.getInstance().log("===Starting BOSS-POD===");
        }

        if (verbose) {
            TetradLogger.getInstance().log("Starting BOSS.");
        }

        // BOSS seems to be doing better here.
        var suborderSearch = new Boss(score);
        suborderSearch.setResetAfterBM(true);
        suborderSearch.setResetAfterRS(true);
        suborderSearch.setVerbose(false);
        suborderSearch.setUseBes(useBes);
        suborderSearch.setUseDataOrder(useDataOrder);
        suborderSearch.setNumStarts(numStarts);
        suborderSearch.setVerbose(verbose);
        var permutationSearch = new PermutationSearch(suborderSearch);
        permutationSearch.setKnowledge(knowledge);
        var dag = permutationSearch.search(false);

        if (verbose) {
            TetradLogger.getInstance().log("Finished BOSS.");
        }

        if (verbose) {
            TetradLogger.getInstance().log("Calculating PAG from CPDAG.");
        }

        MagToPag dagToPag = new MagToPag(GraphTransforms.dagToMag(dag));
        dagToPag.setVerbose(verbose);
        dagToPag.setCompleteRuleSetUsed(completeRuleSetUsed);
        dagToPag.setKnowledge(knowledge);
        Graph pag = dagToPag.convert();

        if (verbose) {
            TetradLogger.getInstance().log("Finished calculating PAG from CPDAG.");
        }

        if (verbose) {
            TetradLogger.getInstance().log("BOSS-POD finished.");
        }

        return pag;
    }

    /**
     * Sets the knowledge used in search.
     *
     * @param knowledge This knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets the verbosity level of the search algorithm.
     *
     * @param verbose true to enable verbose mode, false to disable it
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the number of starts for BOSS.
     *
     * @param numStarts The number of starts.
     */
    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    /**
     * Sets whether the search algorithm should use the order of the data set during the search.
     *
     * @param useDataOrder true if the algorithm should use the data order, false otherwise
     */
    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
    }

    /**
     * Sets whether to use the BES (Backward Elimination Search) algorithm during the search.
     *
     * @param useBes true to use the BES algorithm, false otherwise
     */
    public void setUseBes(boolean useBes) {
        this.useBes = useBes;
    }

    /**
     * True if the complete final FCI rule set (Zhang 2008) should be used, false if the Spirtes ruleset
     * from Causation, Prediction and Search (2000) should be used.
     *
     * @param completeRuleSetUsed True if the complete ruleset should be used.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }
}
