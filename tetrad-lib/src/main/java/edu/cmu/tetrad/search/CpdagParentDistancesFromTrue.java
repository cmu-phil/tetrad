package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.SublistGenerator;

import java.util.ArrayList;
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
     * Calculates the distance matrix for the edges in the given CPDAG (outputCpdag). The distance for each edge u -> v
     * is computed based on how far the true edge strength (from trueEdgeStrengths) is from the estimated regression
     * coefficients for u -&gt; v. The distances are based on the difference between the true edge strength to the range
     * of estimated regression coefficients. The range is determined by considering all possible parent sets for node v.
     * The distance is 0 if the true edge strength falls within the range of coefficients. If the true edge strength is
     * less than the minimum coefficient, the distance is the absolute value of the difference between the true strength
     * and the minimum coefficient. If the true edge strength is greater than the maximum coefficient, the distance is
     * the absolute value of the difference between the true strength and the maximum * coefficient.
     * <p>
     * The distance matrix is a square matrix where the entry at row v and column u is the distance for the edge u -&gt;
     * v.
     *
     * @param outputCpdag       The estimated CPDAG.
     * @param trueEdgeStrengths The true edge strengths (coefficients) for the true DAG, where trueEdgeStrengths[u][v]
     *                          is the beta coefficient for u -&gt; v.
     * @param dataSet           The dataset used for regression.
     * @param distanceType      The type of distance to calculate (absolute or squared).
     * @return A matrix of distances between true edge strengths and estimated strengths. Here, dist[v][u] is the
     * distance for the edge u -&gt; v.
     */
    public static double[][] getDistances(Graph outputCpdag, double[][] trueEdgeStrengths, DataSet dataSet,
                                          DistanceType distanceType) {
        int n = outputCpdag.getNumNodes(); // Number of nodes in the graph
        double[][] dist = new double[n][n]; // Initialize the distance matrix

        // Get the list of nodes in the outputCpdag
        List<Node> nodes = outputCpdag.getNodes();

        // Parallelize the outer loop over u
        nodes.parallelStream().forEach(nodeU -> {
            int u = nodes.indexOf(nodeU); // Get the index of u
            // Iterate over each vertex v (by index)
            for (int v = 0; v < n; v++) {
                // Calculate the distance for the edge u -> v
                dist[v][u] = calculateDistanceForEdge(u, v, outputCpdag, trueEdgeStrengths, dataSet, nodes, distanceType);
            }
        });

        return dist; // Return the distance matrix
    }

    /**
     * Calculates the distance for a specific edge u -&gt; v. The distance is based on the difference between the true
     * edge strength to the range of estimated regression coefficients. The range is determined by considering all
     * possible parent sets for node v. The distance is 0 if the true edge strength falls within the range of
     * coefficients. If the true edge strength is less than the minimum coefficient, the distance is the absolute value
     * of the difference between the true strength and the minimum coefficient. If the true edge strength is greater
     * than the maximum coefficient, the distance is the absolute value of the difference between the true strength and
     * the maximum coefficient.
     *
     * @param u                 Index of the parent node u.
     * @param v                 Index of the child node v.
     * @param outputCpdag       The estimated CPDAG.
     * @param trueEdgeStrengths The true edge strengths (coefficients). Here, trueEdgeStrengths[u][v] is the beta
     *                          coefficient for u -&gt; v.
     * @param dataSet           The dataset used for regression.
     * @param nodes             List of nodes in the CPDAG.
     * @param distanceType      The type of distance to calculate (absolute or squared).
     * @return The distance between the true edge strength and the range of possible estimated coefficients.
     */
    private static double calculateDistanceForEdge(int u, int v, Graph outputCpdag, double[][] trueEdgeStrengths,
                                                   DataSet dataSet, List<Node> nodes, DistanceType distanceType) {
        List<Double> coefflist = new ArrayList<>(); // List to hold regression coefficients
        RegressionDataset regressionDataset = new RegressionDataset(dataSet); // Regression dataset wrapper

        // Form all possible parent sets of v
        List<List<Node>> possibleParentSets = formAllPossibleParentSets(nodes.get(v), outputCpdag);

        // Iterate over each parent set
        for (List<Node> parentSet : possibleParentSets) {
            // Regress v on the parent set
            RegressionResult regResult = regress(nodes.get(v), parentSet, regressionDataset);
            double[] regCoeffs = regResult.getCoef(); // Get the regression coefficients

            // Add the coefficient for each parent node
            for (Node parent : parentSet) {
                coefflist.add(regCoeffs[parentSet.indexOf(parent)]);
            }

            // For nodes not in the parent set, add 0 as the coefficient
            for (Node other : outputCpdag.getNodes()) {
                if (!parentSet.contains(other)) {
                    coefflist.add(0.0);
                }
            }
        }

        // Get the true edge strength for u -> v
        double trueStrength = trueEdgeStrengths[u][v];
        double minCoeff = coefflist.stream().min(Double::compare).orElse(trueStrength);
        double maxCoeff = coefflist.stream().max(Double::compare).orElse(trueStrength);

        // Calculate the distance based on whether true strength falls within the range of coefficients
        if (minCoeff < trueStrength && trueStrength < maxCoeff) {
            return 0.0; // No distance if true strength is within the coefficient range
        } else if (trueStrength <= minCoeff) {
            if (distanceType == DistanceType.SQUARED) {
                return Math.pow(trueStrength - minCoeff, 2); // Squared distance from the minimum coefficient
            } else {
                return Math.abs(trueStrength - minCoeff); // Distance from the minimum coefficient
            }
        } else {
            if (distanceType == DistanceType.SQUARED) {
                return Math.pow(trueStrength - maxCoeff, 2); // Squared distance from the maximum coefficient
            } else {
                return Math.abs(trueStrength - maxCoeff); // Distance from the maximum coefficient
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

        List<List<Node>> combinations = new ArrayList<>(); // List of all parent set combinations

        // Iterate over all combinations of undirected edges
        SublistGenerator sublistGenerator = new SublistGenerator(undirectedEdges.size(), undirectedEdges.size());
        int[] choice;

        while ((choice = sublistGenerator.next()) != null) {
            List<Node> combination = new ArrayList<>();

            // Add all parent edges to the combination
            for (Edge edge : parentEdges) {
                combination.add(edge.getDistalNode(v));
            }

            // Add selected undirected edges to the combination
            for (int i : choice) {
                combination.add(undirectedEdges.get(i).getDistalNode(v));
            }

            combinations.add(combination); // Add the combination to the list
        }

        return combinations; // Return all possible parent sets
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
