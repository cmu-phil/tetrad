package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.algorithm.pairwise.FaskPW;
import edu.cmu.tetrad.algcomparison.algorithm.pairwise.RSkew;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Matrix;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.*;

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
        int reps = 0;

        while (flag) {
            if (Thread.interrupted()) break;

            while (flag) {
                ges.setExternalGraph(g0);
                g0 = ges.search();
                double s1 = ges.getModelScore();
                flag = s1 > s0;
                s0 = s1;
            }
            
            try {
                PrintWriter out = new PrintWriter("/Users/bandrews/Desktop/bridges_fmri/bridges_" + reps++ + ".txt");
                out.println(g0.toString());
                out.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            List<Edge> edges = new ArrayList<>(g0.getEdges());
            int numEdges = edges.size();
            Collections.shuffle(edges);
            Iterator<Edge> edgeIter = edges.iterator();

            while (!flag && edgeIter.hasNext()) {

                Edge edge = edgeIter.next();
                numEdges--;
                if (edge.isDirected()) {

                    Graph g = new EdgeListGraph(g0);
                    Node a = Edges.getDirectedEdgeHead(edge);
                    Node b = Edges.getDirectedEdgeTail(edge);

                    // This code performs "pre-tuck" operation
                    // that makes anterior nodes of the distal
                    // node into parents of the proximal node

                    for (Node c : g.getAdjacentNodes(b)) {
                        if (g.isParentOf(c, b)) continue;
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
                        getOut().println("Score Improvement: " + (s1 - s0));
                        g0 = g1;
                        s0 = s1;
                    } else {
                        g0.removeEdge(reversed);
                        g0.addEdge(edge);
                        getOut().println("Edges Remaining in Current Loop: " + numEdges + " / " + g0.getNumEdges());
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