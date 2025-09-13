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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The MNARDataSimulator class provides functionality to simulate Missing Not at Random (MNAR) data.
 */
public class LgMnarDataSimulator {

    private LgMnarDataSimulator() {
        throw new IllegalArgumentException("Utility class");
    }

    /**
     * Generates a dataset with Missing Not At a Random (MNAR) data mechanism applied to specific variables in a graph.
     * The method modifies the input graph to include missingness indicators and simulates data based on the modified
     * graph. Certain data entries are set to missing based on their corresponding indicators.
     *
     * @param graph                   The input graph defining the relationships between variables.
     * @param numVariablesWithMissing The number of variables to have missing values.
     * @param numExtraInfluences      The number of additional edges influencing missingness.
     * @param threshold               The threshold value to determine missingness, used to produce binary indicators.
     * @param numRows                 The number of rows to simulate in the generated dataset.
     * @return A DataSet object with simulated data, including MNAR modification.
     */
    public static @NotNull DataSet getMnarData(Graph graph, int numVariablesWithMissing,
                                               int numExtraInfluences, double threshold, int numRows) {

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

        if (numVariablesWithMissing < 0) {
            throw new IllegalArgumentException("Number of variables with missing values must be non-negative.");
        }

        if (numVariablesWithMissing > graph.getNumNodes()) {
            throw new IllegalArgumentException("Number of variables with missing values must be less than the number of variables in the graph.");
        }

        if (threshold < 0 || threshold > 1) {
            throw new IllegalArgumentException("Threshold must be between 0 and 1.");
        }

        // Add missingness variables for selected variables
        Graph expandedGraph = new EdgeListGraph(graph);

        // Pick numVariablesWithMissing variables at random to have missing values
        List<String> names = expandedGraph.getNodeNames();
        Collections.shuffle(names);

        String[] targetColumns = new String[numVariablesWithMissing];
        for (int i = 0; i < numVariablesWithMissing; i++) {
            targetColumns[i] = names.get(i);
        }

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
                int colIndex = dataSet.getColumn(node);

                // Retrieve the data for the node column as a double[] array.
                double[] data = new double[dataSet.getNumRows()];

                for (int row = 0; row < dataSet.getNumRows(); row++) {
                    data[row] = dataSet.getDouble(row, colIndex);
                }

                // Sort the data to find the threshold value.
                Arrays.sort(data);

                // Find the threshold value at the 90th percentile.
                double _threshold = data[data.length - (int) Math.ceil(threshold * data.length)];

                IntStream.range(0, dataSet.getNumRows()).parallel().forEach(row -> {
                    double value = dataSet.getDouble(row, colIndex);
                    dataSet.setDouble(row, colIndex, value > _threshold ? 0.0 : 1.0);
                });
            }
        }

        // Apply missingness to data columns
        for (Node indicator : dataSet.getVariables()) {
            if (indicator.getName().endsWith("_missing")) {
                Node associatedColumn = dataSet.getVariable(indicator.getName().replace("_missing", ""));
                if (associatedColumn != null) {
                    int indicatorIndex = dataSet.getColumn(indicator);
                    int columnIndex = dataSet.getColumn(associatedColumn);

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

    /**
     * The entry point for the application. This method generates a random graph,
     * applies a Missing Not At Random (MNAR) mechanism to introduce missing data
     * into a dataset, and displays the dataset along with the associated graph.
     *
     * @param args Command-line arguments (not used for this program).
     */
    public static void main(String[] args) {
        RandomUtil.getInstance().setSeed(38482834L);

        int numMeasures = 10;
        int numEdges = 10;

        Graph graph = RandomGraph.randomGraph(numMeasures, 0, numEdges, 100,
                100, 100, false);

        int numvariableWithMissing = 5;
        int numExtraInfluences = 3;
        double threshold = 1.0;
        int numRows = 100;

        DataSet dataSet = getMnarData(graph, numvariableWithMissing, numExtraInfluences, threshold, numRows);

        // Print the result
        System.out.println("MNAR Dataset:");
        System.out.println(dataSet);

        System.out.println("Graph:");
        System.out.println(graph);
    }
}
