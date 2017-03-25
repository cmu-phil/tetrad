package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.VerticalIntDataBox;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.sem.TemplateExpander;
import edu.cmu.tetrad.util.TextTable;

import java.text.ParseException;
import java.util.*;

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

    //this method returns the set of all parents of a provided set of parents, given a provided graph
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

    //this method returns an array of doubles, which are standard error metrics for graph learning
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

    //this method makes a VerticalIntDataBox from a regular data set
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
    //returns a String formatted as a latex table, which can be pasted directly into a latex document
    public static String makeLatexTable(String[][] tablevalues){
        String nl = System.lineSeparator();
        String output = "\\begin{table}[ht]"+nl;
        output = output + "\\begin{center}" +nl;
        int dim1 = tablevalues.length;
        int dim2 = tablevalues[0].length;
        //determines number of columns in the table
        output=output + "\\begin{tabular}{|";
        for (int c=0;c<dim2;c++){
            output=output+"c|";
        }
        output = output + "}"+nl+"\\hline"+nl;
        //fills in values of the table
        for (int i=0;i<dim1;i++){
            for (int j=0;j<dim2;j++){
                output = output + tablevalues[i][j];
                if (dim2>1 && j!=dim2-1){
                    output = output+" & ";
                }
            }
            output=output+"\\\\ \\hline" + nl;
        }
        //caps off the environments used by the table
        output=output+"\\end{tabular}"+nl+"\\end{center}"+nl+"\\end{table}"+nl;
        return output;
    }
    //this turns an array of doubles into an array of strings, formatted for easier reading
    //the input String should be formatted appropriately for the String.format method
    public static String[] formatErrorsArray(double[] inputArray,String formatting){
        String[] output = new String[inputArray.length];
        for (int i=0;i<inputArray.length;i++){
            output[i]=String.format(formatting,inputArray[i]);
        }
        return output;
    }
    
    //used for making random graphs for SEMS without having to manually constuct the variable set each time
    public static Graph mkRandSEMDAG(int numVars,int numEdges){
        List<Node> varslist = new ArrayList<>();
        for (int i = 0; i < numVars; i++) {
            varslist.add(new ContinuousVariable("X" + i));
        }
        return GraphUtils.randomGraphRandomForwardEdges(varslist, 0, numEdges, 30, 15, 15, false, true);
    }
}
