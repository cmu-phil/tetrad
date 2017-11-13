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

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.sem.TemplateExpander;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.StatUtils;
import org.junit.Test;

import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.Math.abs;

/**
 * My script.
 *
 * @author jdramsey
 */
public class TestVxx {

    @Test
    public void TestCycles_Data_fMRI_FASK() {

        int num = 100;
        double[] e1 = new double[num];
        double[] e2 = new double[num];
        double[] e3 = new double[num];
        double[] e4 = new double[num];
        double[] e5 = new double[num];

        for (int i = 0; i < num; i++) {
            Node x = new GraphNode("X");
            Node y = new GraphNode("Y");
            Node z = new GraphNode("Z");

            EdgeListGraph graph = new EdgeListGraph();
            graph.addNode(x);
            graph.addNode(y);
            graph.addNode(z);

            graph.addDirectedEdge(x, y);
            graph.addDirectedEdge(z, x);
            graph.addDirectedEdge(z, y);

            GeneralizedSemPm pm = new GeneralizedSemPm(graph);

            List<Node> errorNodes = pm.getErrorNodes();

            try {
                for (Node node : errorNodes) {
                    pm.setNodeExpression(node, "Beta(1, 5)");
                }

                pm.setParameterExpression("B", "Split(-.9,-.1,.1, .9)");
            } catch (ParseException e) {
                System.out.println(e);
            }

            GeneralizedSemIm im = new GeneralizedSemIm(pm);

            DataSet dataSet = im.simulateData(1000, false);

            Node dX = dataSet.getVariable("X");
            Node dY = dataSet.getVariable("Y");
            Node dZ = dataSet.getVariable("Z");
            List<Node> dVars = dataSet.getVariables();

            int iX = dVars.indexOf(dX);
            int iY = dVars.indexOf(dY);
            int iZ = dVars.indexOf(dZ);

            double[][] dd = dataSet.getDoubleData().transpose().toArray();

            double vzy = cu(dd[iZ], dd[iZ], dd[iY]);
            double vxy = cu(dd[iX], dd[iX], dd[iY]);
            double vzx = cu(dd[iZ], dd[iZ], dd[iX]);
            double vxx = cu(dd[iX], dd[iX], dd[iX]);
            double vxzy = cu(dd[iX], dd[iZ], dd[iY]);
            double vxzx = cu(dd[iX], dd[iZ], dd[iX]);

            System.out.println("\nvzx = " + vxx + " vxx = " + vxx + " vzy = " + vzy + " vxy = " + vxy);

            System.out.println(" vzy / vxy = " + (vzy / vxy) + " vzx / vxx = " + (vzx / vxx));

            e1[i] = vzy / vxy;
            e2[i] = vzx / vxx;
            e4[i] = vzx;
            e5[i] = vzy;
            e3[i] = e4[i] - e5[i];
        }

        System.out.println();

        System.out.println("mean vzx = " + StatUtils.mean(e4));
        System.out.println("variance vzx = " + StatUtils.variance(e4));
        System.out.println("mean vzy = " + StatUtils.mean(e5));
        System.out.println("variance vzy = " + StatUtils.variance(e5));

//        System.out.println("mean vzy / vxy = " + StatUtils.mean(e1));
//        System.out.println("variance vzy / vxy = " + StatUtils.variance(e1));
//        System.out.println("mean vzx / vxx = " + StatUtils.mean(e2));
//        System.out.println("variance vzx / vxx = " + StatUtils.variance(e2));
//
        System.out.println("mean diff = " + StatUtils.mean(e3));
        System.out.println("variance diff = " + StatUtils.variance(e3));
    }

    public static double cu(double[] x, double[] y, double[] condition) {
        double exy = 0.0;

        int n = 0;

        for (int k = 0; k < x.length; k++) {
            if (condition[k] > 0) {
                exy += x[k] * y[k];
                n++;
            }
        }

        return exy / n;
    }

    public static void main(String... args) {
        new TestVxx().TestCycles_Data_fMRI_FASK();
    }
}




