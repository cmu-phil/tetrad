///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014 by Peter Spirtes, Richard Scheines, Joseph   //
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

import edu.cmu.tetrad.data.BigDataSetUtility;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.DMSearch;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Ignore;
import org.junit.Test;

import java.io.*;
import java.util.List;

import static org.junit.Assert.assertTrue;


/**
 * Tests the DM search.
 *
 * @author Alexander Murray-Watters
 */
public class TestDM {

    @Test
    public void test1() {
        //setting seed for debug.
        RandomUtil.getInstance().setSeed(29483818483L);

        Graph graph = GraphUtils.emptyGraph(4);

        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X2"));
        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X3"));
        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X2"));
        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X3"));

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        DataSet data = im.simulateData(100000, false);

        DMSearch search = new DMSearch();

        search.setInputs(new int[]{0, 1});
        search.setOutputs(new int[]{2, 3});

        search.setData(data);
        search.setTrueInputs(search.getInputs());
        Graph foundGraph = search.search();

        print("Test Case 1");
//        System.out.println(search.getDmStructure());

//        System.out.println(foundGraph);

        Graph trueGraph = new EdgeListGraph();

        trueGraph.addNode(new GraphNode("X0"));
        trueGraph.addNode(new GraphNode("X1"));
        trueGraph.addNode(new GraphNode("X2"));
        trueGraph.addNode(new GraphNode("X3"));
        trueGraph.addNode(new GraphNode("L0"));

        trueGraph.addDirectedEdge(new GraphNode("X0"), new GraphNode("L0"));
        trueGraph.addDirectedEdge(new GraphNode("X1"), new GraphNode("L0"));

        trueGraph.addDirectedEdge(new GraphNode("L0"), new GraphNode("X2"));
        trueGraph.addDirectedEdge(new GraphNode("L0"), new GraphNode("X3"));

        assertTrue(trueGraph.equals(foundGraph));
    }

    @Test
    public void test2() {
        //setting seed for debug.
        RandomUtil.getInstance().setSeed(29483818483L);

        Graph graph = GraphUtils.emptyGraph(8);

        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X2"));
        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X3"));
        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X2"));
        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X3"));

        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X6"));
        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X7"));
        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X6"));
        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X7"));

        graph.addDirectedEdge(new GraphNode("X4"), new GraphNode("X6"));
        graph.addDirectedEdge(new GraphNode("X4"), new GraphNode("X7"));
        graph.addDirectedEdge(new GraphNode("X5"), new GraphNode("X6"));
        graph.addDirectedEdge(new GraphNode("X5"), new GraphNode("X7"));

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        DataSet data = im.simulateData(100000, false);

        DMSearch search = new DMSearch();

        search.setInputs(new int[]{0, 1, 4, 5});
        search.setOutputs(new int[]{2, 3, 6, 7});


        search.setData(data);
        search.setTrueInputs(search.getInputs());

        Graph foundGraph = search.search();

        print("Test Case 2");

        Graph trueGraph = new EdgeListGraph();

        trueGraph.addNode(new GraphNode("X0"));
        trueGraph.addNode(new GraphNode("X1"));
        trueGraph.addNode(new GraphNode("X2"));
        trueGraph.addNode(new GraphNode("X3"));
        trueGraph.addNode(new GraphNode("X4"));
        trueGraph.addNode(new GraphNode("X5"));
        trueGraph.addNode(new GraphNode("X6"));
        trueGraph.addNode(new GraphNode("X7"));

        trueGraph.addNode(new GraphNode("L0"));
        trueGraph.addNode(new GraphNode("L1"));

        trueGraph.addDirectedEdge(new GraphNode("X0"), new GraphNode("L0"));
        trueGraph.addDirectedEdge(new GraphNode("X1"), new GraphNode("L0"));

        trueGraph.addDirectedEdge(new GraphNode("L0"), new GraphNode("X2"));
        trueGraph.addDirectedEdge(new GraphNode("L0"), new GraphNode("X3"));

//        trueGraph.addDirectedEdge(new GraphNode("L0"), new GraphNode("X1"));


        trueGraph.addDirectedEdge(new GraphNode("X0"), new GraphNode("L1"));
        trueGraph.addDirectedEdge(new GraphNode("X1"), new GraphNode("L1"));

        trueGraph.addDirectedEdge(new GraphNode("X4"), new GraphNode("L1"));
        trueGraph.addDirectedEdge(new GraphNode("X5"), new GraphNode("L1"));

        trueGraph.addDirectedEdge(new GraphNode("L1"), new GraphNode("X6"));
        trueGraph.addDirectedEdge(new GraphNode("L1"), new GraphNode("X7"));

        assertTrue(foundGraph.equals(trueGraph));
    }

