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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Vector;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.apache.commons.math3.util.FastMath;

import java.util.*;

import static edu.cmu.tetrad.util.MatrixUtils.transpose;
import static edu.cmu.tetrad.util.StatUtils.*;
import static java.lang.Double.isNaN;
import static org.apache.commons.math3.util.FastMath.pow;
import static org.apache.commons.math3.util.FastMath.*;

/**
 * Implements a number of methods which take a fixed graph as input and use linear, non-Gaussian methods to orient the
 * edges in the graph. where the acronym stands for linear, non-Gaussian Orientation with a Fixed graph Structure
 * (LOFS). The options for different types of scores are given in the enum Lofs.Score. The options for rules to use to
 * do the orientations are given in the enum, Lofs.Rule. Most of these are taken from the literature and can be googled,
 * though we should certainly give this reference for several of them, to which we are indebted:
 * <p>
 * Hyvärinen, A., &amp; Smith, S. M. (2013). Pairwise likelihood ratios for estimation of non-Gaussian structural
 * equation models. The Journal of Machine Learning Research, 14(1), 111-152.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see Score
 * @see Rule
 * @see Knowledge
 */
public class Lofs {
    /**
     * The graph to be oriented.
     */
    private final Graph cpdag;
    /**
     * The square root of 2 * pi.
     */
    private final double SQRT = sqrt(2. * PI);
    /**
     * The data to use to do the orientation.
     */
    Matrix _data;
    /**
     * The data to use to do the orientation.
     */
    private List<DataSet> dataSets;
    /**
     * The matrices to use to do the orientation.
     */
    private List<Matrix> matrices;
    /**
     * The alpha to use, where applicable.
     */
    private double alpha = 1.1;
    /**
     * The regressions to use to do the orientation.
     */
    private List<Regression> regressions;
    /**
     * The variables to use to do the orientation.
     */
    private List<Node> variables;
    /**
     * Whether orientation should be done in the stronger direction, where applicable.
     */
    private boolean orientStrongerDirection;
    /**
     * For R2, whether cycles should be oriented.
     */
    private boolean r2Orient2Cycles = true;
    /**
     * The (LoFS) score to use.
     */
    private Lofs.Score score = Lofs.Score.andersonDarling;
    /**
     * The self-loop strength, if applicable.
     */
    private double epsilon = 1.0;
    /**
     * The knowledge to use to do the orientation.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * The rule to use to do the orientation.
     */
    private Rule rule = Rule.R1;
    /**
     * The number of variables.
     */
    private double selfLoopStrength;
    /**
     * The number of variables.
     */
    private double[] col;

    /**
     * Constructor.
     *
     * @param graph    The graph to be oriented. Orientations for the graph will be overwritten.
     * @param dataSets A list of datasets to use to do the orientation. This may be just one dataset. If more than one
     *                 dataset are given, the data will be concatenated (pooled).
     * @throws java.lang.IllegalArgumentException if any.
     */
    public Lofs(Graph graph, List<DataSet> dataSets)
            throws IllegalArgumentException {

        if (graph == null) {
            throw new IllegalArgumentException("graph must be specified.");
        }

        if (dataSets == null) {
            throw new IllegalArgumentException("Data set must be specified.");
        }

        this.cpdag = graph;
        this.variables = dataSets.getFirst().getVariables();

        List<DataSet> dataSets2 = new ArrayList<>();

        for (DataSet set : dataSets) {
            DataSet dataSet = new BoxDataSet(new DoubleDataBox(set.getDoubleData().toArray()), this.variables);
            dataSets2.add(dataSet);
        }

        this.dataSets = dataSets2;
    }

    /**
     * Orients the graph and returns the oriented graph.
     *
     * @return The oriented graph.
     */
    public Graph orient() {

        Graph skeleton = GraphUtils.undirectedGraph(getCpdag());
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
            FastIca fastIca = new FastIca(this.dataSets.getFirst().getDoubleData(),
                    this.dataSets.getFirst().getNumColumns());
            FastIca.IcaResult result = fastIca.findComponents();
            System.out.println(result.getW());
            return new EdgeListGraph();
        }

