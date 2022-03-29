///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.*;

import java.util.*;

import static java.lang.Math.abs;
import static java.lang.Math.sqrt;


/**
 * Implements FindOneFactorCluster by Erich Kummerfeld (adaptation of a two factor
 * sextet algorithm to a one factor IntSextad algorithm).
 *
 * @author Joseph Ramsey
 */
public class FindTwoFactorClusters {

    public Algorithm getAlgorithm() {
        return this.algorithm;
    }

    public void setAlgorithm(final Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    public enum Algorithm {SAG, GAP}

    private final CorrelationMatrix corr;
    // The list of all variables.
    private final List<Node> variables;

    // The significance level.
    private final double alpha;

    // The Delta test. Testing two sextads simultaneously.
    private final DeltaSextadTest test;

    // The data.
    private final transient DataModel dataModel;

    private List<List<Node>> clusters;

    private boolean verbose;
    private Algorithm algorithm = Algorithm.GAP;

    //========================================PUBLIC METHODS====================================//

    public FindTwoFactorClusters(ICovarianceMatrix cov, final Algorithm algorithm, final double alpha) {
        cov = new CovarianceMatrix(cov);
        this.variables = cov.getVariables();
        this.alpha = alpha;
        this.test = new DeltaSextadTest(cov);
        this.dataModel = cov;
        this.algorithm = algorithm;

        this.corr = new CorrelationMatrix(cov);


    }

    public FindTwoFactorClusters(final DataSet dataSet, final Algorithm algorithm, final double alpha) {
        this.variables = dataSet.getVariables();
        this.alpha = alpha;
        this.test = new DeltaSextadTest(dataSet);
        this.dataModel = dataSet;
        this.algorithm = algorithm;

        this.corr = new CorrelationMatrix(dataSet);
    }

    // renjiey
    private int findFrequentestIndex(final Integer[] outliers) {
        final Map<Integer, Integer> map = new HashMap<>();

        for (final Integer outlier : outliers) {
            if (map.containsKey(outlier)) {
                map.put(outlier, map.get(outlier) + 1);
            } else {
                map.put(outlier, 1);
            }
        }

        Set<Map.Entry<Integer, Integer>> set = map.entrySet();
        Iterator<Map.Entry<Integer, Integer>> it = set.iterator();
        int nums = 0;// how many times variable occur
        int key = 0;// the number occur the most times

        while (it.hasNext()) {
            final Map.Entry<Integer, Integer> entry = it.next();
            if (entry.getValue() > nums) {
                nums = entry.getValue();
                key = entry.getKey();
            }
        }

        return (key);
    }

    // This is the main function. It remove variables in the data such that the remaining correlation matrix
    // does not contain extreme value
    // Inputs: correlation matrix, upper and lower bound for unacceptable correlations
    // Output: and dynamic array of removed variables
    // renjiey
    private ArrayList<Integer> removeVariables(final Matrix correlationMatrix, final double lowerBound, final double upperBound,
                                               final double percentBound) {
        Integer[] outlier = new Integer[correlationMatrix.rows() * (correlationMatrix.rows() - 1)];
        int count = 0;
        for (int i = 2; i < (correlationMatrix.rows() + 1); i++) {
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
        final ArrayList<Integer> removedVariables = new ArrayList<>();

        // Added the percent bound jdramsey
        while (outlier.length > 1 && removedVariables.size() < percentBound * correlationMatrix.rows()) {
            //find out the variable that occurs most frequently in outlier
            final int worstVariable = findFrequentestIndex(outlier);
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

        log(removedVariables.size() + " variables removed: " + variablesForIndices(removedVariables), true);

        return (removedVariables);
    }

    // renjiey
    private Integer[] removeZeroIndex(final Integer[] outlier) {
        final List<Integer> list = new ArrayList<>();
        for (int i = 0; i < outlier.length; i++) {
            list.add(outlier[i]);
        }
        for (final Integer element : outlier) {
            if (element < 1) {
                list.remove(element);
            }
        }
        return list.toArray(new Integer[1]);
    }


    public Graph search() {
        final Set<List<Integer>> allClusters;

        if (this.algorithm == Algorithm.SAG) {
            allClusters = estimateClustersSAG();
        } else if (this.algorithm == Algorithm.GAP) {
            allClusters = estimateClustersGAP();
        } else {
            throw new IllegalStateException("Expected SAG or GAP: " + this.algorithm);
        }
        this.clusters = variablesForIndices(allClusters);
        return convertToGraph(allClusters);
    }

    //========================================PRIVATE METHODS====================================//

    // This is the main algorithm.
    private Set<List<Integer>> estimateClustersGAP() {
        final List<Integer> _variables = allVariables();

        final Set<List<Integer>> pentads = findPurepentads(_variables);
        final Set<List<Integer>> combined = combinePurePentads(pentads, _variables);

        final Set<List<Integer>> _combined = new HashSet<>();

        for (final List<Integer> c : combined) {
            final List<Integer> a = new ArrayList<>(c);
            Collections.sort(a);
            _combined.add(a);
        }

        return _combined;

    }

    private List<Integer> allVariables() {
        final List<Integer> _variables = new ArrayList<>();
        for (int i = 0; i < this.variables.size(); i++) _variables.add(i);
        return _variables;
    }

    private Set<List<Integer>> estimateClustersSAG() {
        final List<Integer> _variables = allVariables();

        final Set<List<Integer>> pureClusters = findPureClusters(_variables);
        final Set<List<Integer>> mixedClusters = findMixedClusters(pureClusters, _variables, unionPure(pureClusters));
        final Set<List<Integer>> allClusters = new HashSet<>(pureClusters);
        allClusters.addAll(mixedClusters);
        return allClusters;

    }

    private Set<List<Integer>> findPurepentads(final List<Integer> variables) {
        if (variables.size() < 6) {
            return new HashSet<>();
        }

        log("Finding pure pentads.", true);

        final ChoiceGenerator gen = new ChoiceGenerator(variables.size(), 5);
        int[] choice;
        final Set<List<Integer>> purePentads = new HashSet<>();
        CHOICE:
        while ((choice = gen.next()) != null) {
            final int n1 = variables.get(choice[0]);
            final int n2 = variables.get(choice[1]);
            final int n3 = variables.get(choice[2]);
            final int n4 = variables.get(choice[3]);
            final int n5 = variables.get(choice[4]);

            final List<Integer> pentad = pentad(n1, n2, n3, n4, n5);

            if (zeroCorr(pentad, 4)) continue;

            for (final int o : variables) {
                if (pentad.contains(o)) {
                    continue;
                }

                final List<Integer> sextet = sextet(n1, n2, n3, n4, n5, o);

                Collections.sort(sextet);

                final boolean vanishes = vanishes(sextet);

                if (!vanishes) {
                    continue CHOICE;
                }
            }

            final List<Integer> _cluster = new ArrayList<>(pentad);

            if (this.verbose) {
                System.out.println(variablesForIndices(pentad));
                log("++" + variablesForIndices(pentad), false);
            }

            purePentads.add(_cluster);
        }

        return purePentads;
    }

    private Set<List<Integer>> combinePurePentads(final Set<List<Integer>> purePentads, final List<Integer> _variables) {
        log("Growing pure pentads.", true);
        final Set<List<Integer>> grown = new HashSet<>();

        // Lax grow phase with speedup.
        if (false) {
            final List<Integer> t = new ArrayList<>();
            int count = 0;
            final int total = purePentads.size();

            do {
                if (!purePentads.iterator().hasNext()) {
                    break;
                }

                final List<Integer> cluster = purePentads.iterator().next();
                final List<Integer> _cluster = new ArrayList<>(cluster);

                for (final int o : _variables) {
                    if (_cluster.contains(o)) continue;

                    final List<Integer> _cluster2 = new ArrayList<>(_cluster);
                    int rejected = 0;
                    int accepted = 0;

                    final ChoiceGenerator gen = new ChoiceGenerator(_cluster2.size(), 4);
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        t.clear();
                        t.add(_cluster2.get(choice[0]));
                        t.add(_cluster2.get(choice[1]));
                        t.add(_cluster2.get(choice[2]));
                        t.add(_cluster2.get(choice[3]));
                        t.add(o);

                        if (!purePentads.contains(t)) {
                            rejected++;
                        } else {
                            accepted++;
                        }
                    }

                    if (rejected > accepted) {
                        continue;
                    }

                    _cluster.add(o);

//                    if (!(avgSumLnP(new ArrayList<Integer>(_cluster)) > -10)) {
//                        _cluster.remove(o);
//                    }
                }

                // This takes out all pure clusters that are subsets of _cluster.
                final ChoiceGenerator gen2 = new ChoiceGenerator(_cluster.size(), 3);
                int[] choice2;
                final List<Integer> _cluster3 = new ArrayList<>(_cluster);

                while ((choice2 = gen2.next()) != null) {
                    final int n1 = _cluster3.get(choice2[0]);
                    final int n2 = _cluster3.get(choice2[1]);
                    final int n3 = _cluster3.get(choice2[2]);
                    final int n4 = _cluster3.get(choice2[3]);
                    final int n5 = _cluster3.get(choice2[4]);

                    t.clear();
                    t.add(n1);
                    t.add(n2);
                    t.add(n3);
                    t.add(n4);
                    t.add(n5);

                    purePentads.remove(t);
                }

                if (this.verbose) {
                    System.out.println("Grown " + (++count) + " of " + total + ": " + variablesForIndices(new ArrayList<>(_cluster)));
                }
                grown.add(_cluster);
            } while (!purePentads.isEmpty());
        }

        // Lax grow phase without speedup.
        if (false) {
            int count = 0;
            final int total = purePentads.size();

            // Optimized lax version of grow phase.
            for (final List<Integer> cluster : new HashSet<>(purePentads)) {
                final List<Integer> _cluster = new ArrayList<>(cluster);

                for (final int o : _variables) {
                    if (_cluster.contains(o)) continue;

                    final List<Integer> _cluster2 = new ArrayList<>(_cluster);
                    int rejected = 0;
                    int accepted = 0;

                    final ChoiceGenerator gen = new ChoiceGenerator(_cluster2.size(), 6);
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        final int n1 = _cluster2.get(choice[0]);
                        final int n2 = _cluster2.get(choice[1]);
                        final int n3 = _cluster2.get(choice[2]);
                        final int n4 = _cluster2.get(choice[3]);

                        final List<Integer> pentad = pentad(n1, n2, n3, n4, o);

                        final List<Integer> t = new ArrayList<>(pentad);

                        Collections.sort(t);

                        if (!purePentads.contains(t)) {
                            rejected++;
                        } else {
                            accepted++;
                        }
                    }

                    if (rejected > accepted) {
                        continue;
                    }

                    _cluster.add(o);
                }

                for (final List<Integer> c : new HashSet<>(purePentads)) {
                    if (_cluster.containsAll(c)) {
                        purePentads.remove(c);
                    }
                }

                if (this.verbose) {
                    System.out.println("Grown " + (++count) + " of " + total + ": " + _cluster);
                }

                grown.add(_cluster);
            }
        }

        // Strict grow phase.
        if (true) {
            final List<Integer> t = new ArrayList<>();
            int count = 0;
            final int total = purePentads.size();

            do {
                if (!purePentads.iterator().hasNext()) {
                    break;
                }

                final List<Integer> cluster = purePentads.iterator().next();
                final List<Integer> _cluster = new ArrayList<>(cluster);

                VARIABLES:
                for (final int o : _variables) {
                    if (_cluster.contains(o)) continue;

                    final List<Integer> _cluster2 = new ArrayList<>(_cluster);

                    final ChoiceGenerator gen = new ChoiceGenerator(_cluster2.size(), 4);
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        final int n1 = _cluster2.get(choice[0]);
                        final int n2 = _cluster2.get(choice[1]);
                        final int n3 = _cluster2.get(choice[2]);
                        final int n4 = _cluster2.get(choice[3]);

                        t.clear();
                        t.add(n1);
                        t.add(n2);
                        t.add(n3);
                        t.add(n4);
                        t.add(o);

                        Collections.sort(t);

                        if (!purePentads.contains(t)) {
                            continue VARIABLES;
                        }
                    }

                    _cluster.add(o);
                }

//                for (Set<Integer> c : new HashSet<>(purePentads)) {
////                    for (Integer d : c) {
////                        if (_cluster.contains(d)) {
////                            purePentads.remove(c);
////                        }
////                    }
//
//                    if (_cluster.containsAll(c)) {
//                        purePentads.remove(c);
//                    }
//                }

                // This takes out all pure clusters that are subsets of _cluster.
                final ChoiceGenerator gen2 = new ChoiceGenerator(_cluster.size(), 5);
                int[] choice2;
                final List<Integer> _cluster3 = new ArrayList<>(_cluster);

                while ((choice2 = gen2.next()) != null) {
                    final int n1 = _cluster3.get(choice2[0]);
                    final int n2 = _cluster3.get(choice2[1]);
                    final int n3 = _cluster3.get(choice2[2]);
                    final int n4 = _cluster3.get(choice2[3]);
                    final int n5 = _cluster3.get(choice2[4]);

                    t.clear();
                    t.add(n1);
                    t.add(n2);
                    t.add(n3);
                    t.add(n4);
                    t.add(n5);

                    Collections.sort(t);

                    purePentads.remove(t);
                }

                if (this.verbose) {
                    System.out.println("Grown " + (++count) + " of " + total + ": " + _cluster);
                }

                grown.add(_cluster);
            } while (!purePentads.isEmpty());
        }

        // Optimized pick phase.
        log("Choosing among grown clusters.", true);

        for (final List<Integer> l : grown) {
            final ArrayList<Integer> _l = new ArrayList<>(l);
            Collections.sort(_l);
            if (this.verbose) {
                log("Grown: " + variablesForIndices(_l), false);
            }
        }

        final Set<List<Integer>> out = new HashSet<>();

        final List<List<Integer>> list = new ArrayList<>(grown);

        Collections.sort(list, new Comparator<List<Integer>>() {
            @Override
            public int compare(final List<Integer> o1, final List<Integer> o2) {
                return o2.size() - o1.size();
            }
        });

        final List<Integer> all = new ArrayList<>();

        CLUSTER:
        for (final List<Integer> cluster : list) {
            for (final Integer i : cluster) {
                if (all.contains(i)) continue CLUSTER;
            }

            out.add(cluster);
            all.addAll(cluster);
        }

        final boolean significanceCalculated = false;
        if (significanceCalculated) {
            for (final List<Integer> _out : out) {
                try {
                    final double p = significance(new ArrayList<>(_out));
                    log("OUT: " + variablesForIndices(new ArrayList<>(_out)) + " p = " + p, true);
                } catch (final Exception e) {
                    log("OUT: " + variablesForIndices(new ArrayList<>(_out)) + " p = EXCEPTION", true);
                }
            }
        } else {
            for (final List<Integer> _out : out) {
                log("OUT: " + variablesForIndices(new ArrayList<>(_out)), true);
            }
        }

//        C:
//        for (List<Integer> cluster : new HashSet<>(out)) {
//            if (cluster.size() >= 6) {
//                ChoiceGenerator gen = new ChoiceGenerator(cluster.size(), 6);
//                int[] choice;
//
//                while ((choice = gen.next()) != null) {
//                    int n1 = cluster.get(choice[0]);
//                    int n2 = cluster.get(choice[1]);
//                    int n3 = cluster.get(choice[2]);
//                    int n4 = cluster.get(choice[3]);
//                    int n5 = cluster.get(choice[4]);
//                    int n6 = cluster.get(choice[5]);
//
//                    List<Integer> _cluster = sextet(n1, n2, n3, n4, n5, n6);
//
//                    // Note that purity needs to be assessed with respect to all of the variables in order to
//                    // remove all latent-measure impurities between pairs of latents.
//                    if (!pure(_cluster)) {
//                        out.remove(cluster);
//                        continue C;
//                    }
//                }
//            }
//        }

        return out;
    }

