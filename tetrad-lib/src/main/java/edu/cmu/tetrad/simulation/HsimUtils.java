package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.VerticalIntDataBox;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.TextTable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Erich on 4/22/2016.
 */
public class HsimUtils {
    //this method will trim an input graph down to the nodes and edges that are used for evaluation
    public static Graph evalEdges(Graph inputgraph, Set<Node> simnodes, Set<Node> realnodes){
        //first, restrict down to subgraph containing only simnods and realnodes
        Set<Node> aNodes = new HashSet<Node>();
        aNodes.addAll(simnodes);
        aNodes.addAll(realnodes);
        //need a List as input for subgraph method, but mbAll is a Set
        List<Node> relevantNodes = new ArrayList<Node>(aNodes);
        Graph subgraph = inputgraph.subgraph(relevantNodes);

        //then remove edges connecting realnodes to other realnodes:

        //loop through all edges
        Set<Edge> edges = subgraph.getEdges();

        for(Edge edge : edges) {
            //if the edge connects realnodes to realnodes, remove it
            if (realnodes.contains(edge.getNode1())) {
                if (realnodes.contains(edge.getNode2())) {
                    subgraph.removeEdge(edge);
                }
            }
        }
        return subgraph;
    }

    public static Set<Node> getAllParents(Graph inputgraph, Set<Node> inputnodes) {
        List<Node> parents = new ArrayList<Node>();
        List<Node> pAdd = new ArrayList<Node>();

        //loop through inputnodes
        for(Node node : inputnodes) {
            //get its parents from graph
            pAdd = inputgraph.getParents(node);
            //merge parents into output
            parents.addAll(pAdd);
        }

        //remove any of the input nodes from the output set
        parents.removeAll(inputnodes);
        //turn list into set
        Set<Node> output = new HashSet<Node>(parents);
        return output;
    }

    public static double[] errorEval(Graph estPattern, Graph truePattern) {
        GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(estPattern, truePattern);

        int adjTp = comparison.getAdjCor();
        int adjFp = comparison.getAdjFp();
        int adjFn = comparison.getAdjFn();

        int arrowptTp = comparison.getAhdCor();
        int arrowptFp = comparison.getAhdFp();
        int arrowptFn = comparison.getAhdFn();

        estPattern = GraphUtils.replaceNodes(estPattern, truePattern.getNodes());

        int[][] counts = GraphUtils.edgeMisclassificationCounts(truePattern, estPattern, false);

        double edgeRatio = HsimUtils.correctnessRatio(counts);

        double adjRecall = adjTp / (double) (adjTp + adjFn);
        double adjPrecision = adjTp / (double) (adjTp + adjFp);
        double arrowRecall = arrowptTp / (double) (arrowptTp + arrowptFn);
        double arrowPrecision = arrowptTp / (double) (arrowptTp + arrowptFp);

        double[] output;
        output = new double[5];
        output[0]=edgeRatio;
        output[1]=adjRecall;
        output[2]=adjPrecision;
        output[3]=arrowRecall;
        output[4]=arrowPrecision;

        return output;
    }

    public static double correctnessRatio(int[][] counts) {
        //StringBuilder builder = new StringBuilder();

        TextTable table2 = new TextTable(9, 7);

        table2.setToken(1, 0, "---");
        table2.setToken(2, 0, "o-o");
        table2.setToken(3, 0, "o->");
        table2.setToken(4, 0, "<-o");
        table2.setToken(5, 0, "-->");
        table2.setToken(6, 0, "<--");
        table2.setToken(7, 0, "<->");
        table2.setToken(8, 0, "No Edge");
        table2.setToken(0, 1, "---");
        table2.setToken(0, 2, "o-o");
        table2.setToken(0, 3, "o->");
        table2.setToken(0, 4, "-->");
        table2.setToken(0, 5, "<->");
        table2.setToken(0, 6, "No Edge");

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 6; j++) {
                if (i == 7 && j == 5) table2.setToken(i + 1, j + 1, "*");
                else
                    table2.setToken(i + 1, j + 1, "" + counts[i][j]);
            }
        }

        //builder.append(table2.toString());

        int correctEdges = 0;
        int estimatedEdges = 0;

        for (int i = 0; i < counts.length; i++) {
            for (int j = 0; j < counts[0].length - 1; j++) {
                if ((i == 0 && j == 0) || (i == 1 && j == 1) || (i == 2 && j == 2) || (i == 4 && j == 3) || (i == 6 && j == 4)) {
                    correctEdges += counts[i][j];
                }

                estimatedEdges += counts[i][j];
            }
        }

        //NumberFormat nf = new DecimalFormat("0.00");

        //builder.append("\nRatio correct edges to estimated edges = ").append(nf.format((correctEdges / (double) estimatedEdges)));

        return (correctEdges / (double) estimatedEdges);
    }

    public static VerticalIntDataBox makeVertIntBox(DataSet dataset) {
        //this is for turning regular data set into verticalintbox (not doublebox...)
        int[][] data = new int[dataset.getNumColumns()][dataset.getNumRows()];
        for (int i = 0; i < dataset.getNumRows(); i++) {
            for (int j = 0; j < dataset.getNumColumns(); j++) {
                data[j][i] = dataset.getInt(i, j);
            }
        }
        return new VerticalIntDataBox(data);
    }

}
