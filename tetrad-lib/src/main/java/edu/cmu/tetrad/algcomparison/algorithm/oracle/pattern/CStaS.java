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

@edu.cmu.tetrad.annotation.Algorithm(
        name = "CStaS",
        command = "cstas",
        algoType = AlgType.forbid_latent_common_causes,
        description = "Performs a CStaS analysis of the given dataset (Stekhoven, Daniel J., et al. " +
                "Causal stability ranking.\" Bioinformatics 28.21 (2012): 2819-2823) and returns a graph " +
                "in which all selected variables are shown as into the target. The target is the first variables."
)
public class CStaS implements Algorithm, TakesIndependenceWrapper {
    static final long serialVersionUID = 23L;
    private Graph trueDag = null;
    private IndependenceWrapper test;
    private LinkedList<edu.cmu.tetrad.search.CStaS.Record> records = null;
    private double evBound;
    private double MBEvBound;

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
        cStaS.setTrueDag(trueDag);
        cStaS.setVerbose(parameters.getBoolean("verbose"));

        final String targetName = parameters.getString("targetNames");
        String[] names = targetName.split(",");
        List<Node> possibleEffects = new ArrayList<>();

        for (String name : names) {
            possibleEffects.add(dataSet.getVariable(name));
        }

        List<Node> possibleCauses = new ArrayList<>(dataSet.getVariables());
        possibleCauses.removeAll(possibleEffects);

        this.records = cStaS.getRecords((DataSet) dataSet, possibleCauses, possibleEffects, test.getTest(dataSet, parameters));
        evBound = this.records.get(0).getEv();
        MBEvBound = this.records.get(0).getMBEv();

        System.out.println(cStaS.makeTable(this.getRecords()));

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

    public void setTrueDag(Graph trueDag) {
        this.trueDag = trueDag;
    }

    public List<edu.cmu.tetrad.search.CStaS.Record> getRecords() {
        return records;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }

    public double getEvBound() {
        return evBound;
    }

    public double getMBEvBound() {
        return MBEvBound;
    }
}
