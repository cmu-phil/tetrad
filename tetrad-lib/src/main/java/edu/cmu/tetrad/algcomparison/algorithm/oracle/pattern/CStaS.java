package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
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
        name = "CStaS",
        command = "cstas",
        algoType = AlgType.forbid_latent_common_causes,
        description = "Performs a CStaS analysis of the given dataset (Stekhoven, Daniel J., et al. " +
                "Causal stability ranking.\" Bioinformatics 28.21 (2012): 2819-2823) and returns a graph " +
                "in which all selected variables are shown as into the target. The target is the first variables."
)
public class CStaS implements Algorithm {
    static final long serialVersionUID = 23L;
    private Algorithm algorithm;
    private Graph trueDag = null;
    private List<edu.cmu.tetrad.search.CStaS.Record> records = null;

    public CStaS() {
        this.algorithm = new Fges();
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        System.out.println("# Available Processors = " + Runtime.getRuntime().availableProcessors());
        System.out.println("Parallelism = " + parameters.getInt("parallelism"));

        edu.cmu.tetrad.search.CStaS cStaS = new edu.cmu.tetrad.search.CStaS();

        cStaS.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        cStaS.setParallelism(parameters.getInt("parallelism"));
        cStaS.setMaxEr(parameters.getDouble("maxEr"));
        cStaS.setNumSubsamples(parameters.getInt("numSubsamples"));
        cStaS.setTrueDag(trueDag);

        final Node target = dataSet.getVariable(parameters.getString("targetName"));

        this.records =  cStaS.getRecords((DataSet) dataSet, target);

        System.out.println(cStaS.makeTable(this.getRecords()));

        return cStaS.makeGraph(target, getRecords());

    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return algorithm.getComparisonGraph(graph);
    }

    @Override
    public String getDescription() {
        return "CStaS";
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

    public List<edu.cmu.tetrad.search.CStaS.Record> getRecords() {
        return records;
    }
}

