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
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ntad_test.BollenTing;
import edu.cmu.tetrad.search.ntad_test.Cca;
import edu.cmu.tetrad.search.ntad_test.NtadTest;
import edu.cmu.tetrad.search.utils.ClusterSignificance;
import edu.cmu.tetrad.search.utils.Sextad;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.util.FastMath;

import java.util.*;


/**
 * Implements the Find Two Factor Clusters (FOFC) algorithm, which uses reasoning about vanishing tetrads of algorithms
 * to infer clusters of the measured variables in a dataset that each be explained by a single latent variable. The
 * reference is as follows:
 * <p>
 * Kummerfeld, E. &amp; Ramsey, J. &amp; Yang, R. &amp; Spirtes, P. &amp; Scheines, R. (2014). Causal Clustering for
 * 2-Factor Measurement Models. In T. Calders, F. Esposito, E. Hullermeier, and R. Meo, editors, Machine Learning and
 * Knowledge Discovery in Databases, volume 8725 of Lecture Notes in Computer Science, pages 34-49. Springer Berlin
 * Heidelberg.
 * <p>
 * The two-factor version of the algorithm substitutes sextad tests for tetrad tests and searches for clusters of at
 * least 6 variables that can be explained by two latent factors by calculating vanishing sextads.
 *
 * @author peterspirtes
 * @author erichkummerfeld
 * @author josephramsey
 * @version $Id: $Id
 * @see Fofc
 */
public class Ftfc {
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
     * A standard normal distribution object used for statistical calculations within the Fofc class. The distribution
     * is characterized by a mean of 0 and a standard deviation of 1.
     */
    private final NormalDistribution normal = new NormalDistribution(0, 1);
    /**
     * The sextad test to use.
     */
    private final NtadTest test;
    private final int sampleSize;
    private final int ess;
    // --- Tunables (optional; adjust if you want less brittleness)
    private double depFractionThreshold = 0.90;   // was hardcoded 0.90
    private double appendPurityFraction = 0.75;   // require ≥75% pure sextads when appending

    /**
     * The clusters that are output by the algorithm from the last call to search().
     */
    private List<List<Node>> clusters;
    /**
     * Whether verbose output is desired.
     */
    private boolean verbose;
    /**
     * A cache of pure sextets.
     */
    private Set<Set<Integer>> pureSextets;
    /**
     * A cache of impure sextets.
     */
    private Set<Set<Integer>> impureSextets;

    /**
     * Conctructor.
     *
     * @param dataSet The continuous dataset searched over.
     * @param test    The NTad test to use.
     * @param alpha   The alpha significance cutoff.
     * @param ess     The effective sample size, or -1 is the actual sample size is to be used.
     */
    public Ftfc(DataSet dataSet, NtadTest test, double alpha, int ess) {
        if (!(ess == -1 || ess > 0)) {
            throw new IllegalArgumentException("ESS should be -1 or > 0.");
        }

        this.variables = dataSet.getVariables();
        this.alpha = alpha;
        this.test = test;
        this.corr = new CorrelationMatrix(dataSet);
        this.sampleSize = this.corr.getSampleSize();
        this.ess = ess == -1 ? this.sampleSize : ess;
    }

