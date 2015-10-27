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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.*;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static java.lang.Math.*;

/**
 * Implements the "fast adjacency search" used in several causal algorithms in this package. In the fast adjacency
 * search, at a given stage of the search, an edge X*-*Y is removed from the graph if X _||_ Y | S, where S is a subset
 * of size d either of adj(X) or of adj(Y), where d is the depth of the search. The fast adjacency search performs this
 * procedure for each pair of adjacent edges in the graph and for each depth d = 0, 1, 2, ..., d1, where d1 is either
 * the maximum depth or else the first such depth at which no edges can be removed. The interpretation of this adjacency
 * search is different for different algorithms, depending on the assumptions of the algorithm. A mapping from {x, y} to
 * S({x, y}) is returned for edges x *-* y that have been removed.
 *
 * @author Joseph Ramsey.
 */
public class FasICov implements IFas {

    private final List<TetradMatrix> covMatices;
    /**
     * The search graph. It is assumed going in that all of the true adjacencies of x are in this graph for every node
     * x. It is hoped (i.e. true in the large sample limit) that true adjacencies are never removed.
     */
    private Graph graph;

    /**
     * The independence test. This should be appropriate to the types
     */
    private IndependenceTest test;

    /**
     * Specification of which edges are forbidden or required.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * The maximum number of variables conditioned on in any conditional independence test. If the depth is -1, it will
     * be taken to be the maximum value, which is 1000. Otherwise, it should be set to a non-negative integer.
     */
    private int depth = 1000;

    /**
     * The number of independence tests.
     */
    private int numIndependenceTests;


    /**
     * The logger, by default the empty logger.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    /**
     * The number of false dependence judgements, judged from the true graph using d-separation. Temporary.
     */
    private int numFalseDependenceJudgments;

    /**
     * The number of dependence judgements. Temporary.
     */
    private int numDependenceJudgement;

    /**
     * The sepsets found during the search.
     */
    private SepsetMap sepset = new SepsetMap();

    /**
     * True if this is being run by FCI--need to skip the knowledge forbid step.
     */
    private NumberFormat nf = new DecimalFormat("0.00E0");

    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose = false;
    private boolean fdr = false;
    private List<Double> pValueList = new ArrayList<Double>();
    private final double alpha;
    private List<Node> nodes;

    List<TetradMatrix> cov = new ArrayList<TetradMatrix>();
    List<TetradMatrix> corr = new ArrayList<TetradMatrix>();
    
    private PrintStream out = System.out;


    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new FastAdjacencySearch.
     */
    public FasICov(IndependenceTest test) {
        this.graph = new EdgeListGraph(test.getVariables());
        this.test = test;
        this.covMatices = test.getCovMatrices();
        this.alpha = test.getAlpha();
        this.nodes = test.getVariables();

        for (TetradMatrix matrix : covMatices) {
            cov.add(matrix);
            corr.add(MatrixUtils.convertCovToCorr(new TetradMatrix(matrix)));
        }
    }

    //==========================PUBLIC METHODS===========================//

    /**
     * Discovers all adjacencies in data.  The procedure is to remove edges in the graph which connect pairs of
     * variables which are independent conditional on some other set of variables in the graph (the "sepset"). These are
     * removed in tiers.  First, edges which are independent conditional on zero other variables are removed, then edges
     * which are independent conditional on one other variable are removed, then two, then three, and so on, until no
     * more edges can be removed from the graph.  The edges which remain in the graph after this procedure are the
     * adjacencies in the data.
     *
     * @return a SepSet, which indicates which variables are independent conditional on which other variables
     */
    public Graph search() {
        this.logger.log("info", "Starting Fast Adjacency Search.");
        graph.removeEdges(graph.getEdges());

        sepset = new SepsetMap();

        int _depth = depth;

        if (_depth == -1) {
            _depth = 1000;
        }

        List<Node> nodes = graph.getNodes();
        Map<Node, Set<Node>> adjacencies = completeGraph(nodes);

//        searchICov(nodes, test, adjacencies, false);


        for (int d = 0; d <= _depth; d++) {
            out.println("depth " + d);
            depth(d, nodes, test, adjacencies);

            if (!(freeDegree(nodes, adjacencies) > d)) {
                break;
            }
        }


//        depth0(nodes, test, adjacencies, false);
//        if (_depth >= 1) {
//            depth1(nodes, test, adjacencies);
//        }
//        if (_depth >= 2) {
//            depth2(nodes, test, adjacencies);
//        }
////        searchiCovAll(nodes, test, adjacencies);
//
//        if (fdr) {
//            for (int d = 3; d <= _depth; d++) {
//                pValueList = new ArrayList<Double>();
//                test.setAlpha(alpha);
//                Map<Node, Set<Node>> _adjacencies = copy(adjacencies);
//                searchAtDepthICov(nodes, test, adjacencies, d);
//                double cutoff = StatUtils.fdr(test.getAlpha(), pValueList, false);
//                adjacencies = _adjacencies;
//                test.setAlpha(cutoff);
//                pValueList = new ArrayList<Double>();
//                searchAtDepthICov(nodes, test, adjacencies, d);
////                searchAtDepth(nodes, test, adjacencies, d);
//
//                if (!(freeDegree(nodes, adjacencies) > depth)) {
//                    break;
//                }
//            }
//
////            for (int d = 0; d <= _depth; d++) {
////                test.setAlpha(alpha);
////                Map<Node, Set<Node>> _adjacencies = copy(adjacencies);
////                searchAtDepth(nodes, test, adjacencies, d);
////                double cutoff = StatUtils.fdr(test.getAlpha(), pValueList, false);
////                adjacencies = _adjacencies;
////                test.setAlpha(cutoff);
////
//////                searchAtHeightICov(nodes, test, adjacencies, d);
////                searchAtDepth(nodes, test, adjacencies, d);
////
////                if (!(freeDegree(nodes, adjacencies) > depth)) {
////                    break;
////                }
////            }
//        } else {
////            for (int d = _depth; d >= 0; d--) {
//            for (int d = 3; d <= _depth; d++) {
////                searchAtDepthICov(nodes, test, adjacencies, d);
////                searchAtDepthICov(nodes, test, adjacencies, d);
//                searchAtDepth(nodes, test, adjacencies, d);
//
//                if (!(freeDegree(nodes, adjacencies) > depth)) {
//                    break;
//                }
//            }
//
////            for (int d = 0; d <= _depth; d++) {
//////                searchAtDepthICov(nodes, test, adjacencies, d);
////                searchAtDepth(nodes, test, adjacencies, d);
//////                searchAtDepth(nodes, test, adjacencies, d);
////
////                if (!(freeDegree(nodes, adjacencies) > depth)) {
////                    break;
////                }
////            }
//        }

//        out.println("Finished with search, constructing Graph...");

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (adjacencies.get(x).contains(y)) {
                    graph.addUndirectedEdge(x, y);
                }
            }
        }

