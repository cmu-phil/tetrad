package edu.cmu.tetrad.algcomparison.algorithms.continuous.cyclic_pag;

import edu.cmu.tetrad.algcomparison.algorithms.Algorithm;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jdramsey on 6/4/16.
 */
public class CCD2 implements Algorithm {
    public Graph search(DataSet dataSet, Parameters parameters) {
        SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        Ccd2 pc = new Ccd2(score);
        return pc.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return graph;
    }

    public String getDescription() {
        return "CCD using the SEM BIC score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("penaltyDiscount");
        return parameters;
    }}
