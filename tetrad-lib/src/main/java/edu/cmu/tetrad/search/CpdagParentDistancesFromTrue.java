package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.SublistGenerator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CpdagParentDistancesFromTrue computes the distances between true edge strengths in a true DAG and the range of
 * estimated edge strengths in an output CPDAG. The distance is based on the difference between the true edge strength
 * and the range of estimated regression coefficients. The range is determined by considering all possible parent sets
 * for the child node. The distance is 0 if the true edge strength falls within the range of coefficients. If the true
 * edge strength is less than the minimum coefficient, the distance is the absolute value of the difference between the
 * true strength and the minimum coefficient. If the true edge strength is greater than the maximum coefficient, the
 * distance is the absolute value of the difference between the true strength and the maximum coefficient.
 * <p>
 * This implements a method due to Peter Sprirtes.
 *
 * @author josephramsey
 */
public class CpdagParentDistancesFromTrue {

    /**
     * Calculates the distance matrix for the edges in the given CPDAG (outputCpdag). The nodes in the output CPDAG must
     * all be in the full list of nodes given. The distance for each edge u -> v is computed based on how far the true
     * edge strength (from trueEdgeStrengths) is from the estimated regression coefficients for u -&gt; v. The distances
     * are based on the difference between the true edge strength to the range of estimated regression coefficients. The
     * range is determined by considering all possible parent sets for node v. The distance is 0 if the true edge
     * strength falls within the range of coefficients. If the true edge strength is less than the minimum coefficient,
     * the distance is the absolute value of the difference between the true strength and the minimum coefficient. If
     * the true edge strength is greater than the maximum coefficient, the distance is the absolute value of the
     * difference between the true strength and the maximum * coefficient.
     * <p>
     * The distance matrix is a square matrix where the entry at row v and column u is the distance for the edge u -&gt;
     * v.
     *
     * @param outputCpdag       The estimated CPDAG.
     * @param trueEdgeStrengths The true edge strengths (coefficients) for the true DAG, where trueEdgeStrengths[u][v]
     *                          is the beta coefficient for u -&gt; v.
     * @param dataSet           The dataset used for regression.
     * @param distanceType      The type of distance to calculate (absolute or squared).
     * @return A matrix of distances between true edge strengths and estimated strengths. Here, dist[u][v] is the
     * distance for the edge u -&gt; v.
     */
    public static double[][] getDistances(Graph outputCpdag, double[][] trueEdgeStrengths, DataSet dataSet,
                                          DistanceType distanceType) {
        int n = outputCpdag.getNumNodes(); // Number of nodes in the graph

        // Get the list of nodes in the outputCpdag
        List<Node> nodes = outputCpdag.getNodes();

        // Make sure the nodes in the CPDAG and the data set match name-wise.
        List<String> names1 = outputCpdag.getNodeNames();
        List<String> names2 = dataSet.getVariableNames();

        if (!names1.equals(names2)) {
            throw new IllegalArgumentException("The nodes in the CPDAG and the data set must match name-wise.");
        }

        // Initialize the min and max coefficient matrices
        double[][] minCoef = new double[n][n];
        double[][] maxCoef = new double[n][n];

        // Initialize all values in minCoef to Double.POSITIVE_INFINITY and all values in maxCoef to
        // Double.NEGATIVE_INFINITY
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                minCoef[i][j] = Double.POSITIVE_INFINITY;
                maxCoef[i][j] = Double.NEGATIVE_INFINITY;
            }
        }

        RegressionDataset regressionDataset = new RegressionDataset(dataSet);

        // Iterate over each node v
        for (int v = 0; v < n; v++) {
            calculateMinMaxCoefPerNode(v, outputCpdag, minCoef, maxCoef, nodes, regressionDataset);
        }

//        System.out.println("min = " + new Matrix(minCoef));
//        System.out.println("max = " + new Matrix(maxCoef));

        // Initialize the distance matrix
        double[][] distances = new double[n][n];

        // Calculate the distances.
        for (int u = 0; u < n; u++) {
            for (int v = 0; v < n; v++) {
                distances[u][v] = calculateDistancesForEdge(u, v, trueEdgeStrengths, minCoef, maxCoef, distanceType);
            }
        }

