package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.algorithm.multi.ImagesSemBic;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.SemBicDTest;
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.util.Params.*;

/**
 */
public class FaskVote {

    // An initial graph to orient, skipping the adjacency step.
    private Graph initialGraph = null;

    // Elapsed time of the search, in milliseconds.
    private long elapsed = 0;

    // For the SEM BIC score, for the Fast Adjacency Search.
    private double penaltyDiscount = 1;

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
    private Parameters parameters;

    public FaskVote(List<DataSet> dataSets) {
        this.dataSets = dataSets;
    }

    //======================================== PUBLIC METHODS ====================================//

    public Graph search() {
        long start = System.currentTimeMillis();

        List<Graph> graphs = new ArrayList<>();
        List<Fask> fasks = new ArrayList<>();

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

            ImagesSemBic imagesSemBic = new ImagesSemBic();

            List<DataModel> _dataSets = new ArrayList<>();
            for (DataSet dataSet : dataSets) _dataSets.add((DataModel) dataSet);
            Graph external = imagesSemBic.search(_dataSets, parameters);

            fask.setDepth(parameters.getInt(DEPTH));
            fask.setAdjacencyMethod(Fask.AdjacencyMethod.FAS_STABLE);
            fask.setSkewEdgeThreshold(parameters.getDouble(SKEW_EDGE_THRESHOLD));
            fask.setTwoCycleScreeningThreshold(parameters.getDouble(TWO_CYCLE_SCREENING_THRESHOLD));
            fask.setTwoCycleTestingAlpha(parameters.getDouble(TWO_CYCLE_TESTING_ALPHA));
            fask.setDelta(parameters.getDouble(FASK_DELTA));
            fask.setEmpirical(!parameters.getBoolean(FASK_NONEMPIRICAL));
//            fask.setExternalGraph(external);
            fask.setAdjacencyMethod(Fask.AdjacencyMethod.FGES);
            fasks.add(fask);
            Graph search = fask.search();
            search = GraphUtils.replaceNodes(search, dataSets.get(0).getVariables());
            graphs.add(search);
        }

        List<Node> nodes = dataSets.get(0).getVariables();
        Graph out = new EdgeListGraph(nodes);

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;
                Edge edge = Edges.directedEdge(nodes.get(i), nodes.get(j));
                int count = 0;
                int total = 0;

                for (Graph graph : graphs) {
                    if (graph.containsEdge(edge)) {
                        count++;
                    }

                    if (graph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                        total++;
                    }
                }

                if (count / (double) total > parameters.getDouble(ACCEPTANCE_PROPORTION)) {
                    out.addEdge(edge);
                }
            }
        }

        elapsed = System.currentTimeMillis() - start;

        return out;
    }

    /**
     * @return Returns the penalty discount used for the adjacency search. The default is 1,
     * though a higher value is recommended, say, 2, 3, or 4.
     */
    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    /**
     * @param penaltyDiscount Sets the penalty discount used for the adjacency search.
     *                        The default is 1, though a higher value is recommended, say,
     *                        2, 3, or 4.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
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

    public int getDepth() {
        // For the Fast Adjacency Search.
        int depth = -1;
        return depth;
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

    public void setParameters(Parameters parameters) {
        this.parameters = parameters;
    }
}
