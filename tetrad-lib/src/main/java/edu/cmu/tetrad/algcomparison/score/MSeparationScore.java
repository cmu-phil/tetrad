package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Score(
        name = "M-separation Score",
        command = "m-sep-score",
        dataType = DataType.Graph
)
public class MSeparationScore implements ScoreWrapper {

    private static final long serialVersionUID = 23L;
    private Graph graph;
    private DataModel dataSet;

    /**
     * Use this empty constructor to satisfy the java reflection
     */
    public MSeparationScore() {

    }

    /**
     * <p>Constructor for MSeparationScore.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public MSeparationScore(Graph graph) {
        this.graph = graph;
    }

    /** {@inheritDoc} */
    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        if (dataSet == null) {
            return new GraphScore(this.graph);
        } else {
            throw new IllegalArgumentException("Expecting no data for a m-separation test.");
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "M-separation Score";
    }

    /** {@inheritDoc} */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getParameters() {
        return new ArrayList<>();
    }

    /** {@inheritDoc} */
    @Override
    public Node getVariable(String name) {
        return this.dataSet.getVariable(name);
    }

    /**
     * <p>Setter for the field <code>graph</code>.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void setGraph(Graph graph) {
        this.graph = graph;
    }


}
