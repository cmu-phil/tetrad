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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.sem.*;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;


/**
 * Tests the MeasurementSimulator class using diagnostics devised by Richard
 * Scheines. The diagnostics are described in the Javadocs, below.
 *
 * @author Joseph Ramsey
 */
public class TestDagScorer extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestDagScorer(String name) {
        super(name);
    }

    public void test1() {
        Graph dag = new Dag(GraphUtils.randomGraph(10, 0, 10, 30, 15, 15, false));

        SemPm pm = new SemPm(dag);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(1000, false);

        GraphUtils.replaceNodes(dag, data.getVariables());

        SemEstimator est = new SemEstimator(data, pm);
        SemIm estSem = est.estimate();
        System.out.println("FML = " + estSem.getScore());

        dag = GraphUtils.replaceNodes(dag, data.getVariables());

        System.out.println(estSem.getEdgeCoef());
        System.out.println(estSem.getErrCovar());

        Scorer scorer = new DagScorer(data);
        double fml = scorer.score(dag);
        System.out.println("FML (scorer) = " + fml);

        System.out.println("BIC = " + scorer.getBicScore());
        System.out.println("DOF = " + scorer.getDof());
        System.out.println("# free params = " + scorer.getNumFreeParams());
        System.out.println("est sem = " + scorer.getEstSem());

        Graph newDag = new Dag(GraphUtils.randomDag(data.getVariables(), 0, 10, 30, 15, 15, false));
        System.out.println("new FML " + scorer.score(newDag));
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestDagScorer.class);
    }
}


