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

import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static edu.cmu.tetrad.util.MatrixUtils.transpose;
import static edu.cmu.tetrad.util.StatUtils.median;
import static java.lang.Math.*;

/**
 * LOFS = Ling Orientation Fixed Structure. Some additional algorithms.
 * </p>
 * Expands the set of algorithms from Lofs.
 *
 * @author Joseph Ramsey
 */
public class Lofs2 {

    public enum Score {
        andersonDarling, skew, kurtosis, fifthMoment, absoluteValue,
        exp, expUnstandardized, expUnstandardizedInverted, other, logcosh, entropy
    }

    private Graph pattern;
    private List<DataSet> dataSets;
    private List<TetradMatrix> matrices;
    private double alpha = 1.0;
    private List<Regression> regressions;
    private List<Node> variables;
    private List<String> varnames;
    private boolean orientStrongerDirection = false;
    private boolean r2Orient2Cycles = true;

    private Lofs.Score score = Lofs.Score.andersonDarling;
    private double epsilon = 1.0;
    private IKnowledge knowledge = new Knowledge2();
    private Rule rule = Rule.R1;
    private double delta = 0.0;
    private double zeta = 0.0;
    private boolean edgeCorrected = false;
    private double selfLoopStrength;

    //===============================CONSTRUCTOR============================//

    public Lofs2(Graph pattern, List<DataSet> dataSets)
            throws IllegalArgumentException {

        if (dataSets == null) {
            throw new IllegalArgumentException("Data set must be specified.");
        }

        if (pattern == null) {
            throw new IllegalArgumentException("Pattern must be specified.");
        }

        this.pattern = pattern;
        this.variables = dataSets.get(0).getVariables();
        this.varnames = dataSets.get(0).getVariableNames();

        List<DataSet> dataSets2 = new ArrayList<DataSet>();

        for (int i = 0; i < dataSets.size(); i++) {
            DataSet dataSet = ColtDataSet.makeContinuousData(variables, dataSets.get(i).getDoubleData());
            dataSets2.add(dataSet);
        }


        this.dataSets = dataSets2;
    }

    //==========================PUBLIC=========================================//

    public void setRule(Rule rule) {
        this.rule = rule;
    }

    public boolean isEdgeCorrected() {
        return edgeCorrected;
    }

    public void setEdgeCorrected(boolean C) {
        this.edgeCorrected = C;
    }

    public double getSelfLoopStrength() {
        return selfLoopStrength;
    }

    public void setSelfLoopStrength(double selfLoopStrength) {
        this.selfLoopStrength = selfLoopStrength;
    }

    // orientStrongerDirection list of past and present rules.
    public enum Rule {
        IGCI, R1TimeLag, R1, R2, R3, R4, Tanh, EB, Skew, SkewE, RSkew, RSkewE,
        Patel, Patel25, Patel50, Patel75, Patel90, FastICA, RC, Nlo
    }

    public Graph orient() {

        Graph skeleton = GraphUtils.undirectedGraph(getPattern());
        Graph graph = new EdgeListGraph(skeleton.getNodes());

        List<Node> nodes = skeleton.getNodes();

        if (this.rule == Rule.R1TimeLag) {
            ruleR1TimeLag(skeleton, graph);
        } else if (this.rule == Rule.R1) {
            ruleR1(skeleton, graph, nodes);
        } else if (this.rule == Rule.R2) {
            graph = GraphUtils.undirectedGraph(skeleton);
            ruleR2(graph, graph);
        } else if (this.rule == Rule.R3) {
            graph = GraphUtils.undirectedGraph(skeleton);
            ruleR3(graph);
        } else if (this.rule == Rule.R4) {
            graph = GraphUtils.undirectedGraph(skeleton);
            return ruleR4(graph);
        } else if (this.rule == Rule.EB) {
            graph = GraphUtils.undirectedGraph(skeleton);
            return entropyBased(graph);
        } else if (this.rule == Rule.Tanh) {
            graph = GraphUtils.undirectedGraph(skeleton);
            return tanhGraph(graph);
        } else if (this.rule == Rule.Skew) {
            graph = GraphUtils.undirectedGraph(skeleton);
            return skewGraph(graph, false);
        } else if (this.rule == Rule.SkewE) {
            graph = GraphUtils.undirectedGraph(skeleton);
            return skewGraph(graph, true);
        } else if (this.rule == Rule.RSkew) {
            graph = GraphUtils.undirectedGraph(skeleton);
            return robustSkewGraph(graph, false);
        } else if (this.rule == Rule.RSkewE) {
            graph = GraphUtils.undirectedGraph(skeleton);
            return robustSkewGraph(graph, true);
        } else if (this.rule == Rule.IGCI) {
            graph = GraphUtils.undirectedGraph(skeleton);
            return igci(graph);
        } else if (this.rule == Rule.RC) {
            graph = GraphUtils.undirectedGraph(skeleton);
            return resolveEdgeConditional(graph);
        } else if (this.rule == Rule.Patel) {
            graph = GraphUtils.undirectedGraph(skeleton);
            return patelTauOrientation(graph, Double.NaN);
        } else if (this.rule == Rule.Patel25) {
            graph = GraphUtils.undirectedGraph(skeleton);
            return patelTauOrientation(graph, 0.25);
        } else if (this.rule == Rule.Patel50) {
            graph = GraphUtils.undirectedGraph(skeleton);
            return patelTauOrientation(graph, 0.50);
        } else if (this.rule == Rule.Patel75) {
            graph = GraphUtils.undirectedGraph(skeleton);
            return patelTauOrientation(graph, 0.75);
        } else if (this.rule == Rule.Patel90) {
            graph = GraphUtils.undirectedGraph(skeleton);
            return patelTauOrientation(graph, 0.90);
        } else if (this.rule == Rule.FastICA) {
            FastIca fastIca = new FastIca(dataSets.get(0).getDoubleData(),
                    dataSets.get(0).getNumColumns());
            FastIca.IcaResult result = fastIca.findComponents();
            System.out.println(result.getW());
            return new EdgeListGraph();
        } else if (this.rule == Rule.Nlo) {
            Nlo nlo = new Nlo(dataSets.get(0), alpha);
            Graph _graph = new EdgeListGraph(skeleton);
            _graph = GraphUtils.replaceNodes(_graph, dataSets.get(0).getVariables());
            return nlo.fullOrient4(_graph);
//            return nlo.fullOrient5(_graph);
//            return nlo.pairwiseOrient3(_graph);
        }

        return graph;
    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Alpha is in range [0, 1]");
        }

