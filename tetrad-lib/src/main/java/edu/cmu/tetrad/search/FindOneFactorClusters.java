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
 * quartet algorithm to a one factor tetrad algorithm).
 *
 * @author Joseph Ramsey
 */
public class FindOneFactorClusters {

    private final CorrelationMatrix corr;
    // The list of all variables.
    private final List<Node> variables;
    // The significance level.
    private final double alpha;
    // The Delta test. Testing two tetrads simultaneously.
    private final DeltaTetradTest test;
    // The tetrad test--using Ricardo's. Used only for Wishart.
    private final ContinuousTetradTest test2;
    // The data.
    private final transient DataModel dataModel;
    private final TestType testType;
    private List<List<Node>> clusters;
    private boolean verbose;
    private boolean significanceCalculated;
    private final Algorithm algorithm;

    public FindOneFactorClusters(ICovarianceMatrix cov, final TestType testType, final Algorithm algorithm, final double alpha) {
        if (testType == null) throw new NullPointerException("Null indepTest type.");
        cov = new CovarianceMatrix(cov);
        this.variables = cov.getVariables();
        this.alpha = alpha;
        this.testType = testType;
        this.test = new DeltaTetradTest(cov);
        this.test2 = new ContinuousTetradTest(cov, testType, alpha);
        this.dataModel = cov;
        this.algorithm = algorithm;

        this.corr = new CorrelationMatrix(cov);


    }

    public FindOneFactorClusters(final DataSet dataSet, final TestType testType, final Algorithm algorithm, final double alpha) {
        if (testType == null) throw new NullPointerException("Null test type.");
        this.variables = dataSet.getVariables();
        this.alpha = alpha;
        this.testType = testType;
        this.test = new DeltaTetradTest(dataSet);
        this.test2 = new ContinuousTetradTest(dataSet, testType, alpha);
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

    // This is the main function. It removes variables in the data such that the remaining
    // correlation matrix does not contain extreme value
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

        log(removedVariables.size() + " variables removed: " + variablesForIndices(removedVariables));

        return (removedVariables);
    }

