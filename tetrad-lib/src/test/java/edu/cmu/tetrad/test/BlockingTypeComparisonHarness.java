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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

/**
 * Compares the two {@link R0R4StrategyTestBased.BlockingType} options for the R4 discriminating
 * path rule on cost and on endpoint accuracy, under both an m-separation oracle and a Fisher Z
 * test on simulated data.
 *
 * <p>Each replication starts from the true PAG with every endpoint set to a circle, so that the
 * measurement isolates the orientation rules from adjacency search error. Reported per blocking
 * type:
 *
 * <ul>
 *   <li>{@code R4 calls} -- number of discriminating-path orientation calls. A blocking type that
 *       reports a change without making one inflates this, because {@code FciOrient.ruleR4}
 *       re-enumerates and re-runs until no task reports a change.
 *   <li>{@code idle reports} -- calls that returned true without altering an endpoint. This should
 *       be zero; a nonzero count means R4 is paying for passes that do no work.
 *   <li>endpoint accuracy against the true PAG, and wall time.
 * </ul>
 *
 * <p>Run directly via {@code main}. System properties: {@code reps} (default 60), {@code seed}
 * (default 913), {@code vars} (default 12), {@code n} (default 1000), {@code alpha}
 * (default 0.01).
 *
 * @author josephramsey
 */
public class BlockingTypeComparisonHarness {

    /**
     * Private constructor; this class is a runnable harness, not a utility to instantiate.
     */
    private BlockingTypeComparisonHarness() {
    }

    /**
     * Runs the comparison.
     *
     * @param args ignored; configuration is by system property
     * @throws Exception if data simulation or search fails
     */
    public static void main(String[] args) throws Exception {
        int reps = Integer.getInteger("reps", 60);
        int vars = Integer.getInteger("vars", 12);
        int n = Integer.getInteger("n", 1000);
        long seed = Long.getLong("seed", 913L);
        double alpha = Double.parseDouble(System.getProperty("alpha", "0.01"));

        System.out.printf("reps=%d vars=%d n=%d alpha=%s seed=%d%n%n", reps, vars, n, alpha, seed);

        for (boolean oracle : new boolean[]{true, false}) {
            RandomUtil.getInstance().setSeed(seed);

            List<Graph> dags = new ArrayList<>();
            List<DataSet> datasets = new ArrayList<>();
            List<Graph> truePags = new ArrayList<>();

            for (int r = 0; r < reps; r++) {
                List<Node> nodes = new ArrayList<>();
                for (int i = 0; i < vars; i++) nodes.add(new ContinuousVariable("X" + (i + 1)));

                Graph dag = RandomGraph.randomGraph(nodes, 2, (int) (1.5 * vars),
                        100, 100, 100, false);
                dags.add(dag);
                datasets.add(oracle ? null
                        : DataTransforms.restrictToMeasured(new SemIm(new SemPm(dag)).simulateData(n, false)));
                truePags.add(GraphTransforms.dagToPag(dag, false, 600000));
            }

            System.out.println(oracle ? "== m-separation oracle ==" : "== Fisher Z on simulated data ==");

            for (R0R4StrategyTestBased.BlockingType type : R0R4StrategyTestBased.BlockingType.values()) {
                int calls = 0, idle = 0, correct = 0, wrong = 0, circles = 0, exact = 0;
                long nanos = 0;

                for (int r = 0; r < reps; r++) {
                    Graph out = new EdgeListGraph(truePags.get(r));
                    Auditor auditor = new Auditor(strategy(dags.get(r), datasets.get(r), alpha, type));

                    long t0 = System.nanoTime();
                    orient(auditor, out);
                    nanos += System.nanoTime() - t0;

                    calls += auditor.calls;
                    idle += auditor.idleReports;

                    int[] s = score(out, truePags.get(r));
                    correct += s[0];
                    wrong += s[1];
                    circles += s[2];
                    if (out.equals(truePags.get(r))) exact++;
                }

                System.out.printf("  %-10s R4 calls=%-5d idle reports=%-5d  exact PAGs=%d/%d  "
                                  + "endpoints correct=%-5d wrong=%-5d circlesLeft=%-4d  time=%.2fs%n",
                        type, calls, idle, exact, reps, correct, wrong, circles, nanos / 1e9);
            }

            System.out.println();
        }
    }