    @Test
    public void test3() {
        //setting seed for debug.
        RandomUtil.getInstance().setSeed(29483818483L);

        Graph graph = GraphUtils.emptyGraph(12);


        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X2"));
        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X3"));
        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X2"));
        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X3"));

        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X6"));
        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X7"));
        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X6"));
        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X7"));

        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X10"));
        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X11"));
        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X10"));
        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X11"));

        graph.addDirectedEdge(new GraphNode("X4"), new GraphNode("X6"));
        graph.addDirectedEdge(new GraphNode("X4"), new GraphNode("X7"));
        graph.addDirectedEdge(new GraphNode("X5"), new GraphNode("X6"));
        graph.addDirectedEdge(new GraphNode("X5"), new GraphNode("X7"));

        graph.addDirectedEdge(new GraphNode("X4"), new GraphNode("X10"));
        graph.addDirectedEdge(new GraphNode("X4"), new GraphNode("X11"));
        graph.addDirectedEdge(new GraphNode("X5"), new GraphNode("X10"));
        graph.addDirectedEdge(new GraphNode("X5"), new GraphNode("X11"));


        graph.addDirectedEdge(new GraphNode("X8"), new GraphNode("X10"));
        graph.addDirectedEdge(new GraphNode("X8"), new GraphNode("X11"));
        graph.addDirectedEdge(new GraphNode("X9"), new GraphNode("X10"));
        graph.addDirectedEdge(new GraphNode("X9"), new GraphNode("X11"));


        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        DataSet data = im.simulateData(100000, false);

        DMSearch search = new DMSearch();


        search.setInputs(new int[]{0, 1, 4, 5, 8, 9});
        search.setOutputs(new int[]{2, 3, 6, 7, 10, 11});

        search.setData(data);
        search.setTrueInputs(search.getInputs());
        Graph foundGraph = search.search();

        print("Test Case 3");

        Graph trueGraph = new EdgeListGraph();

        trueGraph.addNode(new GraphNode("X0"));
        trueGraph.addNode(new GraphNode("X1"));
        trueGraph.addNode(new GraphNode("X2"));
        trueGraph.addNode(new GraphNode("X3"));
        trueGraph.addNode(new GraphNode("X4"));
        trueGraph.addNode(new GraphNode("X5"));
        trueGraph.addNode(new GraphNode("X6"));
        trueGraph.addNode(new GraphNode("X7"));
        trueGraph.addNode(new GraphNode("X8"));
        trueGraph.addNode(new GraphNode("X9"));
        trueGraph.addNode(new GraphNode("X10"));
        trueGraph.addNode(new GraphNode("X11"));

        trueGraph.addNode(new GraphNode("L0"));
        trueGraph.addNode(new GraphNode("L1"));
        trueGraph.addNode(new GraphNode("L2"));

        trueGraph.addDirectedEdge(new GraphNode("X0"), new GraphNode("L1"));
        trueGraph.addDirectedEdge(new GraphNode("X1"), new GraphNode("L1"));

        trueGraph.addDirectedEdge(new GraphNode("L1"), new GraphNode("X2"));
        trueGraph.addDirectedEdge(new GraphNode("L1"), new GraphNode("X3"));


        trueGraph.addDirectedEdge(new GraphNode("X0"), new GraphNode("L2"));
        trueGraph.addDirectedEdge(new GraphNode("X1"), new GraphNode("L2"));
        trueGraph.addDirectedEdge(new GraphNode("X4"), new GraphNode("L2"));
        trueGraph.addDirectedEdge(new GraphNode("X5"), new GraphNode("L2"));

        trueGraph.addDirectedEdge(new GraphNode("L2"), new GraphNode("X6"));
        trueGraph.addDirectedEdge(new GraphNode("L2"), new GraphNode("X7"));


        trueGraph.addDirectedEdge(new GraphNode("X0"), new GraphNode("L0"));
        trueGraph.addDirectedEdge(new GraphNode("X1"), new GraphNode("L0"));
        trueGraph.addDirectedEdge(new GraphNode("X4"), new GraphNode("L0"));
        trueGraph.addDirectedEdge(new GraphNode("X5"), new GraphNode("L0"));

        trueGraph.addDirectedEdge(new GraphNode("X8"), new GraphNode("L0"));
        trueGraph.addDirectedEdge(new GraphNode("X9"), new GraphNode("L0"));

        trueGraph.addDirectedEdge(new GraphNode("L0"), new GraphNode("X10"));
        trueGraph.addDirectedEdge(new GraphNode("L0"), new GraphNode("X11"));


        assertTrue(foundGraph.equals(trueGraph));
    }


