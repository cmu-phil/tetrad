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
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Jcpc;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests the BooleanFunction class.
 *
 * @author Joseph Ramsey
 */
public class TestJcpc {

    @Test
    public void testSearch4() {
        RandomUtil.getInstance().setSeed(1450198393723L);

        int numVars = 4;
        int sampleSize = 1000;

        List<Node> nodes = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Dag trueGraph = new Dag(GraphUtils.randomGraph(nodes, 0, numVars,
                30, 15, 15, false));

        SemPm semPm = new SemPm(trueGraph);
        SemIm bayesIm = new SemIm(semPm);
        DataSet dataSet = bayesIm.simulateData(sampleSize, false);

        IndependenceTest test = new IndTestFisherZ(dataSet, 0.001);
        Jcpc search = new Jcpc(test);

        // Run search
        Graph resultGraph = search.search();

        assertEquals(SearchGraphUtils.patternForDag(trueGraph), resultGraph);
    }

    @Test
    public void testSearch5() {
        RandomUtil.getInstance().setSeed(1450198679419L);

        int numVars = 10;
        int numEdges = 10;
        int sampleSize = 1000;

        List<Node> nodes = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Dag trueGraph = new Dag(GraphUtils.randomGraph(nodes, 0, numEdges,
                7, 5, 5, false));

        SemPm semPm = new SemPm(trueGraph);
        SemIm bayesIm = new SemIm(semPm);
        DataSet dataSet = bayesIm.simulateData(sampleSize, false);

        IndependenceTest test = new IndTestFisherZ(dataSet, 0.001);
        Jcpc search = new Jcpc(test);

        Graph resultGraph = search.search();

        assertEquals(SearchGraphUtils.patternForDag(trueGraph), resultGraph);
    }
}





