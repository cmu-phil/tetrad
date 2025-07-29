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
import edu.cmu.tetrad.search.utils.ClusterSignificance;
import edu.cmu.tetrad.search.utils.ClusterUtils;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.MathUtils;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.util.FastMath;
import org.ejml.simple.SimpleMatrix;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
     * A standard normal distribution object used for statistical calculations within the Fofc class. The distribution
     * is characterized by a mean of 0 and a standard deviation of 1.
     */
    private final NormalDistribution normal = new NormalDistribution(0, 1);
    /**
     * The sample size.
     */
    private final int sampleSize;
    private final int[][] clusterSpecs;
    private final List<Node> dataNodes;
    /**
     * The clusters that are output by the algorithm from the last call to search().
     */
    private Set<Set<Node>> clusters;
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
     * Conctructor using a dataset.
     *
     * @param dataSet The continuous dataset searched over.
     * @param alpha   The alpha significance cutoff.
     */
    public TrekSeparationClusters(DataSet dataSet, int[][] clusterSpecs, double alpha) {
        this(new CorrelationMatrix(dataSet), alpha, clusterSpecs);
    }

    /**
     * Conctructor using a dataset.
     *
     * @param dataSet The continuous dataset searched over.
     * @param alpha   The alpha significance cutoff.
     * @param ess     The expected sample size.
     */
    public TrekSeparationClusters(DataSet dataSet, double alpha, int[][] clusterSpecs, int ess) {
        this(new CorrelationMatrix(dataSet), alpha, clusterSpecs, ess);
    }

    /**
     * Constructor using a covariance matrix (could be a correlation matrix).
     *
     * @param cov   The covariance matrix.
     * @param alpha The alpha level.
     */
    public TrekSeparationClusters(CovarianceMatrix cov, double alpha, int[][] clusterSpecs) {
        this(new CorrelationMatrix(cov), alpha, clusterSpecs, cov.getSampleSize());
    }

    /**
     * Constructor for the TrekSeparationClusters class using a covariance matrix.
     *
     * @param cov   The covariance matrix that could also be a correlation matrix.
     * @param alpha The alpha level for significance cutoff.
     * @param ess   The expected sample size for the analysis.
     */
    public TrekSeparationClusters(CovarianceMatrix cov, double alpha, int[][] clusterSpecs, int ess) {
        this.variables = cov.getVariables();
        this.alpha = alpha;
        this.sampleSize = ess;
        this.dataNodes = cov.getVariables();

        for (int[] spec : clusterSpecs) {
            if (spec.length != 2) {
                throw new IllegalArgumentException("Cluster specs must have two elements");
            }

            if (spec[0] < 2 || spec[1] > spec[0]) {
                throw new IllegalArgumentException("Cluster spec must be of form a:b where a >= 2 and b <= a.");
            }
        }

        this.clusterSpecs = clusterSpecs;
        this.corr = new CorrelationMatrix(cov);
        this.S = this.corr.getMatrix().getDataCopy();
    }

    /**
     * Runs the search and returns a graph of clusters with the ir respective latent parents.
     *
     * @return This graph.
     */
    public Graph search() {
        Set<Set<Integer>> allClusters;

        allClusters = estimateClusters();
        this.clusters = ClusterSignificance.variablesForIndicesSets(allClusters, variables);

        log("clusters = " + this.clusters);

        return convertToGraph(allClusters, includeAllNodes);
    }

    /**
     * The clusters that are output by the algorithm from the last call to search().
     *
     * @return a {@link List} object
     */
    public Set<Set<Node>> getClusters() {
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
    private Set<Set<Integer>> estimateClusters() {
        List<Integer> variables = allVariables();
        if (new HashSet<>(variables).size() != variables.size()) {
            throw new IllegalArgumentException("Variables must be unique.");
        }

        List<Set<Set<Integer>>> clusterList = new ArrayList<>();
        Set<Set<Integer>> allClusters = new HashSet<>();

        for (int i = 0; i < clusterSpecs.length; i++) {
            log("cluster spec: " + Arrays.toString(clusterSpecs[i]));
            Set<Set<Integer>> _clusters = findClustersOfSize(variables, clusterSpecs[i][0], clusterSpecs[i][1], allClusters);
            log("For " + Arrays.toString(clusterSpecs[i]) + "\nFound clusters: " + toNames(_clusters, dataNodes));
            clusterList.add(mergeOverlappingClusters(_clusters));
            log("For " + Arrays.toString(clusterSpecs[i]) + "\nMerged clusters: " + toNames(mergeOverlappingClusters(_clusters), dataNodes));

            for (int j = 0; j < i; j++) {
                if (clusterSpecs[j][1] == clusterSpecs[i][1]) {
                    clusterList.get(j).addAll(clusterList.get(i));
                    clusterList.set(j, mergeOverlappingClusters(clusterList.get(j)));
                    log("For " + Arrays.toString(clusterSpecs[i]) + "\nMerging rank " + clusterSpecs[j][1] + ": " + toNames(clusterList.get(j), dataNodes));

                    clusterList.get(i).clear();
                }
            }

            allClusters.clear();

            for (Set<Set<Integer>> cluster2 : clusterList) {
                allClusters.addAll(cluster2);
            }
        }

        log("final clusters = " + ClusterSignificance.variablesForIndicesSets(allClusters, this.variables));

        return allClusters;
    }

    private String toNames(Set<Set<Integer>> clusters, List<Node> variables) {
        StringBuilder sb = new StringBuilder();

        int count0 = 0;

        for (Set<Integer> cluster : clusters) {
            sb.append("[");
            int count = 0;

            for (Integer var :  cluster) {
                sb.append(variables.get(var));

                if (count++ < cluster.size() - 1) sb.append(", ");
            }

            sb.append("]");

            if (count0++ < clusters.size() - 1) sb.append("; ");
        }

        return sb.toString();
    }

    private @NotNull Set<Set<Integer>> findClustersOfSize(List<Integer> variables, int depth, int rank,
                                                          Set<Set<Integer>> avoid) {
        ChoiceGenerator gen = new ChoiceGenerator(variables.size(), depth);
        int[] _choice;

        List<int[]> choices = new ArrayList<>();
        while ((_choice = gen.next()) != null) {
            choices.add(Arrays.copyOf(_choice, _choice.length));
        }

        Set<Set<Integer>> finalClusters = ConcurrentHashMap.newKeySet();

        choices.parallelStream().forEach(choice -> {
            int[] yIndices = new int[depth];

            for (int i = 0; i < depth; i++) {
                yIndices[i] = variables.get(choice[i]);
            }

            Set<Integer> ySet = new HashSet<>();
            for (int y : yIndices) {
                ySet.add(y);
            }

            for (Set<Integer> set : avoid) {
                if (set.containsAll(ySet)) {
                    return;
                }
            }

            int[] xIndices = new int[variables.size() - depth];

            int index = 0;

            for (int q = 0; q < variables.size(); q++) {
                boolean found = false;
                for (int y : yIndices) {
                    if (q == y) {
                        found = true;
                        break;
                    }
                }
                if (found) continue;

                xIndices[index++] = variables.get(q);
            }

            int _rank = StatUtils.estimateCcaRank(S, xIndices, yIndices, sampleSize, alpha);

            if (_rank == rank) {
                List<Integer> _cluster = MathUtils.getInts(yIndices);

                if (clusterDependent(_cluster)) {
                    finalClusters.add(new HashSet<>(_cluster));
                }
            }
        });
        return finalClusters;
    }

    /**
     * Merges the given clusters.
     *
     * @param clusters The lists of integers representing the clusters.
     * @return The merged clusters.
     */
    private Set<Set<Integer>> mergeOverlappingClusters(Set<Set<Integer>> clusters) {
        boolean merged;

        do {
            merged = false;
            Set<Set<Integer>> newClusters = new HashSet<>();

            for (Set<Integer> cluster1 : clusters) {
                Set<Integer> mergedCluster = new HashSet<>(cluster1);

                for (Set<Integer> cluster2 : clusters) {
                    if (cluster1 == cluster2) continue;

                    Set<Integer> intersection = new HashSet<>(cluster1);
                    intersection.retainAll(cluster2);

                    if (!intersection.isEmpty() && !new HashSet<>(mergedCluster).containsAll(cluster2)) {
                        mergedCluster.addAll(cluster2);
                        merged = true;
                    }
                }

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
    private Graph convertToGraph(Set<Set<Integer>> allClusters, boolean includeAllNodes) {
        Set<Set<Node>> _clustering = new HashSet<>();

        for (Set<Integer> cluster : allClusters) {
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




