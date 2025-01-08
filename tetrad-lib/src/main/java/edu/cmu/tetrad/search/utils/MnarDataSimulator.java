package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The MNARDataSimulator class provides functionality to simulate Missing Not at Random (MNAR) data.
 */
public class MnarDataSimulator {

    /**
     * Generates a dataset with Missing Not At a Random (MNAR) data mechanism applied to specific variables in a graph.
     * The method modifies the input graph to include missingness indicators and simulates data based on the modified
     * graph. Certain data entries are set to missing based on their corresponding indicators.
     *
     * @param graph              The input graph defining the relationships between variables.
     * @param numExtraInfluences The number of additional edges influencing missingness.
     * @param threshold          The threshold value to determine missingness, used to produce binary indicators.
     * @param numRows            The number of rows to simulate in the generated dataset.
     * @return A DataSet object with simulated data, including MNAR modification.
     */
    public static @NotNull DataSet getMnarData(Graph graph, int numExtraInfluences, double threshold, int numRows) {

        // Throw an exceptionm if any variables in the graph has a name ending with "_missing"
        for (Node node : graph.getNodes()) {
            if (node.getName().endsWith("_missing")) {
                throw new IllegalArgumentException("Variable names cannot end with '_missing'");
            }
        }

        if (numExtraInfluences < 0) {
            throw new IllegalArgumentException("Number of extra influences must be non-negative.");
        }

        if (numRows <= 0) {
            throw new IllegalArgumentException("Number of rows must be positive.");
        }

        // Add missingness variables for selected variables
        Graph expandedGraph = new EdgeListGraph(graph);

        List<String> names = expandedGraph.getNodeNames();
        String[] targetColumns = {names.get(0), names.get(2)};
        List<Node> missingnessNodes = new ArrayList<>();

        for (String targetColumn : targetColumns) {
            Node variable = expandedGraph.getNode(targetColumn);
            Node missingnessNode = new ContinuousVariable(variable.getName() + "_missing");
            missingnessNodes.add(missingnessNode);
            expandedGraph.addNode(missingnessNode);
            expandedGraph.addDirectedEdge(variable, missingnessNode);
        }

        // Add additional edges to influence missingness as desired
        for (int i = 0; i < numExtraInfluences; i++) {
            Node node = missingnessNodes.get(RandomUtil.getInstance().nextInt(missingnessNodes.size()));
            List<Node> parents = expandedGraph.getParents(node);

            expandedGraph.getNodes().stream()
                    .filter(n -> !parents.contains(n) && !n.equals(node))
                    .findAny()
                    .ifPresent(parent -> expandedGraph.addDirectedEdge(parent, node));
        }

        // Simulate data over all variables
        SemPm pm = new SemPm(expandedGraph);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(numRows, false);

        // Threshold missingness variables to produce binary 0's and 1's
        for (Node node : dataSet.getVariables()) {
            if (node.getName().endsWith("_missing")) {
                int colIndex = dataSet.getColumnIndex(node);
                IntStream.range(0, dataSet.getNumRows()).parallel().forEach(row -> {
                    double value = dataSet.getDouble(row, colIndex);
                    dataSet.setDouble(row, colIndex, value > threshold ? 0.0 : 1.0);
                });
            }
        }

        // Apply missingness to data columns
        for (Node indicator : dataSet.getVariables()) {
            if (indicator.getName().endsWith("_missing")) {
                Node associatedColumn = dataSet.getVariable(indicator.getName().replace("_missing", ""));
                if (associatedColumn != null) {
                    int indicatorIndex = dataSet.getColumnIndex(indicator);
                    int columnIndex = dataSet.getColumnIndex(associatedColumn);

                    IntStream.range(0, dataSet.getNumRows()).parallel().forEach(row -> {
                        if (dataSet.getDouble(row, indicatorIndex) == 0.0) {
                            dataSet.setDouble(row, columnIndex, Double.NaN);
                        }
                    });
                }
            }
        }

        // Remove the missingness columns
        List<Node> toRemove = new ArrayList<>();
        for (Node node : dataSet.getVariables()) {
            if (node.getName().endsWith("_missing")) {
                toRemove.add(node);
            }
        }
        toRemove.forEach(dataSet::removeColumn);

        return dataSet;
    }

    public static void main(String[] args) {
        RandomUtil.getInstance().setSeed(38482834L);

        int numMeasures = 10;
        int numEdges = 10;

        Graph graph = RandomGraph.randomGraph(numMeasures, 0, numEdges, 100, 100, 100, false);

        int numExtraInfluences = 8;
        double threshold = 1.0;
        int numRows = 100;

        DataSet dataSet = getMnarData(graph, numExtraInfluences, threshold, numRows);

        // Print the result
        System.out.println("MNAR Dataset:");
        System.out.println(dataSet);

        System.out.println("Graph:");
        System.out.println(graph);
    }
}
