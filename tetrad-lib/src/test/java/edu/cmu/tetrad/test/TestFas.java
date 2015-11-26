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
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.LargeSemSimulator;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests the PC search.
 *
 * @author Joseph Ramsey
 */
public class TestFas extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestFas(String name) {
        super(name);
    }

    public void test1() {
        int numVars = 10;
        double edgesPerNode = 1.0;
        int numCases = 1000;

        double alpha = 0.001;
        int depth = -1;

        System.out.println("Tests performance of the FAS algorithm");

        long time1 = System.currentTimeMillis();

        System.out.println("Making list of vars");

        List<Node> vars = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        System.out.println("Finishing list of vars");

        System.out.println("Making graph");

        Graph graph = GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgesPerNode));

        System.out.println("Graph done");

        System.out.println("Graph done");

        System.out.println("Starting simulation");
        LargeSemSimulator simulator = new LargeSemSimulator(graph);

        DataSet data = simulator.simulateDataAcyclic(numCases);

        System.out.println("Finishing simulation");

        long time2 = System.currentTimeMillis();

        System.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

        System.out.println("Making covariance matrix");

//        ICovarianceMatrix cov = new CovarianceMatrix2(data);
        ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(data);
//        ICovarianceMatrix cov = new CorreqlationMatrix(new CovarianceMatrix2(data));
//        ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(data, false);
//        ICovarianceMatrix cov = DataUtils.covarianceParanormalDrton(data);
//        ICovarianceMatrix cov = new CovarianceMatrix(DataUtils.covarianceParanormalWasserman(data));

//        System.out.println(cov);

        data = null;
        System.gc();

        System.out.println("Covariance matrix done");


        long time3 = System.currentTimeMillis();

        System.out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");

//        System.out.println(cov);

        IndTestFisherZ test = new IndTestFisherZ(cov, alpha);

        IFas fas = new FasStableConcurrent(test);
        fas.setDepth(depth);

        Graph outGraph = fas.search();

        System.out.println(outGraph);

        long time4 = System.currentTimeMillis();

        System.out.println("# Vars = " + numVars);
        System.out.println("# Edges = " + (int) (numVars * edgesPerNode));
        System.out.println("# Cases = " + numCases);

        System.out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        System.out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");
        System.out.println("Elapsed (running PC-Stable) " + (time4 - time3) + " ms");

        System.out.println("Total elapsed (cov + PC-Stable) " + (time4 - time2) + " ms");

        GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison(outGraph, SearchGraphUtils.patternForDag(graph));

        System.out.println("Adjacencies:");
        System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());

    }

    public void test2() {
        int numVars = 10;
        double edgesPerNode = 1.0;
        int numCases = 1000;

        int depth = 1;

        List<Node> vars = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        Graph graph = GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgesPerNode));

        IndependenceTest test = new IndTestDSep(graph);

        IFas fas = new Fas(test);
//        IFas fas = new FasStableConcurrent(new EdgeListGraphSingleConnections(test.getVariables()), test);
        fas.setDepth(depth);
        Graph outGraph = fas.search();

        IFas fas2 = new Fas(test);
//        IFas fas = new FasStableConcurrent(new EdgeListGraphSingleConnections(test.getVariables()), test);
        fas2.setDepth(depth);
        Graph outGraph2 = fas2.search();

        System.out.println("# Vars = " + numVars);
        System.out.println("# Edges = " + (int) (numVars * edgesPerNode));
        System.out.println("# Cases = " + numCases);

//        final Graph dag2 = SearchGraphUtils.patternForDag(graph);

        GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison(outGraph, outGraph2);

        System.out.println("Adjacencies:");
        System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestFas.class);
    }
}