        this.alpha = alpha;
    }

    public boolean isOrientStrongerDirection() {
        return orientStrongerDirection;
    }

    public void setOrientStrongerDirection(boolean orientStrongerDirection) {
        this.orientStrongerDirection = orientStrongerDirection;
    }

    public void setR2Orient2Cycles(boolean r2Orient2Cycles) {
        this.r2Orient2Cycles = r2Orient2Cycles;
    }

    public boolean isR2Orient2Cycles() {
        return r2Orient2Cycles;
    }

    public Lofs.Score getScore() {
        return score;
    }

    public void setScore(Lofs.Score score) {
        if (score == null) {
            throw new NullPointerException();
        }

        this.score = score;
    }

    //==========================PRIVATE=======================================//

    private List<Regression> getRegressions() {
        if (this.regressions == null) {
            List<Regression> regressions = new ArrayList<Regression>();
            this.variables = dataSets.get(0).getVariables();

            for (DataSet dataSet : dataSets) {
                regressions.add(new RegressionDataset(dataSet));
            }

            this.regressions = regressions;
        }

        return this.regressions;
    }

    private void setDataSets(List<DataSet> dataSets) {
        this.dataSets = dataSets;

        matrices = new ArrayList<TetradMatrix>();

        for (DataSet dataSet : dataSets) {
            matrices.add(dataSet.getDoubleData());
        }
    }

    private void ruleR1TimeLag(Graph skeleton, Graph graph) {
        List<DataSet> timeSeriesDataSets = new ArrayList<DataSet>();
        IKnowledge knowledge = null;
        List<Node> dataNodes = null;

        for (DataModel dataModel : dataSets) {
            if (!(dataModel instanceof DataSet)) {
                throw new IllegalArgumentException("Only tabular data sets can be converted to time lagged form.");
            }

            DataSet dataSet = (DataSet) dataModel;
            DataSet lags = TimeSeriesUtils.createLagData(dataSet, 1);
            if (dataSet.getName() != null) {
                lags.setName(dataSet.getName());
            }
            timeSeriesDataSets.add(lags);

            if (knowledge == null) {
                knowledge = lags.getKnowledge();
            }

            if (dataNodes == null) {
                dataNodes = lags.getVariables();
            }
        }

        Graph laggedSkeleton = new EdgeListGraph(dataNodes);

        for (Edge edge : skeleton.getEdges()) {
            String node1 = edge.getNode1().getName();
            String node2 = edge.getNode2().getName();

            Node node10 = laggedSkeleton.getNode(node1 + ":0");
            Node node20 = laggedSkeleton.getNode(node2 + ":0");

            laggedSkeleton.addUndirectedEdge(node10, node20);

            Node node11 = laggedSkeleton.getNode(node1 + ":1");
            Node node21 = laggedSkeleton.getNode(node2 + ":1");

            laggedSkeleton.addUndirectedEdge(node11, node21);
        }

        for (Node node : skeleton.getNodes()) {
            String _node = node.getName();

            Node node0 = laggedSkeleton.getNode(_node + ":0");
            Node node1 = laggedSkeleton.getNode(_node + ":1");

            laggedSkeleton.addUndirectedEdge(node0, node1);
        }

        Lofs2 lofs = new Lofs2(laggedSkeleton, timeSeriesDataSets);
        lofs.setKnowledge(knowledge);
        lofs.setRule(Rule.R1);
        Graph _graph = lofs.orient();

        graph.removeEdges(new ArrayList<Edge>(graph.getEdges()));

        for (Edge edge : _graph.getEdges()) {
            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();
            Endpoint end1 = edge.getEndpoint1();
            Endpoint end2 = edge.getEndpoint2();

            String index1 = node1.getName().split(":")[1];
            String index2 = node2.getName().split(":")[1];

            if ("1".equals(index1) || "1".equals(index2)) continue;

            String name1 = node1.getName().split(":")[0];
            String name2 = node2.getName().split(":")[0];

            Node _node1 = graph.getNode(name1);
            Node _node2 = graph.getNode(name2);

            Edge _edge = new Edge(_node1, _node2, end1, end2);
            graph.addEdge(_edge);
        }
    }

    private void ruleR1(Graph skeleton, Graph graph, List<Node> nodes) {
        List<DataSet> centeredData = DataUtils.center(this.dataSets);
        setDataSets(centeredData);

        for (Node node : nodes) {
            SortedMap<Double, String> scoreReports = new TreeMap<Double, String>();

            List<Node> adj = new ArrayList<Node>();

            for (Node _node : skeleton.getAdjacentNodes(node)) {
                if (knowledge.isForbidden(_node.getName(), node.getName())) {
                    continue;
                }

                adj.add(_node);
            }

            DepthChoiceGenerator gen = new DepthChoiceGenerator(adj.size(), adj.size());
            int[] choice;
            double maxScore = Double.NEGATIVE_INFINITY;
            List<Node> parents = null;

            while ((choice = gen.next()) != null) {
                List<Node> _parents = GraphUtils.asList(choice, adj);

                double score = score(node, _parents);
                scoreReports.put(-score, _parents.toString());

                if (score > maxScore) {
                    maxScore = score;
                    parents = _parents;
                }
            }

            double p = pValue(node, parents);

            if (p > alpha) {
                continue;
            }

            for (double score : scoreReports.keySet()) {
                TetradLogger.getInstance().log("score", "For " + node + " parents = " + scoreReports.get(score) + " score = " + -score);
            }

            TetradLogger.getInstance().log("score", "");

            if (parents == null) {
                continue;
            }

            for (Node _node : adj) {
                if (parents.contains(_node)) {
                    Edge parentEdge = Edges.directedEdge(_node, node);

                    if (!graph.containsEdge(parentEdge)) {
                        graph.addEdge(parentEdge);
                    }
                }
            }
        }

        for (Edge edge : skeleton.getEdges()) {
            if (!graph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                graph.addUndirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }
    }

    private void ruleR2(Graph skeleton, Graph graph) {
        List<DataSet> standardized = DataUtils.standardizeData(this.dataSets);
        setDataSets(standardized);

        Set<Edge> edgeList1 = skeleton.getEdges();

        for (Edge adj : edgeList1) {
            Node x = adj.getNode1();
            Node y = adj.getNode2();

            if (!isR2Orient2Cycles() && isTwoCycle(graph, x, y)) {
                continue;
            }

            if (!isTwoCycle(graph, x, y) && !isUndirected(graph, x, y)) {
                continue;
            }

            resolveOneEdgeMax2(graph, x, y, !isOrientStrongerDirection());
        }
    }

    private void resolveOneEdgeMax2(Graph graph, Node x, Node y, boolean strong) {
        TetradLogger.getInstance().log("info", "\nEDGE " + x + " --- " + y);

        SortedMap<Double, String> scoreReports = new TreeMap<Double, String>();

        List<Node> neighborsx = new ArrayList<Node>();

        for (Node _node : graph.getAdjacentNodes(x)) {
            if (!knowledge.isForbidden(_node.getName(), x.getName())) {
//                if (!knowledge.edgeForbidden(x.getName(), _node.getName())) {
                neighborsx.add(_node);
            }
        }

//        neighborsx.remove(y);

        double max = Double.NEGATIVE_INFINITY;
        boolean left = false;
        boolean right = false;

        DepthChoiceGenerator genx = new DepthChoiceGenerator(neighborsx.size(), neighborsx.size());
        int[] choicex;

        while ((choicex = genx.next()) != null) {
            List<Node> condxMinus = GraphUtils.asList(choicex, neighborsx);

            if (condxMinus.contains(y)) continue;

            List<Node> condxPlus = new ArrayList<Node>(condxMinus);

            condxPlus.add(y);

            double xPlus = score(x, condxPlus);
            double xMinus = score(x, condxMinus);

            double p = pValue(x, condxPlus);

            if (p > alpha) {
                continue;
            }

            double p2 = pValue(x, condxMinus);

            if (p2 > alpha) {
                continue;
            }

            List<Node> neighborsy = new ArrayList<Node>();

            for (Node _node : graph.getAdjacentNodes(y)) {
                if (!knowledge.isForbidden(_node.getName(), y.getName())) {
                    neighborsy.add(_node);
                }
            }

            DepthChoiceGenerator geny = new DepthChoiceGenerator(neighborsy.size(), neighborsy.size());
            int[] choicey;

            while ((choicey = geny.next()) != null) {
                List<Node> condyMinus = GraphUtils.asList(choicey, neighborsy);

                if (condyMinus.contains(x)) continue;

                List<Node> condyPlus = new ArrayList<Node>(condyMinus);
                condyPlus.add(x);

                double yPlus = score(y, condyPlus);
                double yMinus = score(y, condyMinus);

                double p3 = pValue(y, condyPlus);

                if (p3 > alpha) {
                    continue;
                }

                double p4 = pValue(y, condyMinus);

                if (p4 > alpha) {
                    continue;
                }

                boolean forbiddenLeft = knowledge.isForbidden(y.getName(), x.getName());
                boolean forbiddenRight = knowledge.isForbidden(x.getName(), y.getName());

                double delta = 0.0;

                if (strong) {
                    if (yPlus <= xPlus + delta && xMinus <= yMinus + delta) {
                        double score = combinedScore(xPlus, yMinus);

                        if ((yPlus <= yMinus + delta && xMinus <= xPlus + delta) || forbiddenRight) {
                            StringBuilder builder = new StringBuilder();

                            builder.append("\nStrong ").append(y).append("->").append(x).append(" ").append(score);
                            builder.append("\n   Parents(").append(x).append(") = ").append(condxMinus);
                            builder.append("\n   Parents(").append(y).append(") = ").append(condyMinus);

                            scoreReports.put(-score, builder.toString());

                            if (score > max) {
                                max = score;
                                left = true;
                                right = false;
                            }
                        } else {
                            StringBuilder builder = new StringBuilder();

                            builder.append("\nNo directed edge ").append(x).append("--").append(y).append(" ").append(score);
                            builder.append("\n   Parents(").append(x).append(") = ").append(condxMinus);
                            builder.append("\n   Parents(").append(y).append(") = ").append(condyMinus);

                            scoreReports.put(-score, builder.toString());
                        }
                    } else if ((xPlus <= yPlus + delta && yMinus <= xMinus + delta) || forbiddenLeft) {
                        double score = combinedScore(yPlus, xMinus);

                        if (yMinus <= yPlus + delta && xPlus <= xMinus + delta) {
                            StringBuilder builder = new StringBuilder();

                            builder.append("\nStrong ").append(x).append("->").append(y).append(" ").append(score);
                            builder.append("\n   Parents(").append(x).append(") = ").append(condxMinus);
                            builder.append("\n   Parents(").append(y).append(") = ").append(condyMinus);

                            scoreReports.put(-score, builder.toString());

                            if (score > max) {
                                max = score;
                                left = false;
                                right = true;
                            }
                        } else {
                            StringBuilder builder = new StringBuilder();

                            builder.append("\nNo directed edge ").append(x).append("--").append(y).append(" ").append(score);
                            builder.append("\n   Parents(").append(x).append(") = ").append(condxMinus);
                            builder.append("\n   Parents(").append(y).append(") = ").append(condyMinus);

                            scoreReports.put(-score, builder.toString());
                        }
                    } else if (yPlus <= xPlus + delta && yMinus <= xMinus + delta) {
                        double score = combinedScore(yPlus, xMinus);

                        StringBuilder builder = new StringBuilder();

                        builder.append("\nNo directed edge ").append(x).append("--").append(y).append(" ").append(score);
                        builder.append("\n   Parents(").append(x).append(") = ").append(condxMinus);
                        builder.append("\n   Parents(").append(y).append(") = ").append(condyMinus);

                        scoreReports.put(-score, builder.toString());
                    } else if (xPlus <= yPlus + delta && xMinus <= yMinus + delta) {
                        double score = combinedScore(yPlus, xMinus);

                        StringBuilder builder = new StringBuilder();

                        builder.append("\nNo directed edge ").append(x).append("--").append(y).append(" ").append(score);
                        builder.append("\n   Parents(").append(x).append(") = ").append(condxMinus);
                        builder.append("\n   Parents(").append(y).append(") = ").append(condyMinus);

                        scoreReports.put(-score, builder.toString());
                    }
                } else {
                    if ((yPlus <= xPlus + delta && xMinus <= yMinus + delta) || forbiddenRight) {
                        double score = combinedScore(xPlus, yMinus);

                        StringBuilder builder = new StringBuilder();

                        builder.append("\nWeak ").append(y).append("->").append(x).append(" ").append(score);
                        builder.append("\n   Parents(").append(x).append(") = ").append(condxMinus);
                        builder.append("\n   Parents(").append(y).append(") = ").append(condyMinus);

                        scoreReports.put(-score, builder.toString());

                        if (score > max) {
                            max = score;
                            left = true;
                            right = false;
                        }
                    } else if ((xPlus <= yPlus + delta && yMinus <= xMinus + delta) || forbiddenLeft) {
                        double score = combinedScore(yPlus, xMinus);

                        StringBuilder builder = new StringBuilder();

                        builder.append("\nWeak ").append(x).append("->").append(y).append(" ").append(score);
                        builder.append("\n   Parents(").append(x).append(") = ").append(condxMinus);
                        builder.append("\n   Parents(").append(y).append(") = ").append(condyMinus);

                        scoreReports.put(-score, builder.toString());

                        if (score > max) {
                            max = score;
                            left = false;
                            right = true;
                        }
                    } else if (yPlus <= xPlus + delta && yMinus <= xMinus + delta) {
                        double score = combinedScore(yPlus, xMinus);

                        StringBuilder builder = new StringBuilder();

                        builder.append("\nNo directed edge ").append(x).append("--").append(y).append(" ").append(score);
                        builder.append("\n   Parents(").append(x).append(") = ").append(condxMinus);
                        builder.append("\n   Parents(").append(y).append(") = ").append(condyMinus);

                        scoreReports.put(-score, builder.toString());
                    } else if (xPlus <= yPlus + delta && xMinus <= yMinus + delta) {
                        double score = combinedScore(yPlus, xMinus);

                        StringBuilder builder = new StringBuilder();

                        builder.append("\nNo directed edge ").append(x).append("--").append(y).append(" ").append(score);
                        builder.append("\n   Parents(").append(x).append(") = ").append(condxMinus);
                        builder.append("\n   Parents(").append(y).append(") = ").append(condyMinus);

                        scoreReports.put(-score, builder.toString());
                    }
                }
            }
        }

        for (double score : scoreReports.keySet()) {
            TetradLogger.getInstance().log("info", scoreReports.get(score));
        }

        graph.removeEdges(x, y);

        if (left) {
            graph.addDirectedEdge(y, x);
        }

        if (right) {
            graph.addDirectedEdge(x, y);
        }

        if (!graph.isAdjacentTo(x, y)) {
            graph.addUndirectedEdge(x, y);
        }
    }


    private Graph ruleR3(Graph graph) {
        List<DataSet> standardized = DataUtils.standardizeData(this.dataSets);
        setDataSets(standardized);

        Set<Edge> edgeList1 = graph.getEdges();

        for (Edge adj : edgeList1) {
            Node x = adj.getNode1();
            Node y = adj.getNode2();

            resolveOneEdgeMaxR3(graph, x, y);
        }

        return graph;

    }

    private void resolveOneEdgeMaxR3(Graph graph, Node x, Node y) {
        System.out.println("Resolving " + x + " === " + y);

        TetradLogger.getInstance().log("info", "\nEDGE " + x + " --- " + y);

        List<Node> condxMinus = Collections.emptyList();
        List<Node> condxPlus = Collections.singletonList(y);
        List<Node> condyMinus = Collections.emptyList();
        List<Node> condyPlus = Collections.singletonList(x);

        double px = pValue(x, condxMinus);
        double py = pValue(y, condyMinus);

        System.out.println("px = " + px + " py = " + py);

        if (px > alpha || py > alpha) {
            return;
        }

        double xPlus = score(x, condxPlus);
        double xMinus = score(x, condxMinus);

        double yPlus = score(y, condyPlus);
        double yMinus = score(y, condyMinus);

        double xMax = xPlus > xMinus ? xPlus : xMinus;
        double yMax = yPlus > yMinus ? yPlus : yMinus;

        double score = combinedScore(xMax, yMax);
        TetradLogger.getInstance().log("info", "Score = " + score);

        double deltaX = xPlus - xMinus;
        double deltaY = yPlus - yMinus;

        double epsilon = 0;
        graph.removeEdges(x, y);

        if (deltaX < deltaY - epsilon) {
            graph.addDirectedEdge(x, y);
        } else if (deltaX > deltaY + epsilon) {
            graph.addDirectedEdge(y, x);
        } else {
            graph.addUndirectedEdge(x, y);
        }
    }

    public Graph ruleR4(Graph graph) {
        List<Node> nodes = dataSets.get(0).getVariables();
        graph = GraphUtils.replaceNodes(graph, nodes);

        // For each row, list the columns of W in that row that are parameters. Note that the diagonal
        // is fixed to 1, so diagonal elements aren't parameters.
        List<List<Integer>> rows = new ArrayList<List<Integer>>();
        List<List<List<Double>>> paramsforDataSets = new ArrayList<List<List<Double>>>();
        List<List<Double>> avgParams = new ArrayList<List<Double>>();

        for (int k = 0; k < nodes.size(); k++) {
            List<Node> adj = graph.getAdjacentNodes(nodes.get(k));
            List<Integer> row = new ArrayList<Integer>();
            List<Double> avgParam = new ArrayList<Double>();
            List<Node> nodesInRow = new ArrayList<Node>();

            for (Node node : adj) {
                if (knowledge.isForbidden(node.getName(), nodes.get(k).getName())) {
                    continue;
                }

                int j = nodes.indexOf(node);
                row.add(j);
                avgParam.add(0.0);
                nodesInRow.add(node);
            }

            for (Node node : nodes) {
                if (knowledge.isRequired(node.getName(), nodes.get(k).getName())) {
                    if (!nodesInRow.contains(node)) {
                        int j = nodes.indexOf(node);
                        row.add(j);
                        avgParam.add(0.0);
                        nodesInRow.add(node);
                    }
                }
            }

//            row.add(k);
//            avgParam.add(0.0);

            rows.add(row);
            avgParams.add(avgParam);
        }

        // Estimate parameters for each data set.
        for (int i = 0; i < dataSets.size(); i++) {
            TetradMatrix data = dataSets.get(i).getDoubleData();
            List<List<Double>> parameters = new ArrayList<List<Double>>();

            // Note that the 1's along the diagonal of W are hard coded into the code for calculating scores.
            // Otherwise list doubles to correspond to each parameter.
            for (int k = 0; k < nodes.size(); k++) {
                List<Double> params = new ArrayList<Double>();

                for (int j : rows.get(k)) {
                    params.add(0.0);
                }

                parameters.add(params);
            }

            double range = zeta;

            // Estimate the parameters.
            optimizeAllRows(data, range, rows, parameters);

            // Print out the estimated coefficients (I - W) for off-diagonals.
            printEstimatedCoefs(nodes, rows, parameters);

            // Remember the parameters for this dataset.
            paramsforDataSets.add(parameters);
        }

        // Note that the average of each parameter across data sets will be used. If there is just one
        // data set, this is just the parameter.
        if (!isOrientStrongerDirection()) {
            Graph _graph = new EdgeListGraph(nodes);

            for (int i = 0; i < rows.size(); i++) {
                for (int _j = 0; _j < rows.get(i).size(); _j++) {
                    double param = avg(paramsforDataSets, i, _j);
                    avgParams.get(i).set(_j, param);
                    int j = rows.get(i).get(_j);

                    if (i == j) continue;

                    Node node1 = nodes.get(j);
                    Node node2 = nodes.get(i);

                    Edge edge1 = Edges.directedEdge(node1, node2);

                    if (Math.abs(param) >= epsilon) {
                        _graph.addEdge(edge1);
                    }
                }
            }

            return _graph;
        } else {
            Graph _graph = new EdgeListGraph(nodes);

            for (int i = 0; i < rows.size(); i++) {
                for (int _j = 0; _j < rows.get(i).size(); _j++) {
                    int j = rows.get(i).get(_j);

                    if (j > i) continue;

                    double param1 = avg(paramsforDataSets, i, _j);
                    avgParams.get(i).set(_j, param1);

                    double param2 = 0.0;

                    for (int _i = 0; _i < rows.get(j).size(); _i++) {

                        int i2 = rows.get(j).get(_i);

                        if (i2 == i) {
                            param2 = avg(paramsforDataSets, j, _i);
                            avgParams.get(j).set(_i, param2);
                            break;
                        }
                    }

                    if (param2 == 0) {
                        throw new IllegalArgumentException();
                    }

                    if (i == j) continue;

                    Node node1 = nodes.get(j);
                    Node node2 = nodes.get(i);
                    Edge edge1 = Edges.directedEdge(node1, node2);
                    Edge edge2 = Edges.directedEdge(node2, node1);

                    if (Math.abs(param1) > Math.abs(param2)) {
                        _graph.addEdge(edge1);
                    } else if (Math.abs(param1) < Math.abs(param2)) {
                        _graph.addEdge(edge2);
                    } else {
                        _graph.addUndirectedEdge(node1, node2);
                    }
                }
            }

            // Print the average coefficients.
            printEstimatedCoefs(nodes, rows, avgParams);

            return _graph;
        }
    }

    private void printEstimatedCoefs(List<Node> nodes, List<List<Integer>> rows, List<List<Double>> parameters) {
        NumberFormat nf = new DecimalFormat("0.0000");

        for (int g = 0; g < rows.size(); g++) {
            System.out.print(nodes.get(g) + "\t");
            for (int h = 0; h < rows.get(g).size(); h++) {
                System.out.print(nodes.get(rows.get(g).get(h)) + ":" + nf.format(-parameters.get(g).get(h)) + "\t");
            }

            System.out.println();
        }
    }

    private double avg(List<List<List<Double>>> paramsforDataSets, int i, int j) {

        // The average of non-missing coefficients.
        double sum = 0.0;
        int count = 0;

        for (List<List<Double>> params : paramsforDataSets) {
            Double coef = params.get(i).get(j);

            if (!Double.isNaN(coef)) {
                sum += coef;
                count++;
            }
        }

        return sum / count;
    }

    private void optimizeAllRows(TetradMatrix data, final double range,
                                 List<List<Integer>> rows, List<List<Double>> parameters) {

        for (int i = 0; i < rows.size(); i++) {
            System.out.println("Optimizing row = " + i);

            try {
                optimizeRow(i, data, range, rows, parameters);
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }
    }

    private void optimizeRow(final int rowIndex, final TetradMatrix data,
                             final double range, final List<List<Integer>> rows,
                             final List<List<Double>> parameters) {
        System.out.println("A");

        final int numParams = rows.get(rowIndex).size();

        final double[] dLeftMin = new double[numParams];
        final double[] dRightMin = new double[numParams];

        double[] values = new double[numParams];
        double delta = 0.1;

        if (false) { //isEdgeCorrected()) {
            double min = -2;
            double max = 2;

            int[] dims = new int[values.length];

            int numBins = 5;
            for (int i = 0; i < values.length; i++) dims[i] = numBins;

            CombinationGenerator gen = new CombinationGenerator(dims);
            int[] comb;
            List<Double> maxParams = new ArrayList<Double>();

            for (int i = 0; i < values.length; i++) maxParams.add(0.0);

            double maxV = Double.NEGATIVE_INFINITY;

            while ((comb = gen.next()) != null) {
                List<Double> params = new ArrayList<Double>();

                for (int i = 0; i < values.length; i++) {
                    params.add(min + (max - min) * (comb[i] / (double) numBins));
                }

                parameters.set(rowIndex, params);

                double v = scoreRow(rowIndex, data, rows, parameters);

                if (v > maxV) {
                    maxV = v;
                    maxParams = params;
                }
            }

            System.out.println("maxparams = " + maxParams);

            parameters.set(rowIndex, maxParams);

            for (int i = 0; i < values.length; i++) {
                dLeftMin[i] = -range;
                dRightMin[i] = range;
                values[i] = maxParams.get(i);
            }
        } else if (false) {
            for (int i = 0; i < numParams; i++) {
                parameters.get(rowIndex).set(i, -range);
                double vLeft = scoreRow(rowIndex, data, rows, parameters);
                double dLeft = -range;

                // Search from the left for the first valley; mark that as dleft.
                for (double d = -range + delta; d < range; d += delta) {
                    parameters.get(rowIndex).set(i, d);
                    double v = scoreRow(rowIndex, data, rows, parameters);
                    if (Double.isNaN(v)) continue;
                    if (v > vLeft) break;
                    vLeft = v;
                    dLeft = d;
                }

                parameters.get(rowIndex).set(i, range);
                double vRight = scoreRow(rowIndex, data, rows, parameters);
                double dRight = range;

                // Similarly for dright. Will take dleft and dright to be bounds for the parameter,
                // to avoid high scores at the boundaries.
                for (double d = range - delta; d > -range; d -= delta) {
                    parameters.get(rowIndex).set(i, d);
                    double v = scoreRow(rowIndex, data, rows, parameters);
                    if (Double.isNaN(v)) continue;
                    if (v > vRight) break;
                    vRight = v;
                    dRight = d;
                }

                // If dleft dright ended up reversed, re-reverse them.
                if (dLeft > dRight) {
                    double temp = dRight;
                    dLeft = dRight;
                    dRight = temp;
                }

                System.out.println("dLeft = " + dLeft + " dRight = " + dRight);

                dLeftMin[i] = dLeft;
                dRightMin[i] = dRight;

                values[i] = (dLeft + dRight) / 2.0;
            }
        } else {

            System.out.println("B");

            // Default case: search for the maximum score over the entire range.
            for (int i = 0; i < numParams; i++) {
                dLeftMin[i] = -range;
                dRightMin[i] = range;

                values[i] = 0;
            }
        }

        MultivariateFunction function = new MultivariateFunction() {
            public double value(double[] values) {
                System.out.println(Arrays.toString(values));

                for (int i = 0; i < values.length; i++) {
                    parameters.get(rowIndex).set(i, values[i]);
                }

                double v = scoreRow(rowIndex, data, rows, parameters);

                if (Double.isNaN(v)) {
                    return Double.POSITIVE_INFINITY; // was 10000
                }

                return -v;
            }
        };

        try {
            MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);

            PointValuePair pair = search.optimize(
                    new InitialGuess(values),
                    new ObjectiveFunction(function),
                    GoalType.MINIMIZE,
                    new MaxEval(100000));

            values = pair.getPoint();
        } catch (Exception e) {
            e.printStackTrace();

            for (int i = 0; i < values.length; i++) {
                parameters.get(rowIndex).set(i, Double.NaN);
            }
        }
    }

    private double[] col;

    // rowIndex is for the W matrix, not for the data.
    public double scoreRow(int rowIndex, TetradMatrix data, List<List<Integer>> rows, List<List<Double>> parameters) {
        if (col == null) {
            col = new double[data.rows()];
        }

        List<Integer> cols = rows.get(rowIndex);

        for (int i = 0; i < data.rows(); i++) {
            double d = 0.0;

            for (int j = 0; j < cols.size(); j++) {
                int _j = cols.get(j);
                double coef = parameters.get(rowIndex).get(j);
                double value = data.get(i, _j);
                d += coef * value;
            }

            // Add in the diagonal, assumed to consist entirely of 1's, indicating no self loop.
            d += (1.0 - getSelfLoopStrength()) * data.get(i, rowIndex);

            col[i] = d;
        }

        return score(col);
    }

    public double rowPValue(int rowIndex, TetradMatrix data, List<List<Integer>> rows, List<List<Double>> parameters) {
        if (col == null) {
            col = new double[data.rows()];
        }

        List<Integer> cols = rows.get(rowIndex);

        for (int i = 0; i < data.rows(); i++) {
            double d = 0.0;

            for (int j = 0; j < cols.size(); j++) {
                int _j = cols.get(j);
                double coef = parameters.get(rowIndex).get(j);
                double value = data.get(i, _j);
                d += coef * value;
            }

            // Add in the diagonal, assumed to consist entirely of 1's.
            d += 1.0 * data.get(i, rowIndex);

            col[i] = d;
        }

        return aSquaredP(col);
    }

    private Graph entropyBased(Graph graph) {
        DataSet dataSet = DataUtils.concatenateData(dataSets);
        dataSet = DataUtils.standardizeData(dataSet);
        Graph _graph = new EdgeListGraph(graph.getNodes());

        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            Node _x = dataSet.getVariable(x.getName());
            Node _y = dataSet.getVariable(y.getName());

            List<double[]> ret = extractData(dataSet, _x, _y);

            double[] xData = ret.get(0);
            double[] yData = ret.get(1);

            double[] d = new double[xData.length];
            double[] e = new double[xData.length];

            double cov = StatUtils.covariance(xData, yData);

            for (int i = 0; i < xData.length; i++) {
                d[i] = yData[i] - cov * xData[i];  // y regressed on x
                e[i] = xData[i] - cov * yData[i];  // x regressed on y
            }

            double R = -StatUtils.maxEntApprox(xData) - StatUtils.maxEntApprox(d)
                    + StatUtils.maxEntApprox(yData) + StatUtils.maxEntApprox(e);

            if (R > 0) {
                _graph.addDirectedEdge(x, y);
            } else {
                _graph.addDirectedEdge(y, x);
            }
        }

        return _graph;
    }

    private Graph tanhGraph(Graph graph) {
        DataSet dataSet = DataUtils.concatenateData(dataSets);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());
        dataSet = DataUtils.standardizeData(dataSet);
        double[][] data = dataSet.getDoubleData().transpose().toArray();
        Graph _graph = new EdgeListGraph(graph.getNodes());
        List<Node> nodes = dataSet.getVariables();
        Map<Node, Integer> nodesHash = new HashMap<Node, Integer>();

        for (int i = 0; i < nodes.size(); i++) {
            nodesHash.put(nodes.get(i), i);
        }

        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            double sumX = 0.0;
            int countX = 0;

            double[] xData = data[nodesHash.get(edge.getNode1())];
            double[] yData = data[nodesHash.get(edge.getNode2())];

            for (int i = 0; i < xData.length; i++) {
                double x0 = xData[i];
                double y0 = yData[i];

                double termX = (x0 * Math.tanh(y0) - Math.tanh(x0) * y0);

                sumX += termX;
                countX++;
            }

            double R = sumX / countX;

            double rhoX = regressionCoef(xData, yData);
            R *= rhoX;

            if (R > 0) {
                _graph.addDirectedEdge(x, y);
            } else {
                _graph.addDirectedEdge(y, x);
            }
        }

        return _graph;
    }


    // @param empirical True if the skew signs are estimated empirically.
    private Graph skewGraph(Graph graph, boolean empirical) {
        DataSet dataSet = DataUtils.concatenateData(dataSets);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());
        dataSet = DataUtils.standardizeData(dataSet);
        double[][] data = dataSet.getDoubleData().transpose().toArray();
        Graph _graph = new EdgeListGraph(graph.getNodes());
        List<Node> nodes = dataSet.getVariables();
        Map<Node, Integer> nodesHash = new HashMap<Node, Integer>();

        for (int i = 0; i < nodes.size(); i++) {
            nodesHash.put(nodes.get(i), i);
        }

        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            double sumX = 0.0;
            int countX = 0;

            int _i = nodesHash.get(edge.getNode1());
            int _j = nodesHash.get(edge.getNode2());

            double[] xData = data[_i];
            double[] yData = data[_j];

            if (empirical) {
                xData = correctSkews(xData);
                yData = correctSkews(yData);
            }

            for (int i = 0; i < xData.length; i++) {
                double x0 = xData[i];
                double y0 = yData[i];

                double termX = x0 * x0 * y0 - x0 * y0 * y0;

                sumX += termX;
                countX++;
            }

            double R = sumX / countX;

            double rhoX = regressionCoef(xData, yData);

            R *= rhoX;

            if (R > 0) {
                _graph.addDirectedEdge(x, y);
            } else {
                _graph.addDirectedEdge(y, x);
            }
        }

        return _graph;

    }

    // @param empirical True if the skew signs are estimated empirically.
    private Graph robustSkewGraph(Graph graph, boolean empirical) {
        DataSet dataSet = DataUtils.concatenateData(dataSets);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());
        dataSet = DataUtils.standardizeData(dataSet);
        double[][] data = dataSet.getDoubleData().transpose().toArray();
        Graph _graph = new EdgeListGraph(graph.getNodes());
        List<Node> nodes = dataSet.getVariables();
        Map<Node, Integer> nodesHash = new HashMap<Node, Integer>();

        for (int i = 0; i < nodes.size(); i++) {
            nodesHash.put(nodes.get(i), i);
        }

        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            double sumX = 0.0;
            int countX = 0;

            double[] xData = data[nodesHash.get(edge.getNode1())];
            double[] yData = data[nodesHash.get(edge.getNode2())];

            if (empirical) {
                xData = correctSkews(xData);
                yData = correctSkews(yData);
            }

            for (int i = 0; i < xData.length; i++) {
                double x0 = xData[i];
                double y0 = yData[i];

                double termX = g(x0) * y0 - x0 * g(y0);

                sumX += termX;
                countX++;
            }

            double R = sumX / countX;

            double rhoX = regressionCoef(xData, yData);
            R *= rhoX;

            if (R > 0) {
                _graph.addDirectedEdge(x, y);
            } else if (R < 0) {
                _graph.addDirectedEdge(y, x);
            } else {
                _graph.addUndirectedEdge(x, y);
            }
        }

        return _graph;
    }

    private double g(double x) {
        return Math.log(Math.cosh(Math.max(x, 0)));
    }

    // cutoff is NaN if no thresholding is to be done, otherwise a threshold between 0 and 1.
    private Graph patelTauOrientation(Graph graph, double cutoff) {
        List<DataSet> centered = DataUtils.center(dataSets);
        DataSet concat = DataUtils.concatenateData(centered);
        DataSet dataSet = DataUtils.standardizeData(concat);

        Graph _graph = new EdgeListGraph(graph.getNodes());

        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            Node _x = dataSet.getVariable(x.getName());
            Node _y = dataSet.getVariable(y.getName());

            List<double[]> ret = prepareData(dataSet, _x, _y, false, false);
            double[] xData = ret.get(0);
            double[] yData = ret.get(1);

            double R = patelTau(xData, yData, cutoff);

            if (R > 0) {
                _graph.addDirectedEdge(x, y);
            } else {
                _graph.addDirectedEdge(y, x);
            }
        }

        System.out.println(_graph);

        return _graph;
    }

    // cutoff is NaN if no thresholding is to be done, otherwise a threshold between 0 and 1.
    private double patelTau(double[] d1in, double[] d2in, double cutoff) {
        double grotMIN = percentile(d1in, 10);
        double grotMAX = percentile(d1in, 90);

        double XT = .25; // Cancels out, don't know why this is here.

        double[] d1b = new double[d1in.length];

        for (int i = 0; i < d1b.length; i++) {
            double y1 = (d1in[i] - grotMIN) / (grotMAX - grotMIN);
            double y2 = Math.min(y1, 1.0);
            double y3 = Math.max(y2, 0.0);
            d1b[i] = y3;
        }

        if (!Double.isNaN(cutoff)) {
            for (int i = 0; i < d1b.length; i++) {
                if (d1b[i] > cutoff) d1b[i] = 1.0;
                else d1b[i] = 0.0;
            }
        }

        grotMIN = percentile(d2in, 10);
        grotMAX = percentile(d2in, 90);

        double[] d2b = new double[d2in.length];

        for (int i = 0; i < d2b.length; i++) {
            double y1 = (d2in[i] - grotMIN) / (grotMAX - grotMIN);
            double y2 = Math.min(y1, 1.0);
            double y3 = Math.max(y2, 0.0);
            d2b[i] = y3;
        }

        if (!Double.isNaN(cutoff)) {
            for (int i = 0; i < d2b.length; i++) {
                if (d2b[i] > cutoff) d2b[i] = 1.0;
                else d2b[i] = 0.0;
            }
        }

        double theta1 = dotProduct(d1b, d2b) / XT;
        double theta2 = dotProduct(d1b, minus(1, d2b)) / XT;
        double theta3 = dotProduct(d2b, minus(1, d1b)) / XT;
//        double theta4= dotProduct(minus(1, d1b), minus(1, d2b))/XT;

        double tau_12;

        if (theta2 > theta3) tau_12 = 1 - (theta1 + theta3) / (theta1 + theta2);
        else tau_12 = (theta1 + theta2) / (theta1 + theta3) - 1;

        return -tau_12;
    }

    private double dotProduct(double[] x, double[] y) {
        double p = 0.0;

        for (int i = 0; i < x.length; i++) {
            p += x[i] * y[i];
        }

        return p;
    }

    private double[] minus(int s, double[] x) {
        double[] y = new double[x.length];

        for (int i = 0; i < x.length; i++) {
            y[i] = s - x[i];
        }

        return y;
    }

    private double percentile(double[] x, double percent) {
        double[] _x = Arrays.copyOf(x, x.length);
        Arrays.sort(_x);
        return _x[(int) (x.length * (percent / 100.0))];
    }

    private List<double[]> extractData(DataSet data, Node _x, Node _y) {
        int xIndex = data.getColumn(_x);
        int yIndex = data.getColumn(_y);

        double[][] _data = data.getDoubleData().transpose().toArray();

//        double[] xData = _data.viewColumn(xIndex).toArray();
//        double[] yData = _data.viewColumn(yIndex).toArray();

        double[] xData = _data[xIndex];
        double[] yData = _data[yIndex];

        List<Double> xValues = new ArrayList<Double>();
        List<Double> yValues = new ArrayList<Double>();

        for (int i = 0; i < data.getNumRows(); i++) {
            if (!Double.isNaN(xData[i]) && !Double.isNaN(yData[i])) {
                xValues.add(xData[i]);
                yValues.add(yData[i]);
            }
        }

        xData = new double[xValues.size()];
        yData = new double[yValues.size()];

        for (int i = 0; i < xValues.size(); i++) {
            xData[i] = xValues.get(i);
            yData[i] = yValues.get(i);
        }

        List<double[]> ret = new ArrayList<double[]>();
        ret.add(xData);
        ret.add(yData);

        return ret;
    }

    private double[] correctSkews(double[] data) {
        double skewness = StatUtils.skewness(data);
        double[] data2 = new double[data.length];
        for (int i = 0; i < data.length; i++) data2[i] = data[i] * Math.signum(skewness);
        return data2;
    }

    private List<double[]> prepareData(DataSet concatData, Node _x, Node _y, boolean skewCorrection, boolean coefCorrection) {
        int xIndex = concatData.getColumn(_x);
        int yIndex = concatData.getColumn(_y);

        double[] xData = concatData.getDoubleData().getColumn(xIndex).toArray();
        double[] yData = concatData.getDoubleData().getColumn(yIndex).toArray();

        List<Double> xValues = new ArrayList<Double>();
        List<Double> yValues = new ArrayList<Double>();

        for (int i = 0; i < concatData.getNumRows(); i++) {
            if (!Double.isNaN(xData[i]) && !Double.isNaN(yData[i])) {
                xValues.add(xData[i]);
                yValues.add(yData[i]);
            }
        }

        xData = new double[xValues.size()];
        yData = new double[yValues.size()];

        for (int i = 0; i < xValues.size(); i++) {
            xData[i] = xValues.get(i);
            yData[i] = yValues.get(i);
        }

        if (skewCorrection) {
            double xSkew = StatUtils.skewness(xData);
            double ySkew = StatUtils.skewness(yData);

            for (int i = 0; i < xData.length; i++) xData[i] *= Math.signum(xSkew);
            for (int i = 0; i < yData.length; i++) yData[i] *= Math.signum(ySkew);
        }

        if (coefCorrection) {
            double coefX;
            try {
                coefX = regressionCoef(xData, yData);
            } catch (Exception e) {
                coefX = Double.NaN;
            }

            double coefY;

            try {
                coefY = regressionCoef(yData, xData);
            } catch (Exception e) {
                coefY = Double.NaN;
            }

            for (int i = 0; i < xData.length; i++) xData[i] *= Math.signum(coefX);
            for (int i = 0; i < yData.length; i++) yData[i] *= Math.signum(coefY);

        }

        List<double[]> ret = new ArrayList<double[]>();
        ret.add(xData);
        ret.add(yData);

        return ret;
    }

    private double regressionCoef(double[] xValues, double[] yValues) {
        List<Node> v = new ArrayList<Node>();
        v.add(new GraphNode("x"));
        v.add(new GraphNode("y"));

        TetradMatrix bothData = new TetradMatrix(xValues.length, 2);

        for (int i = 0; i < xValues.length; i++) {
            bothData.set(i, 0, xValues[i]);
            bothData.set(i, 1, yValues[i]);
        }

        Regression regression2 = new RegressionDataset(bothData, v);

        RegressionResult result;
        try {
            result = regression2.regress(v.get(0), v.get(1));
        } catch (Exception e) {
            return Double.NaN;
        }
        return result.getCoef()[1];
    }

    private boolean isTwoCycle(Graph graph, Node x, Node y) {
        List<Edge> edges = graph.getEdges(x, y);
        return edges.size() == 2;
    }

    private boolean isUndirected(Graph graph, Node x, Node y) {
        List<Edge> edges = graph.getEdges(x, y);
        if (edges.size() == 1) {
            Edge edge = graph.getEdge(x, y);
            return Edges.isUndirectedEdge(edge);
        }

        return false;
    }

    public void setEpsilon(double epsilon) {
        this.epsilon = epsilon;
    }

    public void setZeta(double zeta) {
        this.zeta = zeta;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    private double aSquared(double[] x) {
        return new AndersonDarlingTest(x).getASquaredStar();
    }

    private double aSquaredP(double[] x) {
        return new AndersonDarlingTest(x).getP();
    }

    /**
     * @return the probability associated with the most recently computed independence test.
     */
    public double getPValue(double fisherZ) {
        return 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, Math.abs(fisherZ)));
    }

    private static class Pair {
        int index;
        double value;

        public Pair(int index, double value) {
            this.index = index;
            this.value = value;
        }

        public String toString() {
            return "<" + index + ", " + value + ">";
        }
    }

    private Graph igci(Graph graph) {
        if (dataSets.size() > 1) throw new IllegalArgumentException("Expecting exactly one data set for IGCI.");

        DataSet dataSet = dataSets.get(0);
        TetradMatrix matrix = dataSet.getDoubleData();

        Graph _graph = new EdgeListGraph(graph.getNodes());

        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            Node _x = dataSet.getVariable(x.getName());
            Node _y = dataSet.getVariable(y.getName());

            int xIndex = dataSet.getVariables().indexOf(_x);
            int yIndex = dataSet.getVariables().indexOf(_y);

            double[] xCol = matrix.getColumn(xIndex).toArray();
            double[] yCol = matrix.getColumn(yIndex).toArray();

            double f = igci(xCol, yCol, 2, 1);

            System.out.println(x + "===" + y + " f = " + f);

            graph.removeEdges(x, y);

            if (f < -epsilon) {
                _graph.addDirectedEdge(x, y);
                System.out.println("Orienting using IGCI: " + graph.getEdge(x, y));
            } else if (f > epsilon) {
                _graph.addDirectedEdge(y, x);
                System.out.println("Orienting using IGCI: " + graph.getEdge(x, y));
            } else {
                if (resolveOneEdgeMaxR3(xCol, yCol) < 0) {
                    _graph.addDirectedEdge(x, y);
                } else {
                    _graph.addDirectedEdge(y, x);
                }

                System.out.println("Orienting using non-Gaussianity: " + graph.getEdge(x, y));
            }

        }

        return _graph;
    }

    private double igci(double[] x, double[] y, int refMeasure, int estimator) {
        int m = x.length;

        if (m != y.length) {
            throw new IllegalArgumentException("Vectors must be the same length.");
        }

        switch (refMeasure) {
            case 1:
                // uniform reference measure

                double minx = min(x);
                double maxx = max(x);
                double miny = min(y);
                double maxy = max(y);

                for (int i = 0; i < x.length; i++) {
                    x[i] = (x[i] - minx) / (maxx - minx);
                    y[i] = (y[i] - miny) / (maxy - miny);
                }

                break;

            case 2:
                double meanx = StatUtils.mean(x);
                double stdx = StatUtils.sd(x);
                double meany = StatUtils.mean(y);
                double stdy = StatUtils.sd(y);

                // Gaussian reference measure
                for (int i = 0; i < x.length; i++) {
                    x[i] = (x[i] - meanx) / stdx;
                    y[i] = (y[i] - meany) / stdy;
                }

                break;

            default:
                throw new IllegalArgumentException("Warning: unknown reference measure - no scaling applied.");
        }


        double f;

        if (estimator == 1) {
            // difference of entropies

            double[] x1 = Arrays.copyOf(x, x.length);
            Arrays.sort(x1);

            x1 = removeNaN(x1);

            double[] y1 = Arrays.copyOf(y, y.length);
            Arrays.sort(y1);

            y1 = removeNaN(y1);

            int n1 = x1.length;
            double hx = 0.0;
            for (int i = 0; i < n1 - 1; i++) {
                double delta = x1[i + 1] - x1[i];
                if (delta != 0) {
                    hx = hx + log(abs(delta));
                }
            }

            hx = hx / (n1 - 1) + psi(n1) - psi(1);

            int n2 = y1.length;
            double hy = 0.0;
            for (int i = 0; i < n2 - 1; i++) {
                double delta = y1[i + 1] - y1[i];

                if (delta != 0) {
                    if (Double.isNaN(delta)) {
                        throw new IllegalArgumentException();
                    }

                    hy = hy + log(abs(delta));
                }
            }

            hy = hy / (n2 - 1) + psi(n2) - psi(1);

            f = hy - hx;
        } else if (estimator == 2) {
            // integral-approximation based estimator
            double a = 0;
            double b = 0;

            List<Pair> _x = new ArrayList<Pair>();

            for (int i = 0; i < x.length; i++) {
                _x.add(new Pair(i, x[i]));
            }

            Collections.sort(_x, new Comparator<Pair>() {
                public int compare(Pair pair1, Pair pair2) {
                    return new Double(pair1.value).compareTo(pair2.value);
                }
            });

            List<Pair> _y = new ArrayList<Pair>();

            for (int i = 0; i < y.length; i++) {
                _y.add(new Pair(i, y[i]));
            }

            Collections.sort(_y, new Comparator<Pair>() {
                public int compare(Pair pair1, Pair pair2) {
                    return new Double(pair1.value).compareTo(pair2.value);
                }
            });

            for (int i = 0; i < m - 1; i++) {
                double X1 = x[_x.get(i).index];
                double X2 = x[_x.get(i + 1).index];
                double Y1 = y[_x.get(i).index];
                double Y2 = y[_x.get(i + 1).index];

                if (X2 != X1 && Y2 != Y1) {
                    a = a + log(abs((Y2 - Y1) / (X2 - X1)));
                }

                X1 = x[_y.get(i).index];
                X2 = x[_y.get(i + 1).index];
                Y1 = y[_y.get(i).index];
                Y2 = y[_y.get(i + 1).index];

                if (Y2 != Y1 && X2 != X1) {
                    b = b + log(abs((X2 - X1) / (Y2 - Y1)));
                }
            }

            f = (a - b) / m;
        } else if (estimator == 3) {
            // integral-approximation based estimator
            double a = 0;

            x = Arrays.copyOf(x, x.length);
            y = Arrays.copyOf(y, y.length);

            Arrays.sort(x);
            Arrays.sort(y);

            for (int i = 0; i < m - 1; i++) {
                double X1 = x[i];
                double X2 = x[i + 1];
                double Y1 = y[i];
                double Y2 = y[i + 1];

                if (X2 != X1 && Y2 != Y1) {
                    a = a + log((Y2 - Y1) / (X2 - X1));
                }
            }

            f = a / m;
        } else if (estimator == 4) {
            // integral-approximation based estimator
            double a = 0;
            double b = 0;

            x = Arrays.copyOf(x, x.length);
            y = Arrays.copyOf(y, y.length);

            Arrays.sort(x);
            Arrays.sort(y);

            for (int i = 0; i < m - 1; i++) {
                double X1 = x[i];
                double X2 = x[i + 1];
                double Y1 = y[i];
                double Y2 = y[i + 1];

                if (X2 != X1 && Y2 != Y1) {
                    a = a + log((Y2 - Y1) / (X2 - X1));
                }

                if (Y2 != Y1 && X2 != X1) {
                    b = b + log((X2 - X1) / (Y2 - Y1));
                }
            }

            f = (a - b) / m;
        } else if (estimator == 5) {
            double a = 0;

            x = Arrays.copyOf(x, x.length);
            y = Arrays.copyOf(y, y.length);

            Arrays.sort(x);
            Arrays.sort(y);

            for (int i = 0; i < m - 1; i++) {
                double X1 = x[i];
                double X2 = x[i + 1];
                double Y1 = y[i] - y[0];
                double Y2 = y[i + 1] - y[0];

                if (X2 != X1 && Y2 != Y1) {
                    a += ((Y2 + Y1) / 2.0) * (X2 - X1);
                }
            }

            a -= ((y[m - 1] - y[0]) * (x[m - 1] - x[0])) / 2.0;


            System.out.println("a = " + a);
//            f = b-a;
            f = a;

        } else {
            throw new IllegalArgumentException("Estimator must be 1 or 2: " + estimator);
        }

        return f;

    }

    private double[] removeNaN(double[] data) {
        List<Double> _leaveOutMissing = new ArrayList<Double>();

        for (int i = 0; i < data.length; i++) {
            if (!Double.isNaN(data[i])) {
                _leaveOutMissing.add(data[i]);
            }
        }

        double[] _data = new double[_leaveOutMissing.size()];

        for (int i = 0; i < _leaveOutMissing.size(); i++) _data[i] = _leaveOutMissing.get(i);

        return _data;
    }


    // digamma

    double psi(double x) {
        double result = 0, xx, xx2, xx4;
        assert (x > 0);
        for (; x < 7; ++x)
            result -= 1 / x;
        x -= 1.0 / 2.0;
        xx = 1.0 / x;
        xx2 = xx * xx;
        xx4 = xx2 * xx2;
        result += Math.log(x) + (1. / 24.) * xx2 - (7.0 / 960.0) * xx4 + (31.0 / 8064.0) * xx4 * xx2 - (127.0 / 30720.0) * xx4 * xx4;
        return result;
    }

    private double min(double[] x) {
        double min = Double.POSITIVE_INFINITY;

        for (double _x : x) {
            if (_x < min) min = _x;
        }

        return min;
    }

    private double max(double[] x) {
        double max = Double.NEGATIVE_INFINITY;

        for (double _x : x) {
            if (_x > max) max = _x;
        }

        return max;
    }


    private double combinedScore(double score1, double score2) {
        return score1 + score2;
    }

    private double score(Node y, List<Node> parents) {
        if (score == Lofs.Score.andersonDarling) {
            return andersonDarlingPASquareStar(y, parents);
        } else if (score == Lofs.Score.kurtosis) {
            return Math.abs(StatUtils.kurtosis(residuals(y, parents, true, true)));
        } else if (score == Lofs.Score.entropy) {
            return entropy(y, parents);
        } else if (score == Lofs.Score.skew) {
            return Math.abs(StatUtils.skewness(residuals(y, parents, true, true)));
        } else if (score == Lofs.Score.fifthMoment) {
            return Math.abs(StatUtils.standardizedFifthMoment(residuals(y, parents, true, true)));
        } else if (score == Lofs.Score.absoluteValue) {
            return meanAbsolute(y, parents);
        } else if (score == Lofs.Score.exp) {
            return expScoreUnstandardized(y, parents);
        } else if (score == Lofs.Score.other) {
            double[] _f = residuals(y, parents, true, true);
            return score(_f);
        } else if (score == Lofs.Score.logcosh) {
            return logCoshScore(y, parents);
        }

        throw new IllegalStateException();
    }

    private double score(double[] col) {
        if (score == Lofs.Score.andersonDarling) {
            return new AndersonDarlingTest(col).getASquaredStar();
        } else if (score == Lofs.Score.entropy) {
            return StatUtils.maxEntApprox(col);
        } else if (score == Lofs.Score.kurtosis) {
            col = DataUtils.standardizeData(col);
            return -Math.abs(StatUtils.kurtosis(col));
        } else if (score == Lofs.Score.skew) {
            return Math.abs(StatUtils.skewness(col));
        } else if (score == Lofs.Score.fifthMoment) {
            return Math.abs(StatUtils.standardizedFifthMoment(col));
        } else if (score == Lofs.Score.absoluteValue) {
            return StatUtils.meanAbsolute(col);
        } else if (score == Lofs.Score.exp) {
            return expScoreUnstandardized(col);
        } else if (score == Lofs.Score.other) {
            return Math.abs(StatUtils.kurtosis(col));
        } else if (score == Lofs.Score.logcosh) {
            return StatUtils.logCoshScore(col);
        }

        throw new IllegalStateException("Unrecognized score: " + score);
    }

    //=============================PRIVATE METHODS=========================//

    private double meanAbsolute(Node node, List<Node> parents) {
        double[] _f = residuals(node, parents, false, true);

        return StatUtils.meanAbsolute(_f);
    }

    private double expScoreUnstandardized(Node node, List<Node> parents) {
        double[] _f = residuals(node, parents, false, true);

        return expScoreUnstandardized(_f);
    }

    private double expScoreUnstandardized(double[] _f) {
        double sum = 0.0;

        for (int k = 0; k < _f.length; k++) {
            sum += Math.exp(_f[k]);
        }

        double expected = sum / _f.length;
        return -Math.abs(Math.log(expected));
    }

    private double logCoshScore(Node node, List<Node> parents) {
        double[] _f = residuals(node, parents, true, true);
        return StatUtils.logCoshScore(_f);
    }

    private double[] residuals(Node node, List<Node> parents, boolean standardize, boolean removeNaN) {
        List<Double> _residuals = new ArrayList<Double>();

        Node target = getVariable(variables, node.getName());
        List<Node> regressors = new ArrayList<Node>();

        for (Node _regressor : parents) {
            Node variable = getVariable(variables, _regressor.getName());
            regressors.add(variable);
        }

        DATASET:
        for (int m = 0; m < dataSets.size(); m++) {
            DataSet dataSet = dataSets.get(m);

            int targetCol = dataSet.getColumn(target);

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                if (Double.isNaN(dataSet.getDouble(i, targetCol))) {
                    continue DATASET;
                }
            }

            for (Node regressor : regressors) {
                int regressorCol = dataSet.getColumn(regressor);

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    if (Double.isNaN(dataSet.getDouble(i, regressorCol))) {
                        continue DATASET;
                    }
                }
            }

            RegressionResult result = getRegressions().get(m).regress(target, regressors);
            double[] residualsSingleDataset = result.getResiduals().toArray();

            if (result.getCoef().length > 0) {
                double intercept = result.getCoef()[0];

                for (int i2 = 0; i2 < residualsSingleDataset.length; i2++) {
                    residualsSingleDataset[i2] = residualsSingleDataset[i2] + intercept;
                }
            }

            for (double _x : residualsSingleDataset) {
                if (removeNaN && Double.isNaN(_x)) continue;
                _residuals.add(_x);
            }
        }

        double[] _f = new double[_residuals.size()];

        for (int k = 0; k < _residuals.size(); k++) {
            _f[k] = _residuals.get(k);
        }

        if (standardize && removeNaN) {
            _f = DataUtils.standardizeData(_f);
        }

        return _f;
    }

    private double andersonDarlingPASquareStar(Node node, List<Node> parents) {
        double[] _f = residuals(node, parents, true, true);
        return new AndersonDarlingTest(_f).getASquaredStar();
    }

    private double entropy(Node node, List<Node> parents) {
        double[] _f = residuals(node, parents, true, true);
        return StatUtils.maxEntApprox(_f);
    }

    private double pValue(Node node, List<Node> parents) {
        List<Double> _residuals = new ArrayList<Double>();

        Node target = getVariable(variables, node.getName());
        List<Node> regressors = new ArrayList<Node>();

        for (Node _regressor : parents) {
            Node variable = getVariable(variables, _regressor.getName());
            regressors.add(variable);
        }

        DATASET:
        for (int m = 0; m < dataSets.size(); m++) {
            DataSet dataSet = dataSets.get(m);

            int targetCol = dataSet.getColumn(target);

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                if (Double.isNaN(dataSet.getDouble(i, targetCol))) {
                    continue DATASET;
                }
            }

            for (int g = 0; g < regressors.size(); g++) {
                int regressorCol = dataSet.getColumn(regressors.get(g));

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    if (Double.isNaN(dataSet.getDouble(i, regressorCol))) {
                        continue DATASET;
                    }
                }
            }

            RegressionResult result = getRegressions().get(m).regress(target, regressors);
            TetradVector residualsSingleDataset = result.getResiduals();

            for (int h = 0; h < residualsSingleDataset.size(); h++) {
                if (Double.isNaN(residualsSingleDataset.get(h))) {
                    continue DATASET;
                }
            }

            TetradVector _residualsSingleDataset = new TetradVector(residualsSingleDataset.toArray());

            for (int k = 0; k < _residualsSingleDataset.size(); k++) {
                _residuals.add(_residualsSingleDataset.get(k));
            }
        }

        double[] _f = new double[_residuals.size()];

        for (int k = 0; k < _residuals.size(); k++) {
            _f[k] = _residuals.get(k);
        }

        double p = new AndersonDarlingTest(_f).getP();

        if (p > 1.0 || Double.isNaN(p)) p = 1.0;

        return p;
    }

    private Graph getPattern() {
        return pattern;
    }

    private Node getVariable(List<Node> variables, String name) {
        for (Node node : variables) {
            if (name.equals(node.getName())) {
                return node;
            }
        }

        return null;
    }

    private Graph resolveEdgeConditional(Graph graph) {
        setDataSets(dataSets);

        Set<Edge> edgeList1 = graph.getEdges();
//        Collections.shuffle(edgeList1);

        for (Edge adj : edgeList1) {
            Node x = adj.getNode1();
            Node y = adj.getNode2();

            resolveEdgeConditional(graph, x, y);
        }

        return graph;
    }

    TetradMatrix _data = null;

    private void resolveEdgeConditional(Graph graph, Node x, Node y) {
        if (_data == null) {
            _data = DataUtils.centerData(matrices.get(0));
        }
        int xIndex = dataSets.get(0).getColumn(dataSets.get(0).getVariable(x.getName()));
        int yIndex = dataSets.get(0).getColumn(dataSets.get(0).getVariable(y.getName()));
        double[] xCol = _data.getColumn(xIndex).toArray();
        double[] yCol = _data.getColumn(yIndex).toArray();
        int N = xCol.length;

        double[][] yCols = new double[1][N];
        yCols[0] = yCol;

        double[][] xCols = new double[1][N];
        xCols[0] = xCol;

        double[][] empty = new double[0][N];

        double[] resX = conditionalResiduals(xCol, empty);
        double[] resY = conditionalResiduals(yCol, empty);
        double[] resXY = conditionalResiduals(xCol, yCols);
        double[] resYX = conditionalResiduals(yCol, xCols);

        double ngX = new AndersonDarlingTest(xCol).getASquared();
        double ngY = new AndersonDarlingTest(yCol).getASquared();

        graph.removeEdges(x, y);

        double sdX = StatUtils.sd(resX);
        double sdXY = StatUtils.sd(resXY);
        double sdY = StatUtils.sd(resY);
        double sdYX = StatUtils.sd(resYX);

        double abs1 = abs(sdX - sdXY);
        double abs2 = abs(sdY - sdYX);

        if (abs(abs1 - abs2) < epsilon) {
            System.out.println("Orienting by non-Gaussianity " + abs(abs1 - abs2) + " epsilon = " + epsilon);
            System.out.println(x + "===" + y);
            double v = resolveOneEdgeMaxR3b(xCol, yCol);

            if (v < 0) {
                graph.addDirectedEdge(x, y);
            } else if (v > 0) {
                graph.addDirectedEdge(y, x);
            } else {
                graph.addUndirectedEdge(x, y);
            }

            return;
        }

        System.out.println("Orienting by variances " + abs(abs1 - abs2));
        System.out.println(x + "===" + y);

        if (sdXY + ngY < sdYX + ngX) {
            graph.addDirectedEdge(x, y);
        } else {
            graph.addDirectedEdge(y, x);
        }
    }

    private double[] conditionalResiduals(double[] x, double[][] y) {
        int N = x.length;
        double[] residuals = new double[N];

        double _h = 1.0;

        for (int i = 0; i < y.length; i++) {
            _h *= h(y[i]);
        }

        _h = (y.length == 0) ? 1.0 : pow(_h, 1.0 / (y.length));

        for (int i = 0; i < N; i++) {
            double xi = x[i];

            double sum = 0.0;
            double kTot = 0.0;

            for (int j = 0; j < N; j++) {
                double d = distance(y, i, j);
                double k = kernel(d / _h) / _h;
                if (k < 1e-5) continue;
                double xj = x[j];
                sum += k * xj;
                kTot += k;
            }

            residuals[i] = xi - (sum / kTot);
        }

        return residuals;
    }

    private double h(double[] xCol) {
//        % optimal bandwidth suggested by Bowman and Azzalini (1997) p.31 (rks code Matlab)
//        h *= median(abs(x-median(x)))/0.6745*(4/3/r.h)^0.2, geometric mean across variables.

        double[] g = new double[xCol.length];
        double median = median(xCol);
        for (int j = 0; j < xCol.length; j++) g[j] = abs(xCol[j] - median);
        return median(g) / 0.6745 * pow((4.0 / 3.0) / xCol.length, 0.2);
//        return median(g) * pow((4.0 / 3.0) / xCol.length, 0.2);
//        return sd(xCol) * pow((4.0 / 3.0) / xCol.length, 0.2);
    }

    public double kernel(double z) {
        return kernel1(z);
    }

    private double SQRT = sqrt(2. * PI);

    // Gaussian
    public double kernel1(double z) {
        return exp(-(z * z) / 2.) / SQRT; //(sqrt(2. * PI));
    }

    // Uniform
    public double kernel2(double z) {
        if (abs(z) > 1) return 0;
        else return .5;
    }

    // Triangular
    public double kernel3(double z) {
        if (abs(z) > 1) return 0;
        else return 1 - abs(z);
    }

    // Epanechnikov
    public double kernel4(double z) {
        if (abs(z) > 1) return 0;
        else return (3. / 4.) * (1. - z * z);
    }

    // Quartic
    public double kernel5(double z) {
        if (abs(z) > 1) return 0;
        else return 15. / 16. * pow(1. - z * z, 2.);
    }

    // Triweight
    public double kernel6(double z) {
        if (abs(z) > 1) return 0;
        else return 35. / 32. * pow(1. - z * z, 3.);
    }

    // Tricube
    public double kernel7(double z) {
        if (abs(z) > 1) return 0;
        else return 70. / 81. * pow(1. - z * z * z, 3.);
    }

    // Cosine
    public double kernel8(double z) {
        if (abs(z) > 1) return 0;
        else return (PI / 4.) * cos((PI / 2.) * z);
    }

    private double distance(double[][] yCols, int i, int j) {
        double sum = 0.0;

        for (int m = 0; m < yCols.length; m++) {
            double d = yCols[m][i] - yCols[m][j];
            sum += d * d;
        }

        return sqrt(sum);
    }


    private double resolveOneEdgeMaxR3(double[] x, double[] y) {
        System.out.println("Resolving " + x + " === " + y);

        TetradLogger.getInstance().log("info", "\nEDGE " + x + " --- " + y);

        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        double[][] _x = new double[1][];
        _x[0] = x;
        double[][] _y = new double[1][];
        _y[0] = y;

        regression.newSampleData(x, transpose(_y));
        double[] rXY = regression.estimateResiduals();

        regression.newSampleData(y, transpose(_x));
        double[] rYX = regression.estimateResiduals();

        double xPlus = new AndersonDarlingTest(rXY).getASquared();
        double xMinus = new AndersonDarlingTest(x).getASquared();
        double yPlus = new AndersonDarlingTest(rYX).getASquared();
        double yMinus = new AndersonDarlingTest(y).getASquared();

        double deltaX = xPlus - xMinus;
        double deltaY = yPlus - yMinus;

        return deltaX - deltaY;
    }

    private double resolveOneEdgeMaxR3b(double[] x, double[] y) {
        System.out.println("Resolving " + x + " === " + y);

        TetradLogger.getInstance().log("info", "\nEDGE " + x + " --- " + y);

        int N = x.length;

        double[][] yCols = new double[1][N];
        yCols[0] = y;

        double[][] xCols = new double[1][N];
        xCols[0] = x;

        double[][] empty = new double[0][N];

        double[] rX = conditionalResiduals(x, empty);
        double[] rY = conditionalResiduals(y, empty);
        double[] rXY = conditionalResiduals(x, yCols);
        double[] rYX = conditionalResiduals(y, xCols);

        double xPlus = new AndersonDarlingTest(rXY).getASquared();
        double xMinus = new AndersonDarlingTest(rX).getASquared();
        double yPlus = new AndersonDarlingTest(rYX).getASquared();
        double yMinus = new AndersonDarlingTest(rY).getASquared();

        double deltaX = xPlus - xMinus;
        double deltaY = yPlus - yMinus;

        return deltaX - deltaY;
    }

}



