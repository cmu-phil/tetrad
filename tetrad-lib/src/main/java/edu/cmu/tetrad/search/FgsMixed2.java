package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.LogisticRegression;
import edu.cmu.tetrad.regression.RegressionDataset;

import java.util.*;

/**
 * Created by jdramsey on 6/1/16.
 */
public class FgsMixed2 implements GraphSearch {

    private DataSet originalData;
    private List<Node> searchVariables;
    private DataSet internalData;
    private Map<Node, List<Node>> variablesPerNode = new HashMap<Node, List<Node>>();
    private Fgs fgs;
    private double penaltyDiscount;
    private SemBicScore score;

    public FgsMixed2(DataSet data) {
        this.searchVariables = data.getVariables();
        this.originalData = data.copy();
        DataSet internalData = data.copy();

        List<Node> variables = internalData.getVariables();

        for (Node node : variables) {
            List<Node> nodes = expandVariable(internalData, node);
            variablesPerNode.put(node, nodes);
        }

        this.internalData = internalData;
        SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(internalData));
        this.score = score;
        this.fgs = new Fgs(score);
    }

    private List<Node> expandVariable(DataSet dataSet, Node node) {
        if (node instanceof ContinuousVariable) {
            return Collections.singletonList(node);
        }

        List<String> varCats = new ArrayList<String>(((DiscreteVariable) node).getCategories());
        varCats.remove(0);
        List<Node> variables = new ArrayList<Node>();

        for (String cat : varCats) {

            Node newVar;

            do {
                String newVarName = node.getName() + "MULTINOM" + "." + cat;
                newVar = new ContinuousVariable(newVarName);
            } while (dataSet.getVariable(newVar.getName()) != null);

            variables.add(newVar);

            dataSet.addVariable(newVar);
            int newVarIndex = dataSet.getColumn(newVar);
            int numCases = dataSet.getNumRows();

            for (int l = 0; l < numCases; l++) {
                Object dataCell = dataSet.getObject(l, dataSet.getColumn(node));
                int dataCellIndex = ((DiscreteVariable) node).getIndex(dataCell.toString());

                if (dataCellIndex == ((DiscreteVariable) node).getIndex(cat))
                    dataSet.setDouble(l, newVarIndex, 1);
                else
                    dataSet.setDouble(l, newVarIndex, 0);
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

                boolean existsEdge = false;
                int numRight = 0;
                int numLeft = 0;

                for (int k = 0; k < xNodes.size(); k++) {
                    for (int l = 0; l < yNodes.size(); l++) {
                        Edge e = pattern.getEdge(xNodes.get(k), yNodes.get(l));
                        if (e != null) {
                            existsEdge = true;

                            if (e.pointsTowards(xNodes.get(k))) numLeft++;
                            if (e.pointsTowards(yNodes.get(l))) numRight++;
                        }
                    }
                }

                if (numLeft > 0 && numRight == 0) out.addDirectedEdge(y, x);
                else if (numRight > 0 && numLeft == 0) out.addDirectedEdge(x, y);
                else if (existsEdge) out.addUndirectedEdge(x, y);
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
}