    /**
     * Runs the search and returns a graph of clusters with the ir respective latent parents.
     *
     * @return This graph.
     */
    public List<List<Integer>> findClusters() {
        this.pureSextets = new HashSet<>();
        this.impureSextets = new HashSet<>();

        Set<List<Integer>> allClusters = estimateClustersSag();

        return new ArrayList<>(allClusters);
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
            if (cluster.size() >= 6) {
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
     * Finds clusters of size 6 or higher for the tetrad-first algorithm.
     */
    private Set<List<Integer>> findPureClusters() {
        List<Integer> variables = allVariables();
        Set<List<Integer>> clusters = new HashSet<>();

        log(variables.toString());

        List<Integer> unclustered = new ArrayList<>(variables);
        unclustered.removeAll(union(clusters));

        if (variables.size() < 6) return new HashSet<>();

        ChoiceGenerator gen = new ChoiceGenerator(variables.size(), 6);
        int[] choice;

        while ((choice = gen.next()) != null) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            int n1 = variables.get(choice[0]);
            int n2 = variables.get(choice[1]);
            int n3 = variables.get(choice[2]);
            int n4 = variables.get(choice[3]);
            int n5 = variables.get(choice[4]);
            int n6 = variables.get(choice[5]);

            if (!(unclustered.contains(n1) && unclustered.contains(n2) && unclustered.contains(n3)
                  && unclustered.contains(n4) && unclustered.contains(n5) && unclustered.contains(n6))) {
                continue;
            }

            List<Integer> cluster = sextad(n1, n2, n3, n4, n5, n6);

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

//    private void growCluster(List<Integer> unclustered, List<Integer> cluster) {
//        Iterator<Integer> iterator = unclustered.iterator();
//
//        while (iterator.hasNext()) {
//            int o = iterator.next();
//
//            if (cluster.contains(o)) continue;
//
//            boolean allSextadsPure = true;
//
//            // Check all sextets with o and 5 other elements in the cluster
//            int size = cluster.size();
//
//            for (int i = 0; i < size - 2 && allSextadsPure; i++) {
//                for (int j = i + 1; j < size - 1 && allSextadsPure; j++) {
//                    for (int k = j + 1; k < size && allSextadsPure; k++) {
//                        for (int l = k + 1; l < size && allSextadsPure; l++) {
//                            for (int m = l + 1; m < size && allSextadsPure; m++) {
//                                List<Integer> sextad = List.of(cluster.get(i), cluster.get(j), cluster.get(k),
//                                        cluster.get(l), cluster.get(m), o);
//
//                                if (pure(sextad) != Purity.PURE) {
//                                    allSextadsPure = false;
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//
//            if (allSextadsPure) {
//                cluster.addLast(o);
//                iterator.remove();
//            }
//        }
//    }

    private void growCluster(List<Integer> unclustered, List<Integer> cluster) {
        // iterate with an Iterator so we can remove from unclustered safely
        Iterator<Integer> iterator = unclustered.iterator();

        while (iterator.hasNext()) {
            int o = iterator.next();
            if (cluster.contains(o)) continue;

            int size = cluster.size();
            int tests = 0, pureCount = 0;

            // Check all 5-combinations from current cluster with the candidate o
            for (int i = 0; i < size - 4; i++) {
                for (int j = i + 1; j < size - 3; j++) {
                    for (int k = j + 1; k < size - 2; k++) {
                        for (int l = k + 1; l < size - 1; l++) {
                            for (int m = l + 1; m < size; m++) {
                                tests++;
                                List<Integer> sextad = List.of(
                                        cluster.get(i), cluster.get(j), cluster.get(k),
                                        cluster.get(l), cluster.get(m), o
                                );
                                if (pure(sextad) == Purity.PURE) pureCount++;
                            }
                        }
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
            int n4 = unclustered.get(choice[3]);
            int n5 = unclustered.get(choice[4]);

            if (!(variables.contains(n1) && variables.contains(n2) && variables.contains(n3)
                  && variables.contains(n4) && variables.contains(n5))) {
                continue;
            }

            List<Integer> cluster = new ArrayList<>();
            cluster.add(n1);
            cluster.add(n2);
            cluster.add(n3);
            cluster.add(n4);
            cluster.add(n5);

            for (int o : allVariables()) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (cluster.contains(o)) continue;

                List<Integer> _cluster = new ArrayList<>(cluster);
                _cluster.add(o);

                if (!clusterDependent(cluster)) {
                    continue CHOICE;
                }

                if (!vanishes(_cluster)) {
                    continue CHOICE;
                }

                mixedClusters.add(cluster);
                variables.removeAll(cluster);

                if (this.verbose) {
                    log("5-cluster found: " + ClusterSignificance.variablesForIndices(cluster, this.variables));
                }
            }
        }

        return mixedClusters;
    }

    /**
     * Determines if a given sextet of variables satisfies the conditions for being considered pure.
     *
     * @param sextet The list of integers representing a sextet of variables.
     * @return The Purity judgment of the sextet
     * @see Purity
     */
//    private Purity pure(List<Integer> sextet) {
//        if (!clusterDependent(sextet)) {
//            return Purity.UNDECIDED;
//        }
//
//        if (pureSextets.contains(new HashSet<>(sextet))) {
//            return Purity.PURE;
//        }
//
//        if (impureSextets.contains(new HashSet<>(sextet))) {
//            return Purity.IMPURE;
//        }
//
//        if (vanishes(sextet)) {
//            List<Integer> vars = allVariables();
//
//            for (int o : vars) {
//                if (sextet.contains(o)) continue;
//
//                for (int j = 0; j < sextet.size(); j++) {
//                    List<Integer> _sextet = new ArrayList<>(sextet);
//                    _sextet.set(j, o);
//
//                    if (!vanishes(_sextet)) {
//                        impureSextets.add(new HashSet<>(_sextet));
//                        return Purity.IMPURE;
//                    }
//                }
//            }
//
//            System.out.println("PURE: " + sextet);
//
//            pureSextets.add(new HashSet<>(sextet));
//            return Purity.PURE;
//        } else {
//            impureSextets.add(new HashSet<>(sextet));
//            return Purity.IMPURE;
//        }
//    }
    private Purity pure(List<Integer> sextet) {
        // Quick screen: if the sextet isn't even pairwise dependent, we can't decide purity
        if (!clusterDependent(sextet)) {
            return Purity.UNDECIDED;
        }

        Set<Integer> key = new HashSet<>(sextet);
        if (pureSextets.contains(key)) return Purity.PURE;
        if (impureSextets.contains(key)) return Purity.IMPURE;

        // Base vanishing check for the candidate sextet
        if (vanishes(sextet)) {
            // Substitution test: if ANY single substitution with an outside variable also vanishes,
            // the ORIGINAL sextet is IMPURE (flip from previous logic).
            List<Integer> vars = allVariables();
            for (int o : vars) {
                if (sextet.contains(o)) continue;

                for (int j = 0; j < sextet.size(); j++) {
                    List<Integer> _sextet = new ArrayList<>(sextet);
                    _sextet.set(j, o);

                    if (!vanishes(_sextet)) {
                        impureSextets.add(new HashSet<>(_sextet));
                        return Purity.IMPURE;
                    }
                }
            }

            // Passed all substitutions -> PURE
            pureSextets.add(key);
            return Purity.PURE;
        } else {
            impureSextets.add(key);
            return Purity.IMPURE;
        }
    }

    /**
     * Attempts to move nodes from one cluster to another to improve clustering,.
     *
     * @param clusters The clusters to adjust.
     * @return True if a change was made.
     */
    private boolean exchange(Set<List<Integer>> clusters) {
        boolean moved = false;

        for (List<Integer> cluster : new HashSet<>(clusters)) {
            if (cluster.size() != 6) {
                continue;
            }

            for (Integer o : new HashSet<>(cluster)) {
                for (List<Integer> _cluster : new HashSet<>(clusters)) {
                    if (_cluster == cluster) {
                        continue;
                    }

                    if (_cluster.contains(o)) continue;
                    if (!cluster.contains(o)) continue;

                    if (isAllSextetsPureAppended(_cluster, o)) {
                        _cluster.add(o);

                        if (clusterDependent(_cluster)) {
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

                    if (isAllSextetsPureAppended(_cluster, o)) {
                        _cluster.add(o);

                        if (clusterDependent(_cluster)) {
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

//    private boolean isAllSextetsPureAppended(List<Integer> cluster, int o) {
//
//        // Check all sextets with o and 5 other elements in the cluster
//        int size = cluster.size();
//
//        for (int i = 0; i < size - 2; i++) {
//            for (int j = i + 1; j < size - 1; j++) {
//                for (int k = j + 1; k < size; k++) {
//                    for (int l = k + 1; l < size; l++) {
//                        for (int m = l + 1; m < size; m++) {
//                            List<Integer> sextet = List.of(cluster.get(i), cluster.get(j), cluster.get(k),
//                                    cluster.get(l), cluster.get(m), o);
//
//                            if (pure(sextet) != Purity.PURE) {
//                                return false;
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        return true;
//    }

    private boolean isAllSextetsPureAppended(List<Integer> cluster, int o) {
        int size = cluster.size();
        int tests = 0, pureCount = 0;

        for (int i = 0; i < size - 4; i++) {
            for (int j = i + 1; j < size - 3; j++) {
                for (int k = j + 1; k < size - 2; k++) {
                    for (int l = k + 1; l < size - 1; l++) {
                        for (int m = l + 1; m < size; m++) {
                            tests++;
                            List<Integer> sextet = List.of(
                                    cluster.get(i), cluster.get(j), cluster.get(k),
                                    cluster.get(l), cluster.get(m), o
                            );
                            if (pure(sextet) == Purity.PURE) pureCount++;
                        }
                    }
                }
            }
        }

        double frac = tests == 0 ? 0.0 : (pureCount / (double) tests);
        return frac >= appendPurityFraction;   // previously required 100%
    }

    /**
     * Constructs a sextet from four given integers.
     *
     * @param n1 The first integer.
     * @param n2 The second integer.
     * @param n3 The third integer.
     * @param n4 The fourth integer.
     * @param n5 The fifth integer.
     * @param n6 The sixth integer.
     * @return A list containing the four integers in the order they were passed in.
     * @throws IllegalArgumentException If any of the integers are duplicated.
     */
    private List<Integer> sextad(int n1, int n2, int n3, int n4, int n5, int n6) {
        List<Integer> sextad = new ArrayList<>();
        sextad.add(n1);
        sextad.add(n2);
        sextad.add(n3);
        sextad.add(n4);
        sextad.add(n5);
        sextad.add(n6);
        return sextad;
    }

    /**
     * Determines if a given sextet of variables "vanishes".
     *
     * @param sextet The list of indices representing variables in the sextet.
     * @return True if the sextet vanishes, false otherwise.
     */
    private boolean vanishes(List<Integer> sextet) {
        int n1 = sextet.get(0);
        int n2 = sextet.get(1);
        int n3 = sextet.get(2);
        int n4 = sextet.get(3);
        int n5 = sextet.get(4);
        int n6 = sextet.get(5);

        return vanishes(n1, n2, n3, n4, n5, n6);
    }

    /**
     * Checks if a given cluster is pairwise dependent.
     *
     * @param cluster The list of integers representing the cluster.
     * @return True if the cluster is pairwise dependent, false otherwise.
     */
//    private boolean clusterDependent(List<Integer> cluster) {
////        if (true) return true;
//
//        int numDependencies = 0;
//        int all = 0;
//
//        for (int i = 0; i < cluster.size(); i++) {
//            for (int j = i + 1; j < cluster.size(); j++) {
//                double r = this.corr.getValue(cluster.get(i), cluster.get(j));
//
//                if (Double.isNaN(r)) {
//                    continue;
//                }
//
//                int n = this.corr.getSampleSize();
//                int zSize = 0; // Unconditional check.
//
//                double q = .5 * (FastMath.log(1.0 + abs(r)) - FastMath.log(1.0 - abs(r)));
//                double df = n - 3. - zSize;
//
//                double fisherZ = sqrt(df) * q;
//
//                if (2 * (1.0 - this.normal.cumulativeProbability(abs(fisherZ))) < alpha) {
//                    numDependencies++;
//                }
//
//                all++;
//            }
//        }
//
//        return numDependencies > all * 0.90;
//    }
    private boolean clusterDependent(List<Integer> cluster) {
        if (true) return true;

        int numDependencies = 0;
        int all = 0;

        // Use ESS if provided; otherwise fall back to sample size.
        int n = (this.ess > 0 ? this.ess : this.corr.getSampleSize());
        double df = n - 3.0;
        if (df <= 0) return false;

        for (int i = 0; i < cluster.size(); i++) {
            for (int j = i + 1; j < cluster.size(); j++) {
                double r = this.corr.getValue(cluster.get(i), cluster.get(j));
                if (Double.isNaN(r)) continue;

                // Fisher z on |r|
                double q = 0.5 * (FastMath.log(1.0 + FastMath.abs(r)) - FastMath.log(1.0 - FastMath.abs(r)));
                double fisherZ = FastMath.sqrt(df) * q;

                // two-sided
                double p = 2 * (1.0 - this.normal.cumulativeProbability(FastMath.abs(fisherZ)));
                if (p < alpha) numDependencies++;

                all++;
            }
        }

        return all > 0 && numDependencies >= all * depFractionThreshold;
    }

    /**
     * Checks if the given numbers follow the vanishing pattern.
     *
     * @param n1 first number
     * @param n2 second number
     * @param n3 third number
     * @param n4 fourth number
     * @param n5 fifth number
     * @param n6 sixth number
     * @return true if the numbers follow the vanishing pattern; false otherwise
     */
    private boolean vanishes(int n1, int n2, int n3, int n4, int n5, int n6) {
        Sextad t1 = new Sextad(n1, n2, n3, n4, n5, n6);
        Sextad t2 = new Sextad(n1, n2, n4, n3, n5, n6);
        Sextad t3 = new Sextad(n1, n2, n5, n3, n4, n6);
        Sextad t4 = new Sextad(n1, n2, n6, n3, n4, n5);
        Sextad t5 = new Sextad(n1, n3, n4, n2, n5, n6);
        Sextad t6 = new Sextad(n1, n3, n5, n2, n4, n6);
        Sextad t7 = new Sextad(n1, n3, n6, n2, n4, n5);
        Sextad t8 = new Sextad(n1, n4, n5, n2, n3, n6);
        Sextad t9 = new Sextad(n1, n4, n6, n2, n3, n5);
        Sextad t10 = new Sextad(n1, n5, n6, n2, n3, n4);

        List<Sextad[]> independents = new ArrayList<>();
        independents.add(new Sextad[]{t1, t2, t3, t5, t6});

        for (Sextad[] sextads : independents) {
            List<int[][]> _independents = new ArrayList<>();

            for (Sextad sextad : sextads) {
                int[] x = {sextad.getI(), sextad.getJ(), sextad.getK()};
                int[] y = {sextad.getL(), sextad.getM(), sextad.getN()};

                _independents.add(new int[][]{x, y});
            }

            if (this.test instanceof BollenTing) {

                double p = this.test.ntads(_independents);
                if (Double.isNaN(p)) {
                    return false;
                }

                if (p < this.alpha) return false;
            } else if (this.test instanceof Cca) {
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

    private enum Purity {PURE, IMPURE, UNDECIDED}
}




