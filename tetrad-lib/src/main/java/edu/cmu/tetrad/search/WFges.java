package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.*;

/**
 * "Whimsical"FGES. Handles mixed, continuous, and discrete data.
 *
 * @author Joseph Ramsey
 */
public class WFges implements GraphSearch {

    private final List<Node> searchVariables;
    private final Map<Node, List<Node>> variablesPerNode = new HashMap<>();
    private final Fges fges;
    private double penaltyDiscount;
    private final SemBicScore score;

    public WFges(final DataSet data) {
        if (data == null) throw new NullPointerException("Data was not provided.");

        this.searchVariables = data.getVariables();
        final DataSet internalData = data.copy();

        final List<Node> variables = data.getVariables();

        for (final Node node : variables) {
            final List<Node> nodes = expandVariable(internalData, node);
            this.variablesPerNode.put(node, nodes);
        }

        System.out.println("Data expanded.");

        final ICovarianceMatrix covariances = new CovarianceMatrix(internalData);

        System.out.println("Cov matrix made.");

        final SemBicScore score = new SemBicScore(covariances);
        this.score = score;
        this.fges = new Fges(score);
    }

    private List<Node> expandVariable(final DataSet dataSet, final Node node) {
        if (node instanceof ContinuousVariable) {
            return Collections.singletonList(node);
        }

        final List<String> varCats = new ArrayList<>(((DiscreteVariable) node).getCategories());

        final List<Node> variables = new ArrayList<>();

        for (int i = 0; i < varCats.size() - 1; i++) {
            final Node newVar = new ContinuousVariable(node.getName() + "." + varCats.get(i));
            variables.add(newVar);
            dataSet.addVariable(newVar);
            final int newVarIndex = dataSet.getColumn(newVar);
            for (int l = 0; l < dataSet.getNumRows(); l++) {
                final int v = dataSet.getInt(l, dataSet.getColumn(node));

                if (v == i) {
                    dataSet.setDouble(l, newVarIndex, 1);
                } else {
                    dataSet.setDouble(l, newVarIndex, 0);
                }
            }
        }

        dataSet.removeColumn(node);

        return variables;
    }

    public Graph search() {
        this.score.setPenaltyDiscount(this.penaltyDiscount);
        final Graph g = this.fges.search();

        final Graph out = new EdgeListGraph(this.searchVariables);

        for (int i = 0; i < this.searchVariables.size(); i++) {
            for (int j = i + 1; j < this.searchVariables.size(); j++) {
                final Node x = this.searchVariables.get(i);
                final Node y = this.searchVariables.get(j);

                final List<Node> xNodes = this.variablesPerNode.get(x);
                final List<Node> yNodes = this.variablesPerNode.get(y);

                int left = 0;
                int right = 0;
                int total = 0;

                for (int k = 0; k < xNodes.size(); k++) {
                    for (int l = 0; l < yNodes.size(); l++) {
                        final Edge e = g.getEdge(xNodes.get(k), yNodes.get(l));

                        if (e != null) {
                            total++;
                            if (e.pointsTowards(xNodes.get(k))) left++;
                            if (e.pointsTowards(yNodes.get(l))) right++;
                        }
                    }
                }

                if (total > 0) {
                    if (left == total) out.addDirectedEdge(y, x);
                    else if (right == total) out.addDirectedEdge(x, y);
                    else out.addUndirectedEdge(x, y);
                }
            }
        }

        return out;
    }

    @Override
    public long getElapsedTime() {
        return 0;
    }

    public void setPenaltyDiscount(final double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }
}
