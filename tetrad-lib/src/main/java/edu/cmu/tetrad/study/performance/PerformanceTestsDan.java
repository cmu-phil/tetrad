/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

package edu.cmu.tetrad.study.performance;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.FgesFci;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Contains some tests for Dan Malinsky, that might be of interest to others.
 *
 * @author josephramsey.
 * @version $Id: $Id
 */
public class PerformanceTestsDan {

    /**
     * <p>Constructor for PerformanceTestsDan.</p>
     */
    public PerformanceTestsDan() {
    }

    /**
     * <p>main.</p>
     *
     * @param args a {@link java.lang.String} object
     */
    public static void main(String... args) {
        System.out.println("Start ");

        try {
            new PerformanceTestsDan().testIdaOutputForDan();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void testIdaOutputForDan() throws InterruptedException {
        final int numRuns = 100;

        for (int run = 0; run < numRuns; run++) {
            final double alphaFgesFci = 0.01;
            final double alphaPc = 0.01;
            final int penaltyDiscount = 1;
            final int depth = 3;
            final int maxDiscriminatingPathLength = -1;

            final int numVars = 15;
            final double edgesPerNode = 1.0;
            final int numCases = 1000;
//            final int numLatents = RandomUtil.getInstance().nextInt(3) + 1;
            final int numLatents = 6;

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

            File dir0 = new File("fges.fci.output");
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
                out8 = new PrintStream(new File(dir, "cpdag.long.txt"));
                out9 = new PrintStream(new File(dir, "cpdag.matrix.txt"));
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
            out1.println("Alpha for FFCI = " + alphaFgesFci);
            out1.println("Penalty discount = " + penaltyDiscount);
            out1.println("Depth = " + depth);
            out1.println("Maximum reachable path length for msep search and discriminating undirectedPaths = " + maxDiscriminatingPathLength);

            List<Node> vars = new ArrayList<>();
            for (int i = 0; i < numVars; i++) vars.add(new GraphNode("X" + (i + 1)));

//        Graph dag = DataGraphUtils.randomDagQuick2(varsWithLatents, 0, (int) (varsWithLatents.size() * edgesPerNode));
            Graph dag = RandomGraph.randomGraph(vars, 0, (int) (vars.size() * edgesPerNode), 5, 5, 5, false);

            RandomGraph.fixLatents1(numLatents, dag);


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

            String vars_temp = vars.toString();
            vars_temp = vars_temp.replace("[", "");
            vars_temp = vars_temp.replace("]", "");
            vars_temp = vars_temp.replace("X", "");
            out2.println(vars_temp);

            List<Node> _vars = new ArrayList<>();

            for (Node node : vars) {
                if (node.getNodeType() == NodeType.MEASURED) {
                    _vars.add(node);
                }
            }

            String _vars_temp = _vars.toString();
            _vars_temp = _vars_temp.replace("[", "");
            _vars_temp = _vars_temp.replace("]", "");
            _vars_temp = _vars_temp.replace("X", "");
            out2.println(_vars_temp);

            DataSet fullData = im.simulateData(numCases, false);

            DataSet data = DataTransforms.restrictToMeasured(fullData);

            ICovarianceMatrix cov = new CovarianceMatrix(data);

            IndTestFisherZ independenceTestGFci = new IndTestFisherZ(cov, alphaFgesFci);
            SemBicScore scoreGfci = new SemBicScore(cov);

            out6.println("GFCI.PAG_of_the_true_DAG");

            FgesFci fgesFci = new FgesFci(independenceTestGFci, scoreGfci);
            fgesFci.setVerbose(false);
            fgesFci.setMaxDegree(depth);
            fgesFci.setMaxDiscriminatingPathLength(maxDiscriminatingPathLength);
//            fgesFci.setPossibleDsepSearchDone(true);
            fgesFci.setCompleteRuleSetUsed(true);

            Graph pag = fgesFci.search();

            out6.println(pag);
            printDanMatrix(_vars, pag, out7);

            out8.println("CPDAG_of_the_true_DAG OVER MEASURED VARIABLES");

            IndTestFisherZ independencePc = new IndTestFisherZ(cov, alphaPc);

            Pc pc = new Pc(independencePc);
            pc.setVerbose(false);
            pc.setDepth(depth);

            Graph CPDAG = pc.search();

            out8.println(CPDAG);

            printDanMatrix(_vars, CPDAG, out9);

            out10.println(data);

            out11.println("True PAG_of_the_true_DAG");
            Graph truePag = GraphTransforms.dagToPag(dag);
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

    private void printDanMatrix(List<Node> vars, Graph CPDAG, PrintStream out) {
        CPDAG = GraphUtils.replaceNodes(CPDAG, vars);
        for (int i = 0; i < vars.size(); i++) {
            for (Node var : vars) {
                Edge edge = CPDAG.getEdge(vars.get(i), var);

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
}

