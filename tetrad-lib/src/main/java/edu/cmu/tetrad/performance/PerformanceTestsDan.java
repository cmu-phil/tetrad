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

package edu.cmu.tetrad.performance;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.DagToPag;
import edu.cmu.tetrad.search.GFci;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Contains some tests for Dan Malinsky, that might be of interest to others.
 * @author Joseph Ramsey.
 */
public class PerformanceTestsDan {
    private void testIcaOutputForDan() {
        int numRuns = 100;

        for (int run = 0; run < numRuns; run++) {
            double alphaGFci = 0.01;
            double alphaPc = 0.01;
            int penaltyDiscount = 1;
            int depth = 3;
            int maxPathLength = 3;

            final int numVars = 15;
            final double edgesPerNode = 1.0;
            final int numCases = 1000;
            final int numLatents = RandomUtil.getInstance().nextInt(3) + 1;

//        writeToFile = false;

            PrintStream out1;
            PrintStream out2;
            PrintStream out3;
            PrintStream out4;
            PrintStream out5;
            PrintStream out6;
            PrintStream out7;
            PrintStream out8;
            PrintStream out9;
            PrintStream out10;
            PrintStream out11;
            PrintStream out12;

            File dir0 = new File("fci..ges.output");
            dir0.mkdirs();

            File dir = new File(dir0, "" + (run + 1));
            dir.mkdir();

            try {
                out1 = new PrintStream(new File(dir, "hyperparameters.txt"));
                out2 = new PrintStream(new File(dir, "variables.txt"));
                out3 = new PrintStream(new File(dir, "dag.long.txt"));
                out4 = new PrintStream(new File(dir, "dag.matrix.txt"));
                out5 = new PrintStream(new File(dir, "coef.matrix.txt"));
                out6 = new PrintStream(new File(dir, "pag.long.txt"));
                out7 = new PrintStream(new File(dir, "pag.matrix.txt"));
                out8 = new PrintStream(new File(dir, "pattern.long.txt"));
                out9 = new PrintStream(new File(dir, "pattern.matrix.txt"));
                out10 = new PrintStream(new File(dir, "data.txt"));
                out11 = new PrintStream(new File(dir, "true.pag.long.txt"));
                out12 = new PrintStream(new File(dir, "true.pag.matrix.txt"));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }

            out1.println("Num _vars = " + numVars);
            out1.println("Num edges = " + (int) (numVars * edgesPerNode));
            out1.println("Num cases = " + numCases);
            out1.println("Alpha for PC = " + alphaPc);
            out1.println("Alpha for FFCI = " + alphaGFci);
            out1.println("Penalty discount = " + penaltyDiscount);
            out1.println("Depth = " + depth);
            out1.println("Maximum reachable path length for dsep search and discriminating undirectedPaths = " + maxPathLength);

            List<Node> vars = new ArrayList<Node>();
            for (int i = 0; i < numVars; i++) vars.add(new GraphNode("X" + (i + 1)));

//        Graph dag = DataGraphUtils.randomDagQuick2(varsWithLatents, 0, (int) (varsWithLatents.size() * edgesPerNode));
            Graph dag = GraphUtils.randomGraph(vars, 0, (int) (vars.size() * edgesPerNode), 5, 5, 5, false);

            GraphUtils.fixLatents1(numLatents, dag);
//        List<Node> varsWithLatents = new ArrayList<Node>();
//
//        Graph dag = getLatentGraph(_vars, varsWithLatents, edgesPerNode, numLatents);


            out3.println(dag);

            printDanMatrix(vars, dag, out4);

            SemPm pm = new SemPm(dag);
            SemIm im = new SemIm(pm);
            NumberFormat nf = new DecimalFormat("0.0000");

            for (int i = 0; i < vars.size(); i++) {
                for (Node var : vars) {
                    if (im.existsEdgeCoef(var, vars.get(i))) {
                        double coef = im.getEdgeCoef(var, vars.get(i));
                        out5.print(nf.format(coef) + "\t");
                    } else {
                        out5.print(nf.format(0) + "\t");
                    }
                }

                out5.println();
            }

            out5.println();

            out2.println(vars);

            List<Node> _vars = new ArrayList<Node>();

            for (Node node : _vars) {
                if (node.getNodeType() == NodeType.MEASURED) {
                    _vars.add(node);
                }
            }

            out2.println(_vars);

            DataSet fullData = im.simulateData(numCases, false);

            DataSet data = DataUtils.restrictToMeasured(fullData);

            ICovarianceMatrix cov = new CovarianceMatrix(data);

            final IndTestFisherZ independenceTestGFci = new IndTestFisherZ(cov, alphaGFci);

            out6.println("FCI.GES.PAG");

            GFci gFci = new GFci(independenceTestGFci);
            gFci.setVerbose(false);
            gFci.setPenaltyDiscount(penaltyDiscount);
            gFci.setDepth(depth);
            gFci.setMaxPathLength(maxPathLength);
            gFci.setPossibleDsepSearchDone(true);
            gFci.setCompleteRuleSetUsed(true);

            Graph pag = gFci.search();

            out6.println(pag);

            printDanMatrix(_vars, pag, out7);

            out8.println("PATTERN OVER MEASURED VARIABLES");

            final IndTestFisherZ independencePc = new IndTestFisherZ(cov, alphaPc);

            Pc pc = new Pc(independencePc);
            pc.setVerbose(false);
            pc.setDepth(depth);

            Graph pattern = pc.search();

            out8.println(pattern);

            printDanMatrix(_vars, pattern, out9);

            out10.println(data);

            out11.println("True PAG");
            final Graph truePag = new DagToPag(dag).convert();
            out11.println(truePag);
            printDanMatrix(_vars, truePag, out12);

            out1.close();
            out2.close();
            out3.close();
            out4.close();
            out5.close();
            out6.close();
            out7.close();
            out8.close();
            out9.close();
            out10.close();
            out11.close();
            out12.close();
        }
    }

    private void printDanMatrix(List<Node> vars, Graph pattern, PrintStream out) {
        for (int i = 0; i < vars.size(); i++) {
            for (Node var : vars) {
                Edge edge = pattern.getEdge(vars.get(i), var);

                if (edge == null) {
                    out.print(0 + "\t");
                } else {
                    Endpoint ej = edge.getProximalEndpoint(var);

                    if (ej == Endpoint.TAIL) {
                        out.print(3 + "\t");
                    } else if (ej == Endpoint.ARROW) {
                        out.print(2 + "\t");
                    } else if (ej == Endpoint.CIRCLE) {
                        out.print(1 + "\t");
                    }
                }
            }

            out.println();
        }

        out.println();
    }

    public static void main(String... args) {
        NodeEqualityMode.setEqualityMode(NodeEqualityMode.Type.OBJECT);
        System.out.println("Start ");

        new PerformanceTestsDan().testIcaOutputForDan();
    }
}

