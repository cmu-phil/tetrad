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

package edu.cmu.tetradapp.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.Discretizer;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.LogisticRegression;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a test of logistic regression based on an example (South African heart
 * disease) from "Elements of Statistical Learning" by Hastie, Tibshirani and
 * Friedman.
 *
 * @author Frank Wimberly
 */
public class TestLogisticRegression {

    @Test
    public void test1() {


        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph graph = new Dag(GraphUtils.randomGraph(nodes, 0, 5,
                3, 3, 3, false));

        System.out.println(graph);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateDataRecursive(1000, false);

        Node x1 = data.getVariable("X1");
        Node x2 = data.getVariable("X2");
        Node x3 = data.getVariable("X3");
        Node x4 = data.getVariable("X4");
        Node x5 = data.getVariable("X5");

        Discretizer discretizer = new Discretizer(data);

        discretizer.equalCounts(x1, 2);

        DataSet d2 = discretizer.discretize();

        LogisticRegression regression = new LogisticRegression(d2);

        List<Node> regressors = new ArrayList<>();

        regressors.add(x2);
        regressors.add(x3);
        regressors.add(x4);
        regressors.add(x5);

        DiscreteVariable x1b = (DiscreteVariable) d2.getVariable("X1");

        regression.regress(x1b, regressors);

        System.out.println(regression);
    }
}