    //Three latent fork case
    @Test
    public void test4() {
        //setting seed for debug.
        RandomUtil.getInstance().setSeed(29483818483L);

        Graph graph = GraphUtils.emptyGraph(6);

        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X3"));
        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X3"));
        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X4"));
        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X5"));
        graph.addDirectedEdge(new GraphNode("X2"), new GraphNode("X5"));


        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        DataSet data = im.simulateData(100000, false);

        DMSearch search = new DMSearch();

        search.setInputs(new int[]{0, 1, 2});
        search.setOutputs(new int[]{3, 4, 5});

        search.setData(data);
        search.setTrueInputs(search.getInputs());
        Graph foundGraph = search.search();

        print("Three Latent Fork Case");

        Graph trueGraph = new EdgeListGraph();

        trueGraph.addNode(new GraphNode("X0"));
        trueGraph.addNode(new GraphNode("X1"));
        trueGraph.addNode(new GraphNode("X2"));
        trueGraph.addNode(new GraphNode("X3"));
        trueGraph.addNode(new GraphNode("X4"));
        trueGraph.addNode(new GraphNode("X5"));
        trueGraph.addNode(new GraphNode("L0"));
        trueGraph.addNode(new GraphNode("L1"));
        trueGraph.addNode(new GraphNode("L2"));

        trueGraph.addDirectedEdge(new GraphNode("X0"), new GraphNode("L0"));
        trueGraph.addDirectedEdge(new GraphNode("X1"), new GraphNode("L0"));

        trueGraph.addDirectedEdge(new GraphNode("L0"), new GraphNode("X3"));

        trueGraph.addDirectedEdge(new GraphNode("X1"), new GraphNode("L1"));

        trueGraph.addDirectedEdge(new GraphNode("L1"), new GraphNode("X4"));

        trueGraph.addDirectedEdge(new GraphNode("X1"), new GraphNode("L2"));
        trueGraph.addDirectedEdge(new GraphNode("X2"), new GraphNode("L2"));

        trueGraph.addDirectedEdge(new GraphNode("L2"), new GraphNode("X5"));

        assertTrue(foundGraph.equals(trueGraph));

    }

    // Three latent collider case
    @Test
    public void test5() {
        //setting seed for debug.
        RandomUtil.getInstance().setSeed(29483818483L);

        Graph graph = GraphUtils.emptyGraph(6);

        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X3"));
        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X4"));
        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X4"));
        graph.addDirectedEdge(new GraphNode("X2"), new GraphNode("X4"));
        graph.addDirectedEdge(new GraphNode("X2"), new GraphNode("X5"));


        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        DataSet data = im.simulateData(100000, false);

        DMSearch search = new DMSearch();

        search.setInputs(new int[]{0, 1, 2});
        search.setOutputs(new int[]{3, 4, 5});

        search.setData(data);
        search.setTrueInputs(search.getInputs());
        Graph foundGraph = search.search();

        print("Three Latent Collider Case");

        Graph trueGraph = new EdgeListGraph();

        trueGraph.addNode(new GraphNode("X0"));
        trueGraph.addNode(new GraphNode("X1"));
        trueGraph.addNode(new GraphNode("X2"));
        trueGraph.addNode(new GraphNode("X3"));
        trueGraph.addNode(new GraphNode("X4"));
        trueGraph.addNode(new GraphNode("X5"));
        trueGraph.addNode(new GraphNode("L0"));
        trueGraph.addNode(new GraphNode("L1"));
        trueGraph.addNode(new GraphNode("L2"));

        trueGraph.addDirectedEdge(new GraphNode("X0"), new GraphNode("L0"));
        trueGraph.addDirectedEdge(new GraphNode("L0"), new GraphNode("X3"));

        trueGraph.addDirectedEdge(new GraphNode("X0"), new GraphNode("L1"));
        trueGraph.addDirectedEdge(new GraphNode("X1"), new GraphNode("L1"));
        trueGraph.addDirectedEdge(new GraphNode("X2"), new GraphNode("L1"));

        trueGraph.addDirectedEdge(new GraphNode("L1"), new GraphNode("X4"));

        trueGraph.addDirectedEdge(new GraphNode("X2"), new GraphNode("L2"));
        trueGraph.addDirectedEdge(new GraphNode("L2"), new GraphNode("X5"));

        assertTrue(foundGraph.equals(trueGraph));
    }