    // Finds clusters of size 6 or higher for the IntSextad first algorithm.
    private Set<List<Integer>> findPureClusters(final List<Integer> _variables) {
        final Set<List<Integer>> clusters = new HashSet<>();

        for (int k = 6; k >= 6; k--) {
            VARIABLES:
            while (!_variables.isEmpty()) {
                if (this.verbose) {
                    System.out.println(_variables);
                }
                if (_variables.size() < 6) break;

                final ChoiceGenerator gen = new ChoiceGenerator(_variables.size(), 6);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    final int n1 = _variables.get(choice[0]);
                    final int n2 = _variables.get(choice[1]);
                    final int n3 = _variables.get(choice[2]);
                    final int n4 = _variables.get(choice[3]);
                    final int n5 = _variables.get(choice[4]);
                    final int n6 = _variables.get(choice[5]);

                    final List<Integer> cluster = sextet(n1, n2, n3, n4, n5, n6);

                    // Note that purity needs to be assessed with respect to all of the variables in order to
                    // remove all latent-measure impurities between pairs of latents.
                    if (pure(cluster)) {
                        if (this.verbose) {
                            log("Found a pure: " + variablesForIndices(cluster), false);
                        }

                        addOtherVariables(_variables, cluster);

                        if (cluster.size() < k) continue;

                        if (this.verbose) {
                            log("Cluster found: " + variablesForIndices(cluster), true);
                            System.out.println("Indices for cluster = " + cluster);
                        }

                        clusters.add(cluster);
                        _variables.removeAll(cluster);

                        continue VARIABLES;
                    }
                }

                break;
            }

//            C:
//            for (List<Integer> cluster : new HashSet<>(clusters)) {
//                if (cluster.size() >= 6) {
//                    ChoiceGenerator gen = new ChoiceGenerator(cluster.size(), 6);
//                    int[] choice;
//
//                    while ((choice = gen.next()) != null) {
//                        int n1 = cluster.get(choice[0]);
//                        int n2 = cluster.get(choice[1]);
//                        int n3 = cluster.get(choice[2]);
//                        int n4 = cluster.get(choice[3]);
//                        int n5 = cluster.get(choice[4]);
//                        int n6 = cluster.get(choice[5]);
//
//                        List<Integer> _cluster = sextet(n1, n2, n3, n4, n5, n6);
//
//                        // Note that purity needs to be assessed with respect to all of the variables in order to
//                        // remove all latent-measure impurities between pairs of latents.
//                        if (!pure(_cluster)) {
//                            clusters.remove(cluster);
//                            continue C;
//                        }
//                    }
//                }
//            }
        }

