package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.LogisticRegression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.dist.Discrete;

import java.util.*;

/**
 * "Whimsical"FGS. Handles mixed, continuous, and discrete data.
 *
 * @author Joseph Ramsey
 */
public class WFgs implements GraphSearch {

    private List<Node> searchVariables;
    private Map<Node, List<Node>> variablesPerNode = new HashMap<Node, List<Node>>();
    private Fgs fgs;
    private double penaltyDiscount;
    private SemBicScore score;
    private int depth;

    public WFgs(DataSet data) {
        this.searchVariables = data.getVariables();
        DataSet internalData = data.copy();

        List<Node> variables = data.getVariables();

        for (Node node : variables) {
            List<Node> nodes = expandVariable(internalData, node);
            variablesPerNode.put(node, nodes);
        }

//        internalData = DataUtils.center(internalData);

        SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(internalData));
        this.score = score;
        this.fgs = new Fgs(score);
        fgs.setDepth(depth);

    }

    private List<Node> expandVariable(DataSet dataSet, Node node) {
        if (node instanceof ContinuousVariable) {
            return Collections.singletonList(node);
        }

        List<String> varCats = new ArrayList<String>(((DiscreteVariable) node).getCategories());

        List<Node> variables = new ArrayList<>();

        for (int i = 0; i < varCats.size() - 1; i++) {
            Node newVar = new ContinuousVariable(node.getName() + ".MULTINOM" + "." + varCats.get(i));
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
        score.setPenaltyDiscount(penaltyDiscount);
        Graph pattern = fgs.search();

        Graph out = new EdgeListGraph(searchVariables);

        for (int i = 0; i < searchVariables.size(); i++) {
            for (int j = i + 1; j < searchVariables.size(); j++) {
                Node x = searchVariables.get(i);
                Node y = searchVariables.get(j);

                List<Node> xNodes = variablesPerNode.get(x);
                List<Node> yNodes = variablesPerNode.get(y);

                int numEdges = 0;
                int numRight = 0;
                int numLeft = 0;

                for (int k = 0; k < xNodes.size(); k++) {
                    for (int l = 0; l < yNodes.size(); l++) {
                        Edge e = pattern.getEdge(xNodes.get(k), yNodes.get(l));
                        if (e != null) {
                            numEdges++;
                            if (e.pointsTowards(xNodes.get(k))) numLeft++;
                            if (e.pointsTowards(yNodes.get(l))) numRight++;
                        }
                    }
                }

                if (numEdges > 0) {
//                    if (numLeft > 0 && numRight == 0) out.addDirectedEdge(y, x);
//                    else if (numRight > 0 && numLeft == 0) out.addDirectedEdge(x, y);
//                    else {
//                        out.addUndirectedEdge(x, y);
//                    }

                    if (numLeft == numEdges) {out.addDirectedEdge(y, x);}
                    else if (numRight == numEdges) {out.addDirectedEdge(x, y);}
                    else {out.addUndirectedEdge(x, y);}
                }
            }
        }

        return out;
    }

    @Override
    public long getElapsedTime() {
        return 0;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}
