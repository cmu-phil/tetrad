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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.List;

/**
 * Attempts to use variance to determine which is the collider in a triangle. Doesn't work well unless you have a pure
 * triangle.
 *
 * @author Joseph Ramsey
 */
public class TestVarianceTiebreaking extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestVarianceTiebreaking(String name) {
        super(name);
    }


    public void setUp() throws Exception {
        TetradLogger.getInstance().addOutputStream(System.out);
        TetradLogger.getInstance().setForceLog(true);
    }


    public void tearDown() {
        TetradLogger.getInstance().setForceLog(false);
        TetradLogger.getInstance().removeOutputStream(System.out);
    }

    /**
     * Runs the PC algorithm on the graph X1 --> X2, X1 --> X3, X2 --> X4, X3 --> X4. Should produce X1 -- X2, X1 -- X3,
     * X2 --> X4, X3 --> X4.
     */
    public void testSearch1() {
        Dag dag = new Dag();

        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");
        Node z = new GraphNode("Z");

        dag.addNode(x);
        dag.addNode(y);
        dag.addNode(z);

        dag.addDirectedEdge(x, y);
        dag.addDirectedEdge(y, z);
        dag.addDirectedEdge(x, z);

        System.out.println(dag);


        SemPm semPm = new SemPm(dag);
        SemIm semIm = new SemIm(semPm);

        semIm.setParamValue(x, y, 1.0);
        semIm.setParamValue(y, z, -0.5);
        semIm.setParamValue(x, z, 0.5);

        System.out.println(semIm);

        DataSet data = semIm.simulateData(1000, false);
        ICovarianceMatrix cov = new CovarianceMatrix(data);

//        System.out.println(variance(y, cov));

        System.out.println("x");
        System.out.println(variance(x, cov));
        System.out.println(conditionalVariance(x, y, cov));
        System.out.println(conditionalVariance(x, z, cov));

        System.out.println("y");
        System.out.println(variance(y, cov));
        System.out.println(conditionalVariance(y, x, cov));
        System.out.println(conditionalVariance(y, z, cov));

        System.out.println("z");
        System.out.println(variance(z, cov));
        System.out.println(conditionalVariance(z, x, cov));
        System.out.println(conditionalVariance(z, y, cov));

//        IndependenceTest test = new IndTestFisherZ(data, 0.5);
//
//        PcSearch pc = new PcSearch(test, new Knowledge2());
//
//        Graph pattern = pc.search();
//
//        System.out.println("Pattern = " + pattern);

    }

    private double variance(Node x, ICovarianceMatrix cov) {
        int index = cov.getVariableNames().indexOf(x.getName());
        return cov.getMatrix().get(index, index);
    }

    private double conditionalVariance(Node x, Node y, ICovarianceMatrix cov) {
        int[] indices = new int[2];

        List<String> variableNames = cov.getVariableNames();
        indices[0] = variableNames.indexOf(x.getName());
        indices[1] = variableNames.indexOf(y.getName());

        // Extract submatrix of correlation cov using this index array.
        TetradMatrix submatrix =
                cov.getMatrix().getSelection(indices, indices);

        // Invert submatrix.
        if (submatrix.rank() != submatrix.rows()) {
            throw new IllegalArgumentException(
                    "Matrix singularity detected.");
        }

//        if (TetradAlgebra.rank(submatrix) != submatrix.rows()) {
//            throw new IllegalArgumentException(
//                    "Matrix singularity detected.");
//        }

        submatrix = submatrix.inverse();

        double d = submatrix.get(0, 0);

        return (d - 1) / d;
//
//        return d;

//        return 1./d;
    }

    private double conditionalVariance(Node x, Node y, Node z, ICovarianceMatrix cov) {
        int[] indices = new int[3];

        List<String> variableNames = cov.getVariableNames();
        indices[0] = variableNames.indexOf(x.getName());
        indices[1] = variableNames.indexOf(y.getName());
        indices[2] = variableNames.indexOf(z.getName());

        // Extract submatrix of correlation cov using this index array.
        TetradMatrix submatrix =
                cov.getMatrix().getSelection(indices, indices);

        // Invert submatrix.
        if (submatrix.rank() != submatrix.rows()) {
            throw new IllegalArgumentException(
                    "Matrix singularity detected.");
        }

//        if (TetradAlgebra.rank(submatrix) != submatrix.rows()) {
//            throw new IllegalArgumentException(
//                    "Matrix singularity detected.");
//        }

        submatrix = submatrix.inverse();

        double d = submatrix.get(0, 0);

        return (d - 1) / d;

//        return d;

//        return 1./d;
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestVarianceTiebreaking.class);
    }
}



