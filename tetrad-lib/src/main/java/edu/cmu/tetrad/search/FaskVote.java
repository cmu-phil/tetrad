package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.algorithm.multi.ImagesSemBic;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static edu.cmu.tetrad.util.Params.*;
import static edu.cmu.tetrad.util.StatUtils.skewness;
import static java.lang.Math.abs;

/**
 *
 */
public class FaskVote {

    // An initial graph to orient, skipping the adjacency step.
    private Graph initialGraph = null;

    // Knowledge the the search will obey, of forbidden and required edges.
    private IKnowledge knowledge = new Knowledge2();

    // A threshold for including extra adjacencies due to skewness.
    private double extraEdgeThreshold = 0.3;

    // True if FAS adjacencies should be included in the output.
    private boolean useFasAdjacencies = true;

    // True if skew adjacencies should be included in the output.
    private boolean useSkewAdjacencies = true;

    // Threshold for reversing casual judgments for negative coefficients.
    private double delta = -0.2;

    private final List<DataSet> dataSets;

    public FaskVote(List<DataSet> dataSets) {
        this.dataSets = dataSets;
    }

    //======================================== PUBLIC METHODS ====================================//

    public Graph search(Parameters parameters) {
        List<Graph> graphs = new ArrayList<>();

        ImagesSemBic imagesSemBic = new ImagesSemBic();
        List<DataModel> _dataSets = new ArrayList<>(dataSets);
        Graph external = imagesSemBic.search(_dataSets, parameters);

        for (DataSet dataSet1 : dataSets) {
            Fask fask;

            if (parameters.getInt(FASK_ADJACENCY_METHOD) != 3) {
                fask = new Fask(dataSet1, new FisherZ().getTest(dataSet1, parameters));
            } else if (parameters.getInt(FASK_ADJACENCY_METHOD) == 3) {
                fask = new Fask(dataSet1, new SemBicTest().getTest(dataSet1, parameters));
            } else {
                throw new IllegalStateException("That adacency method for FASK was not configured: "
                        + parameters.getInt(FASK_ADJACENCY_METHOD));
            }

            Fask.AdjacencyMethod adjacencyMethod = Fask.AdjacencyMethod.FAS_STABLE;

            switch (parameters.getInt(FASK_ADJACENCY_METHOD)) {
                case 1:
                    adjacencyMethod = Fask.AdjacencyMethod.FAS_STABLE;
                case 2:
                    adjacencyMethod = Fask.AdjacencyMethod.FAS_STABLE_CONCURRENT;
                case 3:
                    adjacencyMethod = Fask.AdjacencyMethod.FGES;
                case 4:
                    adjacencyMethod = Fask.AdjacencyMethod.EXTERNAL_GRAPH;
            }

            fask.setDepth(parameters.getInt(DEPTH));
            fask.setAdjacencyMethod(Fask.AdjacencyMethod.FAS_STABLE);
            fask.setSkewEdgeThreshold(parameters.getDouble(SKEW_EDGE_THRESHOLD));
            fask.setTwoCycleScreeningThreshold(parameters.getDouble(TWO_CYCLE_SCREENING_THRESHOLD));
            fask.setTwoCycleTestingAlpha(parameters.getDouble(TWO_CYCLE_TESTING_ALPHA));
            fask.setDelta(parameters.getDouble(FASK_DELTA));
            fask.setEmpirical(!parameters.getBoolean(FASK_NONEMPIRICAL));
            fask.setExternalGraph(external);
            fask.setAdjacencyMethod(adjacencyMethod);
            Graph search = fask.search();
            search = GraphUtils.replaceNodes(search, dataSets.get(0).getVariables());
            graphs.add(search);
        }

        List<Node> nodes = dataSets.get(0).getVariables();
        Graph out = new EdgeListGraph(nodes);

        double[][][] D = new double[dataSets.size()][][];

        for (int k = 0; k < dataSets.size(); k++) {
            D[k] =  dataSets.get(k).getDoubleData().transpose().toArray();
        }

        Map<String, Integer> indices = new HashMap<>();

        for (int i = 0; i < nodes.size(); i++) {
            indices.put(nodes.get(i).getName(), i);
        }


        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                if (i == j) continue;
                Edge edge1 = Edges.directedEdge(nodes.get(i), nodes.get(j));
                Edge edge2 = Edges.directedEdge(nodes.get(j), nodes.get(i));
                double count1 = 0, count2 = 0;

                for (int k = 0; k < graphs.size(); k++) {
                    if (graphs.get(k).containsEdge(edge1)) {
                        count1++;
//
//                        double[] d1 = D[k][indices.get(edge1.getNode1().getName())];
//                        double[] d2 = D[k][indices.get(edge1.getNode2().getName())];
//                        double s = abs(skewness(d1)) * abs(skewness(d2));
//
//                        count += s;
                    }

                    if (graphs.get(k).containsEdge(edge2)) {
                        count2++;
//
//                        double[] d1 = D[k][indices.get(edge1.getNode1().getName())];
//                        double[] d2 = D[k][indices.get(edge1.getNode2().getName())];
//                        double s = abs(skewness(d1)) * abs(skewness(d2));
//
//                        count += s;
                    }
                }


                if (count1 > count2) {// / (double) graphs.size() >= parameters.getDouble(ACCEPTANCE_PROPORTION)) {
                    out.addEdge(edge1);
                } else if (count2 > count1) {// / (double) graphs.size() >= parameters.getDouble(ACCEPTANCE_PROPORTION)) {
                    out.addEdge(edge2);
                }
            }
        }

        return out;
    }

    /**
     * @return the current knowledge.
     */
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    /**
     * @param knowledge Knowledge of forbidden and required edges.
     */
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    //======================================== PRIVATE METHODS ===================================//

    public Graph getInitialGraph() {
        return initialGraph;
    }

    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    public double getExtraEdgeThreshold() {
        return extraEdgeThreshold;
    }

    public void setExtraEdgeThreshold(double extraEdgeThreshold) {
        this.extraEdgeThreshold = extraEdgeThreshold;
    }

    public boolean isUseFasAdjacencies() {
        return useFasAdjacencies;
    }

    public void setUseFasAdjacencies(boolean useFasAdjacencies) {
        this.useFasAdjacencies = useFasAdjacencies;
    }

    public boolean isUseSkewAdjacencies() {
        return useSkewAdjacencies;
    }

    public void setUseSkewAdjacencies(boolean useSkewAdjacencies) {
        this.useSkewAdjacencies = useSkewAdjacencies;
    }

    public double getDelta() {
        return delta;
    }

    public void setDelta(double delta) {
        this.delta = delta;
    }
}