//        out.println("Finished constructing Graph.");

        this.logger.log("info", "Finishing Fast Adjacency Search.");

        return graph;
    }

    private Map<Node, Set<Node>> copy(Map<Node, Set<Node>> adjacencies) {
        Map<Node, Set<Node>> copy = new HashMap<Node, Set<Node>>();

        for (Node node : adjacencies.keySet()) {
            copy.put(node, new HashSet<Node>(adjacencies.get(node)));
        }

        return copy;
    }

    private Map<Node, Set<Node>> emptyGraph(List<Node> nodes) {
        Map<Node, Set<Node>> adjacencies = new HashMap<Node, Set<Node>>();

        for (Node node : nodes) {
            adjacencies.put(node, new TreeSet<Node>());
        }
        return adjacencies;
    }

    private Map<Node, Set<Node>> completeGraph(List<Node> nodes) {
        Map<Node, Set<Node>> adjacencies = new HashMap<Node, Set<Node>>();

        for (int i = 0; i < nodes.size(); i++) {
            adjacencies.put(nodes.get(i), new HashSet<Node>());
        }

        for (int i = 0; i < nodes.size(); i++) {
            Node x = nodes.get(i);

            for (int j = i + 1; j < nodes.size(); j++) {
                Node y = nodes.get(j);
                adjacencies.get(x).add(y);
                adjacencies.get(y).add(x);
            }
        }

        return adjacencies;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0.");
        }

        this.depth = depth;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Cannot set knowledge to null");
        }
        this.knowledge = knowledge;
    }

    //==============================PRIVATE METHODS======================/

    private boolean depth(int n, List<Node> nodes, IndependenceTest test, Map<Node, Set<Node>> adjacencies) {
        boolean removed = false;

        int[] varsMax = new int[n + 2];
        int[] varsMin = new int[n + 2];
        int[] varsMinAbs = new int[n + 2];

        for (int x = 0; x < nodes.size(); x++) {
            for (int y = x + 1; y < nodes.size(); y++) {
                varsMax[0] = x;
                varsMax[1] = y;
                varsMin[0] = x;
                varsMin[1] = y;
                varsMinAbs[0] = x;
                varsMinAbs[1] = y;

                int[] maxIndex = new int[n];
                int[] minIndex = new int[n];
                int[] minAbsIndex = new int[n];

                Node _x = nodes.get(x);
                Node _y = nodes.get(y);
                if (!adjacencies.get(_x).contains(_y)) continue;

                for (int i = 2; i < n + 2; i++) {
                    if (i == 2) {
                        double max = Double.NEGATIVE_INFINITY;
                        double min = Double.POSITIVE_INFINITY;
                        double minAbs = Double.POSITIVE_INFINITY;

                        for (int j = 0; j < nodes.size(); j++) {
                            if (j == x || j == y) continue;
                            double c = sumCorrDepth1(j, x, y);
//                            double c = corr.get(j, x) * corr.get(j, y);

                            if (c > max) {
                                max = c;
                                maxIndex[i - 2] = j;
                            }
                        }

                        for (int j = 0; j < nodes.size(); j++) {
                            if (j == x || j == y) continue;
                            double c = sumCorrDepth1(j, x, y);
//                            double c = corr.get(j, x) * corr.get(j, y);

                            if (c < min) {
                                min = c;
                                minIndex[i - 2] = j;
                            }
                        }

                        for (int j = 0; j < nodes.size(); j++) {
                            if (j == x || j == y) continue;
                            double c = sumCorrDepth1(j, x, y);
//                            double c = corr.get(j, x) * corr.get(j, y);

                            if (abs(c) < minAbs) {
                                minAbs = abs(c);
                                minAbsIndex[i - 2] = j;
                            }
                        }

                        varsMax[i] = maxIndex[i - 2];
                        varsMin[i] = minIndex[i - 2];
                    } else {
                        double max = Double.NEGATIVE_INFINITY;
                        double min = Double.POSITIVE_INFINITY;
                        double minAbs = Double.POSITIVE_INFINITY;

                        J:
                        for (int j = 0; j < nodes.size(); j++) {
                            for (int k = 0; k < i; k++) if (varsMax[k] == j) continue J;
                            double c = sumCorrDepth2(j, varsMax[0]);
//                            double c = corr.get(j, varsMax[2]);

                            if (c > max) {
                                max = c;
                                maxIndex[i - 2] = j;
                            }
                        }

                        J:
                        for (int j = 0; j < nodes.size(); j++) {
                            for (int k = 0; k < i; k++) if (varsMin[k] == j) continue J;
                            double c = sumCorrDepth2(j, varsMin[2]);
//                            double c = corr.get(j, varsMin[2]);

                            if (c < min) {
                                min = c;
                                minIndex[i - 2] = j;
                            }
                        }

                        J:
                        for (int j = 0; j < nodes.size(); j++) {
                            for (int k = 0; k < i; k++) if (varsMin[k] == j) continue J;
                            double c = sumCorrDepth2(j, varsMin[2]);
//                            double c = corr.get(j, varsMin[2]);

                            if (abs(c) < minAbs) {
                                minAbs = abs(c);
                                minAbsIndex[i - 2] = j;
                            }
                        }

                        varsMax[i] = maxIndex[i - 2];
                        varsMin[i] = minIndex[i - 2];
                        varsMinAbs[i] = minAbsIndex[i - 2];
                    }
                }

                removed = removeDepth(nodes, adjacencies, x, y, maxIndex) || removed;
                removed = removeDepth(nodes, adjacencies, x, y, minIndex) || removed;
//                removed = removeDepth(nodes, adjacencies, x, y, minAbsIndex) || removed;

//                double p1 = pValue(nodes, test, x, y, maxIndex);
//                double p2 = pValue(nodes, test, x, y, minIndex);
//                double p3 = pValue(nodes, test, x, y, minAbsIndex);
//
//                if (p1 >= p2 && p1 >= p3) {
//                    removed = removeDepth(nodes, test, adjacencies, x, y, maxIndex) || removed;
//                }
//                else if (p2 >= p1 && p2 >= p3) {
//                    removed = removeDepth(nodes, test, adjacencies, x, y, minIndex) || removed;
//                }
//                else if (p3 >= p1 && p3 >= p2) {
//                    removed = removeDepth(nodes, test, adjacencies, x, y, minAbsIndex) || removed;
//                }
            }
        }

        return removed;
    }

