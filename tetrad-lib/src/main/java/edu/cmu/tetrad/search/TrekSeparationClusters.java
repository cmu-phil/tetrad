/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ntad_test.Cca;
import edu.cmu.tetrad.search.utils.ClusterSignificance;
import edu.cmu.tetrad.search.utils.ClusterUtils;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.util.FastMath;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.decomposition.chol.CholeskyDecompositionLDL_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition;
import org.ejml.simple.SimpleMatrix;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Math.max;
import static org.apache.commons.math3.util.FastMath.abs;
import static org.apache.commons.math3.util.FastMath.sqrt;


/**
 * Implements the Find One Factor Clusters (FOFC) algorithm by Erich Kummerfeld, which uses reasoning about vanishing
 * tetrads of algorithms to infer clusters of the measured variables in a dataset that each be explained by a single
 * latent variable. A reference is the following
 * <p>
 * Kummerfeld, E., &amp; Ramsey, J. (2016, August). Causal clustering for 1-factor measurement models. In Proceedings of
 * the 22nd ACM SIGKDD international conference on knowledge discovery and data mining (pp. 1655-1664).
 * <p>
 * The algorithm uses tests of vanishing tetrads (list of 4 variables that follow a certain pattern in the
 * exchangeability of latent paths with respect to the data). The notion of vanishing tetrads is an old one but is
 * explained in this book:
 * <p>
 * Spirtes, P., Glymour, C. N., Scheines, R., &amp; Heckerman, D. (2000). Causation, prediction, and search. MIT press.
 *
 * @author erichkummerfeld
 * @author peterspirtes
 * @author josephramsey
 * @version $Id: $Id
 * @see Ftfc
 */
public class TrekSeparationClusters {
    /**
     * The covariance matrix.
     */
    private final CorrelationMatrix corr;
    /**
     * The correlation matrix as a SimpleMatrix.
     */
    private final SimpleMatrix S;
    /**
     * The list of all variables.
     */
    private final List<Node> variables;
    /**
     * The significance level.
     */
    private final double alpha;
    /**
     * The data.
     */
    private final SimpleMatrix dataSet;
    /**
     * A standard normal distribution object used for statistical calculations within the Fofc class. The distribution
     * is characterized by a mean of 0 and a standard deviation of 1.
     */
    private final NormalDistribution normal = new NormalDistribution(0, 1);
    /**
     * The clusters that are output by the algorithm from the last call to search().
     */
    private List<List<Node>> clusters;
    /**
     * Whether verbose output is desired.
     */
    private boolean verbose;
    /**
     * Indicates whether all nodes should be included in the graph construction or processing. When set to true, the
     * algorithm will incorporate all nodes into the resulting graph, regardless of specific clustering or filtering
     * criteria. If false, only nodes that meet specific clustering or filtering conditions will be included.
     */
    private boolean includeAllNodes = false;

    /**
     * Conctructor.
     *
     * @param dataSet The continuous dataset searched over.
     * @param alpha   The alpha significance cutoff.
     */
    public TrekSeparationClusters(DataSet dataSet, double alpha) {
        this.variables = dataSet.getVariables();
        this.alpha = alpha;
        this.dataSet = dataSet.getDoubleData().getDataCopy();
        this.corr = new CorrelationMatrix(dataSet);
        this.S = this.corr.getMatrix().getDataCopy();
    }

    /**
     * Runs the search and returns a graph of clusters with the ir respective latent parents.
     *
     * @return This graph.
     */
    public Graph search() {
        Set<List<Integer>> allClusters;

        allClusters = estimateClusters();
        this.clusters = ClusterSignificance.variablesForIndices(allClusters, variables);

        log("clusters = " + this.clusters);

        return convertToGraph(allClusters, includeAllNodes);
    }

    /**
     * The clusters that are output by the algorithm from the last call to search().
     *
     * @return a {@link List} object
     */
    public List<List<Node>> getClusters() {
        return this.clusters;
    }

    /**
     * <p>Setter for the field <code>verbose</code>.</p>
     *
     * @param verbose a boolean
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Retrieves a list of all variables.
     *
     * @return A list of integers representing all variables.
     */
    private List<Integer> allVariables() {
        List<Integer> _variables = new ArrayList<>();
        for (int i = 0; i < this.variables.size(); i++) _variables.add(i);
        return _variables;
    }

