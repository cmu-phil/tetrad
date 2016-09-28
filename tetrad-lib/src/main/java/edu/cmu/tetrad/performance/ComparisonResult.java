package edu.cmu.tetrad.performance;

import edu.cmu.tetrad.graph.EdgeListGraphSingleConnections;
import edu.cmu.tetrad.graph.Graph;

/**.
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

    public ComparisonResult(ComparisonParameters params) {
        this.params = new ComparisonParameters(params);
    }

    public void setResultGraph(Graph graph) {
        this.resultGraph = new EdgeListGraphSingleConnections(graph);
    }

    public void setElapsed(long elapsed) {
        this.elapsed = elapsed;
    }

    public void setTrueDag(Graph trueDag) {
        this.trueDag = new EdgeListGraphSingleConnections(trueDag);
    }

    public void setCorrectResult(Graph correctResult) {
        this.correctResult = correctResult;
    }

    public Graph getResultGraph() {
        return resultGraph;
    }

    public long getElapsed() {
        return elapsed;
    }

    public Graph getTrueDag() {
        return trueDag;
    }

    public Graph getCorrectResult() {
        return correctResult;
    }

    public String toString() {
        return params.toString();
    }

    public ComparisonParameters getParams() {
        return params;
    }
}
