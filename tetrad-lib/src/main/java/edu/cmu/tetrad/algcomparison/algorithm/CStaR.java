package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

@edu.cmu.tetrad.annotation.Algorithm(
        name = "CStaR",
        command = "cstar",
        algoType = AlgType.forbid_latent_common_causes,
        description = "Performs a CStaR analysis of the given dataset (Stekhoven, Daniel J., et al. " +
                "Causal stability ranking.\" Bioinformatics 28.21 (2012): 2819-2823) and returns a graph " +
                "in which all selected variables are shown as into the target. The target is the first variables."
)
public class CStaR implements Algorithm {
    static final long serialVersionUID = 23L;
    private Algorithm algorithm;
    private Graph trueDag = null;

    public CStaR() {
        this.algorithm = new Fges();
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        System.out.println("# Available Processors = " + Runtime.getRuntime().availableProcessors());
        System.out.println("Parallelism = " + parameters.getInt("parallelism"));

        edu.cmu.tetrad.search.CStaR cStaR = new edu.cmu.tetrad.search.CStaR();

        cStaR.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        cStaR.setParallelism(parameters.getInt("parallelism"));
        cStaR.setMaxEr(parameters.getDouble("maxEr"));
        cStaR.setNumSubsamples(parameters.getInt("numSubsamples"));
        cStaR.setTrueDag(trueDag);

        final Node target = dataSet.getVariable(parameters.getString("targetName"));

        List<edu.cmu.tetrad.search.CStaR.Record> records
                =  cStaR.getRecords((DataSet) dataSet, target);

        System.out.println(cStaR.makeTable(records));

        return cStaR.makeGraph(target, records);

    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return algorithm.getComparisonGraph(graph);
    }

    @Override
    public String getDescription() {
        return "CStaR";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("penaltyDiscount");
        parameters.add("numSubsamples");
        parameters.add("maxEr");
        parameters.add("targetName");
        parameters.add("parallelism");
        return parameters;
    }

    public void setTrueDag(Graph trueDag) {
        this.trueDag = trueDag;
    }


}


