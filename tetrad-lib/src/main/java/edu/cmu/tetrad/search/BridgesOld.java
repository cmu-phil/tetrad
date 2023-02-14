package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static edu.cmu.tetrad.util.RandomUtil.shuffle;

/**
 * Implementation of the experimental BRIDGES algorithm
 *
 * @author bryanandrews
 */

public class BridgesOld {

    private final List<Node> variables;

    private final Fges ges;

    private final MeekRules meeks;
    private Knowledge knowledge = new Knowledge();

    public BridgesOld(@NotNull Score score) {
        this.variables = new ArrayList<>(score.getVariables());
        this.ges = new Fges(score);
//        this.ges.setKnowledge(knowledge);
        this.meeks = new MeekRules();
    }

    public Graph search() {

        Graph g0 = ges.search();
        double s0 = ges.getModelScore();

        boolean flag = true;

        while (flag) {
            if (Thread.interrupted()) break;

            flag = false;
            List<Edge> edges = new ArrayList<>(g0.getEdges());
            shuffle(edges);
            Iterator<Edge> edgeItr = edges.iterator();

            while (!flag && edgeItr.hasNext()) {

                Edge edge = edgeItr.next();
                if (edge.isDirected()) {

                    Graph g = new EdgeListGraph((EdgeListGraph) g0);
                    Node a = Edges.getDirectedEdgeHead(edge);
                    Node b = Edges.getDirectedEdgeTail(edge);

                    for (Node c : g.getAdjacentNodes(b)) {
                        if (c == a || g.paths().existsSemidirectedPath(c, a)) {
                            g.removeEdge(g.getEdge(b, c));
                            g.addDirectedEdge(c, b);
                        }
                    }

                    meeks.orientImplied(g);

                    ges.setInitialGraph(g);
                    Graph g1 = ges.search();
                    double s1 = ges.getModelScore();

                    if (s1 > s0) {
                        flag = true;
                        g0 = g1;
                        s0 = s1;
                        getOut().println(g0.getNumEdges());
                    }
                }
            }
        }

        return g0;
    }

//    public Graph search() {
//
//        Graph g1 = ges.search();
//        Graph g0 = g1;
//        double s1 = ges.getModelScore();
//        double s0 = s1 - 1;
//
//        while (s1 > s0) {
//            if (Thread.interrupted()) break;
//            g0 = new EdgeListGraph((EdgeListGraph) g1);
//            s0 = s1;
//
//            Set<Edge> edges = g0.getEdges();
//            getOut().println(edges.size());
//
//            for (Edge edge : edges) {
//
//                if (edge.isDirected()) {
//
//                    Graph g = new EdgeListGraph((EdgeListGraph) g0);
//                    Node a = Edges.getDirectedEdgeHead(edge);
//                    Node b = Edges.getDirectedEdgeTail(edge);
//
//                    for (Node c : g.getAdjacentNodes(b)) {
//                        if (c == a || existsSemidirectedPath(c, a, g)) {
//                            g.removeEdge(g.getEdge(b, c));
//                            g.addDirectedEdge(c, b);
//                        }
//                    }
//
//                    meeks.orientImplied(g);
//                    ges.setExternalGraph(g);
//                    g = ges.search();
//
//                    double s = ges.getModelScore();
//                    if (s > s1) {
//                        g1 = g;
//                        s1 = s;
//                    }
//                }
//            }
//        }
//
//        return g0;
//    }


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

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }
}
