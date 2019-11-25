package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A confusion matrix for adjacencies--i.e. TP, FP, TN, FN for counts of adjacencies.
 *
 * @author jdramsey
 */
public class UnshieldedTripleConfusion {
    private int tp;
    private int fp;
    private int fn;
    private int tn;
    private Set<Edge> involvedUtFp;
    private Set<Edge> involvedUtFn;
    private Set<Edge> involvedUtTrue;
    private Set<Set<Node>> triangles = new HashSet<>();
    private int ambiguousTriple;
    private int nonambiguousTriangle;

    public UnshieldedTripleConfusion(Graph truth, Graph est) {
        Set<Triple> trueTriangles = getUnshieldedTriples(truth);
        Set<Triple> estTriangles = getUnshieldedTriples(est);
        involvedUtFp = getInvolvedUtFp(truth, est);
        involvedUtFn = getInvolvedUtFn(truth, est);
        involvedUtTrue = getInvolvedNotFpUt(truth, est);

        Set<Triple> allTriangles = new HashSet<>(trueTriangles);
        allTriangles.addAll(estTriangles);

        tp = 0;
        fp = 0;
        fn = 0;

        for (Triple triple : allTriangles) {
            if (estTriangles.contains(triple) && !trueTriangles.contains(triple)) {
                fp++;
            }

            if (trueTriangles.contains(triple) && !estTriangles.contains(triple)) {
                fn++;
            }

            if (trueTriangles.contains(triple) && estTriangles.contains(triple)) {
                tp++;
            }
        }

        tn = allTriangles.size() - trueTriangles.size();
    }

    private Set<Triple> getUnshieldedTriples(Graph graph) {
        Set<Triple> triples = new HashSet<>();


        for (Node b : graph.getNodes()) {
            List<Node> adjb = graph.getAdjacentNodes(b);

            if (adjb.size() < 2) continue;

            ChoiceGenerator gen = new ChoiceGenerator(adjb.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> _adj = GraphUtils.asList(choice, adjb);
                Node a = _adj.get(0);
                Node c = _adj.get(1);

                if (graph.isAdjacentTo(a, c)) continue;

                triples.add(new Triple(a, b, c));
            }
        }

        return triples;
    }

    private Set<Edge> getInvolvedUtFp(Graph _true, Graph _est) {
        Set<Edge> involved = new HashSet<>();

        for (Node b : _est.getNodes()) {
            List<Node> adjb = _est.getAdjacentNodes(b);

            if (adjb.size() < 2) continue;

            ChoiceGenerator gen = new ChoiceGenerator(adjb.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> _adj = GraphUtils.asList(choice, adjb);
                Node a = _adj.get(0);
                Node c = _adj.get(1);

                if (_true.isAdjacentTo(a, c) && !_est.isAdjacentTo(a, c)) {
                    involved.add(Edges.undirectedEdge(a, b));
                    involved.add(Edges.undirectedEdge(c, b));
                }
            }
        }

        return involved;
    }

    private Set<Edge> getInvolvedUtFn(Graph _true, Graph _est) {
        Set<Edge> involved = new HashSet<>();
        triangles = new HashSet<>();

        ambiguousTriple = 0;
        nonambiguousTriangle = 0;

        for (Node b : _est.getNodes()) {
            List<Node> adjb = _est.getAdjacentNodes(b);

            if (adjb.size() < 2) continue;

            ChoiceGenerator gen = new ChoiceGenerator(adjb.size(), 2);
            int[] choice;


            while ((choice = gen.next()) != null) {
                List<Node> _adj = GraphUtils.asList(choice, adjb);
                Node a = _adj.get(0);
                Node c = _adj.get(1);

                if (!_true.isAdjacentTo(a, c) && _est.isAdjacentTo(a, c)) {
                    involved.add(Edges.undirectedEdge(a, b));
                    involved.add(Edges.undirectedEdge(c, b));

                    Set<Node> triangle = new HashSet<>();
                    triangle.add(a);
                    triangle.add(c);

                    if (_est.isAmbiguousTriple(a, b, c)) {
                        ambiguousTriple++;
                    } else {
                        nonambiguousTriangle++;
                    }

                    getTriangles().add(triangle);
                }
            }
        }

        return involved;
    }

    private Set<Edge> getInvolvedNotFpUt(Graph _true, Graph _est) {
        Set<Edge> involved = new HashSet<>();

        for (Node b : _est.getNodes()) {
            List<Node> adjb = _est.getAdjacentNodes(b);

            if (adjb.size() < 2) continue;

            ChoiceGenerator gen = new ChoiceGenerator(adjb.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> _adj = GraphUtils.asList(choice, adjb);
                Node a = _adj.get(0);
                Node c = _adj.get(1);

                if (!(_true.isAdjacentTo(a, c) && !_est.isAdjacentTo(a, c))) {
                    involved.add(Edges.undirectedEdge(a, b));
                    involved.add(Edges.undirectedEdge(c, b));
                }
            }
        }

        return involved;
    }

    public int getTp() {
        return tp;
    }

    public int getFp() {
        return fp;
    }

    public int getFn() {
        return fn;
    }

    public int getTn() {
        return tn;
    }

    public int getInvolvedUtFp() {
        return involvedUtFp.size();//* (1 - ambiguousTriple / (nonambiguousTriangle + 1));
    }

    public int getInvolvedUtFn() {
        return involvedUtFn.size();
    }

    public Set<Set<Node>> getTriangles() {
        return triangles;
    }
}