        return clusters;
    }

    private void addOtherVariables(final List<Integer> _variables, final List<Integer> cluster) {

        O:
        for (final int o : _variables) {
            if (cluster.contains(o)) continue;
            final List<Integer> _cluster = new ArrayList<>(cluster);

            final ChoiceGenerator gen2 = new ChoiceGenerator(_cluster.size(), 6);
            int[] choice;

            while ((choice = gen2.next()) != null) {
                final int t1 = _cluster.get(choice[0]);
                final int t2 = _cluster.get(choice[1]);
                final int t3 = _cluster.get(choice[2]);
                final int t4 = _cluster.get(choice[3]);
                final int t5 = _cluster.get(choice[4]);

                final List<Integer> sextad = pentad(t1, t2, t3, t4, t5);
                sextad.add(o);

                if (!pure(sextad)) {
                    continue O;
                }
            }

            log("Extending by " + this.variables.get(o), false);
            cluster.add(o);
        }
    }

    //  Finds clusters of size 5 for the sextet first algorithm.
    private Set<List<Integer>> findMixedClusters(final Set<List<Integer>> clusters, final List<Integer> remaining, final Set<Integer> unionPure) {
        final Set<List<Integer>> pentads = new HashSet<>();
        final Set<List<Integer>> _clusters = new HashSet<>(clusters);

        if (unionPure.isEmpty()) {
            return new HashSet<>();
        }

        REMAINING:
        while (true) {
            if (remaining.size() < 5) break;

            if (this.verbose) {
                log("UnionPure = " + variablesForIndices(new ArrayList<>(unionPure)), false);
            }

            final ChoiceGenerator gen = new ChoiceGenerator(remaining.size(), 5);
            int[] choice;

            while ((choice = gen.next()) != null) {
                final int t2 = remaining.get(choice[0]);
                final int t3 = remaining.get(choice[1]);
                final int t4 = remaining.get(choice[2]);
                final int t5 = remaining.get(choice[3]);
                final int t6 = remaining.get(choice[4]);

                final List<Integer> cluster = new ArrayList<>();
                cluster.add(t2);
                cluster.add(t3);
                cluster.add(t4);
                cluster.add(t5);
                cluster.add(t6);

                if (zeroCorr(cluster, 4)) {
                    continue;
                }

                // Check all x as a cross check; really only one should be necessary.
                boolean allvanish = true;
                boolean someVanish = false;

                for (final int t1 : allVariables()) {
                    if (cluster.contains(t1)) continue;

                    final List<Integer> _cluster = new ArrayList<>(cluster);
                    _cluster.add(t1);


                    if (vanishes(_cluster)) {
                        someVanish = true;
                    } else {
                        allvanish = false;
                        break;
                    }
                }

                if (someVanish && allvanish) {
                    pentads.add(cluster);
                    _clusters.add(cluster);
                    unionPure.addAll(cluster);
                    remaining.removeAll(cluster);

                    if (this.verbose) {
                        log("3-cluster found: " + variablesForIndices(cluster), false);
                    }

                    continue REMAINING;
                }
            }

            break;
        }

        return pentads;
    }

    private double significance(final List<Integer> cluster) {
        final double chisq = getClusterChiSquare(cluster);

        // From "Algebraic factor analysis: sextads, pentads and beyond" Drton et al.
        final int n = cluster.size();
        final int dof = dofHarman(n);
        final double q = ProbUtils.chisqCdf(chisq, dof);
        return 1.0 - q;
    }

    private int dofDrton(final int n) {
        int dof = ((n - 2) * (n - 3)) / 2 - 2;
        if (dof < 0) dof = 0;
        return dof;
    }

    private int dofHarman(final int n) {
        int dof = n * (n - 5) / 2 + 1;
        if (dof < 0) dof = 0;
        return dof;
    }

    private List<Node> variablesForIndices(final List<Integer> cluster) {
        final List<Node> _cluster = new ArrayList<>();

        for (final int c : cluster) {
            _cluster.add(this.variables.get(c));
        }

        return _cluster;
    }

    private List<List<Node>> variablesForIndices(final Set<List<Integer>> clusters) {
        final List<List<Node>> variables = new ArrayList<>();

        for (final List<Integer> cluster : clusters) {
            variables.add(variablesForIndices(cluster));
        }

        return variables;
    }

    private boolean pure(final List<Integer> sextet) {
        if (zeroCorr(sextet, 5)) {
            return false;
        }

        if (vanishes(sextet)) {
            for (final int o : allVariables()) {
                if (sextet.contains(o)) continue;

                for (int i = 0; i < sextet.size(); i++) {
                    final List<Integer> _sextet = new ArrayList<>(sextet);
                    _sextet.remove(sextet.get(i));
                    _sextet.add(i, o);

                    if (!(vanishes(_sextet))) {
                        return false;
                    }
                }
            }

            System.out.println("PURE: " + variablesForIndices(sextet));

            return true;
        }

        return false;
    }

    private double getClusterChiSquare(final List<Integer> cluster) {
        final SemIm im = estimateClusterModel(cluster);
        return im.getChiSquare();
    }

    private SemIm estimateClusterModel(final List<Integer> sextet) {
        final Graph g = new EdgeListGraph();
        final Node l1 = new GraphNode("L1");
        l1.setNodeType(NodeType.LATENT);
        final Node l2 = new GraphNode("L2");
        l2.setNodeType(NodeType.LATENT);
        g.addNode(l1);
        g.addNode(l2);

        for (final Integer aQuartet : sextet) {
            final Node n = this.variables.get(aQuartet);
            g.addNode(n);
            g.addDirectedEdge(l1, n);
            g.addDirectedEdge(l2, n);
        }

        final SemPm pm = new SemPm(g);

        final SemEstimator est;

        if (this.dataModel instanceof DataSet) {
            est = new SemEstimator((DataSet) this.dataModel, pm, new SemOptimizerEm());
        } else {
            est = new SemEstimator((CovarianceMatrix) this.dataModel, pm, new SemOptimizerEm());
        }

        return est.estimate();
    }

    private SemIm estimateModel(final List<List<Integer>> clusters) {
        final Graph g = new EdgeListGraph();

        final List<Node> upperLatents = new ArrayList<>();
        final List<Node> lowerLatents = new ArrayList<>();

        for (int i = 0; i < clusters.size(); i++) {
            final List<Integer> cluster = clusters.get(i);
            final Node l1 = new GraphNode("L1." + (i + 1));
            l1.setNodeType(NodeType.LATENT);

            final Node l2 = new GraphNode("L2." + (i + 1));
            l2.setNodeType(NodeType.LATENT);

            upperLatents.add(l1);
            lowerLatents.add(l2);

            g.addNode(l1);
            g.addNode(l2);

            for (final Integer aCluster : cluster) {
                final Node n = this.variables.get(aCluster);
                g.addNode(n);
                g.addDirectedEdge(l1, n);
                g.addDirectedEdge(l2, n);
            }
        }

        for (int i = 0; i < upperLatents.size(); i++) {
            for (int j = i + 1; j < upperLatents.size(); j++) {
                g.addDirectedEdge(upperLatents.get(i), upperLatents.get(j));
                g.addDirectedEdge(lowerLatents.get(i), lowerLatents.get(j));
            }
        }

        for (int i = 0; i < upperLatents.size(); i++) {
            for (int j = 0; j < lowerLatents.size(); j++) {
                if (i == j) continue;
                g.addDirectedEdge(upperLatents.get(i), lowerLatents.get(j));
            }
        }

        final SemPm pm = new SemPm(g);

        for (final Node node : upperLatents) {
            final Parameter p = pm.getParameter(node, node);
            p.setFixed(true);
            p.setStartingValue(1.0);
        }

        for (final Node node : lowerLatents) {
            final Parameter p = pm.getParameter(node, node);
            p.setFixed(true);
            p.setStartingValue(1.0);
        }

        final SemEstimator est;

        if (this.dataModel instanceof DataSet) {
            est = new SemEstimator((DataSet) this.dataModel, pm, new SemOptimizerEm());
        } else {
            est = new SemEstimator((CovarianceMatrix) this.dataModel, pm, new SemOptimizerEm());
        }

        return est.estimate();
    }

    private List<Integer> sextet(final int n1, final int n2, final int n3, final int n4, final int n5, final int n6) {
        final List<Integer> sextet = new ArrayList<>();
        sextet.add(n1);
        sextet.add(n2);
        sextet.add(n3);
        sextet.add(n4);
        sextet.add(n5);
        sextet.add(n6);

        if (new HashSet<>(sextet).size() < 6)
            throw new IllegalArgumentException("sextet elements must be unique: <" + n1 + ", " + n2 + ", " + n3
                    + ", " + n4 + ", " + n5 + ", " + n6 + ">");

        return sextet;
    }

    private List<Integer> pentad(final int n1, final int n2, final int n3, final int n4, final int n5) {
        final List<Integer> pentad = new ArrayList<>();
        pentad.add(n1);
        pentad.add(n2);
        pentad.add(n3);
        pentad.add(n4);
        pentad.add(n5);

        if (new HashSet<>(pentad).size() < 5)
            throw new IllegalArgumentException("pentad elements must be unique: <" + n1 + ", " + n2 + ", " + n3
                    + ", " + n4 + ", " + n5 + ">");

        return pentad;
    }

    private boolean vanishes(final List<Integer> sextet) {

        final PermutationGenerator gen = new PermutationGenerator(6);
        int[] perm;

//        while ((perm = gen.next()) != null) {
//            int n1 = sextet.get(perm[0]);
//            int n2 = sextet.get(perm[1]);
//            int n3 = sextet.get(perm[2]);
//            int n4 = sextet.get(perm[3]);
//            int n5 = sextet.get(perm[4]);
//            int n6 = sextet.get(perm[5);
//
//            if (!vanishes(n1, n2, n3, n4, n5, n6)) return false;
//        }
//
//        return true;

        final int n1 = sextet.get(0);
        final int n2 = sextet.get(1);
        final int n3 = sextet.get(2);
        final int n4 = sextet.get(3);
        final int n5 = sextet.get(4);
        final int n6 = sextet.get(5);

        return vanishes(n1, n2, n3, n4, n5, n6)
                && vanishes(n3, n2, n1, n6, n5, n4)
                && vanishes(n4, n5, n6, n1, n2, n3)
                && vanishes(n6, n5, n4, n3, n2, n1);
    }

    private boolean zeroCorr(final List<Integer> cluster, final int n) {
        int count = 0;

        for (int i = 0; i < cluster.size(); i++) {
            for (int j = i + 1; j < cluster.size(); j++) {
                final double r = this.corr.getValue(cluster.get(i), cluster.get(j));
                final int N = this.corr.getSampleSize();
                final double f = sqrt(N) * Math.log((1. + r) / (1. - r));
                final double p = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(f)));
                if (p > this.alpha) count++;
            }
        }

        return count >= n;
    }

    /**
     * The clusters output by the algorithm from the last call to search().
     */
    public List<List<Node>> getClusters() {
        return this.clusters;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    private boolean vanishes(final int n1, final int n2, final int n3, final int n4, final int n5, final int n6) {
        final IntSextad t1 = new IntSextad(n1, n2, n3, n4, n5, n6);
        final IntSextad t2 = new IntSextad(n1, n5, n6, n2, n3, n4);
        final IntSextad t3 = new IntSextad(n1, n4, n6, n2, n3, n5);
        final IntSextad t4 = new IntSextad(n1, n4, n5, n2, n3, n6);
        final IntSextad t5 = new IntSextad(n1, n3, n4, n2, n5, n6);
        final IntSextad t6 = new IntSextad(n1, n3, n5, n2, n4, n6);
        final IntSextad t7 = new IntSextad(n1, n3, n6, n2, n4, n5);
        final IntSextad t8 = new IntSextad(n1, n2, n4, n3, n5, n6);
        final IntSextad t9 = new IntSextad(n1, n2, n5, n3, n4, n6);
        final IntSextad t10 = new IntSextad(n1, n2, n6, n3, n4, n5);

//            IntSextad[] independents = {t2, t5, t10, t3, t6};

        final List<IntSextad[]> independents = new ArrayList<>();
        independents.add(new IntSextad[]{t1, t2, t3, t5, t6});
//        independents.add(new IntSextad[]{t1, t2, t3, t9, t10});
//        independents.add(new IntSextad[]{t6, t7, t8, t9, t10});
//        independents.add(new IntSextad[]{t1, t2, t4, t5, t9});
//        independents.add(new IntSextad[]{t1, t3, t4, t6, t10});

//        independents.add(new IntSextad[]{t1, t2, t3, t4, t5, t6, t7, t8, t9, t10});

        // The four sextads implied by equation 5.17 in Harmann.
//            independents.add(new IntSextad[]{t3, t7, t8, t9});

        for (final IntSextad[] sextads : independents) {
            final double p = this.test.getPValue(sextads);

            if (Double.isNaN(p)) {
                return false;
            }

            if (p < this.alpha) return false;
        }

//        IntSextad[] sextads = new IntSextad[]{t1, t2, t3, t4, t5, t6, t7, t8, t9, t10};
//
//        for (IntSextad sextad : sextads) {
//            if (test.getScore(sextad) < alpha) return false;
//        }

        return true;
    }

    private Graph convertSearchGraphNodes(final Set<Set<Node>> clusters) {
        final Graph graph = new EdgeListGraph(this.variables);

        final List<Node> latents = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++) {
            final Node latent = new GraphNode(ClusterUtils.LATENT_PREFIX + (i + 1));
            latent.setNodeType(NodeType.LATENT);
            latents.add(latent);
            graph.addNode(latent);
        }

        final List<Set<Node>> _clusters = new ArrayList<>(clusters);

        for (int i = 0; i < latents.size(); i++) {
            for (final Node node : _clusters.get(i)) {
                if (!graph.containsNode(node)) graph.addNode(node);
                graph.addDirectedEdge(latents.get(i), node);
            }
        }

        return graph;
    }

    private Graph convertToGraph(final Set<List<Integer>> allClusters) {
        final Set<Set<Node>> _clustering = new HashSet<>();

        for (final List<Integer> cluster : allClusters) {
            final Set<Node> nodes = new HashSet<>();

            for (final int i : cluster) {
                nodes.add(this.variables.get(i));
            }

            _clustering.add(nodes);
        }

        return convertSearchGraphNodes(_clustering);
    }

    private Set<Integer> unionPure(final Set<List<Integer>> pureClusters) {
        final Set<Integer> unionPure = new HashSet<>();

        for (final List<Integer> cluster : pureClusters) {
            unionPure.addAll(cluster);
        }

        return unionPure;
    }

    private void log(final String s, final boolean toLog) {
        if (toLog) {
            TetradLogger.getInstance().log("info", s);
//            System.out.println(s);
        }
    }
}