    //Four latent case.
    @Test
    public void test6() {
        //setting seed for debug.
        RandomUtil.getInstance().setSeed(29483818483L);

        Graph graph = GraphUtils.emptyGraph(8);

        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X4"));
        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X5"));
        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X6"));
        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X7"));

        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X5"));
        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X6"));
        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X7"));

        graph.addDirectedEdge(new GraphNode("X2"), new GraphNode("X6"));
        graph.addDirectedEdge(new GraphNode("X2"), new GraphNode("X7"));

        graph.addDirectedEdge(new GraphNode("X3"), new GraphNode("X7"));

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        DataSet data = im.simulateData(1000, false);

        DMSearch search = new DMSearch();

        search.setInputs(new int[]{0, 1, 2, 3});
        search.setOutputs(new int[]{4, 5, 6, 7});

        search.setData(data);
        search.setTrueInputs(search.getInputs());
        Graph foundGraph = search.search();

        print("Four Latent Case");
        print("search.getDmStructure().latentStructToEdgeListGraph(search.getDmStructure())");

        Graph trueGraph = new EdgeListGraph();

        trueGraph.addNode(new GraphNode("X0"));
        trueGraph.addNode(new GraphNode("X1"));
        trueGraph.addNode(new GraphNode("X2"));
        trueGraph.addNode(new GraphNode("X3"));
        trueGraph.addNode(new GraphNode("X4"));
        trueGraph.addNode(new GraphNode("X5"));
        trueGraph.addNode(new GraphNode("X6"));
        trueGraph.addNode(new GraphNode("X7"));
        trueGraph.addNode(new GraphNode("L0"));
        trueGraph.addNode(new GraphNode("L1"));
        trueGraph.addNode(new GraphNode("L2"));
        trueGraph.addNode(new GraphNode("L3"));

        trueGraph.addDirectedEdge(new GraphNode("X0"), new GraphNode("L0"));
        trueGraph.addDirectedEdge(new GraphNode("L0"), new GraphNode("X4"));

        trueGraph.addDirectedEdge(new GraphNode("X0"), new GraphNode("L1"));
        trueGraph.addDirectedEdge(new GraphNode("X1"), new GraphNode("L1"));


        trueGraph.addDirectedEdge(new GraphNode("L1"), new GraphNode("X5"));

        trueGraph.addDirectedEdge(new GraphNode("X0"), new GraphNode("L2"));
        trueGraph.addDirectedEdge(new GraphNode("X1"), new GraphNode("L2"));
        trueGraph.addDirectedEdge(new GraphNode("X2"), new GraphNode("L2"));
        trueGraph.addDirectedEdge(new GraphNode("L2"), new GraphNode("X6"));


        trueGraph.addDirectedEdge(new GraphNode("X0"), new GraphNode("L3"));
        trueGraph.addDirectedEdge(new GraphNode("X1"), new GraphNode("L3"));
        trueGraph.addDirectedEdge(new GraphNode("X2"), new GraphNode("L3"));
        trueGraph.addDirectedEdge(new GraphNode("X3"), new GraphNode("L3"));
        trueGraph.addDirectedEdge(new GraphNode("L3"), new GraphNode("X7"));


        assertTrue(foundGraph.equals(trueGraph));
    }


//    Test cases after here serve as examples and/or were used to diagnose a no longer applicable problem.
//    Still have to clean up.
    @Ignore
    public void rtest7() {

        print("test 7");
        DMSearch result =
                readAndSearchData("src/edu/cmu/tetradproj/amurrayw/testcase7.txt",
                        new int[]{0, 1}, new int[]{2, 3}, true, new int[]{0, 1});


        File file = new File("src/edu/cmu/tetradproj/amurrayw/output_test7.txt");
        try {
            FileOutputStream out = new FileOutputStream(file);
            PrintStream outStream = new PrintStream(out);
            outStream.println(result.getDmStructure().latentStructToEdgeListGraph(result.getDmStructure()));
            outStream.println();
        } catch (java.io.FileNotFoundException e) {
            print("Can't write to file.");

        }

        print("DONE");
    }

    @Test
    public void test8() {
//
//        int nInputs=17610;
//        int nOutputs=12042;
//
//        int[] inputs = new int[nInputs];
//        int[] outputs = new int[nOutputs];
//
//        for(int i=0;  i<nInputs; i++){inputs[i]=i;}
//        for(int i=0;  i<nOutputs; i++){outputs[i]=nInputs+i-1;}
//
//        System.out.println("test 8");
//
//        DMSearch result = new DMSearch();
//        result.setAlphaPC(.000001);
//        result.setAlphaSober(.000001);
//        result.setDiscount(1);
//
//        result =
//                 readAndSearchData("src/edu/cmu/tetradproj/amurrayw/combined_renamed.txt",
//                        inputs, outputs);
//
//
//        System.out.println("Finished search, now writing output to file.");
//
//
//        File file=new File("src/edu/cmu/tetradproj/amurrayw/output_old_inputs.txt");
//        try {
//            FileOutputStream out = new FileOutputStream(file);
//            PrintStream outStream = new PrintStream(out);
//            outStream.println(result.getDmStructure().latentStructToEdgeListGraph(result.getDmStructure()));
//            //outStream.println();
//        }
//        catch (java.io.FileNotFoundException e) {
//                    System.out.println("Can't write to file.");
//
//        }
//
//
//        //System.out.println(result.getDmStructure().latentStructToEdgeListGraph(result.getDmStructure()));
//        System.out.println("DONE");
    }

