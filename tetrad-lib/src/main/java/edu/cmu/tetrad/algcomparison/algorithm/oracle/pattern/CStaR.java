package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Cstar;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@edu.cmu.tetrad.annotation.Algorithm(
        name = "CStaR",
        command = "cstar",
        algoType = AlgType.forbid_latent_common_causes
)
@Experimental
public class CStaR implements Algorithm, TakesIndependenceWrapper {
    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private LinkedList<Cstar.Record> records = null;

    public CStaR() {
    }

    @Override
    public Graph search(final DataModel dataSet, final Parameters parameters) {
        System.out.println("# Available Processors = " + Runtime.getRuntime().availableProcessors());
        System.out.println("Parallelism = " + parameters.getInt("parallelism"));

        final Cstar cStaR = new Cstar();

        cStaR.setParallelism(parameters.getInt(Params.PARALLELISM));
        cStaR.setNumSubsamples(parameters.getInt(Params.NUM_SUBSAMPLES));
        cStaR.setqFrom(parameters.getInt(Params.CSTAR_Q));
        cStaR.setqTo(parameters.getInt(Params.CSTAR_Q));
        cStaR.setqIncrement(1);
        cStaR.setPatternAlgorithm(Cstar.PatternAlgorithm.PC_STABLE);
        cStaR.setSampleStyle(Cstar.SampleStyle.SPLIT);
        cStaR.setVerbose(parameters.getBoolean("verbose"));

        final List<Node> possibleEffects = new ArrayList<>();

        final String targetName = parameters.getString("targetNames");

        if (targetName.trim().equalsIgnoreCase("all")) {
            for (final String name : dataSet.getVariableNames()) {
                possibleEffects.add(dataSet.getVariable(name));
            }
        } else {
            final String[] names = targetName.split(",");

            for (final String name : names) {
                possibleEffects.add(dataSet.getVariable(name.trim()));
            }
        }

        final List<Node> possibleCauses = new ArrayList<>(dataSet.getVariables());

        final LinkedList<LinkedList<Cstar.Record>> allRecords
                = cStaR.getRecords((DataSet) dataSet, possibleCauses, possibleEffects, this.test.getTest(dataSet, parameters));

        if (allRecords.isEmpty()) {
            throw new IllegalStateException("There were no records.");
        }

        this.records = allRecords.getLast();

        System.out.println(cStaR.makeTable(this.getRecords(), false));

        return cStaR.makeGraph(getRecords());
    }

    @Override
    public Graph getComparisonGraph(final Graph graph) {
        return new EdgeListGraph();
    }

    @Override
    public String getDescription() {
        return "CStaR";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        final List<String> parameters = new ArrayList<>(this.test.getParameters());
        parameters.add(Params.SELECTION_ALPHA);
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.NUM_SUBSAMPLES);
        parameters.add(Params.TARGET_NAMES);
        parameters.add(Params.CSTAR_Q);
        parameters.add(Params.PARALLELISM);
        return parameters;
    }

    public LinkedList<Cstar.Record> getRecords() {
        return this.records;
    }

    @Override
    public void setIndependenceWrapper(final IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }
}