//        System.out.println("distances = " + new Matrix(distances));

        return distances;
    }

    /**
     * Adjusts the min and max estimated coefficients for nodes u adjacent to v in the CPDAG by regressing node v on all
     * possible parents set of v that do not imply new unshielded colliders. The adjusted min and max coefficients are
     * stored in the minCoef and maxCoef matrices.
     */
    private static void calculateMinMaxCoefPerNode(int v, Graph outputCpdag,
                                                   double[][] minCoef, double[][] maxCoef, List<Node> nodes,
                                                   RegressionDataset regressionDataSet) {

        // Form all possible parent sets of v
        List<List<Node>> possibleParentSets = formAllPossibleParentSets(nodes.get(v), outputCpdag);

        List<Node> adj = outputCpdag.getAdjacentNodes(nodes.get(v));

        // Iterate over each parent set
        for (List<Node> parentList : possibleParentSets) {

            // Regress v on the parent set
            RegressionResult regResult = regress(nodes.get(v), parentList, regressionDataSet);
            double[] regCoeffs = regResult.getCoef(); // Get the regression coefficients

            // Update the min and max coefficients for each edge u -> v
            for (int u = 0; u < parentList.size(); u++) {
                int _u = nodes.indexOf(parentList.get(u)); // Get the index of u

                double regCoeff = regCoeffs[u + 1]; // Get the regression coefficient for u -> v; skip the intercept

                // Update the min and max coefficients for u -> v
                if (regCoeff < minCoef[_u][v]) {
                    minCoef[_u][v] = regCoeff;
                }

                if (regCoeff > maxCoef[_u][v]) {
                    maxCoef[_u][v] = regCoeff;
                }
            }

            // Adjust min and max for the value 0 for all nodes u adjacent to v but not in the parent set
            List<Node> compl = new ArrayList<>(adj);
            compl.removeAll(parentList);

            for (Node u : compl) {
                int _u = nodes.indexOf(u); // Get the index of u

                // Update the min and max coefficients for u -> v
                if (0 < minCoef[_u][v]) {
                    minCoef[_u][v] = 0;
                }

                if (0 > maxCoef[_u][v]) {
                    maxCoef[_u][v] = 0;
                }
            }
        }
    }

    private static double calculateDistancesForEdge(int u, int v, double[][] trueEdgeStrengths,
                                                    double[][] minCoef, double[][] maxCoef, DistanceType distanceType) {

        // Get the true edge strength for u -> v
        double trueStrength = trueEdgeStrengths[u][v];

        // Get the min estimated coef
        double min = minCoef[u][v] == Double.POSITIVE_INFINITY ? 0.0 : minCoef[u][v];

        // Get the max estimated coef
        double max = maxCoef[u][v] == Double.NEGATIVE_INFINITY ? 0.0 : maxCoef[u][v];

        // Calculate the distance based on whether true strength falls within the range of coefficients
        if (min < trueStrength && trueStrength < max) {
            return 0.0; // No distance if true strength is within the coefficient range
        } else if (trueStrength <= min) {
            if (distanceType == DistanceType.SQUARED) {
                return Math.pow(trueStrength - min, 2); // Squared distance from the minimum coefficient
            } else {
                return Math.abs(trueStrength - min); // Distance from the minimum coefficient
            }
        } else {
            if (distanceType == DistanceType.SQUARED) {
                return Math.pow(trueStrength - max, 2); // Squared distance from the maximum coefficient
            } else {
                return Math.abs(trueStrength - max); // Distance from the maximum coefficient
            }
        }
    }

    /**
     * Forms all possible parent sets for a given node v, considering both directed parent edges and undirected edges
     * that can potentially serve as parents.
     *
     * @param v     The target node v.
     * @param cpdag The CPDAG.
     * @return A list of all possible parent sets for node v.
     */
    private static List<List<Node>> formAllPossibleParentSets(Node v, Graph cpdag) {
        Set<Edge> edges = cpdag.getEdges(v); // Get all edges adjacent to v

        // Separate edges into parent edges and undirected edges
        List<Edge> parentEdges = new ArrayList<>();
        List<Edge> undirectedEdges = new ArrayList<>();

        for (Edge edge : edges) {
            if (cpdag.isParentOf(edge.getDistalNode(v), v)) {
                parentEdges.add(edge); // Add to parent edges if the other node is a parent
            } else if (Edges.isUndirectedEdge(edge)) {
                undirectedEdges.add(edge); // Add to undirected edges if the edge is undirected
            }
        }

        // Make a list to hold all possible parent sets that do not imply new unshielded colliders.
        List<List<Node>> combinations = new ArrayList<>();

        // Iterate over all combinations of undirected edges
        SublistGenerator sublistGenerator = new SublistGenerator(undirectedEdges.size(), undirectedEdges.size());
        int[] choice;

        CHOICE:
        while ((choice = sublistGenerator.next()) != null) {
            List<Node> parentNodes = new ArrayList<>();

            // Add all parent edges to the combination
            for (Edge edge : parentEdges) {
                parentNodes.add(edge.getDistalNode(v));
            }

            // Get the set of undirected neighbors.
            Set<Node> undirectedNeighbors = new HashSet<>();
            for (int i : choice) {
                undirectedNeighbors.add(undirectedEdges.get(i).getDistalNode(v));
            }

            // Add the undirected neighbors to the parent set one at a time, checking at each step to make sure each
            // newly added node doesn't form a new unshielded collider with any other nodes in the parent set.
            for (Node z : undirectedNeighbors) {
                for (Node x : parentNodes) {
                    if (z != x && !cpdag.isAdjacentTo(x, z)) {
                        continue CHOICE;
                    }
                }

                parentNodes.add(z);
            }

            combinations.add(parentNodes);
        }

//        System.out.println("Parent sets for node " + v + ": " + combinations);

        return combinations;
    }

    /**
     * Performs a regression of node v on a given parent set using the provided dataset.
     *
     * @param v                 The target node v.
     * @param parentSet         The parent set to regress v on.
     * @param regressionDataset The dataset wrapper for regression.
     * @return The result of the regression.
     */
    private static RegressionResult regress(Node v, List<Node> parentSet, RegressionDataset regressionDataset) {
        return regressionDataset.regress(v, parentSet); // Perform regression and return the result
    }

    /**
     * The type of distance to calculate.
     */
    public enum DistanceType {

        /**
         * Calculate the absolute distance between the true edge strength and the range of estimated coefficients.
         */
        ABSOLUTE,

        /**
         * Calculate the squared distance between the true edge strength and the range of estimated coefficients.
         */
        SQUARED
    }
}
