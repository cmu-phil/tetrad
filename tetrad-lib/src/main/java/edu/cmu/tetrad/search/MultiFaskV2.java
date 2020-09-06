package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.algorithm.multi.ImagesSemBic;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static edu.cmu.tetrad.util.Params.*;
import static edu.cmu.tetrad.util.StatUtils.*;
import static java.lang.Math.*;
import static java.lang.Math.abs;

/**
 * @author Madelyn Glymour
 * @author Joseph Ramsey 9/5/2020
 */
public class MultiFaskV2 {

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
    private double delta = -0.1;

    private final List<DataSet> dataSets;

    // Alpha for checking 2-cycles.
    private double twoCycleScreeningThreshold = 0.01;

    // Alpha for checking 2-cycles.
    private double twoCycleTestingAlpha = 0.01;

    // Depth for combinatorial steps.
    private int depth = -1;

    public MultiFaskV2(List<DataSet> dataSets) {
        this.dataSets = dataSets;
    }

    //======================================== PUBLIC METHODS ====================================//

    public Graph search(Parameters parameters) {
        List<DataModel> _dataSets = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            _dataSets.add(DataUtils.standardizeData(dataSet));
        }

        ImagesSemBic imagesSemBic = new ImagesSemBic();
        Graph G0 = imagesSemBic.search(_dataSets, parameters);

        List<Node> V = dataSets.get(0).getVariables();
        Graph G = new EdgeListGraph(V);

        List<Graph> fasks = new ArrayList<>();

        List<Node> nodes = G0.getNodes();

        for (DataSet dataSet : dataSets) {
            Fask fask = new Fask(dataSet, new IndTestFisherZ(dataSet, 0.001));
            fask.setExternalGraph(G0);
            fask.setAdjacencyMethod(Fask.AdjacencyMethod.EXTERNAL_GRAPH);
            fask.setEmpirical(!parameters.getBoolean(FASK_NONEMPIRICAL));
            fask.setLeftRight(Fask.LeftRight.FASK2);
            fask.setSkewEdgeThreshold(parameters.getDouble(SKEW_EDGE_THRESHOLD));
            fask.setDepth(parameters.getInt(DEPTH));
            fask.setDelta(parameters.getDouble(FASK_DELTA));
            fask.setTwoCycleScreeningThreshold(parameters.getDouble(TWO_CYCLE_SCREENING_THRESHOLD));
            fask.setTwoCycleTestingAlpha(parameters.getDouble(TWO_CYCLE_TESTING_ALPHA));
            Graph g = fask.search();
            g = GraphUtils.replaceNodes(g, nodes);
            fasks.add(g);
        }

        for (Edge edge : G0.getEdges()) {
            Node X = edge.getNode1();
            Node Y = edge.getNode2();

            Edge dir1 = Edges.directedEdge(X, Y);
            Edge dir2 = Edges.directedEdge(Y, X);

            int sum1 = 0;
            int sum2 = 0;

            for (Graph g : fasks) {
                if (g.containsEdge(dir1)) {
                    sum1++;
                }

                if (g.containsEdge(dir2)) {
                    sum2++;
                }
            }

            double mean1 = sum1 / (double) dataSets.size();
            double mean2 = sum2 / (double) dataSets.size()       ;

            System.out.println(X + " " + Y + " " + mean1 + " " + mean2);

            if (mean1 == 0.5 && mean2 == 0.5) {
                G.addUndirectedEdge(X, Y);
            } else {
                if (mean1 > 0.5) {
                    G.addDirectedEdge(X, Y);
                }

                if (mean2 > 0.5) {
                    G.addDirectedEdge(Y, X);
                }
            }
        }

        return G;
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

    public void setTwoCycleTestingAlpha(double twoCycleTestingAlpha) {
        this.twoCycleTestingAlpha = twoCycleTestingAlpha;
    }

    public double getTwoCycleScreeningThreshold() {
        return twoCycleScreeningThreshold;
    }

    public void setTwoCycleScreeningThreshold(double twoCycleScreeningThreshold) {
        this.twoCycleScreeningThreshold = twoCycleScreeningThreshold;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}