//    private boolean depth(int n, List<Node> nodes, IndependenceTest test, Map<Node, Set<Node>> adjacencies) {
//        boolean removed = false;
//
//        ChoiceGenerator gen = new ChoiceGenerator(nodes.size(), 2);
//        int[] choice;
//        int[] vars = new int[n + 2];
//
//        while ((choice = gen.next()) != null) {
//            vars[0] = choice[0];
//            vars[1] = choice[1];
//
//            int[] maxIndex = new int[n];
//
//            Node _x = nodes.get(vars[0]);
//            Node _y = nodes.get(vars[1]);
//            if (!adjacencies.get(_x).contains(_y)) continue;
//
//            for (int i = 2; i < n + 2; i++) {
//                if (i == 2) {
//                    double max = Double.NEGATIVE_INFINITY;
//
//                    for (int j = 0; j < nodes.size(); j++) {
//                        if (j == vars[0] || j == vars[1]) continue;
//
//                        double c = sumCorrDepth1(vars[0], vars[2], j);
////                        double c = corr.get(j, vars[0]) * corr.get(j, vars[1]);
//
//                        if (abs(c) > max) {
//                            max = abs(c);
//                            maxIndex[i - 2] = j;
//                        }
//                    }
//
//                    vars[i] = maxIndex[i - 2];
//                } else {
//                    double max = Double.NEGATIVE_INFINITY;
//
//                    J:
//                    for (int j = 0; j < nodes.size(); j++) {
//                        for (int k = 0; k < i; k++) if (vars[k] == j) continue J;
//                        double c = sumCorrDepth2(j, vars[0]);
////                        double c = corr.get(j, vars[2]);
//
//                        if (abs(c) > max) {
//                            max = abs(c);
//                            maxIndex[i - 2] = j;
//                        }
//                    }
//
//                    vars[i] = maxIndex[i - 2];
//                }
//            }
//
//            boolean exists = false;
//
//            for (int i = 0; i < n; i++) {
//                for (int j = 0; j < n; j++) {
//                    if (i == j) continue;
//                    if (maxIndex[i] == maxIndex[j]) exists = true;
//                }
//            }
//
//            if (!exists) {
//                removed = removeDepth(nodes, adjacencies, vars[0], vars[1], maxIndex) || removed;
//            }
//        }
//
//        return removed;
//    }

    private boolean removeDepth(List<Node> nodes, Map<Node, Set<Node>> adjacencies, int x, int y, int... z) {
        boolean removed = false;

        Node _x = nodes.get(x);
        Node _y = nodes.get(y);
        if (!adjacencies.get(_x).contains(_y)) return false;

        int[] indices = new int[2 + z.length];
        indices[0] = x;
        indices[1] = y;
        for (int i = 0; i < z.length; i++) indices[2 + i] = z[i];
        List<Double> pvalues = new ArrayList<Double>();

        for (TetradMatrix _corr : corr) {
            TetradMatrix prec = _corr.getSelection(indices, indices).inverse();
            double r = -prec.get(0, 1) / sqrt(prec.get(0, 0) * prec.get(1, 1));
            double fisherZ = sqrt(test.getSampleSize()) * 0.5 * (log(1.0 + r) - log(1.0 - r));
            double pvalue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(fisherZ)));
            pvalues.add(pvalue);
        }

        Collections.sort(pvalues);

        if (pvalues.get(0) > test.getAlpha()) {
            adjacencies.get(_x).remove(_y);
            adjacencies.get(_y).remove(_x);

            List<Node> cond = new ArrayList<Node>();

            for (int i = 0; i < z.length; i++) {
                cond.add(nodes.get(z[i]));
            }

            getSepsets().set(_x, _y, cond);

            out.println(SearchLogUtils.independenceFactMsg(_x, _y, cond, test.getPValue()));

            removed = true;
        }

//        TetradMatrix prec = test.getCov().getMatrix().getSelection(indices, indices).inverse();
//        double r = - prec.get(0, 1) / sqrt(prec.get(0, 0) * prec.get(1, 1));
//        double fisherZ = sqrt(test.getSampleSize()) * 0.5 * (log(1.0 + r) - log(1.0 - r));
//        double pvalue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(fisherZ)));
//
//        if (pvalue > test.getAlpha()) {
//            adjacencies.get(_x).remove(_y);
//            adjacencies.get(_y).remove(_x);
//
//            List<Node> cond = new ArrayList<Node>();
//
//            for (int i = 0; i < z.length; i++) {
//                cond.add(nodes.get(z[i]));
//            }
//
//            getSepsets().set(_x, _y, cond);
//
//            out.println(SearchLogUtils.independenceFactMsg(_x, _y, cond, pvalue));
//
//            removed = true;
//        }

        return removed;
    }

    private boolean depth0(List<Node> nodes, IndependenceTest test, Map<Node, Set<Node>> adjacencies,
                           boolean addDependencies) {
        boolean removed = false;
        int sampleSize = test.getSampleSize();

        if (sampleSize == 0) throw new IllegalArgumentException();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                List<Double> pValues = new ArrayList<Double>();

                for (int m = 0; m < corr.size(); m++) {
                    TetradMatrix _corr = corr.get(m);
                    double r = -_corr.get(i, j);
                    double fisherZ = sqrt(sampleSize - 3.0) * 0.5 * (log(1.0 + r) - log(1.0 - r));
                    double pvalue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(fisherZ)));

                    if (pvalue >= 0.0) {
                        pValues.add(pvalue);
                        pValueList.add(pvalue);
                    }
                }

                double _cutoff = test.getAlpha();

                Collections.sort(pValues);
//                int index = (int) Math.round((1.0 - percent) * pValues.size());
                boolean independent = pValues.size() == 0 || pValues.get(0) > _cutoff;

                if (addDependencies) {
                    if (independent) {
                        getSepsets().set(x, y, new ArrayList<Node>());

                        if (verbose) {
//                            out.println(SearchLogUtils.independenceFactMsg(x, y, theRest, test.getPValue()));
                            out.println(x + " _||_ " + y + " | empty" + " p = " +
                                    nf.format(test.getPValue()));
                        }

                        removed = true;
                    } else if (!forbiddenEdge(x, y)) {
                        adjacencies.get(x).add(y);
                        adjacencies.get(y).add(x);
                    }
                } else {
                    if (independent) {
                        if (!adjacencies.get(x).contains(y)) continue;

                        adjacencies.get(x).remove(y);
                        adjacencies.get(y).remove(x);

                        getSepsets().set(x, y, new ArrayList<Node>());

                        if (verbose) {
                            out.println(SearchLogUtils.independenceFactMsg(x, y, new ArrayList<Node>(), test.getPValue()));
//                            out.println(x + " _||_ " + y + " | the rest" + " p = " +
//                                    nf.format(test.getPValue()));
                        }

                        removed = true;
                    }
                }
            }
        }

        return removed;
    }

    private boolean depth1(List<Node> nodes, IndependenceTest test, Map<Node, Set<Node>> adjacencies) {
        boolean removed = false;

        for (int x = 0; x < nodes.size(); x++) {
            for (int y = x + 1; y < nodes.size(); y++) {
                Node _x = nodes.get(x);
                Node _y = nodes.get(y);
                if (!adjacencies.get(_x).contains(_y)) continue;

                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;
                int minr = -1;
                int maxr = -1;

                for (int r = 0; r < nodes.size(); r++) {
                    if (r == x || r == y) continue;
                    double prod = sumCorrDepth1(x, y, r);

                    if (prod < min) {
                        min = prod;
                        minr = r;
                    }

                    if (prod > max) {
                        max = prod;
                        maxr = r;
                    }
                }

                removed = removeDepth1(nodes, test, adjacencies, x, y, minr) || removed;
                removed = removeDepth1(nodes, test, adjacencies, x, y, maxr) || removed;
            }
        }

        return removed;
    }


