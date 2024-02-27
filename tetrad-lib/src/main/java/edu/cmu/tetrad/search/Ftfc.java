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
import edu.cmu.tetrad.search.utils.BpcTestType;
import edu.cmu.tetrad.search.utils.ClusterUtils;
import edu.cmu.tetrad.search.utils.DeltaSextadTest;
import edu.cmu.tetrad.search.utils.Sextad;
import edu.cmu.tetrad.sem.SemEstimator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemOptimizerEm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.util.*;

import static org.apache.commons.math3.util.FastMath.abs;
import static org.apache.commons.math3.util.FastMath.sqrt;


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
 * @see Bpc
 */
public class Ftfc {
    /**
     * The correlation matrix.
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
     * The Delta test. Testing two sextads simultaneously.
     */
    private final DeltaSextadTest test;
    /**
     * The data.
     */
    private final transient DataModel dataModel;
    /**
     * The algorithm used.
     */
    private final Algorithm algorithm;
    /**
     * The clusters found.
     */
    private List<List<Node>> clusters;
    /**
     * Whether verbose output should be printed.
     */
    private boolean verbose;

    /**
     * Conctructor.
     *
     * @param cov       The covariance matrix searched over.
     * @param algorithm The type of FOFC algorithm used.
     * @param alpha     The alpha significance cutoff.
     * @see BpcTestType
     * @see Fofc.Algorithm
     */
    public Ftfc(ICovarianceMatrix cov, Algorithm algorithm, double alpha) {
        cov = new CovarianceMatrix(cov);
        this.variables = cov.getVariables();
        this.alpha = alpha;
        this.test = new DeltaSextadTest(cov);
        this.dataModel = cov;
        this.algorithm = algorithm;

        this.corr = new CorrelationMatrix(cov);
    }

    /**
     * Conctructor.
     *
     * @param dataSet   The continuous dataset searched over.
     * @param algorithm The type of FOFC algorithm used.
     * @param alpha     The alpha significance cutoff.
     * @see BpcTestType
     * @see Fofc.Algorithm
     */
    public Ftfc(DataSet dataSet, Algorithm algorithm, double alpha) {
        this.variables = dataSet.getVariables();
        this.alpha = alpha;
        this.test = new DeltaSextadTest(dataSet);
        this.dataModel = dataSet;
        this.algorithm = algorithm;

        this.corr = new CorrelationMatrix(dataSet);
    }

