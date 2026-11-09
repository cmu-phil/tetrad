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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.work_in_progress.MagDgBicScore;
import edu.cmu.tetrad.search.work_in_progress.MagSemBicScore;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Regression tests for the conditioning-set indexing in the head-and-tail expansion of
 * {@link MagSemBicScore} (and its siblings). Before the fix, the expansion allocated and filled the
 * parents array by the subset BITMASK rather than the conditioning set's size, silently truncating the
 * conditioning set (or throwing IndexOutOfBoundsException when the bitmask exceeded it). These tests
 * fail on the pre-fix code and pass after it. They certify the indexing, not the head-and-tail
 * criterion itself.
 *
 * @author josephramsey
 */
public class TestMagSemBicScore {

    /**
     * With a DAG wrapped, the expansion reduces to a single term: the node given its parents, so the
     * score must equal SemBicScore's localScore exactly. Before the fix, any node with parents got the
     * node's marginal score instead.
     */
    @Test
    public void dagLocalScoresEqualSemBic() throws Exception {
        RandomUtil.getInstance().setSeed(74195628L);

        Graph dag = RandomGraph.randomGraph(7, 0, 10, 100, 100, 100, false);
        DataSet data = new SemIm(new SemPm(dag)).simulateData(1000, false);
        ICovarianceMatrix cov = new CovarianceMatrix(data);

        Graph replaced = GraphUtils.replaceNodes(dag, cov.getVariables());
        List<Node> order = replaced.paths().getValidOrder(replaced.getNodes(), true);

        MagSemBicScore magScore = new MagSemBicScore(cov);
        magScore.setMag(replaced);
        magScore.setOrder(order);

        SemBicScore plain = new SemBicScore(cov);

        List<Node> vars = magScore.getVariables();

        for (Node node : replaced.getNodes()) {
            int i = vars.indexOf(node);
            List<Node> parents = replaced.getParents(node);

            int[] js = new int[parents.size()];
            for (int k = 0; k < parents.size(); k++) js[k] = vars.indexOf(parents.get(k));

            assertEquals("node " + node, plain.localScore(i, js), magScore.localScore(i, js), 1e-10);
        }
    }

    /**
     * The smallest case with a sibling and a nonempty tail: the MAG X1 -> X2 &lt;-&gt; X3 with order
     * X1, X2, X3, scoring X3 given the blanket {X1, X2}. The head-and-tail expansion for this case,
     * worked by hand, is ls(X3) - ls(X3 | X1) + ls(X3 | X1, X2). Before the fix the code computed
     * ls(X3) - ls(X3) + ls(X3 | X1) instead.
     */
    @Test
    public void handExpandedSiblingCase() throws Exception {
        RandomUtil.getInstance().setSeed(85206317L);

        Node x1 = new GraphNode("X1"), x2 = new GraphNode("X2"), x3 = new GraphNode("X3");
        Node l = new GraphNode("L");
        l.setNodeType(NodeType.LATENT);

        Graph dagL = new EdgeListGraph(List.of(x1, x2, x3, l));
        dagL.addEdge(Edges.directedEdge(x1, x2));
        dagL.addEdge(Edges.directedEdge(l, x2));
        dagL.addEdge(Edges.directedEdge(l, x3));

        DataSet data = new SemIm(new SemPm(dagL)).simulateData(1000, false);
        List<Node> keep = List.of(data.getVariable("X1"), data.getVariable("X2"), data.getVariable("X3"));
        ICovarianceMatrix cov = new CovarianceMatrix(data.subsetColumns(keep));

        List<Node> vars = cov.getVariables();
        Node v1 = vars.get(0), v2 = vars.get(1), v3 = vars.get(2);

        Graph mag = new EdgeListGraph(vars);
        mag.addEdge(Edges.directedEdge(v1, v2));
        mag.addEdge(Edges.bidirectedEdge(v2, v3));

        MagSemBicScore magScore = new MagSemBicScore(cov);
        magScore.setMag(mag);
        magScore.setOrder(new ArrayList<>(vars));

        SemBicScore plain = new SemBicScore(cov);

        double expected = plain.localScore(2)
                          - plain.localScore(2, 0)
                          + plain.localScore(2, 0, 1);

        assertEquals(expected, magScore.localScore(2, 0, 1), 1e-10);
    }

    /**
     * The same indexing bug was copied into MagDgBicScore. With a DAG wrapped, its localScore must
     * equal its own pass-through score (mag unset), which bypasses the expansion. Before the fix the
     * two differed for any node with parents.
     */
    @Test
    public void magDgDagLocalScoresEqualPassThrough() throws Exception {
        RandomUtil.getInstance().setSeed(96317428L);

        Graph dag = RandomGraph.randomGraph(6, 0, 8, 100, 100, 100, false);
        DataSet data = new SemIm(new SemPm(dag)).simulateData(500, false);

        MagDgBicScore withMag = new MagDgBicScore(data);
        Graph replaced = GraphUtils.replaceNodes(dag, withMag.getVariables());
        withMag.setMag(replaced);
        withMag.setOrder(replaced.paths().getValidOrder(replaced.getNodes(), true));

        MagDgBicScore passThrough = new MagDgBicScore(data);

        List<Node> vars = withMag.getVariables();

        for (Node node : replaced.getNodes()) {
            int i = vars.indexOf(node);
            List<Node> parents = replaced.getParents(node);

            int[] js = new int[parents.size()];
            for (int k = 0; k < parents.size(); k++) js[k] = vars.indexOf(parents.get(k));

            assertEquals("node " + node, passThrough.localScore(i, js), withMag.localScore(i, js), 1e-10);
        }
    }
}
