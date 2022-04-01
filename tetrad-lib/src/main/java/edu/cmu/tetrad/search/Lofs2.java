///////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.Vector;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

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
    private boolean orientStrongerDirection;
    private boolean r2Orient2Cycles = true;

    private Lofs.Score score = Lofs.Score.andersonDarling;
    private double epsilon = 1.0;
    private IKnowledge knowledge = new Knowledge2();
    private Rule rule = Rule.R1;
    private double selfLoopStrength;

    //===============================CONSTRUCTOR============================//

    public Lofs2(Graph CPDAG, List<DataSet> dataSets)
            throws IllegalArgumentException {

        if (dataSets == null) {
            throw new IllegalArgumentException("Data set must be specified.");
        }

        if (CPDAG == null) {
            throw new IllegalArgumentException("CPDAG must be specified.");
        }

        this.CPDAG = CPDAG;
        this.variables = dataSets.get(0).getVariables();

        List<DataSet> dataSets2 = new ArrayList<>();

        for (DataSet set : dataSets) {
            DataSet dataSet = new BoxDataSet(new DoubleDataBox(set.getDoubleData().toArray()), this.variables);
            dataSets2.add(dataSet);
        }

        this.dataSets = dataSets2;
    }

    //==========================PUBLIC=========================================//

    public void setRule(Rule rule) {
        this.rule = rule;
    }

    public double getSelfLoopStrength() {
        return this.selfLoopStrength;
    }

    public void setSelfLoopStrength(double selfLoopStrength) {
        this.selfLoopStrength = selfLoopStrength;
    }

    // orientStrongerDirection list of past and present rules.
    public enum Rule {
        IGCI, R1TimeLag, R1, R2, R3, Tanh, EB, Skew, SkewE, RSkew, RSkewE,
        Patel, Patel25, Patel50, Patel75, Patel90, FastICA, RC
    }

    public Graph orient() {

        Graph skeleton = GraphUtils.undirectedGraph(getCPDAG());
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
            FastIca fastIca = new FastIca(this.dataSets.get(0).getDoubleData(),
                    this.dataSets.get(0).getNumColumns());
            FastIca.IcaResult result = fastIca.findComponents();
            System.out.println(result.getW());
            return new EdgeListGraph();
        }

        return graph;
    }

    public double getAlpha() {
        return this.alpha;
    }

    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Alpha is in range [0, 1]");
        }

        this.alpha = alpha;
    }

    public boolean isOrientStrongerDirection() {
        return this.orientStrongerDirection;
    }

    public void setOrientStrongerDirection(boolean orientStrongerDirection) {
        this.orientStrongerDirection = orientStrongerDirection;
    }

    public void setR2Orient2Cycles(boolean r2Orient2Cycles) {
        this.r2Orient2Cycles = r2Orient2Cycles;
    }

    public boolean isR2Orient2Cycles() {
        return this.r2Orient2Cycles;
    }

    public Lofs.Score getScore() {
        return this.score;
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
            List<Regression> regressions = new ArrayList<>();
            this.variables = this.dataSets.get(0).getVariables();

            for (DataSet dataSet : this.dataSets) {
                regressions.add(new RegressionDataset(dataSet));
            }

            this.regressions = regressions;
        }

        return this.regressions;
    }

    private void setDataSets(List<DataSet> dataSets) {
        this.dataSets = dataSets;

        this.matrices = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            this.matrices.add(dataSet.getDoubleData());
        }
    }

    private void ruleR1TimeLag(Graph skeleton, Graph graph) {
        List<DataSet> timeSeriesDataSets = new ArrayList<>();
        IKnowledge knowledge = null;
        List<Node> dataNodes = null;

        for (DataSet dataModel : this.dataSets) {
            if (dataModel == null) {
                throw new IllegalArgumentException("Dataset is not supplied.");
            }

            DataSet lags = TimeSeriesUtils.createLagData(dataModel, 1);
            if (dataModel.getName() != null) {
                lags.setName(dataModel.getName());
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
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

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
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            String _node = node.getName();

            Node node0 = laggedSkeleton.getNode(_node + ":0");
            Node node1 = laggedSkeleton.getNode(_node + ":1");

            laggedSkeleton.addUndirectedEdge(node0, node1);
        }

        Lofs2 lofs = new Lofs2(laggedSkeleton, timeSeriesDataSets);
        lofs.setKnowledge(knowledge);
        lofs.setRule(Rule.R1);
        Graph _graph = lofs.orient();

        graph.removeEdges(new ArrayList<>(graph.getEdges()));

        for (Edge edge : _graph.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

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
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            SortedMap<Double, String> scoreReports = new TreeMap<>();

            List<Node> adj = new ArrayList<>();

            for (Node _node : skeleton.getAdjacentNodes(node)) {
                if (this.knowledge.isForbidden(_node.getName(), node.getName())) {
                    continue;
                }

                adj.add(_node);
            }

            DepthChoiceGenerator gen = new DepthChoiceGenerator(adj.size(), adj.size());
            int[] choice;
            double maxScore = Double.NEGATIVE_INFINITY;
            List<Node> parents = null;

            while ((choice = gen.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                List<Node> _parents = GraphUtils.asList(choice, adj);

                double score = score(node, _parents);
                scoreReports.put(-score, _parents.toString());

                if (score > maxScore) {
                    maxScore = score;
                    parents = _parents;
                }
            }

            for (double score : scoreReports.keySet()) {
                TetradLogger.getInstance().log("score", "For " + node + " parents = " + scoreReports.get(score) + " score = " + -score);
            }

            TetradLogger.getInstance().log("score", "");

            if (parents == null) {
                continue;
            }

            for (Node _node : adj) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (parents.contains(_node)) {
                    Edge parentEdge = Edges.directedEdge(_node, node);

                    if (!graph.containsEdge(parentEdge)) {
                        graph.addEdge(parentEdge);
                    }
                }
            }
        }

        for (Edge edge : skeleton.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

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
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

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

        SortedMap<Double, String> scoreReports = new TreeMap<>();

        List<Node> neighborsx = new ArrayList<>();

        for (Node _node : graph.getAdjacentNodes(x)) {
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

        DepthChoiceGenerator genx = new DepthChoiceGenerator(neighborsx.size(), neighborsx.size());
        int[] choicex;

        while ((choicex = genx.next()) != null) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> condxMinus = GraphUtils.asList(choicex, neighborsx);

            if (condxMinus.contains(y)) continue;

            List<Node> condxPlus = new ArrayList<>(condxMinus);

            condxPlus.add(y);

            double xPlus = score(x, condxPlus);
            double xMinus = score(x, condxMinus);

            double p = pValue(x, condxPlus);

            if (p > this.alpha) {
                continue;
            }

            double p2 = pValue(x, condxMinus);

            if (p2 > this.alpha) {
                continue;
            }

            List<Node> neighborsy = new ArrayList<>();

            for (Node _node : graph.getAdjacentNodes(y)) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (!this.knowledge.isForbidden(_node.getName(), y.getName())) {
                    neighborsy.add(_node);
                }
            }

            DepthChoiceGenerator geny = new DepthChoiceGenerator(neighborsy.size(), neighborsy.size());
            int[] choicey;

            while ((choicey = geny.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                List<Node> condyMinus = GraphUtils.asList(choicey, neighborsy);

                if (condyMinus.contains(x)) continue;

                List<Node> condyPlus = new ArrayList<>(condyMinus);
                condyPlus.add(x);

                double yPlus = score(y, condyPlus);
                double yMinus = score(y, condyMinus);

                boolean forbiddenLeft = this.knowledge.isForbidden(y.getName(), x.getName());
                boolean forbiddenRight = this.knowledge.isForbidden(x.getName(), y.getName());

                final double delta = 0.0;

                if (strong) {
                    if (yPlus <= xPlus + delta && xMinus <= yMinus + delta) {
                        double score = combinedScore(xPlus, yMinus);

                        if ((yPlus <= yMinus + delta && xMinus <= xPlus + delta) || forbiddenRight) {

                            String s = "\nStrong " + y + "->" + x + " " + score +
                                    "\n   Parents(" + x + ") = " + condxMinus +
                                    "\n   Parents(" + y + ") = " + condyMinus;

                            scoreReports.put(-score, s);

                            if (score > max) {
                                max = score;
                                left = true;
                                right = false;
                            }
                        } else {

                            String s = "\nNo directed edge " + x + "--" + y + " " + score +
                                    "\n   Parents(" + x + ") = " + condxMinus +
                                    "\n   Parents(" + y + ") = " + condyMinus;

                            scoreReports.put(-score, s);
                        }
                    } else if ((xPlus <= yPlus + delta && yMinus <= xMinus + delta) || forbiddenLeft) {
                        double score = combinedScore(yPlus, xMinus);

                        if (yMinus <= yPlus + delta && xPlus <= xMinus + delta) {

                            String s = "\nStrong " + x + "->" + y + " " + score +
                                    "\n   Parents(" + x + ") = " + condxMinus +
                                    "\n   Parents(" + y + ") = " + condyMinus;

                            scoreReports.put(-score, s);

                            if (score > max) {
                                max = score;
                                left = false;
                                right = true;
                            }
                        } else {

                            String s = "\nNo directed edge " + x + "--" + y + " " + score +
                                    "\n   Parents(" + x + ") = " + condxMinus +
                                    "\n   Parents(" + y + ") = " + condyMinus;

                            scoreReports.put(-score, s);
                        }
                    } else if (yPlus <= xPlus + delta && yMinus <= xMinus + delta) {
                        double score = combinedScore(yPlus, xMinus);

                        String s = "\nNo directed edge " + x + "--" + y + " " + score +
                                "\n   Parents(" + x + ") = " + condxMinus +
                                "\n   Parents(" + y + ") = " + condyMinus;

                        scoreReports.put(-score, s);
                    } else if (xPlus <= yPlus + delta && xMinus <= yMinus + delta) {
                        double score = combinedScore(yPlus, xMinus);

                        String s = "\nNo directed edge " + x + "--" + y + " " + score +
                                "\n   Parents(" + x + ") = " + condxMinus +
                                "\n   Parents(" + y + ") = " + condyMinus;

                        scoreReports.put(-score, s);
                    }
                } else {
                    if ((yPlus <= xPlus + delta && xMinus <= yMinus + delta) || forbiddenRight) {
                        double score = combinedScore(xPlus, yMinus);

                        String s = "\nWeak " + y + "->" + x + " " + score +
                                "\n   Parents(" + x + ") = " + condxMinus +
                                "\n   Parents(" + y + ") = " + condyMinus;

                        scoreReports.put(-score, s);

                        if (score > max) {
                            max = score;
                            left = true;
                            right = false;
                        }
                    } else if ((xPlus <= yPlus + delta && yMinus <= xMinus + delta) || forbiddenLeft) {
                        double score = combinedScore(yPlus, xMinus);

                        String s = "\nWeak " + x + "->" + y + " " + score +
                                "\n   Parents(" + x + ") = " + condxMinus +
                                "\n   Parents(" + y + ") = " + condyMinus;

                        scoreReports.put(-score, s);

                        if (score > max) {
                            max = score;
                            left = false;
                            right = true;
                        }
                    } else if (yPlus <= xPlus + delta && yMinus <= xMinus + delta) {
                        double score = combinedScore(yPlus, xMinus);

                        String s = "\nNo directed edge " + x + "--" + y + " " + score +
                                "\n   Parents(" + x + ") = " + condxMinus +
                                "\n   Parents(" + y + ") = " + condyMinus;

                        scoreReports.put(-score, s);
                    } else if (xPlus <= yPlus + delta && xMinus <= yMinus + delta) {
                        double score = combinedScore(yPlus, xMinus);

                        String s = "\nNo directed edge " + x + "--" + y + " " + score +
                                "\n   Parents(" + x + ") = " + condxMinus +
                                "\n   Parents(" + y + ") = " + condyMinus;

                        scoreReports.put(-score, s);
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


    private void ruleR3(Graph graph) {
        List<DataSet> standardized = DataUtils.standardizeData(this.dataSets);
        setDataSets(standardized);

        Set<Edge> edgeList1 = graph.getEdges();

        for (Edge adj : edgeList1) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node x = adj.getNode1();
            Node y = adj.getNode2();

            resolveOneEdgeMaxR3(graph, x, y);
        }

    }

    private void resolveOneEdgeMaxR3(Graph graph, Node x, Node y) {
        String xname = x.getName();
        String yname = y.getName();

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

        List<Node> condxMinus = Collections.emptyList();
        List<Node> condxPlus = Collections.singletonList(y);
        List<Node> condyMinus = Collections.emptyList();
        List<Node> condyPlus = Collections.singletonList(x);

        double xPlus = score(x, condxPlus);
        double xMinus = score(x, condxMinus);

        double yPlus = score(y, condyPlus);
        double yMinus = score(y, condyMinus);

//        if (!(xPlus > 0.8 && xMinus > 0.8 && yPlus > 0.8 && yMinus > 0.8)) return;

        double deltaX = xPlus - xMinus;
        double deltaY = yPlus - yMinus;

        graph.removeEdges(x, y);
//        double epsilon = 0;

        if (deltaY > deltaX) {
            graph.addDirectedEdge(x, y);
        } else {
            graph.addDirectedEdge(y, x);
        }
    }

    private double[] col;

    // rowIndex is for the W matrix, not for the data.
    public double scoreRow(int rowIndex, Matrix data, List<List<Integer>> rows, List<List<Double>> parameters) {
        if (this.col == null) {
            this.col = new double[data.rows()];
        }

        List<Integer> cols = rows.get(rowIndex);

        for (int i = 0; i < data.rows(); i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            double d = 0.0;

            for (int j = 0; j < cols.size(); j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                int _j = cols.get(j);
                double coef = parameters.get(rowIndex).get(j);
                double value = data.get(i, _j);
                d += coef * value;
            }

            // Add in the diagonal, assumed to consist entirely of 1's, indicating no self loop.
            d += (1.0 - getSelfLoopStrength()) * data.get(i, rowIndex);

            this.col[i] = d;
        }

        return score(this.col);
    }

    private Graph entropyBased(Graph graph) {
        DataSet dataSet = DataUtils.concatenate(this.dataSets);
        dataSet = DataUtils.standardizeData(dataSet);
        Graph _graph = new EdgeListGraph(graph.getNodes());

        for (Edge edge : graph.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node x = edge.getNode1();
            Node y = edge.getNode2();

            Node _x = dataSet.getVariable(x.getName());
            Node _y = dataSet.getVariable(y.getName());

            List<double[]> ret = extractData(dataSet, _x, _y);

            double[] xData = ret.get(0);
            double[] yData = ret.get(1);

            double[] d = new double[xData.length];
            double[] e = new double[xData.length];

            double cov = covariance(xData, yData);

            for (int i = 0; i < xData.length; i++) {
                d[i] = yData[i] - cov * xData[i];  // y regressed on x
                e[i] = xData[i] - cov * yData[i];  // x regressed on y
            }

            double R = -maxEntApprox(xData) - maxEntApprox(d)
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
        double[][] data = dataSet.getDoubleData().transpose().toArray();
        Graph _graph = new EdgeListGraph(graph.getNodes());
        List<Node> nodes = dataSet.getVariables();
        Map<Node, Integer> nodesHash = new HashMap<>();

        for (int i = 0; i < nodes.size(); i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            nodesHash.put(nodes.get(i), i);
        }

        for (Edge edge : graph.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node x = edge.getNode1();
            Node y = edge.getNode2();

            double sumX = 0.0;
            int countX = 0;

            double[] xData = data[nodesHash.get(edge.getNode1())];
            double[] yData = data[nodesHash.get(edge.getNode2())];

            for (int i = 0; i < xData.length; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                double x0 = xData[i];
                double y0 = yData[i];

                double termX = (x0 * tanh(y0) - tanh(x0) * y0);

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
        DataSet dataSet = DataUtils.concatenate(this.dataSets);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());
        dataSet = DataUtils.standardizeData(dataSet);
        double[][] data = dataSet.getDoubleData().transpose().toArray();
        Graph _graph = new EdgeListGraph(graph.getNodes());
        List<Node> nodes = dataSet.getVariables();
        Map<Node, Integer> nodesHash = new HashMap<>();

        for (int i = 0; i < nodes.size(); i++) {
            nodesHash.put(nodes.get(i), i);
        }

        for (Edge edge : graph.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node x = edge.getNode1();
            Node y = edge.getNode2();

            double sumX = 0.0;
            int countX = 0;

            int _i = nodesHash.get(edge.getNode1());
            int _j = nodesHash.get(edge.getNode2());

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
        // DataUtils.standardizeData(dataSet));
        List<DataSet> _dataSets = new ArrayList<>(this.dataSets);
        DataSet dataSet = DataUtils.concatenate(_dataSets);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());
        dataSet = DataUtils.standardizeData(dataSet);
        double[][] data = dataSet.getDoubleData().transpose().toArray();
        List<Node> nodes = dataSet.getVariables();
        Map<Node, Integer> nodesHash = new HashMap<>();

        for (int i = 0; i < nodes.size(); i++) {
            nodesHash.put(nodes.get(i), i);
        }

        for (Edge edge : graph.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node x = edge.getNode1();
            Node y = edge.getNode2();

            double[] xData = data[nodesHash.get(edge.getNode1())];
            double[] yData = data[nodesHash.get(edge.getNode2())];

            if (empirical) {
                xData = correctSkewnesses(xData);
                yData = correctSkewnesses(yData);
            }

            double[] xx = new double[xData.length];
            double[] yy = new double[yData.length];

            for (int i = 0; i < xData.length; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                double xi = xData[i];
                double yi = yData[i];

                double s1 = g(xi) * yi;
                double s2 = xi * g(yi);

                xx[i] = s1;
                yy[i] = s2;
            }

            double mxx = mean(xx);
            double myy = mean(yy);

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

    private double g(double x) {
        return log(cosh(Math.max(x, 0)));
    }

    // cutoff is NaN if no thresholding is to be done, otherwise a threshold between 0 and 1.
    private Graph patelTauOrientation(Graph graph, double cutoff) {
        List<DataSet> centered = DataUtils.center(this.dataSets);
        DataSet concat = DataUtils.concatenate(centered);
        DataSet dataSet = DataUtils.standardizeData(concat);

        Graph _graph = new EdgeListGraph(graph.getNodes());

        for (Edge edge : graph.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node x = edge.getNode1();
            Node y = edge.getNode2();

            Node _x = dataSet.getVariable(x.getName());
            Node _y = dataSet.getVariable(y.getName());

            List<double[]> ret = prepareData(dataSet, _x, _y);
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

        final double XT = .25; // Cancels out, don't know why this is here.

        double[] d1b = new double[d1in.length];

        for (int i = 0; i < d1b.length; i++) {
            double y1 = (d1in[i] - grotMIN) / (grotMAX - grotMIN);
            double y2 = Math.min(y1, 1.0);
            double y3 = Math.max(y2, 0.0);
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

        double[] d2b = new double[d2in.length];

        for (int i = 0; i < d2b.length; i++) {
            double y1 = (d2in[i] - grotMIN) / (grotMAX - grotMIN);
            double y2 = Math.min(y1, 1.0);
            double y3 = Math.max(y2, 0.0);
            d2b[i] = y3;
        }

        if (!isNaN(cutoff)) {
            for (int i = 0; i < d2b.length; i++) {
                if (d2b[i] > cutoff) d2b[i] = 1.0;
                else d2b[i] = 0.0;
            }
        }

        double theta1 = dotProduct(d1b, d2b) / XT;
        double theta2 = dotProduct(d1b, minus(d2b)) / XT;
        double theta3 = dotProduct(d2b, minus(d1b)) / XT;
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

    private double[] minus(double[] x) {
        double[] y = new double[x.length];

        for (int i = 0; i < x.length; i++) {
            y[i] = 1 - x[i];
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

        double[] xData = _data[xIndex];
        double[] yData = _data[yIndex];

        List<Double> xValues = new ArrayList<>();
        List<Double> yValues = new ArrayList<>();

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

        List<double[]> ret = new ArrayList<>();
        ret.add(xData);
        ret.add(yData);

        return ret;
    }

    private double[] correctSkewnesses(double[] data) {
        double skewness = skewness(data);
        double[] data2 = new double[data.length];
        for (int i = 0; i < data.length; i++) data2[i] = data[i] * signum(skewness);
        return data2;
    }

    private List<double[]> prepareData(DataSet concatData, Node _x, Node _y) {
        int xIndex = concatData.getColumn(_x);
        int yIndex = concatData.getColumn(_y);

        double[] xData = concatData.getDoubleData().getColumn(xIndex).toArray();
        double[] yData = concatData.getDoubleData().getColumn(yIndex).toArray();

        List<Double> xValues = new ArrayList<>();
        List<Double> yValues = new ArrayList<>();

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

        List<double[]> ret = new ArrayList<>();
        ret.add(xData);
        ret.add(yData);

        return ret;
    }

    private double regressionCoef(double[] xValues, double[] yValues) {
        List<Node> v = new ArrayList<>();
        v.add(new GraphNode("x"));
        v.add(new GraphNode("y"));

        Matrix bothData = new Matrix(xValues.length, 2);

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

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * @return the probability associated with the most recently computed independence test.
     */
    public double getPValue(double fisherZ) {
        return 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(fisherZ)));
    }

    private Graph igci(Graph graph) {
        if (this.dataSets.size() > 1) throw new IllegalArgumentException("Expecting exactly one data set for IGCI.");

        DataSet dataSet = this.dataSets.get(0);
        Matrix matrix = dataSet.getDoubleData();

        Graph _graph = new EdgeListGraph(graph.getNodes());

        for (Edge edge : graph.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node x = edge.getNode1();
            Node y = edge.getNode2();

            Node _x = dataSet.getVariable(x.getName());
            Node _y = dataSet.getVariable(y.getName());

            int xIndex = dataSet.getVariables().indexOf(_x);
            int yIndex = dataSet.getVariables().indexOf(_y);

            double[] xCol = matrix.getColumn(xIndex).toArray();
            double[] yCol = matrix.getColumn(yIndex).toArray();

            double f = igci(xCol, yCol);

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

    private double igci(double[] x, double[] y) {
        int m = x.length;

        if (m != y.length) {
            throw new IllegalArgumentException("Vectors must be the same length.");
        }

        // uniform reference measure

        double meanx = mean(x);
        double stdx = sd(x);
        double meany = mean(y);
        double stdy = sd(y);

        // Gaussian reference measure
        for (int i = 0; i < x.length; i++) {
            x[i] = (x[i] - meanx) / stdx;
            y[i] = (y[i] - meany) / stdy;
        }


        double f;

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
                if (isNaN(delta)) {
                    throw new IllegalArgumentException();
                }

                hy = hy + log(abs(delta));
            }
        }

        hy = hy / (n2 - 1) + psi(n2) - psi(1);

        f = hy - hx;

        return f;

    }

    private double[] removeNaN(double[] data) {
        List<Double> _leaveOutMissing = new ArrayList<>();

        for (double datum : data) {
            if (!isNaN(datum)) {
                _leaveOutMissing.add(datum);
            }
        }

        double[] _data = new double[_leaveOutMissing.size()];

        for (int i = 0; i < _leaveOutMissing.size(); i++) _data[i] = _leaveOutMissing.get(i);

        return _data;
    }


    // digamma

    double psi(double x) {
        double result = 0;
        double xx;
        double xx2;
        double xx4;
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


    private double combinedScore(double score1, double score2) {
        return score1 + score2;
    }

    private double score(Node y, List<Node> parents) {
        if (this.score == Lofs.Score.andersonDarling) {
            return andersonDarlingPASquare(y, parents);
        } else if (this.score == Lofs.Score.kurtosis) {
            return abs(kurtosis(residuals(y, parents, true)));
        } else if (this.score == Lofs.Score.entropy) {
            return entropy(y, parents);
        } else if (this.score == Lofs.Score.skew) {
            return abs(skewness(residuals(y, parents, true)));
        } else if (this.score == Lofs.Score.fifthMoment) {
            return abs(standardizedFifthMoment(residuals(y, parents, true)));
        } else if (this.score == Lofs.Score.absoluteValue) {
            return meanAbsolute(y, parents);
        } else if (this.score == Lofs.Score.exp) {
            return expScoreUnstandardized(y, parents);
        } else if (this.score == Lofs.Score.other) {
            double[] _f = residuals(y, parents, true);
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

    private double meanAbsolute(Node node, List<Node> parents) {
        double[] _f = residuals(node, parents, false);

        return StatUtils.meanAbsolute(_f);
    }

    private double expScoreUnstandardized(Node node, List<Node> parents) {
        double[] _f = residuals(node, parents, false);

        return expScoreUnstandardized(_f);
    }

    private double expScoreUnstandardized(double[] _f) {
        double sum = 0.0;

        for (double v : _f) {
            sum += exp(v);
        }

        double expected = sum / _f.length;
        return -abs(log(expected));
    }

    private double logCoshScore(Node node, List<Node> parents) {
        double[] _f = residuals(node, parents, true);
        return StatUtils.logCoshScore(_f);
    }

    private double[] residuals(Node node, List<Node> parents, boolean standardize) {
        List<Double> _residuals = new ArrayList<>();

        Node target = getVariable(this.variables, node.getName());
        List<Node> regressors = new ArrayList<>();

        for (Node _regressor : parents) {
            Node variable = getVariable(this.variables, _regressor.getName());
            regressors.add(variable);
        }

        DATASET:
        for (int m = 0; m < this.dataSets.size(); m++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            DataSet dataSet = this.dataSets.get(m);

            int targetCol = dataSet.getColumn(target);

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                if (isNaN(dataSet.getDouble(i, targetCol))) {
                    continue DATASET;
                }
            }

            for (Node regressor : regressors) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                int regressorCol = dataSet.getColumn(regressor);

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    if (isNaN(dataSet.getDouble(i, regressorCol))) {
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
                if (isNaN(_x)) continue;
                _residuals.add(_x);
            }
        }

        double[] _f = new double[_residuals.size()];

        for (int k = 0; k < _residuals.size(); k++) {
            _f[k] = _residuals.get(k);
        }

        if (standardize) {
            _f = DataUtils.standardizeData(_f);
        }

        return _f;
    }

    private double andersonDarlingPASquare(Node node, List<Node> parents) {
        double[] _f = residuals(node, parents, true);
//        return new AndersonDarlingTest(_f).getASquaredStar();
        return new AndersonDarlingTest(_f).getASquared();
    }

    private double entropy(Node node, List<Node> parents) {
        double[] _f = residuals(node, parents, true);
        return maxEntApprox(_f);
    }

    private double pValue(Node node, List<Node> parents) {
        List<Double> _residuals = new ArrayList<>();

        Node target = getVariable(this.variables, node.getName());
        List<Node> regressors = new ArrayList<>();

        for (Node _regressor : parents) {
            Node variable = getVariable(this.variables, _regressor.getName());
            regressors.add(variable);
        }

        DATASET:
        for (int m = 0; m < this.dataSets.size(); m++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            DataSet dataSet = this.dataSets.get(m);

            int targetCol = dataSet.getColumn(target);

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                if (isNaN(dataSet.getDouble(i, targetCol))) {
                    continue DATASET;
                }
            }

            for (Node regressor : regressors) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                int regressorCol = dataSet.getColumn(regressor);

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    if (isNaN(dataSet.getDouble(i, regressorCol))) {
                        continue DATASET;
                    }
                }
            }

            RegressionResult result = getRegressions().get(m).regress(target, regressors);
            Vector residualsSingleDataset = result.getResiduals();

            for (int h = 0; h < residualsSingleDataset.size(); h++) {
                if (isNaN(residualsSingleDataset.get(h))) {
                    continue DATASET;
                }
            }

            Vector _residualsSingleDataset = new Vector(residualsSingleDataset.toArray());

            for (int k = 0; k < _residualsSingleDataset.size(); k++) {
                _residuals.add(_residualsSingleDataset.get(k));
            }
        }

        double[] _f = new double[_residuals.size()];

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

    private Node getVariable(List<Node> variables, String name) {
        for (Node node : variables) {
            if (name.equals(node.getName())) {
                return node;
            }
        }

        return null;
    }

    private Graph resolveEdgeConditional(Graph graph) {
        setDataSets(this.dataSets);

        Set<Edge> edgeList1 = graph.getEdges();
//        Collections.shuffle(edgeList1);

        for (Edge adj : edgeList1) {
            Node x = adj.getNode1();
            Node y = adj.getNode2();

            resolveEdgeConditional(graph, x, y);
        }

        return graph;
    }

    Matrix _data;

    private void resolveEdgeConditional(Graph graph, Node x, Node y) {
        if (this._data == null) {
            this._data = DataUtils.centerData(this.matrices.get(0));
        }
        int xIndex = this.dataSets.get(0).getColumn(this.dataSets.get(0).getVariable(x.getName()));
        int yIndex = this.dataSets.get(0).getColumn(this.dataSets.get(0).getVariable(y.getName()));
        double[] xCol = this._data.getColumn(xIndex).toArray();
        double[] yCol = this._data.getColumn(yIndex).toArray();
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

        double sdX = sd(resX);
        double sdXY = sd(resXY);
        double sdY = sd(resY);
        double sdYX = sd(resYX);

        double abs1 = abs(sdX - sdXY);
        double abs2 = abs(sdY - sdYX);

        if (abs(abs1 - abs2) < this.epsilon) {
            System.out.println("Orienting by non-Gaussianity " + abs(abs1 - abs2) + " epsilon = " + this.epsilon);
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

        for (double[] doubles : y) {
            _h *= h(doubles);
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
    }

    public double kernel(double z) {
        return kernel1(z);
    }

    private final double SQRT = sqrt(2. * PI);

    // Gaussian
    public double kernel1(double z) {
        return exp(-(z * z) / 2.) / this.SQRT; //(sqrt(2. * PI));
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

        for (double[] yCol : yCols) {
            double d = yCol[i] - yCol[j];
            sum += d * d;
        }

        return sqrt(sum);
    }


    private double resolveOneEdgeMaxR3(double[] x, double[] y) {
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