    /**
     * Runs the search and returns a graph of clusters, each of which has two common latent parents.
     *
     * @return This graph.
     */
    public Graph search() {
        Set<List<Integer>> allClusters;

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

    /**
     * Returns clusters output by the algorithm from the last call to search().
     *
     * @return These clusters.
     */
    public List<List<Node>> getClusters() {
        return this.clusters;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True if the case.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    // This is the main algorithm.
    private Set<List<Integer>> estimateClustersGAP() {
        List<Integer> _variables = allVariables();

        Set<List<Integer>> pentads = findPurepentads(_variables);
        Set<List<Integer>> combined = combinePurePentads(pentads, _variables);

        Set<List<Integer>> _combined = new HashSet<>();

        for (List<Integer> c : combined) {
            List<Integer> a = new ArrayList<>(c);
            Collections.sort(a);
            _combined.add(a);
        }

        return _combined;

    }

    /**
     * Returns a list of all variables.
     *
     * @return A list of all variables.
     */
    private List<Integer> allVariables() {
        List<Integer> _variables = new ArrayList<>();
        for (int i = 0; i < this.variables.size(); i++) _variables.add(i);
        return _variables;
    }

    /**
     * Estimates the clusters using the SAG algorithm.
     *
     * @return A set of clusters found by the SAG algorithm.
     */
    private Set<List<Integer>> estimateClustersSAG() {
        List<Integer> _variables = allVariables();

        Set<List<Integer>> pureClusters = findPureClusters(_variables);
        Set<List<Integer>> mixedClusters = findMixedClusters(pureClusters, _variables, unionPure(pureClusters));
        Set<List<Integer>> allClusters = new HashSet<>(pureClusters);
        allClusters.addAll(mixedClusters);
        return allClusters;

    }

    /**
     * Finds pure pentads from the given list of variables.
     *
     * @param variables The list of variables to search for pure pentads.
     * @return A set of pure pentads found from the given list of variables.
     */
    private Set<List<Integer>> findPurepentads(List<Integer> variables) {
        if (variables.size() < 6) {
            return new HashSet<>();
        }

        log("Finding pure pentads.", true);

        ChoiceGenerator gen = new ChoiceGenerator(variables.size(), 5);
        int[] choice;
        Set<List<Integer>> purePentads = new HashSet<>();
        CHOICE:
        while ((choice = gen.next()) != null) {
            int n1 = variables.get(choice[0]);
            int n2 = variables.get(choice[1]);
            int n3 = variables.get(choice[2]);
            int n4 = variables.get(choice[3]);
            int n5 = variables.get(choice[4]);

            List<Integer> pentad = pentad(n1, n2, n3, n4, n5);

            if (zeroCorr(pentad, 4)) continue;

            for (int o : variables) {
                if (pentad.contains(o)) {
                    continue;
                }

                List<Integer> sextet = sextet(n1, n2, n3, n4, n5, o);

                Collections.sort(sextet);

                boolean vanishes = vanishes(sextet);

                if (!vanishes) {
                    continue CHOICE;
                }
            }

            List<Integer> _cluster = new ArrayList<>(pentad);

            if (this.verbose) {
                System.out.println(variablesForIndices(pentad));
                log("++" + variablesForIndices(pentad), false);
            }

            purePentads.add(_cluster);
        }

        return purePentads;
    }

    /**
     * Combines pure pentads with variables to create new clusters.
     *
     * @param purePentads The set of pure pentads.
     * @param _variables  The list of variables.
     * @return The set of combined clusters.
     */
    private Set<List<Integer>> combinePurePentads(Set<List<Integer>> purePentads, List<Integer> _variables) {
        log("Growing pure pentads.", true);
        Set<List<Integer>> grown = new HashSet<>();

        // Lax grow phase with speedup.
        if (false) {
            List<Integer> t = new ArrayList<>();
            int count = 0;
            int total = purePentads.size();

            do {
                if (!purePentads.iterator().hasNext()) {
                    break;
                }

                List<Integer> cluster = purePentads.iterator().next();
                List<Integer> _cluster = new ArrayList<>(cluster);

                for (int o : _variables) {
                    if (_cluster.contains(o)) continue;

                    List<Integer> _cluster2 = new ArrayList<>(_cluster);
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

                }

                // This takes out all pure clusters that are subsets of _cluster.
                ChoiceGenerator gen2 = new ChoiceGenerator(_cluster.size(), 3);
                int[] choice2;
                List<Integer> _cluster3 = new ArrayList<>(_cluster);

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

                if (this.verbose) {
                    System.out.println("Grown " + (++count) + " of " + total + ": " + variablesForIndices(new ArrayList<>(_cluster)));
                }
                grown.add(_cluster);
            } while (!purePentads.isEmpty());
        }

        // Lax grow phase without speedup.
        if (false) {
            int count = 0;
            int total = purePentads.size();

            // Optimized lax version of grow phase.
            for (List<Integer> cluster : new HashSet<>(purePentads)) {
                List<Integer> _cluster = new ArrayList<>(cluster);

                for (int o : _variables) {
                    if (_cluster.contains(o)) continue;

                    List<Integer> _cluster2 = new ArrayList<>(_cluster);
                    int rejected = 0;
                    int accepted = 0;

                    ChoiceGenerator gen = new ChoiceGenerator(_cluster2.size(), 6);
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        int n1 = _cluster2.get(choice[0]);
                        int n2 = _cluster2.get(choice[1]);
                        int n3 = _cluster2.get(choice[2]);
                        int n4 = _cluster2.get(choice[3]);

                        List<Integer> pentad = pentad(n1, n2, n3, n4, o);

                        List<Integer> t = new ArrayList<>(pentad);

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

                for (List<Integer> c : new HashSet<>(purePentads)) {
                    if (new HashSet<>(_cluster).containsAll(c)) {
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
            List<Integer> t = new ArrayList<>();
            int count = 0;
            int total = purePentads.size();

            do {
                if (!purePentads.iterator().hasNext()) {
                    break;
                }

                List<Integer> cluster = purePentads.iterator().next();
                List<Integer> _cluster = new ArrayList<>(cluster);

                VARIABLES:
                for (int o : _variables) {
                    if (_cluster.contains(o)) continue;

                    List<Integer> _cluster2 = new ArrayList<>(_cluster);

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

                        Collections.sort(t);

                        if (!purePentads.contains(t)) {
                            continue VARIABLES;
                        }
                    }

                    _cluster.add(o);
                }

                // This takes out all pure clusters that are subsets of _cluster.
                ChoiceGenerator gen2 = new ChoiceGenerator(_cluster.size(), 5);
                int[] choice2;
                List<Integer> _cluster3 = new ArrayList<>(_cluster);

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

        for (List<Integer> l : grown) {
            ArrayList<Integer> _l = new ArrayList<>(l);
            Collections.sort(_l);
            if (this.verbose) {
                log("Grown: " + variablesForIndices(_l), false);
            }
        }

        Set<List<Integer>> out = new HashSet<>();

        List<List<Integer>> list = new ArrayList<>(grown);

        list.sort((o1, o2) -> o2.size() - o1.size());

        List<Integer> all = new ArrayList<>();

        CLUSTER:
        for (List<Integer> cluster : list) {
            for (Integer i : cluster) {
                if (all.contains(i)) continue CLUSTER;
            }

            out.add(cluster);
            all.addAll(cluster);
        }

        final boolean significanceCalculated = false;
        if (significanceCalculated) {
            for (List<Integer> _out : out) {
                try {
                    double p = significance(new ArrayList<>(_out));
                    log("OUT: " + variablesForIndices(new ArrayList<>(_out)) + " p = " + p, true);
                } catch (Exception e) {
                    log("OUT: " + variablesForIndices(new ArrayList<>(_out)) + " p = EXCEPTION", true);
                }
            }
        } else {
            for (List<Integer> _out : out) {
                log("OUT: " + variablesForIndices(new ArrayList<>(_out)), true);
            }
        }

        return out;
    }

    /**
     * Finds clusters of size 6 or higher for the IntSextad first algorithm.
     *
     * @param _variables The list of variables to search for pure clusters.
     * @return A set of pure clusters found from the given list of variables.
     */
    private Set<List<Integer>> findPureClusters(List<Integer> _variables) {
        Set<List<Integer>> clusters = new HashSet<>();

        for (int k = 6; k >= 6; k--) {
            VARIABLES:
            while (!_variables.isEmpty()) {
                if (this.verbose) {
                    System.out.println(_variables);
                }
                if (_variables.size() < 6) break;

                ChoiceGenerator gen = new ChoiceGenerator(_variables.size(), 6);
                int[] choice;

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

        }

        return clusters;
    }

    /**
     * Adds other variables to the cluster if they meet certain conditions.
     *
     * @param _variables The list of variables to consider.
     * @param cluster    The current cluster.
     */
    private void addOtherVariables(List<Integer> _variables, List<Integer> cluster) {

        O:
        for (int o : _variables) {
            if (cluster.contains(o)) continue;
            List<Integer> _cluster = new ArrayList<>(cluster);

            ChoiceGenerator gen2 = new ChoiceGenerator(_cluster.size(), 6);
            int[] choice;

            while ((choice = gen2.next()) != null) {
                int t1 = _cluster.get(choice[0]);
                int t2 = _cluster.get(choice[1]);
                int t3 = _cluster.get(choice[2]);
                int t4 = _cluster.get(choice[3]);
                int t5 = _cluster.get(choice[4]);

                List<Integer> sextad = pentad(t1, t2, t3, t4, t5);
                sextad.add(o);

                if (!pure(sextad)) {
                    continue O;
                }
            }

            log("Extending by " + this.variables.get(o), false);
            cluster.add(o);
        }
    }

    /**
     * Finds clusters of size 5 for the sextet-first algorithm.
     *
     * @param clusters  The current set of clusters.
     * @param remaining The list of remaining variables.
     * @param unionPure The set of variables that have been added to clusters.
     * @return The set of clusters of size 5 found by the algorithm.
     */
    private Set<List<Integer>> findMixedClusters(Set<List<Integer>> clusters, List<Integer> remaining, Set<Integer> unionPure) {
        Set<List<Integer>> pentads = new HashSet<>();

        if (unionPure.isEmpty()) {
            return new HashSet<>();
        }

        REMAINING:
        while (true) {
            if (remaining.size() < 5) break;

            if (this.verbose) {
                log("UnionPure = " + variablesForIndices(new ArrayList<>(unionPure)), false);
            }

            ChoiceGenerator gen = new ChoiceGenerator(remaining.size(), 5);
            int[] choice;

            while ((choice = gen.next()) != null) {
                int t2 = remaining.get(choice[0]);
                int t3 = remaining.get(choice[1]);
                int t4 = remaining.get(choice[2]);
                int t5 = remaining.get(choice[3]);
                int t6 = remaining.get(choice[4]);

                List<Integer> cluster = new ArrayList<>();
                cluster.add(t2);
                cluster.add(t3);
                cluster.add(t4);
                cluster.add(t5);
                cluster.add(t6);

                if (zeroCorr(cluster, 4)) {
                    continue;
                }

                // Check all x as a cross-check; really only one should be necessary.
                boolean allVanish = true;
                boolean someVanish = false;

                for (int t1 : allVariables()) {
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
                    pentads.add(cluster);
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

    /**
     * Calculates the significance of a cluster based on the cluster's chi-square value.
     *
     * @param cluster The list of variables in the cluster.
     * @return The significance of the cluster.
     */
    private double significance(List<Integer> cluster) {
        double chisq = getClusterChiSquare(cluster);

        // From "Algebraic factor analysis: sextads, pentads and beyond" Drton et al.
        int n = cluster.size();
        int dof = dofHarman(n);
        double q = ProbUtils.chisqCdf(chisq, dof);
        return 1.0 - q;
    }

    /**
     * Calculates the degrees of freedom based on the number of variables in a cluster.
     *
     * @param n The number of variables in the cluster.
     * @return The calculated degrees of freedom.
     */
    private int dofHarman(int n) {
        int dof = n * (n - 5) / 2 + 1;
        if (dof < 0) dof = 0;
        return dof;
    }

    /**
     * Returns a list of Node objects corresponding to the indices in the provided cluster.
     *
     * @param cluster The list of indices representing variables.
     * @return A list of Node objects corresponding to the indices in the cluster.
     */
    private List<Node> variablesForIndices(List<Integer> cluster) {
        List<Node> _cluster = new ArrayList<>();

        for (int c : cluster) {
            _cluster.add(this.variables.get(c));
        }

        return _cluster;
    }

    /**
     * Returns a list of Node objects corresponding to the indices in the provided cluster.
     *
     * @param clusters The list of indices representing variables, for each cluster.
     * @return A list of Node objects corresponding to the indices in the cluster.
     */
    private List<List<Node>> variablesForIndices(Set<List<Integer>> clusters) {
        List<List<Node>> variables = new ArrayList<>();

        for (List<Integer> cluster : clusters) {
            variables.add(variablesForIndices(cluster));
        }

        return variables;
    }

    /**
     * Determines if a sextet of variables is pure.
     *
     * @param sextet The list of indices representing variables in the sextet.
     * @return True if the sextet is pure, false otherwise.
     */
    private boolean pure(List<Integer> sextet) {
        if (zeroCorr(sextet, 5)) {
            return false;
        }

        if (vanishes(sextet)) {
            for (int o : allVariables()) {
                if (sextet.contains(o)) continue;

                for (int i = 0; i < sextet.size(); i++) {
                    List<Integer> _sextet = new ArrayList<>(sextet);
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

    /**
     * Calculates the chi-square value for a given cluster.
     *
     * @param cluster The list of variables in the cluster.
     * @return The chi-square value for the cluster.
     */
    private double getClusterChiSquare(List<Integer> cluster) {
        SemIm im = estimateClusterModel(cluster);
        return im.getChiSquare();
    }

    /**
     * Estimates the cluster model using the given sextet.
     *
     * @param sextet The list of indices representing variables in the sextet.
     * @return The estimated cluster model.
     */
    private SemIm estimateClusterModel(List<Integer> sextet) {
        Graph g = new EdgeListGraph();
        Node l1 = new GraphNode("L1");
        l1.setNodeType(NodeType.LATENT);
        Node l2 = new GraphNode("L2");
        l2.setNodeType(NodeType.LATENT);
        g.addNode(l1);
        g.addNode(l2);

        for (Integer aQuartet : sextet) {
            Node n = this.variables.get(aQuartet);
            g.addNode(n);
            g.addDirectedEdge(l1, n);
            g.addDirectedEdge(l2, n);
        }

        SemPm pm = new SemPm(g);

        SemEstimator est;

        if (this.dataModel instanceof DataSet) {
            est = new SemEstimator((DataSet) this.dataModel, pm, new SemOptimizerEm());
        } else {
            est = new SemEstimator((CovarianceMatrix) this.dataModel, pm, new SemOptimizerEm());
        }

        return est.estimate();
    }

    /**
     * Constructs a List of six integers representing a sextet. The six integers must be unique.
     *
     * @param n1 the first integer
     * @param n2 the second integer
     * @param n3 the third integer
     * @param n4 the fourth integer
     * @param n5 the fifth integer
     * @param n6 the sixth integer
     * @return a List of six integers representing a sextet
     * @throws IllegalArgumentException if the sextet elements are not unique
     */
    private List<Integer> sextet(int n1, int n2, int n3, int n4, int n5, int n6) {
        List<Integer> sextet = new ArrayList<>();
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

    /**
     * Constructs a List of five integers representing a pentad. The five integers must be unique.
     *
     * @param n1 the first integer
     * @param n2 the second integer
     * @param n3 the third integer
     * @param n4 the fourth integer
     * @param n5 the fifth integer
     * @return a List of five integers representing a pentad
     * @throws IllegalArgumentException if the pentad elements are not unique
     */
    private List<Integer> pentad(int n1, int n2, int n3, int n4, int n5) {
        List<Integer> pentad = new ArrayList<>();
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

        return vanishes(n1, n2, n3, n4, n5, n6)
                && vanishes(n3, n2, n1, n6, n5, n4)
                && vanishes(n4, n5, n6, n1, n2, n3)
                && vanishes(n6, n5, n4, n3, n2, n1);
    }

    /**
     * Checks if the correlation value between all pairs of variables in a given cluster is equal to zero for at least
     * 'n' pairs.
     *
     * @param cluster The list of variables in the cluster.
     * @param n       The minimum number of pairs with zero correlation required for the cluster to be considered.
     * @return True if the cluster meets the requirement, false otherwise.
     */
    private boolean zeroCorr(List<Integer> cluster, int n) {
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

        return count >= n;
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
        Sextad t2 = new Sextad(n1, n5, n6, n2, n3, n4);
        Sextad t3 = new Sextad(n1, n4, n6, n2, n3, n5);
        Sextad t5 = new Sextad(n1, n3, n4, n2, n5, n6);
        Sextad t6 = new Sextad(n1, n3, n5, n2, n4, n6);
        Sextad t7 = new Sextad(n1, n3, n6, n2, n4, n5);
        Sextad t8 = new Sextad(n1, n2, n4, n3, n5, n6);
        Sextad t9 = new Sextad(n1, n2, n5, n3, n4, n6);
        Sextad t10 = new Sextad(n1, n2, n6, n3, n4, n5);

        List<Sextad[]> independents = new ArrayList<>();
        independents.add(new Sextad[]{t1, t2, t3, t5, t6});

        for (Sextad[] sextads : independents) {
            double p = this.test.getPValue(sextads);

            if (Double.isNaN(p)) {
                return false;
            }

            if (p < this.alpha) return false;
        }

        return true;
    }

    /**
     * Converts a set of clusters represented by sets of nodes into a graph representation. Each cluster is represented
     * by a latent node connected to its member nodes.
     *
     * @param clusters The set of clusters represented by sets of nodes.
     * @return The graph representation of the clusters.
     */
    private Graph convertSearchGraphNodes(Set<Set<Node>> clusters) {
        Graph graph = new EdgeListGraph(this.variables);

        List<Node> latents = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++) {
            Node latent = new GraphNode(ClusterUtils.LATENT_PREFIX + (i + 1));
            latent.setNodeType(NodeType.LATENT);
            latents.add(latent);
            graph.addNode(latent);
        }

        List<Set<Node>> _clusters = new ArrayList<>(clusters);

        for (int i = 0; i < latents.size(); i++) {
            for (Node node : _clusters.get(i)) {
                if (!graph.containsNode(node)) graph.addNode(node);
                graph.addDirectedEdge(latents.get(i), node);
            }
        }

        return graph;
    }

    /**
     * Converts a set of clusters represented by sets of integer indices into a graph representation. Each cluster is
     * represented by a latent node connected to its member nodes.
     *
     * @param allClusters The set of clusters represented by sets of integer indices.
     * @return The graph representation of the clusters.
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
     * Calculates the union of all elements in the given set of clusters.
     *
     * @param pureClusters The set of clusters containing integer elements.
     * @return The union of all elements in the given set of clusters.
     */
    private Set<Integer> unionPure(Set<List<Integer>> pureClusters) {
        Set<Integer> unionPure = new HashSet<>();

        for (List<Integer> cluster : pureClusters) {
            unionPure.addAll(cluster);
        }

        return unionPure;
    }

    /**
     * Logs the given message if the toLog parameter is true.
     *
     * @param s     The message to be logged.
     * @param toLog Indicates whether the message should be logged or not.
     */
    private void log(String s, boolean toLog) {
        if (toLog) {
            TetradLogger.getInstance().forceLogMessage(s);
            //            System.out.println(s);
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




