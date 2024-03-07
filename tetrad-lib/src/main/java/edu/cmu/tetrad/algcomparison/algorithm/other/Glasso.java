package edu.cmu.tetrad.algcomparison.algorithm.other;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * GLASSO.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "GLASSO",
//        command = "glasso",
//        algoType = AlgType.produce_undirected_graphs,
//        dataType = DataType.Continuous
//)
@Bootstrapping
@Experimental
public class Glasso extends AbstractBootstrapAlgorithm implements Algorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * {@inheritDoc}
     */
    public Graph runSearch(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet dataSet && dataModel.isContinuous())) {
            throw new IllegalArgumentException("Expecting a continuous dataset.");
        }

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            for (int i = 0; i < dataSet.getNumRows(); i++) {
                if (Double.isNaN(dataSet.getDouble(i, j))) {
                    throw new IllegalArgumentException("Please remove or impute missing values.");
                }
            }
        }

        Matrix cov = new Matrix(SimpleDataLoader.getContinuousDataSet(dataSet)
                .getCovarianceMatrix().toArray());

        edu.cmu.tetrad.search.work_in_progress.Glasso glasso = new edu.cmu.tetrad.search.work_in_progress.Glasso(cov);
        glasso.setMaxit(parameters.getInt(Params.MAXIT));
        glasso.setIa(parameters.getBoolean(Params.IA));
        glasso.setIs(parameters.getBoolean(Params.IS));
        glasso.setItr(parameters.getBoolean(Params.ITR));
        glasso.setIpen(parameters.getBoolean(Params.IPEN));
        glasso.setThr(parameters.getDouble(Params.THR));
        glasso.setRhoAllEqual(1.0);

        edu.cmu.tetrad.search.work_in_progress.Glasso.Result result = glasso.search();
        Matrix wwi = new Matrix(result.getWwi().toArray());

        List<Node> variables = dataSet.getVariables();
        Graph resultGraph = new EdgeListGraph(variables);

        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                if (wwi.get(i, j) != 0.0 && wwi.get(i, j) != 0.0) {
                    resultGraph.addUndirectedEdge(variables.get(i), variables.get(j));
                }
            }
        }

        return resultGraph;
    }

    /**
     * {@inheritDoc}
     */
    public Graph getComparisonGraph(Graph graph) {
        return GraphUtils.undirectedGraph(graph);
    }

    /**
     * <p>getDescription.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getDescription() {
        return "GLASSO (Graphical LASSO)";
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
        List<String> params = new ArrayList<>();
        params.add(Params.MAXIT);
        params.add(Params.IA);
        params.add(Params.IS);
        params.add(Params.ITR);
        params.add(Params.IPEN);
        params.add(Params.THR);

        params.add(Params.VERBOSE);
        return params;
    }
}
