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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.*;


/**
 * Implements FindOneFactorCluster by Erich Kummerfeld (adaptation of a two factor
 * sextet algorithm to a one factor tetrad algorithm).
 *
 * @author Joseph Ramsey
 */
public class FindTwoFactorClusters {

    // The list of all variables.
    private List<Node> variables;

    // The significance level.
    private double alpha;

    // Pentads first or Sextads first, two algorithms. Pendads first (GAP) is TestType.Tetrad_DELTA,
    // Tetrads first is TestType.TETRAD_WISHART. (Sorry, I'll fix this.)
    private TestType testType = TestType.GAP;

    // The Bollen test. Testing two tetrads simultaneously.
    private IDeltaSextadTest test;

    // independence test.
    private IndependenceTest indTest;

    // independence test alpha.
    private double indTestAlpha = 0.1;

    // The data.
    private DataModel dataModel;

    private List<List<Node>> clusters;

    private int depth = 0;
    private boolean verbose = false;
    private boolean significanceCalculated = false;

    //========================================PUBLIC METHODS====================================//

    public FindTwoFactorClusters(ICovarianceMatrix cov, TestType testType, double alpha) {
        cov = new CovarianceMatrix(cov);
//        this.variables = cov.getVariables();
//
//        List<Integer> removedVars = removeVariables(cov.getMatrix(), 0.1, 0.9, .1);
//        List<Node> allVars = new ArrayList<Node>(cov.getVariables());
//        List<Node> _removedVars = new ArrayList<Node>();
//        for (int i = 0; i < allVars.size(); i++) {
//            if (removedVars.contains(i)) _removedVars.add(allVars.get(i));
//        }
//
//        allVars.removeAll(_removedVars);
//
//        List<String> names = new ArrayList<String>();
//        for (Node node : allVars) names.add(node.getName());
//
//        cov = cov.getSubmatrix(names);

        this.variables = cov.getVariables();
        this.indTest = new IndTestFisherZ(cov, indTestAlpha);
        this.alpha = alpha;
        this.testType = testType;
        this.test = new DeltaSextadTest(cov);
//        this.test = new DeltaSextadTest2(cov);
        this.dataModel = cov;


    }

    public FindTwoFactorClusters(DataSet dataSet, TestType testType, double alpha) {
//        CovarianceMatrix cov = new CovarianceMatrix(dataSet);
//        this.variables = cov.getVariables();
//
//        List<Integer> removedVars = removeVariables(cov.getMatrix(), 0.1, 0.9, .1);
//        List<Node> allVars = new ArrayList<Node>(cov.getVariables());
//        List<Node> _removedVars = new ArrayList<Node>();
//        for (int i = 0; i < allVars.size(); i++) {
//            if (removedVars.contains(i)) _removedVars.add(allVars.get(i));
//        }
//
//        allVars.removeAll(_removedVars);
//
//        dataSet = dataSet.subsetColumns(allVars);

        this.variables = dataSet.getVariables();
        this.indTest = new IndTestFisherZ(dataSet, indTestAlpha);
        this.alpha = alpha;
        this.testType = testType;
        this.test = new DeltaSextadTest(dataSet);
//        this.test = new DeltaSextadTest2(dataSet); // The old test.
        this.dataModel = dataSet;
    }

