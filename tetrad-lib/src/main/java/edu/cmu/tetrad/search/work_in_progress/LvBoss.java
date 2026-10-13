///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * LV-BOSS: a fully score-based latent-variable search that embeds the MAG projection inside a
 * BOSS-style search over orders. The state is a total order of the observed variables; each order is
 * projected to a high-scoring ancestral graph by {@link MagOrderProjection}, and the outer loop
 * relocates one variable at a time to its best position, exactly as BOSS does, keeping the
 * best-scoring order found. Candidate relocations are projected warm-started from the incumbent
 * graph, since successive orders differ by one move and most of the incumbent remains legal and
 * right.
 *
 * <p><b>Procedure.</b> For each restart: take a starting order (the variables as given for the first
 * restart; seeded shuffles after); project it cold; then repeat passes over the variables, and for
 * each variable try every other position, projecting each candidate order warm-started from the
 * incumbent; adopt the best strictly improving relocation; stop when a full pass improves nothing.
 * The best graph over all restarts is returned. Deterministic given the seed.</p>
 *
 * <p><b>Honest status.</b> Everything about search quality is empirical: there is no consistency
 * theorem for any greedy search over MAG space (the Meek-conjecture analog for MAGs is open), the
 * projection is itself a hill climb, and the stage-1 finding stands that even a perfect search would
 * not always return the true MAG's class, because a latent projection's distribution can satisfy
 * rank-type constraints that let a sparser ancestral graph contain it with fewer parameters. The
 * oracle tests assert what has been measured: from random starting orders at the population, the
 * output's score never falls below the true MAG's, and the true class is recovered at close to the
 * stage-1 true-order rate.</p>
 *
 * @author josephramsey
 * @see MagOrderProjection
 * @see MagRicfBicScorer
 */
@Experimental
public class LvBoss {

    /**
     * The scorer; fixes the variable set.
     */
    private final MagRicfBicScorer scorer;

    /**
     * The projection built over the scorer.
     */
    private final MagOrderProjection projection;

    /**
     * Number of restarts; the first uses the variables as given, the rest seeded shuffles.
     */
    private int numStarts = 1;

    /**
     * Seed for the restart shuffles.
     */
    private long seed = 42L;

    /**
     * Minimum score improvement for a relocation to be adopted.
     */
    private double improvementEpsilon = 1e-6;

    /**
     * Cap on passes over the variables per restart, as a safety net.
     */
    private int maxPasses = 100;

    /**
     * The best order found by the last search.
     */
    private List<Node> bestOrder = null;

    /**
     * The score of the best graph found by the last search.
     */
    private double bestScore = Double.NaN;

    /**
     * Constructs the search over the given scorer.
     *
     * @param scorer the MAG BIC scorer; its covariance matrix fixes the variable set.
     */
    public LvBoss(MagRicfBicScorer scorer) {
        this.scorer = Objects.requireNonNull(scorer, "Scorer not specified.");
        this.projection = new MagOrderProjection(scorer);
    }

    /**
     * Runs the search and returns the best graph found.
     *
     * @return the best-scoring ancestral graph found, over the scorer's variables.
     */
    public Graph search() {
        List<Node> variables = new ArrayList<>(this.scorer.getVariables());
        int p = variables.size();

        Random random = new Random(this.seed);

        Graph globalBest = null;
        double globalBestScore = Double.NEGATIVE_INFINITY;
        List<Node> globalBestOrder = null;

        boolean priorSkip = this.scorer.isSkipChecks();
        this.scorer.setSkipChecks(true);

        try {
            for (int start = 0; start < this.numStarts; start++) {
                List<Node> order = new ArrayList<>(variables);
                if (start > 0) Collections.shuffle(order, random);

                Graph g = this.projection.project(order);
                double score = this.scorer.score(g);

                boolean improved = true;
                int passes = 0;

                while (improved && passes++ < this.maxPasses) {
                    improved = false;

                    for (int vi = 0; vi < p; vi++) {
                        Node v = order.get(vi);

                        double bestRelocScore = score;
                        int bestPos = -1;
                        Graph bestRelocGraph = null;

                        for (int pos = 0; pos < p; pos++) {
                            if (pos == vi) continue;

                            List<Node> candidate = new ArrayList<>(order);
                            candidate.remove(vi);
                            candidate.add(pos, v);

                            Graph candidateGraph = this.projection.project(candidate, g);
                            double candidateScore = this.scorer.score(candidateGraph);

                            // Strict > keeps the first-found position among ties: deterministic.
                            if (candidateScore > bestRelocScore + this.improvementEpsilon) {
                                bestRelocScore = candidateScore;
                                bestPos = pos;
                                bestRelocGraph = candidateGraph;
                            }
                        }

                        if (bestPos >= 0) {
                            order.remove(vi);
                            order.add(bestPos, v);
                            g = bestRelocGraph;
                            score = bestRelocScore;
                            improved = true;
                        }
                    }
                }

                if (score > globalBestScore) {
                    globalBestScore = score;
                    globalBest = g;
                    globalBestOrder = new ArrayList<>(order);
                }
            }
        } finally {
            this.scorer.setSkipChecks(priorSkip);
        }

        this.bestOrder = globalBestOrder;
        this.bestScore = globalBestScore;

        // Return a copy detached from the search's working state.
        Graph out = new EdgeListGraph(globalBest.getNodes());
        for (Edge e : globalBest.getEdges()) out.addEdge(e);
        return out;
    }

    /**
     * Sets the number of restarts; the first uses the variables as given, the rest seeded shuffles.
     *
     * @param numStarts a positive count.
     */
    public void setNumStarts(int numStarts) {
        if (numStarts < 1) throw new IllegalArgumentException("Number of starts must be at least 1.");
        this.numStarts = numStarts;
    }

    /**
     * Sets the seed for the restart shuffles.
     *
     * @param seed the seed.
     */
    public void setSeed(long seed) {
        this.seed = seed;
    }

    /**
     * Sets the minimum score improvement for a relocation to be adopted.
     *
     * @param improvementEpsilon a positive threshold.
     */
    public void setImprovementEpsilon(double improvementEpsilon) {
        if (!(improvementEpsilon > 0.0)) throw new IllegalArgumentException("Epsilon must be positive.");
        this.improvementEpsilon = improvementEpsilon;
    }

    /**
     * Sets the cap on passes over the variables per restart.
     *
     * @param maxPasses a positive cap.
     */
    public void setMaxPasses(int maxPasses) {
        if (maxPasses < 1) throw new IllegalArgumentException("Cap must be at least 1.");
        this.maxPasses = maxPasses;
    }

    /**
     * The projection used by the search, for configuring its caps.
     *
     * @return the projection.
     */
    public MagOrderProjection getProjection() {
        return this.projection;
    }

    /**
     * The best order found by the last search, or null before any search.
     *
     * @return the order.
     */
    public List<Node> getBestOrder() {
        return this.bestOrder == null ? null : new ArrayList<>(this.bestOrder);
    }

    /**
     * The score of the best graph found by the last search, or NaN before any search.
     *
     * @return the score.
     */
    public double getBestScore() {
        return this.bestScore;
    }
}
