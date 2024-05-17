package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.algcomparison.algorithm.multi.Images;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fask;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.util.Params.*;

/**
 * <p>Runs IMaGES on a list of algorithms and then produces a graph over the
 * ImaGES adjacencies where each edge orientation is voted on by running FASK on each dataset in turn and voting on edge
 * orientation.</p>
 *
 * <p>Moving this to the work_in_progress directory because this functionality
 * can be generalized to arbitrary GraphSearch algorithms, not just FASK, given an adjacency graph, as an alternative to
 * bootstrapping.</p>
 *
 * @author Madelyn Glymour
 * @author josephramsey 9/5/2020
 * @version $Id: $Id
 */
public class FaskVote {
    private final IndependenceWrapper test;
    private final ScoreWrapper score;
    private final List<DataSet> dataSets;
    private Knowledge knowledge = new Knowledge();

    /**
     * Constructor.
     *
     * @param dataSets The datasets being searched over. A composite graph will be generated.
     * @param score    The score to use.
     * @param test     The test to use.
     */
    public FaskVote(List<DataSet> dataSets, ScoreWrapper score, IndependenceWrapper test) {
        this.dataSets = dataSets;
        this.score = score;
        this.test = test;
    }

    //======================================== PUBLIC METHODS ====================================//

    /**
     * Does the search.
     *
     * @param parameters The parameers.
     * @return The composite graph.
     * @see Parameters
     */
    public Graph search(Parameters parameters) {
        List<DataModel> _dataSets = new ArrayList<>();

        for (DataSet dataSet : this.dataSets) {
            _dataSets.add(DataTransforms.standardizeData(dataSet));
        }

        Images imagesSemBic = new Images(score);
        imagesSemBic.setKnowledge(this.knowledge);
        Graph G0 = imagesSemBic.search(_dataSets, parameters);

        List<Node> V = this.dataSets.get(0).getVariables();
        Graph G = new EdgeListGraph(V);

        List<Graph> fasks = new ArrayList<>();

        List<Node> nodes = G0.getNodes();

        for (DataSet dataSet : this.dataSets) {
            Fask fask = new Fask(dataSet, this.score.getScore(dataSet, parameters));
            fask.setExternalGraph(GraphUtils.undirectedGraph(G0));
            fask.setLeftRight(Fask.LeftRight.FASK2);
            fask.setExtraEdgeThreshold(parameters.getDouble(SKEW_EDGE_THRESHOLD));
            fask.setDepth(parameters.getInt(DEPTH));
            fask.setDelta(parameters.getDouble(FASK_DELTA));
            fask.setAlpha(parameters.getDouble(ORIENTATION_ALPHA));
            fask.setKnowledge(this.knowledge);


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
     * <p>Setter for the field <code>knowledge</code>.</p>
     *
     * @param knowledge Knowledge of forbidden and required edges.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }
}
