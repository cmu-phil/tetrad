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

import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.GesMe;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

/**
 * @author Joseph Ramsey
 */
public class TestFgesFa {


    public void test1() {

        final Graph graph = GraphUtils.randomGraph(10, 0, 10,
                100, 100, 100, false);

        System.out.println(graph);

        final SemPm semPm = new SemPm(graph);
        final SemIm semIm = new SemIm(semPm);
        final DataSet dataSet = semIm.simulateData(1000, false);

        final Parameters parameters = new Parameters();
        parameters.set(Params.PENALTY_DISCOUNT, 8);
        parameters.set(Params.SYMMETRIC_FIRST_STEP, true);
        parameters.set(Params.FAITHFULNESS_ASSUMED, false);
        parameters.set(Params.MAX_DEGREE, 100);
        parameters.set(Params.VERBOSE, true);
        parameters.set(Params.DETERMINISM_THRESHOLD, .2);
        parameters.set("convergenceThreshold", 1e-7);

        parameters.set(Params.DETERMINISM_THRESHOLD, 1);

        final GesMe alg = new GesMe();
        final Graph CPDAG = alg.search(dataSet, parameters);

        System.out.println(CPDAG);
    }

    public static void main(final String... args) {
        new TestFgesFa().test1();
    }
}




