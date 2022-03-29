package edu.cmu.tetrad.performance;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;

/**
 * .
 * Result of a comparison.
 *
 * @author jdramsey 2016.03.24
 */
public class ComparisonResult {
    private final ComparisonParameters params;
    private Graph trueDag;
    private Graph resultGraph;
    private Graph correctResult;
    private long elapsed;

    public ComparisonResult(final ComparisonParameters params) {
        this.params = new ComparisonParameters(params);
    }

    public void setResultGraph(final Graph graph) {
        this.resultGraph = new EdgeListGraph(graph);
    }

    public void setElapsed(final long elapsed) {
        this.elapsed = elapsed;
    }

    public void setTrueDag(final Graph trueDag) {
        this.trueDag = new EdgeListGraph(trueDag);
    }

    public void setCorrectResult(final Graph correctResult) {
        this.correctResult = correctResult;
    }

    public Graph getResultGraph() {
        return this.resultGraph;
    }

    public long getElapsed() {
        return this.elapsed;
    }

    public Graph getTrueDag() {
        return this.trueDag;
    }

    public Graph getCorrectResult() {
        return this.correctResult;
    }

    public String toString() {
        return this.params.toString();
    }

    public ComparisonParameters getParams() {
        return this.params;
    }
}
