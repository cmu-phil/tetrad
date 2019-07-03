package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.annotation.Gaussian;
import edu.cmu.tetrad.annotation.Linear;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
@TestOfIndependence(
        name = "Fisher Z Test",
        command = "fisher-z-test",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@Gaussian
@Linear
public class FisherZ implements IndependenceWrapper, TakesInitialGraph {

    static final long serialVersionUID = 23L;
    private double alpha = 0.001;
    private Graph trueGraph;
    private Graph initialGraph;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        double alpha = parameters.getDouble("alpha");
        this.alpha = alpha;

        if (trueGraph == null) {
            if (dataSet instanceof ICovarianceMatrix) {
                return new IndTestFisherZ((ICovarianceMatrix) dataSet, alpha);
            } else if (dataSet instanceof DataSet) {
                return new IndTestFisherZ((DataSet) dataSet, alpha);
            }
        } else {
            if (dataSet instanceof ICovarianceMatrix) {
                return new IndTestFisherZ(trueGraph, (ICovarianceMatrix) dataSet, alpha);
            } else if (dataSet instanceof DataSet) {
                return new IndTestFisherZ(trueGraph, (DataSet) dataSet, alpha);
            }
        }

        throw new IllegalArgumentException("Expecting eithet a data set or a covariance matrix.");
    }

    @Override
    public String getDescription() {
        return "Fisher Z test, alpha = " + alpha;
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add("alpha");
        return params;
    }

    @Override
    public Graph getInitialGraph() {
        return initialGraph;
    }

    @Override
    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    @Override
    public void setInitialGraph(Algorithm algorithm) {
        throw new IllegalStateException();
    }

    public void setTrueGraph(Graph trueGraph) {
        this.trueGraph = trueGraph;
        System.out.println(trueGraph);
        System.out.println();
    }
}