        return graph;
    }

    /**
     * Sets the rule to use to do the orientation.
     *
     * @param rule This rule.
     * @see Rule
     */
    public void setRule(Rule rule) {
        this.rule = rule;
    }

    /**
     * Sets the (LoFS) score to use.
     *
     * @param score This score.
     * @see Score
     */
    public void setScore(Lofs.Score score) {
        if (score == null) {
            throw new NullPointerException();
        }

        this.score = score;
    }

    /**
     * Sets the self-loop strength, if applicable.
     *
     * @param selfLoopStrength This strength.
     */
    public void setSelfLoopStrength(double selfLoopStrength) {
        this.selfLoopStrength = selfLoopStrength;
    }

    /**
     * Sets the alpha to use, where applicable.
     *
     * @param alpha This alpha.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Alpha is in range [0, 1]");
        }

        this.alpha = alpha;
    }

    /**
     * Sets whether orientation should be done in the stronger direction, where applicable.
     *
     * @param orientStrongerDirection True, if so.
     */
    public void setOrientStrongerDirection(boolean orientStrongerDirection) {
        this.orientStrongerDirection = orientStrongerDirection;
    }


    /**
     * Sets for R2 whether cycles should be oriented.
     *
     * @param r2Orient2Cycles True, if so.
     */
    public void setR2Orient2Cycles(boolean r2Orient2Cycles) {
        this.r2Orient2Cycles = r2Orient2Cycles;
    }

    private List<Regression> getRegressions() {
        if (this.regressions == null) {
            List<Regression> regressions = new ArrayList<>();
            this.variables = this.dataSets.getFirst().getVariables();

            for (DataSet dataSet : this.dataSets) {
                regressions.add(new RegressionDataset(dataSet));
            }

            this.regressions = regressions;
        }

        return this.regressions;
    }

    /**
     * Sets the data sets to be used for orientation.
     *
     * @param dataSets The list of data sets to be used for orientation.
     */
    private void setDataSets(List<DataSet> dataSets) {
        this.dataSets = dataSets;

        this.matrices = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            this.matrices.add(dataSet.getDoubleData());
        }
    }

    /**
     * Apply Rule R1 Time Lag to the given skeleton and graph.
     *
     * @param skeleton The original graph skeleton.
     * @param graph    The graph to apply the rule to.
     */
    private void ruleR1TimeLag(Graph skeleton, Graph graph) {
        List<DataSet> timeSeriesDataSets = new ArrayList<>();
        Knowledge knowledge = null;
        List<Node> dataNodes = null;

        for (DataSet dataModel : this.dataSets) {
            if (dataModel == null) {
                throw new IllegalArgumentException("Dataset is not supplied.");
            }

            DataSet lags = TsUtils.createLagData(dataModel, 1);
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

        Lofs lofs = new Lofs(laggedSkeleton, timeSeriesDataSets);
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

    /**
     * Applies rule R1 to the given graph skeleton and modifies the graph accordingly.
     *
     * @param skeleton The skeleton graph.
     * @param graph    The modified graph.
     * @param nodes    The list of nodes to apply the rule on.
     */
    private void ruleR1(Graph skeleton, Graph graph, List<Node> nodes) {
        List<DataSet> centeredData = DataTransforms.center(this.dataSets);
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

            SublistGenerator gen = new SublistGenerator(adj.size(), adj.size());
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
                String message = "For " + node + " parents = " + scoreReports.get(score) + " score = " + -score;
                TetradLogger.getInstance().log(message);
            }

            TetradLogger.getInstance().log("");

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

    /**
     * Applies rule R2 to the given graph skeleton and modifies the graph accordingly.
     *
     * @param skeleton The original graph skeleton.
     * @param graph    The graph to apply the rule to.
     */
    private void ruleR2(Graph skeleton, Graph graph) {
        List<DataSet> standardized = DataTransforms.standardizeData(this.dataSets);
        setDataSets(standardized);

        Set<Edge> edgeList1 = skeleton.getEdges();

        for (Edge adj : edgeList1) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node x = adj.getNode1();
            Node y = adj.getNode2();

            if (!this.r2Orient2Cycles && isTwoCycle(graph, x, y)) {
                continue;
            }

            if (!isTwoCycle(graph, x, y) && !isUndirected(graph, x, y)) {
                continue;
            }

            resolveOneEdgeMax2(graph, x, y, !this.orientStrongerDirection);
        }
    }

    /**
     * Resolves one edge in the graph using the Maximum 2 algorithm.
     *
     * @param graph  The graph to resolve.
     * @param x      The first node of the edge.
     * @param y      The second node of the edge.
     * @param strong Indicates whether to use strong or weak restrictions.
     */
    private void resolveOneEdgeMax2(Graph graph, Node x, Node y, boolean strong) {
        TetradLogger.getInstance().log("\nEDGE " + x + " --- " + y);

        SortedMap<Double, String> scoreReports = new TreeMap<>();

        List<Node> neighborsx = new ArrayList<>();

        for (Node _node : graph.getAdjacentNodes(x)) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            if (!this.knowledge.isForbidden(_node.getName(), x.getName())) {
                neighborsx.add(_node);
            }
        }

        double max = Double.NEGATIVE_INFINITY;
        boolean left = false;
        boolean right = false;

        SublistGenerator genx = new SublistGenerator(neighborsx.size(), neighborsx.size());
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

            SublistGenerator geny = new SublistGenerator(neighborsy.size(), neighborsy.size());
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
            String message = scoreReports.get(score);
            TetradLogger.getInstance().log(message);
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

    /**
     * Applies rule R3 to the given graph.
     *
     * @param graph The graph to apply the rule to.
     */
    private void ruleR3(Graph graph) {
        List<DataSet> standardized = DataTransforms.standardizeData(this.dataSets);
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

    /**
     * This method resolves one edge in the graph based on certain conditions.
     *
     * @param graph the graph on which the operation needs to be performed
     * @param x     the first node of the edge
     * @param y     the second node of the edge
     */
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

        double deltaX = xPlus - xMinus;
        double deltaY = yPlus - yMinus;

        graph.removeEdges(x, y);

        if (deltaY > deltaX) {
            graph.addDirectedEdge(x, y);
        } else {
            graph.addDirectedEdge(y, x);
        }
    }

    /**
     * Calculates the score for a given row in a matrix using the specified parameters.
     *
     * @param rowIndex   the index of the row to score
     * @param data       the matrix containing the data
     * @param rows       the list of rows containing the column indices to score
     * @param parameters the list of parameters for each column index in the rows
     * @return the score of the row
     */
    public double scoreRow(int rowIndex, Matrix data, List<List<Integer>> rows, List<List<Double>> parameters) {
        if (this.col == null) {
            this.col = new double[data.getNumRows()];
        }

        List<Integer> cols = rows.get(rowIndex);

        for (int i = 0; i < data.getNumRows(); i++) {
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
            d += (1.0 - this.selfLoopStrength) * data.get(i, rowIndex);

            this.col[i] = d;
        }

        return score(this.col);
    }

    /**
     * Computes the entropy-based graph from the given input graph.
     *
     * @param graph The input graph.
     * @return The entropy-based graph.
     */
    private Graph entropyBased(Graph graph) {
        DataSet dataSet = DataTransforms.concatenate(this.dataSets);
        dataSet = DataTransforms.standardizeData(dataSet);
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

    /**
     * This method calculates the tanhGraph of a given graph.
     *
     * @param graph The input graph.
     * @return The tanhGraph of the input graph.
     */
    private Graph tanhGraph(Graph graph) {
        DataSet dataSet = DataTransforms.concatenate(this.dataSets);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());
        dataSet = DataTransforms.standardizeData(dataSet);
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


    /**
     * Skews the given graph based on the provided data.
     *
     * @param graph     The input graph to be skewed.
     * @param empirical A boolean flag indicating whether to perform empirical skewness correction.
     * @return The skewed graph.
     */
    private Graph skewGraph(Graph graph, boolean empirical) {
        DataSet dataSet = DataTransforms.concatenate(this.dataSets);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());
        dataSet = DataTransforms.standardizeData(dataSet);
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

    /**
     * Performs robust skew graph transformation on the given graph.
     *
     * @param graph     The original graph to transform.
     * @param empirical If true, corrects skewnesses using empirical method.
     * @return The transformed graph.
     */
    private Graph robustSkewGraph(Graph graph, boolean empirical) {
        // DataUtils.standardizeData(dataSet));
        List<DataSet> _dataSets = new ArrayList<>(this.dataSets);
        DataSet dataSet = DataTransforms.concatenate(_dataSets);
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());
        dataSet = DataTransforms.standardizeData(dataSet);
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

    /**
     * Calculates the value of function g(x).
     *
     * @param x The input value.
     * @return The result of applying the function g(x) to the input value.
     */
    private double g(double x) {
        return log(cosh(FastMath.max(x, 0)));
    }

    /**
     * This method performs Patel-Tau orientation of the edges in a graph based on a given cutoff value.
     *
     * @param graph  The input graph on which Patel-Tau orientation should be performed.
     * @param cutoff The cutoff value used to determine the presence of an edge.
     * @return The directed graph with edges oriented based on Patel-Tau test results.
     */
    private Graph patelTauOrientation(Graph graph, double cutoff) {
        List<DataSet> centered = DataTransforms.center(this.dataSets);
        DataSet concat = DataTransforms.concatenate(centered);
        DataSet dataSet = DataTransforms.standardizeData(concat);

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

    /**
     * Calculates the patelTau correlation coefficient between two input arrays.
     *
     * @param d1in   The first input array.
     * @param d2in   The second input array.
     * @param cutoff The cutoff value for binarization. Pass Double.NaN for no binarization.
     * @return The patelTau correlation coefficient.
     */
    private double patelTau(double[] d1in, double[] d2in, double cutoff) {
        double grotMIN = percentile(d1in, 10);
        double grotMAX = percentile(d1in, 90);

        final double XT = .25; // Cancels out, don't know why this is here.

        double[] d1b = new double[d1in.length];

        for (int i = 0; i < d1b.length; i++) {
            double y1 = (d1in[i] - grotMIN) / (grotMAX - grotMIN);
            double y2 = FastMath.min(y1, 1.0);
            double y3 = FastMath.max(y2, 0.0);
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
            double y2 = FastMath.min(y1, 1.0);
            double y3 = FastMath.max(y2, 0.0);
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

        double tau_12;

        if (theta2 > theta3) tau_12 = 1 - (theta1 + theta3) / (theta1 + theta2);
        else tau_12 = (theta1 + theta2) / (theta1 + theta3) - 1;

        return -tau_12;
    }

    /**
     * Calculates the dot product of two arrays.
     *
     * @param x the first array
     * @param y the second array
     * @return the dot product of the arrays
     */
    private double dotProduct(double[] x, double[] y) {
        double p = 0.0;

        for (int i = 0; i < x.length; i++) {
            p += x[i] * y[i];
        }

        return p;
    }

    /**
     * Computes the difference between 1 and each element in the input array.
     *
     * @param x the input array
     * @return an array containing the differences between 1 and each element in the input array
     */
    private double[] minus(double[] x) {
        double[] y = new double[x.length];

        for (int i = 0; i < x.length; i++) {
            y[i] = 1 - x[i];
        }

        return y;
    }

    /**
     * Returns the value at the specified percentile from the given array of values.
     *
     * @param x       the array of values
     * @param percent the percentile (0 to 100)
     * @return the value at the specified percentile
     */
    private double percentile(double[] x, double percent) {
        double[] _x = Arrays.copyOf(x, x.length);
        Arrays.sort(_x);
        return _x[(int) (x.length * (percent / 100.0))];
    }

    /**
     * Extracts the data from the given DataSet based on the specified x and y Nodes.
     *
     * @param data The DataSet containing the data.
     * @param _x   The x Node indicating the column to extract.
     * @param _y   The y Node indicating the column to extract.
     * @return A List of double arrays containing the extracted x and y data.
     */
    private List<double[]> extractData(DataSet data, Node _x, Node _y) {
        int xIndex = data.getColumnIndex(_x);
        int yIndex = data.getColumnIndex(_y);

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

    /**
     * Corrects the skewnesses of the given data array.
     *
     * @param data the data array to correct
     * @return a new array with corrected skewnesses
     */
    private double[] correctSkewnesses(double[] data) {
        double skewness = skewness(data);
        double[] data2 = new double[data.length];
        for (int i = 0; i < data.length; i++) data2[i] = data[i] * signum(skewness);
        return data2;
    }

    /**
     * Prepares the data by extracting the values corresponding to the given nodes from the provided DataSet.
     *
     * @param concatData The DataSet containing the data.
     * @param _x         The node representing the X-values.
     * @param _y         The node representing the Y-values.
     * @return A list containing the X-values and Y-values as double arrays.
     */
    private List<double[]> prepareData(DataSet concatData, Node _x, Node _y) {
        int xIndex = concatData.getColumnIndex(_x);
        int yIndex = concatData.getColumnIndex(_y);

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

    /**
     * Calculates the regression coefficient for given x and y values.
     *
     * @param xValues an array of x values
     * @param yValues an array of y values
     * @return the regression coefficient
     */
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

    /**
     * Determines if there is a two-cycle between two nodes in a graph.
     *
     * @param graph The graph to check for two-cycle.
     * @param x     The first node.
     * @param y     The second node.
     * @return true if there is a two-cycle between the nodes, false otherwise.
     */
    private boolean isTwoCycle(Graph graph, Node x, Node y) {
        List<Edge> edges = graph.getEdges(x, y);
        return edges.size() == 2;
    }

    /**
     * Determines whether there is an undirected edge between two nodes in a graph.
     *
     * @param graph the graph to check for undirected edges
     * @param x     the starting node of the edge
     * @param y     the ending node of the edge
     * @return true if there is an undirected edge between the given nodes, otherwise false
     */
    private boolean isUndirected(Graph graph, Node x, Node y) {
        List<Edge> edges = graph.getEdges(x, y);
        if (edges.size() == 1) {
            Edge edge = graph.getEdge(x, y);
            return Edges.isUndirectedEdge(edge);
        }

        return false;
    }

    /**
     * Sets the epsilon value.
     *
     * @param epsilon the new value of epsilon
     */
    public void setEpsilon(double epsilon) {
        this.epsilon = epsilon;
    }

    /**
     * Sets the knowledge for the object.
     *
     * @param knowledge the knowledge to set.
     * @throws NullPointerException if the knowledge is null.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * Calculates the p-value using Fisher's exact test. The p-value is the probability of observing a test statistic as
     * extreme as the observed value under the null hypothesis. It is used to determine the statistical significance of
     * the observed value.
     *
     * @param fisherZ the Fisher's Z value
     * @return the p-value
     */
    public double getPValue(double fisherZ) {
        return 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(fisherZ)));
    }

    /**
     * Calculates the Information-Geometric Causal Inference (IGCI) graph based on the given graph.
     *
     * @param graph the input graph
     * @return the IGCI graph
     * @throws IllegalArgumentException if there is not exactly one data set for IGCI
     */
    private Graph igci(Graph graph) {
        if (this.dataSets.size() > 1) throw new IllegalArgumentException("Expecting exactly one data set for IGCI.");

        DataSet dataSet = this.dataSets.getFirst();
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

    /**
     * Calculates the Information-Geometric Causal Inference (IGCI) between two vectors.
     *
     * @param x the first vector
     * @param y the second vector
     * @return the IGCI value
     * @throws IllegalArgumentException if the length of the vectors is different
     */
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

    /**
     * Removes any NaN (Not a Number) values from the given double array.
     *
     * @param data the array of double values
     * @return a new array with NaN values removed
     */
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


    /**
     * Calculates the psi value for a given input.
     *
     * @param x the input value
     * @return the result of the psi calculation
     */
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

    /**
     * Calculates the combined score by summing up two scores.
     *
     * @param score1 the first score
     * @param score2 the second score
     * @return the combined score
     */
    private double combinedScore(double score1, double score2) {
        return score1 + score2;
    }

    /**
     * This method calculates the score for a given Node and its parents. The score is calculated based on the specified
     * score type.
     *
     * @param y       The Node for which the score is calculated.
     * @param parents The list of parent Nodes of the given Node.
     * @return The calculated score.
     * @throws IllegalStateException If the specified score type is not supported.
     */
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

    /**
     * Calculates the score for the given column of data.
     *
     * @param col the column of data
     * @return the calculated score
     * @throws IllegalStateException if an unrecognized score type is encountered
     */
    private double score(double[] col) {
        if (this.score == Lofs.Score.andersonDarling) {
            return new AndersonDarlingTest(col).getASquaredStar();
        } else if (this.score == Lofs.Score.entropy) {
            return maxEntApprox(col);
        } else if (this.score == Lofs.Score.kurtosis) {
            col = DataTransforms.standardizeData(col);
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

    /**
     * Calculates the mean absolute value of an array of residuals for a given node and its parents.
     *
     * @param node    The node for which residuals are being calculated
     * @param parents The list of parent nodes of the given node
     * @return The mean absolute value of the residuals
     */
    private double meanAbsolute(Node node, List<Node> parents) {
        double[] _f = residuals(node, parents, false);

        return StatUtils.meanAbsolute(_f);
    }

    /**
     * Calculates the unstandardized expected score for a given node and its parents.
     *
     * @param node    The node for which to calculate the unstandardized expected score.
     * @param parents The list of parent nodes.
     * @return The unstandardized expected score for the node.
     */
    private double expScoreUnstandardized(Node node, List<Node> parents) {
        double[] _f = residuals(node, parents, false);

        return expScoreUnstandardized(_f);
    }

    /**
     * Calculates the unstandardized expected score.
     *
     * @param _f an array of doubles representing the values.
     * @return the unstandardized expected score.
     */
    private double expScoreUnstandardized(double[] _f) {
        double sum = 0.0;

        for (double v : _f) {
            sum += exp(v);
        }

        double expected = sum / _f.length;
        return -abs(log(expected));
    }

    /**
     * Calculates the log-cosh score for a given node and its parents.
     *
     * @param node    The node for which to calculate the score.
     * @param parents The list of parent nodes of the given node.
     * @return The log-cosh score for the given node and its parents.
     */
    private double logCoshScore(Node node, List<Node> parents) {
        double[] _f = residuals(node, parents, true);
        return StatUtils.logCoshScore(_f);
    }

    /**
     * Calculates the residuals for a given node and its parent nodes.
     *
     * @param node        The node for which the residuals are calculated.
     * @param parents     The parent nodes that are used as regressors.
     * @param standardize Indicates whether the residuals should be standardized.
     * @return An array of doubles representing the residuals.
     */
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

            int targetCol = dataSet.getColumnIndex(target);

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                if (isNaN(dataSet.getDouble(i, targetCol))) {
                    continue DATASET;
                }
            }

            for (Node regressor : regressors) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                int regressorCol = dataSet.getColumnIndex(regressor);

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
            _f = DataTransforms.standardizeData(_f);
        }

        return _f;
    }

    /**
     * Calculates the Anderson-Darling test statistic for the probability integral transform of the residuals using the
     * PASquare method.
     *
     * @param node    The node whose residuals need to be calculated.
     * @param parents The list of parent nodes used in the calculation.
     * @return The Anderson-Darling test statistic for the probability integral transform of the residuals.
     */
    private double andersonDarlingPASquare(Node node, List<Node> parents) {
        double[] _f = residuals(node, parents, true);
        return new AndersonDarlingTest(_f).getASquared();
    }

    /**
     * Calculates the entropy of a given node.
     *
     * @param node    the node for which the entropy is to be calculated
     * @param parents the list of parent nodes
     * @return the entropy value
     */
    private double entropy(Node node, List<Node> parents) {
        double[] _f = residuals(node, parents, true);
        return maxEntApprox(_f);
    }

    /**
     * Calculates the p-value for a given node and list of parents.
     *
     * @param node    the node for which to calculate the p-value
     * @param parents the list of parents for the node
     * @return the calculated p-value
     */
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

            int targetCol = dataSet.getColumnIndex(target);

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                if (isNaN(dataSet.getDouble(i, targetCol))) {
                    continue DATASET;
                }
            }

            for (Node regressor : regressors) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                int regressorCol = dataSet.getColumnIndex(regressor);

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

    /**
     * Retrieves the CPDAG (Completed Partially Directed Acyclic Graph).
     *
     * @return the CPDAG as a Graph object
     */
    private Graph getCpdag() {
        return this.cpdag;
    }

    /**
     * Finds a variable node by name from a given list of nodes.
     *
     * @param variables the list of nodes to search through
     * @param name      the name of the variable node to find
     * @return the variable node with the specified name, or null if not found
     */
    private Node getVariable(List<Node> variables, String name) {
        for (Node node : variables) {
            if (name.equals(node.getName())) {
                return node;
            }
        }

        return null;
    }

    /**
     * Resolves edge conditionals in the given graph.
     *
     * @param graph The graph in which edge conditionals need to be resolved.
     * @return The graph with resolved edge conditionals.
     */
    private Graph resolveEdgeConditional(Graph graph) {
        setDataSets(this.dataSets);

        Set<Edge> edgeList1 = graph.getEdges();

        for (Edge adj : edgeList1) {
            Node x = adj.getNode1();
            Node y = adj.getNode2();

            resolveEdgeConditional(graph, x, y);
        }

        return graph;
    }

    /**
     * Resolves the edge conditionality between two nodes in the graph.
     *
     * @param graph The graph object representing the network.
     * @param x     The first node.
     * @param y     The second node.
     */
    private void resolveEdgeConditional(Graph graph, Node x, Node y) {
        if (this._data == null) {
            this._data = DataTransforms.centerData(this.matrices.get(0));
        }
        int xIndex = this.dataSets.getFirst().getColumnIndex(this.dataSets.getFirst().getVariable(x.getName()));
        int yIndex = this.dataSets.getFirst().getColumnIndex(this.dataSets.getFirst().getVariable(y.getName()));
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

    /**
     * Calculates the conditional residuals based on the given inputs.
     *
     * @param x The array of values to calculate the residuals for.
     * @param y The matrix of values used to calculate the residuals.
     * @return The array containing the conditional residuals.
     */
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

    /**
     * Computes the optimal bandwidth for kernel density estimation using the method suggested by Silverman. The
     * optimal bandwidth is computed as the geometric mean across variables.
     *
     * @param x the input array of values for which the optimal bandwidth needs to be computed
     * @return the optimal bandwidth value
     */
    private double h(double[] x) {
        x = x.clone();
        var N = x.length;
        var central = median(x);
        for (var j = 0; j < N; j++) x[j] = abs(x[j] - central);
        var mad = median(x);
        var sigmaRobust = 1.4826 * mad;
        return 1.06 * sigmaRobust * FastMath.pow(N, -0.20);
    }

    /**
     * Calculates the kernel value for a given input.
     *
     * @param z the input value to calculate the kernel value for.
     * @return the calculated kernel value for the given input.
     */
    public double kernel(double z) {
        return kernel1(z);
    }

    /**
     * Computes the value of the kernel function 1 for a given input. (Gaussian.)
     *
     * @param z the input value for which the kernel function is computed
     * @return the value of the kernel function computed for the given input value
     */
    public double kernel1(double z) {
        return exp(-(z * z) / 2.) / this.SQRT; //(sqrt(2. * PI));
    }

    /**
     * Calculates the value of the kernel2 function for the given input. (Uniform.)
     *
     * @param z the input value to calculate the kernel2 for
     * @return the calculated value of kernel2
     */
    public double kernel2(double z) {
        if (abs(z) > 1) return 0;
        else return .5;
    }

    /**
     * This method calculates the value of the kernel function, kernel3, for a given input. (Triangular.)
     *
     * @param z the input value for which the kernel function is to be calculated
     * @return the calculated value of the kernel function
     */
    public double kernel3(double z) {
        if (abs(z) > 1) return 0;
        else return 1 - abs(z);
    }

    /**
     * Calculates the result of kernel4 function. (Epanechnikov)
     *
     * @param z a double value representing the input parameter
     * @return the result of kernel4 function
     */
    public double kernel4(double z) {
        if (abs(z) > 1) return 0;
        else return (3. / 4.) * (1. - z * z);
    }

    /**
     * Calculates the value of the kernel function, kernel5, for the given input. (Quartic)
     * <p>
     * The kernel function calculates the value based on the input value z. If the absolute value of z is greater than
     * 1, the function returns 0. Otherwise, it returns the result of the calculation: (15 / 16)
     *
     * @param z the input parameter
     * @return the result of kernel5 function
     */
    public double kernel5(double z) {
        if (abs(z) > 1) return 0;
        else return 15. / 16. * pow(1. - z * z, 2.);
    }

    /**
     * Calculates the value of the kernel function 6 given a parameter z. (Triweight)
     *
     * @param z the input parameter
     * @return the calculated value of the kernel function 6
     */
    public double kernel6(double z) {
        if (abs(z) > 1) return 0;
        else return 35. / 32. * pow(1. - z * z, 3.);
    }

    /**
     * Computes the value of the kernel7 function for the given value. (Tricube)
     *
     * @param z the input value
     * @return the computed value of the kernel7 function
     */
    public double kernel7(double z) {
        if (abs(z) > 1) return 0;
        else return 70. / 81. * pow(1. - z * z * z, 3.);
    }

    /**
     * Calculates the value of the kernel8 function for the given input. (Cosine)
     *
     * @param z the input value for the kernel8 function
     * @return the calculated value of the kernel8 function
     */
    public double kernel8(double z) {
        if (abs(z) > 1) return 0;
        else return (PI / 4.) * cos((PI / 2.) * z);
    }

    /**
     * Calculates the distance between two elements from a two-dimensional array.
     *
     * @param yCols a two-dimensional array containing data
     * @param i     the index of the first element
     * @param j     the index of the second element
     * @return the distance between the two elements
     */
    private double distance(double[][] yCols, int i, int j) {
        double sum = 0.0;

        for (double[] yCol : yCols) {
            double d = yCol[i] - yCol[j];
            sum += d * d;
        }

        return sqrt(sum);
    }

    /**
     * Resolves the difference between the maximum R3 values obtained from two edge cases.
     *
     * @param x The input values for the first edge case.
     * @param y The input values for the second edge case.
     * @return The difference between the maximum R^3 values.
     */
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

    /**
     * Calculates the difference between the conditional residuals of two arrays, x and y, based on the Anderson-Darling
     * test.
     *
     * @param x An array of double values representing the x-coordinates.
     * @param y An array of double values representing the y-coordinates.
     * @return The difference between the conditional residuals.
     */
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


    /**
     * Gives a list of options for non-Gaussian transformations that can be used for some scores.
     */
    public enum Score {
        /**
         * The absolute value.
         */
        absoluteValue,
        /**
         * The  Anderson-Darling score.
         */
        andersonDarling,
        /**
         * The  entropy.
         */
        entropy,
        /**
         * The exp.
         */
        exp,
        /**
         * The exp unstandardized.
         */
        expUnstandardized,
        /**
         * The exp unstandardized inverted.
         */
        expUnstandardizedInverted,
        /**
         * The fifth moment.
         */
        fifthMoment,
        /**
         * The kurtosis.
         */
        kurtosis,
        /**
         * The logcosh.
         */
        logcosh,
        /**
         * Other score.
         */
        other,
        /**
         * The skew.
         */
        skew

    }

    /**
     * Give a list of options for rules for doing the non-Gaussian orientations.
     */
    public enum Rule {
        /**
         * The EB rule.
         */
        EB,
        /**
         * The FastICA rule.
         */
        FastICA,
        /**
         * The IGCI rule.
         */
        IGCI,
        /**
         * The Patel rule.
         */
        Patel,
        /**
         * The Patel25 rule.
         */
        Patel25,
        /**
         * The Patel50 rule.
         */
        Patel50,
        /**
         * The Patel75 rule.
         */
        Patel75,
        /**
         * The Patel90 rule.
         */
        Patel90,
        /**
         * The R1 rule.
         */
        R1,
        /**
         * The R1 Time Lag rule.
         */
        R1TimeLag,
        /**
         * The R2 rule.
         */
        R2,
        /**
         * The R3 rule.
         */
        R3,
        /**
         * The RC rule.
         */
        RC,
        /**
         * The RSkew rule.
         */
        RSkew,
        /**
         * The RSkewE rule.
         */
        RSkewE,
        /**
         * The Skew rule.
         */
        Skew,
        /**
         * The SkewE rule.
         */
        SkewE,
        /**
         * The Tahn rule.
         */
        Tanh
    }
}