    @Ignore
    public void rtest9() {
        internaltest9(10);
    }

    @Ignore
    public int internaltest9(double initialDiscount) {

        RandomUtil.getInstance().setSeed(29483818483L);


        int nInputs = 17610;
        int nOutputs = 12042;

        int[] inputs = new int[nInputs];
        int[] outputs = new int[nOutputs];

        for (int i = 0; i < nInputs; i++) {
            inputs[i] = i;
        }
        for (int i = 0; i < nOutputs; i++) {
            outputs[i] = nInputs + i - 1;
        }

        print("test 9");

//Trying recursion as while loop seems to reduce speed below that of non-loop version.
        //double initialDiscount = 20;
//        while(initialDiscount>0){
        DMSearch result = new DMSearch();
        result.setAlphaPC(.000001);
        result.setAlphaSober(.000001);

        result.setDiscount(initialDiscount);

        result =
                readAndSearchData("src/edu/cmu/tetradproj/amurrayw/final_joined_data_no_par.txt",
                        inputs, outputs, true, inputs);


        print("Finished search, now writing output to file.");


        File file = new File("src/edu/cmu/tetradproj/amurrayw/final_output_" + initialDiscount + "_.txt");
        try {
            FileOutputStream out = new FileOutputStream(file);
            PrintStream outStream = new PrintStream(out);
            outStream.println(result.getDmStructure().latentStructToEdgeListGraph(result.getDmStructure()));
            //outStream.println();
        } catch (java.io.FileNotFoundException e) {
            print("Can't write to file.");

        }


        File file2 = new File("src/edu/cmu/tetradproj/amurrayw/unconverted_output" + initialDiscount + "_.txt");
        try {
            FileOutputStream out = new FileOutputStream(file2);
            PrintStream outStream = new PrintStream(out);
            outStream.println(result.getDmStructure());
            //outStream.println();
        } catch (java.io.FileNotFoundException e) {
            print("Can't write to file.");

        }
//            initialDiscount--;
//        }

        //System.out.println(result.getDmStructure().latentStructToEdgeListGraph(result.getDmStructure()));
        print("DONE");

//        if(initialDiscount>1){
//            result=null;
//            internaltest9(initialDiscount-1);
//        }

        return (1);
    }


//
    @Test
    public void test10() {
        //setting seed for debug.
        RandomUtil.getInstance().setSeed(29483818483L);


        Graph graph = GraphUtils.emptyGraph(5);

        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X2"));
        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X3"));
        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X3"));
        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X4"));


        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        DataSet data = im.simulateData(100000, false);


        DMSearch search = new DMSearch();

        search.setUseFgs(false);

        search.setInputs(new int[]{0, 1});
        search.setOutputs(new int[]{2, 3, 4});

        search.setData(data);
        search.setTrueInputs(search.getInputs());
        search.search();

        print("Test Case 10");

        // Trying to quiet the output for unit tests.
        if (false) {
            System.out.println(search.getDmStructure());
        }


        assertTrue(true);

    }

    public boolean listHasDuplicates(List<Node> list) {
        for (Node node : list) {
            list.remove(node);
            if (list.contains(node)) {
                return (true);
            }
        }
        return (false);
    }

    public boolean cycleExists(Graph graph, List<Node> adjacentNodes, List<Node> path, Node currentNode) {

        if (adjacentNodes.isEmpty() && path.isEmpty()) {
            for (Node node : graph.getNodes()) {
                adjacentNodes = graph.getAdjacentNodes(node);

                if (adjacentNodes.isEmpty()) {
                    continue;
                }

                path.add(node);
                currentNode = node;

                print("RAN: " + adjacentNodes + " " + path + " " + currentNode);

                return (cycleExists(graph, adjacentNodes, path, currentNode));
            }
        } else {
            adjacentNodes = graph.getAdjacentNodes(currentNode);
            for (Node node : adjacentNodes) {
                if (path.lastIndexOf(node) == (path.size() - 1)) {
                    continue;
                } else {
                    path.add(node);
                    currentNode = node;
                    return (cycleExists(graph, adjacentNodes, path, currentNode));
                }
            }
            if (listHasDuplicates(path)) {
                return (true);
            }
        }
        return (false);
    }

