///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Discretizer;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndTestDSep;
import edu.cmu.tetrad.search.IndTestTimeSeries;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import edu.pitt.csb.mgm.IndTestMultinomialLogisticRegressionWald;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;


/**
 * Tests the IndTestTimeSeries class.
 *
 * @author Joseph Ramsey
 */
public class TestIndTestWaldLR {

    @Test
    public void testIsIndependent() {
        RandomUtil.getInstance().setSeed(1450705713157L);

        int numPassed = 0;

        for (int i = 0; i < 10; i++) {
            List<Node> nodes = new ArrayList<Node>();

            for (int i1 = 0; i1 < 10; i1++) {
                nodes.add(new ContinuousVariable("X" + (i1 + 1)));
            }

            Graph graph = GraphUtils.randomGraph(nodes, 0, 10,
                    3, 3, 3, false);
            SemPm pm = new SemPm(graph);
            SemIm im = new SemIm(pm);
            DataSet data = im.simulateData(1000, false);

            Discretizer discretizer = new Discretizer(data);
            discretizer.setVariablesCopied(true);
            discretizer.equalCounts(data.getVariable(0), 2);
            discretizer.equalCounts(data.getVariable(3), 2);
            data = discretizer.discretize();

            Node x1 = data.getVariable("X1");
            Node x2 = data.getVariable("X2");
            Node x3 = data.getVariable("X3");
            Node x4 = data.getVariable("X4");
            Node x5 = data.getVariable("X5");

            List<Node> cond = new ArrayList<Node>();
            cond.add(x3);
            cond.add(x4);
            cond.add(x5);

            Node x1Graph = graph.getNode(x1.getName());
            Node x2Graph = graph.getNode(x2.getName());

            List<Node> condGraph = new ArrayList<Node>();

            for (Node node : cond) {
                condGraph.add(graph.getNode(node.getName()));
            }

            // Using the Wald LR test since it's most up to date.
            IndependenceTest test = new IndTestMultinomialLogisticRegressionWald(data, 0.05, false);
            IndTestDSep dsep = new IndTestDSep(graph);

            boolean correct = test.isIndependent(x2, x1, cond) == dsep.isIndependent(x2Graph, x1Graph, condGraph);

            if (correct) {
                numPassed++;
            }
        }

//        System.out.println(RandomUtil.getInstance().getSeed());

        // Do not always get all 10.
        assertEquals(10, numPassed);
    }
}


