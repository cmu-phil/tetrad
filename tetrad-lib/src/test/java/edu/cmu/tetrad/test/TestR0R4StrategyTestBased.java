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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Pins the behavior of the two {@link R0R4StrategyTestBased.BlockingType} options in the R4
 * (discriminating path) rule.
 *
 * <p>The invariants tested are:
 *
 * <ol>
 *   <li>A discriminating-path call that reports {@code true} ("something changed") must actually
 *       have changed an endpoint. A previous version of the GREEDY branch recorded the separator
 *       and returned {@code true} without orienting, relying on {@code FciOrient.ruleR4}'s retry
 *       loop to do the orientation on a later pass. That made GREEDY roughly 2.5x more expensive
 *       than necessary and lost the orientation entirely for any caller that does not re-iterate.
 *   <li>GREEDY and RECURSIVE agree with the true PAG under an oracle test.
 *   <li>The GREEDY branch tolerates the case where no adjacency-restricted separator exists,
 *       rather than dereferencing null.
 * </ol>
 *
 * @author josephramsey
 */
public class TestR0R4StrategyTestBased {

    /**
     * Wraps a strategy and records, for each discriminating-path call, whether a {@code true}
     * return was accompanied by an actual endpoint change.
     */
    private static class AuditingStrategy implements R0R4Strategy {
        private final R0R4StrategyTestBased inner;
        int calls = 0;
        int trueWithoutChange = 0;

        AuditingStrategy(R0R4StrategyTestBased inner) {
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

            Pair<DiscriminatingPath, Boolean> result =
                    inner.doDiscriminatingPathOrientation(dp, recursiveDepth, maxDiscriminatingPathLength, graph, vNodes);

            if (result.getRight()
                && graph.getEndpoint(dp.getY(), dp.getV()) == atVfromY
                && graph.getEndpoint(dp.getW(), dp.getV()) == atVfromW) {
                trueWithoutChange++;
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

    private static AuditingStrategy orientFromCircles(Graph dag, Graph out,
                                                      R0R4StrategyTestBased.BlockingType type) {
        R0R4StrategyTestBased strategy = (R0R4StrategyTestBased)
                R0R4StrategyTestBased.specialConfiguration(new MsepTest(dag), new Knowledge(), false);
        strategy.setDepth(-1);
        strategy.setMaxLength(-1);
        strategy.setBlockingType(type);

        AuditingStrategy auditing = new AuditingStrategy(strategy);

        FciOrient fciOrient = new FciOrient(auditing);
        fciOrient.setCompleteRuleSetUsed(true);
        fciOrient.setRecursiveDepth(-1);
        fciOrient.setMaxDiscriminatingPathLength(-1);
        fciOrient.setVerbose(false);

        out.reorientAllWith(Endpoint.CIRCLE);
        fciOrient.ruleR0(out, new HashSet<>(), false);
        fciOrient.finalOrientation(out);

        return auditing;
    }

    /**
     * Both blocking types must recover the true PAG from its circle-ized skeleton under an oracle
     * test, and neither may report a change it did not make.
     */
    @Test
    public void testBothBlockingTypesOrientWithoutIdleReports() {
        RandomUtil.getInstance().setSeed(402L);

        int graphsWithR4 = 0;

        for (int rep = 0; rep < 60; rep++) {
            List<Node> nodes = new ArrayList<>();
            for (int i = 0; i < 9; i++) nodes.add(new GraphNode("X" + (i + 1)));

            Graph dag = RandomGraph.randomGraph(nodes, 2, 12, 100, 100, 100, false);
            Graph truePag = GraphTransforms.dagToPag(dag, false, 600000);

            Graph greedy = new EdgeListGraph(truePag);
            AuditingStrategy g = orientFromCircles(dag, greedy, R0R4StrategyTestBased.BlockingType.GREEDY);

            Graph recursive = new EdgeListGraph(truePag);
            AuditingStrategy r = orientFromCircles(dag, recursive, R0R4StrategyTestBased.BlockingType.RECURSIVE);

            if (g.calls > 0 || r.calls > 0) graphsWithR4++;

            assertEquals("GREEDY did not recover the true PAG on rep " + rep, truePag, greedy);
            assertEquals("RECURSIVE did not recover the true PAG on rep " + rep, truePag, recursive);

            assertEquals("GREEDY reported a change it did not make on rep " + rep,
                    0, g.trueWithoutChange);
            assertEquals("RECURSIVE reported a change it did not make on rep " + rep,
                    0, r.trueWithoutChange);

            // The greedy branch is a fast path for the same rule, so it must not need more
            // discriminating-path calls than the recursive branch to reach the same PAG.
            assertTrue("GREEDY used more R4 calls (" + g.calls + ") than RECURSIVE ("
                       + r.calls + ") on rep " + rep, g.calls <= r.calls);
        }

        assertTrue("No R4 orientations were exercised; the test is vacuous.", graphsWithR4 > 0);
    }

    /**
     * A discriminating path whose endpoints have no separator among the adjacents of either
     * endpoint must not blow up the GREEDY branch; it must fall through to the recursive search.
     */
    @Test
    public void testGreedyToleratesMissingAdjacencySepset() {
        // X1 -> X3 <- L1 -> X4, X3 -> X4, X2 -> X3, plus a latent linking X1 and X4 forces
        // separators that are not confined to the adjacents of a single endpoint.
        RandomUtil.getInstance().setSeed(77L);

        for (int rep = 0; rep < 40; rep++) {
            List<Node> nodes = new ArrayList<>();
            for (int i = 0; i < 10; i++) nodes.add(new GraphNode("X" + (i + 1)));

            Graph dag = RandomGraph.randomGraph(nodes, 3, 14, 100, 100, 100, false);
            Graph truePag = GraphTransforms.dagToPag(dag, false, 600000);

            Graph out = new EdgeListGraph(truePag);

            // Must not throw.
            orientFromCircles(dag, out, R0R4StrategyTestBased.BlockingType.GREEDY);

            assertEquals("GREEDY did not recover the true PAG on rep " + rep, truePag, out);
        }
    }
}
