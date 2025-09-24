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

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemEvidence;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.sem.SemUpdater;
import org.junit.Test;

import java.util.List;

/**
 * @author josephramsey
 */
public class TestSemUpdater {

    @Test
    public void testUpdate() {

        Graph graph = constructGraph1();

        SemPm semPm = new SemPm(graph);
        SemIm semIm = new SemIm(semPm);

        List<Node> nodes = semIm.getVariableNodes();

        SemUpdater semUpdater = new SemUpdater(semIm);

        SemEvidence evidence = new SemEvidence(semIm);
        evidence.getProposition().setValue(nodes.get(4), 10.0);
        evidence.getProposition().setValue(nodes.get(2), 1.5);

        semUpdater.setEvidence(evidence);
        evidence.setManipulated(1, true);

        semUpdater.getManipulatedSemIm();

        semUpdater.getUpdatedSemIm();
    }

    private Graph constructGraph1() {
        Graph graph = new EdgeListGraph();

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");
        Node x5 = new GraphNode("X5");

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);
        graph.addNode(x5);

        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x3, x4);
        graph.addDirectedEdge(x1, x4);
        graph.addDirectedEdge(x4, x5);

        return graph;
    }
}