//    private boolean depth1(List<Node> nodes, IndependenceTest test, Map<Node, Set<Node>> adjacencies) {
//        boolean removed = false;
//
//        for (int x = 0; x < nodes.size(); x++) {
//            for (int y = x + 1; y < nodes.size(); y++) {
//                Node _x = nodes.get(x);
//                Node _y = nodes.get(y);
//                if (!adjacencies.get(_x).contains(_y)) continue;
//
//                double[] _prod = new double[nodes.size()];
//
//                double min = Double.POSITIVE_INFINITY;
//                double max = Double.NEGATIVE_INFINITY;
//
//                for (int r = 0; r < nodes.size(); r++) {
//                    if (r == x || r == y) continue;
//
//                    double sum = sumCorrDepth1(x, y, r);
//
//                    _prod[r] = sum;
//
//                    if (sum < min) {
//                        min = sum;
//                    }
//
//                    if (sum > max) {
//                        max = sum;
//                    }
//                }
//
//                for (int r = 0; r < nodes.size(); r++) {
//                    if (_prod[r] == max) {
//                        boolean _removed = removeDepth1(nodes, test, adjacencies, removed, x, y, r);
//                        if (_removed) {
//                            removed = removed || _removed;
//                            break;
//                        }
//                    }
//                }
//
//                for (int r = 0; r < nodes.size(); r++) {
//                    if (_prod[r] == min) {
//                        boolean _removed = removeDepth1(nodes, test, adjacencies, removed, x, y, r);
//                        if (_removed) {
//                            removed = removed || _removed;
//                            break;
//                        }
//                    }
//                }
//            }
//        }
//
//        return removed;
//    }

    private double sumCorrDepth1(int x, int y, int r) {
        double s = 0.0;

        for (int m = 0; m < corr.size(); m++) {
            TetradMatrix _corr = corr.get(m);
            s += _corr.get(x, r) * _corr.get(y, r);
        }

        return s;
    }

    private boolean removeDepth1(List<Node> nodes, IndependenceTest test, Map<Node, Set<Node>> adjacencies, int x, int y, int z) {
        Node _x = nodes.get(x);
        Node _y = nodes.get(y);
        if (!adjacencies.get(_x).contains(_y)) return false;

        Node _z = nodes.get(z);
        List<Double> pvalues = new ArrayList<Double>();

        for (TetradMatrix _corr : corr) {
            double c1 = _corr.get(x, y);
            double c2 = _corr.get(x, z);
            double c3 = _corr.get(z, y);

            double r = (c1 - c2 * c3) / (sqrt(1 - c2 * c2) * sqrt(1 - c3 * c3));
            double fisherZ = sqrt(test.getSampleSize() - 1 - 3.0) * 0.5 * (log(1.0 + r) - log(1.0 - r));
            double pvalue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(fisherZ)));
            pvalues.add(pvalue);
        }

        Collections.sort(pvalues);

        if (pvalues.get(0) > test.getAlpha()) {
//            if (!adjacencies.get(_x).contains(_y)) return removed;

            adjacencies.get(_x).remove(_y);
            adjacencies.get(_y).remove(_x);

            getSepsets().set(_x, _y, Collections.singletonList(_z));

            if (verbose) {
                out.println(SearchLogUtils.independenceFactMsg(_x, _y, Collections.singletonList(_z), test.getPValue()));
//                            out.println(_x + " _||_ " + _y + " | the rest" + " p = " +
//                                    nf.format(test.getPValue()));
            }

            return true;
        }

        return false;
    }

    private boolean depth2(List<Node> nodes, IndependenceTest test, Map<Node, Set<Node>> adjacencies) {
        boolean removed = false;

        for (int x = 0; x < nodes.size(); x++) {
            for (int y = x + 1; y < nodes.size(); y++) {
                Node _x = nodes.get(x);
                Node _y = nodes.get(y);
                if (!adjacencies.get(_x).contains(_y)) continue;

                int maxr = -1;
                int maxs = -1;
                int minr = -1;
                int mins = -1;

                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;

                for (int r = 0; r < nodes.size(); r++) {
                    if (r == x || r == y) {
                        continue;
                    }

                    double c = sumCorrDepth2(x, r);

                    if (c < min) {
                        min = c;
                        minr = r;
                    }

                    if (c > max) {
                        max = c;
                        maxr = r;
                    }
                }

                min = Double.POSITIVE_INFINITY;
                max = Double.NEGATIVE_INFINITY;

                for (int s = 0; s < nodes.size(); s++) {
                    if (s == x || s == y) {
                        continue;
                    }

                    double c = sumCorrDepth2(y, s);

                    if (c < min) {
                        min = c;
                        mins = s;
                    }

                    if (c > max) {
                        max = c;
                        maxs = s;
                    }
                }

                if (minr == mins) {
                    continue;
                }

                if (maxr == maxs) {
                    continue;
                }

                removed = removeDepth2(nodes, test, adjacencies, x, y, minr, mins) || removed;
                removed = removeDepth2(nodes, test, adjacencies, x, y, maxr, maxs) || removed;
            }
        }

        return removed;
    }

    private double sumCorrDepth2(int x, int r) {
        double s = 0.0;

        for (int m = 0; m < corr.size(); m++) {
            TetradMatrix _corr = corr.get(m);
            s += _corr.get(x, r);
        }

        return s;
    }


