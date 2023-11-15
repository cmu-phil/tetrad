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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.ScoreIndTest;
import edu.cmu.tetrad.util.MillisecondTimes;

import java.util.Collections;

/**
 * <p>Runs Fast Adjacency Search (FAS) and then orients each edge using the robust
 * skew orientation algorithm. Checks are done for adding two-cycles. The two-cycle checks do not require
 * non-Gaussianity. The robust skew orientation of edges left or right does.</p>
 *
 * <p>Moving this to the work_in_progress package because the functionality can be
 * generalized. Instead of hard-coding FAS, an arbitrary algorithm can be used to obtain adjacencies. Instead of
 * hard-coding robust skew, and arbitrary algorithm can be used to to pairwise orientation. Instead of orienting all
 * edges, an option can be given to just orient the edges that are unoriented in the input graph (see, e.g., PC LiNGAM).
 * This was an early attempt at this. For BOSS-LiNGAM, see this paper:</p>
 *
 * <p>Hoyer, P. O., Hyvarinen, A., Scheines, R., Spirtes, P. L., Ramsey, J., Lacerda, G.,
 * &amp; Shimizu, S. (2012). Causal discovery of linear acyclic models with arbitrary distributions. arXiv preprint
 * arXiv:1206.3260.</p>
 *
 * @author josephramsey
 * @see Fas
 * @see Fask
 */
public final class FasLofs implements IGraphSearch {

    private final Lofs.Rule rule;
    // The data sets being analyzed. They must all have the same variables and the same
    // number of records.
    private final DataSet dataSet;
    // Elapsed time of the search, in milliseconds.
    private long elapsed;
    // For the Fast Adjacency Search.
    private int depth = -1;

    // For the SEM BIC score, for the Fast Adjacency Search.
    private double penaltyDiscount = 1;

    // Knowledge the the search will obey, of forbidden and required edges.
    private Knowledge knowledge = new Knowledge();

    /**
     * @param dataSet These datasets to analyze.
     */
    public FasLofs(DataSet dataSet, Lofs.Rule rule) {
        this.dataSet = dataSet;
        this.rule = rule;
    }

    //======================================== PUBLIC METHODS ====================================//

    /**
     * Runs the search on the concatenated data, returning a graph, possibly cyclic, possibly with two-cycles. Runs the
     * fast adjacency search (FAS, Spirtes et al., 2000) follows by a modification of the robust skew rule (Pairwise
     * Likelihood Ratios for Estimation of Non-Gaussian Structural Equation Models, Smith and Hyvarinen), together with
     * some heuristics for orienting two-cycles.
     *
     * @return the graph. Some of the edges may be undirected (though it shouldn't be many in most cases) and some of
     * the adjacencies may be two-cycles.
     */
    public Graph search() {
        long start = MillisecondTimes.timeMillis();

        SemBicScore score = new SemBicScore(new CovarianceMatrix(this.dataSet));
        score.setPenaltyDiscount(this.penaltyDiscount);
        IndependenceTest test = new ScoreIndTest(score, this.dataSet);

        System.out.println("FAS");

        Fas fas = new Fas(test);
        fas.setStable(true);
        fas.setDepth(getDepth());
        fas.setVerbose(false);
        fas.setKnowledge(this.knowledge);
        Graph G0 = fas.search();

        System.out.println("LOFS orientation, rule " + this.rule);

        Lofs lofs2 = new Lofs(G0, Collections.singletonList(this.dataSet));
        lofs2.setRule(this.rule);
        lofs2.setKnowledge(this.knowledge);
        Graph graph = lofs2.orient();

        System.out.println("Done");

        long stop = MillisecondTimes.timeMillis();
        this.elapsed = stop - start;

        return graph;
    }

    /**
     * @return The depth of search for the Fast Adjacency Search (FAS).
     */
    public int getDepth() {
        return this.depth;
    }

    /**
     * @param depth The depth of search for the Fast Adjacency Search (S). The default is -1. unlimited. Making this too
     *              high may results in statistical errors.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * @return The elapsed time in milliseconds.
     */
    public long getElapsedTime() {
        return this.elapsed;
    }

    /**
     * @return Returns the penalty discount used for the adjacency search. The default is 1, though a higher value is
     * recommended, say, 2, 3, or 4.
     */
    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    /**
     * @param penaltyDiscount Sets the penalty discount used for the adjacency search. The default is 1, though a higher
     *                        value is recommended, say, 2, 3, or 4.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * @return the current knowledge.
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * @param knowledge Knowledge of forbidden and required edges.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }
}






