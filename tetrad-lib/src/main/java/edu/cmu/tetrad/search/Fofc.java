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
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ntad_test.*;
import edu.cmu.tetrad.search.utils.ClusterSignificance;
import edu.cmu.tetrad.search.utils.ClusterUtils;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.util.FastMath;

import java.util.*;

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
public class Fofc {
    /**
     * The type of test used.
     */
    private final CorrelationMatrix corr;
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
    private final transient DataModel dataModel;
    /**
     * The type of test used.
     */
    private final int testType;
    /**
     * The Wishart test. This tests a single tetrad.
     */
    private final NtadTest test1;
    /**
     * The Delta test. Testing two tetrads simultaneously.
     */
    private final NtadTest test2;
    /**
     * The Delta test. Testing two tetrads simultaneously.
     */
    private final NtadTest test3;
    /**
     * The Delta test. Testing two tetrads simultaneously.
     */
    private final NtadTest test4;
    private final IndependenceTest independenceTest;
    private final List<String> variableNames;
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
    private Set<Set<Integer>> pureQuartets;
    private Set<Set<Integer>> impureQuartets;

    /**
     * Conctructor.
     *
     * @param dataSet  The continuous dataset searched over.
     * @param testType The type of test used. 1 = CCA, 2 = Bollen-Ting, 3 = Wishart
     * @param alpha    The alpha significance cutoff.
     */
    public Fofc(DataSet dataSet, int testType, IndependenceTest test, double alpha) {
        this.variables = dataSet.getVariables();
        this.alpha = alpha;
        this.testType = testType;
        this.test1 = new Cca(dataSet.getDoubleData().getDataCopy(), false);
        this.test2 = new BollenTing(dataSet.getDoubleData().getDataCopy(), false);
        this.test3 = new Wishart(dataSet.getDoubleData().getDataCopy(), false);
        this.test4 = new Ark(dataSet.getDoubleData().getDataCopy(), 1.0);
//        this.test4 = new Ark(dataSet.getDoubleData().getDataCopy(), 0.25);
        this.dataModel = dataSet;
        this.independenceTest = test;
        this.variableNames = dataSet.getVariableNames();

        this.corr = new CorrelationMatrix(dataSet);
    }

    /**
     * Runs the search and returns a graph of clusters with the ir respective latent parents.
     *
     * @return This graph.
     */
    public Graph search() {
        this.pureQuartets = new HashSet<>();
        this.impureQuartets = new HashSet<>();

        Set<List<Integer>> allClusters;

        allClusters = estimateClustersSag();
        this.clusters = ClusterSignificance.variablesForIndices(allClusters, variables);

        log("clusters = " + this.clusters);

        if (verbose) {
            ClusterSignificance clusterSignificance = new ClusterSignificance(variables, dataModel);
            clusterSignificance.printClusterPValues(allClusters);
        }

        return convertToGraph(allClusters, includeAllNodes);
    }

