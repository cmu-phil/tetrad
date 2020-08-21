package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.util.Params.*;
import static edu.cmu.tetrad.util.Params.FASK_DELTA;

/**
 * Created by user on 3/29/18.
 */
public class FaskVote {

    private final SemBicScoreMultiFas score;

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

    public FaskVote(List<DataSet> dataSets, SemBicScoreMultiFas score) {
        this.dataSets = dataSets;
        this.score = score;
    }

    //======================================== PUBLIC METHODS ====================================//

    public Graph search() {
        long start = System.currentTimeMillis();

        List<Graph> graphs = new ArrayList<>();

        for (DataSet dataSet1 : dataSets) {
            Fask search = new Fask(dataSet1, new IndTestFisherZ(dataSet1, 0.01));
            search.setDepth(parameters.getInt(DEPTH));
            search.setUseFasAdjacencies(parameters.getBoolean(USE_FAS_ADJACENCIES));
            search.setSkewEdgeThreshold(parameters.getDouble(SKEW_EDGE_THRESHOLD));
            search.setTwoCycleScreeningThreshold(parameters.getDouble(TWO_CYCLE_SCREENING_THRESHOLD));
            search.setTwoCycleTestingAlpha(parameters.getDouble(TWO_CYCLE_TESTING_ALPHA));
            search.setDelta(parameters.getDouble(FASK_DELTA));
            graphs.add(search.search());
        }

        List<Node> nodes = dataSets.get(0).getVariables();
        Graph out = new EdgeListGraph(nodes);

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                int count = 0;

                for (Graph graph : graphs) {
                    if (!graph.isAdjacentTo(nodes.get(i), nodes.get(j))) continue;
                    Edge edge = graph.getEdge(nodes.get(i), nodes.get(j));

                    if (edge.pointsTowards(nodes.get(j))) {
                        count++;
                    }
                }

                if (count == 0) continue;

                if (count >= 0.6 * graphs.size()) {
                    out.addDirectedEdge(nodes.get(i), nodes.get(j));
                } else if (count <= 0.4 * graphs.size()) {
                    out.addDirectedEdge(nodes.get(j), nodes.get(i));
                } else {
                    out.addUndirectedEdge(nodes.get(i), nodes.get(j));
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
