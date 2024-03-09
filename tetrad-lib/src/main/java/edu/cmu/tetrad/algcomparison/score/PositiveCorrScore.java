package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.IndTestScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.work_in_progress.IndTestPositiveCorr;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class PositiveCorrScore implements ScoreWrapper {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * The alpha level.
     */
    double alpha = 0.001;
    /**
     * The data set.
     */
    private DataModel dataSet;

    /**
     * Constructs a new instance of the FisherZScore.
     */
    public PositiveCorrScore() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        double alpha = parameters.getDouble("alpha");
        this.alpha = alpha;
        IndTestPositiveCorr test = new IndTestPositiveCorr((DataSet) dataSet, alpha);
        return new IndTestScore(test);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Fisher Z Score";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("alpha");
        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node getVariable(String name) {
        return this.dataSet.getVariable(name);
    }
}