//    private boolean depth2(List<Node> nodes, IndependenceTest test, Map<Node, Set<Node>> adjacencies) {
//        boolean removed = false;
//
//        double[][] _prod = new double[nodes.size()][nodes.size()];
//
//        for (int x = 0; x < nodes.size(); x++) {
//            for (int y = x + 1; y < nodes.size(); y++) {
//                Node _x = nodes.get(x);
//                Node _y = nodes.get(y);
//                if (!adjacencies.get(_x).contains(_y)) continue;
//
//                double min = Double.POSITIVE_INFINITY;
//                double max = Double.NEGATIVE_INFINITY;
//                int maxr = -1;
//                int maxs = -1;
//                int minr = -1;
//                int mins = -1;
//
//                for (int r = 0; r < nodes.size(); r++) {
//                    if (r == x || r == y) continue;
//                    for (int s = 0; s < nodes.size(); s++) {
//                        if (s == x || s == y || s == r) continue;
//                        double sum = 0.0;
//
//                        for (int m = 0; m < corr.size(); m++) {
//                            TetradMatrix _corr = corr.get(m);
//                            sum +=  _corr.get(x, r) * _corr.get(r, s) * _corr.get(y, s);
//                        }
//
//                        _prod[r][s] = sum;
////                        removeDepth2(nodes, test, adjacencies, corr, x, y, r, s);
//
//                        if (sum < min) {
//                            min = sum;
//                            minr = r;
//                            mins = s;
//                        }
//
//                        if (sum > max) {
//                            max = sum;
//                            maxr = r;
//                            maxs = s;
//                        }
//                    }
//                }
//
//                boolean _removed = removeDepth2(nodes, test, adjacencies, x, y, minr, mins);
//
//                if (_removed) {
//                    removed = removed || _removed;
//                }
//                else {
//                    _removed = removeDepth2(nodes, test, adjacencies, x, y, maxr, maxs);
//
//                    if (_removed) {
//                        removed = removed || _removed;
//                    }
//                }
//            }
//        }
//
//        return removed;
//    }

    private boolean removeDepth2(List<Node> nodes, IndependenceTest test, Map<Node, Set<Node>> adjacencies, int x, int y, int z, int w) {
        Node _x = nodes.get(x);
        Node _y = nodes.get(y);
        if (!adjacencies.get(_x).contains(_y)) return false;

        Node _z = nodes.get(z);
        Node _w = nodes.get(w);

        List<Double> pvalues = new ArrayList<Double>();

        for (int k = 0; k < cov.size(); k++) {
            int[] indices = new int[]{x, y, z, w};
            TetradMatrix prec = cov.get(k).getSelection(indices, indices).inverse();
            double r = -prec.get(0, 1) / sqrt(prec.get(0, 0) * prec.get(1, 1));
            double fisherZ = sqrt(test.getSampleSize() - 1 - 3.0) * 0.5 * (log(1.0 + r) - log(1.0 - r));
            double pvalue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(fisherZ)));
            pvalues.add(pvalue);
        }

        Collections.sort(pvalues);

        if (pvalues.get(0) > test.getAlpha()) {
            adjacencies.get(_x).remove(_y);
            adjacencies.get(_y).remove(_x);

            List<Node> cond = new ArrayList<Node>();
            cond.add(_z);
            cond.add(_w);

            getSepsets().set(_x, _y, cond);

            if (verbose) {
                out.println(SearchLogUtils.independenceFactMsg(_x, _y, cond, test.getPValue()));
            }

            return true;
        }

        return false;
    }

    private boolean depth1a(List<Node> nodes, IndependenceTest test, Map<Node, Set<Node>> adjacencies) {
        boolean removed = false;
        List<TetradMatrix> corr = new ArrayList<TetradMatrix>();

        for (TetradMatrix matrix : covMatices) {
            corr.add(MatrixUtils.convertCovToCorr(new TetradMatrix(matrix)));
        }
        int sampleSize = test.getSampleSize();

        if (sampleSize == 0) throw new IllegalArgumentException();

        for (int x = 0; x < nodes.size(); x++) {

            Y:
            for (int y = x + 1; y < nodes.size(); y++) {

                int minr = -1;
                int mins = -1;
                double min = Double.POSITIVE_INFINITY;

                for (int r = 0; r < nodes.size(); r++) {
                    for (int s = 0; s < nodes.size(); s++) {
                        if (r == x || r == y) continue;
                        if (s == x || s == y) continue;

                        double sum = 0.0;

                        for (int m = 0; m < corr.size(); m++) {
                            TetradMatrix _corr = corr.get(m);
                            sum += _corr.get(r, s);
                        }

                        if (sum < min) {
                            min = sum;
                            minr = r;
                            mins = s;
                        }
                    }
                }

                int mint = -1;
                min = Double.POSITIVE_INFINITY;

                for (int t = 0; t < nodes.size(); t++) {
                    if (t == x || t == y || t == minr || t == mins) continue;

                    double sum = 0.0;

                    for (int m = 0; m < corr.size(); m++) {
                        TetradMatrix _corr = corr.get(m);
                        sum += _corr.get(minr, t);
                    }

                    if (sum < min) {
                        min = sum;
                        mint = t;
                    }
                }

                Node _x = nodes.get(x);
                Node _y = nodes.get(y);
                Node _z = nodes.get(mint);

                for (int m = 0; m < corr.size(); m++) {
                    TetradMatrix _corr = corr.get(m);

                    double c1 = _corr.get(x, y);
                    double c2 = _corr.get(x, mint);
                    double c3 = _corr.get(mint, y);

                    double r = (c1 - c2 * c3) / (sqrt(1 - c2 * c2) * sqrt(1 - c3 * c3));
                    double fisherZ = sqrt(sampleSize - 3.0) * 0.5 * (log(1.0 + r) - log(1.0 - r));
                    double pvalue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(fisherZ)));

                    if (pvalue < alpha) {
                        continue Y;
                    }
                }

                if (!adjacencies.get(_x).contains(_y)) continue;

                adjacencies.get(_x).remove(_y);
                adjacencies.get(_y).remove(_x);

                getSepsets().set(_x, _y, Collections.singletonList(_z));

                if (verbose) {
                    out.println(SearchLogUtils.independenceFactMsg(_x, _y, Collections.singletonList(_z), test.getPValue()));
//                            out.println(_x + " _||_ " + _y + " | the rest" + " p = " +
//                                    nf.format(test.getPValue()));
                }

                removed = true;

            }
        }

//        double min;
//
//        for (int x = 0; x < nodes.size(); x++) {
//
//            Y:
//            for (int y = x + 1; y < nodes.size(); y++) {
////                out.println("x = " + x + " y = " + y);
//
//                // Do a search in the X row for the minimum value, then in the Y row, then take whichever one
//                // yields the minimum log abs score sum.
//                int z = -1;
//
//                int xcol = -1;
//                min = Double.POSITIVE_INFINITY;
//
//                for (int r = 0; r < nodes.size(); r++) {
//                    if (r == x || r == y) continue;
//                    double sum = 0.0;
//
//                    for (int m = 0; m < corr.size(); m++) {
//                        TetradMatrix _corr = corr.get(m);
//                        sum += log(abs(_corr.get(x, r))) + log(abs(_corr.get(y, r)));
//                    }
//
//                    if (sum < min) {
//                        min = sum;
//                        z = r;
//                    }
//                }
//
////                for (int r = 0; r < nodes.size(); r++) {
////                    if (r == x || r == y) continue;
////                    double sum = 0.0;
////
////                    for (int m = 0; m < corr.size(); m++) {
////                        TetradMatrix _corr = corr.get(m);
////                        sum += abs(_corr.get(x, r));
////                    }
////
////                    if (sum < min) {
////                        min = sum;
////                        xcol = r;
////                    }
////                }
////
////                int ycol = -1;
////                min = Double.POSITIVE_INFINITY;
////
////                for (int r = 0; r < nodes.size(); r++) {
////                    if (r == x || r == y) continue;
////                    double sum = 0.0;
////
////                    for (int m = 0; m < corr.size(); m++) {
////                        TetradMatrix _corr = corr.get(m);
////                        sum += abs(_corr.get(y, r));
////                    }
////
////                    if (sum < min) {
////                        min = sum;
////                        ycol = r;
////                    }
////                }
//
////                int z;
////
////                {
////                    double sumx = 0.0;
////
////                    for (int m = 0; m < corr.size(); m++) {
////                        TetradMatrix _corr = corr.get(m);
////                        sumx += log(abs(_corr.get(x, xcol))) + log(abs(_corr.get(y, xcol)));
////                    }
////
////                    double sumy = 0.0;
////
////                    for (int m = 0; m < corr.size(); m++) {
////                        TetradMatrix _corr = corr.get(m);
////                        sumy += log(abs(_corr.get(x, ycol))) + log(abs(_corr.get(y, ycol)));
////                    }
////
////                    if (sumx < sumy) z = xcol; else z = ycol;
////                }
//
//                Node _x = nodes.get(x);
//                Node _y = nodes.get(y);
//                Node _z = nodes.get(z);
//
//                for (int m = 0; m < corr.size(); m++) {
//                    TetradMatrix _corr = corr.get(m);
//
//                    double c1 = _corr.get(x, y);
//                    double c2 = _corr.get(x, z);
//                    double c3 = _corr.get(z, y);
//
//                    double r = (c1 - c2 * c3) / (sqrt(1 - c2 * c2) * sqrt(1 - c3 * c3));
//                    double fisherZ = sqrt(sampleSize - 1 - 3.0) * 0.5 * (log(1.0 + r) - log(1.0 - r));
//                    double pvalue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(fisherZ)));
//
//                    if (pvalue < alpha) {
//                        continue Y;
//                    }
//                }
//
//                if (!adjacencies.get(_x).contains(_y)) continue;
//
//                adjacencies.get(_x).remove(_y);
//                adjacencies.get(_y).remove(_x);
//
//                getSepsets().set(_x, _y, Collections.singletonList(_z));
//
//                if (verbose) {
//                    out.println(SearchLogUtils.independenceFactMsg(_x, _y, Collections.singletonList(_z), test.getPValue()));
////                            out.println(_x + " _||_ " + _y + " | the rest" + " p = " +
////                                    nf.format(test.getPValue()));
//                }
//
//                removed = true;
//
//            }
//        }

