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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.CpdagParentDistancesFromTrue;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;


public class TestCpdagParentDistancesFromTrue {

    @Test
    public void testDistanceMethod() {

        RandomUtil.getInstance().setSeed(384828384L);

        // Create the estimated CPDAG.
        ArrayList<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        Graph trueGraph = new EdgeListGraph(nodes);

        trueGraph.addDirectedEdge(nodes.get(0), nodes.get(3));
        trueGraph.addDirectedEdge(nodes.get(1), nodes.get(3));
        trueGraph.addDirectedEdge(nodes.get(2), nodes.get(3));
        trueGraph.addDirectedEdge(nodes.get(3), nodes.get(4));

        // Make a SEM PM, SEM IM and simulate N = 2000 data.
        SemPm semPm = new SemPm(trueGraph);
        SemIm semIm = new SemIm(semPm);
        DataSet data = semIm.simulateData(2000, false);

        Matrix edgeCoef = semIm.getEdgeCoef();
//        System.out.println("Edge Coefficients: ");
//        System.out.println(edgeCoef);

        Graph estCpdag = new EdgeListGraph(nodes);

        estCpdag.addUndirectedEdge(nodes.get(0), nodes.get(3));
        estCpdag.addUndirectedEdge(nodes.get(1), nodes.get(3));
        estCpdag.addUndirectedEdge(nodes.get(2), nodes.get(3));
        estCpdag.addUndirectedEdge(nodes.get(3), nodes.get(4));

//        System.out.println("True DAG = " + trueGraph);
//        System.out.println("Estimated CPDAG = " + estCpdag);

        estCpdag = GraphUtils.replaceNodes(estCpdag, data.getVariables());

        double[][] distances = new CpdagParentDistancesFromTrue().getDistances(estCpdag, edgeCoef.toArray(), data, CpdagParentDistancesFromTrue.DistanceType.ABSOLUTE);

        System.out.println("distances = ");
        System.out.println(new Matrix(distances));

        assertEquals(0.0091, distances[0][3], 0.001);
    }
}
