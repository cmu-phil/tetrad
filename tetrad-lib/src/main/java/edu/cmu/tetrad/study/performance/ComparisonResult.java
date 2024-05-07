package edu.cmu.tetrad.study.performance;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;

/**
 * . Result of a comparison.
 *
 * @author josephramsey 2016.03.24
 * @version $Id: $Id
 */
public class ComparisonResult {
    private final ComparisonParameters params;
    private Graph trueDag;
    private Graph resultGraph;
    private Graph correctResult;
    private long elapsed;

    /**
     * <p>Constructor for ComparisonResult.</p>
     *
     * @param params a {@link edu.cmu.tetrad.study.performance.ComparisonParameters} object
     */
    public ComparisonResult(ComparisonParameters params) {
        this.params = new ComparisonParameters(params);
    }

    /**
     * <p>Getter for the field <code>resultGraph</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getResultGraph() {
        return this.resultGraph;
    }

    /**
     * <p>Setter for the field <code>resultGraph</code>.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void setResultGraph(Graph graph) {
        this.resultGraph = new EdgeListGraph(graph);
    }

    /**
     * <p>Getter for the field <code>elapsed</code>.</p>
     *
     * @return a long
     */
    public long getElapsed() {
        return this.elapsed;
    }

    /**
     * <p>Setter for the field <code>elapsed</code>.</p>
     *
     * @param elapsed a long
     */
    public void setElapsed(long elapsed) {
        this.elapsed = elapsed;
    }

    /**
     * <p>Getter for the field <code>trueDag</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getTrueDag() {
        return this.trueDag;
    }

    /**
     * <p>Setter for the field <code>trueDag</code>.</p>
     *
     * @param trueDag a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void setTrueDag(Graph trueDag) {
        this.trueDag = new EdgeListGraph(trueDag);
    }

    /**
     * <p>Getter for the field <code>correctResult</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getCorrectResult() {
        return this.correctResult;
    }

    /**
     * <p>Setter for the field <code>correctResult</code>.</p>
     *
     * @param correctResult a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void setCorrectResult(Graph correctResult) {
        this.correctResult = correctResult;
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return this.params.toString();
    }

    /**
     * <p>Getter for the field <code>params</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.study.performance.ComparisonParameters} object
     */
    public ComparisonParameters getParams() {
        return this.params;
    }
}
