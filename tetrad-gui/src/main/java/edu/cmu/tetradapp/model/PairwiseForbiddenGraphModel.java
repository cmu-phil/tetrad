/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fask;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A knowledge box model that orients every pair of variables using the FASK pairwise left-right
 * statistic and forbids, for each pair, the disfavored direction. The favored directions form a
 * complete directed graph (a tournament) over the variables; the knowledge consists exactly of
 * the backward edges of that tournament.
 */
public class PairwiseForbiddenGraphModel extends KnowledgeBoxModel implements KnowledgeSourceGraph {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The largest number of variables for which the full orientation tournament is constructed
     * for display. Beyond this, the tournament has too many edges to be worth building; the
     * textual summary still describes the orientations.
     */
    private static final int MAX_TOURNAMENT_NODES = 60;

    /**
     * Kept for serialization compatibility with sessions saved by earlier versions, where this
     * held a full FASK search result. It is no longer computed or displayed; the orientation
     * graph shown in the editor is rebuilt from the knowledge by
     * {@link #getKnowledgeSourceGraph()}, which is also correct for deserialized sessions.
     */
    private Graph resultGraph = new EdgeListGraph();

    /**
     * <p>Constructor for PairwiseForbiddenGraphModel.</p>
     *
     * @param wrapper a {@link DataWrapper} object
     * @param params  a {@link Parameters} object
     */
    public PairwiseForbiddenGraphModel(DataWrapper wrapper, Parameters params) {
        super(params);
        createKnowledge((DataSet) wrapper.getSelectedDataModel(), params);
    }

    private void createKnowledge(DataSet dataSet, Parameters params) {
        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("Pairwise Forbidden Graph requires continuous data.");
        }

        DataSet _dataSet = DataTransforms.standardizeData(dataSet);
        double[][] data = _dataSet.getDoubleData().transpose().toArray();
        List<Node> nodes = _dataSet.getVariables();

        Knowledge knowledge = getKnowledge();

        if (knowledge == null) {
            return;
        }

        knowledge.clear();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                double[] _x = data[i];
                double[] _y = data[j];

                double diff = Fask.leftRightDiff(_x, _y, 3);

                if (diff > 0) {
                    knowledge.setForbidden(y.getName(), x.getName());
                } else {
                    knowledge.setForbidden(x.getName(), y.getName());
                }
            }
        }
    }

    /**
     * The graph of favored pairwise directions, rebuilt from the current knowledge: for each
     * pair of variables with exactly one direction forbidden, the graph contains the other
     * direction. For the knowledge this model constructs, this is a tournament; after user
     * edits it may be any subgraph of one.
     *
     * @return the orientation graph, or null when there are too many variables for the graph to
     * be worth constructing
     */
    @Override
    public Graph getKnowledgeSourceGraph() {
        Knowledge knowledge = getKnowledge();

        if (knowledge == null) {
            return null;
        }

        List<String> names = knowledge.getVariables();

        if (names.size() > MAX_TOURNAMENT_NODES) {
            return null;
        }

        Graph graph = new EdgeListGraph();
        List<Node> nodes = new ArrayList<>();

        for (String name : names) {
            Node node = new GraphNode(name);
            nodes.add(node);
            graph.addNode(node);
        }

        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                String a = names.get(i);
                String b = names.get(j);

                boolean abForbidden = knowledge.isForbidden(a, b);
                boolean baForbidden = knowledge.isForbidden(b, a);

                if (baForbidden && !abForbidden) {
                    graph.addDirectedEdge(nodes.get(i), nodes.get(j));
                } else if (abForbidden && !baForbidden) {
                    graph.addDirectedEdge(nodes.get(j), nodes.get(i));
                }
            }
        }

        return graph;
    }

    /**
     * Describes the pairwise orientations: how the knowledge was made, the ordering of the
     * variables implied by counting pairwise wins, and how many pairwise decisions point
     * backward relative to that ordering (zero exactly when the tournament is transitive, in
     * which case the knowledge is equivalent to putting each variable in its own tier, in that
     * order).
     *
     * @return the description
     */
    @Override
    public String getKnowledgeSourceDescription() {
        Knowledge knowledge = getKnowledge();

        if (knowledge == null) {
            return null;
        }

        List<String> names = knowledge.getVariables();
        int n = names.size();

        if (n < 2) {
            return "For each pair of variables, the direction disfavored by the FASK pairwise "
                   + "left-right statistic was forbidden.";
        }

        // A pair's favored direction a --> b is recorded when b --> a is forbidden and
        // a --> b is not. Wins count favored out-edges.
        int[] wins = new int[n];
        int decided = 0;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                boolean ijForbidden = knowledge.isForbidden(names.get(i), names.get(j));
                boolean jiForbidden = knowledge.isForbidden(names.get(j), names.get(i));

                if (jiForbidden && !ijForbidden) {
                    wins[i]++;
                    decided++;
                } else if (ijForbidden && !jiForbidden) {
                    wins[j]++;
                    decided++;
                }
            }
        }

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);
        order.sort(Comparator.comparingInt((Integer i) -> -wins[i])
                .thenComparing(names::get));

        int[] rank = new int[n];
        for (int pos = 0; pos < n; pos++) rank[order.get(pos)] = pos;

        int backward = 0;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                boolean ijForbidden = knowledge.isForbidden(names.get(i), names.get(j));
                boolean jiForbidden = knowledge.isForbidden(names.get(j), names.get(i));

                // Favored i --> j but j comes first in the ordering, or the reverse.
                if (jiForbidden && !ijForbidden && rank[j] < rank[i]) backward++;
                if (ijForbidden && !jiForbidden && rank[i] < rank[j]) backward++;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("For each pair of variables, the direction disfavored by the FASK pairwise ")
                .append("left-right statistic was forbidden; the graph below shows the favored ")
                .append("direction for each pair (").append(decided).append(" of ")
                .append(n * (n - 1) / 2).append(" pairs decided).\n\n");

        sb.append("Ordering implied by pairwise wins, most upstream first: ");

        for (int pos = 0; pos < n; pos++) {
            if (pos > 0) sb.append(", ");
            sb.append(names.get(order.get(pos)));
        }

        sb.append(".\n\n");

        if (backward == 0) {
            sb.append("Every pairwise decision agrees with this ordering, so the knowledge is ")
                    .append("equivalent to placing each variable in its own tier, in this order.");
        } else {
            sb.append(backward).append(backward == 1 ? " pairwise decision points" : " pairwise decisions point")
                    .append(" backward relative to this ordering, so the orientations are not ")
                    .append("equivalent to any single variable ordering.");
        }

        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getKnowledgeSourceTabTitle() {
        return "Pairwise Orientations";
    }

    /**
     * Returns the orientation graph described at {@link #getKnowledgeSourceGraph()}. In earlier
     * versions this returned a FASK search result that was computed as a side effect and never
     * used; that search is no longer run.
     *
     * @return a {@link Graph} object, or null when there are too many variables
     */
    public Graph getResultGraph() {
        return getKnowledgeSourceGraph();
    }

}
