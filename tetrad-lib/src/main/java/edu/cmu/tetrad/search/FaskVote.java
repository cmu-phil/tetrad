package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.algorithm.multi.ImagesSemBic;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
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
    // Knowledge the the search will obey, of forbidden and required edges.
    private IKnowledge knowledge = new Knowledge2();

    private final List<DataSet> dataSets;

    public FaskVote(List<DataSet> dataSets, IndependenceWrapper test) {
        this.dataSets = dataSets;
        this.test = test;
    }

    //======================================== PUBLIC METHODS ====================================//

    public Graph search(Parameters parameters) {
        List<DataModel> _dataSets = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            _dataSets.add(DataUtils.standardizeData(dataSet));
        }

        ImagesSemBic imagesSemBic = new ImagesSemBic();
        imagesSemBic.setKnowledge(knowledge);
        Graph G0 = imagesSemBic.search(_dataSets, parameters);

        List<Node> V = dataSets.get(0).getVariables();
        Graph G = new EdgeListGraph(V);

        List<Graph> fasks = new ArrayList<>();

        List<Node> nodes = G0.getNodes();

        for (DataSet dataSet : dataSets) {
            Fask fask = new Fask(dataSet, test.getTest(dataSet, parameters));
            fask.setExternalGraph(GraphUtils.undirectedGraph(G0));
            fask.setAdjacencyMethod(Fask.AdjacencyMethod.EXTERNAL_GRAPH);
            fask.setEmpirical(!parameters.getBoolean(FASK_NONEMPIRICAL));
            fask.setLeftRight(Fask.LeftRight.FASK2);
            fask.setSkewEdgeThreshold(parameters.getDouble(SKEW_EDGE_THRESHOLD));
            fask.setDepth(parameters.getInt(DEPTH));
            fask.setDelta(parameters.getDouble(FASK_DELTA));
            fask.setTwoCycleScreeningCutoff(parameters.getDouble(TWO_CYCLE_SCREENING_THRESHOLD));
            fask.setOrientationAlpha(parameters.getDouble(ORIENTATION_ALPHA));
            fask.setKnowledge(knowledge);


//            Lingam lingam = new Lingam();
//            Graph g = lingam.search(dataSet);
//
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
            int count = 0;

            for (Graph g : fasks) {
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

            double mean1 = sum1 / (double) count;
            double mean2 = sum2 / (double) count;

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
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }
}
