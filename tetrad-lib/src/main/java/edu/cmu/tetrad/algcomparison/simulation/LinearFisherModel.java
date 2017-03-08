package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.utils.TakesData;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.LargeScaleSimulation;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.dist.Split;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author jdramsey
 */
public class LinearFisherModel implements Simulation, TakesData {
    static final long serialVersionUID = 23L;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<Graph> graphs = new ArrayList<>();
    private RandomGraph randomGraph;
    private List<Node> shuffledOrder;
    private List<DataModel> shocks = null;

    public LinearFisherModel(RandomGraph graph) {
        this.randomGraph = graph;
        this.shocks = null;
    }

    public LinearFisherModel(RandomGraph graph, List<DataModel> shocks) {
        this.randomGraph = graph;
        this.shocks = shocks;

        if (shocks != null) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "The initial dataset you've provided will be used as initial shocks" +
                            "\nfor a Fisher model.");

            for (DataModel _shocks : shocks) {
                if (_shocks == null) throw new NullPointerException("Dataset containing shocks must not be null.");
                DataSet dataSet = (DataSet) _shocks;
                if (!dataSet.isContinuous()) {
                    throw new IllegalArgumentException("Dataset containing shocks must be continuous tabular.");
                }
            }
        }
    }

    @Override
    public void createData(Parameters parameters) {
        dataSets = new ArrayList<>();
        graphs = new ArrayList<>();
        Graph graph = randomGraph.createGraph(parameters);

        for (int i = 0; i < parameters.getInt("numRuns"); i++) {
            System.out.println("Simulating dataset #" + (i + 1));

            if (shocks != null && shocks.size() > 0) {
                parameters.set("numVars", shocks.get(0).getVariables().size());
            }

            if (parameters.getBoolean("differentGraphs") && i > 0) {
                graph = randomGraph.createGraph(parameters);
            }

            if (shocks != null && shocks.size() > 0) {
                graph.setNodes(shocks.get(0).getVariables());
            }

            graphs.add(graph);

            int[] tiers = new int[graph.getNodes().size()];
            for (int j = 0; j < tiers.length; j++) tiers[j] = j;

            LargeScaleSimulation simulator = new LargeScaleSimulation(
                    graph, graph.getNodes(), tiers);
            simulator.setCoefRange(
                    parameters.getDouble("coefLow"),
                    parameters.getDouble("coefHigh"));
            simulator.setVarRange(
                    parameters.getDouble("varLow"),
                    parameters.getDouble("varHigh"));
            simulator.setCoefSymmetric(parameters.getBoolean("coefSymmetric"));
            simulator.setMeanRange(
                    parameters.getDouble("meanLow"),
                    parameters.getDouble("meanHigh"));

            simulator.setVerbose(parameters.getBoolean("verbose"));

            DataSet dataSet;

//            if (shocks == null) {
//                dataSet = simulator.simulateDataFisher(
//                        simulator.getUncorrelatedGaussianShocks(parameters.getInt("sampleSize")),
//                        parameters.getInt("intervalBetweenShocks"),
//                        parameters.getInt("intervalBetweenRecordings"),
//                        parameters.getDouble("fisherEpsilon")
//                );
//            } else {
//                DataSet _shocks = (DataSet) shocks.get(i);
//
//                dataSet = simulator.simulateDataFisher(
//                        _shocks.getDoubleData().toArray(),
//                        parameters.getInt("intervalBetweenShocks"),
//                        parameters.getInt("intervalBetweenRecordings"),
//                        parameters.getDouble("fisherEpsilon")
//                );
//            }

            if (shocks == null) {
                dataSet = simulator.simulateDataFisher(
                        parameters.getInt("intervalBetweenShocks"),
                        parameters.getInt("intervalBetweenRecordings"),
                        parameters.getInt("sampleSize"),
                        parameters.getDouble("fisherEpsilon")
                );
            } else {
                DataSet _shocks = (DataSet) shocks.get(i);

                dataSet = simulator.simulateDataFisher(
                        _shocks.getDoubleData().toArray(),
                        parameters.getInt("intervalBetweenShocks"),
                        parameters.getDouble("fisherEpsilon")
                );
            }

            dataSet.setName("" + (i + 1));

            if (parameters.getDouble("percentDiscrete") > 0.0) {
                if (this.shuffledOrder == null) {
                    List<Node> shuffledNodes = new ArrayList<>(dataSet.getVariables());
                    Collections.shuffle(shuffledNodes);
                    this.shuffledOrder = shuffledNodes;
                }

                Discretizer discretizer = new Discretizer(dataSet);

                for (int k = 0; k < shuffledOrder.size() * parameters.getDouble("percentDiscrete") * 0.01; k++) {
                    discretizer.equalIntervals(dataSet.getVariable(shuffledOrder.get(k).getName()),
                            parameters.getInt("numCategories"));
                }

                String name = dataSet.getName();
                dataSet = discretizer.discretize();
                dataSet.setName(name);
            }

            dataSets.add(DataUtils.restrictToMeasured(dataSet));
        }
    }

    @Override
    public DataSet getDataSet(int index) {
        return dataSets.get(index);
    }

    @Override
    public Graph getTrueGraph(int index) {
        return graphs.get(index);
    }

    @Override
    public String getDescription() {
        return "Large scale SEM simulation";
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.addAll(randomGraph.getParameters());

        if (shocks != null) {
            parameters.remove("numMeasures");
            parameters.remove("numLatents");
        }

        parameters.add("coefLow");
        parameters.add("coefHigh");
        parameters.add("varLow");
        parameters.add("varHigh");
        parameters.add("verbose");
        parameters.add("coefSymmetric");
        parameters.add("numRuns");
        parameters.add("percentDiscrete");
        parameters.add("numCategories");
        parameters.add("differentGraphs");
        parameters.add("sampleSize");
        parameters.add("intervalBetweenShocks");
        parameters.add("intervalBetweenRecordings");
        parameters.add("fisherEpsilon");
        return parameters;
    }

    @Override
    public int getNumDataSets() {
        return dataSets.size();
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }
}
