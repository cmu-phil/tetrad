package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static edu.cmu.tetrad.search.SearchGraphUtils.cpdagFromDag;
import static edu.cmu.tetrad.search.SearchGraphUtils.dagFromCPDAG;

/**
 * Implementation of the experimental rGES algorithm
 *
 * @author bryanandrews
 */

public class Rges {

    private final List<Node> variables;

    private final Fges ges;

    public Rges(@NotNull Score score) {
        this.variables = new ArrayList<>(score.getVariables());
        this.ges = new Fges(score);
        this.ges.setFaithfulnessAssumed(false);
        this.ges.setSymmetricFirstStep(true);
    }

    public Graph search() {

        Graph g0 = ges.search();
        double s0 = ges.getModelScore();

        W:
        while (true) {
            for (Edge edge : g0.getEdges()) {
                if (edge.isDirected()) {

//                    g0.removeEdge(edge);
//                    if (!GraphUtils.existsSemidirectedPath(
//                            Edges.getDirectedEdgeTail(edge),
//                            Edges.getDirectedEdgeHead(edge), g0)) {
//                        Edge reversed = edge.reverse();
//                        g0.addEdge(reversed);
//
//                        Graph g = new EdgeListGraph(g0);
//                        new MeekRules().orientImplied(g);
//
//                        ges.setExternalGraph(g);
//                        Graph g1 = ges.search();
//                        double s1 = ges.getModelScore();
//
//                        if (s1 > s0) {
//                            g0 = g1;
//                            s0 = s1;
//                            continue W;
//                        }
//
//                        g0.removeEdge(reversed);
//                    }

                    // This code performs tuck-like operation
                    // and makes ancestors of the distal node
                    // into ancestors of the proximal node
                    // before reversing the edge

                    Graph g = dagFromCPDAG(g0);
                    Edge reversed = edge.reverse();

                    List<Node> a = new ArrayList<>();
                    List<Node> b = new ArrayList<>();

                    a.add(Edges.getDirectedEdgeHead(edge));
                    b.add(Edges.getDirectedEdgeTail(edge));

                    Set<Node> an = new HashSet<>(g.getAncestors(a));
                    Set<Node> de = new HashSet<>(g.getDescendants(b));

                    an.retainAll(de);
                    de.removeAll(an);
                    de.add(Edges.getDirectedEdgeTail(edge));

                    for (Node c : an) {
                        for (Node d : de) {
                            if (g.isChildOf(c, d)) {
                                Edge flip = g.getEdge(c, d);
                                g.removeEdge(flip);
                                flip = flip.reverse();
                                g.addEdge(flip);
                            }
                        }
                    }

                    g.removeEdge(edge);
                    g.addEdge(reversed);

                    new MeekRules().orientImplied(g);

                    ges.setExternalGraph(g);
                    Graph g1 = ges.search();
                    double s1 = ges.getModelScore();

                    if (s1 > s0) {
                        g0 = g1;
                        s0 = s1;
                        getOut().println(g0.getNumEdges());
                        continue W;
                    }

                    g0.removeEdge(reversed);
                    g0.addEdge(edge);
                }
            }

            break;
        }

        return g0;
    }

    public List<Node> getVariables() {
        return this.variables;
    }

    public int getMaxDegree() {
        return ges.getMaxDegree();
    }

    public void setMaxDegree(int maxDegree) {
        ges.setMaxDegree(maxDegree);
    }

    public void setSymmetricFirstStep(boolean symmetricFirstStep) {
        ges.setSymmetricFirstStep(symmetricFirstStep);
    }

    public void setFaithfulnessAssumed(boolean faithfulnessAssumed) {
        ges.setFaithfulnessAssumed(faithfulnessAssumed);
    }

    public void setParallelized(boolean parallelized) {
        ges.setParallelized(parallelized);
    }

    public void setVerbose(boolean verbose) {
        ges.setVerbose(verbose);
    }

    public PrintStream getOut() {
        return ges.getOut();
    }

    public void setOut(PrintStream out) {
        ges.setOut(out);
    }

}