    @Ignore
    public void rtest11() {
        //setting seed for debug.
        RandomUtil.getInstance().setSeed(29483818483L);


        Graph graph = GraphUtils.emptyGraph(4);

        Node X0 = graph.getNode("X0");
        Node X1 = graph.getNode("X1");
        Node X2 = graph.getNode("X2");
        Node X3 = graph.getNode("X3");

        graph.addDirectedEdge(X0, X2);
        graph.addDirectedEdge(X2, X3);
        graph.addDirectedEdge(X3, X0);


        System.out.print(graph.existsDirectedPathFromTo(X0, X3));
        System.out.print(graph.existsDirectedPathFromTo(X3, X0));

        for (Node node : graph.getNodes()) {
            print("Nodes adjacent to " + node + ": " + graph.getAdjacentNodes(node) + "\n");
        }


        print("graph.existsDirectedCycle: " + graph.existsDirectedCycle());


        print("Graph structure: " + graph);


        assertTrue(true);

    }

    @Ignore
    public void rtest12() {
        //setting seed for debug.
        RandomUtil.getInstance().setSeed(29483818483L);


        Graph graph = GraphUtils.emptyGraph(9);

        Node X0 = graph.getNode("X0");
        Node X1 = graph.getNode("X1");
        Node X2 = graph.getNode("X2");

        Node X3 = graph.getNode("X3");
        Node X4 = graph.getNode("X4");


        Node X5 = graph.getNode("X5");


        Node X6 = graph.getNode("X6");
        Node X7 = graph.getNode("X7");
        Node X8 = graph.getNode("X8");


        graph.addDirectedEdge(X0, X6);
        graph.addDirectedEdge(X0, X7);
        graph.addDirectedEdge(X0, X8);
        graph.addDirectedEdge(X1, X6);
        graph.addDirectedEdge(X1, X7);
        graph.addDirectedEdge(X1, X8);
        graph.addDirectedEdge(X2, X6);
        graph.addDirectedEdge(X2, X7);
        graph.addDirectedEdge(X2, X8);


        graph.addDirectedEdge(X3, X8);
        graph.addDirectedEdge(X3, X7);
        graph.addDirectedEdge(X4, X8);
        graph.addDirectedEdge(X4, X7);


        graph.addDirectedEdge(X5, X8);


        RandomUtil.getInstance().setSeed(29483818483L);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        DataSet data = im.simulateData(100000, false);

        DMSearch search = new DMSearch();

        search.setInputs(new int[]{0, 1, 2, 3, 4, 5});
        search.setOutputs(new int[]{6, 7, 8});

        search.setData(data);
        search.setTrueInputs(search.getInputs());
        search.search();

        print("");
        print("" + search.getDmStructure());


        print("graph.existsDirectedCycle: " + search.getDmStructure().latentStructToEdgeListGraph(search.getDmStructure()).existsDirectedCycle());


        print("Graph structure: " + search);


        assertTrue(true);

    }

    @Ignore
    public void rtest13() {
        //setting seed for debug.
        RandomUtil.getInstance().setSeed(29483818483L);


        Graph graph = GraphUtils.emptyGraph(12);

        Node X0 = graph.getNode("X0");
        Node X1 = graph.getNode("X1");
        Node X2 = graph.getNode("X2");

        Node X3 = graph.getNode("X3");
        Node X4 = graph.getNode("X4");


        Node X5 = graph.getNode("X5");


        Node X6 = graph.getNode("X6");
        Node X7 = graph.getNode("X7");
        Node X8 = graph.getNode("X8");
        Node X9 = graph.getNode("X9");
        Node X10 = graph.getNode("X10");
        Node X11 = graph.getNode("X11");

        graph.addDirectedEdge(X0, X6);


        graph.addDirectedEdge(X1, X6);
        graph.addDirectedEdge(X1, X7);
        graph.addDirectedEdge(X1, X8);

        graph.addDirectedEdge(X2, X8);

        graph.addDirectedEdge(X3, X8);
        graph.addDirectedEdge(X3, X9);
        graph.addDirectedEdge(X3, X10);

        graph.addDirectedEdge(X3, X9);

        graph.addDirectedEdge(X4, X10);
        graph.addDirectedEdge(X4, X11);


        graph.addDirectedEdge(X5, X11);


//
//        graph.addDirectedEdge(X1, X8);
//        graph.addDirectedEdge(X2, X6);
//        graph.addDirectedEdge(X2, X7);
//        graph.addDirectedEdge(X2, X8);
//
//
//        graph.addDirectedEdge(X3, X8);
//        graph.addDirectedEdge(X3, X7);
//        graph.addDirectedEdge(X4, X8);
//        graph.addDirectedEdge(X4, X7);
//
//
//        graph.addDirectedEdge(X5, X8);


        RandomUtil.getInstance().setSeed(29483818483L);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        DataSet data = im.simulateData(100000, false);

        DMSearch search = new DMSearch();

        search.setInputs(new int[]{0, 1, 2, 3, 4, 5});
        search.setOutputs(new int[]{6, 7, 8, 9, 10, 11});

        search.setData(data);
        search.setTrueInputs(search.getInputs());
        search.search();

        print("");
        print("" + search.getDmStructure());


        print("graph.existsDirectedCycle: " + search.getDmStructure().latentStructToEdgeListGraph(search.getDmStructure()).existsDirectedCycle());


        print("Graph structure: " + search);


        assertTrue(true);

    }

