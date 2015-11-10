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
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.Kpc;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.TetradLogger;
import junit.framework.TestCase;

/**
 * Tests Kpc class
 *
 * @author Robert Tillman
 */
public class TestKpc extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestKpc(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        TetradLogger.getInstance().addOutputStream(System.out);
        TetradLogger.getInstance().setForceLog(true);
        TetradLogger.getInstance().setLogging(true);
    }


    public void tearDown() {
        TetradLogger.getInstance().setForceLog(false);
        TetradLogger.getInstance().removeOutputStream(System.out);
    }

    public void testBlank() {

    }

    // This takes too long for the test set--Joe

    public void rtestkPCSearch() {
        Dag dag = new Dag(GraphUtils.randomGraph(4, 0, 4, 3,
                3, 3, true));
        SemPm sem = new SemPm(dag);
        SemIm im = new SemIm(sem);
        DataSet data = im.simulateData(500, false);
        System.out.println("True Graph: \n" + dag);
        Kpc kpc = new Kpc(data, .05);
        kpc.setIncompleteCholesky(1e-18);
        kpc.search();
        Pc pc = new Pc(new IndTestFisherZ(data, .05));
        pc.search();
    }

}




