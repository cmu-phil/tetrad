///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
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
 * The algorithm employs tests of vanishing tetrads (list of 4 variables that follow a certain pattern in the
 * exchangeability of latent paths with respect to the data). The notion of vanishing tetrads is old one but is
 * explained in this book:
 * <p>
 * Spirtes, P., Glymour, C. N., Scheines, R., &amp; Heckerman, D. (2000). Causation, prediction, and search. MIT press.
 *
 * @author peterspirtes
 * @author erichkummerfeld
 * @author josephramsey
 * @version $Id: $Id
 * @see Ftfc
 * @see Bpc
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
     * The Delta test. Testing two tetrads simultaneously.
     */
    private final DeltaTetradTest test;
    /**
     * The tetrad test--using Ricardo's. Used only for Wishart.
     */
    private final TetradTestContinuous test2;
    /**
     * The data.
     */
    private final transient DataModel dataModel;

    /**
     * The type of test used.
     */
    private final BpcTestType testType;

    /**
     * The type of FOFC algorithm used.
     */
    private final Algorithm algorithm;

    /**
     * The clusters that are output by the algorithm from the last call to search().
     */
    private List<List<Node>> clusters;

    /**
     * Whether verbose output is desired.
     */
    private boolean verbose;

    /**
     * Whether the significance of the cluster should be checked for each cluster.
     */
    private boolean significanceChecked;

    /**
     * The type of cluster check should be performed.
     */
    private ClusterSignificance.CheckType checkType = ClusterSignificance.CheckType.Clique;

    /**
     * Constructor.
     *
     * @param cov       The covariance matrix searched over.
     * @param testType  The type of test used.
     * @param algorithm The type of FOFC algorithm used.
     * @param alpha     The alpha significance cutoff.
     * @see BpcTestType
     * @see Algorithm
     */
    public Fofc(ICovarianceMatrix cov, BpcTestType testType, Algorithm algorithm, double alpha) {
        if (testType == null) throw new NullPointerException("Null indepTest type.");
        cov = new CovarianceMatrix(cov);
        this.variables = cov.getVariables();
        this.alpha = alpha;
        this.testType = testType;
        this.test = new DeltaTetradTest(cov);
        this.test2 = new TetradTestContinuous(cov, testType, alpha);
        this.dataModel = cov;
        this.algorithm = algorithm;

        this.corr = new CorrelationMatrix(cov);


    }

    /**
     * Conctructor.
     *
     * @param dataSet   The continuous dataset searched over.
     * @param testType  The type of test used.
     * @param algorithm The type of FOFC algorithm used.
     * @param alpha     The alpha significance cutoff.
     * @see BpcTestType
     * @see Algorithm
     */
    public Fofc(DataSet dataSet, BpcTestType testType, Algorithm algorithm, double alpha) {
        if (testType == null) throw new NullPointerException("Null test type.");
        this.variables = dataSet.getVariables();
        this.alpha = alpha;
        this.testType = testType;
        this.test = new DeltaTetradTest(dataSet);
        this.test2 = new TetradTestContinuous(dataSet, testType, alpha);
        this.dataModel = dataSet;
        this.algorithm = algorithm;

        this.corr = new CorrelationMatrix(dataSet);
    }

    /**
     * Runs the search and returns a graph of clusters with the ir respective latent parents.
     *
     * @return This graph.
     */
    public Graph search() {
        Set<List<Integer>> allClusters;

        if (this.algorithm == Algorithm.SAG) {
            allClusters = estimateClustersTetradsFirst();
        } else if (this.algorithm == Algorithm.GAP) {
            allClusters = estimateClustersTriplesFirst();
        } else {
            throw new IllegalStateException("Expected SAG or GAP: " + this.testType);
        }

        this.clusters = ClusterSignificance.variablesForIndices2(allClusters, variables);

        System.out.println("allClusters = " + allClusters);
        System.out.println("this.clusters = " + this.clusters);

        ClusterSignificance clusterSignificance = new ClusterSignificance(variables, dataModel);
        clusterSignificance.printClusterPValues(allClusters);

        return convertToGraph(allClusters);
    }

    /**
     * Sets whether the significance of the cluster should be checked for each cluster.
     *
     * @param significanceChecked True, if so.
     */
    public void setSignificanceChecked(boolean significanceChecked) {
        this.significanceChecked = significanceChecked;
    }

    /**
     * Sets which type of cluster check should be performed.
     *
     * @param checkType The type to be performed.
     * @see ClusterSignificance.CheckType
     */
    public void setCheckType(ClusterSignificance.CheckType checkType) {
        this.checkType = checkType;
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
     * Returns the index of the variable that occurs most frequently in the given array. (renjiey).
     *
     * @param outliers An array of integers representing variables.
     * @return The index of the most frequently occurring variable.
     */
    private int findFrequentestIndex(Integer[] outliers) {
        Map<Integer, Integer> map = new HashMap<>();

        for (Integer outlier : outliers) {
            if (map.containsKey(outlier)) {
                map.put(outlier, map.get(outlier) + 1);
            } else {
                map.put(outlier, 1);
            }
        }

        Set<Map.Entry<Integer, Integer>> set = map.entrySet();
        Iterator<Map.Entry<Integer, Integer>> it = set.iterator();
        int nums = 0;// how many times variables occur?
        int key = 0;// the number occurs the most times

        while (it.hasNext()) {
            Map.Entry<Integer, Integer> entry = it.next();
            if (entry.getValue() > nums) {
                nums = entry.getValue();
                key = entry.getKey();
            }
        }

        return (key);
    }

    /**
     * This is the main function. It removes variables in the data such that the remaining correlation matrix does not
     * contain extreme value Inputs: correlation matrix, upper and lower bound for unacceptable correlations Output: and
     * dynamic array of removed variables renjiey
     */
    private ArrayList<Integer> removeVariables(Matrix correlationMatrix, double lowerBound, double upperBound,
                                               double percentBound) {
        Integer[] outlier = new Integer[correlationMatrix.getNumRows() * (correlationMatrix.getNumRows() - 1)];
        int count = 0;
        for (int i = 2; i < (correlationMatrix.getNumRows() + 1); i++) {
            for (int j = 1; j < i; j++) {

                if ((abs(correlationMatrix.get(i - 1, j - 1)) < lowerBound)
                    || (abs(correlationMatrix.get(i - 1, j - 1)) > upperBound)) {
                    outlier[count * 2] = i;
                    outlier[count * 2 + 1] = j;

                } else {
                    outlier[count * 2] = 0;
                    outlier[count * 2 + 1] = 0;
                }
                count = count + 1;
            }
        }

        //find out the variables that should be deleted
        ArrayList<Integer> removedVariables = new ArrayList<>();

        // Added the percent bound jdramsey
        while (outlier.length > 1 && removedVariables.size() < percentBound * correlationMatrix.getNumRows()) {
            //find out the variable that occurs most frequently in outlier
            int worstVariable = findFrequentestIndex(outlier);
            if (worstVariable > 0) {
                removedVariables.add(worstVariable);
            }

            //remove the correlations having the bad variable (change the relevant variables to 0)
            for (int i = 1; i < outlier.length + 1; i++) {
                if (outlier[i - 1] == worstVariable) {
                    outlier[i - 1] = 0;

                    if (i % 2 != 0) {
                        outlier[i] = 0;
                    } else {
                        outlier[i - 2] = 0;
                    }
                }
            }

            //delete zero elements in outlier
            outlier = removeZeroIndex(outlier);
        }

        log(removedVariables.size() + " variables removed: " + ClusterSignificance.variablesForIndices(removedVariables, variables));

        return (removedVariables);
    }

    /**
     * Removes the elements with zero index from the given integer array. (renjiey)
     *
     * @param outlier The array of integers.
     * @return The updated array with zero index elements removed.
     */
    private Integer[] removeZeroIndex(Integer[] outlier) {
        List<Integer> list = new ArrayList<>();
        Collections.addAll(list, outlier);
        for (Integer element : outlier) {
            if (element < 1) {
                list.remove(element);
            }
        }
        return list.toArray(new Integer[1]);
    }

    /**
     * Estimates clusters using the triples-first algorithm.
     *
     * @return A set of lists of integers representing the clusters.
     */
    private Set<List<Integer>> estimateClustersTriplesFirst() {
        List<Integer> _variables = allVariables();

        Set<Set<Integer>> triples = findPuretriples(_variables);
        Set<Set<Integer>> combined = combinePuretriples(triples, _variables);

        Set<List<Integer>> _combined = new HashSet<>();

        for (Set<Integer> c : combined) {
            List<Integer> a = new ArrayList<>(c);
            Collections.sort(a);
            _combined.add(a);
        }

        return _combined;

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
    private Set<List<Integer>> estimateClustersTetradsFirst() {
        List<Integer> _variables = allVariables();

        Set<List<Integer>> pureClusters = findPureClusters(_variables);
        Set<List<Integer>> mixedClusters = findMixedClusters(_variables, unionPure(pureClusters));
        Set<List<Integer>> allClusters = new HashSet<>(pureClusters);
        allClusters.addAll(mixedClusters);
        return allClusters;

    }

    /**
     * Finds pure triples from the given list of variables.
     *
     * @param allVariables The list of integers representing all variables.
     * @return A set of sets of integers representing the pure triples.
     */
    private Set<Set<Integer>> findPuretriples(List<Integer> allVariables) {
        if (allVariables.size() < 4) {
            return new HashSet<>();
        }

        log("Finding pure triples.");

        ChoiceGenerator gen = new ChoiceGenerator(allVariables.size(), 3);
        int[] choice;
        Set<Set<Integer>> puretriples = new HashSet<>();
        CHOICE:
        while ((choice = gen.next()) != null) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            int n1 = allVariables.get(choice[0]);
            int n2 = allVariables.get(choice[1]);
            int n3 = allVariables.get(choice[2]);

            List<Integer> triple = triple(n1, n2, n3);

            if (zeroCorr(triple)) continue;

            for (int o : allVariables) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (triple.contains(o)) {
                    continue;
                }

                List<Integer> quartet = quartet(n1, n2, n3, o);

                boolean vanishes = vanishes(quartet);

                if (!vanishes) {
                    continue CHOICE;
                }

            }

            HashSet<Integer> _cluster = new HashSet<>(triple);

            if (this.verbose) {
                log("++" + ClusterSignificance.variablesForIndices(triple, variables));
            }

            puretriples.add(_cluster);
        }

        return puretriples;
    }

    /**
     * Combines pure triples with given variables.
     *
     * @param puretriples The set of pure triples.
     * @param _variables  The list of variables.
     * @return A set of combined clusters.
     */
    private Set<Set<Integer>> combinePuretriples(Set<Set<Integer>> puretriples, List<Integer> _variables) {
        log("Growing pure triples.");
        Set<Set<Integer>> grown = new HashSet<>();

        // Lax grow phase with speedup.
        if (true) {
            Set<Integer> t = new HashSet<>();
            int count = 0;
            int total = puretriples.size();

            do {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (!puretriples.iterator().hasNext()) {
                    break;
                }

                Set<Integer> cluster = puretriples.iterator().next();
                Set<Integer> _cluster = new HashSet<>(cluster);

                for (int o : _variables) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    if (_cluster.contains(o)) continue;

                    List<Integer> _cluster2 = new ArrayList<>(_cluster);
                    int rejected = 0;
                    int accepted = 0;

                    ChoiceGenerator gen = new ChoiceGenerator(_cluster2.size(), 2);
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        t.clear();
                        t.add(_cluster2.get(choice[0]));
                        t.add(_cluster2.get(choice[1]));
                        t.add(o);

                        if (!puretriples.contains(t)) {
                            rejected++;
                        } else {
                            accepted++;
                        }
                    }

                    if (rejected > accepted) {
                        continue;
                    }

                    _cluster.add(o);

                    ClusterSignificance clusterSignificance = new ClusterSignificance(variables, dataModel);
                    clusterSignificance.setCheckType(checkType);

                    if (significanceChecked && clusterSignificance.significant(_cluster2, alpha)) {
                        _cluster2.remove(o);
                    }
                }

                // This takes out all pure clusters that are subsets of _cluster.
                ChoiceGenerator gen2 = new ChoiceGenerator(_cluster.size(), 3);
                int[] choice2;
                List<Integer> _cluster3 = new ArrayList<>(_cluster);

                while ((choice2 = gen2.next()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    int n1 = _cluster3.get(choice2[0]);
                    int n2 = _cluster3.get(choice2[1]);
                    int n3 = _cluster3.get(choice2[2]);

                    t.clear();
                    t.add(n1);
                    t.add(n2);
                    t.add(n3);

                    puretriples.remove(t);
                }

                if (this.verbose) {
                    log("Grown " + (++count) + " of " + total + ": "
                        + ClusterSignificance.variablesForIndices(new ArrayList<>(_cluster), variables));
                }
                grown.add(_cluster);
            } while (!puretriples.isEmpty());
        }

        // Lax grow phase without speedup.
        if (false) {
            int count = 0;
            int total = puretriples.size();

            // Optimized lax version of grow phase.
            for (Set<Integer> cluster : new HashSet<>(puretriples)) {
                Set<Integer> _cluster = new HashSet<>(cluster);

                for (int o : _variables) {
                    if (_cluster.contains(o)) continue;

                    List<Integer> _cluster2 = new ArrayList<>(_cluster);
                    int rejected = 0;
                    int accepted = 0;

                    ChoiceGenerator gen = new ChoiceGenerator(_cluster2.size(), 4);
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        int n1 = _cluster2.get(choice[0]);
                        int n2 = _cluster2.get(choice[1]);

                        List<Integer> triple = triple(n1, n2, o);

                        Set<Integer> t = new HashSet<>(triple);

                        if (!puretriples.contains(t)) {
                            rejected++;
                        } else {
                            accepted++;
                        }

//                        if (avgSumLnP(triple) < -10) continue CLUSTER;
                    }

                    if (rejected > accepted) {
                        continue;
                    }

                    _cluster.add(o);
                }

                for (Set<Integer> c : new HashSet<>(puretriples)) {
                    if (_cluster.containsAll(c)) {
                        puretriples.remove(c);
                    }
                }

                if (this.verbose) {
                    System.out.println("Grown " + (++count) + " of " + total + ": " + _cluster);
                }

                grown.add(_cluster);
            }
        }

        // Strict grow phase.
        if (false) {
            Set<Integer> t = new HashSet<>();
            int count = 0;
            int total = puretriples.size();

            do {
                if (!puretriples.iterator().hasNext()) {
                    break;
                }

                Set<Integer> cluster = puretriples.iterator().next();
                Set<Integer> _cluster = new HashSet<>(cluster);

                VARIABLES:
                for (int o : _variables) {
                    if (_cluster.contains(o)) continue;

                    List<Integer> _cluster2 = new ArrayList<>(_cluster);

                    ChoiceGenerator gen = new ChoiceGenerator(_cluster2.size(), 4);
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        int n1 = _cluster2.get(choice[0]);
                        int n2 = _cluster2.get(choice[1]);
                        int n3 = _cluster2.get(choice[2]);
                        int n4 = _cluster2.get(choice[3]);

                        t.clear();
                        t.add(n1);
                        t.add(n2);
                        t.add(n3);
                        t.add(n4);
                        t.add(o);

                        if (!puretriples.contains(t)) {
                            continue VARIABLES;
                        }

//                        if (avgSumLnP(new ArrayList<Integer>(t)) < -10) continue CLUSTER;
                    }

                    _cluster.add(o);
                }

                // This takes out all pure clusters that are subsets of _cluster.
                ChoiceGenerator gen2 = new ChoiceGenerator(_cluster.size(), 3);
                int[] choice2;
                List<Integer> _cluster3 = new ArrayList<>(_cluster);

                while ((choice2 = gen2.next()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    int n1 = _cluster3.get(choice2[0]);
                    int n2 = _cluster3.get(choice2[1]);
                    int n3 = _cluster3.get(choice2[2]);

                    t.clear();
                    t.add(n1);
                    t.add(n2);
                    t.add(n3);

                    puretriples.remove(t);
                }

                if (this.verbose) {
                    System.out.println("Grown " + (++count) + " of " + total + ": " + _cluster);
                }
                grown.add(_cluster);
            } while (!puretriples.isEmpty());
        }

        if (false) {
            System.out.println("# pure triples = " + puretriples.size());

            List<Set<Integer>> clusters = new LinkedList<>(puretriples);
            Set<Integer> t = new HashSet<>();

            for (int i = 0; i < clusters.size(); i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                System.out.println("I = " + i);

                J:
                for (int j = i + 1; j < clusters.size(); j++) {
                    Set<Integer> ci = clusters.get(i);
                    Set<Integer> cj = clusters.get(j);

                    if (ci == null) continue;
                    if (cj == null) continue;

                    Set<Integer> ck = new HashSet<>(ci);
                    ck.addAll(cj);

                    List<Integer> cm = new ArrayList<>(ck);

                    ChoiceGenerator gen = new ChoiceGenerator(cm.size(), 3);
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        t.clear();
                        t.add(cm.get(choice[0]));
                        t.add(cm.get(choice[1]));
                        t.add(cm.get(choice[2]));

                        if (!puretriples.contains(t)) {
                            continue J;
                        }
                    }

                    clusters.set(i, ck);
                    clusters.remove(j);
                    j--;
                    System.out.println("Removing " + ci + ", " + cj + ", adding " + ck);
                }
            }

            grown = new HashSet<>(clusters);
        }

        // Optimized pick phase.
        log("Choosing among grown clusters.");

        for (Set<Integer> l : grown) {
            ArrayList<Integer> _l = new ArrayList<>(l);
            Collections.sort(_l);
            if (this.verbose) {
                log("Grown: " + ClusterSignificance.variablesForIndices(_l, variables));
            }
        }

        Set<Set<Integer>> out = new HashSet<>();

        List<Set<Integer>> list = new ArrayList<>(grown);

        list.sort((o1, o2) -> o2.size() - o1.size());

        Set<Integer> all = new HashSet<>();

        CLUSTER:
        for (Set<Integer> cluster : list) {
            for (Integer i : cluster) {
                if (all.contains(i)) continue CLUSTER;
            }

            out.add(cluster);
            all.addAll(cluster);
        }

        return out;
    }

    // Finds clusters of size 4 or higher for the tetrad-first algorithm.
    private Set<List<Integer>> findPureClusters(List<Integer> _variables) {
        Set<List<Integer>> clusters = new HashSet<>();

        VARIABLES:
        while (!_variables.isEmpty()) {
            if (this.verbose) {
                System.out.println(_variables);
            }
            if (_variables.size() < 4) break;

            ChoiceGenerator gen = new ChoiceGenerator(_variables.size(), 4);
            int[] choice;

            while ((choice = gen.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                int n1 = _variables.get(choice[0]);
                int n2 = _variables.get(choice[1]);
                int n3 = _variables.get(choice[2]);
                int n4 = _variables.get(choice[3]);

                List<Integer> cluster = quartet(n1, n2, n3, n4);

                // Note that purity needs to be assessed with respect to all the variables in order to
                // remove all latent-measure impurities between pairs of latents.
                if (pure(cluster)) {

                    addOtherVariables(_variables, cluster);

                    if (this.verbose) {
                        log("Cluster found: " + ClusterSignificance.variablesForIndices(cluster, variables));
                    }
                    clusters.add(cluster);
                    _variables.removeAll(cluster);

                    continue VARIABLES;
                }
            }

            break;
        }

        return clusters;
    }

    /**
     * Adds other variables to the given cluster if they satisfy certain conditions.
     *
     * @param _variables The list of available variables.
     * @param cluster    The current cluster.
     */
    private void addOtherVariables(List<Integer> _variables, List<Integer> cluster) {
        O:
        for (int o : _variables) {
            if (cluster.contains(o)) continue;
            List<Integer> _cluster = new ArrayList<>(cluster);

            ChoiceGenerator gen2 = new ChoiceGenerator(_cluster.size(), 3);
            int[] choice2;
//            boolean found = false;

            while ((choice2 = gen2.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                int t1 = _cluster.get(choice2[0]);
                int t2 = _cluster.get(choice2[1]);
                int t3 = _cluster.get(choice2[2]);

                List<Integer> quartet = triple(t1, t2, t3);


                quartet.add(o);

                if (!pure(quartet)) {
                    continue O;
                }
            }

            log("Extending by " + this.variables.get(o));
            cluster.add(o);
        }
    }

    /**
     * Determines if adding a new cluster to the existing clusters would result in an insignificant model.
     *
     * @param clusters  The set of existing clusters.
     * @param cluster   The new cluster to be added.
     * @param variable  The list of variables.
     * @param dataModel The data model to be used in significance calculations.
     * @return True if adding the new cluster would result in an insignificant model, false otherwise.
     */
    private boolean modelInsignificantWithNewCluster(Set<List<Integer>> clusters, List<Integer> cluster,
                                                     List<Node> variable, DataModel dataModel) {
        List<List<Integer>> __clusters = new ArrayList<>(clusters);
        __clusters.add(cluster);

        ClusterSignificance clusterSignificance = new ClusterSignificance(variables, dataModel);
        clusterSignificance.setCheckType(checkType);
        double significance3 = clusterSignificance.getModelPValue(__clusters);

        if (this.verbose) {
            log("Significance * " + __clusters + " = " + significance3);
        }

        return significance3 < this.alpha;
    }

    /**
     * Finds clusters of size 3 3or the quartet-first algorithm.
     *
     * @param remaining The list of remaining variables.
     * @param unionPure The set of union pure variables.
     * @return A set of lists of integers representing the mixed clusters.
     */
    private Set<List<Integer>> findMixedClusters(List<Integer> remaining, Set<Integer> unionPure) {
        Set<List<Integer>> triples = new HashSet<>();

        if (unionPure.isEmpty()) {
            return new HashSet<>();
        }

        REMAINING:
        while (true) {
            if (remaining.size() < 3) break;

            ChoiceGenerator gen = new ChoiceGenerator(remaining.size(), 3);
            int[] choice;

            while ((choice = gen.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                int t2 = remaining.get(choice[0]);
                int t3 = remaining.get(choice[1]);
                int t4 = remaining.get(choice[2]);

                List<Integer> cluster = new ArrayList<>();
                cluster.add(t2);
                cluster.add(t3);
                cluster.add(t4);

                if (zeroCorr(cluster)) {
                    continue;
                }

                // Check all x as a cross-check; really only one should be necessary.
                boolean allVanish = true;
                boolean someVanish = false;

                for (int t1 : allVariables()) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    if (cluster.contains(t1)) continue;

                    List<Integer> _cluster = new ArrayList<>(cluster);
                    _cluster.add(t1);


                    if (vanishes(_cluster)) {
                        someVanish = true;
                    } else {
                        allVanish = false;
                        break;
                    }
                }

                if (someVanish && allVanish) {
                    triples.add(cluster);
                    unionPure.addAll(cluster);
                    remaining.removeAll(cluster);

                    if (this.verbose) {
                        log("3-cluster found: " + ClusterSignificance.variablesForIndices(cluster, variables));
                    }

                    continue REMAINING;
                }
            }

            break;
        }

        return triples;
    }

    /**
     * Calculate the degrees of freedom for Drton's method.
     *
     * @param n The number of variables.
     * @return The number of degrees of freedom.
     */
    private int dofDrton(int n) {
        int dof = ((n - 2) * (n - 3)) / 2 - 2;
        if (dof < 0) dof = 0;
        return dof;
    }

    /**
     * Determines if a given quartet of variables satisfies the conditions for being considered pure.
     *
     * @param quartet The list of integers representing a quartet of variables.
     * @return True if the quartet is pure, false otherwise.
     */
    private boolean pure(List<Integer> quartet) {
        if (zeroCorr(quartet)) {
            return false;
        }

        if (vanishes(quartet)) {
            for (int o : allVariables()) {
                if (quartet.contains(o)) continue;

                for (int i = 0; i < quartet.size(); i++) {
                    List<Integer> _quartet = new ArrayList<>(quartet);
                    _quartet.remove(quartet.get(i));
                    _quartet.add(o);

                    if (!(vanishes(_quartet))) {
                        return false;
                    }
                }
            }

            return true;
        }

        return false;
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

        if (new HashSet<>(quartet).size() < 4)
            throw new IllegalArgumentException("quartet elements must be unique: <" + n1 + ", " + n2 + ", " + n3 + ", " + n4 + ">");

        return quartet;
    }

    /**
     * Constructs a {@link List} of integers representing a triple.
     *
     * @param n1 The first integer.
     * @param n2 The second integer.
     * @param n3 The third integer.
     * @return A {@link List} containing the three integers in the order they were passed in.
     * @throws IllegalArgumentException If any of the integers are duplicated.
     */
    private List<Integer> triple(int n1, int n2, int n3) {
        List<Integer> triple = new ArrayList<>();
        triple.add(n1);
        triple.add(n2);
        triple.add(n3);

        if (new HashSet<>(triple).size() < 3)
            throw new IllegalArgumentException("triple elements must be unique: <" + n1 + ", " + n2 + ", " + n3 + ">");

        return triple;
    }

    /**
     * Determines if the quartet of variables vanishes based on the test type.
     *
     * @param quartet The list of integers representing the quartet of variables.
     * @return True if the quartet vanishes, false otherwise.
     */
    private boolean vanishes(List<Integer> quartet) {
        int n1 = quartet.get(0);
        int n2 = quartet.get(1);
        int n3 = quartet.get(2);
        int n4 = quartet.get(3);

        return vanishes(n1, n2, n3, n4);
    }

    /**
     * Checks if a given cluster has zero correlation among its variables.
     *
     * @param cluster The list of integers representing the cluster.
     * @return True if the cluster has zero correlation, false otherwise.
     */
    private boolean zeroCorr(List<Integer> cluster) {
        int count = 0;

        for (int i = 0; i < cluster.size(); i++) {
            for (int j = i + 1; j < cluster.size(); j++) {
                double r = this.corr.getValue(cluster.get(i), cluster.get(j));
                int N = this.corr.getSampleSize();
                double f = sqrt(N) * FastMath.log((1. + r) / (1. - r));
                double p = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(f)));
                if (p > this.alpha) count++;
            }
        }

        return count >= 1;
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
        if (this.testType == BpcTestType.TETRAD_DELTA) {
            Tetrad t1 = new Tetrad(this.variables.get(x), this.variables.get(y), this.variables.get(z), this.variables.get(w));
            Tetrad t2 = new Tetrad(this.variables.get(x), this.variables.get(y), this.variables.get(w), this.variables.get(z));

            return this.test.getPValue(t1, t2) > this.alpha;
        } else if (this.testType == BpcTestType.TETRAD_WISHART) {
            return this.test2.tetradPValue(x, y, z, w) > this.alpha && this.test2.tetradPValue(x, y, w, z) > this.alpha;
        }

        throw new IllegalArgumentException("Only the delta and wishart tests are being used: " + this.testType);
    }

    /**
     * Converts search graph nodes to a Graph object.
     *
     * @param clusters The set of sets of Node objects representing the clusters.
     * @return A Graph object representing the search graph nodes.
     */
    private Graph convertSearchGraphNodes(Set<Set<Node>> clusters) {
        Graph graph = new EdgeListGraph();

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
    private Graph convertToGraph(Set<List<Integer>> allClusters) {
        Set<Set<Node>> _clustering = new HashSet<>();

        for (List<Integer> cluster : allClusters) {
            Set<Node> nodes = new HashSet<>();

            for (int i : cluster) {
                nodes.add(this.variables.get(i));
            }

            _clustering.add(nodes);
        }

        return convertSearchGraphNodes(_clustering);
    }

    /**
     * Returns the union of all integers in the given list of clusters.
     *
     * @param pureClusters The set of clusters, where each cluster is represented as a list of integers.
     * @return A set containing the union of all integers in the clusters.
     */
    private Set<Integer> unionPure(Set<List<Integer>> pureClusters) {
        Set<Integer> unionPure = new HashSet<>();

        for (List<Integer> cluster : pureClusters) {
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
            TetradLogger.getInstance().forceLogMessage(s);
        }
    }

    /**
     * Gives the options to be used in FOFC to sort through the various possibilities for forming clusters to find the
     * best options. SAG (Seed and Grow) looks for good seed clusters and then grows them by adding one variable at a
     * time. GAP (Grow and Pick) grows out all the cluster initially and then just picks from among these. SAG is
     * generally faster; GAP is generally slower but more accurate.
     */
    public enum Algorithm {

        /**
         * The SAG algorithm.
         */
        SAG,

        /**
         * The GAP algorithm.
         */
        GAP
    }
}




