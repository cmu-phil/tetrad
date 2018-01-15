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

import edu.cmu.tetrad.algcomparison.algorithm.CStar;
import edu.cmu.tetrad.algcomparison.algorithm.CStar2;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Ida;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import org.junit.Test;

import java.util.List;

import static java.lang.Math.abs;
import static java.lang.Math.min;

/**
 * Tests IDA.
 *
 * @author Joseph Ramsey
 */
public class TestIda {

    @Test
    public void test1() {
        Graph graph = GraphUtils.randomGraph(10, 0, 10,
                100, 100, 100, false);

        System.out.println(graph);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(1000, false);

        Node y = dataSet.getVariable("X10");

        Ida ida = new Ida(new CovarianceMatrixOnTheFly(dataSet));

        Ida.NodeEffects effects = ida.getSortedMinEffects(y);

        for (int i = 0; i < effects.getNodes().size(); i++) {
            Node x = effects.getNodes().get(i);
            System.out.println(x + "\t" + effects.getEffects().get(i));
        }
    }

    @Test
    public void test2() {
        Graph trueDag = GraphUtils.randomGraph(10, 0, 10,
                100, 100, 100, false);

        System.out.println(trueDag);

        SemPm pm = new SemPm(trueDag);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(1000, false);
        trueDag = GraphUtils.replaceNodes(trueDag, dataSet.getVariables());

        Ida ida = new Ida(new CovarianceMatrixOnTheFly(dataSet));

        final List<Node> variables = dataSet.getVariables();

        for (Node x : variables) {
            for (Node y : variables) {
                if (x == y) continue;
                double trueEffect = ida.trueEffect(x, y, trueDag);

                List<Double> effects = ida.getEffects(x, y);

                if (!effects.isEmpty()) {
                    double distance = 0.0;

                    if (effects.size() > 1) {
                        double min = effects.get(0);
                        double max = effects.get(effects.size() - 1);

                        if (trueEffect >= min && trueEffect <= max) {
                            distance = 0.0;
                        } else {
                            final double m1 = abs(trueEffect - min);
                            final double m2 = abs(trueEffect - max);
                            distance = min(m1, m2);
                        }
                    }

                    System.out.println("x = " + x + " y = " + y + " distance = " + distance);
                }
            }
        }
    }

    @Test
    public void testCStar() {
        Graph trueDag = GraphUtils.randomGraph(20, 0, 40,
                100, 100, 100, false);

        System.out.println(trueDag);

        SemPm pm = new SemPm(trueDag);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(100, false);

        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", 2);
        parameters.set("numSubsamples", 100);
        parameters.set("percentSubsampleSize", .5);
        parameters.set("topQ", 5);
        parameters.set("piThreshold", .7);
        parameters.set("targetName", "X14");

        CStar cstar = new CStar();

        Graph graph = cstar.search(dataSet, parameters);

        System.out.println(graph);
    }

    @Test
    public void testCStar2() {
        Graph trueDag = GraphUtils.randomGraph(20, 0, 40,
                100, 100, 100, false);

        System.out.println(trueDag);

        SemPm pm = new SemPm(trueDag);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(100, false);

        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", 2);
        parameters.set("numSubsamples", 100);
        parameters.set("percentSubsampleSize", .5);
        parameters.set("topQ", 5);
        parameters.set("piThreshold", .7);
        parameters.set("targetName", "X14");

        CStar2 cstar = new CStar2();

        Graph graph = cstar.search(dataSet, parameters);

        System.out.println(graph);
    }
}





