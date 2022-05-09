package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static edu.cmu.tetrad.graph.GraphUtils.existsSemidirectedPath;

/**
 * Implementation of the experimental rGES algorithm
 *
 * @author bryanandrews
 */

public class Rges {

    private final List<Node> variables;

    private final Fges ges;

    private final MeekRules meeks;

    public Rges(@NotNull Score score) {
        this.variables = new ArrayList<>(score.getVariables());
        this.ges = new Fges(score);
        this.meeks = new MeekRules();
    }

    public Graph search() {

        Graph g0 = ges.search();
        double s0 = ges.getModelScore();

        boolean flag = true;

        while (flag) {
            if (Thread.interrupted()) break;

            flag = false;
            Iterator<Edge> edges = g0.getEdges().iterator();

            while (!flag && edges.hasNext()) {

                Edge edge = edges.next();
                if (edge.isDirected()) {

                    Graph g = new EdgeListGraph(g0);
                    Node a = Edges.getDirectedEdgeHead(edge);
                    Node b = Edges.getDirectedEdgeTail(edge);

                    // This code performs "pre-tuck" operation
                    // that makes anterior nodes of the distal
                    // node into parents of the proximal node

                    for (Node c : g.getAdjacentNodes(b)) {
                        if (existsSemidirectedPath(c, a, g)) {
                            g.removeEdge(g.getEdge(b, c));
                            g.addDirectedEdge(c, b);
                        }
                    }

                    Edge reversed = edge.reverse();

                    g.removeEdge(edge);
                    g.addEdge(reversed);

                    meeks.orientImplied(g);

                    ges.setExternalGraph(g);
                    Graph g1 = ges.search();
                    double s1 = ges.getModelScore();

                    if (s1 > s0) {
                        flag = true;
                        g0 = g1;
                        s0 = s1;
                        getOut().println(g0.getNumEdges());
                    } else {
                        g0.removeEdge(reversed);
                        g0.addEdge(edge);
                    }
                }
            }
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
