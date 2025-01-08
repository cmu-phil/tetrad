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

/**
 * The MNARDataSimulator class provides functionality to simulate Missing Not at Random (MNAR) data.
 *
 * @author josephramsey
 */
public class MnarDataSimulator {

    /**
     * Generates a model with a constructed graph and simulated data, applying MNAR (Missing Not At Random) mechanisms
     * to introduce missingness to the dataset based on a specified graph structure.
     *
     * @param numMeasures        the number of measure variables to include in the graph
     * @param numEdges           the number of edges to be created in the graph
     * @param numExtraInfluences the number of additional influences to be added to the missingness indicators
     * @return a {@code MnarDataSimulator.Model} containing the constructed graph and the dataset with simulated
     * missingness values applied
     */
    public static @NotNull MnarDataSimulator.Model getMnarModel(int numMeasures, int numEdges, int numExtraInfluences) {
        Graph g = RandomGraph.randomGraph(numMeasures, 0, numEdges, 100, 100, 100, false);
        Graph saveG = new EdgeListGraph(g);

        // Add missingness variables for selected variables.
        List<String> names = g.getNodeNames();
        String[] targetColumns = {names.get(0), names.get(2)};
        List<Node> missingnessNodes = new ArrayList<>();

        for (String targetColumn : targetColumns) {
            Node variable = g.getNode(targetColumn);
            Node missingnesNode = new ContinuousVariable(variable.getName() + "_missing");
            missingnessNodes.add(missingnesNode);
            g.addNode(missingnesNode);
            g.addDirectedEdge(variable, missingnesNode);
        }

        // Add additional edges to influence missingness as desired
        for (int i = 0; i < numExtraInfluences; i++) {
            // Pick a random node with name ending with "_missing".
            Node node = missingnessNodes.get(RandomUtil.getInstance().nextInt(missingnessNodes.size()));
            List<Node> parents = g.getParents(node);

            // Pick a node that's not a parent of the missingness variable and make it a parent.
            g.getNodes().stream().filter(n -> !parents.contains(n) && !n.equals(node)).findAny()
                    .ifPresent(parent -> g.addDirectedEdge(parent, node));
        }

        // Simulate data over all the variables
        SemPm pm = new SemPm(g);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(10, false);

        // Threshold the missingness variables to produce 0's and 1's.
        double threshold = 0.4;

        for (Node node : dataSet.getVariables()) {
            if (node.getName().endsWith("_missing")) {
                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    double value = dataSet.getDouble(i, dataSet.getColumnIndex(node));
                    dataSet.setDouble(i, dataSet.getColumnIndex(node), value > threshold ? 0.0 : 1.0);
                }
            }
        }

        // Apply missingness to data columns
        for (Node indicator : dataSet.getVariables()) {
            if (indicator.getName().endsWith("_missing")) {
                Node associatedColumn = dataSet.getVariable(indicator.getName().replace("_missing", ""));
                if (associatedColumn != null) {
                    int indicatorIndex = dataSet.getColumnIndex(indicator);
                    int columnIndex = dataSet.getColumnIndex(associatedColumn);

                    for (int row = 0; row < dataSet.getNumRows(); row++) {
                        if (dataSet.getDouble(row, indicatorIndex) == 0.0) {
                            dataSet.setDouble(row, columnIndex, Double.NaN);
                        }
                    }
                }
            }
        }

        // Remove the missingness columns.
        for (Node node : dataSet.getVariables()) {
            if (node.getName().endsWith("_missing")) {
                dataSet.removeColumn(node);
            }
        }

        return new Model(saveG, dataSet);
    }

    public static void main(String[] args) {
        RandomUtil.getInstance().setSeed(38482834L);

        int numMeasures = 5;
        int numEdges = 2;
        int numExtraInfluences = 2;

        var result = getMnarModel(numMeasures, numEdges, numExtraInfluences);

        // Print the result
        System.out.println("MNAR Dataset:");
        System.out.println(result.dataSet());

        System.out.println("Graph:");
        System.out.println(result.graph());
    }

    public record Model(Graph graph, DataSet dataSet) {
    }
}
