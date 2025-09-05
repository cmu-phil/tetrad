package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.test.IndTestBlocksLemma10;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wrapper for Rank independence test.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@TestOfIndependence(
        name = "Rank Independence Test Lemma 10",
        command = "rank-test-lemma-10",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
@Deprecated(since = "7.9", forRemoval = false)
public class RankIndependenceTestLemma10 implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the algorithm.
     */
    public RankIndependenceTestLemma10() {
    }

    /**
     * Gets an independence test based on the given data model and parameters.
     *
     * @param dataModel  The data set to test independence against.
     * @param parameters The parameters of the test.
     * @return An IndependenceTest object.
     * @throws IllegalArgumentException if the dataModel is not a dataset or a covariance matrix.
     */
    @Override
    public IndependenceTest getTest(DataModel dataModel, Parameters parameters) {
        List<Node> nodes = dataModel.getVariables();
        List<Node> blockVars = new ArrayList<>();
        List<List<Integer>> blocks = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            blockVars.add(nodes.get(i));
            blocks.add(Collections.singletonList(i));
        }

        // If you’re using the Wilks-rank test:
        IndTestBlocksLemma10 ind = new IndTestBlocksLemma10(new BlockSpec((DataSet) dataModel, blocks, blockVars));
        ind.setAlpha(parameters.getDouble(Params.ALPHA));

        return ind;
    }

    /**
     * Retrieves the description of the Fisher Z test.
     *
     * @return The description of the Fisher Z test.
     */
    @Override
    public String getDescription() {
        return "Rank test Lemma 10";
    }

    /**
     * Retrieves the data type of the independence test.
     *
     * @return The data type of the independence test.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves the parameters of the Fisher Z test.
     *
     * @return A list of strings representing the parameters of the Fisher Z test.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        return params;
    }
}