    private static R0R4StrategyTestBased strategy(Graph dag, DataSet data, double alpha,
                                                  R0R4StrategyTestBased.BlockingType type) {
        IndependenceTest test = (data == null) ? new MsepTest(dag) : new IndTestFisherZ(data, alpha);
        R0R4StrategyTestBased strategy = (R0R4StrategyTestBased)
                R0R4StrategyTestBased.specialConfiguration(test, new Knowledge(), false);
        strategy.setDepth(-1);
        strategy.setMaxLength(-1);
        strategy.setBlockingType(type);
        return strategy;
    }

    private static void orient(R0R4Strategy strategy, Graph out) {
        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setCompleteRuleSetUsed(true);
        fciOrient.setRecursiveDepth(-1);
        fciOrient.setMaxDiscriminatingPathLength(-1);
        fciOrient.setVerbose(false);

        out.reorientAllWith(Endpoint.CIRCLE);

        try {
            fciOrient.ruleR0(out, new HashSet<>(), false);
            fciOrient.finalOrientation(out);
        } catch (RuntimeException e) {
            // FciOrient swallows per-task failures internally; match that here.
        }
    }

    /**
     * Endpoint tally over adjacencies shared with the true PAG: correct, wrong, and still a circle
     * where the true PAG has a definite mark.
     */
    private static int[] score(Graph est, Graph truth) {
        int correct = 0, wrong = 0, circles = 0;

        for (Edge e : truth.getEdges()) {
            Node a = e.getNode1(), b = e.getNode2();
            if (!est.isAdjacentTo(a, b)) continue;

            for (int k = 0; k < 2; k++) {
                Node from = k == 0 ? a : b, to = k == 0 ? b : a;
                Endpoint t = truth.getEndpoint(from, to);
                Endpoint s = est.getEndpoint(from, to);

                if (s == Endpoint.CIRCLE && t != Endpoint.CIRCLE) circles++;
                else if (s == t) correct++;
                else wrong++;
            }
        }

        return new int[]{correct, wrong, circles};
    }

    /**
     * Delegating strategy that counts discriminating-path calls and, of those, how many reported a
     * change without actually moving an endpoint.
     */
    private static class Auditor implements R0R4Strategy {
        private final R0R4StrategyTestBased inner;
        private int calls = 0;
        private int idleReports = 0;

        Auditor(R0R4StrategyTestBased inner) {
            this.inner = inner;
        }

        @Override
        public boolean isUnshieldedCollider(Graph graph, Node a, Node b, Node c) {
            return inner.isUnshieldedCollider(graph, a, b, c);
        }

        @Override
        public Pair<DiscriminatingPath, Boolean> doDiscriminatingPathOrientation(
                DiscriminatingPath dp, int recursiveDepth, int maxDiscriminatingPathLength,
                Graph graph, Set<Node> vNodes) throws InterruptedException {
            calls++;

            Endpoint atVfromY = graph.getEndpoint(dp.getY(), dp.getV());
            Endpoint atVfromW = graph.getEndpoint(dp.getW(), dp.getV());

            Pair<DiscriminatingPath, Boolean> result = inner.doDiscriminatingPathOrientation(
                    dp, recursiveDepth, maxDiscriminatingPathLength, graph, vNodes);

            if (result.getRight()
                && graph.getEndpoint(dp.getY(), dp.getV()) == atVfromY
                && graph.getEndpoint(dp.getW(), dp.getV()) == atVfromW) {
                idleReports++;
            }

            return result;
        }

        @Override
        public void setKnowledge(Knowledge knowledge) {
            inner.setKnowledge(knowledge);
        }

        @Override
        public Knowledge getknowledge() {
            return inner.getknowledge();
        }
    }
}