    @Ignore
    public void rtest16() {

        print("test PC");
        DMSearch result =
                readAndSearchData("src/edu/cmu/tetradproj/amurrayw/testcase7_fixed.txt",
                        new int[]{0, 1}, new int[]{2, 3}, false, new int[]{0, 1});


        File file = new File("src/edu/cmu/tetradproj/amurrayw/output_test7_fixed.txt");
        try {
            FileOutputStream out = new FileOutputStream(file);
            PrintStream outStream = new PrintStream(out);
            outStream.println(result.getDmStructure().latentStructToEdgeListGraph(result.getDmStructure()));
            outStream.println();
        } catch (java.io.FileNotFoundException e) {
            print("Can't write to file.");

        }


        System.out.println(result.getDmStructure().latentStructToEdgeListGraph(result.getDmStructure()));
        print("DONE");
    }

    @Ignore
    public void rtest17() {
        internaltest17(999);
    }

    @Ignore
    public int internaltest17(double initialDiscount) {
        RandomUtil.getInstance().setSeed(29483818483L);


        int nInputs = 17610;
        int nOutputs = 12042;

        int[] inputs = new int[nInputs];
        int[] outputs = new int[nOutputs];


        int[] trueInputs = new int[]{2761, 2762, 4450, 2247, 16137, 13108, 12530, 231, 1223, 1379, 5379, 12745,
                14913, 16066, 16197, 16199, 17353, 17392, 4397, 3009, 3143, 5478, 5479, 5480,
                5481, 5482, 7474, 12884, 12885, 12489, 9112, 1943, 9114, 1950, 9644, 9645,
                9647};


        for (int i = 0; i < nInputs; i++) {
            inputs[i] = i;
        }
        for (int i = 0; i < nOutputs; i++) {
            outputs[i] = nInputs + i - 1;
        }

        print("test 17");

//Trying recursion as while loop seems to reduce speed below that of non-loop version.
        //double initialDiscount = 20;
//        while(initialDiscount>0){
        DMSearch result = new DMSearch();
//        result.setAlphaPC(1e-30);
//        result.setAlphaSober(1e-30);


        result.setAlphaPC(1e-6);
        result.setAlphaSober(1e-6);

        result.setDiscount(initialDiscount);


        result =
                readAndSearchData("src/edu/cmu/tetradproj/amurrayw/final_joined_data_no_par_fixed.txt",
                        inputs, outputs, false, trueInputs);


        print("Finished search, now writing output to file.");


        File file = new File("src/edu/cmu/tetradproj/amurrayw/final_output_" + initialDiscount + "_.txt");
        try {
            FileOutputStream out = new FileOutputStream(file);
            PrintStream outStream = new PrintStream(out);
            outStream.println(result.getDmStructure().latentStructToEdgeListGraph(result.getDmStructure()));
            //outStream.println();
        } catch (java.io.FileNotFoundException e) {
            print("Can't write to file.");

        }


        File file2 = new File("src/edu/cmu/tetradproj/amurrayw/unconverted_output" + initialDiscount + "_.txt");
        try {
            FileOutputStream out = new FileOutputStream(file2);
            PrintStream outStream = new PrintStream(out);
            outStream.println(result.getDmStructure());
            //outStream.println();
        } catch (java.io.FileNotFoundException e) {
            print("Can't write to file.");

        }
//            initialDiscount--;
//        }

        //System.out.println(result.getDmStructure().latentStructToEdgeListGraph(result.getDmStructure()));
        print("DONE");

//        if(initialDiscount>1){
//            result=null;
//            internaltest9(initialDiscount-1);
//        }

        return (1);
    }

