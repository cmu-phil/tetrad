///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;

/**
 * Uses FGES for the initial step of *-FCI. This is an adjustment to the original GFCI algorithm here:
 * <p>
 * Ogarrio, J. M., Spirtes, P., &amp; Ramsey, J. (2016, August). A hybrid causal search algorithm for latent variable
 * models. In Conference on probabilistic graphical models (pp. 368-379). PMLR.
 * <p>
 * See *-FCI for the modifications used. *-FCI requires a test; FGES requires a score, so both are needed.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author Juan Miguel Ogarrio
 * @author peterspirtes
 * @author josephramsey
 * @version $Id: $Id
 * @see StarFci
 * @see Fges
 * @see Knowledge
 */
public final class FgesFci extends StarFci {
    /**
     * The score used in search.
     */
    private final Score score;
    /**
     * The maximum degree of the output graph.
     */
    private int maxDegree = -1;
    /**
     * The print stream used for output.
     */
    private transient PrintStream out = System.out;
    /**
     * Whether one-edge faithfulness is assumed.
     */
    private boolean faithfulnessAssumed = true;
    /**
     * The number of threads to use in the search. Must be at least 1.
     */
    private int numThreads = 1;

    /**
     * Constructs a new GFci algorithm with the given independence test and score.
     *
     * @param test  The independence test to use.
     * @param score The score to use.
     */
    public FgesFci(IndependenceTest test, Score score) {
        super(test);
        if (score == null) {
            throw new NullPointerException();
        }
        this.score = score;
    }

    public Graph getMarkovCpdag() throws InterruptedException {
        if (isVerbose()) {
            TetradLogger.getInstance().log("Starting FGES.");
        }

        Fges fges = new Fges(this.score);
        fges.setKnowledge(getKnowledge());
        fges.setVerbose(isVerbose());
        fges.setFaithfulnessAssumed(this.faithfulnessAssumed);
        fges.setMaxDegree(this.maxDegree);
        fges.setOut(this.out);
        fges.setNumThreads(numThreads);
        Graph cpdag = fges.search();

        if (isVerbose()) {
            TetradLogger.getInstance().log("Finished FGES.");
        }

        return cpdag;
    }

    /**
     * Sets the maximum indegree of the output graph.
     *
     * @param maxDegree This maximum.
     */
    public void setMaxDegree(int maxDegree) {
        if (maxDegree < -1) {
            throw new IllegalArgumentException("Depth must be -1 (unlimited) or >= 0: " + maxDegree);
        }

        this.maxDegree = maxDegree;
    }

    /**
     * Sets the print stream used for output, default System.out.
     *
     * @param out This print stream.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * Sets whether one-edge faithfulness is assumed. For FGES
     *
     * @param faithfulnessAssumed True, if so.
     * @see Fges#setFaithfulnessAssumed(boolean)
     */
    public void setFaithfulnessAssumed(boolean faithfulnessAssumed) {
        this.faithfulnessAssumed = faithfulnessAssumed;
    }

    /**
     * Sets the number of threads to use in the search.
     *
     * @param numThreads The number of threads to use. Must be at least 1.
     */
    public void setNumThreads(int numThreads) {
        if (numThreads < 1) {
            throw new IllegalArgumentException("Number of threads must be at least 1: " + numThreads);
        }
        this.numThreads = numThreads;
    }
}