//        for (int x = 0; x < nodes.size(); x++) {
//
//            Y:
//            for (int y = x + 1; y < nodes.size(); y++) {
//                Node _x = nodes.get(x);
//                Node _y = nodes.get(y);
//                if (!adjacencies.get(_x).contains(_y)) continue;
//                int maxZ = -1;
//                double min = Double.POSITIVE_INFINITY;
//
//                for (int z = 0; z < nodes.size(); z++) {
//                    if (z == x) continue;
//                    if (z == y) continue;
//
//                    double sum = 0.0;
//
//                    for (int m = 0; m < corr.size(); m++) {
//                        TetradMatrix _corr = corr.get(m);
//                        double abs = log(abs(_corr.get(x, z))) + log(abs(_corr.get(y, z)));
//                        sum += abs;
//                    }
//
//                    double avg = sum / corr.size();
//
//                    if (avg < min) {
//                        min = avg;
//                        maxZ = z;
//                    }
//                }
//
//                Node z = nodes.get(maxZ);
//
//                for (int m = 0; m < corr.size(); m++) {
//                    TetradMatrix _corr = corr.get(m);
//
//                    double c1 = _corr.get(x, y);
//                    double c2 = _corr.get(x, maxZ);
//                    double c3 = _corr.get(maxZ, y);
//
//                    double r = (c1 - c2 * c3) / (sqrt(1 - c2 * c2) * sqrt(1 - c3 * c3));
//                    double fisherZ = sqrt(sampleSize - 3.0) * 0.5 * (log(1.0 + r) - log(1.0 - r));
//                    double pvalue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(fisherZ)));
//
//                    if (pvalue < alpha) {
//                        continue Y;
//                    }
//                }
//
//                if (!adjacencies.get(_x).contains(_y)) continue;
//
//                adjacencies.get(_x).remove(_y);
//                adjacencies.get(_y).remove(_x);
//
//                getSepsets().set(_x, _y, Collections.singletonList(z));
//
//                if (verbose) {
//                    out.println(SearchLogUtils.independenceFactMsg(_x, _y, Collections.singletonList(z), test.getPValue()));
////                            out.println(_x + " _||_ " + _y + " | the rest" + " p = " +
////                                    nf.format(test.getPValue()));
//                }
//
//                removed = true;
//            }
//        }

        return removed;
    }

