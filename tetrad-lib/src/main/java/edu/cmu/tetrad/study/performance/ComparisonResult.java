package edu.cmu.tetrad.study.performance;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;

/**
 * . Result of a comparison.
 *
 * @author josephramsey 2016.03.24
 */
public class ComparisonResult {
    private final ComparisonParameters params;
    private Graph trueDag;
    private Graph resultGraph;
    private Graph correctResult;
    private long elapsed;

    public ComparisonResult(ComparisonParameters params) {
        this.params = new ComparisonParameters(params);
    }

    public Graph getResultGraph() {
        return this.resultGraph;
    }

    public void setResultGraph(Graph graph) {
        this.resultGraph = new EdgeListGraph(graph);
    }

    public long getElapsed() {
        return this.elapsed;
    }

    public void setElapsed(long elapsed) {
        this.elapsed = elapsed;
    }

    public Graph getTrueDag() {
        return this.trueDag;
    }

    public void setTrueDag(Graph trueDag) {
        this.trueDag = new EdgeListGraph(trueDag);
    }

    public Graph getCorrectResult() {
        return this.correctResult;
    }

    public void setCorrectResult(Graph correctResult) {
        this.correctResult = correctResult;
    }

    public String toString() {
        return this.params.toString();
    }

    public ComparisonParameters getParams() {
        return this.params;
    }
}
