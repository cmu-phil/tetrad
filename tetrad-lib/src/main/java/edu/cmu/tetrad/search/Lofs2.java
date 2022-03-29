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
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.Vector;
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
import static edu.cmu.tetrad.util.StatUtils.*;
import static java.lang.Double.isNaN;
import static java.lang.Math.pow;
import static java.lang.Math.*;

/**
 * LOFS = Ling Orientation Fixed Structure. Some additional algorithm.
 * </p>
 * Expands the set of algorithm from Lofs.
 *
 * @author Joseph Ramsey
 */
public class Lofs2 {

    public enum Score {
        andersonDarling, skew, kurtosis, fifthMoment, absoluteValue,
        exp, expUnstandardized, expUnstandardizedInverted, other, logcosh, entropy
    }

    private final Graph CPDAG;
    private List<DataSet> dataSets;
    private List<Matrix> matrices;
    private double alpha = 1.1;
    private List<Regression> regressions;
    private List<Node> variables;
    private final List<String> varnames;
    private boolean orientStrongerDirection;
    private boolean r2Orient2Cycles = true;

    private Lofs.Score score = Lofs.Score.andersonDarling;
    private double epsilon = 1.0;
    private IKnowledge knowledge = new Knowledge2();
    private Rule rule = Rule.R1;
    private final double delta = 0.0;
    private double zeta;
    private boolean edgeCorrected;
    private double selfLoopStrength;

    //===============================CONSTRUCTOR============================//

    public Lofs2(final Graph CPDAG, final List<DataSet> dataSets)
            throws IllegalArgumentException {

        if (dataSets == null) {
            throw new IllegalArgumentException("Data set must be specified.");
        }

        if (CPDAG == null) {
            throw new IllegalArgumentException("CPDAG must be specified.");
        }

//        for (DataSet dataSet : dataSets) {
//            for (int j = 0; j < dataSet.getNumRows(); j++) {
//                for (int i = 0; i < dataSet.getNumColumns(); i++) {
//                    if (isNaN(dataSet.getDouble(j, i))) {
//                        throw new IllegalArgumentException("Please remove or impute missing values.");
//                    }
//                }
//            }
//        }

        this.CPDAG = CPDAG;
        this.variables = dataSets.get(0).getVariables();
        this.varnames = dataSets.get(0).getVariableNames();

        final List<DataSet> dataSets2 = new ArrayList<>();

        for (int i = 0; i < dataSets.size(); i++) {
            final DataSet dataSet = new BoxDataSet(new DoubleDataBox(dataSets.get(i).getDoubleData().toArray()), this.variables);
            dataSets2.add(dataSet);
        }

        this.dataSets = dataSets2;
    }

    //==========================PUBLIC=========================================//

    public void setRule(final Rule rule) {
        this.rule = rule;
    }

    public boolean isEdgeCorrected() {
        return this.edgeCorrected;
    }

    public void setEdgeCorrected(final boolean C) {
        this.edgeCorrected = C;
    }

    public double getSelfLoopStrength() {
        return this.selfLoopStrength;
    }

    public void setSelfLoopStrength(final double selfLoopStrength) {
        this.selfLoopStrength = selfLoopStrength;
    }

    // orientStrongerDirection list of past and present rules.
    public enum Rule {
        IGCI, R1TimeLag, R1, R2, R3, R4, Tanh, EB, Skew, SkewE, RSkew, RSkewE,
        Patel, Patel25, Patel50, Patel75, Patel90, FastICA, RC
    }

    public Graph orient() {

        final Graph skeleton = GraphUtils.undirectedGraph(getCPDAG());
        Graph graph = new EdgeListGraph(skeleton.getNodes());

        final List<Node> nodes = skeleton.getNodes();

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
            final FastIca fastIca = new FastIca(this.dataSets.get(0).getDoubleData(),
                    this.dataSets.get(0).getNumColumns());
            final FastIca.IcaResult result = fastIca.findComponents();
            System.out.println(result.getW());
            return new EdgeListGraph();
        }

