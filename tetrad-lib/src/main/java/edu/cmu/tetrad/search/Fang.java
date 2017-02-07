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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.*;

import static edu.cmu.tetrad.util.StatUtils.mean;
import static edu.cmu.tetrad.util.StatUtils.sd;
import static java.lang.Math.*;

/**
 * Fast adjacency search followed by pairwise orientation. If run with time series data and
 * knowledge, the graph can optionally be collapsed. The R3 algorithm was selected for
 * pairwise, after trying several such algorithms. The independence test needs to return its
 * data using the getData method.
 *
 * @author Joseph Ramsey
 */
public final class Fang implements GraphSearch {
    private IndependenceTest independenceTest;
    private int depth = -1;
    private long elapsed = 0;
    private IKnowledge knowledge = new Knowledge2();
    private List<DataSet> dataSets = null;
    private double alpha = 0.05;

    public Fang(IndependenceTest test, List<DataSet> dataSets) {
        if (test == null) throw new NullPointerException();
        this.independenceTest = test;
        this.knowledge = new Knowledge2();
        this.dataSets = dataSets;
    }

    //======================================== PUBLIC METHODS ====================================//

    public Graph search() {
        long start = System.currentTimeMillis();

        System.out.println("FAS");

        DataSet dataSet = DataUtils.concatenate(dataSets);

        final ICovarianceMatrix cov = new CovarianceMatrix(dataSet);
        List<Node> variables = cov.getVariables();
        Graph graph = new EdgeListGraph(variables);

        double[][] colData = dataSet.getDoubleData().transpose().toArray();

        FasStableConcurrent fas = new FasStableConcurrent(null, independenceTest);
        fas.setDepth(getDepth());
        fas.setKnowledge(knowledge);
        fas.setVerbose(false);
        Graph graph0 = fas.search();

        System.out.println("Robust Skew");

        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                if (i == j) continue;

                final double[] xData = colData[i];
                final double[] yData = colData[j];

                double[] xxg = new double[xData.length];
                double[] yyg = new double[yData.length];
                double[] xxh = new double[xData.length];
                double[] yyh = new double[yData.length];

                for (int k = 0; k < xxg.length; k++) {
                    double xi = xData[k];
                    double yi = yData[k];

                    xxg[k] = g(xi) * yi;
                    yyg[k] = xi * g(yi);
                    xxh[k] = h(xi) * yi;
                    yyh[k] = xi * h(yi);
                }

                double[] Dg = new double[xxg.length];
                double[] Dh = new double[xxg.length];

                for (int k = 0; k < xxg.length; k++) {
                    Dg[k] = xxg[k] - yyg[k];
                    Dh[k] = xxh[k] - yyh[k];
                }

                double mdg = mean(Dg);
                double sdg = sd(Dg);

                double mdh = mean(Dh);
                double sdh = sd(Dh);

                int n = xxg.length;

                double mxxg = mean(xxg);
                double myyg = mean(yyg);
                double mxxh = mean(xxh);
                double myyh = mean(yyh);

                double tdg = (mdg - 0.0) / (sdg / sqrt(n));
                double tdxg = (mxxg - 0.0) / (sdg / sqrt(n));
                double tdyg = (myyg - 0.0) / (sdg / sqrt(n));
//                double tda = 2 * (1.0 - new TDistribution(n - 1).cumulativeProbability(Math.abs(tdg)));
//
//                double xa = new AndersonDarlingTest(xData).getP();
//                double ya = new AndersonDarlingTest(yData).getP();

                double tdh = (mdh - 0.0) / (sdh / sqrt(n));
                double tdxh = (mxxh - 0.0) / (sdh / sqrt(n));
                double tdyh = (myyh - 0.0) / (sdh / sqrt(n));

                if (abs(tdh) > 12 || graph0.isAdjacentTo(variables.get(i), variables.get(j))) {
                    Node x = variables.get(i);
                    Node y = variables.get(j);
                    graph.removeEdge(x, y);

                    System.out.println("Edge " + x + "--" + y + " tdg = " + tdg + " mxxg = " + mxxg + " myyg = " + myyg);

                    if (signum(tdxh) == -signum(tdyh) || abs(tdh) < 1.96 || abs(tdxh) < 1.96 || abs(tdyg) < 1.96) {
                        graph.addDirectedEdge(x, y);
                        graph.addDirectedEdge(y, x);
                        System.out.println("\n    ORIENTING " + x + "--" + y + " AS A TWO CYCLE: " + "xa = " +
                                " tdg = " + tdg + " mxxg = " + mxxg + " myyg = " + myyg + "\n");
                    } else if (tdg > 1.96) {
                        graph.addDirectedEdge(x, y);
                    } else if (tdg < -1.96) {
                        graph.addDirectedEdge(y, x);
                    } else {
                        graph.addUndirectedEdge(x, y);
                    }
                }
            }
        }

        long stop = System.currentTimeMillis();
        this.elapsed = stop - start;

        System.out.println("Done");
        return graph;

    }

    private double g(double x) {
        return log(Math.cosh(Math.max(x, 0)));
    }

    private double h(double x) {
        return x < 0 ? 0 : x;
    }

    /**
     * @return The depth of search for the Fast Adjacency Search.
     */
    public int getDepth() {
        return depth;
    }

    /**
     * @param depth The depth of search for the Fast Adjacency Search.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * @return The elapsed time in milliseconds.
     */
    public long getElapsedTime() {
        return elapsed;
    }

    //======================================== PRIVATE METHODS ====================================//

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }
}






