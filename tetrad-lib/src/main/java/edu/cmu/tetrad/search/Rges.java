package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
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

    private final Score score;

    private final Fges ges;

    private double modelScore;

    public Rges(@NotNull Score score) {
        this.variables = new ArrayList<>(score.getVariables());
        this.score = score;
        this.ges = new Fges(score);
//        this.ges.setFaithfulnessAssumed(false);
//        this.ges.setSymmetricFirstStep(true);
    }

    public Graph search() {

        Graph cpdag = ges.search();
        modelScore = ges.getModelScore();

        Graph g0;
        double s0;

        do {
            g0 = null;
            s0 = modelScore;
            Set<Edge> edges = cpdag.getEdges();

            for (Edge edge : edges) {
                if (edge.isDirected()) {
                    Edge reversed = edge.reverse();
                    cpdag.removeEdge(edge);
                    cpdag.addEdge(reversed);
                    if (!cpdag.existsDirectedCycle()) {
                        ges.setExternalGraph(cpdagFromDag(dagFromCPDAG(cpdag)));
                        Graph g1 = ges.search();
                        double s1 = ges.getModelScore();
                        if (s1 == s0) {
                            g0 = g1;
                            s0 = s1;
                        }
                    }
                    cpdag.removeEdge(reversed);
                    cpdag.addEdge(edge);
                }
            }

            if (g0 != null) {
                cpdag = g0;
                modelScore = s0;
            }
        } while(g0 != null);

        return cpdag;
    }

    public double getModelScore() {
        return modelScore;
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
