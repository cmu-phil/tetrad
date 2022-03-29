package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.algorithm.multi.ImagesSemBic;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.util.Params.*;

/**
 * @author Madelyn Glymour
 * @author Joseph Ramsey 9/5/2020
 */
public class FaskVote {

    private final IndependenceWrapper test;
    private final ScoreWrapper score;
    // Knowledge the the search will obey, of forbidden and required edges.
    private IKnowledge knowledge = new Knowledge2();

    private final List<DataSet> dataSets;

    public FaskVote(final List<DataSet> dataSets, final ScoreWrapper score, final IndependenceWrapper test) {
        this.dataSets = dataSets;
        this.score = score;
        this.test = test;
    }

    //======================================== PUBLIC METHODS ====================================//

    public Graph search(final Parameters parameters) {
        final List<DataModel> _dataSets = new ArrayList<>();

        for (final DataSet dataSet : this.dataSets) {
            _dataSets.add(DataUtils.standardizeData(dataSet));
        }

        final ImagesSemBic imagesSemBic = new ImagesSemBic();
        imagesSemBic.setKnowledge(this.knowledge);
        final Graph G0 = imagesSemBic.search(_dataSets, parameters);

        final List<Node> V = this.dataSets.get(0).getVariables();
        final Graph G = new EdgeListGraph(V);

        final List<Graph> fasks = new ArrayList<>();

        final List<Node> nodes = G0.getNodes();

        for (final DataSet dataSet : this.dataSets) {
            final Fask fask = new Fask(dataSet,
                    this.score.getScore(dataSet, parameters),
                    this.test.getTest(dataSet, parameters));
            fask.setExternalGraph(GraphUtils.undirectedGraph(G0));
            fask.setAdjacencyMethod(Fask.AdjacencyMethod.EXTERNAL_GRAPH);
            fask.setEmpirical(!parameters.getBoolean(FASK_NONEMPIRICAL));
            fask.setLeftRight(Fask.LeftRight.FASK2);
            fask.setSkewEdgeThreshold(parameters.getDouble(SKEW_EDGE_THRESHOLD));
            fask.setDepth(parameters.getInt(DEPTH));
            fask.setDelta(parameters.getDouble(FASK_DELTA));
            fask.setTwoCycleScreeningCutoff(parameters.getDouble(TWO_CYCLE_SCREENING_THRESHOLD));
            fask.setOrientationAlpha(parameters.getDouble(ORIENTATION_ALPHA));
            fask.setKnowledge(this.knowledge);


//            Lingam lingam = new Lingam();
//            Graph g = lingam.search(dataSet);
//
            Graph g = fask.search();
            g = GraphUtils.replaceNodes(g, nodes);
            fasks.add(g);
        }

        for (final Edge edge : G0.getEdges()) {
            final Node X = edge.getNode1();
            final Node Y = edge.getNode2();

            final Edge dir1 = Edges.directedEdge(X, Y);
            final Edge dir2 = Edges.directedEdge(Y, X);

            int sum1 = 0;
            int sum2 = 0;
            int count = 0;

            for (final Graph g : fasks) {
                if (g.containsEdge(dir1)) {
                    sum1++;
                }

                if (g.containsEdge(dir2)) {
                    sum2++;
                }

                if (g.containsEdge(dir1) || g.containsEdge(dir2)) {
                    count++;
                }
            }

            final double mean1 = sum1 / (double) count;
            final double mean2 = sum2 / (double) count;

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
     * @param knowledge Knowledge of forbidden and required edges.
     */
    public void setKnowledge(final IKnowledge knowledge) {
        this.knowledge = knowledge;
    }
}