    @Ignore
    public void rtest14() {
        //setting seed for debug.
//        RandomUtil.getInstance().setSeed(29483818483L);
//
//
//        Graph graph = GraphUtils.emptyGraph(5);
//
//        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X2"));
//        graph.addDirectedEdge(new GraphNode("X0"), new GraphNode("X3"));
//        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X3"));
//        graph.addDirectedEdge(new GraphNode("X1"), new GraphNode("X4"));
//
//
//        SemPm pm = new SemPm(graph);
//        SemIm im = new SemIm(pm);
//
//        DataSet data = im.simulateData(100000, false);
//
//        DMSearch search = new DMSearch();
//
//        search.setInputs(new int[]{0, 1});
//        search.setOutputs(new int[]{2, 3, 4});
//
//        search.search(data);
//
//        System.out.println("Test Case 10");
//        System.out.println(search.getDmStructure());


        assertTrue(true);

    }

    @Ignore
    public void rtest15() {
//        for(int i=10; i>=4; i--){
//            finishRenaming(i);
//        }

        finishRenaming(999);
    }


    public void finishRenaming(int penalty) {
        String currentLine;

        try {
            FileReader file = new FileReader("src/edu/cmu/tetradproj/amurrayw/final_run/renamed_graph_penalty" + penalty + ".r.txt");
            BufferedReader br = new BufferedReader(file);

            String[] varNames = null;

            int nVar = 0;
            int lineNumber = 0;


            Graph graph = new EdgeListGraph();

            while ((currentLine = br.readLine()) != null) {


                //finds/gets variable names and adds to graph. Note that it assumes no whitespace in variable names.
                if (lineNumber == 0) {
                    varNames = currentLine.split("\\s+");

                    nVar = varNames.length;

                    for (String nodeName : varNames) {
                        graph.addNode(new GraphNode(nodeName));

                    }
                } else {

                    //splits line to single
                    String[] adjInfoString = currentLine.split("\\s+");


                    for (int i = 0; i < nVar; i++) {
                        ;
                        if (Integer.parseInt(adjInfoString[i]) > 1) {
                            print(adjInfoString[i]);
                        } else if (Integer.parseInt(adjInfoString[i]) < 0) {
                            print(adjInfoString[i]);
                        }


//                        System.out.println(adjInfoString[i]);
                        if (Integer.parseInt(adjInfoString[i]) == 1) {


//                            System.out.println("i");
//                            System.out.println(i);
//
//                            System.out.println("varNames[i]");
//                            System.out.println(varNames[i]);
//
//
//                            System.out.println("lineNumber");
//                            System.out.println(lineNumber);
//
//                            System.out.println("varNames.length");
//                            System.out.println(varNames.length);
//
//
//                            System.out.println("varNames[lineNumber]");
//                            System.out.println(varNames[lineNumber]);

                            graph.addDirectedEdge(graph.getNode(varNames[lineNumber - 1]), graph.getNode(varNames[i]));

//                            graph.addDirectedEdge(graph.getNode(varNames[i]), graph.getNode(varNames[lineNumber]));
                        }

                    }

                }
                lineNumber++;
//                System.out.println(currentLine);
            }


            File outfile = new File("src/edu/cmu/tetradproj/amurrayw/final_run/renamed_final_output_" + penalty + "_.txt");
            try {
                FileOutputStream out = new FileOutputStream(outfile);
                PrintStream outStream = new PrintStream(out);
                outStream.println(graph);
                //outStream.println();
            } catch (java.io.FileNotFoundException e) {
                String x = "Can't write to file.";
                print(x);

            }


        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void print(String x) {
        if (false) {
            System.out.println(x);
        }
    }

    //Reads in data and runs search. Note: Assumes variable names are of the form X0, X1, etc.
    // Both input and output integer arrays are the indexes of their respective variables.
    public DMSearch readAndSearchData(String fileLocation, int[] inputs, int[] outputs, boolean useGES, int[] trueInputs) {
        File file = new File(fileLocation);
        DataSet data = null;

        try {
            data = BigDataSetUtility.readInContinuousData(file, ' ');

        } catch (IOException e) {
            print("Failed to read in data.");
            e.printStackTrace();
        }


        print("Read Data");
        DMSearch search = new DMSearch();

        search.setInputs(inputs);
        search.setOutputs(outputs);

        if (useGES == false) {

            search.setAlphaPC(.05);
            search.setUseFgs(useGES);

            search.setData(data);
            search.setTrueInputs(trueInputs);
            search.search();

        } else {
            search.setData(data);
            search.setTrueInputs(trueInputs);
            search.search();

//            search.search(data, trueInputs);
        }


        return (search);

    }

    public static void main(String... args) {
        new TestDM().test8();
    }
}




