package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.Parameters;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Score(
        name = "D-separation Score",
        command = "d-sep-score",
        dataType = DataType.Graph
)
public class DSeparationScore implements ScoreWrapper {

    static final long serialVersionUID = 23L;
    private Graph graph;
    private DataModel dataSet;

    /**
     * Use this empty constructor to satisfy the java reflection
     */
    public DSeparationScore() {

    }
    
    public DSeparationScore(Graph graph) {
        this.graph = graph;
    }

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        if (dataSet == null) {
            return new GraphScore(graph);
        } else {
            throw new IllegalArgumentException("Expecting no data for a d-separation test.");
        }
    }

    @Override
    public String getDescription() {
        return "D-separation Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        return new ArrayList<>();
    }

    @Override
    public Node getVariable(String name) {
        return dataSet.getVariable(name);
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
    }

    
}
