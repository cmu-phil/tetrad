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

package edu.cmu.tetradapp.test;

import edu.cmu.tetrad.data.IndependenceFacts;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.model.IndependenceFactsModel;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertTrue;

/**
 * Tests the Knowledge class.
 *
 * @author josephramsey
 */
public final class TestIndependenceFacts {
    private IndependenceFactsModel facts;

    @Test
    public void test1() {
        IndependenceFactsModel facts = new IndependenceFactsModel();

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");
        Node x5 = new GraphNode("X5");
        Node x6 = new GraphNode("X6");

        facts.add(new IndependenceFact(x1, x2, x3));
        facts.add(new IndependenceFact(x2, x3));
        facts.add(new IndependenceFact(x2, x4, x1, x2));
        facts.add(new IndependenceFact(x2, x4, x1, x3, x5));
        facts.add(new IndependenceFact(x2, x4, x3));
        facts.add(new IndependenceFact(x2, x4, x3, x6));

        facts.remove(new IndependenceFact(x1, x2, x3));

        IndependenceFacts _facts = new IndependenceFacts(facts.getFacts());

        assertTrue(_facts.isIndependent(x4, x2, x1, x2));
        assertTrue(_facts.isIndependent(x4, x2, x5, x3, x1));

        Set<Node> l = new HashSet<>();
        l.add(x1);
        l.add(x2);

        assertTrue(_facts.isIndependent(x4, x2, l));

    }

    @Test
    public void test2() {
        File file = new File("src/test/resources/sample.independencies.txt");

        try {
            IndependenceFactsModel facts = IndependenceFactsModel.loadFacts(new FileReader(file));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}






