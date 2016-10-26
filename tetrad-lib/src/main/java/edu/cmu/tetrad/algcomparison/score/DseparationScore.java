package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class DseparationScore implements ScoreWrapper {
    static final long serialVersionUID = 23L;
    private final RandomGraph randomGraph;

    public DseparationScore(RandomGraph randomGraph) {
        this.randomGraph = randomGraph;
    }

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        if (dataSet == null) {
            return new GraphScore(randomGraph.createGraph(parameters));
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

}
