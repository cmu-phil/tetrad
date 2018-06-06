package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "CStaS",
//        command = "cstas",
//        algoType = AlgType.forbid_latent_common_causes,
//        description = "Performs a CStaS analysis of the given dataset (Stekhoven, Daniel J., et al. " +
//                "Causal stability ranking.\" Bioinformatics 28.21 (2012): 2819-2823) and returns a graph " +
//                "in which all selected variables are shown as into the target. The target is the first variables."
//)
public class CStaS implements Algorithm, TakesIndependenceWrapper {
    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private LinkedList<edu.cmu.tetrad.search.CStaS.Record> records = null;

    public CStaS() {
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        System.out.println("# Available Processors = " + Runtime.getRuntime().availableProcessors());
        System.out.println("Parallelism = " + parameters.getInt("parallelism"));

        edu.cmu.tetrad.search.CStaS cStaS = new edu.cmu.tetrad.search.CStaS();

        cStaS.setParallelism(parameters.getInt("parallelism"));
        cStaS.setNumSubsamples(parameters.getInt("numSubsamples"));
        cStaS.setqFrom(parameters.getInt("q"));
        cStaS.setqTo(parameters.getInt("q"));
        cStaS.setqIncrement(1);
        cStaS.setPatternAlgorithm(edu.cmu.tetrad.search.CStaS.PatternAlgorithm.PC_STABLE);
        cStaS.setSampleStyle(edu.cmu.tetrad.search.CStaS.SampleStyle.SPLIT);
        cStaS.setVerbose(parameters.getBoolean("verbose"));

        List<Node> possibleEffects = new ArrayList<>();

        final String targetName = parameters.getString("targetNames");

        if (targetName.trim().equalsIgnoreCase("all")) {
            for (String name : dataSet.getVariableNames()) {
                possibleEffects.add(dataSet.getVariable(name));
            }
        } else {
            String[] names = targetName.split(",");

            for (String name : names) {
                possibleEffects.add(dataSet.getVariable(name.trim()));
            }
        }

        List<Node> possibleCauses = new ArrayList<>(dataSet.getVariables());

        final LinkedList<LinkedList<edu.cmu.tetrad.search.CStaS.Record>> allRecords
                = cStaS.getRecords((DataSet) dataSet, possibleCauses, possibleEffects, test.getTest(dataSet, parameters));
        this.records = allRecords.getLast();

        System.out.println(cStaS.makeTable(this.getRecords(), false));

        return cStaS.makeGraph(getRecords());
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph();
    }

    @Override
    public String getDescription() {
        return "CStaS";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>(test.getParameters());
        parameters.add("selectionAlpha");
        parameters.add("penaltyDiscount");
        parameters.add("numSubsamples");
        parameters.add("targetNames");
        parameters.add("q");
        parameters.add("parallelism");
        return parameters;
    }

    public LinkedList<edu.cmu.tetrad.search.CStaS.Record> getRecords() {
        return records;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }
}