    // renjiey
    private int findFrequentestIndex(Integer outliers[]) {
        Map<Integer, Integer> map = new HashMap<Integer, Integer>();

        for (int i = 0; i < outliers.length; i++) {
            if (map.containsKey(outliers[i])) {
                map.put(outliers[i], map.get(outliers[i]) + 1);
            } else {
                map.put(outliers[i], 1);
            }
        }

        Set<Map.Entry<Integer, Integer>> set = map.entrySet();
        Iterator<Map.Entry<Integer, Integer>> it = set.iterator();
        int nums = 0;// how many times variable occur
        int key = 0;// the number occur the most times

        while (it.hasNext()) {
            Map.Entry<Integer, Integer> entry = it.next();
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
    private ArrayList<Integer> removeVariables(TetradMatrix correlationMatrix, double lowerBound, double upperBound,
                                               double percentBound) {
        Integer outlier[] = new Integer[correlationMatrix.rows() * (correlationMatrix.rows() - 1)];
        int count = 0;
        for (int i = 2; i < (correlationMatrix.rows() + 1); i++) {
            for (int j = 1; j < i; j++) {

                if ((Math.abs(correlationMatrix.get(i - 1, j - 1)) < lowerBound)
                        || (Math.abs(correlationMatrix.get(i - 1, j - 1)) > upperBound)) {
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
        ArrayList<Integer> removedVariables = new ArrayList<Integer>();

        // Added the percent bound jdramsey
        while (outlier.length > 1 && removedVariables.size() < percentBound * correlationMatrix.rows()) {
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

        log(removedVariables.size() + " variables removed: " + variablesForIndices(removedVariables), true);

        return (removedVariables);
    }

    // renjiey
    private Integer[] removeZeroIndex(Integer outlier[]) {
        List<Integer> list = new ArrayList<Integer>();
        for (int i = 0; i < outlier.length; i++) {
            list.add(outlier[i]);
        }
        for (Integer element : outlier) {
            if (element < 1) {
                list.remove(element);
            }
        }
        return list.toArray(new Integer[1]);
    }


    public Graph search() {
        Set<List<Integer>> allClusters;

        if (testType == TestType.SAG) {
            allClusters = estimateClustersSextadsFirst();
        } else {
            allClusters = estimateClustersPentadsFirst();
        }
        this.clusters = variablesForIndices2(allClusters);
        return convertToGraph(allClusters);
    }

    //========================================PRIVATE METHODS====================================//

    // This is the main algorithm.
    private Set<List<Integer>> estimateClustersPentadsFirst() {
//        List<Integer> _variables = new ArrayList<Integer>();
//        for (int i = 0; i < variables.size(); i++) _variables.add(i);
        List<Integer> _variables = allVariables();

        Set<Set<Integer>> fiveClusters = findPurePentads(_variables);
        Set<Set<Integer>> combined = combinePurePentads(fiveClusters, _variables);

        Set<List<Integer>> _combined = new HashSet<List<Integer>>();

        for (Set<Integer> c : combined) {
            List a = new ArrayList<Integer>(c);
            Collections.sort(a);
            _combined.add(a);
        }

        return _combined;

    }

    private List<Integer> allVariables() {
        List<Integer> _variables = new ArrayList<Integer>();
        for (int i = 0; i < variables.size(); i++) _variables.add(i);
        return _variables;
    }

    private Set<List<Integer>> estimateClustersSextadsFirst() {
        if (verbose) {
            log("Running PC adjacency search...", true);
        }

        Graph graph = new EdgeListGraph(variables);
        Fas fas = new Fas(graph, indTest);
        fas.setDepth(depth);
        graph = fas.search();
        if (verbose) {
            log("...done.", true);
        }

//        List<Integer> _variables = new ArrayList<Integer>();
//        for (int i = 0; i < variables.size(); i++) _variables.add(i);
        List<Integer> _variables = allVariables();

        Set<List<Integer>> pureClusters = findPureClusters(_variables, graph);
        Set<List<Integer>> mixedClusters = findMixedClusters(pureClusters, _variables, unionPure(pureClusters), graph);
        Set<List<Integer>> allClusters = new HashSet<List<Integer>>(pureClusters);
        allClusters.addAll(mixedClusters);
        return allClusters;

    }

    private Set<Set<Integer>> findPurePentads(List<Integer> allVariables) {
        if (allVariables.size() < 6) {
            return new HashSet<Set<Integer>>();
        }

        log("Finding pure pentads.", true);

        ChoiceGenerator gen = new ChoiceGenerator(allVariables.size(), 5);
        int[] choice;
        Set<Set<Integer>> purePentads = new HashSet<Set<Integer>>();
        CHOICE:
        while ((choice = gen.next()) != null) {
            int n1 = allVariables.get(choice[0]);
            int n2 = allVariables.get(choice[1]);
            int n3 = allVariables.get(choice[2]);
            int n4 = allVariables.get(choice[3]);
            int n5 = allVariables.get(choice[4]);

            List<Integer> pentad = pentad(n1, n2, n3, n4, n5);

            for (int o : allVariables) {
                if (pentad.contains(o)) {
                    continue;
                }

                List<Integer> sextet = sextet(n1, n2, n3, n4, n5, o);

                double p = sextadVanishingP(sextet);

                if (!(p > alpha)) {
                    continue CHOICE;
                }

//                if (!(avgSumLnP(sextet) > -20)) {
//                    continue CHOICE;
//                }
            }

            HashSet<Integer> _cluster = new HashSet<Integer>(pentad);

            if (verbose) {
                log("++" + variablesForIndices(pentad), false);
            }

            purePentads.add(_cluster);
        }

        return purePentads;
    }

    private Set<Set<Integer>> combinePurePentads(Set<Set<Integer>> purePentads, List<Integer> _variables) {
        log("Growing pure pentads.", true);
        Set<Set<Integer>> grown = new HashSet<Set<Integer>>();

        // Lax grow phase with speedup.
        if (true) {
            Set<Integer> t = new HashSet<Integer>();
            int count = 0;
            int total = purePentads.size();

            do {
                if (!purePentads.iterator().hasNext()) {
                    break;
                }

                Set<Integer> cluster = purePentads.iterator().next();
                Set<Integer> _cluster = new HashSet<Integer>(cluster);

                for (int o : _variables) {
                    if (_cluster.contains(o)) continue;

                    List<Integer> _cluster2 = new ArrayList<Integer>(_cluster);
                    int rejected = 0;
                    int accepted = 0;

                    ChoiceGenerator gen = new ChoiceGenerator(_cluster2.size(), 4);
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
                ChoiceGenerator gen2 = new ChoiceGenerator(_cluster.size(), 5);
                int[] choice2;
                List<Integer> _cluster3 = new ArrayList<Integer>(_cluster);

                while ((choice2 = gen2.next()) != null) {
                    int n1 = _cluster3.get(choice2[0]);
                    int n2 = _cluster3.get(choice2[1]);
                    int n3 = _cluster3.get(choice2[2]);
                    int n4 = _cluster3.get(choice2[3]);
                    int n5 = _cluster3.get(choice2[4]);

                    t.clear();
                    t.add(n1);
                    t.add(n2);
                    t.add(n3);
                    t.add(n4);
                    t.add(n5);

                    purePentads.remove(t);
                }

                if (verbose) {
                    System.out.println("Grown " + (++count) + " of " + total + ": " + variablesForIndices(new ArrayList<Integer>(_cluster)));
                }
                grown.add(_cluster);
            } while (!purePentads.isEmpty());
        }

        // Lax grow phase without speedup.
        if (false) {
            int count = 0;
            int total = purePentads.size();

            // Optimized lax version of grow phase.
            for (Set<Integer> cluster : new HashSet<Set<Integer>>(purePentads)) {
                Set<Integer> _cluster = new HashSet<Integer>(cluster);

                for (int o : _variables) {
                    if (_cluster.contains(o)) continue;

                    List<Integer> _cluster2 = new ArrayList<Integer>(_cluster);
                    int rejected = 0;
                    int accepted = 0;

                    ChoiceGenerator gen = new ChoiceGenerator(_cluster2.size(), 4);
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        int n1 = _cluster2.get(choice[0]);
                        int n2 = _cluster2.get(choice[1]);
                        int n3 = _cluster2.get(choice[2]);
                        int n4 = _cluster2.get(choice[3]);

                        List<Integer> pentad = pentad(n1, n2, n3, n4, o);

                        Set<Integer> t = new HashSet<Integer>(pentad);

                        if (!purePentads.contains(t)) {
                            rejected++;
                        } else {
                            accepted++;
                        }

//                        if (avgSumLnP(pentad) < -10) continue CLUSTER;
                    }

                    if (rejected > accepted) {
                        continue;
                    }

                    _cluster.add(o);
                }

                for (Set<Integer> c : new HashSet<Set<Integer>>(purePentads)) {
                    if (_cluster.containsAll(c)) {
                        purePentads.remove(c);
                    }
                }

                if (verbose) {
                    System.out.println("Grown " + (++count) + " of " + total + ": " + _cluster);
                }

                grown.add(_cluster);
            }
        }

        // Strict grow phase.
        if (false) {
            Set<Integer> t = new HashSet<Integer>();
            int count = 0;
            int total = purePentads.size();

            do {
                if (!purePentads.iterator().hasNext()) {
                    break;
                }

                Set<Integer> cluster = purePentads.iterator().next();
                Set<Integer> _cluster = new HashSet<Integer>(cluster);

                VARIABLES:
                for (int o : _variables) {
                    if (_cluster.contains(o)) continue;

                    List<Integer> _cluster2 = new ArrayList<Integer>(_cluster);

                    ChoiceGenerator gen = new ChoiceGenerator(_cluster2.size(), 4);
                    int[] choice;

                    while ((choice = gen.next()) != null) {
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

                        if (!purePentads.contains(t)) {
                            continue VARIABLES;
                        }

//                        if (avgSumLnP(new ArrayList<Integer>(t)) < -10) continue CLUSTER;
                    }

                    _cluster.add(o);
                }

                // This takes out all pure clusters that are subsets of _cluster.
                ChoiceGenerator gen2 = new ChoiceGenerator(_cluster.size(), 5);
                int[] choice2;
                List<Integer> _cluster3 = new ArrayList<Integer>(_cluster);

                while ((choice2 = gen2.next()) != null) {
                    int n1 = _cluster3.get(choice2[0]);
                    int n2 = _cluster3.get(choice2[1]);
                    int n3 = _cluster3.get(choice2[2]);
                    int n4 = _cluster3.get(choice2[3]);
                    int n5 = _cluster3.get(choice2[4]);

                    t.clear();
                    t.add(n1);
                    t.add(n2);
                    t.add(n3);
                    t.add(n4);
                    t.add(n5);

                    purePentads.remove(t);
                }

                if (verbose) {
                    System.out.println("Grown " + (++count) + " of " + total + ": " + _cluster);
                }
                grown.add(_cluster);
            } while (!purePentads.isEmpty());
        }

        if (false) {
            System.out.println("# pure pentads = " + purePentads.size());

            List<Set<Integer>> clusters = new LinkedList<Set<Integer>>(purePentads);
            Set<Integer> t = new HashSet<Integer>();

            I:
            for (int i = 0; i < clusters.size(); i++) {
                System.out.println("I = " + i);

//                // remove "i" clusters that intersect with previous clusters.
//                for (int k = 0; k < i - 1; k++) {
//                    Set<Integer> ck = clusters.get(k);
//                    Set<Integer> ci = clusters.get(i);
//
//                    if (ck == null) continue;
//                    if (ci == null) continue;
//
//                    Set<Integer> cm = new HashSet<Integer>(ck);
//                    cm.retainAll(ci);
//
//                    if (!cm.isEmpty()) {
//                        clusters.remove(i);
//                        i--;
//                        continue I;
//                    }
//                }

                J:
                for (int j = i + 1; j < clusters.size(); j++) {
                    Set<Integer> ci = clusters.get(i);
                    Set<Integer> cj = clusters.get(j);

                    if (ci == null) continue;
                    if (cj == null) continue;

                    Set<Integer> ck = new HashSet<Integer>(ci);
                    ck.addAll(cj);

                    List<Integer> cm = new ArrayList<Integer>(ck);

                    ChoiceGenerator gen = new ChoiceGenerator(cm.size(), 5);
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        t.clear();
                        t.add(cm.get(choice[0]));
                        t.add(cm.get(choice[1]));
                        t.add(cm.get(choice[2]));
                        t.add(cm.get(choice[3]));
                        t.add(cm.get(choice[4]));

                        if (!purePentads.contains(t)) {
                            continue J;
                        }
                    }

                    clusters.set(i, ck);
                    clusters.remove(j);
                    j--;
                    System.out.println("Removing " + ci + ", " + cj + ", adding " + ck);
                }
            }

            grown = new HashSet<Set<Integer>>(clusters);
        }

        // Optimized pick phase.
        log("Choosing among grown clusters.", true);

        for (Set<Integer> l : grown) {
            ArrayList<Integer> _l = new ArrayList<Integer>(l);
            Collections.sort(_l);
            if (verbose) {
                log("Grown: " + variablesForIndices(_l), false);
            }
        }

        Set<Set<Integer>> out = new HashSet<Set<Integer>>();

        List<Set<Integer>> list = new ArrayList<Set<Integer>>(grown);

        Collections.sort(list, new Comparator<Set<Integer>>() {
            @Override
            public int compare(Set<Integer> o1, Set<Integer> o2) {
                return o2.size() - o1.size();
            }
        });

//        final Map<Set<Integer>, Double> significances = new HashMap<Set<Integer>, Double>();

//        Collections.sort(list, new Comparator<Set<Integer>>() {
//            @Override
//            public int compare(Set<Integer> cluster1, Set<Integer> cluster2) {
////                Double sum1 = significances.get(cluster1);
////                if (sum1 == null) {
////                    double sig = significance(new ArrayList<Integer>(cluster1));
////                    significances.put(cluster1, sig);
////                    sum1 = sig;
////                }
////                Double sum2 = significances.get(cluster2);
////                if (sum2 == null) {
////                    double sig = significance(new ArrayList<Integer>(cluster2));
////                    significances.put(cluster2, sig);
////                    sum2 = sig;
////                }
//
//                double avg1 = avgSumLnP(new ArrayList<Integer>(cluster1));
//                double avg2 = avgSumLnP(new ArrayList<Integer>(cluster2));
//
//                return Double.compare(avg2, avg1);
//            }
//        });

        Set<Integer> all = new HashSet<Integer>();

        CLUSTER:
        for (Set<Integer> cluster : list) {
            for (Integer i : cluster) {
                if (all.contains(i)) continue CLUSTER;
            }

            out.add(cluster);
            all.addAll(cluster);
        }

        if (significanceCalculated) {
            for (Set<Integer> _out : out) {
                try {
                    double p = significance(new ArrayList<Integer>(_out));
                    log("OUT: " + variablesForIndices(new ArrayList<Integer>(_out)) + " p = " + p, true);
                } catch (Exception e) {
                    log("OUT: " + variablesForIndices(new ArrayList<Integer>(_out)) + " p = EXCEPTION", true);
                }
            }
        } else {
            for (Set<Integer> _out : out) {
                log("OUT: " + variablesForIndices(new ArrayList<Integer>(_out)), true);
            }
        }

        return out;
    }

    Map<Set<Integer>, Double> avgSumLnPs = new HashMap<Set<Integer>, Double>();

    private double avgSumLnP(List<Integer> cluster) {
        ChoiceGenerator gen = new ChoiceGenerator(cluster.size(), 5);
        int[] choice;
        int count = 0;
        double sumLnP = 0;

        Set<Integer> _cluster = new HashSet<Integer>(cluster);

        if (avgSumLnPs.containsKey(_cluster)) {
            return avgSumLnPs.get(_cluster);
        }

        while ((choice = gen.next()) != null) {
            int n1 = cluster.get(choice[0]);
            int n2 = cluster.get(choice[1]);
            int n3 = cluster.get(choice[2]);
            int n4 = cluster.get(choice[3]);
            int n5 = cluster.get(choice[4]);

            List<Integer> pentad = pentad(n1, n2, n3, n4, n5);

            for (int o : cluster) {
                if (pentad.contains(o)) {
                    continue;
                }

                List<Integer> sextad = sextet(n1, n2, n3, n4, n5, o);
                double p = sextadVanishingP(sextad);
                sumLnP += p;
                count++;
            }
        }

        sumLnP /= count;

        if (count == 0) sumLnP = Double.NEGATIVE_INFINITY;

        avgSumLnPs.put(_cluster, sumLnP);
        return sumLnP;
    }

    // Finds clusters of size 6 or higher for the sextad first algorithm.
    private Set<List<Integer>> findPureClusters(List<Integer> _variables, Graph graph) {
        Set<List<Integer>> clusters = new HashSet<List<Integer>>();
//        List<Integer> allVariables = new ArrayList<Integer>();
//        for (int i = 0; i < this.variables.size(); i++) allVariables.add(i);
        List<Integer> allVariables = allVariables();

        VARIABLES:
        while (!_variables.isEmpty()) {
            if (verbose) {
                System.out.println(_variables);
            }
            if (_variables.size() < 6) break;

            ChoiceGenerator gen = new ChoiceGenerator(_variables.size(), 6);
            int[] choice;

            CHOICE:
            while ((choice = gen.next()) != null) {
                int n1 = _variables.get(choice[0]);
                int n2 = _variables.get(choice[1]);
                int n3 = _variables.get(choice[2]);
                int n4 = _variables.get(choice[3]);
                int n5 = _variables.get(choice[4]);
                int n6 = _variables.get(choice[5]);

                List<Integer> cluster = sextet(n1, n2, n3, n4, n5, n6);

                // Note that purity needs to be assessed with respect to all of the variables in order to
                // remove all latent-measure impurities between pairs of latents.
                if (pure(cluster, allVariables)) {
                    if (verbose) {
                        log("Found a pure: " + variablesForIndices(cluster), false);
                    }

//                    if (modelInsignificantWithNewCluster(clusters, cluster)) continue CHOICE;

                    O:
                    for (int o : _variables) {
                        if (cluster.contains(o)) continue;
                        List<Integer> _cluster = new ArrayList<Integer>(cluster);
                        _cluster.add(o);

                        ChoiceGenerator gen2 = new ChoiceGenerator(_cluster.size(), 6);
                        int[] choice2;

                        while ((choice2 = gen2.next()) != null) {
                            int t1 = _cluster.get(choice2[0]);
                            int t2 = _cluster.get(choice2[1]);
                            int t3 = _cluster.get(choice2[2]);
                            int t4 = _cluster.get(choice2[3]);
                            int t5 = _cluster.get(choice2[4]);
                            int t6 = _cluster.get(choice2[5]);

                            List<Integer> sextet = sextet(t1, t2, t3, t4, t5, t6);

                            if (sextet.contains(o)) {

//                                Optimizes for large clusters.
//                                if (++count > 100) {
//                                    break WHILE;
//                                }

                                if (!pure(sextet, allVariables)) {
                                    continue O;
                                }
                            }
                        }

//                        if (modelInsignificantWithNewCluster(clusters, cluster)) continue O;

                        if (verbose) {
                            log("Extending by " + variables.get(o), false);
                        }
                        cluster.add(o);
                    }

                    if (verbose) {
                        log("Cluster found: " + variablesForIndices(cluster), true);
                    }
                    clusters.add(cluster);
                    _variables.removeAll(cluster);

                    for (int p : cluster) {
                        graph.removeNode(variables.get(p));
                    }

                    continue VARIABLES;
                }
            }

            break;
        }

        return clusters;
    }

    private boolean modelInsignificantWithNewCluster(Set<List<Integer>> clusters, List<Integer> cluster) {
        if (true) return false;

        List<List<Integer>> __clusters = new ArrayList<List<Integer>>(clusters);
        __clusters.add(cluster);
        double significance3 = getModelPValue(__clusters);
        if (verbose) {
            log("Significance * " + __clusters + " = " + significance3, false);
        }

        if (significance3 < alpha) {
            return true;
        }
        return false;
    }

    //  Finds clusters of size 5 for the sextet first algorithm.
    private Set<List<Integer>> findMixedClusters(Set<List<Integer>> clusters, List<Integer> remaining, Set<Integer> unionPure, Graph graph) {
        Set<List<Integer>> fiveClusters = new HashSet<List<Integer>>();
        Set<List<Integer>> _clusters = new HashSet<List<Integer>>(clusters);

        if (unionPure.isEmpty()) {
            return new HashSet<List<Integer>>();
        }

        REMAINING:
        while (true) {
            if (remaining.size() < 5) break;

            if (verbose) {
                log("UnionPure = " + variablesForIndices(new ArrayList<Integer>(unionPure)), false);
            }

            ChoiceGenerator gen = new ChoiceGenerator(remaining.size(), 5);
            int[] choice;

            CHOICE:
            while ((choice = gen.next()) != null) {
                int t2 = remaining.get(choice[0]);
                int t3 = remaining.get(choice[1]);
                int t4 = remaining.get(choice[2]);
                int t5 = remaining.get(choice[3]);
                int t6 = remaining.get(choice[4]);

                List<Integer> cluster = new ArrayList<Integer>();
                cluster.add(t2);
                cluster.add(t3);
                cluster.add(t4);
                cluster.add(t5);
                cluster.add(t6);

                // Check all x as a cross check; really only one should be necessary.
                boolean allT1 = true;
                boolean someT1 = false;
                int count = 0;

                for (int t1 : unionPure) {
                    if (cluster.contains(t1)) continue;

                    List<Integer> _cluster = new ArrayList<Integer>(cluster);
                    _cluster.add(t1);

                    if (!(sextadVanishingP(_cluster) > alpha)) {
                        allT1 = false;
                    } else {
                        someT1 = true;
                    }
                }

                if (someT1 && allT1) {
                    if (modelInsignificantWithNewCluster(_clusters, cluster)) continue;

                    fiveClusters.add(cluster);
                    _clusters.add(cluster);
                    unionPure.addAll(cluster);
                    remaining.removeAll(cluster);

                    if (verbose) {
                        log("5-cluster found: " + variablesForIndices(cluster), false);
                    }

                    continue REMAINING;
                }
            }

            break;
        }

        return fiveClusters;
    }

    private double significance(List<Integer> cluster) {
        double chisq = getClusterChiSquare(cluster);

        // From "Algebraic factor analysis: tetrads, pentads and beyond" Drton et al.
        int n = cluster.size();
        int dof = dofHarman(n);
        double q = ProbUtils.chisqCdf(chisq, dof);
        return 1.0 - q;
    }

    private double modelSignificance(List<List<Integer>> clusters) {
        return getModelPValue(clusters);
    }

    private int dofDrton(int n) {
        int dof = ((n - 2) * (n - 3)) / 2 - 2;
        if (dof < 0) dof = 0;
        return dof;
    }

    private int dofHarman(int n) {
        int dof = n * (n - 5) / 2 + 1;
        if (dof < 0) dof = 0;
        return dof;
    }

    private List<Node> variablesForIndices(List<Integer> cluster) {
        List<Node> _cluster = new ArrayList<Node>();

        for (int c : cluster) {
            _cluster.add(variables.get(c));
        }

        Collections.sort(_cluster);

        return _cluster;
    }

    private List<List<Node>> variablesForIndices2(Set<List<Integer>> clusters) {
        List<List<Node>> variables = new ArrayList<List<Node>>();

        for (List<Integer> cluster : clusters) {
            variables.add(variablesForIndices(cluster));
        }

        return variables;
    }

    private boolean pure(List<Integer> sextet, List<Integer> variables) {
        if (sextadVanishingP(sextet) > alpha) {
            int fails = 0;

            for (int o : variables) {
                if (sextet.contains(o)) continue;

                for (int i = 0; i < sextet.size(); i++) {
                    List<Integer> _sextet = new ArrayList<Integer>(sextet);
                    _sextet.remove(_sextet.get(i));
                    _sextet.add(i, o);

                    if (!(sextadVanishingP(_sextet) > alpha)) {
                        fails++;

                        if (fails > 0) {
                            return false;
                        }
                    }
                }
            }

            return true;
        }

        return false;
    }

    private double getClusterChiSquare(List<Integer> cluster) {
        SemIm im = estimateClusterModel(cluster);
        return im.getChiSquare();
    }

    private SemIm estimateClusterModel(List<Integer> sextet) {
        Graph g = new EdgeListGraph();
        Node l1 = new GraphNode("L1");
        l1.setNodeType(NodeType.LATENT);
        Node l2 = new GraphNode("L2");
        l2.setNodeType(NodeType.LATENT);
        g.addNode(l1);
        g.addNode(l2);

        for (int i = 0; i < sextet.size(); i++) {
            Node n = this.variables.get(sextet.get(i));
            g.addNode(n);
            g.addDirectedEdge(l1, n);
            g.addDirectedEdge(l2, n);
        }

        SemPm pm = new SemPm(g);

        SemEstimator est;

        if (dataModel instanceof DataSet) {
            est = new SemEstimator((DataSet) dataModel, pm, new SemOptimizerEm());
        } else {
            est = new SemEstimator((CovarianceMatrix) dataModel, pm, new SemOptimizerEm());
        }

        return est.estimate();
    }

    private double getModelPValue(List<List<Integer>> clusters) {
        SemIm im = estimateModel(clusters);
        return im.getPValue();
    }

    private SemIm estimateModel(List<List<Integer>> clusters) {
        Graph g = new EdgeListGraph();

        List<Node> upperLatents = new ArrayList<Node>();
        List<Node> lowerLatents = new ArrayList<Node>();

        for (int i = 0; i < clusters.size(); i++) {
            List<Integer> cluster = clusters.get(i);
            Node l1 = new GraphNode("L1." + (i + 1));
            l1.setNodeType(NodeType.LATENT);

            Node l2 = new GraphNode("L2." + (i + 1));
            l2.setNodeType(NodeType.LATENT);

            upperLatents.add(l1);
            lowerLatents.add(l2);

            g.addNode(l1);
            g.addNode(l2);

            for (int k = 0; k < cluster.size(); k++) {
                Node n = this.variables.get(cluster.get(k));
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

        SemPm pm = new SemPm(g);

        for (Node node : upperLatents) {
            Parameter p = pm.getParameter(node, node);
            p.setFixed(true);
            p.setStartingValue(1.0);
        }

        for (Node node : lowerLatents) {
            Parameter p = pm.getParameter(node, node);
            p.setFixed(true);
            p.setStartingValue(1.0);
        }

        SemEstimator est;

        if (dataModel instanceof DataSet) {
            est = new SemEstimator((DataSet) dataModel, pm, new SemOptimizerEm());
        } else {
            est = new SemEstimator((CovarianceMatrix) dataModel, pm, new SemOptimizerEm());
        }

        return est.estimate();
    }

    private List<Integer> sextet(int n1, int n2, int n3, int n4, int n5, int n6) {
        List<Integer> sextet = new ArrayList<Integer>();
        sextet.add(n1);
        sextet.add(n2);
        sextet.add(n3);
        sextet.add(n4);
        sextet.add(n5);
        sextet.add(n6);

        if (new HashSet<Integer>(sextet).size() < 6)
            throw new IllegalArgumentException("Sextet elements must be unique: <" + n1 + ", " + n2 + ", " + n3 + ", " + n4 + ", " + n5 + ", " + n6 + ">");

        return sextet;
    }

    private List<Integer> pentad(int n1, int n2, int n3, int n4, int n5) {
        List<Integer> pentad = new ArrayList<Integer>();
        pentad.add(n1);
        pentad.add(n2);
        pentad.add(n3);
        pentad.add(n4);
        pentad.add(n5);

        if (new HashSet<Integer>(pentad).size() < 5)
            throw new IllegalArgumentException("Pentad elements must be unique: <" + n1 + ", " + n2 + ", " + n3 + ", " + n4 + ", " + n5 + ">");

        return pentad;
    }

    private double sextadVanishingP(List<Integer> sextet) {
//        Collections.sort(sextet);

        int n1 = sextet.get(0);
        int n2 = sextet.get(1);
        int n3 = sextet.get(2);
        int n4 = sextet.get(3);
        int n5 = sextet.get(4);
        int n6 = sextet.get(5);

        return testVanishing(n1, n2, n3, n4, n5, n6);
    }

    /**
     * The clusters output by the algorithm from the last call to search().
     */
    public List<List<Node>> getClusters() {
        return clusters;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    private double testVanishing(int n1, int n2, int n3, int n4, int n5, int n6) {
        Node m1 = variables.get(n1);
        Node m2 = variables.get(n2);
        Node m3 = variables.get(n3);
        Node m4 = variables.get(n4);
        Node m5 = variables.get(n5);
        Node m6 = variables.get(n6);

//            sextad[,1]=c(indices[1],indices[2],indices[3],indices[4],indices[5],indices[6])
//            sextad[,2]=c(indices[1],indices[5],indices[6],indices[2],indices[3],indices[4])
//            sextad[,3]=c(indices[1],indices[4],indices[6],indices[2],indices[3],indices[5])
//            sextad[,4]=c(indices[1],indices[4],indices[5],indices[2],indices[3],indices[6])
//            sextad[,5]=c(indices[1],indices[3],indices[4],indices[2],indices[5],indices[6])
//            sextad[,6]=c(indices[1],indices[3],indices[5],indices[2],indices[4],indices[6])
//            sextad[,7]=c(indices[1],indices[3],indices[6],indices[2],indices[4],indices[5])
//            sextad[,8]=c(indices[1],indices[2],indices[4],indices[3],indices[5],indices[6])
//            sextad[,9]=c(indices[1],indices[2],indices[5],indices[3],indices[4],indices[6])
//            sextad[,10]=c(indices[1],indices[2],indices[6],indices[3],indices[4],indices[5])

        Sextad t1 = new Sextad(m1, m2, m3, m4, m5, m6);
        Sextad t2 = new Sextad(m1, m5, m6, m2, m3, m4);
        Sextad t3 = new Sextad(m1, m4, m6, m2, m3, m5);
        Sextad t4 = new Sextad(m1, m4, m5, m2, m3, m6);
        Sextad t5 = new Sextad(m1, m3, m4, m2, m5, m6);
        Sextad t6 = new Sextad(m1, m3, m5, m2, m4, m6);
        Sextad t7 = new Sextad(m1, m3, m6, m2, m4, m5);
        Sextad t8 = new Sextad(m1, m2, m4, m3, m5, m6);
        Sextad t9 = new Sextad(m1, m2, m5, m3, m4, m6);
        Sextad t10 = new Sextad(m1, m2, m6, m3, m4, m5);

        if (test instanceof DeltaSextadTest) {

//            Sextad[] independents = {t2, t5, t10, t3, t6};

            List<Sextad[]> independents = new ArrayList<Sextad[]>();
//            independents.add(new Sextad[]{t1a, t2, t3, t5, t6});
//            independents.add(new Sextad[]{t1a, t2, t3, t9, t10});
//            independents.add(new Sextad[]{t6, t7, t8, t9, t10});
//            independents.add(new Sextad[]{t1a, t2, t4, t5, t9});
//            independents.add(new Sextad[]{t1a, t3, t4, t6, t10});

            // The four tetrads implied by equation 5.17 in Harmann.
            independents.add(new Sextad[]{t3, t7, t8, t9});


//            1	2	4	5	9
//            1	3	4	6	10
//            double p = test.getPValue(independents);
//            return p > alpha;

//            List<Sextad> t = new ArrayList<Sextad>();
//            t.add(t1a);
//            t.add(t2);
//            t.add(t3);
//            t.add(t4);
//            t.add(t5);
//            t.add(t6);
//            t.add(t7);
//            t.add(t8);
//            t.add(t9);
//            t.add(t10);
//
//            for (Sextad[] sextad : independents) {
//                double p = test.getPValue(sextad);
//                if (p < alpha) return false;
//            }
            double p = test.getPValue(independents.get(0));
            return p;
//
        } else {
            throw new IllegalArgumentException();
        }
    }

    private Graph convertSearchGraphNodes(Set<Set<Node>> clusters) {
        Graph graph = new EdgeListGraph(variables);

        List<Node> latents = new ArrayList<Node>();
        for (int i = 0; i < clusters.size(); i++) {
            Node latent = new GraphNode(MimBuild.LATENT_PREFIX + (i + 1));
            latent.setNodeType(NodeType.LATENT);
            latents.add(latent);
            graph.addNode(latent);
        }

        List<Set<Node>> _clusters = new ArrayList<Set<Node>>(clusters);

        for (int i = 0; i < latents.size(); i++) {
            for (Node node : _clusters.get(i)) {
                if (!graph.containsNode(node)) graph.addNode(node);
                graph.addDirectedEdge(latents.get(i), node);
            }
        }

        return graph;
    }

    private Graph convertToGraph(Set<List<Integer>> allClusters) {
        Set<Set<Node>> _clustering = new HashSet<Set<Node>>();

        for (List<Integer> cluster : allClusters) {
            Set<Node> nodes = new HashSet<Node>();

            for (int i : cluster) {
                nodes.add(variables.get(i));
            }

            _clustering.add(nodes);
        }

        return convertSearchGraphNodes(_clustering);
    }

    private Set<Integer> unionPure(Set<List<Integer>> pureClusters) {
        Set<Integer> unionPure = new HashSet<Integer>();

        for (List<Integer> cluster : pureClusters) {
            unionPure.addAll(cluster);
        }

        return unionPure;
    }

    private void log(String s, boolean toLog) {
        if (toLog) {
            TetradLogger.getInstance().log("info", s);
        }

        System.out.println(s);
    }

    public boolean isSignificanceCalculated() {
        return significanceCalculated;
    }

    public void setSignificanceCalculated(boolean significanceCalculated) {
        this.significanceCalculated = significanceCalculated;
    }
}




