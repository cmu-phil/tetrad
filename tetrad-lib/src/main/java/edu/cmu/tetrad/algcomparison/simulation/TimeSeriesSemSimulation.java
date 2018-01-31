package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.TimeLagGraph;
import edu.cmu.tetrad.search.TimeSeriesUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author jdramsey
 * @author Daniel Malinsky
 */
public class TimeSeriesSemSimulation implements Simulation, HasKnowledge {

    static final long serialVersionUID = 23L;
    private final RandomGraph randomGraph;
    private List<Graph> graphs = new ArrayList<>();
    private List<DataSet> dataSets = new ArrayList<>();
    private IKnowledge knowledge;

    public TimeSeriesSemSimulation(RandomGraph randomGraph) {
        if (randomGraph == null) {
            throw new NullPointerException();
        }
        this.randomGraph = randomGraph;
    }

    @Override
    public void createData(Parameters parameters) {
        dataSets = new ArrayList<>();
        graphs = new ArrayList<>();

        Graph graph = randomGraph.createGraph(parameters);
        graph = TimeSeriesUtils.graphToLagGraph(graph, parameters.getInt("numLags"));
        topToBottomLayout((TimeLagGraph) graph);
        this.knowledge = TimeSeriesUtils.getKnowledge(graph);

        for (int i = 0; i < parameters.getInt("numRuns"); i++) {
            if (parameters.getBoolean("differentGraphs") && i > 0) {
                graph = randomGraph.createGraph(parameters);
                graph = TimeSeriesUtils.graphToLagGraph(graph, 2);
                topToBottomLayout((TimeLagGraph) graph);
            }

            graphs.add(graph);

            SemPm pm = new SemPm(graph);
            SemIm im = new SemIm(pm, parameters);

            final int sampleSize = parameters.getInt("sampleSize");

            boolean saveLatentVars = parameters.getBoolean("saveLatentVars");
            DataSet dataSet = im.simulateData(sampleSize, saveLatentVars);

            int numLags = ((TimeLagGraph) graph).getMaxLag();

            dataSet = TimeSeriesUtils.createLagData(dataSet, numLags);

            dataSet.setName("" + (i + 1));
            dataSet.setKnowledge(knowledge.copy());
            dataSets.add(dataSet);

//            LargeScaleSimulation sim = new LargeScaleSimulation(graph);
//            if (parameters.getDouble("coefHigh") > 0.80) {
//                System.out.println("Coefficients have been set (perhaps by default) too " +
//                        "high for stationary time series.");
//                System.out.println("Setting coefficient range to [0.20,0.60].");
//                sim.setCoefRange(0.20, 0.60);
//            } else sim.setCoefRange(parameters.getDouble("coefLow"), parameters.getDouble("coefHigh"));
//            boolean isStableTetradMatrix;
//            int attempt = 1;
//            int tierSize = parameters.getInt("numMeasures") + parameters.getInt("numLatents"); //params.getNumVars();
//            int[] sub = new int[tierSize];
//            int[] sub2 = new int[tierSize];
//            for (int j = 0; j < tierSize; j++) {
//                sub[j] = j;
//                sub2[j] = tierSize + j;
//            }
//            DataSet dataSet;
//            do {
//                dataSet = sim.simulateDataFisher(parameters.getInt("sampleSize")); //params.getSampleSize());
//
//                TetradMatrix coefMat = new TetradMatrix(sim.getCoefficientMatrix());
//                TetradMatrix B = coefMat.getSelection(sub, sub);
//                TetradMatrix Gamma1 = coefMat.getSelection(sub2, sub);
//                TetradMatrix Gamma0 = TetradMatrix.identity(tierSize).minus(B);
//                TetradMatrix A1 = Gamma0.inverse().times(Gamma1);
//
//                isStableTetradMatrix = TimeSeriesUtils.allEigenvaluesAreSmallerThanOneInModulus(A1);
//                attempt++;
//            } while ((!isStableTetradMatrix) && attempt <= 5);
//            if (!isStableTetradMatrix) {
//                System.out.println("%%%%%%%%%% WARNING %%%%%%%% not a stable coefficient matrix, forcing coefs to [0.15,0.3]");
//                System.out.println("Made " + (attempt - 1) + " attempts to get stable matrix.");
//                sim.setCoefRange(0.15, 0.3);
//                dataSet = sim.simulateDataFisher(parameters.getInt("sampleSize"));//params.getSampleSize());
//            } //else System.out.println("Coefficient matrix is stable.");
//            dataSet.setName("" + (i + 1));
//            dataSet.setKnowledge(knowledge.copy());
//            dataSets.add(dataSet);
        }
    }

    @Override
    public DataModel getDataModel(int index) {
        return dataSets.get(index);
    }

    @Override
    public Graph getTrueGraph(int index) {
        return graphs.get(index);
    }

    @Override
    public String getDescription() {
        return "Linear, Gaussian Dynamic SEM (1-lag SVAR) simulation";
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        parameters.add("numLags");

        if (!(randomGraph instanceof SingleGraph)) {
            parameters.addAll(randomGraph.getParameters());
        }

        parameters.addAll(SemIm.getParameterNames());

        parameters.add("standardize");
        parameters.add("measurementVariance");
        parameters.add("numRuns");
        parameters.add("differentGraphs");
        parameters.add("sampleSize");
        parameters.add("saveLatentVars");

        return parameters;

    }

    @Override
    public int getNumDataModels() {
        return dataSets.size();
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public static void topToBottomLayout(TimeLagGraph graph) {

        int xStart = 65;
        int yStart = 50;
        int xSpace = 100;
        int ySpace = 100;
        List<Node> lag0Nodes = graph.getLag0Nodes();

        Collections.sort(lag0Nodes, new Comparator<Node>() {
            public int compare(Node o1, Node o2) {
                return o1.getCenterX() - o2.getCenterX();
            }
        });

        int x = xStart - xSpace;

        for (Node node : lag0Nodes) {
            x += xSpace;
            int y = yStart - ySpace;
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = graph.getMaxLag(); lag >= 0; lag--) {
                y += ySpace;
                Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find " + _node);
                    continue;
                }

                _node.setCenterX(x);
                _node.setCenterY(y);
            }
        }
    }

}