    /**
     * The clusters that are output by the algorithm from the last call to search().
     *
     * @return a {@link java.util.List} object
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
    private Set<List<Integer>> estimateClustersSag() {
        List<Integer> variables = allVariables();
        if (new HashSet<>(variables).size() != variables.size()) {
            throw new IllegalArgumentException("Variables must be unique.");
        }

        Set<List<Integer>> pureClusters = findPureClusters();
        Set<Integer> unionClustered = union(pureClusters);
        Set<List<Integer>> mixedClusters = findMixedClusters(unionClustered);
        Set<List<Integer>> allClusters = new HashSet<>(pureClusters);
        allClusters.addAll(mixedClusters);

        int count = 0;
        while (count++ < 20 && exchange(allClusters)) ;

//        System.out.println("All clusters: " + ClusterSignificance.variablesForIndices(allClusters, this.variables));

        Set<List<Integer>> finalClusters = new HashSet<>();

        for (List<Integer> cluster : new HashSet<>(allClusters)) {
            if (cluster.size() >= 4) {
                finalClusters.add(cluster);
            }
        }

        Set<Integer> unionClustered2 = union(finalClusters);
        Set<List<Integer>> mixedClusters2 = findMixedClusters(unionClustered2);

        finalClusters.addAll(mixedClusters2);

        System.out.println("final clusters = " + ClusterSignificance.variablesForIndices(finalClusters, this.variables));

        return finalClusters;
    }

    // Finds clusters of size 4 or higher for the tetrad-first algorithm.
    private Set<List<Integer>> findPureClusters() {
        List<Integer> variables = allVariables();
        Set<List<Integer>> clusters = new HashSet<>();

        VARIABLES:
//        while (!variables.isEmpty()) {
        log(variables.toString());

        List<Integer> unclustered = new ArrayList<>(variables);
        unclustered.removeAll(union(clusters));

        if (variables.size() < 4) return new HashSet<>();

        ChoiceGenerator gen = new ChoiceGenerator(variables.size(), 4);
        int[] choice;

        while ((choice = gen.next()) != null) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            int n1 = variables.get(choice[0]);
            int n2 = variables.get(choice[1]);
            int n3 = variables.get(choice[2]);
            int n4 = variables.get(choice[3]);

            if (!(unclustered.contains(n1) && unclustered.contains(n2) && unclustered.contains(n3) && unclustered.contains(n4))) {
                continue;
            }

            List<Integer> cluster = quartet(n1, n2, n3, n4);

            // Note that purity needs to be assessed with respect to all the variables to
            // remove all latent-measure impurities between pairs of latents.
            if (pure(cluster) == Purity.PURE) {


                growCluster(unclustered, cluster);

                if (this.verbose) {
                    log("Cluster found: " + ClusterSignificance.variablesForIndices(cluster, this.variables));
                }

                clusters.add(cluster);
//                variables.removeAll(cluster);

                unclustered.removeAll(cluster);

//                    continue VARIABLES;
            }
        }
//
//            break;
//        }

        return clusters;
    }

    private void growCluster(List<Integer> unclustered, List<Integer> cluster) {
        Iterator<Integer> iterator = unclustered.iterator();

        while (iterator.hasNext()) {
            int o = iterator.next();

            if (cluster.contains(o)) continue;

            boolean allQuartetsPure = true;

            // Check all quartets with o and 3 other elements in the cluster
            int size = cluster.size();

            for (int i = 0; i < size - 2 && allQuartetsPure; i++) {
                for (int j = i + 1; j < size - 1 && allQuartetsPure; j++) {
                    for (int k = j + 1; k < size && allQuartetsPure; k++) {
                        List<Integer> quartet = List.of(cluster.get(i), cluster.get(j), cluster.get(k), o);

                        if (pure(quartet) != Purity.PURE) {
                            allQuartetsPure = false;
                        }
                    }
                }
            }

            if (allQuartetsPure) {
                cluster.addLast(o);   // or addFirst(o) if you want to keep consistent with previous logic
                iterator.remove();    // remove from unclustered
            }
        }
    }

    /**
     * Finds clusters of size 3 for the SAG algorithm.
     *
     * @param unionClustered The set of union pure variables.
     * @return A set of lists of integers representing the mixed clusters.
     */
    private Set<List<Integer>> findMixedClusters(Set<Integer> unionClustered) {
        Set<List<Integer>> mixedClusters = new HashSet<>();

        if (unionClustered.isEmpty()) {
            return new HashSet<>();
        }

        Set<Integer> _unionClustered = new HashSet<>(unionClustered);
        List<Integer> unclustered = new ArrayList<>(allVariables());
        unclustered.removeAll(_unionClustered);

        List<Integer> variables = new ArrayList<>(unclustered);

        ChoiceGenerator gen = new ChoiceGenerator(unclustered.size(), 3);
        int[] choice;


        CHOICE:
        while ((choice = gen.next()) != null) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            int n1 = unclustered.get(choice[0]);
            int n2 = unclustered.get(choice[1]);
            int n3 = unclustered.get(choice[2]);

            if (!(variables.contains(n1) && variables.contains(n2) && variables.contains(n3))) {
                continue;
            }

            List<Integer> cluster = new  ArrayList<>();
            cluster.add(n1);
            cluster.add(n2);
            cluster.add(n3);

            for (int o : allVariables()) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (cluster.contains(o)) continue;

                List<Integer> _cluster = new ArrayList<>(cluster);
                _cluster.add(o);

                if (containsZeroCorrelation(cluster)) {
                    continue CHOICE;
                }

                if (!vanishes(_cluster)) {
                    continue CHOICE;
                }

                mixedClusters.add(cluster);
                variables.removeAll(cluster);

                if (this.verbose) {
                    log("3-cluster found: " + ClusterSignificance.variablesForIndices(cluster, this.variables));
                }
            }
        }

        return mixedClusters;
    }

    /**
     * Determines if a given quartet of variables satisfies the conditions for being considered pure.
     *
     * @param quartet The list of integers representing a quartet of variables.
     * @return The Purity judgment of the quarter
     * @see Purity
     */
    private Purity pure(List<Integer> quartet) {
        if (containsZeroCorrelation(quartet)) {
            return Purity.UNDECIDED;
        }

        if (pureQuartets.contains(new HashSet<>(quartet))) {
            return Purity.PURE;
        }

        if (impureQuartets.contains(new HashSet<>(quartet))) {
            return Purity.IMPURE;
        }

        if (vanishes(quartet)) {
            List<Integer> vars = allVariables();

            for (int o : vars) {
                if (quartet.contains(o)) continue;

                for (int j = 0; j < quartet.size(); j++) {
                    List<Integer> _quartet = new ArrayList<>(quartet);
                    _quartet.set(j, o);

                    if (!vanishes(_quartet)) {
                        impureQuartets.add(new HashSet<>(_quartet));
                        return Purity.IMPURE;
                    }
                }
            }

            System.out.println("PURE: " + quartet);

            pureQuartets.add(new HashSet<>(quartet));
            return Purity.PURE;
        } else {
            impureQuartets.add(new HashSet<>(quartet));
            return Purity.IMPURE;
        }
    }

    private boolean exchange(Set<List<Integer>> clusters) {
        boolean moved = false;

        for (List<Integer> cluster : new HashSet<>(clusters)) {
            if (cluster.size() != 4) {
                continue;
            }

            for (Integer o : new HashSet<>(cluster)) {
                for (List<Integer> _cluster : new HashSet<>(clusters)) {
                    if (_cluster == cluster) {
                        continue;
                    }

                    if (_cluster.contains(o)) continue;
                    if (!cluster.contains(o)) continue;

                    if (isAllQuartetsPureAppended(_cluster, o)) {
                        _cluster.add(o);

                        if (!containsZeroCorrelation(_cluster)) {
                            cluster.remove(o);
                            clusters.remove(cluster);
                            moved = true;
                        } else {
                            _cluster.remove(o);
                        }
                    }
                }
            }

            Set<Integer> unclustered = new HashSet<>(allVariables());
            Set<Integer> clustered = union(clusters);
            unclustered.removeAll(clustered);

            for (Integer o : new HashSet<>(unclustered)) {
                for (List<Integer> _cluster : new HashSet<>(clusters)) {
                    if (_cluster == cluster) {
                        continue;
                    }

                    if (_cluster.contains(o)) continue;
                    if (!cluster.contains(o)) continue;

                    if (isAllQuartetsPureAppended(_cluster, o)) {
                        _cluster.add(o);

                        if (!containsZeroCorrelation(_cluster)) {
                            cluster.remove(o);
                            clusters.remove(cluster);
                            moved = true;
                        } else {
                            _cluster.remove(o);
                        }
                    }
                }
            }
        }

        return moved;
    }

    private boolean isAllQuartetsPureAppended(List<Integer> cluster, int o) {

        // Check all quartets with o and 3 other elements in the cluster
        int size = cluster.size();

        for (int i = 0; i < size - 2; i++) {
            for (int j = i + 1; j < size - 1; j++) {
                for (int k = j + 1; k < size; k++) {
                    List<Integer> quartet = List.of(cluster.get(i), cluster.get(j), cluster.get(k), o);

                    if (pure(quartet) != Purity.PURE) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Constructs a quartet from four given integers.
     *
     * @param n1 The first integer.
     * @param n2 The second integer.
     * @param n3 The third integer.
     * @param n4 The fourth integer.
     * @return A list containing the four integers in the order they were passed in.
     * @throws IllegalArgumentException If any of the integers are duplicated.
     */
    private List<Integer> quartet(int n1, int n2, int n3, int n4) {
        List<Integer> quartet = new ArrayList<>();
        quartet.add(n1);
        quartet.add(n2);
        quartet.add(n3);
        quartet.add(n4);
        return quartet;
    }

    /**
     * Determines if the quartet of variables vanishes based on the test type.
     *
     * @param quartet The list of integers representing the quartet of variables.
     * @return True if the quartet vanishes, false otherwise.
     */
    private boolean vanishes(List<Integer> quartet) {
        if (quartet.size() != 4) {
            throw new IllegalArgumentException("Expecting a quartet: " + quartet);
        }

        int n1 = quartet.get(0);
        int n2 = quartet.get(1);
        int n3 = quartet.get(2);
        int n4 = quartet.get(3);

        return vanishes(n1, n2, n3, n4);
    }

    private boolean allPairsDependent(List<Integer> vars) {


        for (int i = 0; i < vars.size(); i++) {
            for (int j = i + 1; j < vars.size(); j++) {
                Node x = independenceTest.getVariable(variableNames.get(vars.get(i)));
                Node y = independenceTest.getVariable(variableNames.get(vars.get(j)));
                try {
                    if (independenceTest.checkIndependence(x, y).isIndependent()) return false;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return true;
    }

    /**
     * Checks if a given cluster has a zero correlation among its variables. Legitimate clusters have zero correlation.
     *
     * @param cluster The list of integers representing the cluster.
     * @return True if the cluster has zero correlation, false otherwise.
     */
    private boolean containsZeroCorrelation(List<Integer> cluster) {
        for (int i = 0; i < cluster.size(); i++) {
            for (int j = i + 1; j < cluster.size(); j++) {
                double r = this.corr.getValue(cluster.get(i), cluster.get(j));
                int n = this.corr.getSampleSize();
                int zSize = 0;

                double q = .5 * (FastMath.log(1.0 + abs(r)) - FastMath.log(1.0 - abs(r)));
                double df = n - 3. - zSize;

                double fisherZ = sqrt(df) * q;

                if (2 * (1.0 - this.normal.cumulativeProbability(fisherZ)) > alpha) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Determines if the quartet of variables vanishes based on the test type.
     *
     * @param x The first variable index.
     * @param y The second variable index.
     * @param z The third variable index.
     * @param w The fourth variable index.
     * @return True if the quartet vanishes, false otherwise.
     */
    private boolean vanishes(int x, int y, int z, int w) {
        int[][] ints1 = {{x, y}, {z, w}};
        int[][] ints2 = {{x, z}, {y, w}};

        List<int[][]> ints = new ArrayList<>();
        ints.add(ints1);
        ints.add(ints2);

        switch (this.testType) {
            case 1 -> {
                return this.test1.allGreaterThanAlpha(ints, this.alpha);
            }
            case 2 -> {
                return this.test2.tetrads(ints) > this.alpha;
            }
            case 3 -> {
                return this.test3.allGreaterThanAlpha(ints, this.alpha);
            }
            case 4 -> {
                return this.test4.allGreaterThanAlpha(ints, this.alpha);
            }
        }

        throw new IllegalArgumentException("Only the delta and wishart tests are being used: " + this.testType);
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
     * Returns the union of all integers in the given list of clusters.
     *
     * @param pureClusters The set of clusters, where each cluster is represented as a list of integers.
     * @return A set containing the union of all integers in the clusters.
     */
    private Set<Integer> union(Set<List<Integer>> pureClusters) {
        Set<Integer> unionPure = new HashSet<>();

        for (Collection<Integer> cluster : pureClusters) {
            unionPure.addAll(cluster);
        }

        return unionPure;
    }

    private Set<Integer> unionSet(Set<Set<Integer>> pureClusters) {
        Set<Integer> unionPure = new HashSet<>();

        for (Collection<Integer> cluster : pureClusters) {
            unionPure.addAll(cluster);
        }

        return unionPure;
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




