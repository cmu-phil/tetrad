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
public class WGfci implements GraphSearch {

    private List<Node> searchVariables;
    private Map<Node, List<Node>> variablesPerNode = new HashMap<>();
    private GFci gfci;
    private double alpha;
    private IndependenceTest test;
    private SemBicScore score;

    public WGfci(DataSet data) {
        this.searchVariables = data.getVariables();
        DataSet internalData = data.copy();

        List<Node> variables = data.getVariables();

        for (Node node : variables) {
            List<Node> nodes = expandVariable(internalData, node);
            variablesPerNode.put(node, nodes);
        }

        System.out.println("Data expanded.");

        ICovarianceMatrix covariances = new CovarianceMatrix(internalData);

        System.out.println("Cov matrix made.");

        test = new IndTestFisherZ(covariances, 0.001);
        this.score = new SemBicScore(covariances);
        score.setPenaltyDiscount(4);
        this.gfci = new GFci(test, score);
    }

    private List<Node> expandVariable(DataSet dataSet, Node node) {
        if (node instanceof ContinuousVariable) {
            return Collections.singletonList(node);
        }

        List<String> varCats = new ArrayList<>(((DiscreteVariable) node).getCategories());

        List<Node> variables = new ArrayList<>();

        for (int i = 0; i < varCats.size() - 1; i++) {
            Node newVar = new ContinuousVariable(node.getName() + "." + varCats.get(i));
            variables.add(newVar);
            dataSet.addVariable(newVar);
            int newVarIndex = dataSet.getColumn(newVar);
            for (int l = 0; l < dataSet.getNumRows(); l++) {
                int v = dataSet.getInt(l, dataSet.getColumn(node));

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
        test.setAlpha(alpha);
        Graph g = gfci.search();

        Graph out = new EdgeListGraph(searchVariables);

        for (int i = 0; i < searchVariables.size(); i++) {
            for (int j = i + 1; j < searchVariables.size(); j++) {
                Node x = searchVariables.get(i);
                Node y = searchVariables.get(j);

                List<Node> xNodes = variablesPerNode.get(x);
                List<Node> yNodes = variablesPerNode.get(y);

                int left = 0;
                int right = 0;
                int total = 0;

                for (int k = 0; k < xNodes.size(); k++) {
                    for (int l = 0; l < yNodes.size(); l++) {
                        Edge e = g.getEdge(xNodes.get(k), yNodes.get(l));

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

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }
}