//    private boolean depth2(List<Node> nodes, IndependenceTest test, Map<Node, Set<Node>> adjacencies) {
//        boolean removed = false;
//        List<TetradMatrix> corr = new ArrayList<TetradMatrix>();
//
//        for (TetradMatrix matrix : covMatices) {
//            corr.add(MatrixUtils.convertCovToCorr(matrix));
//        }
//        int sampleSize = test.getSampleSize();
//
//        if (sampleSize == 0) throw new IllegalArgumentException();
//
//        int minx = -1;
//        int miny = -1;
//        int minz = -1;
//        int minw = -1;
//        double min;
//
//        for (int x = 0; x < nodes.size(); x++) {
//
//            Y:
//            for (int y = x + 1; y < nodes.size(); y++) {
////                out.println("x = " + x + " y = " + y);
//
//                if (x == minx || y == miny || -1 == minx || -1 == miny) {
//                    min = Double.POSITIVE_INFINITY;
//
//                    for (int r = 0; r < nodes.size(); r++) {
//                        for (int s = 0; s < nodes.size(); s++) {
//                            if (r == x || r == y) continue;
//                            if (s == x || s == y) continue;
//
//                            double sum = 0.0;
//
//                            for (int m = 0; m < corr.size(); m++) {
//                                TetradMatrix _corr = corr.get(m);
//                                sum += _corr.get(r, s);
//                            }
//
//                            if (sum < min) {
//                                min = sum;
//                                minx = r;
//                                miny = s;
//                            }
//                        }
//                    }
//                }
//
//                if (x == minx || y == miny || -1 == minz || minx == minz || miny == minz) {
//                    min = Double.POSITIVE_INFINITY;
//
//                    for (int t = 0; t < nodes.size(); t++) {
//                        if (t == x || t == y || t == minx || t == miny) continue;
//
//                        double sum = 0.0;
//
//                        for (int m = 0; m < corr.size(); m++) {
//                            TetradMatrix _corr = corr.get(m);
//                            sum += _corr.get(minx, t);
//                        }
//
//                        if (sum < min) {
//                            min = sum;
//                            minz = t;
//                        }
//                    }
//                }
//
//                if (x == minx || y == miny || -1 == minw || minx == minw || miny == minw || minz == minw) {
//                    min = Double.POSITIVE_INFINITY;
//
//                    for (int t = 0; t < nodes.size(); t++) {
//                        if (t == x || t == y || t == minx || t == miny) continue;
//
//                        double sum = 0.0;
//
//                        for (int m = 0; m < corr.size(); m++) {
//                            TetradMatrix _corr = corr.get(m);
//                            sum += _corr.get(minx, t);
//                        }
//
//                        if (sum < min) {
//                            min = sum;
//                            minz = t;
//                        }
//                    }
//                }
//
//                Node _x = nodes.get(x);
//                Node _y = nodes.get(y);
//                Node _z = nodes.get(minz);
//
//                for (int m = 0; m < corr.size(); m++) {
//                    TetradMatrix _corr = corr.get(m);
//
//                    double c1 = _corr.get(x, y);
//                    double c2 = _corr.get(x, minz);
//                    double c3 = _corr.get(minz, y);
//
//                    double r = (c1 - c2 * c3) / (sqrt(1 - c2 * c2) * sqrt(1 - c3 * c3));
//                    double fisherZ = sqrt(sampleSize - 3.0) * 0.5 * (log(1.0 + r) - log(1.0 - r));
//                    double pvalue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(fisherZ)));
//
//                    if (pvalue < alpha) {
//                        continue Y;
//                    }
//                }
//
//                if (!adjacencies.get(_x).contains(_y)) continue;
//
//                adjacencies.get(_x).remove(_y);
//                adjacencies.get(_y).remove(_x);
//
//                getSepsets().set(_x, _y, Collections.singletonList(_z));
//
//                if (verbose) {
//                    out.println(SearchLogUtils.independenceFactMsg(_x, _y, Collections.singletonList(_z), test.getPValue()));
////                            out.println(_x + " _||_ " + _y + " | the rest" + " p = " +
////                                    nf.format(test.getPValue()));
//                }
//
//                removed = true;
//
//            }
//        }
//
////        boolean removed = false;
////        List<TetradMatrix> corrs = new ArrayList<TetradMatrix>();
////
////        for (TetradMatrix matrix : covMatices) {
////            corrs.add(MatrixUtils.convertCovToCorr(matrix));
////        }
////        int sampleSize = test.getSampleSize();
////
////        if (sampleSize == 0) throw new IllegalArgumentException();
////
////        for (int x = 0; x < nodes.size(); x++) {
////            for (int y = 0; y < nodes.size(); y++) {
////                if (y == x) continue;
////
////                int maxZ = -1;
////                int maxW = -1;
////                double min = Double.POSITIVE_INFINITY;
////
////                for (int z = 0; z < nodes.size(); z++) {
////                    if (z == x || z == y) continue;
////
////                    for (int w = 0; w < nodes.size(); w++) {
////                        if (w == x || w == y || w == z) continue;
////
////
////                        double sum = 0.0;
////
////                        for (int m = 0; m < corrs.size(); m++) {
////                            TetradMatrix _corr = corrs.get(m);
////                            double log = log(abs(_corr.get(z, x) * _corr.get(z, w) * _corr.get(z, y)));
////                            sum += log;
////                        }
////
////                        double avg = sum / corrs.size();
////
////                        if (avg < min) {
////                            min = avg;
////                            maxZ = z;
////                            maxW = w;
////                        }
////                    }
////                }
////
////                Node _x = nodes.get(x);
////                Node _y = nodes.get(y);
////                if (!adjacencies.get(_x).contains(_y)) continue;
////
////                for (int z = 0; z < nodes.size(); z++) {
////                    for (int w = 0; w < nodes.size(); w++) {
////                        if (x == y || x == z || x == w) continue;
////                        if (y == z || y == w) continue;
////                        if (z == w) continue;
////
////                        double sum = 0.0;
////
////                        for (int m = 0; m < corrs.size(); m++) {
////                            TetradMatrix _corr = corrs.get(m);
////                            double log = log(abs(_corr.get(z, x))) + log(abs(_corr.get(z, w))) + log(abs(_corr.get(z, y)));
////                            sum += log;
////                        }
////
////                        double avg = sum / corrs.size();
////
////                        if (avg < min) {
////                            min = avg;
////                            maxW = w;
////                            maxZ = z;
////                        }
////                    }
////                }
////
////                Node _z = nodes.get(maxZ);
////                Node _w = nodes.get(maxW);
////
////                List<Node> condSet = new ArrayList<Node>();
////                condSet.add(_z);
////                condSet.add(_w);
////
////                boolean independent;
////                double pValue = Double.NaN;
////
////                try {
////                    independent = test.isIndependent(_x, _y, condSet);
////                    pValue = test.getPValue();
////                    pValueList.add(test.getPValue());
////                } catch (Exception e) {
////                    independent = true;
////                }
////
////                if (independent) {
////                    adjacencies.get(_x).remove(_y);
////                    adjacencies.get(_y).remove(_x);
////
////                    getSepsets().set(_x, _y, condSet);
////
////                    if (verbose) {
////                        out.println(SearchLogUtils.independenceFactMsg(_x, _y, condSet, test.getPValue()));
//////                            out.println(x + " _||_ " + y + " | the rest" + " p = " +
//////                                    nf.format(test.getPValue()));
////                    }
////
////                    removed = true;
////
////                    for (int x2 = x; x2 <= x; x2++) {
////                        for (int y2 = 0; y2 < nodes.size(); y2++) {
////                            if (y2 == x2 || y2 == maxW || y2 == maxZ) continue;
////
////                            Node _x2 = nodes.get(x2);
////                            Node _y2 = nodes.get(y2);
////
////                            if (!adjacencies.get(_x2).contains(_y2)) continue;
////
////                            try {
////                                test.isIndependent(_x2, _y2, condSet);
////                                double pValue2 = test.getPValue();
////                                independent = pValue2 >= pValue;
////                            } catch (Exception e) {
////                                independent = true;
////                            }
////
////                            if (independent) {
////                                adjacencies.get(_x2).remove(_y2);
////                                adjacencies.get(_y2).remove(_x2);
////
////                                getSepsets().set(_x2, _y2, condSet);
////
////                                if (verbose) {
////                                    out.println(SearchLogUtils.independenceFactMsg(_x2, _y2, condSet, test.getPValue()));
////                                }
////
////                                removed = true;
////                            }
////                        }
////                    }
////                }
////
////            }
////        }
//
//        return removed;
//    }

    private boolean searchICov(List<Node> nodes, IndependenceTest test, Map<Node, Set<Node>> adjacencies,
                               boolean addDependencies) {
        boolean removed = false;

        List<TetradMatrix> subCovInvs = new ArrayList<TetradMatrix>();
        for (TetradMatrix matrix : covMatices) {
            int[] ind = new int[nodes.size()];
            for (int i = 0; i < nodes.size(); i++) ind[i] = test.getVariables().indexOf(nodes.get(i));
            subCovInvs.add(matrix.getSelection(ind, ind).inverse());
        }

        int sampleSize = test.getSampleSize();

        if (sampleSize == 0) throw new IllegalArgumentException();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                List<Double> pValues = new ArrayList<Double>();

                for (int m = 0; m < subCovInvs.size(); m++) {
                    TetradMatrix inv = subCovInvs.get(m);
                    double r = -inv.get(i, j) / sqrt(inv.get(i, i) * inv.get(j, j));
                    double fisherZ = sqrt(sampleSize - (nodes.size() - 2) - 3.0) *
                            0.5 * (log(1.0 + r) - log(1.0 - r));
                    double pvalue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(fisherZ)));

                    if (pvalue >= 0.0) {
                        pValues.add(pvalue);
                        if (pValueList.size() < 20000) {
                            pValueList.add(pvalue);
                        }
                    }
                }

                double _cutoff = test.getAlpha();

                Collections.sort(pValues);
