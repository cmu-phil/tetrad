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

package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.*;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.*;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.text.ParseException;
import java.util.List;

import static junit.framework.TestCase.assertTrue;

public class TestKnowledge {

    /**
     * For CPDAG algorithms (causal sufficiency assumed), "X1 is in the last tier" implies X1 has no children, so no
     * edge X1 --&gt; m may appear, and getNodesOutTo(x1, ARROW) must be empty.
     */
    private static void testKnowledgeCpdag(DataSet dataSet, Knowledge knowledge, Parameters parameters, AcceptsKnowledge algorithm) {
        Graph _graph = runSearch(dataSet, knowledge, parameters, algorithm);
        Node x1 = _graph.getNode("X1");
        List<Node> innodes = _graph.getNodesOutTo(x1, Endpoint.ARROW);
        assertTrue("CPDAG semantics: X1 in the last tier may have no children, but found " + innodes,
                innodes.isEmpty());
    }

    /**
     * For PAG algorithms, "X1 is in the last tier" (X1 causes nothing) forbids only a TAIL at X1 with an arrow into
     * m, i.e., an edge X1 --&gt; m claiming X1 is an ancestor of m. Edges X1 &lt;-&gt; m and X1 o-&gt; m are NOT
     * violations: X1 &lt;-&gt; m records confounding of X1 and m by a latent, which is exactly the graph a correct
     * search should return when the data show dependence that knowledge forbids from being causal. The previous
     * version of this test asserted getNodesOutTo(x1, ARROW) empty for PAG algorithms as well; that assertion pinned
     * a former (incorrect) veto in FciOrient.isArrowheadAllowed which deleted collider arrowheads at m whenever
     * X1 --&gt; m was forbidden, erasing the confounding record (see TestFciKnowledgeOrientation).
     */
    private static void testKnowledgePag(DataSet dataSet, Knowledge knowledge, Parameters parameters, AcceptsKnowledge algorithm) {
        Graph _graph = runSearch(dataSet, knowledge, parameters, algorithm);
        Node x1 = _graph.getNode("X1");
        List<Node> violations = new java.util.ArrayList<>();
        for (Node m : _graph.getNodesOutTo(x1, Endpoint.ARROW)) {
            if (_graph.getEndpoint(m, x1) == Endpoint.TAIL) {
                violations.add(m);
            }
        }
        assertTrue("PAG semantics: X1 in the last tier may have no edge X1 --> m (tail at X1, arrow at m), "
                + "but found such edges to " + violations, violations.isEmpty());
    }

    private static Graph runSearch(DataSet dataSet, Knowledge knowledge, Parameters parameters, AcceptsKnowledge algorithm) {
        algorithm.setKnowledge(knowledge);
        Graph _graph;
        try {
            _graph = ((Algorithm) algorithm).search(dataSet, parameters);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return GraphUtils.replaceNodes(_graph, dataSet.getVariables());
    }

    // Tests to make sure knowledge gets passed into the algcomparison wrappers for
    // all methods that take knowledge.
    @Test
    public void test1() {
        RandomUtil.getInstance().setSeed(3848283L);

        Graph graph = RandomGraph.randomGraph(10, 0, 10, 100, 1090, 100, false);
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet dataSet = null;
        try {
            dataSet = im.simulateData(100, false);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        Knowledge knowledge = new Knowledge();
        knowledge.addToTier(1, "X1");
        for (int i = 2; i <= 10; i++) knowledge.addToTier(0, "X" + i);

        System.out.println(knowledge);

        IndependenceWrapper test = new FisherZ();
        ScoreWrapper score = new SemBicScore();
        Parameters parameters = new Parameters();

        testKnowledgeCpdag(dataSet, knowledge, parameters, new Boss(score));
        testKnowledgeCpdag(dataSet, knowledge, parameters, new Fges(score));
        testKnowledgeCpdag(dataSet, knowledge, parameters, new Grasp(test, score));
        testKnowledgeCpdag(dataSet, knowledge, parameters, new Pc(test));
        testKnowledgeCpdag(dataSet, knowledge, parameters, new Sp(score));

        testKnowledgePag(dataSet, knowledge, parameters, new Bfci(test, score));
        testKnowledgePag(dataSet, knowledge, parameters, new Fci(test));
        testKnowledgePag(dataSet, knowledge, parameters, new GraspFci(test, score));
        testKnowledgePag(dataSet, knowledge, parameters, new Rfci(test));
        testKnowledgePag(dataSet, knowledge, parameters, new SpFci(test, score));
    }
}

