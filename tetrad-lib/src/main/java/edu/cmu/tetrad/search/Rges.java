package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.ArrayList;
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
                    g0.removeEdge(edge);

                    if (!GraphUtils.existsSemidirectedPath(
                            Edges.getDirectedEdgeTail(edge),
                            Edges.getDirectedEdgeHead(edge), g0)) {
                        Edge reversed = edge.reverse();
                        g0.addEdge(reversed);

                        Graph g = new EdgeListGraph(g0);
                        new MeekRules().orientImplied(g);

                        ges.setExternalGraph(g);
                        Graph g1 = ges.search();
                        double s1 = ges.getModelScore();

                        if (s1 > s0) {
                            g0 = g1;
                            s0 = s1;
                            continue W;
                        }

                        g0.removeEdge(reversed);
                    } else {
                        getOut().println("edge reversal ignored");
                    }

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
