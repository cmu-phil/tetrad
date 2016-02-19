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
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fgs;
import edu.cmu.tetrad.search.Ges;
import edu.cmu.tetrad.search.GraphScore;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests GES.
 *
 * @author Joseph Ramsey
 */
public class TestGes {

    /**
     * Runs the PC algorithm on the graph X1 --> X2, X1 --> X3, X2 --> X4, X3 --> X4. Should produce X1 -- X2, X1 -- X3,
     * X2 --> X4, X3 --> X4.
     */
    @Test
    public void testSearch1() {
        RandomUtil.getInstance().setSeed(1449864244802L);

        checkSearch("X1-->X2,X1-->X3,X2-->X4,X3-->X4",
                "X1---X2,X1---X3,X2-->X4,X3-->X4");
    }

    /**
     * Runs the PC algorithm on the graph X1 --> X2, X1 --> X3, X2 --> X4, X3 --> X4. Should produce X1 -- X2, X1 -- X3,
     * X2 --> X4, X3 --> X4.
     */
    @Test
    public void testSearch2() {
        RandomUtil.getInstance().setSeed(4294832L);
        checkSearch("X1-->X2,X1-->X3,X2-->X4,X3-->X4",
                "X1---X2,X1---X3,X2-->X4,X3-->X4");
    }

    /**
     * This will fail if the orientation loop doesn't continue after the first orientation.
     */
    @Test
    public void testSearch3() {
        RandomUtil.getInstance().setSeed(1450031582986L);
        checkSearch("A-->D,A-->B,B-->D,C-->D,D-->E",
                "A-->D,A---B,B-->D,C-->D,D-->E");
    }

    /**
     * This will fail if the orientation loop doesn't continue after the first orientation.
     */
    @Test
    public void testSearch4() {
        IKnowledge knowledge = new Knowledge2();
        knowledge.setForbidden("B", "D");
        knowledge.setForbidden("D", "B");
        knowledge.setForbidden("C", "B");

        checkWithKnowledge("A-->B,C-->B,B-->D", "A---C,A---D,B---A,B---C,C---D",
                knowledge);
    }

    @Test
    public void testSearch5() {
        int numVars = 10;
        int numEdges = 10;
        int sampleSize = 1000;

        RandomUtil.getInstance().setSeed(1450028448632L);

        List<Node> nodes = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Dag trueGraph = new Dag(GraphUtils.randomGraph(nodes, 0, numEdges, 7,
                5, 5, false));

        SemPm semPm = new SemPm(trueGraph);
        SemIm im = new SemIm(semPm);
        DataSet dataSet = im.simulateData(sampleSize, false);

        Ges ges = new Ges(dataSet);
        ges.setTrueGraph(trueGraph);

        // Run search
        Graph resultGraph = ges.search();

        assertEquals(resultGraph, SearchGraphUtils.patternForDag(trueGraph));
    }

    /**
     * Presents the input graph to Fci and checks to make sure the output of Fci is equivalent to the given output
     * graph.
     */
    private void checkSearch(String inputGraph, String outputGraph) {
//        testGes(inputGraph, outputGraph);
        testFgs(inputGraph, outputGraph);
    }

    private void testGes(String inputGraph, String outputGraph) {
        // Set up graph and node objects.
        Graph graph = GraphConverter.convert(inputGraph);
        SemPm semPm = new SemPm(graph);
        SemIm semIM = new SemIm(semPm);
        DataSet dataSet = semIM.simulateData(1000, false);

        // Set up search.
        Ges ges = new Ges(dataSet);
//        Fgs ges = new Fgs(dataSet);
        ges.setPenaltyDiscount(2);
//        ges.setTrueGraph(graph);

        // Run search
        Graph resultGraph = ges.search();

        // Build comparison graph.
        Graph trueGraph = GraphConverter.convert(outputGraph);

        // Do test.
        Graph p = SearchGraphUtils.patternForDag(trueGraph);

        assertTrue(resultGraph.equals(p));
    }

    private void testFgs(String inputGraph, String outputGraph) {
        // Set up graph and node objects.
        Graph graph = GraphConverter.convert(inputGraph);
        SemPm semPm = new SemPm(graph);
        SemIm semIM = new SemIm(semPm);
        DataSet dataSet = semIM.simulateData(1000, false);

        // Set up search.
//        Ges ges = new Ges(dataSet);
        Fgs ges = new Fgs(dataSet);
        ges.setPenaltyDiscount(1.0);
//        ges.setTrueGraph(graph);

        // Run search
        Graph resultGraph = ges.search();

        // Build comparison graph.
        Graph trueGraph = GraphConverter.convert(outputGraph);

        // Do test.
        assertTrue(resultGraph.equals(SearchGraphUtils.patternForDag(trueGraph)));
    }


    /**
     * Presents the input graph to Fci and checks to make sure the output of Fci is equivalent to the given output
     * graph.
     */
    private void checkWithKnowledge(String inputGraph, String outputGraph,
                                    IKnowledge knowledge) {

        // Set up graph and node objects.
        Graph graph = GraphConverter.convert(inputGraph);
        Graph trueGraph = GraphConverter.convert(outputGraph);

//        SemPm semPm = new SemPm(graph);
//        SemIm semIM = new SemIm(semPm);
//        DataSet dataSet = semIM.simulateData(5000, false);

        // Set up search.
        Fgs ges = new Fgs(new GraphScore(graph));
        ges.setKnowledge(knowledge);

        // Run search
        Graph resultGraph = ges.search();

        // Do test.
        assertEquals(trueGraph, resultGraph);
    }

    private static List<Set<Node>> powerSet(List<Node> nodes) {
        List<Set<Node>> subsets = new ArrayList<Set<Node>>();
        int total = (int) Math.pow(2, nodes.size());
        for (int i = 0; i < total; i++) {
            Set<Node> newSet = new HashSet<Node>();
            String selection = Integer.toBinaryString(i);

            int shift = nodes.size() - selection.length();

            for (int j = nodes.size() - 1; j >= 0; j--) {
                if (j >= shift && selection.charAt(j - shift) == '1') {
                    newSet.add(nodes.get(j));
                }
            }
            subsets.add(newSet);
        }

        return subsets;
    }
}





