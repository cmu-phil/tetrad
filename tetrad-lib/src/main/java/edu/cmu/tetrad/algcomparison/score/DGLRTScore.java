package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.IndTestScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndTestDegenerateGaussianLrt;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for degenerate Gaussian likelihood ratio test score.
 *
 * @author bryan andrews
 * @version $Id: $Id
 */
public class DGLRTScore implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The data set.
     */
    double alpha = 0.001;

    /**
     * The data set.
     */
    private DataModel dataSet;

    /**
     * {@inheritDoc}
     */
    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        double alpha = parameters.getDouble(Params.ALPHA);
        this.alpha = alpha;
        IndTestDegenerateGaussianLrt test = new IndTestDegenerateGaussianLrt((DataSet) dataSet);
        test.setAlpha(alpha);
        return new IndTestScore(test);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "DG LRT Score";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ALPHA);
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