    // renjiey
    private Integer[] removeZeroIndex(final Integer[] outlier) {
        final List<Integer> list = new ArrayList<>();
        Collections.addAll(list, outlier);
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
            allClusters = estimateClustersTetradsFirst();
        } else if (this.algorithm == Algorithm.GAP) {
            allClusters = estimateClustersTriplesFirst();
        } else {
            throw new IllegalStateException("Expected SAG or GAP: " + this.testType);
        }
        this.clusters = variablesForIndices2(allClusters);
        return convertToGraph(allClusters);
    }

    //========================================PRIVATE METHODS====================================//

    // This is the main algorithm.
    private Set<List<Integer>> estimateClustersTriplesFirst() {
        final List<Integer> _variables = allVariables();

        final Set<Set<Integer>> triples = findPuretriples(_variables);
        final Set<Set<Integer>> combined = combinePuretriples(triples, _variables);

        final Set<List<Integer>> _combined = new HashSet<>();

        for (final Set<Integer> c : combined) {
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

    private Set<List<Integer>> estimateClustersTetradsFirst() {
        final List<Integer> _variables = allVariables();

        final Set<List<Integer>> pureClusters = findPureClusters(_variables);
        final Set<List<Integer>> mixedClusters = findMixedClusters(_variables, unionPure(pureClusters));
        final Set<List<Integer>> allClusters = new HashSet<>(pureClusters);
        allClusters.addAll(mixedClusters);
        return allClusters;

    }

    private Set<Set<Integer>> findPuretriples(final List<Integer> allVariables) {
        if (allVariables.size() < 4) {
            return new HashSet<>();
        }

        log("Finding pure triples.");

        final ChoiceGenerator gen = new ChoiceGenerator(allVariables.size(), 3);
        int[] choice;
        final Set<Set<Integer>> puretriples = new HashSet<>();
        CHOICE:
        while ((choice = gen.next()) != null) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final int n1 = allVariables.get(choice[0]);
            final int n2 = allVariables.get(choice[1]);
            final int n3 = allVariables.get(choice[2]);

            final List<Integer> triple = triple(n1, n2, n3);

            if (zeroCorr(triple)) continue;

            for (final int o : allVariables) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (triple.contains(o)) {
                    continue;
                }

                final List<Integer> quartet = quartet(n1, n2, n3, o);

                final boolean vanishes = vanishes(quartet);

                if (!vanishes) {
                    continue CHOICE;
                }

            }

            final HashSet<Integer> _cluster = new HashSet<>(triple);

            if (this.verbose) {
                log("++" + variablesForIndices(triple));
            }

            puretriples.add(_cluster);
        }

        return puretriples;
    }

    private Set<Set<Integer>> combinePuretriples(final Set<Set<Integer>> puretriples, final List<Integer> _variables) {
        log("Growing pure triples.");
        Set<Set<Integer>> grown = new HashSet<>();

        // Lax grow phase with speedup.
        if (true) {
            final Set<Integer> t = new HashSet<>();
            int count = 0;
            final int total = puretriples.size();

            do {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (!puretriples.iterator().hasNext()) {
                    break;
                }

                final Set<Integer> cluster = puretriples.iterator().next();
                final Set<Integer> _cluster = new HashSet<>(cluster);

                for (final int o : _variables) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    if (_cluster.contains(o)) continue;

                    final List<Integer> _cluster2 = new ArrayList<>(_cluster);
                    int rejected = 0;
                    int accepted = 0;

                    final ChoiceGenerator gen = new ChoiceGenerator(_cluster2.size(), 2);
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

                }

                // This takes out all pure clusters that are subsets of _cluster.
                final ChoiceGenerator gen2 = new ChoiceGenerator(_cluster.size(), 3);
                int[] choice2;
                final List<Integer> _cluster3 = new ArrayList<>(_cluster);

                while ((choice2 = gen2.next()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    final int n1 = _cluster3.get(choice2[0]);
                    final int n2 = _cluster3.get(choice2[1]);
                    final int n3 = _cluster3.get(choice2[2]);

                    t.clear();
                    t.add(n1);
                    t.add(n2);
                    t.add(n3);

                    puretriples.remove(t);
                }

                if (this.verbose) {
                    log("Grown " + (++count) + " of " + total + ": " + variablesForIndices(new ArrayList<>(_cluster)));
                }
                grown.add(_cluster);
            } while (!puretriples.isEmpty());
        }

        // Lax grow phase without speedup.
        if (false) {
            int count = 0;
            final int total = puretriples.size();

            // Optimized lax version of grow phase.
            for (final Set<Integer> cluster : new HashSet<>(puretriples)) {
                final Set<Integer> _cluster = new HashSet<>(cluster);

                for (final int o : _variables) {
                    if (_cluster.contains(o)) continue;

                    final List<Integer> _cluster2 = new ArrayList<>(_cluster);
                    int rejected = 0;
                    int accepted = 0;

                    final ChoiceGenerator gen = new ChoiceGenerator(_cluster2.size(), 4);
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        final int n1 = _cluster2.get(choice[0]);
                        final int n2 = _cluster2.get(choice[1]);

                        final List<Integer> triple = triple(n1, n2, o);

                        final Set<Integer> t = new HashSet<>(triple);

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

                for (final Set<Integer> c : new HashSet<>(puretriples)) {
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
            final Set<Integer> t = new HashSet<>();
            int count = 0;
            final int total = puretriples.size();

            do {
                if (!puretriples.iterator().hasNext()) {
                    break;
                }

                final Set<Integer> cluster = puretriples.iterator().next();
                final Set<Integer> _cluster = new HashSet<>(cluster);

                VARIABLES:
                for (final int o : _variables) {
                    if (_cluster.contains(o)) continue;

                    final List<Integer> _cluster2 = new ArrayList<>(_cluster);

                    final ChoiceGenerator gen = new ChoiceGenerator(_cluster2.size(), 4);
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

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

                        if (!puretriples.contains(t)) {
                            continue VARIABLES;
                        }

//                        if (avgSumLnP(new ArrayList<Integer>(t)) < -10) continue CLUSTER;
                    }

                    _cluster.add(o);
                }

                // This takes out all pure clusters that are subsets of _cluster.
                final ChoiceGenerator gen2 = new ChoiceGenerator(_cluster.size(), 3);
                int[] choice2;
                final List<Integer> _cluster3 = new ArrayList<>(_cluster);

                while ((choice2 = gen2.next()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    final int n1 = _cluster3.get(choice2[0]);
                    final int n2 = _cluster3.get(choice2[1]);
                    final int n3 = _cluster3.get(choice2[2]);

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

            final List<Set<Integer>> clusters = new LinkedList<>(puretriples);
            final Set<Integer> t = new HashSet<>();

            for (int i = 0; i < clusters.size(); i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                System.out.println("I = " + i);

                J:
                for (int j = i + 1; j < clusters.size(); j++) {
                    final Set<Integer> ci = clusters.get(i);
                    final Set<Integer> cj = clusters.get(j);

                    if (ci == null) continue;
                    if (cj == null) continue;

                    final Set<Integer> ck = new HashSet<>(ci);
                    ck.addAll(cj);

                    final List<Integer> cm = new ArrayList<>(ck);

                    final ChoiceGenerator gen = new ChoiceGenerator(cm.size(), 3);
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

        for (final Set<Integer> l : grown) {
            final ArrayList<Integer> _l = new ArrayList<>(l);
            Collections.sort(_l);
            if (this.verbose) {
                log("Grown: " + variablesForIndices(_l));
            }
        }

        final Set<Set<Integer>> out = new HashSet<>();

        final List<Set<Integer>> list = new ArrayList<>(grown);

        list.sort((o1, o2) -> o2.size() - o1.size());

        final Set<Integer> all = new HashSet<>();

        CLUSTER:
        for (final Set<Integer> cluster : list) {
            for (final Integer i : cluster) {
                if (all.contains(i)) continue CLUSTER;
            }

            out.add(cluster);
            all.addAll(cluster);
        }

        if (this.significanceCalculated) {
            for (final Set<Integer> _out : out) {
                try {
                    final double p = significance(new ArrayList<>(_out));
                    log("OUT: " + variablesForIndices(new ArrayList<>(_out)) + " p = " + p);
                } catch (final Exception e) {
                    log("OUT: " + variablesForIndices(new ArrayList<>(_out)) + " p = EXCEPTION");
                }
            }
        } else {
            for (final Set<Integer> _out : out) {
                log("OUT: " + variablesForIndices(new ArrayList<>(_out)));
            }
        }

        return out;
    }

    // Finds clusters of size 4 or higher for the tetrad-first algorithm.
    private Set<List<Integer>> findPureClusters(final List<Integer> _variables) {
        final Set<List<Integer>> clusters = new HashSet<>();
        final List<Integer> allVariables = allVariables();

        VARIABLES:
        while (!_variables.isEmpty()) {
            if (this.verbose) {
                System.out.println(_variables);
            }
            if (_variables.size() < 4) break;

            final ChoiceGenerator gen = new ChoiceGenerator(_variables.size(), 4);
            int[] choice;

            while ((choice = gen.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final int n1 = _variables.get(choice[0]);
                final int n2 = _variables.get(choice[1]);
                final int n3 = _variables.get(choice[2]);
                final int n4 = _variables.get(choice[3]);

                final List<Integer> cluster = quartet(n1, n2, n3, n4);

                // Note that purity needs to be assessed with respect to all the variables in order to
                // remove all latent-measure impurities between pairs of latents.
                if (pure(cluster)) {

                    addOtherVariables(_variables, allVariables, cluster);

                    if (this.verbose) {
                        log("Cluster found: " + variablesForIndices(cluster));
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

    private void addOtherVariables(final List<Integer> _variables, final List<Integer> allVariables, final List<Integer> cluster) {
        O:
        for (final int o : _variables) {
            if (cluster.contains(o)) continue;
            final List<Integer> _cluster = new ArrayList<>(cluster);

            final ChoiceGenerator gen2 = new ChoiceGenerator(_cluster.size(), 3);
            int[] choice2;
//            boolean found = false;

            while ((choice2 = gen2.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final int t1 = _cluster.get(choice2[0]);
                final int t2 = _cluster.get(choice2[1]);
                final int t3 = _cluster.get(choice2[2]);

                final List<Integer> quartet = triple(t1, t2, t3);


                quartet.add(o);

                if (!pure(quartet)) {
                    continue O;
                }
            }

//            if (found) {
            log("Extending by " + this.variables.get(o));
            cluster.add(o);
//            }
        }
    }

    private boolean modelInsignificantWithNewCluster(final Set<List<Integer>> clusters, final List<Integer> cluster) {
//        if (true) return false;

        final List<List<Integer>> __clusters = new ArrayList<>(clusters);
        __clusters.add(cluster);
        final double significance3 = getModelPValue(__clusters);
        if (this.verbose) {
            log("Significance * " + __clusters + " = " + significance3);
        }

        return significance3 < this.alpha;
    }

    //  Finds clusters of size 3 3or the quartet-first algorithm.
    private Set<List<Integer>> findMixedClusters(final List<Integer> remaining, final Set<Integer> unionPure) {
        final Set<List<Integer>> triples = new HashSet<>();

        if (unionPure.isEmpty()) {
            return new HashSet<>();
        }

        REMAINING:
        while (true) {
            if (remaining.size() < 3) break;

            final ChoiceGenerator gen = new ChoiceGenerator(remaining.size(), 3);
            int[] choice;

            while ((choice = gen.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final int t2 = remaining.get(choice[0]);
                final int t3 = remaining.get(choice[1]);
                final int t4 = remaining.get(choice[2]);

                final List<Integer> cluster = new ArrayList<>();
                cluster.add(t2);
                cluster.add(t3);
                cluster.add(t4);

                if (zeroCorr(cluster)) {
                    continue;
                }

                // Check all x as a cross-check; really only one should be necessary.
                boolean allVanish = true;
                boolean someVanish = false;

                for (final int t1 : allVariables()) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    if (cluster.contains(t1)) continue;

                    final List<Integer> _cluster = new ArrayList<>(cluster);
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
                        log("3-cluster found: " + variablesForIndices(cluster));
                    }

                    continue REMAINING;
                }
            }

            break;
        }

        return triples;
    }

    private double significance(final List<Integer> cluster) {
        final double chisq = getClusterChiSquare(cluster);

        // From "Algebraic factor analysis: tetrads, triples and beyond" Drton et al.
        final int n = cluster.size();
        final int dof = dofHarman(n);
        final double q = ProbUtils.chisqCdf(chisq, dof);
        return 1.0 - q;
    }

    private double modelSignificance(final List<List<Integer>> clusters) {
        return getModelPValue(clusters);
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

//        Collections.sort(_cluster);

        return _cluster;
    }

    private List<List<Node>> variablesForIndices2(final Set<List<Integer>> clusters) {
        final List<List<Node>> variables = new ArrayList<>();

        for (final List<Integer> cluster : clusters) {
            variables.add(variablesForIndices(cluster));
        }

        return variables;
    }

    private boolean pure(final List<Integer> quartet) {
        if (zeroCorr(quartet)) {
            return false;
        }

        if (vanishes(quartet)) {
            for (final int o : allVariables()) {
                if (quartet.contains(o)) continue;

                for (int i = 0; i < quartet.size(); i++) {
                    final List<Integer> _quartet = new ArrayList<>(quartet);
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

    private double getClusterChiSquare(final List<Integer> cluster) {
        final SemIm im = estimateClusterModel(cluster);
        return im.getChiSquare();
    }

    private SemIm estimateClusterModel(final List<Integer> quartet) {
        final Graph g = new EdgeListGraph();
        final Node l1 = new GraphNode("L1");
        l1.setNodeType(NodeType.LATENT);
        final Node l2 = new GraphNode("L2");
        l2.setNodeType(NodeType.LATENT);
        g.addNode(l1);
        g.addNode(l2);

        for (final Integer integer : quartet) {
            final Node n = this.variables.get(integer);
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

    private double getModelPValue(final List<List<Integer>> clusters) {
        final SemIm im = estimateModel(clusters);
        return im.getPValue();
    }

    private SemIm estimateModel(final List<List<Integer>> clusters) {
        final Graph g = new EdgeListGraph();

        final List<Node> upperLatents = new ArrayList<>();
        final List<Node> lowerLatents = new ArrayList<>();

        for (int i = 0; i < clusters.size(); i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final List<Integer> cluster = clusters.get(i);
            final Node l1 = new GraphNode("L1." + (i + 1));
            l1.setNodeType(NodeType.LATENT);

            final Node l2 = new GraphNode("L2." + (i + 1));
            l2.setNodeType(NodeType.LATENT);

            upperLatents.add(l1);
            lowerLatents.add(l2);

            g.addNode(l1);
            g.addNode(l2);

            for (final Integer integer : cluster) {
                final Node n = this.variables.get(integer);
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

    private List<Integer> quartet(final int n1, final int n2, final int n3, final int n4) {
        final List<Integer> quartet = new ArrayList<>();
        quartet.add(n1);
        quartet.add(n2);
        quartet.add(n3);
        quartet.add(n4);

        if (new HashSet<>(quartet).size() < 4)
            throw new IllegalArgumentException("quartet elements must be unique: <" + n1 + ", " + n2 + ", " + n3 + ", " + n4 + ">");

        return quartet;
    }

    private List<Integer> triple(final int n1, final int n2, final int n3) {
        final List<Integer> triple = new ArrayList<>();
        triple.add(n1);
        triple.add(n2);
        triple.add(n3);

        if (new HashSet<>(triple).size() < 3)
            throw new IllegalArgumentException("triple elements must be unique: <" + n1 + ", " + n2 + ", " + n3 + ">");

        return triple;
    }

    private boolean vanishes(final List<Integer> quartet) {
        final int n1 = quartet.get(0);
        final int n2 = quartet.get(1);
        final int n3 = quartet.get(2);
        final int n4 = quartet.get(3);

        return vanishes(n1, n2, n3, n4);
    }

    private boolean zeroCorr(final List<Integer> cluster) {
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

        return count >= 1;
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

    private boolean vanishes(final int x, final int y, final int z, final int w) {
        if (this.testType == TestType.TETRAD_DELTA) {
            final Tetrad t1 = new Tetrad(this.variables.get(x), this.variables.get(y), this.variables.get(z), this.variables.get(w));
            final Tetrad t2 = new Tetrad(this.variables.get(x), this.variables.get(y), this.variables.get(w), this.variables.get(z));

            return this.test.getPValue(t1, t2) > this.alpha;
        } else if (this.testType == TestType.TETRAD_WISHART) {
            return this.test2.tetradPValue(x, y, z, w) > this.alpha && this.test2.tetradPValue(x, y, w, z) > this.alpha;
        }

        throw new IllegalArgumentException("Only the delta and wishart tests are being used: " + this.testType);
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

    private void log(final String s) {
        if (this.verbose) {
            TetradLogger.getInstance().forceLogMessage(s);
        }
    }

    public boolean isSignificanceCalculated() {
        return this.significanceCalculated;
    }

    public void setSignificanceCalculated(final boolean significanceCalculated) {
        this.significanceCalculated = significanceCalculated;
    }

    public enum Algorithm {SAG, GAP}
}




