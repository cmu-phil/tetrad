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
import edu.cmu.tetrad.search.Cstar.CpdagAlgorithm;
import edu.cmu.tetrad.search.Cstar.SampleStyle;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@edu.cmu.tetrad.annotation.Algorithm(
        name = "CStaR",
        command = "cstar",
        algoType = AlgType.forbid_latent_common_causes
)
public class Cstar implements Algorithm, TakesIndependenceWrapper {
    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private LinkedList<edu.cmu.tetrad.search.Cstar.Record> records;

    public Cstar() {
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        System.out.println("# Available Processors = " + Runtime.getRuntime().availableProcessors());
        System.out.println("Parallelized = " + parameters.getBoolean("parallelized"));

        edu.cmu.tetrad.search.Cstar cStaR = new edu.cmu.tetrad.search.Cstar();

        CpdagAlgorithm algorithm;

        switch (parameters.getInt(Params.CSTAR_CPDAG_ALGORITHM)) {
            case 1:
                algorithm = CpdagAlgorithm.PC_STABLE;
                break;
            case 2:
                algorithm = CpdagAlgorithm.FGES;
                break;
            case 3:
                algorithm = CpdagAlgorithm.BOSS;
                break;
            case 4:
                algorithm = CpdagAlgorithm.RESTRICTED_BOSS;
                break;
            default:
                throw new IllegalArgumentException("Unknown CPDAG algorithm: " + parameters.getInt(Params.CSTAR_CPDAG_ALGORITHM));
        }

        cStaR.setParallelized(parameters.getBoolean(Params.PARALLELIZED));
        cStaR.setNumSubsamples(parameters.getInt(Params.NUM_SUBSAMPLES));
        cStaR.setqFrom(parameters.getInt(Params.CSTAR_Q));
        cStaR.setqTo(parameters.getInt(Params.CSTAR_Q));
        cStaR.setSelectionAlpha(parameters.getDouble(Params.SELECTION_MIN_EFFECT));
        cStaR.setqIncrement(1);
        cStaR.setCpdagAlgorithm(CpdagAlgorithm.PC_STABLE);
        cStaR.setSampleStyle(SampleStyle.SPLIT);
        cStaR.setVerbose(parameters.getBoolean(Params.VERBOSE));

        List<Node> possibleEffects = new ArrayList<>();

        String targetNames = parameters.getString(Params.TARGETS);

        if (targetNames.trim().equalsIgnoreCase("")) {
            throw new IllegalStateException("Please specify target name(s).");
        }

        if (targetNames.trim().equalsIgnoreCase("all")) {
            for (String name : dataSet.getVariableNames()) {
                possibleEffects.add(dataSet.getVariable(name));
            }
        } else {
            String[] names = targetNames.split(",");

            for (String name : names) {
                possibleEffects.add(dataSet.getVariable(name.trim()));
            }
        }

        List<Node> possibleCauses = new ArrayList<>(dataSet.getVariables());
//        possibleCauses.removeAll(possibleEffects);

        if (!(dataSet instanceof DataSet)) {
            throw new IllegalArgumentException("Expecting tabular data for CStaR.");
        }

        LinkedList<LinkedList<edu.cmu.tetrad.search.Cstar.Record>> allRecords
                = cStaR.getRecords((DataSet) dataSet, possibleCauses, possibleEffects, test.getTest(dataSet, parameters));

        if (allRecords.isEmpty()) {
            throw new IllegalStateException("There were no records.");
        }

        records = allRecords.getLast();

        TetradLogger.getInstance().forceLogMessage("CStaR Table");
        TetradLogger.getInstance().forceLogMessage(cStaR.makeTable(edu.cmu.tetrad.search.Cstar.cStar(allRecords), true));
        TetradLogger.getInstance().forceLogMessage("\nStability Selection Table");
        TetradLogger.getInstance().forceLogMessage(cStaR.makeTable(getRecords(), true));

        return cStaR.makeGraph(this.getRecords());
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
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
        List<String> parameters = new ArrayList<>(test.getParameters());
        parameters.add(Params.SELECTION_MIN_EFFECT);
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.NUM_SUBSAMPLES);
        parameters.add(Params.TARGETS);
        parameters.add(Params.CSTAR_Q);
        parameters.add(Params.PARALLELIZED);
        parameters.add(Params.VERBOSE);
        return parameters;
    }

    public LinkedList<edu.cmu.tetrad.search.Cstar.Record> getRecords() {
        return this.records;
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }
}
