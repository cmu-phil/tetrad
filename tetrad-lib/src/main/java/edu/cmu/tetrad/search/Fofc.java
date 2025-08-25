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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ntad_test.BollenTing;
import edu.cmu.tetrad.search.ntad_test.Cca;
import edu.cmu.tetrad.search.ntad_test.NtadTest;
import edu.cmu.tetrad.search.utils.ClusterSignificance;
import edu.cmu.tetrad.search.utils.Tetrad;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;


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
     * The list of all variables.
     */
    private final List<Node> variables;
    /**
     * The significance level.
     */
    private final double alpha;
    /**
     * The tetrad test to use.
     */
    private final NtadTest test;
    /**
     * Whether verbose output is desired.
     */
    private boolean verbose;
    /**
     * A cache of pure tetrads.
     */
    private Set<Set<Integer>> pureQuartets;
    /**
     * A cache of impure tetrads.
     */
    private Set<Set<Integer>> impureQuartets;
    /**
     * Represents the fraction of purity required when appending variables to clusters. This value determines the
     * strictness of the constraints applied during the clustering process. A higher value implies stricter requirements
     * for purity when merging variables.
     */
    private double appendPurityFraction = 1;

    /**
     * Conctructor.
     *
     * @param dataSet The continuous dataset searched over.
     * @param test    The NTad test to use.
     * @param alpha   The alpha significance cutoff.
     */
    public Fofc(DataSet dataSet, NtadTest test, double alpha) {
        this.variables = dataSet.getVariables();
        this.alpha = alpha;
        this.test = test;
    }

    /**
     * Runs the search and returns a graph of clusters with the ir respective latent parents.
     *
     * @return This graph.
     */
    public List<List<Integer>> findClusters() {
        this.pureQuartets = new HashSet<>();
        this.impureQuartets = new HashSet<>();

        Set<List<Integer>> allClusters = estimateClustersSag();

        return new ArrayList<>(allClusters);
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

    /**
     * Finds clusters of size 4 or higher for the tetrad-first algorithm.
     */
    private Set<List<Integer>> findPureClusters() {
        List<Integer> variables = allVariables();
        Set<List<Integer>> clusters = new HashSet<>();

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

            if (!(unclustered.contains(n1) && unclustered.contains(n2) && unclustered.contains(n3)
                  && unclustered.contains(n4))) {
                continue;
            }

            List<Integer> cluster = tetrad(n1, n2, n3, n4);

            // Note that purity needs to be assessed with respect to all the variables to
            // remove all latent-measure impurities between pairs of latents.
            if (pure(cluster) == Purity.PURE) {
                growCluster(unclustered, cluster);

                if (this.verbose) {
                    log("Cluster found: " + ClusterSignificance.variablesForIndices(cluster, this.variables));
                }

                clusters.add(cluster);
                unclustered.removeAll(cluster);
            }
        }

        return clusters;
    }

    private void growCluster(List<Integer> unclustered, List<Integer> cluster) {
        // iterate with an Iterator so we can remove from unclustered safely
        Iterator<Integer> iterator = unclustered.iterator();

        while (iterator.hasNext()) {
            int o = iterator.next();
            if (cluster.contains(o)) continue;

            int size = cluster.size();
            int tests = 0, pureCount = 0;

            // Check all 5-combinations from the current cluster with the candidate o
            for (int i = 0; i < size; i++) {
                for (int j = i + 1; j < size; j++) {
                    for (int k = j + 1; k < size; k++) {
                        tests++;
                        List<Integer> tetrad = List.of(
                                cluster.get(i), cluster.get(j), cluster.get(k), o
                        );
                        if (pure(tetrad) == Purity.PURE) pureCount++;
                    }
                }
            }

            double frac = tests == 0 ? 0.0 : (pureCount / (double) tests);

            if (frac >= appendPurityFraction) {
                cluster.add(o);     // NOTE: use add(), not addLast()
                iterator.remove();
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

        ChoiceGenerator gen = new ChoiceGenerator(unclustered.size(), 5);
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

            List<Integer> cluster = new ArrayList<>();
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

                if (!vanishes(_cluster)) {
                    continue CHOICE;
                }
            }

            mixedClusters.add(cluster);
            variables.removeAll(cluster);

            if (this.verbose) {
                log("5-cluster found: " + ClusterSignificance.variablesForIndices(cluster, this.variables));
            }
        }

        return mixedClusters;
    }

    private Purity pure(List<Integer> tetrad) {
        Set<Integer> key = new HashSet<>(tetrad);
        if (pureQuartets.contains(key)) return Purity.PURE;
        if (impureQuartets.contains(key)) return Purity.IMPURE;

        // Base vanishing check for the candidate tetrad
        if (vanishes(tetrad)) {
            List<Integer> vars = allVariables();
            for (int o : vars) {
                if (tetrad.contains(o)) continue;

                for (int j = 0; j < tetrad.size(); j++) {
                    List<Integer> _tetrad = new ArrayList<>(tetrad);
                    _tetrad.set(j, o);

                    if (!vanishes(_tetrad)) {
                        impureQuartets.add(new HashSet<>(_tetrad));
                        return Purity.IMPURE;
                    }
                }
            }

            // Passed all substitutions -> PURE
            pureQuartets.add(key);
            return Purity.PURE;
        } else {
            impureQuartets.add(key);
            return Purity.IMPURE;
        }
    }

    /**
     * Constructs a tetrad from four given integers.
     *
     * @param n1 The first integer.
     * @param n2 The second integer.
     * @param n3 The third integer.
     * @param n4 The fourth integer.
     * @return A list containing the four integers in the order they were passed in.
     * @throws IllegalArgumentException If any of the integers are duplicated.
     */
    private List<Integer> tetrad(int n1, int n2, int n3, int n4) {
        List<Integer> tetrad = new ArrayList<>();
        tetrad.add(n1);
        tetrad.add(n2);
        tetrad.add(n3);
        tetrad.add(n4);
        return tetrad;
    }

    /**
     * Determines if a given tetrad of variables "vanishes".
     *
     * @param tetrad The list of indices representing variables in the tetrad.
     * @return True if the tetrad vanishes, false otherwise.
     */
    private boolean vanishes(List<Integer> tetrad) {
        int n1 = tetrad.get(0);
        int n2 = tetrad.get(1);
        int n3 = tetrad.get(2);
        int n4 = tetrad.get(3);

        return vanishes(n1, n2, n3, n4);
    }

    /**
     * Checks if the given numbers follow the vanishing pattern.
     *
     * @param n1 first number
     * @param n2 second number
     * @param n3 third number
     * @param n4 fourth number
     * @return true if the numbers follow the vanishing pattern; false otherwise
     */
    private boolean vanishes(int n1, int n2, int n3, int n4) {
        Tetrad t1 = new Tetrad(n1, n2, n3, n4);
        Tetrad t2 = new Tetrad(n1, n3, n2, n4);
        Tetrad t3 = new Tetrad(n1, n4, n2, n3);

        List<Tetrad[]> independents = new ArrayList<>();

        if (test instanceof BollenTing) {

            // For Bollen-Ting we need an independent subset of the tetrads.
            independents.add(new Tetrad[]{t1, t3});
        } else {
            independents.add(new Tetrad[]{t1, t2, t3});
        }

        for (Tetrad[] tetrads : independents) {
            List<int[][]> _independents = new ArrayList<>();

            for (Tetrad tetrad : tetrads) {
                int[] x = {tetrad.getI(), tetrad.getJ()};
                int[] y = {tetrad.getK(), tetrad.getL()};

                _independents.add(new int[][]{x, y});
            }

            if (this.test instanceof BollenTing) {

                double p = this.test.ntads(_independents);
                if (Double.isNaN(p)) {
                    return false;
                }

                if (p < this.alpha) return false;
            } else if (test instanceof Cca) {
                for (int[][] independent : _independents) {
                    int r = Math.min(independent[0].length, independent[1].length) - 1;
                    int rank = ((Cca) this.test).rank(independent, alpha);
                    if (rank != r) return false;
                }
            } else {
                if (!this.test.allGreaterThanAlpha(_independents, alpha)) {
                    return false;
                }
            }
        }

        return true;
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

    public void setAppendPurityFraction(double appendPurityFraction) {
        this.appendPurityFraction = appendPurityFraction;
    }

    private enum Purity {PURE, IMPURE}
}