    /**
     * Estimates clusters using the tetrads-first algorithm.
     *
     * @return A set of lists of integers representing the clusters.
     */
    private Set<List<Integer>> estimateClusters() {
        List<Integer> variables = allVariables();
        if (new HashSet<>(variables).size() != variables.size()) {
            throw new IllegalArgumentException("Variables must be unique.");
        }

        Set<List<Integer>> clusters = new HashSet<>();

        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                int[] yIndices = new int[]{variables.get(i), variables.get(j)};
                int[] xIndices = new int[variables.size() - 2];

                int index = 0;

                for (int k = 0; k < variables.size(); k++) {
                    if (k != i && k != j) {
                        xIndices[index++] = variables.get(k);
                    }
                }

                double p = StatUtils.getCcaPValueRankD(S, xIndices, yIndices, dataSet.getNumRows(), 1, true);

                if (p >= alpha) {
                    List<Integer> _cluster = new ArrayList<>();
                    _cluster.add(variables.get(i));
                    _cluster.add(variables.get(j));

                    if (clusterDependent(_cluster)) {
                        clusters.add(_cluster);
                    }
                }
            }
        }

        clusters = mergeOverlappingClusters(clusters);

        System.out.println("final clusters = " + ClusterSignificance.variablesForIndices(clusters, this.variables));

        return clusters;
    }

    /**
     * Merges the given clusters.
     *
     * @param clusters The lists of integers representing the clusters.
     * @return The merged clusters.
     */
    private Set<List<Integer>> mergeOverlappingClusters(Set<List<Integer>> clusters) {
        boolean merged;
        do {
            merged = false;
            Set<List<Integer>> newClusters = new HashSet<>();
            Set<List<Integer>> used = new HashSet<>();

            for (List<Integer> cluster1 : clusters) {
                if (used.contains(cluster1)) continue;

                List<Integer> mergedCluster = new ArrayList<>(cluster1);

                for (List<Integer> cluster2 : clusters) {
                    if (cluster1 == cluster2) continue;// || used.contains(cluster2)) continue;

                    Set<Integer> intersection = new HashSet<>(cluster1);
                    intersection.retainAll(cluster2);

                    if (!intersection.isEmpty()) {
                        mergedCluster.addAll(cluster2);
                        used.add(cluster2);
                        merged = true;
                    }
                }

                used.add(cluster1);
                newClusters.add(mergedCluster);
            }

            clusters = newClusters;
        } while (merged);

        return clusters;
    }

    private boolean clusterDependent(List<Integer> cluster) {
        int numDependencies = 0;
        int all = 0;

        for (int i = 0; i < cluster.size(); i++) {
            for (int j = i + 1; j < cluster.size(); j++) {
                double r = this.corr.getValue(cluster.get(i), cluster.get(j));

                if (Double.isNaN(r)) {
                    continue;
                }

                int n = this.corr.getSampleSize();
                int zSize = 0; // Unconditional check.

                double q = .5 * (FastMath.log(1.0 + abs(r)) - FastMath.log(1.0 - abs(r)));
                double df = n - 3. - zSize;

                double fisherZ = sqrt(df) * q;

                if (2 * (1.0 - this.normal.cumulativeProbability(abs(fisherZ))) < alpha) {
                    numDependencies++;
                }

                all++;
            }
        }

        return numDependencies == all;
    }

    /**
     * Converts search graph nodes to a Graph object.
     *
     * @param clusters The set of sets of Node objects representing the clusters.
     * @return A Graph object representing the search graph nodes.
     */
    private Graph convertSearchGraphNodes(Set<Set<Node>> clusters, boolean includeAllNodes) {
        Graph graph = includeAllNodes ? new EdgeListGraph(this.variables) : new EdgeListGraph();

        List<Node> latents = new ArrayList<>();
        List<Set<Node>> _clusters = new ArrayList<>(clusters);

        for (int i = 0; i < _clusters.size(); i++) {
            Node latent = new GraphNode(ClusterUtils.LATENT_PREFIX + (i + 1));
            latent.setNodeType(NodeType.LATENT);
            latents.add(latent);
            graph.addNode(latent);

            for (Node node : _clusters.get(i)) {
                if (!graph.containsNode(node)) graph.addNode(node);
                graph.addDirectedEdge(latents.get(i), node);
            }
        }

        return graph;
    }

    /**
     * Converts search graph nodes to a Graph object.
     *
     * @param allClusters The set of sets of Node objects representing the clusters.
     * @return A Graph object representing the search graph nodes.
     */
    private Graph convertToGraph(Set<List<Integer>> allClusters, boolean includeAllNodes) {
        Set<Set<Node>> _clustering = new HashSet<>();

        for (List<Integer> cluster : allClusters) {
            Set<Node> nodes = new HashSet<>();

            for (int i : cluster) {
                nodes.add(this.variables.get(i));
            }

            _clustering.add(nodes);
        }

        return convertSearchGraphNodes(_clustering, includeAllNodes);
    }

    /**
     * Logs a message if the verbose flag is set to true.
     *
     * @param s The message to log.
     */
    private void log(String s) {
        if (this.verbose) {
            TetradLogger.getInstance().log(s);
        }
    }

    /**
     * Indicates whether all nodes should be included in the graph construction or processing. When set to true, the
     * algorithm will incorporate all nodes into the resulting graph, regardless of specific clustering or filtering
     * criteria. If false, only nodes that meet specific clustering or filtering conditions will be included.
     *
     * @param includeAllNodes True if all nodes should be included in the graph output.
     */
    public void setIncludeAllNodes(boolean includeAllNodes) {
        this.includeAllNodes = includeAllNodes;
    }

    private enum Purity {PURE, IMPURE, UNDECIDED}
}