        return graph;
    }

    public double getAlpha() {
        return this.alpha;
    }

    public void setAlpha(final double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Alpha is in range [0, 1]");
        }

        this.alpha = alpha;
    }

    public boolean isOrientStrongerDirection() {
        return this.orientStrongerDirection;
    }

    public void setOrientStrongerDirection(final boolean orientStrongerDirection) {
        this.orientStrongerDirection = orientStrongerDirection;
    }

    public void setR2Orient2Cycles(final boolean r2Orient2Cycles) {
        this.r2Orient2Cycles = r2Orient2Cycles;
    }

    public boolean isR2Orient2Cycles() {
        return this.r2Orient2Cycles;
    }

    public Lofs.Score getScore() {
        return this.score;
    }

    public void setScore(final Lofs.Score score) {
        if (score == null) {
            throw new NullPointerException();
        }

        this.score = score;
    }

    //==========================PRIVATE=======================================//

    private List<Regression> getRegressions() {
        if (this.regressions == null) {
            final List<Regression> regressions = new ArrayList<>();
            this.variables = this.dataSets.get(0).getVariables();

            for (final DataSet dataSet : this.dataSets) {
                regressions.add(new RegressionDataset(dataSet));
            }

            this.regressions = regressions;
        }

        return this.regressions;
    }

    private void setDataSets(final List<DataSet> dataSets) {
        this.dataSets = dataSets;

        this.matrices = new ArrayList<>();

        for (final DataSet dataSet : dataSets) {
            this.matrices.add(dataSet.getDoubleData());
        }
    }

    private void ruleR1TimeLag(final Graph skeleton, final Graph graph) {
        final List<DataSet> timeSeriesDataSets = new ArrayList<>();
        IKnowledge knowledge = null;
        List<Node> dataNodes = null;

        for (final DataModel dataModel : this.dataSets) {
            if (!(dataModel instanceof DataSet)) {
                throw new IllegalArgumentException("Only tabular data sets can be converted to time lagged form.");
            }

            final DataSet dataSet = (DataSet) dataModel;
            final DataSet lags = TimeSeriesUtils.createLagData(dataSet, 1);
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

        final Graph laggedSkeleton = new EdgeListGraph(dataNodes);

        for (final Edge edge : skeleton.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final String node1 = edge.getNode1().getName();
            final String node2 = edge.getNode2().getName();

            final Node node10 = laggedSkeleton.getNode(node1 + ":0");
            final Node node20 = laggedSkeleton.getNode(node2 + ":0");

            laggedSkeleton.addUndirectedEdge(node10, node20);

            final Node node11 = laggedSkeleton.getNode(node1 + ":1");
            final Node node21 = laggedSkeleton.getNode(node2 + ":1");

            laggedSkeleton.addUndirectedEdge(node11, node21);
        }

        for (final Node node : skeleton.getNodes()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final String _node = node.getName();

            final Node node0 = laggedSkeleton.getNode(_node + ":0");
            final Node node1 = laggedSkeleton.getNode(_node + ":1");

            laggedSkeleton.addUndirectedEdge(node0, node1);
        }

        final Lofs2 lofs = new Lofs2(laggedSkeleton, timeSeriesDataSets);
        lofs.setKnowledge(knowledge);
        lofs.setRule(Rule.R1);
        final Graph _graph = lofs.orient();

        graph.removeEdges(new ArrayList<>(graph.getEdges()));

        for (final Edge edge : _graph.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final Node node1 = edge.getNode1();
            final Node node2 = edge.getNode2();
            final Endpoint end1 = edge.getEndpoint1();
            final Endpoint end2 = edge.getEndpoint2();

            final String index1 = node1.getName().split(":")[1];
            final String index2 = node2.getName().split(":")[1];

            if ("1".equals(index1) || "1".equals(index2)) continue;

            final String name1 = node1.getName().split(":")[0];
            final String name2 = node2.getName().split(":")[0];

            final Node _node1 = graph.getNode(name1);
            final Node _node2 = graph.getNode(name2);

            final Edge _edge = new Edge(_node1, _node2, end1, end2);
            graph.addEdge(_edge);
        }
    }

    private void ruleR1(final Graph skeleton, final Graph graph, final List<Node> nodes) {
        final List<DataSet> centeredData = DataUtils.center(this.dataSets);
        setDataSets(centeredData);

        for (final Node node : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final SortedMap<Double, String> scoreReports = new TreeMap<>();

            final List<Node> adj = new ArrayList<>();

            for (final Node _node : skeleton.getAdjacentNodes(node)) {
                if (this.knowledge.isForbidden(_node.getName(), node.getName())) {
                    continue;
                }

                adj.add(_node);
            }

            final DepthChoiceGenerator gen = new DepthChoiceGenerator(adj.size(), adj.size());
            int[] choice;
            double maxScore = Double.NEGATIVE_INFINITY;
            List<Node> parents = null;

            while ((choice = gen.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final List<Node> _parents = GraphUtils.asList(choice, adj);

                final double score = score(node, _parents);
                scoreReports.put(-score, _parents.toString());

                if (score > maxScore) {
                    maxScore = score;
                    parents = _parents;
                }
            }

//            double p = pValue(node, parents);
//
//            if (p > alpha) {
//                continue;
//            }

            for (final double score : scoreReports.keySet()) {
                TetradLogger.getInstance().log("score", "For " + node + " parents = " + scoreReports.get(score) + " score = " + -score);
            }

            TetradLogger.getInstance().log("score", "");

            if (parents == null) {
                continue;
            }

            for (final Node _node : adj) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (parents.contains(_node)) {
                    final Edge parentEdge = Edges.directedEdge(_node, node);

                    if (!graph.containsEdge(parentEdge)) {
                        graph.addEdge(parentEdge);
                    }
                }
            }
        }

        for (final Edge edge : skeleton.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            if (!graph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                graph.addUndirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }
    }

    private void ruleR2(final Graph skeleton, final Graph graph) {
        final List<DataSet> standardized = DataUtils.standardizeData(this.dataSets);
        setDataSets(standardized);

        final Set<Edge> edgeList1 = skeleton.getEdges();

        for (final Edge adj : edgeList1) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final Node x = adj.getNode1();
            final Node y = adj.getNode2();

            if (!isR2Orient2Cycles() && isTwoCycle(graph, x, y)) {
                continue;
            }

            if (!isTwoCycle(graph, x, y) && !isUndirected(graph, x, y)) {
                continue;
            }

            resolveOneEdgeMax2(graph, x, y, !isOrientStrongerDirection());
        }
    }

    private void resolveOneEdgeMax2(final Graph graph, final Node x, final Node y, final boolean strong) {
        TetradLogger.getInstance().log("info", "\nEDGE " + x + " --- " + y);

        final SortedMap<Double, String> scoreReports = new TreeMap<>();

        final List<Node> neighborsx = new ArrayList<>();

        for (final Node _node : graph.getAdjacentNodes(x)) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            if (!this.knowledge.isForbidden(_node.getName(), x.getName())) {
//                if (!knowledge.edgeForbidden(x.getNode(), _node.getNode())) {
                neighborsx.add(_node);
            }
        }

//        neighborsx.remove(y);

        double max = Double.NEGATIVE_INFINITY;
        boolean left = false;
        boolean right = false;

        final DepthChoiceGenerator genx = new DepthChoiceGenerator(neighborsx.size(), neighborsx.size());
        int[] choicex;

        while ((choicex = genx.next()) != null) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final List<Node> condxMinus = GraphUtils.asList(choicex, neighborsx);

            if (condxMinus.contains(y)) continue;

            final List<Node> condxPlus = new ArrayList<>(condxMinus);

            condxPlus.add(y);

            final double xPlus = score(x, condxPlus);
            final double xMinus = score(x, condxMinus);

            final double p = pValue(x, condxPlus);

            if (p > this.alpha) {
                continue;
            }

            final double p2 = pValue(x, condxMinus);

            if (p2 > this.alpha) {
                continue;
            }

            final List<Node> neighborsy = new ArrayList<>();

            for (final Node _node : graph.getAdjacentNodes(y)) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (!this.knowledge.isForbidden(_node.getName(), y.getName())) {
                    neighborsy.add(_node);
                }
            }

            final DepthChoiceGenerator geny = new DepthChoiceGenerator(neighborsy.size(), neighborsy.size());
            int[] choicey;

            while ((choicey = geny.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final List<Node> condyMinus = GraphUtils.asList(choicey, neighborsy);

                if (condyMinus.contains(x)) continue;

                final List<Node> condyPlus = new ArrayList<>(condyMinus);
                condyPlus.add(x);

                final double yPlus = score(y, condyPlus);
                final double yMinus = score(y, condyMinus);

                final double p3 = pValue(y, condyPlus);

//                if (p3 > alpha) {
//                    continue;
//                }

                final double p4 = pValue(y, condyMinus);

//                if (p4 > alpha) {
//                    continue;
//                }

                final boolean forbiddenLeft = this.knowledge.isForbidden(y.getName(), x.getName());
                final boolean forbiddenRight = this.knowledge.isForbidden(x.getName(), y.getName());

                final double delta = 0.0;

                if (strong) {
                    if (yPlus <= xPlus + delta && xMinus <= yMinus + delta) {
                        final double score = combinedScore(xPlus, yMinus);

                        if ((yPlus <= yMinus + delta && xMinus <= xPlus + delta) || forbiddenRight) {
                            final StringBuilder builder = new StringBuilder();

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
                            final StringBuilder builder = new StringBuilder();

                            builder.append("\nNo directed edge ").append(x).append("--").append(y).append(" ").append(score);
                            builder.append("\n   Parents(").append(x).append(") = ").append(condxMinus);
                            builder.append("\n   Parents(").append(y).append(") = ").append(condyMinus);

                            scoreReports.put(-score, builder.toString());
                        }
                    } else if ((xPlus <= yPlus + delta && yMinus <= xMinus + delta) || forbiddenLeft) {
                        final double score = combinedScore(yPlus, xMinus);

                        if (yMinus <= yPlus + delta && xPlus <= xMinus + delta) {
                            final StringBuilder builder = new StringBuilder();

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
                            final StringBuilder builder = new StringBuilder();

                            builder.append("\nNo directed edge ").append(x).append("--").append(y).append(" ").append(score);
                            builder.append("\n   Parents(").append(x).append(") = ").append(condxMinus);
                            builder.append("\n   Parents(").append(y).append(") = ").append(condyMinus);

                            scoreReports.put(-score, builder.toString());
                        }
                    } else if (yPlus <= xPlus + delta && yMinus <= xMinus + delta) {
                        final double score = combinedScore(yPlus, xMinus);

                        final StringBuilder builder = new StringBuilder();

                        builder.append("\nNo directed edge ").append(x).append("--").append(y).append(" ").append(score);
                        builder.append("\n   Parents(").append(x).append(") = ").append(condxMinus);
                        builder.append("\n   Parents(").append(y).append(") = ").append(condyMinus);

                        scoreReports.put(-score, builder.toString());
                    } else if (xPlus <= yPlus + delta && xMinus <= yMinus + delta) {
                        final double score = combinedScore(yPlus, xMinus);

                        final StringBuilder builder = new StringBuilder();

                        builder.append("\nNo directed edge ").append(x).append("--").append(y).append(" ").append(score);
                        builder.append("\n   Parents(").append(x).append(") = ").append(condxMinus);
                        builder.append("\n   Parents(").append(y).append(") = ").append(condyMinus);

                        scoreReports.put(-score, builder.toString());
                    }
                } else {
                    if ((yPlus <= xPlus + delta && xMinus <= yMinus + delta) || forbiddenRight) {
                        final double score = combinedScore(xPlus, yMinus);

                        final StringBuilder builder = new StringBuilder();

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
                        final double score = combinedScore(yPlus, xMinus);

                        final StringBuilder builder = new StringBuilder();

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
                        final double score = combinedScore(yPlus, xMinus);

                        final StringBuilder builder = new StringBuilder();

                        builder.append("\nNo directed edge ").append(x).append("--").append(y).append(" ").append(score);
                        builder.append("\n   Parents(").append(x).append(") = ").append(condxMinus);
                        builder.append("\n   Parents(").append(y).append(") = ").append(condyMinus);

                        scoreReports.put(-score, builder.toString());
                    } else if (xPlus <= yPlus + delta && xMinus <= yMinus + delta) {
                        final double score = combinedScore(yPlus, xMinus);

                        final StringBuilder builder = new StringBuilder();

                        builder.append("\nNo directed edge ").append(x).append("--").append(y).append(" ").append(score);
                        builder.append("\n   Parents(").append(x).append(") = ").append(condxMinus);
                        builder.append("\n   Parents(").append(y).append(") = ").append(condyMinus);

                        scoreReports.put(-score, builder.toString());
                    }
                }
            }
        }

        for (final double score : scoreReports.keySet()) {
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


    private Graph ruleR3(final Graph graph) {
        final List<DataSet> standardized = DataUtils.standardizeData(this.dataSets);
        setDataSets(standardized);

        final Set<Edge> edgeList1 = graph.getEdges();

        for (final Edge adj : edgeList1) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final Node x = adj.getNode1();
            final Node y = adj.getNode2();

            resolveOneEdgeMaxR3(graph, x, y);
        }

        return graph;

    }

    private void resolveOneEdgeMaxR3(final Graph graph, final Node x, final Node y) {
        final String xname = x.getName();
        final String yname = y.getName();

        if (this.knowledge.isForbidden(yname, xname) || this.knowledge.isRequired(xname, yname)) {
            graph.removeEdge(x, y);
            graph.addDirectedEdge(x, y);
            return;
        } else if (this.knowledge.isForbidden(xname, yname) || this.knowledge.isRequired(yname, xname)) {
            graph.removeEdge(y, x);
            graph.addDirectedEdge(y, x);
            return;
        }

//        TetradLogger.getInstance().log("info", "\nEDGE " + x + " --- " + y);

        final List<Node> condxMinus = Collections.emptyList();
        final List<Node> condxPlus = Collections.singletonList(y);
        final List<Node> condyMinus = Collections.emptyList();
        final List<Node> condyPlus = Collections.singletonList(x);

//        double px = pValue(x, condxMinus);
//        double py = pValue(y, condyMinus);

//        if (px > alpha || py > alpha) {
//            return;
//        }

        final double xPlus = score(x, condxPlus);
        final double xMinus = score(x, condxMinus);

        final double yPlus = score(y, condyPlus);
        final double yMinus = score(y, condyMinus);

//        if (!(xPlus > 0.8 && xMinus > 0.8 && yPlus > 0.8 && yMinus > 0.8)) return;

        final double deltaX = xPlus - xMinus;
        final double deltaY = yPlus - yMinus;

        graph.removeEdges(x, y);
//        double epsilon = 0;

        if (deltaY > deltaX) {
            graph.addDirectedEdge(x, y);
        } else {
            graph.addDirectedEdge(y, x);
        }
    }

    public Graph ruleR4(Graph graph) {
        final List<Node> nodes = this.dataSets.get(0).getVariables();
        graph = GraphUtils.replaceNodes(graph, nodes);

        // For each row, list the columns of W in that row that are parameters. Note that the diagonal
        // is fixed to 1, so diagonal elements aren't parameters.
        final List<List<Integer>> rows = new ArrayList<>();
        final List<List<List<Double>>> paramsforDataSets = new ArrayList<>();
        final List<List<Double>> avgParams = new ArrayList<>();

        for (int k = 0; k < nodes.size(); k++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final List<Node> adj = graph.getAdjacentNodes(nodes.get(k));
            final List<Integer> row = new ArrayList<>();
            final List<Double> avgParam = new ArrayList<>();
            final List<Node> nodesInRow = new ArrayList<>();

            for (final Node node : adj) {
                if (this.knowledge.isForbidden(node.getName(), nodes.get(k).getName())) {
                    continue;
                }

                final int j = nodes.indexOf(node);
                row.add(j);
                avgParam.add(0.0);
                nodesInRow.add(node);
            }

            for (final Node node : nodes) {
                if (this.knowledge.isRequired(node.getName(), nodes.get(k).getName())) {
                    if (!nodesInRow.contains(node)) {
                        final int j = nodes.indexOf(node);
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
        for (int i = 0; i < this.dataSets.size(); i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final Matrix data = this.dataSets.get(i).getDoubleData();
            final List<List<Double>> parameters = new ArrayList<>();

            // Note that the 1's along the diagonal of W are hard coded into the code for calculating scores.
            // Otherwise list doubles to correspond to each parameter.
            for (int k = 0; k < nodes.size(); k++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final List<Double> params = new ArrayList<>();

                for (final int j : rows.get(k)) {
                    params.add(0.0);
                }

                parameters.add(params);
            }

            final double range = this.zeta;

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
            final Graph _graph = new EdgeListGraph(nodes);

            for (int i = 0; i < rows.size(); i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                for (int _j = 0; _j < rows.get(i).size(); _j++) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    final double param = avg(paramsforDataSets, i, _j);
                    avgParams.get(i).set(_j, param);
                    final int j = rows.get(i).get(_j);

                    if (i == j) continue;

                    final Node node1 = nodes.get(j);
                    final Node node2 = nodes.get(i);

                    final Edge edge1 = Edges.directedEdge(node1, node2);

                    if (abs(param) >= this.epsilon) {
                        _graph.addEdge(edge1);
                    }
                }
            }

            return _graph;
        } else {
            final Graph _graph = new EdgeListGraph(nodes);

            for (int i = 0; i < rows.size(); i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                for (int _j = 0; _j < rows.get(i).size(); _j++) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    final int j = rows.get(i).get(_j);

                    if (j > i) continue;

                    final double param1 = avg(paramsforDataSets, i, _j);
                    avgParams.get(i).set(_j, param1);

                    double param2 = 0.0;

                    for (int _i = 0; _i < rows.get(j).size(); _i++) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        final int i2 = rows.get(j).get(_i);

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

                    final Node node1 = nodes.get(j);
                    final Node node2 = nodes.get(i);
                    final Edge edge1 = Edges.directedEdge(node1, node2);
                    final Edge edge2 = Edges.directedEdge(node2, node1);

                    if (abs(param1) > abs(param2)) {
                        _graph.addEdge(edge1);
                    } else if (abs(param1) < abs(param2)) {
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

    private void printEstimatedCoefs(final List<Node> nodes, final List<List<Integer>> rows, final List<List<Double>> parameters) {
        final NumberFormat nf = new DecimalFormat("0.0000");

        for (int g = 0; g < rows.size(); g++) {
            System.out.print(nodes.get(g) + "\t");
            for (int h = 0; h < rows.get(g).size(); h++) {
                System.out.print(nodes.get(rows.get(g).get(h)) + ":" + nf.format(-parameters.get(g).get(h)) + "\t");
            }

            System.out.println();
        }
    }

    private double avg(final List<List<List<Double>>> paramsforDataSets, final int i, final int j) {

        // The average of non-missing coefficients.
        double sum = 0.0;
        int count = 0;

        for (final List<List<Double>> params : paramsforDataSets) {
            final Double coef = params.get(i).get(j);

            if (!isNaN(coef)) {
                sum += coef;
                count++;
            }
        }

        return sum / count;
    }

    private void optimizeAllRows(final Matrix data, final double range,
                                 final List<List<Integer>> rows, final List<List<Double>> parameters) {

        for (int i = 0; i < rows.size(); i++) {
            System.out.println("Optimizing row = " + i);

            try {
                optimizeRow(i, data, range, rows, parameters);
            } catch (final IllegalStateException e) {
                e.printStackTrace();
            }
        }
    }

    private void optimizeRow(final int rowIndex, final Matrix data,
                             final double range, final List<List<Integer>> rows,
                             final List<List<Double>> parameters) {
        System.out.println("A");

        final int numParams = rows.get(rowIndex).size();

        final double[] dLeftMin = new double[numParams];
        final double[] dRightMin = new double[numParams];

        double[] values = new double[numParams];
        final double delta = 0.1;

        if (false) { //isEdgeCorrected()) {
            final double min = -2;
            final double max = 2;

            final int[] dims = new int[values.length];

            final int numBins = 5;
            for (int i = 0; i < values.length; i++) dims[i] = numBins;

            final CombinationGenerator gen = new CombinationGenerator(dims);
            int[] comb;
            List<Double> maxParams = new ArrayList<>();

            for (int i = 0; i < values.length; i++) maxParams.add(0.0);

            double maxV = Double.NEGATIVE_INFINITY;

            while ((comb = gen.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final List<Double> params = new ArrayList<>();

                for (int i = 0; i < values.length; i++) {
                    params.add(min + (max - min) * (comb[i] / (double) numBins));
                }

                parameters.set(rowIndex, params);

                final double v = scoreRow(rowIndex, data, rows, parameters);

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
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                parameters.get(rowIndex).set(i, -range);
                double vLeft = scoreRow(rowIndex, data, rows, parameters);
                double dLeft = -range;

                // Search from the left for the first valley; mark that as dleft.
                for (double d = -range + delta; d < range; d += delta) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    parameters.get(rowIndex).set(i, d);
                    final double v = scoreRow(rowIndex, data, rows, parameters);
                    if (isNaN(v)) continue;
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
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    parameters.get(rowIndex).set(i, d);
                    final double v = scoreRow(rowIndex, data, rows, parameters);
                    if (isNaN(v)) continue;
                    if (v > vRight) break;
                    vRight = v;
                    dRight = d;
                }

                // If dleft dright ended up reversed, re-reverse them.
                if (dLeft > dRight) {
                    final double temp = dRight;
                    dLeft = dRight;
                    dRight = temp;
                }

                System.out.println("dLeft = " + dLeft + " dRight = " + dRight);

                dLeftMin[i] = dLeft;
                dRightMin[i] = dRight;

                values[i] = (dLeft + dRight) / 2.0;
            }
        } else {

            // Default case: search for the maximum score over the entire range.
            for (int i = 0; i < numParams; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                dLeftMin[i] = -range;
                dRightMin[i] = range;

                values[i] = 0;
            }
        }

        final MultivariateFunction function = new MultivariateFunction() {
            public double value(final double[] values) {
                System.out.println(Arrays.toString(values));

                for (int i = 0; i < values.length; i++) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    parameters.get(rowIndex).set(i, values[i]);
                }

                final double v = scoreRow(rowIndex, data, rows, parameters);

                if (isNaN(v)) {
                    return Double.POSITIVE_INFINITY; // was 10000
                }

                return -v;
            }
        };

        try {
            final MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);

            final PointValuePair pair = search.optimize(
                    new InitialGuess(values),
                    new ObjectiveFunction(function),
                    GoalType.MINIMIZE,
                    new MaxEval(100000));

            values = pair.getPoint();
        } catch (final Exception e) {
            e.printStackTrace();

            for (int i = 0; i < values.length; i++) {
                parameters.get(rowIndex).set(i, Double.NaN);
            }
        }
    }

    private double[] col;

    // rowIndex is for the W matrix, not for the data.
    public double scoreRow(final int rowIndex, final Matrix data, final List<List<Integer>> rows, final List<List<Double>> parameters) {
        if (this.col == null) {
            this.col = new double[data.rows()];
        }

        final List<Integer> cols = rows.get(rowIndex);

        for (int i = 0; i < data.rows(); i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            double d = 0.0;

            for (int j = 0; j < cols.size(); j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final int _j = cols.get(j);
                final double coef = parameters.get(rowIndex).get(j);
                final double value = data.get(i, _j);
                d += coef * value;
            }

            // Add in the diagonal, assumed to consist entirely of 1's, indicating no self loop.
            d += (1.0 - getSelfLoopStrength()) * data.get(i, rowIndex);

            this.col[i] = d;
        }

        return score(this.col);
    }

    public double rowPValue(final int rowIndex, final Matrix data, final List<List<Integer>> rows, final List<List<Double>> parameters) {
        if (this.col == null) {
            this.col = new double[data.rows()];
        }

        final List<Integer> cols = rows.get(rowIndex);

        for (int i = 0; i < data.rows(); i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            double d = 0.0;

            for (int j = 0; j < cols.size(); j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final int _j = cols.get(j);
                final double coef = parameters.get(rowIndex).get(j);
                final double value = data.get(i, _j);
                d += coef * value;
            }

            // Add in the diagonal, assumed to consist entirely of 1's.
            d += 1.0 * data.get(i, rowIndex);

            this.col[i] = d;
        }

        return aSquaredP(this.col);
    }

    private Graph entropyBased(final Graph graph) {
        DataSet dataSet = DataUtils.concatenate(this.dataSets);
        dataSet = DataUtils.standardizeData(dataSet);
        final Graph _graph = new EdgeListGraph(graph.getNodes());

        for (final Edge edge : graph.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final Node x = edge.getNode1();
            final Node y = edge.getNode2();

            final Node _x = dataSet.getVariable(x.getName());
            final Node _y = dataSet.getVariable(y.getName());

            final List<double[]> ret = extractData(dataSet, _x, _y);

            final double[] xData = ret.get(0);
            final double[] yData = ret.get(1);

            final double[] d = new double[xData.length];
            final double[] e = new double[xData.length];

            final double cov = covariance(xData, yData);

            for (int i = 0; i < xData.length; i++) {
                d[i] = yData[i] - cov * xData[i];  // y regressed on x
                e[i] = xData[i] - cov * yData[i];  // x regressed on y
            }

            final double R = -maxEntApprox(xData) - maxEntApprox(d)
                    + maxEntApprox(yData) + maxEntApprox(e);

            if (R > 0) {
                _graph.addDirectedEdge(x, y);
            } else {
                _graph.addDirectedEdge(y, x);
            }
        }

        return _graph;
    }

    private Graph tanhGraph(Graph graph) {
        DataSet dataSet = DataUtils.concatenate(this.dataSets);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());
        dataSet = DataUtils.standardizeData(dataSet);
        final double[][] data = dataSet.getDoubleData().transpose().toArray();
        final Graph _graph = new EdgeListGraph(graph.getNodes());
        final List<Node> nodes = dataSet.getVariables();
        final Map<Node, Integer> nodesHash = new HashMap<>();

        for (int i = 0; i < nodes.size(); i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            nodesHash.put(nodes.get(i), i);
        }

        for (final Edge edge : graph.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final Node x = edge.getNode1();
            final Node y = edge.getNode2();

            double sumX = 0.0;
            int countX = 0;

            final double[] xData = data[nodesHash.get(edge.getNode1())];
            final double[] yData = data[nodesHash.get(edge.getNode2())];

            for (int i = 0; i < xData.length; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final double x0 = xData[i];
                final double y0 = yData[i];

                final double termX = (x0 * tanh(y0) - tanh(x0) * y0);

                sumX += termX;
                countX++;
            }

            double R = sumX / countX;

            final double rhoX = regressionCoef(xData, yData);
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
    private Graph skewGraph(Graph graph, final boolean empirical) {
        DataSet dataSet = DataUtils.concatenate(this.dataSets);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());
        dataSet = DataUtils.standardizeData(dataSet);
        final double[][] data = dataSet.getDoubleData().transpose().toArray();
        final Graph _graph = new EdgeListGraph(graph.getNodes());
        final List<Node> nodes = dataSet.getVariables();
        final Map<Node, Integer> nodesHash = new HashMap<>();

        for (int i = 0; i < nodes.size(); i++) {
            nodesHash.put(nodes.get(i), i);
        }

        for (final Edge edge : graph.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final Node x = edge.getNode1();
            final Node y = edge.getNode2();

            double sumX = 0.0;
            int countX = 0;

            final int _i = nodesHash.get(edge.getNode1());
            final int _j = nodesHash.get(edge.getNode2());

            double[] xData = data[_i];
            double[] yData = data[_j];

            if (empirical) {
                xData = correctSkewnesses(xData);
                yData = correctSkewnesses(yData);
            }

            for (int i = 0; i < xData.length; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final double x0 = xData[i];
                final double y0 = yData[i];

                final double termX = x0 * x0 * y0 - x0 * y0 * y0;

                sumX += termX;
                countX++;
            }

            double R = sumX / countX;

            final double rhoX = regressionCoef(xData, yData);

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
    private Graph robustSkewGraph(Graph graph, final boolean empirical) {
        final List<DataSet> _dataSets = new ArrayList<>();
        for (final DataSet dataSet : this.dataSets) _dataSets.add(dataSet);// DataUtils.standardizeData(dataSet));
        DataSet dataSet = DataUtils.concatenate(_dataSets);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());
        dataSet = DataUtils.standardizeData(dataSet);
        final double[][] data = dataSet.getDoubleData().transpose().toArray();
        final List<Node> nodes = dataSet.getVariables();
        final Map<Node, Integer> nodesHash = new HashMap<>();

        for (int i = 0; i < nodes.size(); i++) {
            nodesHash.put(nodes.get(i), i);
        }

        for (final Edge edge : graph.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final Node x = edge.getNode1();
            final Node y = edge.getNode2();

            double[] xData = data[nodesHash.get(edge.getNode1())];
            double[] yData = data[nodesHash.get(edge.getNode2())];

            if (empirical) {
                xData = correctSkewnesses(xData);
                yData = correctSkewnesses(yData);
            }

            final double[] xx = new double[xData.length];
            final double[] yy = new double[yData.length];

            for (int i = 0; i < xData.length; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final double xi = xData[i];
                final double yi = yData[i];

                final double s1 = g(xi) * yi;
                final double s2 = xi * g(yi);

                xx[i] = s1;
                yy[i] = s2;
            }

            final double mxx = mean(xx);
            final double myy = mean(yy);

            graph.removeEdge(edge);

            if (mxx > myy) {
                graph.addDirectedEdge(x, y);
            } else if (myy > mxx) {
                graph.addDirectedEdge(y, x);
            } else {
                graph.addUndirectedEdge(x, y);
            }
        }

        return graph;
    }

    private double g(final double x) {
        return log(cosh(Math.max(x, 0)));
    }

    private double g2(final double x) {
        return log(cosh(Math.max(-x, 0)));
    }

    // cutoff is NaN if no thresholding is to be done, otherwise a threshold between 0 and 1.
    private Graph patelTauOrientation(final Graph graph, final double cutoff) {
        final List<DataSet> centered = DataUtils.center(this.dataSets);
        final DataSet concat = DataUtils.concatenate(centered);
        final DataSet dataSet = DataUtils.standardizeData(concat);

        final Graph _graph = new EdgeListGraph(graph.getNodes());

        for (final Edge edge : graph.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final Node x = edge.getNode1();
            final Node y = edge.getNode2();

            final Node _x = dataSet.getVariable(x.getName());
            final Node _y = dataSet.getVariable(y.getName());

            final List<double[]> ret = prepareData(dataSet, _x, _y, false, false);
            final double[] xData = ret.get(0);
            final double[] yData = ret.get(1);

            final double R = patelTau(xData, yData, cutoff);

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
    private double patelTau(final double[] d1in, final double[] d2in, final double cutoff) {
        double grotMIN = percentile(d1in, 10);
        double grotMAX = percentile(d1in, 90);

        final double XT = .25; // Cancels out, don't know why this is here.

        final double[] d1b = new double[d1in.length];

        for (int i = 0; i < d1b.length; i++) {
            final double y1 = (d1in[i] - grotMIN) / (grotMAX - grotMIN);
            final double y2 = Math.min(y1, 1.0);
            final double y3 = Math.max(y2, 0.0);
            d1b[i] = y3;
        }

        if (!isNaN(cutoff)) {
            for (int i = 0; i < d1b.length; i++) {
                if (d1b[i] > cutoff) d1b[i] = 1.0;
                else d1b[i] = 0.0;
            }
        }

        grotMIN = percentile(d2in, 10);
        grotMAX = percentile(d2in, 90);

        final double[] d2b = new double[d2in.length];

        for (int i = 0; i < d2b.length; i++) {
            final double y1 = (d2in[i] - grotMIN) / (grotMAX - grotMIN);
            final double y2 = Math.min(y1, 1.0);
            final double y3 = Math.max(y2, 0.0);
            d2b[i] = y3;
        }

        if (!isNaN(cutoff)) {
            for (int i = 0; i < d2b.length; i++) {
                if (d2b[i] > cutoff) d2b[i] = 1.0;
                else d2b[i] = 0.0;
            }
        }

        final double theta1 = dotProduct(d1b, d2b) / XT;
        final double theta2 = dotProduct(d1b, minus(1, d2b)) / XT;
        final double theta3 = dotProduct(d2b, minus(1, d1b)) / XT;
//        double theta4= dotProduct(minus(1, d1b), minus(1, d2b))/XT;

        final double tau_12;

        if (theta2 > theta3) tau_12 = 1 - (theta1 + theta3) / (theta1 + theta2);
        else tau_12 = (theta1 + theta2) / (theta1 + theta3) - 1;

        return -tau_12;
    }

    private double dotProduct(final double[] x, final double[] y) {
        double p = 0.0;

        for (int i = 0; i < x.length; i++) {
            p += x[i] * y[i];
        }

        return p;
    }

    private double[] minus(final int s, final double[] x) {
        final double[] y = new double[x.length];

        for (int i = 0; i < x.length; i++) {
            y[i] = s - x[i];
        }

        return y;
    }

    private double percentile(final double[] x, final double percent) {
        final double[] _x = Arrays.copyOf(x, x.length);
        Arrays.sort(_x);
        return _x[(int) (x.length * (percent / 100.0))];
    }

    private List<double[]> extractData(final DataSet data, final Node _x, final Node _y) {
        final int xIndex = data.getColumn(_x);
        final int yIndex = data.getColumn(_y);

        final double[][] _data = data.getDoubleData().transpose().toArray();

//        double[] xData = _data.viewColumn(xIndex).toArray();
//        double[] yData = _data.viewColumn(yIndex).toArray();

        double[] xData = _data[xIndex];
        double[] yData = _data[yIndex];

        final List<Double> xValues = new ArrayList<>();
        final List<Double> yValues = new ArrayList<>();

        for (int i = 0; i < data.getNumRows(); i++) {
            if (!isNaN(xData[i]) && !isNaN(yData[i])) {
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

        final List<double[]> ret = new ArrayList<>();
        ret.add(xData);
        ret.add(yData);

        return ret;
    }

    private double[] correctSkewnesses(final double[] data) {
        final double skewness = skewness(data);
        final double[] data2 = new double[data.length];
        for (int i = 0; i < data.length; i++) data2[i] = data[i] * signum(skewness);
        return data2;
    }

    private List<double[]> prepareData(final DataSet concatData, final Node _x, final Node _y, final boolean skewCorrection, final boolean coefCorrection) {
        final int xIndex = concatData.getColumn(_x);
        final int yIndex = concatData.getColumn(_y);

        double[] xData = concatData.getDoubleData().getColumn(xIndex).toArray();
        double[] yData = concatData.getDoubleData().getColumn(yIndex).toArray();

        final List<Double> xValues = new ArrayList<>();
        final List<Double> yValues = new ArrayList<>();

        for (int i = 0; i < concatData.getNumRows(); i++) {
            if (!isNaN(xData[i]) && !isNaN(yData[i])) {
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
            final double xSkew = skewness(xData);
            final double ySkew = skewness(yData);

            for (int i = 0; i < xData.length; i++) xData[i] *= signum(xSkew);
            for (int i = 0; i < yData.length; i++) yData[i] *= signum(ySkew);
        }

        if (coefCorrection) {
            double coefX;
            try {
                coefX = regressionCoef(xData, yData);
            } catch (final Exception e) {
                coefX = Double.NaN;
            }

            double coefY;

            try {
                coefY = regressionCoef(yData, xData);
            } catch (final Exception e) {
                coefY = Double.NaN;
            }

            for (int i = 0; i < xData.length; i++) xData[i] *= signum(coefX);
            for (int i = 0; i < yData.length; i++) yData[i] *= signum(coefY);

        }

        final List<double[]> ret = new ArrayList<>();
        ret.add(xData);
        ret.add(yData);

        return ret;
    }

    private double regressionCoef(final double[] xValues, final double[] yValues) {
        final List<Node> v = new ArrayList<>();
        v.add(new GraphNode("x"));
        v.add(new GraphNode("y"));

        final Matrix bothData = new Matrix(xValues.length, 2);

        for (int i = 0; i < xValues.length; i++) {
            bothData.set(i, 0, xValues[i]);
            bothData.set(i, 1, yValues[i]);
        }

        final Regression regression2 = new RegressionDataset(bothData, v);
        final RegressionResult result;

        try {
            result = regression2.regress(v.get(0), v.get(1));
        } catch (final Exception e) {
            return Double.NaN;
        }

        return result.getCoef()[1];
    }

    private boolean isTwoCycle(final Graph graph, final Node x, final Node y) {
        final List<Edge> edges = graph.getEdges(x, y);
        return edges.size() == 2;
    }

    private boolean isUndirected(final Graph graph, final Node x, final Node y) {
        final List<Edge> edges = graph.getEdges(x, y);
        if (edges.size() == 1) {
            final Edge edge = graph.getEdge(x, y);
            return Edges.isUndirectedEdge(edge);
        }

        return false;
    }

    public void setEpsilon(final double epsilon) {
        this.epsilon = epsilon;
    }

    public void setZeta(final double zeta) {
        this.zeta = zeta;
    }

    public void setKnowledge(final IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    private double aSquared(final double[] x) {
        return new AndersonDarlingTest(x).getASquaredStar();
    }

    private double aSquaredP(final double[] x) {
        return new AndersonDarlingTest(x).getP();
    }

    /**
     * @return the probability associated with the most recently computed independence test.
     */
    public double getPValue(final double fisherZ) {
        return 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(fisherZ)));
    }

    private static class Pair {
        int index;
        double value;

        public Pair(final int index, final double value) {
            this.index = index;
            this.value = value;
        }

        public String toString() {
            return "<" + this.index + ", " + this.value + ">";
        }
    }

    private Graph igci(final Graph graph) {
        if (this.dataSets.size() > 1) throw new IllegalArgumentException("Expecting exactly one data set for IGCI.");

        final DataSet dataSet = this.dataSets.get(0);
        final Matrix matrix = dataSet.getDoubleData();

        final Graph _graph = new EdgeListGraph(graph.getNodes());

        for (final Edge edge : graph.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final Node x = edge.getNode1();
            final Node y = edge.getNode2();

            final Node _x = dataSet.getVariable(x.getName());
            final Node _y = dataSet.getVariable(y.getName());

            final int xIndex = dataSet.getVariables().indexOf(_x);
            final int yIndex = dataSet.getVariables().indexOf(_y);

            final double[] xCol = matrix.getColumn(xIndex).toArray();
            final double[] yCol = matrix.getColumn(yIndex).toArray();

            final double f = igci(xCol, yCol, 2, 1);

            graph.removeEdges(x, y);

            if (f < -this.epsilon) {
                _graph.addDirectedEdge(x, y);
            } else if (f > this.epsilon) {
                _graph.addDirectedEdge(y, x);
            } else {
                if (resolveOneEdgeMaxR3(xCol, yCol) < 0) {
                    _graph.addDirectedEdge(x, y);
                } else {
                    _graph.addDirectedEdge(y, x);
                }
            }

        }

        return _graph;
    }

    private double igci(double[] x, double[] y, final int refMeasure, final int estimator) {
        final int m = x.length;

        if (m != y.length) {
            throw new IllegalArgumentException("Vectors must be the same length.");
        }

        switch (refMeasure) {
            case 1:
                // uniform reference measure

                final double minx = min(x);
                final double maxx = max(x);
                final double miny = min(y);
                final double maxy = max(y);

                for (int i = 0; i < x.length; i++) {
                    x[i] = (x[i] - minx) / (maxx - minx);
                    y[i] = (y[i] - miny) / (maxy - miny);
                }

                break;

            case 2:
                final double meanx = mean(x);
                final double stdx = sd(x);
                final double meany = mean(y);
                final double stdy = sd(y);

                // Gaussian reference measure
                for (int i = 0; i < x.length; i++) {
                    x[i] = (x[i] - meanx) / stdx;
                    y[i] = (y[i] - meany) / stdy;
                }

                break;

            default:
                throw new IllegalArgumentException("Warning: unknown reference measure - no scaling applied.");
        }


        final double f;

        if (estimator == 1) {
            // difference of entropies

            double[] x1 = Arrays.copyOf(x, x.length);
            Arrays.sort(x1);

            x1 = removeNaN(x1);

            double[] y1 = Arrays.copyOf(y, y.length);
            Arrays.sort(y1);

            y1 = removeNaN(y1);

            final int n1 = x1.length;
            double hx = 0.0;
            for (int i = 0; i < n1 - 1; i++) {
                final double delta = x1[i + 1] - x1[i];
                if (delta != 0) {
                    hx = hx + log(abs(delta));
                }
            }

            hx = hx / (n1 - 1) + psi(n1) - psi(1);

            final int n2 = y1.length;
            double hy = 0.0;
            for (int i = 0; i < n2 - 1; i++) {
                final double delta = y1[i + 1] - y1[i];

                if (delta != 0) {
                    if (isNaN(delta)) {
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

            final List<Pair> _x = new ArrayList<>();

            for (int i = 0; i < x.length; i++) {
                _x.add(new Pair(i, x[i]));
            }

            Collections.sort(_x, new Comparator<Pair>() {
                public int compare(final Pair pair1, final Pair pair2) {
                    return new Double(pair1.value).compareTo(pair2.value);
                }
            });

            final List<Pair> _y = new ArrayList<>();

            for (int i = 0; i < y.length; i++) {
                _y.add(new Pair(i, y[i]));
            }

            Collections.sort(_y, new Comparator<Pair>() {
                public int compare(final Pair pair1, final Pair pair2) {
                    return new Double(pair1.value).compareTo(pair2.value);
                }
            });

            for (int i = 0; i < m - 1; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

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
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final double X1 = x[i];
                final double X2 = x[i + 1];
                final double Y1 = y[i];
                final double Y2 = y[i + 1];

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
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final double X1 = x[i];
                final double X2 = x[i + 1];
                final double Y1 = y[i];
                final double Y2 = y[i + 1];

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
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final double X1 = x[i];
                final double X2 = x[i + 1];
                final double Y1 = y[i] - y[0];
                final double Y2 = y[i + 1] - y[0];

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

    private double[] removeNaN(final double[] data) {
        final List<Double> _leaveOutMissing = new ArrayList<>();

        for (int i = 0; i < data.length; i++) {
            if (!isNaN(data[i])) {
                _leaveOutMissing.add(data[i]);
            }
        }

        final double[] _data = new double[_leaveOutMissing.size()];

        for (int i = 0; i < _leaveOutMissing.size(); i++) _data[i] = _leaveOutMissing.get(i);

        return _data;
    }


    // digamma

    double psi(double x) {
        double result = 0;
        final double xx;
        final double xx2;
        final double xx4;
        assert (x > 0);
        for (; x < 7; ++x)
            result -= 1 / x;
        x -= 1.0 / 2.0;
        xx = 1.0 / x;
        xx2 = xx * xx;
        xx4 = xx2 * xx2;
        result += log(x) + (1. / 24.) * xx2 - (7.0 / 960.0) * xx4 + (31.0 / 8064.0) * xx4 * xx2 - (127.0 / 30720.0) * xx4 * xx4;
        return result;
    }

    private double min(final double[] x) {
        double min = Double.POSITIVE_INFINITY;

        for (final double _x : x) {
            if (_x < min) min = _x;
        }

        return min;
    }

    private double max(final double[] x) {
        double max = Double.NEGATIVE_INFINITY;

        for (final double _x : x) {
            if (_x > max) max = _x;
        }

        return max;
    }


    private double combinedScore(final double score1, final double score2) {
        return score1 + score2;
    }

    private double score(final Node y, final List<Node> parents) {
        if (this.score == Lofs.Score.andersonDarling) {
            return andersonDarlingPASquare(y, parents);
        } else if (this.score == Lofs.Score.kurtosis) {
            return abs(kurtosis(residuals(y, parents, true, true)));
        } else if (this.score == Lofs.Score.entropy) {
            return entropy(y, parents);
        } else if (this.score == Lofs.Score.skew) {
            return abs(skewness(residuals(y, parents, true, true)));
        } else if (this.score == Lofs.Score.fifthMoment) {
            return abs(standardizedFifthMoment(residuals(y, parents, true, true)));
        } else if (this.score == Lofs.Score.absoluteValue) {
            return meanAbsolute(y, parents);
        } else if (this.score == Lofs.Score.exp) {
            return expScoreUnstandardized(y, parents);
        } else if (this.score == Lofs.Score.other) {
            final double[] _f = residuals(y, parents, true, true);
            return score(_f);
        } else if (this.score == Lofs.Score.logcosh) {
            return logCoshScore(y, parents);
        }

        throw new IllegalStateException();
    }

    private double score(double[] col) {
        if (this.score == Lofs.Score.andersonDarling) {
            return new AndersonDarlingTest(col).getASquaredStar();
        } else if (this.score == Lofs.Score.entropy) {
            return maxEntApprox(col);
        } else if (this.score == Lofs.Score.kurtosis) {
            col = DataUtils.standardizeData(col);
            return -abs(kurtosis(col));
        } else if (this.score == Lofs.Score.skew) {
            return abs(skewness(col));
        } else if (this.score == Lofs.Score.fifthMoment) {
            return abs(standardizedFifthMoment(col));
        } else if (this.score == Lofs.Score.absoluteValue) {
            return StatUtils.meanAbsolute(col);
        } else if (this.score == Lofs.Score.exp) {
            return expScoreUnstandardized(col);
        } else if (this.score == Lofs.Score.other) {
            return abs(kurtosis(col));
        } else if (this.score == Lofs.Score.logcosh) {
            return StatUtils.logCoshScore(col);
        }

        throw new IllegalStateException("Unrecognized score: " + this.score);
    }

    //=============================PRIVATE METHODS=========================//

    private double meanAbsolute(final Node node, final List<Node> parents) {
        final double[] _f = residuals(node, parents, false, true);

        return StatUtils.meanAbsolute(_f);
    }

    private double expScoreUnstandardized(final Node node, final List<Node> parents) {
        final double[] _f = residuals(node, parents, false, true);

        return expScoreUnstandardized(_f);
    }

    private double expScoreUnstandardized(final double[] _f) {
        double sum = 0.0;

        for (int k = 0; k < _f.length; k++) {
            sum += exp(_f[k]);
        }

        final double expected = sum / _f.length;
        return -abs(log(expected));
    }

    private double logCoshScore(final Node node, final List<Node> parents) {
        final double[] _f = residuals(node, parents, true, true);
        return StatUtils.logCoshScore(_f);
    }

    private double[] residuals(final Node node, final List<Node> parents, final boolean standardize, final boolean removeNaN) {
        final List<Double> _residuals = new ArrayList<>();

        final Node target = getVariable(this.variables, node.getName());
        final List<Node> regressors = new ArrayList<>();

        for (final Node _regressor : parents) {
            final Node variable = getVariable(this.variables, _regressor.getName());
            regressors.add(variable);
        }

        DATASET:
        for (int m = 0; m < this.dataSets.size(); m++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final DataSet dataSet = this.dataSets.get(m);

            final int targetCol = dataSet.getColumn(target);

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                if (isNaN(dataSet.getDouble(i, targetCol))) {
                    continue DATASET;
                }
            }

            for (final Node regressor : regressors) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final int regressorCol = dataSet.getColumn(regressor);

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    if (isNaN(dataSet.getDouble(i, regressorCol))) {
                        continue DATASET;
                    }
                }
            }

            final RegressionResult result = getRegressions().get(m).regress(target, regressors);
            final double[] residualsSingleDataset = result.getResiduals().toArray();

            if (result.getCoef().length > 0) {
                final double intercept = result.getCoef()[0];

                for (int i2 = 0; i2 < residualsSingleDataset.length; i2++) {
                    residualsSingleDataset[i2] = residualsSingleDataset[i2] + intercept;
                }
            }

            for (final double _x : residualsSingleDataset) {
                if (removeNaN && isNaN(_x)) continue;
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

    private double andersonDarlingPASquare(final Node node, final List<Node> parents) {
        final double[] _f = residuals(node, parents, true, true);
//        return new AndersonDarlingTest(_f).getASquaredStar();
        return new AndersonDarlingTest(_f).getASquared();
    }

    private double entropy(final Node node, final List<Node> parents) {
        final double[] _f = residuals(node, parents, true, true);
        return maxEntApprox(_f);
    }

    private double pValue(final Node node, final List<Node> parents) {
        final List<Double> _residuals = new ArrayList<>();

        final Node target = getVariable(this.variables, node.getName());
        final List<Node> regressors = new ArrayList<>();

        for (final Node _regressor : parents) {
            final Node variable = getVariable(this.variables, _regressor.getName());
            regressors.add(variable);
        }

        DATASET:
        for (int m = 0; m < this.dataSets.size(); m++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final DataSet dataSet = this.dataSets.get(m);

            final int targetCol = dataSet.getColumn(target);

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                if (isNaN(dataSet.getDouble(i, targetCol))) {
                    continue DATASET;
                }
            }

            for (int g = 0; g < regressors.size(); g++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final int regressorCol = dataSet.getColumn(regressors.get(g));

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    if (isNaN(dataSet.getDouble(i, regressorCol))) {
                        continue DATASET;
                    }
                }
            }

            final RegressionResult result = getRegressions().get(m).regress(target, regressors);
            final Vector residualsSingleDataset = result.getResiduals();

            for (int h = 0; h < residualsSingleDataset.size(); h++) {
                if (isNaN(residualsSingleDataset.get(h))) {
                    continue DATASET;
                }
            }

            final Vector _residualsSingleDataset = new Vector(residualsSingleDataset.toArray());

            for (int k = 0; k < _residualsSingleDataset.size(); k++) {
                _residuals.add(_residualsSingleDataset.get(k));
            }
        }

        final double[] _f = new double[_residuals.size()];

        for (int k = 0; k < _residuals.size(); k++) {
            _f[k] = _residuals.get(k);
        }

        double p = new AndersonDarlingTest(_f).getP();

        if (p > 1.0 || isNaN(p)) p = 1.0;

        return p;
    }

    private Graph getCPDAG() {
        return this.CPDAG;
    }

    private Node getVariable(final List<Node> variables, final String name) {
        for (final Node node : variables) {
            if (name.equals(node.getName())) {
                return node;
            }
        }

        return null;
    }

    private Graph resolveEdgeConditional(final Graph graph) {
        setDataSets(this.dataSets);

        final Set<Edge> edgeList1 = graph.getEdges();
//        Collections.shuffle(edgeList1);

        for (final Edge adj : edgeList1) {
            final Node x = adj.getNode1();
            final Node y = adj.getNode2();

            resolveEdgeConditional(graph, x, y);
        }

        return graph;
    }

    Matrix _data;

    private void resolveEdgeConditional(final Graph graph, final Node x, final Node y) {
        if (this._data == null) {
            this._data = DataUtils.centerData(this.matrices.get(0));
        }
        final int xIndex = this.dataSets.get(0).getColumn(this.dataSets.get(0).getVariable(x.getName()));
        final int yIndex = this.dataSets.get(0).getColumn(this.dataSets.get(0).getVariable(y.getName()));
        final double[] xCol = this._data.getColumn(xIndex).toArray();
        final double[] yCol = this._data.getColumn(yIndex).toArray();
        final int N = xCol.length;

        final double[][] yCols = new double[1][N];
        yCols[0] = yCol;

        final double[][] xCols = new double[1][N];
        xCols[0] = xCol;

        final double[][] empty = new double[0][N];

        final double[] resX = conditionalResiduals(xCol, empty);
        final double[] resY = conditionalResiduals(yCol, empty);
        final double[] resXY = conditionalResiduals(xCol, yCols);
        final double[] resYX = conditionalResiduals(yCol, xCols);

        final double ngX = new AndersonDarlingTest(xCol).getASquared();
        final double ngY = new AndersonDarlingTest(yCol).getASquared();

        graph.removeEdges(x, y);

        final double sdX = sd(resX);
        final double sdXY = sd(resXY);
        final double sdY = sd(resY);
        final double sdYX = sd(resYX);

        final double abs1 = abs(sdX - sdXY);
        final double abs2 = abs(sdY - sdYX);

        if (abs(abs1 - abs2) < this.epsilon) {
            System.out.println("Orienting by non-Gaussianity " + abs(abs1 - abs2) + " epsilon = " + this.epsilon);
            System.out.println(x + "===" + y);
            final double v = resolveOneEdgeMaxR3b(xCol, yCol);

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

    private double[] conditionalResiduals(final double[] x, final double[][] y) {
        final int N = x.length;
        final double[] residuals = new double[N];

        double _h = 1.0;

        for (int i = 0; i < y.length; i++) {
            _h *= h(y[i]);
        }

        _h = (y.length == 0) ? 1.0 : pow(_h, 1.0 / (y.length));

        for (int i = 0; i < N; i++) {
            final double xi = x[i];

            double sum = 0.0;
            double kTot = 0.0;

            for (int j = 0; j < N; j++) {
                final double d = distance(y, i, j);
                final double k = kernel(d / _h) / _h;
                if (k < 1e-5) continue;
                final double xj = x[j];
                sum += k * xj;
                kTot += k;
            }

            residuals[i] = xi - (sum / kTot);
        }

        return residuals;
    }

    private double h(final double[] xCol) {
//        % optimal bandwidth suggested by Bowman and Azzalini (1997) p.31 (rks code Matlab)
//        h *= median(abs(x-median(x)))/0.6745*(4/3/r.h)^0.2, geometric mean across variables.

        final double[] g = new double[xCol.length];
        final double median = median(xCol);
        for (int j = 0; j < xCol.length; j++) g[j] = abs(xCol[j] - median);
        return median(g) / 0.6745 * pow((4.0 / 3.0) / xCol.length, 0.2);
//        return median(g) * pow((4.0 / 3.0) / xCol.length, 0.2);
//        return sd(xCol) * pow((4.0 / 3.0) / xCol.length, 0.2);
    }

    public double kernel(final double z) {
        return kernel1(z);
    }

    private final double SQRT = sqrt(2. * PI);

    // Gaussian
    public double kernel1(final double z) {
        return exp(-(z * z) / 2.) / this.SQRT; //(sqrt(2. * PI));
    }

    // Uniform
    public double kernel2(final double z) {
        if (abs(z) > 1) return 0;
        else return .5;
    }

    // Triangular
    public double kernel3(final double z) {
        if (abs(z) > 1) return 0;
        else return 1 - abs(z);
    }

    // Epanechnikov
    public double kernel4(final double z) {
        if (abs(z) > 1) return 0;
        else return (3. / 4.) * (1. - z * z);
    }

    // Quartic
    public double kernel5(final double z) {
        if (abs(z) > 1) return 0;
        else return 15. / 16. * pow(1. - z * z, 2.);
    }

    // Triweight
    public double kernel6(final double z) {
        if (abs(z) > 1) return 0;
        else return 35. / 32. * pow(1. - z * z, 3.);
    }

    // Tricube
    public double kernel7(final double z) {
        if (abs(z) > 1) return 0;
        else return 70. / 81. * pow(1. - z * z * z, 3.);
    }

    // Cosine
    public double kernel8(final double z) {
        if (abs(z) > 1) return 0;
        else return (PI / 4.) * cos((PI / 2.) * z);
    }

    private double distance(final double[][] yCols, final int i, final int j) {
        double sum = 0.0;

        for (int m = 0; m < yCols.length; m++) {
            final double d = yCols[m][i] - yCols[m][j];
            sum += d * d;
        }

        return sqrt(sum);
    }


    private double resolveOneEdgeMaxR3(final double[] x, final double[] y) {
        TetradLogger.getInstance().log("info", "\nEDGE " + x + " --- " + y);

        final OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        final double[][] _x = new double[1][];
        _x[0] = x;
        final double[][] _y = new double[1][];
        _y[0] = y;

        regression.newSampleData(x, transpose(_y));
        final double[] rXY = regression.estimateResiduals();

        regression.newSampleData(y, transpose(_x));
        final double[] rYX = regression.estimateResiduals();

        final double xPlus = new AndersonDarlingTest(rXY).getASquared();
        final double xMinus = new AndersonDarlingTest(x).getASquared();
        final double yPlus = new AndersonDarlingTest(rYX).getASquared();
        final double yMinus = new AndersonDarlingTest(y).getASquared();

        final double deltaX = xPlus - xMinus;
        final double deltaY = yPlus - yMinus;

        return deltaX - deltaY;
    }

    private double resolveOneEdgeMaxR3b(final double[] x, final double[] y) {
        TetradLogger.getInstance().log("info", "\nEDGE " + x + " --- " + y);

        final int N = x.length;

        final double[][] yCols = new double[1][N];
        yCols[0] = y;

        final double[][] xCols = new double[1][N];
        xCols[0] = x;

        final double[][] empty = new double[0][N];

        final double[] rX = conditionalResiduals(x, empty);
        final double[] rY = conditionalResiduals(y, empty);
        final double[] rXY = conditionalResiduals(x, yCols);
        final double[] rYX = conditionalResiduals(y, xCols);

        final double xPlus = new AndersonDarlingTest(rXY).getASquared();
        final double xMinus = new AndersonDarlingTest(rX).getASquared();
        final double yPlus = new AndersonDarlingTest(rYX).getASquared();
        final double yMinus = new AndersonDarlingTest(rY).getASquared();

        final double deltaX = xPlus - xMinus;
        final double deltaY = yPlus - yMinus;

        return deltaX - deltaY;
    }

}