//                int index = (int) Math.round((1.0 - percent) * pValues.size());
                boolean independent = pValues.size() == 0 || pValues.get(0) > _cutoff;

                if (addDependencies) {
                    if (independent) {
                        List<Node> theRest = new ArrayList<Node>();

                        for (Node node : nodes) {
                            if (node != x && node != y) theRest.add(node);
                        }

                        getSepsets().set(x, y, theRest);

                        if (verbose) {
//                            out.println(SearchLogUtils.independenceFactMsg(x, y, theRest, test.getPValue()));
                            out.println(x + " _||_ " + y + " | the rest" + " p = " +
                                    nf.format(test.getPValue()));
                        }

                        removed = true;
                    } else if (!forbiddenEdge(x, y)) {
                        adjacencies.get(x).add(y);
                        adjacencies.get(y).add(x);
                    }
                } else {
                    if (independent) {
                        if (!adjacencies.get(x).contains(y)) continue;

                        List<Node> theRest = new ArrayList<Node>();

                        for (Node node : nodes) {
                            if (node != x && node != y) theRest.add(node);
                        }

                        adjacencies.get(x).remove(y);
                        adjacencies.get(y).remove(x);

                        getSepsets().set(x, y, theRest);

                        if (verbose) {
                            out.println(SearchLogUtils.independenceFactMsg(x, y, theRest, test.getPValue()));
//                            out.println(x + " _||_ " + y + " | the rest" + " p = " +
//                                    nf.format(test.getPValue()));
                        }

                        removed = true;
                    }
                }
            }
        }

        return removed;
    }

    private void searchiCovAll(List<Node> nodes, final IndependenceTest test, Map<Node, Set<Node>> adjacencies) {
        boolean removed;

        do {
            removed = false;

            for (Node x : nodes) {
                List<Node> adjx = new ArrayList<Node>(adjacencies.get(x));

                for (Node y : adjx) {
                    if (!adjacencies.get(x).contains(y)) continue;
                    List<Node> adjy = new ArrayList<Node>(adjacencies.get(y));
                    List<Node> adj = new ArrayList<Node>(adjx);
                    for (Node node : adjy) if (!adj.contains(node)) adj.add(node);
                    removed = removed || searchICov(adj, test, adjacencies, false);
                }
            }
        } while (removed);
    }

    private void searchiCovAdj(List<Node> nodes, final IndependenceTest test, Map<Node, Set<Node>> adjacencies) {
        boolean removed;

        do {
            removed = false;

            for (Node x : nodes) {
                List<Node> adj = new ArrayList<Node>(adjacencies.get(x));
                adj.add(x);
                removed = removed || searchICov(adj, test, adjacencies, false);
            }
        } while (removed);
    }

    private int freeDegree(List<Node> nodes, Map<Node, Set<Node>> adjacencies) {
        int max = 0;

        for (Node x : nodes) {
            Set<Node> opposites = adjacencies.get(x);

            for (Node y : opposites) {
                Set<Node> adjx = new HashSet<Node>(opposites);
                adjx.remove(y);

                if (adjx.size() > max) {
                    max = adjx.size();
                }
            }
        }

        return max;
    }

    private boolean forbiddenEdge(Node x, Node y) {
        String name1 = x.getName();
        String name2 = y.getName();

        if (knowledge.isForbidden(name1, name2) &&
                knowledge.isForbidden(name2, name1)) {
            this.logger.log("edgeRemoved", "Removed " + Edges.undirectedEdge(x, y) + " because it was " +
                    "forbidden by background knowledge.");

            return true;
        }

        return false;
    }

    private void searchAtDepth(List<Node> nodes, final IndependenceTest test, Map<Node, Set<Node>> adjacencies, int depth) {
        int count = 0;

        for (Node x : nodes) {
            if (++count % 100 == 0) out.println("count " + count + " of " + nodes.size());

            List<Node> adjx = new ArrayList<Node>(adjacencies.get(x));

            EDGE:
            for (Node y : adjx) {
                List<Node> _adjx = new ArrayList<Node>(adjacencies.get(x));
                _adjx.remove(y);
                List<Node> ppx = possibleParents(x, _adjx, knowledge);

                if (ppx.size() >= depth) {
                    ChoiceGenerator cg = new ChoiceGenerator(ppx.size(), depth);
                    int[] choice;

                    while ((choice = cg.next()) != null) {
                        List<Node> condSet = GraphUtils.asList(choice, ppx);

                        boolean independent;

                        try {
                            independent = test.isIndependent(x, y, condSet);
                            pValueList.add(test.getPValue());
                        } catch (Exception e) {
                            independent = true;
                        }

                        boolean noEdgeRequired =
                                knowledge.noEdgeRequired(x.getName(), y.getName());

                        if (independent && noEdgeRequired) {
                            adjacencies.get(x).remove(y);
                            adjacencies.get(y).remove(x);
                            getSepsets().set(x, y, condSet);

                            if (verbose) {
                                out.println("SearchAtDepth: " + SearchLogUtils.independenceFactMsg(x, y, condSet, test.getPValue()));
                            }

                            continue EDGE;
                        }
                    }
                }
            }
        }
    }

    private boolean searchAtDepthICov(List<Node> nodes, final IndependenceTest test, Map<Node, Set<Node>> adjacencies, int depth) {
        int count = 0;

        for (Node x : nodes) {
            if (++count % 100 == 0) out.println("count " + count + " of " + nodes.size());

            List<Node> adjx = new ArrayList<Node>(adjacencies.get(x));
            if (adjx.size() < depth + 1) continue;

            adjx.add(x);

            ChoiceGenerator cg = new ChoiceGenerator(adjx.size(), depth + 1);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> condSet = GraphUtils.asList(choice, adjx);

                if (!adjacencies.get(x).containsAll(condSet)) {
                    continue;
                }

                List<Node> _cond = new ArrayList<Node>(condSet);
                _cond.add(x);
                searchICov(_cond, test, adjacencies, false);
            }
        }

//        out.println("Num removed = " + numRemoved);
//        return numRemoved > 0;

        return freeDegree(nodes, adjacencies) > depth;
    }

    private boolean searchAtHeightICov(List<Node> nodes, final IndependenceTest test, Map<Node, Set<Node>> adjacencies, int depth) {
        int count = 0;

        for (Node x : nodes) {
            if (++count % 100 == 0) out.println("count " + count + " of " + nodes.size());

            List<Node> adjx = new ArrayList<Node>(adjacencies.get(x));

            if (adjx.size() < depth) continue;

            ChoiceGenerator cg = new ChoiceGenerator(adjx.size(), depth);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> condSet = GraphUtils.asList(choice, adjx);

                if (!adjacencies.get(x).containsAll(condSet)) {
                    continue;
                }

                List<Node> cond = new ArrayList<Node>(adjacencies.get(x));
                cond.removeAll(condSet);
                searchICov(cond, test, adjacencies, false);
            }
        }

//        out.println("Num removed = " + numRemoved);
//        return numRemoved > 0;

        return freeDegree(nodes, adjacencies) > depth;
    }

    private List<Node> possibleParents(Node x, List<Node> adjx,
                                       IKnowledge knowledge) {
        List<Node> possibleParents = new LinkedList<Node>();
        String _x = x.getName();

        for (Node z : adjx) {
            String _z = z.getName();

            if (possibleParentOf(_z, _x, knowledge)) {
                possibleParents.add(z);
            }
        }

        return possibleParents;
    }

    private boolean possibleParentOf(String z, String x, IKnowledge knowledge) {
        return !knowledge.isForbidden(z, x) && !knowledge.isRequired(x, z);
    }

    public int getNumIndependenceTests() {
        return numIndependenceTests;
    }

    public void setTrueGraph(Graph trueGraph) {
//        throw new UnsupportedOperationException();
    }

    public int getNumFalseDependenceJudgments() {
        return numFalseDependenceJudgments;
    }

    public int getNumDependenceJudgments() {
        return numDependenceJudgement;
    }

    public SepsetMap getSepsets() {
        return sepset;
    }

    public void setInitialGraph(Graph initialGraph) {
//        throw new UnsupportedOperationException();
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public boolean isAggressivelyPreventCycles() {
        return false;
    }

    @Override
    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {

    }

    @Override
    public IndependenceTest getIndependenceTest() {
        return null;
    }

    @Override
    public Graph search(List<Node> nodes) {
        return null;
    }

    @Override
    public long getElapsedTime() {
        return 0;
    }

    @Override
    public List<Node> getNodes() {
        return this.nodes;
    }

    @Override
    public List<Triple> getAmbiguousTriples(Node node) {
        return null;
    }

    public boolean isFdr() {
        return fdr;
    }

    public void setFdr(boolean fdr) {
        this.fdr = fdr;
    }

    @Override
    public void setOut(PrintStream out) {
        this.out = out;
    }
}


