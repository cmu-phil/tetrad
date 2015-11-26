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
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;


/**
 * Implements FindOneFactorCluster by Erich Kummerfeld (adaptation of a two factor
 * sextet algorithm to a one factor tetrad algorithm).
 *
 * @author Joseph Ramsey
 */
public class FindOneFactorClustersWithCausalIndicators {

    private boolean extraShuffle;

    public enum SortKey {size, pValue}

    public enum Algorithm {SAG, GAP}

    public enum AlgType {strict, lax, laxWithSpeedup}

    private ICovarianceMatrix cov;

    // The list of all variables.
    private List<Node> variables;

    private Algorithm algorithm = Algorithm.GAP;
//    private Algorithm algorithm = Algorithm.SAG;

    // The type of the algorithm
    private AlgType algType = AlgType.laxWithSpeedup;

    // The tetrad test--using Ricardo's. Used only for Wishart.
    private TetradTest test;

    // The significance level for the tetrad test.
    private double alpha;

    // The minimum p vaue for output clusters; clusters with lower p values will be ignored.
    private Double clusterMinP = Double.NaN;

    // Wishart or Bollen.
    private TestType testType;

    // The Bollen test. Testing two tetrads simultaneously.
    private DeltaTetradTest deltaTest;

    // independence test.
    private IndependenceTest indTest;

    private DataModel dataModel;

    // The depth of the PC search, -2 if the PC search should not be run.
    private List<List<Node>> clusters = new ArrayList<List<Node>>();

    private int depth = 0; // -2 to turn PC off

    private SortKey sortKey = SortKey.pValue;

    private boolean verbose = false;

    //========================================PUBLIC METHODS====================================//

    public FindOneFactorClustersWithCausalIndicators(ICovarianceMatrix cov, TestType testType, double alpha) {
        this.variables = cov.getVariables();
        this.test = new ContinuousTetradTest(cov, testType, alpha);
        this.indTest = new IndTestFisherZ(cov, alpha);
        this.alpha = alpha;
        this.testType = testType;
        deltaTest = new DeltaTetradTest(cov);
        this.dataModel = cov;
        this.cov = cov;
    }

    public FindOneFactorClustersWithCausalIndicators(DataSet dataSet, TestType testType, double alpha) {
        if (dataSet.isContinuous()) {
            this.variables = dataSet.getVariables();
            this.test = new ContinuousTetradTest(dataSet, testType, alpha);
            this.indTest = new IndTestFisherZ(dataSet, alpha);
            this.alpha = alpha;
            this.testType = testType;
            this.dataModel = dataSet;

            if (testType == TestType.TETRAD_DELTA) {
                deltaTest = new DeltaTetradTest(dataSet);
                deltaTest.setCacheFourthMoments(false);
            }

            this.cov = new CovarianceMatrix(dataSet);
        } else if (dataSet.isDiscrete()) {
            this.variables = dataSet.getVariables();
            this.test = new DiscreteTetradTest(dataSet, alpha);
            this.indTest = new IndTestChiSquare(dataSet, alpha);
            this.alpha = alpha;
            this.testType = testType;
            this.dataModel = dataSet;

            if (testType == TestType.TETRAD_DELTA) {
                deltaTest = new DeltaTetradTest(dataSet);
                deltaTest.setCacheFourthMoments(false);
            }
        }
    }

  /*  public Graph search() {
        TetradLogger.getInstance().log("info", "FOFC alpha = " + alpha + " test = " + testType);
        long start = System.currentTimeMillis();
        Set<Set<Integer>> clusters;
        if (algorithm == FindOneFactorClustersWithCausalIndicators.Algorithm.GAP) {
            clusters = estimateClustersGAP(); // Triples first.
        } else if (algorithm == FindOneFactorClustersWithCausalIndicators.Algorithm.SAG) {
            clusters = estimateClustersSAG(); // Quartets first.
        } else {
            throw new IllegalStateException();
        }
        for (Set<Integer> cluster : clusters) {
            this.clusters.add(variablesForIndices(new ArrayList<Integer>(cluster)));
        }
        ClusterUtils.logClusters(clusters, variables);
        long stop = System.currentTimeMillis();
        long elapsed = stop - start;
        TetradLogger.getInstance().log("ela
        psed", "Elapsed " + elapsed + " ms");
        return convertToGraph(clusters);
    }*/

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public Algorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public AlgType getAlgType() {
        return algType;
    }

    public void setAlgType(AlgType algType) {
        this.algType = algType;
    }


    public void setExtraShuffle(boolean extraShuffle) {
        this.extraShuffle = extraShuffle;
    }

    public boolean isExtraShuffle() {
        return extraShuffle;
    }

    public Set<Set<Integer>> ESeeds = new HashSet<Set<Integer>>();

    public List<Set<Integer>> CSeeds = new ArrayList<Set<Integer>>();

    public double CIparameter = .8;

    //========================================PRIVATE METHODS====================================//

    private Set<List<Set<Integer>>> estimateClustersGAP() {
        findSeeds();
        Set<Set<Integer>> ESeeds = findESeeds();
        List<Set<Integer>> CSeeds = findCSeeds();
        return combineClusters(ESeeds,CSeeds);

    }

    private Set<List<Set<Integer>>> combineClusters(Set<Set<Integer>> ESeeds,List<Set<Integer>> CSeeds) {
        Set<Set<Integer>> EClusters = finishESeeds(ESeeds);
        Set<Integer> Cs = new HashSet();
        for (int i = 0; i < variables.size(); i++) Cs.add(i);
        Set<Integer> Es = new HashSet();
        for (Set<Integer> ECluster : EClusters) Es.addAll(ECluster);
        Cs.removeAll(Es);
        List<List<Set<Integer>>> Clusters = new ArrayList();
        for (Set<Integer> ECluster : EClusters) {
            List<Set<Integer>> newCluster = new ArrayList<Set<Integer>>();
            newCluster.add(1,ECluster);
            Clusters.add(newCluster);
        }
        List<Set<Integer>> EClustersArray = new ArrayList<Set<Integer>>();
        for (Set<Integer> ECluster : EClusters) EClustersArray.add(ECluster);
        for (Integer c : Cs) {
            int match = -1;
            int overlap = 0;
            boolean pass = false;
            for (int i = 0; i < EClusters.size(); i++) {
                Set<Integer> ECluster = EClustersArray.get(i);
                Set<Integer> intersection = ECluster;
                intersection.retainAll(CSeeds.get(c));
                int _overlap = intersection.size();
                if (_overlap > overlap) {
                    overlap = _overlap;
                    match = i;
                    if (overlap/ECluster.size() > CIparameter) {
                        pass = true;
                    }
                }
            }
            if (pass) {
                List<Set<Integer>> modCluster = new ArrayList<Set<Integer>>();
                Set<Integer> newCs = Clusters.get(match).get(0);
                newCs.add(c);
                modCluster.add(newCs);
                modCluster.add(EClustersArray.get(match));
                Clusters.set(match,modCluster);
            }
        }
        Set<List<Set<Integer>>> ClusterSet = new HashSet<List<Set<Integer>>>(Clusters);
        return ClusterSet;
    }

    private Void findSeeds() {
        Tetrad tetrad = null;
        List<Node> empty = new ArrayList();
        if (variables.size() < 4) {
            Set<Set<Integer>> ESeeds = new HashSet<Set<Integer>>();
        }

        Map<Node, Set<Node>> adjacencies;

        if (depth == -2) {
            adjacencies = new HashMap<Node, Set<Node>>();

            for (Node node : variables) {
                HashSet<Node> _nodes = new HashSet<Node>(variables);
                _nodes.remove(node);
                adjacencies.put(node, _nodes);
            }
        } else {
//            System.out.println("Running PC adjacency search...");
            Graph graph = new EdgeListGraph(variables);
            Fas fas = new Fas(graph, indTest);
            fas.setVerbose(false);
            fas.setDepth(depth);     // 1?
            adjacencies = fas.searchMapOnly();
//            System.out.println("...done.");
        }

        List<Integer> allVariables = new ArrayList<Integer>();
        for (int i = 0; i < variables.size(); i++) allVariables.add(i);

        log("Finding seeds.", true);

        ChoiceGenerator gen = new ChoiceGenerator(allVariables.size(), 3);
        int[] choice;
        CHOICE:
        while ((choice = gen.next()) != null) {
            int n1 = allVariables.get(choice[0]);
            int n2 = allVariables.get(choice[1]);
            int n3 = allVariables.get(choice[2]);
            Node v1 = variables.get(choice[0]);
            Node v2 = variables.get(choice[1]);
            Node v3 = variables.get(choice[2]);

            Set<Integer> triple = triple(n1, n2, n3);

            if (!clique(triple, adjacencies)) {
                continue;
            }

            boolean EPure = true;
            boolean CPure1 = true;
            boolean CPure2 = true;
            boolean CPure3 = true;

            for (int o : allVariables) {
                if (triple.contains(o)) {
                    continue;
                }

                Node v4 = variables.get(o);
                tetrad = new Tetrad(v1, v2, v3, v4);

                if (deltaTest.getPValue(tetrad) > alpha) {
                    EPure = false;
                    if (indTest.isDependent(v1,v4,empty)) {
                        CPure1 = false;
                    }
                    if (indTest.isDependent(v2,v4,empty)) {
                        CPure2 = false;
                    }
                }
                tetrad = new Tetrad(v1, v3, v2, v4);
                if (deltaTest.getPValue(tetrad) > alpha) {
                    EPure = false;
                    if (indTest.isDependent(v3,v4,empty)) {
                        CPure3 = false;
                    }
                }

                if (!(EPure||CPure1||CPure2||CPure3)) {
                    continue CHOICE;
                }
            }

            HashSet<Integer> _cluster = new HashSet<Integer>(triple);

            if (verbose) {
                log("++" + variablesForIndices(new ArrayList<Integer>(triple)), false);
            }

            if (EPure) {
                ESeeds.add(_cluster);
            }
            if (!EPure) {
                if (CPure1) {
                    Set<Integer> _cluster1 = new HashSet<Integer>(n2, n3);
                    _cluster1.addAll(CSeeds.get(n1));
                    CSeeds.set(n1, _cluster1);
                }
                if (CPure2) {
                    Set<Integer> _cluster2 = new HashSet<Integer>(n1, n3);
                    _cluster2.addAll(CSeeds.get(n2));
                    CSeeds.set(n2, _cluster2);
                }
                if (CPure3) {
                    Set<Integer> _cluster3 = new HashSet<Integer>(n1, n2);
                    _cluster3.addAll(CSeeds.get(n3));
                    CSeeds.set(n3, _cluster3);
                }
            }
        }
        return null;
    }

    private Set<Set<Integer>> findESeeds() {
        return(ESeeds);
    }

    private List<Set<Integer>> findCSeeds() {
        return(CSeeds);
    }

    private Set<Set<Integer>> finishESeeds(Set<Set<Integer>> ESeeds) {
        log("Growing Effect Seeds.", true);
        Set<Set<Integer>> grown = new HashSet<Set<Integer>>();

        List<Integer> _variables = new ArrayList<Integer>();
        for (int i = 0; i < variables.size(); i++) _variables.add(i);


        // Lax grow phase with speedup.
        if (algType == AlgType.lax) {
            Set<Integer> t = new HashSet<Integer>();
            int count = 0;
            int total = ESeeds.size();

            do {
                if (!ESeeds.iterator().hasNext()) {
                    break;
                }

                Set<Integer> cluster = ESeeds.iterator().next();
                Set<Integer> _cluster = new HashSet<Integer>(cluster);

                if (extraShuffle) {
                    Collections.shuffle(_variables);
                }

                for (int o : _variables) {
                    if (_cluster.contains(o)) continue;

                    List<Integer> _cluster2 = new ArrayList<Integer>(_cluster);
                    int rejected = 0;
                    int accepted = 0;

                    ChoiceGenerator gen = new ChoiceGenerator(_cluster2.size(), 2);
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        int n1 = _cluster2.get(choice[0]);
                        int n2 = _cluster2.get(choice[1]);

                        t.clear();
                        t.add(n1);
                        t.add(n2);
                        t.add(o);

                        if (!ESeeds.contains(t)) {
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
                ChoiceGenerator gen2 = new ChoiceGenerator(_cluster.size(), 3);
                int[] choice2;
                List<Integer> _cluster3 = new ArrayList<Integer>(_cluster);

                while ((choice2 = gen2.next()) != null) {
                    int n1 = _cluster3.get(choice2[0]);
                    int n2 = _cluster3.get(choice2[1]);
                    int n3 = _cluster3.get(choice2[2]);

                    t.clear();
                    t.add(n1);
                    t.add(n2);
                    t.add(n3);

                    ESeeds.remove(t);
                }

                if (verbose) {
                    System.out.println("Grown " + (++count) + " of " + total + ": " + variablesForIndices(new ArrayList<Integer>(_cluster)));
                }
                grown.add(_cluster);
            } while (!ESeeds.isEmpty());
        }

        // Lax grow phase without speedup.
        if (algType == AlgType.laxWithSpeedup) {
            int count = 0;
            int total = ESeeds.size();

            // Optimized lax version of grow phase.
            for (Set<Integer> cluster : new HashSet<Set<Integer>>(ESeeds)) {
                Set<Integer> _cluster = new HashSet<Integer>(cluster);

                if (extraShuffle) {
                    Collections.shuffle(_variables);
                }

                for (int o : _variables) {
                    if (_cluster.contains(o)) continue;

                    List<Integer> _cluster2 = new ArrayList<Integer>(_cluster);
                    int rejected = 0;
                    int accepted = 0;
//
                    ChoiceGenerator gen = new ChoiceGenerator(_cluster2.size(), 2);
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        int n1 = _cluster2.get(choice[0]);
                        int n2 = _cluster2.get(choice[1]);

                        Set<Integer> triple = triple(n1, n2, o);

                        if (!ESeeds.contains(triple)) {
                            rejected++;
                        } else {
                            accepted++;
                        }
                    }
//
                    if (rejected > accepted) {
                        continue;
                    }

//                    System.out.println("Adding " + o  + " to " + cluster);
                    _cluster.add(o);
                }

                for (Set<Integer> c : new HashSet<Set<Integer>>(ESeeds)) {
                    if (_cluster.containsAll(c)) {
                        ESeeds.remove(c);
                    }
                }

                if (verbose) {
                    System.out.println("Grown " + (++count) + " of " + total + ": " + _cluster);
                }

                grown.add(_cluster);
            }
        }

        // Strict grow phase.
        if (algType == AlgType.strict) {
            Set<Integer> t = new HashSet<Integer>();
            int count = 0;
            int total = ESeeds.size();

            do {
                if (!ESeeds.iterator().hasNext()) {
                    break;
                }

                Set<Integer> cluster = ESeeds.iterator().next();
                Set<Integer> _cluster = new HashSet<Integer>(cluster);

                if (extraShuffle) {
                    Collections.shuffle(_variables);
                }

                VARIABLES:
                for (int o : _variables) {
                    if (_cluster.contains(o)) continue;

                    List<Integer> _cluster2 = new ArrayList<Integer>(_cluster);

                    ChoiceGenerator gen = new ChoiceGenerator(_cluster2.size(), 2);
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        int n1 = _cluster2.get(choice[0]);
                        int n2 = _cluster2.get(choice[1]);

                        t.clear();
                        t.add(n1);
                        t.add(n2);
                        t.add(o);

                        if (!ESeeds.contains(t)) {
                            continue VARIABLES;
                        }

//                        if (avgSumLnP(new ArrayList<Integer>(t)) < -10) continue CLUSTER;
                    }

                    _cluster.add(o);
                }

                // This takes out all pure clusters that are subsets of _cluster.
                ChoiceGenerator gen2 = new ChoiceGenerator(_cluster.size(), 3);
                int[] choice2;
                List<Integer> _cluster3 = new ArrayList<Integer>(_cluster);

                while ((choice2 = gen2.next()) != null) {
                    int n1 = _cluster3.get(choice2[0]);
                    int n2 = _cluster3.get(choice2[1]);
                    int n3 = _cluster3.get(choice2[2]);

                    t.clear();
                    t.add(n1);
                    t.add(n2);
                    t.add(n3);

                    ESeeds.remove(t);
                }

                if (verbose) {
                    System.out.println("Grown " + (++count) + " of " + total + ": " + _cluster);
                }
                grown.add(_cluster);
            } while (!ESeeds.isEmpty());
        }

        // Optimized pick phase.
        log("Choosing among grown Effect Clusters.", true);

        for (Set<Integer> l : grown) {
            ArrayList<Integer> _l = new ArrayList<Integer>(l);
            Collections.sort(_l);
            if (verbose) {
                log("Grown: " + variablesForIndices(_l), false);
            }
        }

        Set<Set<Integer>> out = new HashSet<Set<Integer>>();

        List<Set<Integer>> list = new ArrayList<Set<Integer>>(grown);

//        final Map<Set<Integer>, Double> pValues = new HashMap<Set<Integer>, Double>();
//
//        for (Set<Integer> o : grown) {
//            pValues.put(o, getP(new ArrayList<Integer>(o)));
//        }

        Collections.sort(list, new Comparator<Set<Integer>>() {
            @Override
            public int compare(Set<Integer> o1, Set<Integer> o2) {
//                if (o1.size() == o2.size()) {
//                    double chisq1 = pValues.get(o1);
//                    double chisq2 = pValues.get(o2);
//                    return Double.compare(chisq2, chisq1);
//                }

                return o2.size() - o1.size();
            }
        });

//        for (Set<Integer> o : list) {
//            if (pValues.get(o) < alpha) continue;
//            System.out.println(variablesForIndices(new ArrayList<Integer>(o)) + "  p = " + pValues.get(o));
//        }

        Set<Integer> all = new HashSet<Integer>();

        CLUSTER:
        for (Set<Integer> cluster : list) {
//            if (pValues.get(cluster) < alpha) continue;

            for (Integer i : cluster) {
                if (all.contains(i)) continue CLUSTER;
            }

            out.add(cluster);

//            if (getPMulticluster(out) < alpha) {
//                out.remove(cluster);
//                continue;
//            }

            all.addAll(cluster);
        }

        return out;
    }

    private double getP(List<Integer> o) {
        if (o.size() == 3) return 0;

        double max = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < 1; i++) {
            double c = getP(new ArrayList<Integer>(o), 3);
            if (c > max) max = c;
        }

        return max;
    }

    private double getP(List<Integer> cluster, int numRestarts) {
        if (true) {
            Node latent = new GraphNode("L");
            latent.setNodeType(NodeType.LATENT);
            Graph g = new EdgeListGraph();
            g.addNode(latent);
            List<Node> measures = variablesForIndices(cluster);
            for (Node node : measures) {
                g.addNode(node);
                g.addDirectedEdge(latent, node);
            }
            SemPm pm = new SemPm(g);

//            pm.fixOneLoadingPerLatent();

            SemOptimizerPowell semOptimizer = new SemOptimizerPowell();
            semOptimizer.setNumRestarts(numRestarts);

            SemEstimator est = new SemEstimator(cov, pm, semOptimizer);
            est.setScoreType(SemIm.ScoreType.Fgls);
            est.estimate();
            return est.getEstimatedSem().getPValue();
        } else {
            double max = Double.NEGATIVE_INFINITY;

            for (int i = 0; i < numRestarts; i++) {
                Mimbuild2 mimbuild = new Mimbuild2();

                List<List<Node>> clusters1 = new ArrayList<List<Node>>();
                clusters1.add(variablesForIndices(new ArrayList<Integer>(cluster)));

                List<String> names = new ArrayList<String>();
                names.add("L");

                mimbuild.search(clusters1, names, cov);

                double c = mimbuild.getpValue();
                if (c > max) max = c;
            }

            return max;
        }
    }

    private double getPMulticluster(Set<Set<Integer>> o) {
        List<List<Integer>> list = new ArrayList<List<Integer>>();

        for (Set<Integer> _o : o) {
            list.add(new ArrayList<Integer>(_o));
        }
        return getPMulticluster(list, 3);
    }

    private double getPMulticluster(List<List<Integer>> clusters, int numRestarts) {
        if (false) {
            Graph g = new EdgeListGraph();
            List<Node> latents = new ArrayList<Node>();
            for (int i = 0; i < clusters.size(); i++) {
                GraphNode latent = new GraphNode("L" + i);
                latent.setNodeType(NodeType.LATENT);
                latents.add(latent);
                g.addNode(latent);

                List<Node> cluster = variablesForIndices(clusters.get(i));

                for (int j = 0; j < cluster.size(); j++) {
                    g.addNode(cluster.get(j));
                    g.addDirectedEdge(latent, cluster.get(j));
                }
            }
            SemPm pm = new SemPm(g);

//            pm.fixOneLoadingPerLatent();

            SemOptimizerPowell semOptimizer = new SemOptimizerPowell();
            semOptimizer.setNumRestarts(numRestarts);

            SemEstimator est = new SemEstimator(cov, pm, semOptimizer);
            est.setScoreType(SemIm.ScoreType.Fgls);
            est.estimate();
            return est.getEstimatedSem().getPValue();
        } else {
            double max = Double.NEGATIVE_INFINITY;

            for (int i = 0; i < numRestarts; i++) {
                Mimbuild2 mimbuild = new Mimbuild2();

                List<List<Node>> _clusters = new ArrayList<List<Node>>();

                for (List<Integer> _cluster : clusters) {
                    _clusters.add(variablesForIndices(_cluster));
                }

                List<String> names = new ArrayList<String>();

                for (int j = 0; j < clusters.size(); j++) {
                    names.add("L" + j);
                }

                mimbuild.search(_clusters, names, cov);

                double c = mimbuild.getpValue();
                if (c > max) max = c;
            }

            return max;
        }
    }

    private void log(String s, boolean toLog) {
        if (toLog) {
            TetradLogger.getInstance().log("info", s);
        }

//        System.out.println(s);
    }

//    private Set<Set<Integer>> findThreeClusters() {
////        Graph graph = new EdgeListGraph(variables);
////        Fas fas = new Fas(graph, indTest);
////        fas.setDepth(0);     // 1?
////        Map<Node, Set<Node>> adjacencies = fas.searchMapOnly();
//
//        List<Integer> allVariables = new ArrayList<Integer>();
//        for (int i = 0; i < this.variables.size(); i++) allVariables.add(i);
//
//        if (allVariables.size() < 4) {
//            return new HashSet<Set<Integer>>();
//        }
//
//        ChoiceGenerator gen = new ChoiceGenerator(allVariables.size(), 3);
//        int[] choice;
//        Set<Set<Integer>> threeClusters = new HashSet<Set<Integer>>();
//
//        CHOICE:
//        while ((choice = gen.next()) != null) {
//            int n1 = allVariables.get(choice[0]);
//            int n2 = allVariables.get(choice[1]);
//            int n3 = allVariables.get(choice[2]);
//
//            Set<Integer> triple = triple(n1, n2, n3);
//
////            if (!clique(triple, adjacencies)) {
////                continue;
////            }
//
//            int rejected = 0;
//
//            for (int o : allVariables) {
//                if (triple.contains(o)) {
//                    continue;
//                }
//
//                Set<Integer> cluster = quartet(o, n1, n2, n3);
//
//                if (!quartetVanishes(cluster)) {
//                    rejected++;
//                }
//
//                if (rejected > variables.size() * 0.1) {
//                    continue CHOICE;
//                }
////
////                if (rejected > 0) {
////                    continue CHOICE;
////                }
//            }
//
//            System.out.println("++ " + triple);
//
//            threeClusters.add(triple);
//        }
//
//        return threeClusters;
//    }

//    private Set<Set<Integer>> combineThreeClusters(Set<Set<Integer>> threeClusters) {
//        Set<Set<Integer>> grown = new HashSet<Set<Integer>>();
//        List<Integer> _variables = new ArrayList<Integer>();
//        for (int i = 0; i < variables.size(); i++) _variables.add(i);
//
//        for (Set<Integer> cluster : threeClusters) {
//            Set<Integer> _cluster = new HashSet<Integer>(cluster);
//
//            // Strict.
//            for (int o : _variables) {
//                _cluster.add(o);
//
//                List<Integer> _cluster2 = new ArrayList<Integer>(_cluster);
//
//                ChoiceGenerator gen = new ChoiceGenerator(_cluster2.size(), 3);
//                int[] choice;
//                boolean rejected = false;
//
//                while ((choice = gen.next()) != null) {
//                    int n1 = _cluster2.get(choice[0]);
//                    int n2 = _cluster2.get(choice[1]);
//                    int n3 = _cluster2.get(choice[2]);
//
//                    Set<Integer> triple = triple(n1, n2, n3);
//
//                    if (!triple.contains(o)) continue;
//
//                    if (!threeClusters.contains(triple)) {
//                        rejected = true;
//                        break;
//                    }
//                }
//
//                if (rejected) {
//                    _cluster.remove(o);
//                }
//            }
//
//            grown.add(_cluster);
//        }
//
//        List<Set<Integer>> _grown = new ArrayList<Set<Integer>>(grown);
//        final Map<Set<Integer>, Double> pValues = new HashMap<Set<Integer>, Double>();
//
//        if (!Double.isNaN(clusterMinP) || sortKey == SortKey.pValue) {
//            for (Set<Integer> g : _grown) {
//                double p = getClusterP2(variablesForIndices(g));
//                pValues.put(g, p);
//            }
//        }
//
//        // Print the grown clusters.
//        Collections.sort(_grown, new Comparator<Set<Integer>>() {
//            public int compare(Set<Integer> o1, Set<Integer> o2) {
//                if (sortKey == SortKey.pValue) {
//                    Double p1 = pValues.get(o2);
//                    Double p2 = pValues.get(o1);
//                    return Double.compare(Double.isNaN(p1) ? -1 : p1, Double.isNaN(p2) ? -1 : p2);
//                } else if (sortKey == SortKey.size) {
//                    return o2.size() - o1.size();
//                } else {
//                    throw new IllegalStateException();
//                }
//            }
//        });
//
////        System.out.println("Grown");
////        for (Set<Integer> l : _grown) {
////            List<Node> nodes = variablesForIndices(l);
////
////            for (int i = 0; i < nodes.size(); i++) {
////                System.out.print(nodes.get(i));
////
////                if (i < nodes.size() - 1) {
////                    System.out.print(" ");
////                }
////            }
////
////            if (sortKey == SortKey.size) {
////                System.out.println();
////            }
////            if (sortKey == SortKey.pValue) {
////                System.out.println("\t" + pValues.get(l));
////            }
////        }
//
//        Set<Set<Integer>> out = new HashSet<Set<Integer>>();
//
//        while (!_grown.isEmpty()) {
//            Set<Integer> maxCluster = _grown.remove(0);
//            if (!Double.isNaN(clusterMinP) && maxCluster.size() == 3) {
//                _grown.remove(maxCluster);
//                continue;
//            }
//            if (!Double.isNaN(clusterMinP) && pValues.get(maxCluster) < clusterMinP) {
//                grown.remove(maxCluster);
//                continue;
//            }
//            out.add(maxCluster);
//
//            // Remove from grown any cluster that intersects it.
//            for (Set<Integer> _cluster : new HashSet<Set<Integer>>(_grown)) {
//                Set<Integer> cluster2 = new HashSet<Integer>(_cluster);
//                cluster2.retainAll(maxCluster);
//
//                if (!cluster2.isEmpty()) {
//                    _grown.remove(_cluster);
//                }
//            }
//        }
//
////        NumberFormat nf = new DecimalFormat("0.0000");
////
////        // Print the output clusters.
////        System.out.println("Output clusters:");
////
////        for (Set<Integer> l : out) {
////            List<Node> nodes = variablesForIndices(l);
////
////            for (int i = 0; i < nodes.size(); i++) {
////                System.out.print(nodes.get(i));
////
////                if (i < nodes.size() - 1) {
////                    System.out.print(" ");
////                }
////            }
////
////            if (sortKey == SortKey.size) {
////                System.out.println();
////            }
////            else if (sortKey == SortKey.pValue) {
////                System.out.println("\t" + nf.format(pValues.get(l)));
////            }
////        }
//
//        return out;
//    }

    // Quartets first , then triples.
    private Set<Set<Integer>> estimateClustersSAG() {
        Map<Node, Set<Node>> adjacencies;

        if (depth == -2) {
            adjacencies = new HashMap<Node, Set<Node>>();

            for (Node node : variables) {
                HashSet<Node> _nodes = new HashSet<Node>(variables);
                _nodes.remove(node);
                adjacencies.put(node, _nodes);
            }
        } else {
            System.out.println("Running PC adjacency search...");
            Graph graph = new EdgeListGraph(variables);
            Fas fas = new Fas(graph, indTest);
            fas.setDepth(depth);     // 1?
            adjacencies = fas.searchMapOnly();
            System.out.println("...done.");
        }

        List<Integer> _variables = new ArrayList<Integer>();
        for (int i = 0; i < variables.size(); i++) _variables.add(i);

        Set<Set<Integer>> pureClusters = findPureClusters(_variables, adjacencies);
        for (Set<Integer> cluster : pureClusters) _variables.removeAll(cluster);
        Set<Set<Integer>> mixedClusters = findMixedClusters(_variables, unionPure(pureClusters), adjacencies);
        Set<Set<Integer>> allClusters = new HashSet<Set<Integer>>(pureClusters);
        allClusters.addAll(mixedClusters);
        return allClusters;

    }

    // Finds clusters of size 4 or higher.
    private Set<Set<Integer>> findPureClusters(List<Integer> _variables, Map<Node, Set<Node>> adjacencies) {
//        System.out.println("Original variables = " + variables);

        Set<Set<Integer>> clusters = new HashSet<Set<Integer>>();
        List<Integer> allVariables = new ArrayList<Integer>();
        for (int i = 0; i < this.variables.size(); i++) allVariables.add(i);

        VARIABLES:
        while (!_variables.isEmpty()) {
            if (_variables.size() < 4) break;

            for (int x : _variables) {
                Node nodeX = variables.get(x);
                List<Node> adjX = new ArrayList<Node>(adjacencies.get(nodeX));
                adjX.retainAll(variablesForIndices(new ArrayList<Integer>(_variables)));

                for (Node node : new ArrayList<Node>(adjX)) {
                    if (adjacencies.get(node).size() < 3) {
                        adjX.remove(node);
                    }
                }

                if (adjX.size() < 3) {
                    continue;
                }

                ChoiceGenerator gen = new ChoiceGenerator(adjX.size(), 3);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    Node nodeY = adjX.get(choice[0]);
                    Node nodeZ = adjX.get(choice[1]);
                    Node nodeW = adjX.get(choice[2]);

                    int y = variables.indexOf(nodeY);
                    int w = variables.indexOf(nodeW);
                    int z = variables.indexOf(nodeZ);

                    Set<Integer> cluster = quartet(x, y, z, w);

                    if (!clique(cluster, adjacencies)) {
                        continue;
                    }

                    // Note that purity needs to be assessed with respect to all of the variables in order to
                    // remove all latent-measure impurities between pairs of latents.
                    if (pure(cluster, allVariables)) {

//                        Collections.shuffle(_variables);

                        O:
                        for (int o : _variables) {
                            if (cluster.contains(o)) continue;
                            cluster.add(o);
                            List<Integer> _cluster = new ArrayList<Integer>(cluster);

                            if (!clique(cluster, adjacencies)) {
                                cluster.remove(o);
                                continue O;
                            }

//                            if (!allVariablesDependent(cluster)) {
//                                cluster.remove(o);
//                                continue O;
//                            }

                            ChoiceGenerator gen2 = new ChoiceGenerator(_cluster.size(), 4);
                            int[] choice2;
                            int count = 0;

                            while ((choice2 = gen2.next()) != null) {
                                int x2 = _cluster.get(choice2[0]);
                                int y2 = _cluster.get(choice2[1]);
                                int z2 = _cluster.get(choice2[2]);
                                int w2 = _cluster.get(choice2[3]);

                                Set<Integer> quartet = quartet(x2, y2, z2, w2);

                                // Optimizes for large clusters.
                                if (quartet.contains(o)) {
                                    if (++count > 50) continue O;
                                }

                                if (quartet.contains(o) && !pure(quartet, allVariables)) {
                                    cluster.remove(o);
                                    continue O;
                                }
                            }
                        }

                        System.out.println("Cluster found: " + variablesForIndices(new ArrayList<Integer>(cluster)));
                        clusters.add(cluster);
                        _variables.removeAll(cluster);

                        continue VARIABLES;
                    }
                }
            }

            break;
        }

        return clusters;
    }

    // Trying to optimize the search for 4-cliques a bit.
    private Set<Set<Integer>> findPureClusters2(List<Integer> _variables, Map<Node, Set<Node>> adjacencies) {
        System.out.println("Original variables = " + variables);

        Set<Set<Integer>> clusters = new HashSet<Set<Integer>>();
        List<Integer> allVariables = new ArrayList<Integer>();
        Set<Node> foundVariables = new HashSet<Node>();
        for (int i = 0; i < this.variables.size(); i++) allVariables.add(i);

        for (int x : _variables) {
            Node nodeX = variables.get(x);
            if (foundVariables.contains(nodeX)) continue;

            List<Node> adjX = new ArrayList<Node>(adjacencies.get(nodeX));
            adjX.removeAll(foundVariables);

            if (adjX.size() < 3) continue;

            for (Node nodeY : adjX) {
                if (foundVariables.contains(nodeY)) continue;

                List<Node> commonXY = new ArrayList<Node>(adjacencies.get(nodeY));
                commonXY.retainAll(adjX);
                commonXY.removeAll(foundVariables);

                for (Node nodeZ : commonXY) {
                    if (foundVariables.contains(nodeZ)) continue;

                    List<Node> commonXZ = new ArrayList<Node>(commonXY);
                    commonXZ.retainAll(adjacencies.get(nodeZ));
                    commonXZ.removeAll(foundVariables);

                    for (Node nodeW : commonXZ) {
                        if (foundVariables.contains(nodeW)) continue;

                        if (!adjacencies.get(nodeY).contains(nodeW)) {
                            continue;
                        }

                        int y = variables.indexOf(nodeY);
                        int w = variables.indexOf(nodeW);
                        int z = variables.indexOf(nodeZ);

                        Set<Integer> cluster = quartet(x, y, z, w);

                        // Note that purity needs to be assessed with respect to all of the variables in order to
                        // remove all latent-measure impurities between pairs of latents.
                        if (pure(cluster, allVariables)) {

                            O:
                            for (int o : _variables) {
                                if (cluster.contains(o)) continue;
                                cluster.add(o);

                                if (!clique(cluster, adjacencies)) {
                                    cluster.remove(o);
                                    continue O;
                                }

//                                if (!allVariablesDependent(cluster)) {
//                                    cluster.remove(o);
//                                    continue O;
//                                }

                                List<Integer> _cluster = new ArrayList<Integer>(cluster);

                                ChoiceGenerator gen2 = new ChoiceGenerator(_cluster.size(), 4);
                                int[] choice2;
                                int count = 0;

                                while ((choice2 = gen2.next()) != null) {
                                    int x2 = _cluster.get(choice2[0]);
                                    int y2 = _cluster.get(choice2[1]);
                                    int z2 = _cluster.get(choice2[2]);
                                    int w2 = _cluster.get(choice2[3]);

                                    Set<Integer> quartet = quartet(x2, y2, z2, w2);

                                    // Optimizes for large clusters.
                                    if (quartet.contains(o)) {
                                        if (++count > 2) continue O;
                                    }

                                    if (quartet.contains(o) && !pure(quartet, allVariables)) {
                                        cluster.remove(o);
                                        continue O;
                                    }
                                }
                            }

                            System.out.println("Cluster found: " + variablesForIndices(new ArrayList<Integer>(cluster)));
                            clusters.add(cluster);
                            foundVariables.addAll(variablesForIndices(new ArrayList<Integer>(cluster)));
                        }
                    }
                }
            }
        }

        return clusters;
    }

    //  Finds clusters of size 3.
    private Set<Set<Integer>> findMixedClusters(List<Integer> remaining, Set<Integer> unionPure, Map<Node, Set<Node>> adjacencies) {
        Set<Set<Integer>> threeClusters = new HashSet<Set<Integer>>();

        if (unionPure.isEmpty()) {
            return new HashSet<Set<Integer>>();
        }

        REMAINING:
        while (true) {
            if (remaining.size() < 3) break;

            ChoiceGenerator gen = new ChoiceGenerator(remaining.size(), 3);
            int[] choice;

            while ((choice = gen.next()) != null) {
                int y = remaining.get(choice[0]);
                int z = remaining.get(choice[1]);
                int w = remaining.get(choice[2]);

                Set<Integer> cluster = new HashSet<Integer>();
                cluster.add(y);
                cluster.add(z);
                cluster.add(w);

//                if (!allVariablesDependent(cluster)) {
//                    continue;
//                }

                if (!clique(cluster, adjacencies)) {
                    continue;
                }

                // Check all x as a cross check; really only one should be necessary.
                boolean allX = true;

                for (int x : unionPure) {
                    Set<Integer> _cluster = new HashSet<Integer>(cluster);
                    _cluster.add(x);

                    if (!quartetVanishes(_cluster) || !significant(new ArrayList<Integer>(_cluster))) {
                        allX = false;
                        break;
                    }
                }

                if (allX) {
                    threeClusters.add(cluster);
                    unionPure.addAll(cluster);
                    remaining.removeAll(cluster);

                    System.out.println("3-cluster found: " + variablesForIndices(new ArrayList<Integer>(cluster)));

                    continue REMAINING;
                }
            }

            break;
        }

        return threeClusters;
    }

    private boolean clique(Set<Integer> cluster, Map<Node, Set<Node>> adjacencies) {
        List<Integer> _cluster = new ArrayList<Integer>(cluster);

        for (int i = 0; i < cluster.size(); i++) {
            for (int j = i + 1; j < cluster.size(); j++) {
                Node nodei = variables.get(_cluster.get(i));
                Node nodej = variables.get(_cluster.get(j));

                if (!adjacencies.get(nodei).contains(nodej)) {
                    return false;
                }
            }
        }

        return true;
    }

    private List<Node> variablesForIndices(List<Integer> cluster) {
        List<Node> _cluster = new ArrayList<Node>();

        for (int c : cluster) {
            _cluster.add(variables.get(c));
        }

        Collections.sort(_cluster);

        return _cluster;
    }


    private boolean pure(Set<Integer> quartet, List<Integer> variables) {
        if (quartetVanishes(quartet)) {
            for (int o : variables) {
                if (quartet.contains(o)) continue;

                for (int p : quartet) {
                    Set<Integer> _quartet = new HashSet<Integer>(quartet);
                    _quartet.remove(p);
                    _quartet.add(o);

                    if (!quartetVanishes(_quartet)) {
                        return false;
                    }
                }
            }

            return significant(new ArrayList<Integer>(quartet));
        }

        return false;
    }

    private Set<Integer> quartet(int x, int y, int z, int w) {
        Set<Integer> set = new HashSet<Integer>();
        set.add(x);
        set.add(y);
        set.add(z);
        set.add(w);

        if (set.size() < 4)
            throw new IllegalArgumentException("Quartet elements must be unique: <" + x + ", " + y + ", " + z + ", " + w + ">");

        return set;
    }

    private boolean quartetVanishes(Set<Integer> quartet) {
        if (quartet.size() != 4) throw new IllegalArgumentException("Expecting a quartet, size = " + quartet.size());

        Iterator<Integer> iter = quartet.iterator();
        int x = iter.next();
        int y = iter.next();
        int z = iter.next();
        int w = iter.next();

        return testVanishing(x, y, z, w);
    }

    private boolean testVanishing(int x, int y, int z, int w) {
        if (testType == TestType.TETRAD_DELTA) {
            Tetrad t1 = new Tetrad(variables.get(x), variables.get(y), variables.get(z), variables.get(w));
            Tetrad t2 = new Tetrad(variables.get(x), variables.get(y), variables.get(w), variables.get(z));
            double p = deltaTest.getPValue(t1, t2);
            return p > alpha;
        } else {
            return test.tetradHolds(x, y, z, w) && test.tetradHolds(x, y, w, z);
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

    private Graph convertToGraph(Set<Set<Integer>> allClusters) {
        Set<Set<Node>> _clustering = new HashSet<Set<Node>>();

        for (Set<Integer> cluster : allClusters) {
            Set<Node> nodes = new HashSet<Node>();

            for (int i : cluster) {
                nodes.add(variables.get(i));
            }

            _clustering.add(nodes);
        }

        return convertSearchGraphNodes(_clustering);
    }

    private Set<Integer> unionPure(Set<Set<Integer>> pureClusters) {
        Set<Integer> unionPure = new HashSet<Integer>();

        for (Set<Integer> cluster : pureClusters) {
            unionPure.addAll(cluster);
        }

        return unionPure;
    }

    private boolean significant(List<Integer> cluster) {
        double p = getClusterP2(variablesForIndices(new ArrayList<Integer>(cluster)));

        return p > alpha;
    }

    private double getClusterP2(List<Node> c) {
        Graph g = new EdgeListGraph(c);
        Node l = new GraphNode("L");
        l.setNodeType(NodeType.LATENT);
        g.addNode(l);

        for (Node n : c) {
            g.addDirectedEdge(l, n);
        }

        SemPm pm = new SemPm(g);
        SemEstimator est;
        if (dataModel instanceof DataSet) {
            est = new SemEstimator((DataSet) dataModel, pm, new SemOptimizerEm());
        } else {
            est = new SemEstimator((CovarianceMatrix) dataModel, pm, new SemOptimizerEm());
        }
        SemIm estIm = est.estimate();
        double pValue = estIm.getPValue();
        return pValue == 1 ? Double.NaN : pValue;
    }


    private Set<Integer> triple(int n1, int n2, int n3) {
        Set<Integer> triple = new HashSet<Integer>();
        triple.add(n1);
        triple.add(n2);
        triple.add(n3);

        if (triple.size() < 3)
            throw new IllegalArgumentException("Triple elements must be unique: <" + n1 + ", " + n2 + ", " + n3 + ">");

        return triple;
    }

    public List<List<Node>> getClusters() {
        return clusters;
    }

    public SortKey getSortKey() {
        return sortKey;
    }

    public void setSortKey(SortKey sortKey) {
        this.sortKey = sortKey;
    }

    /**
     * Clusters with p value balow this will not be returned, saving you time.
     */
    public Double getClusterMinP() {
        return clusterMinP;
    }

    public void setClusterMinP(Double clusterMinP) {
        if (clusterMinP < 0 || clusterMinP > 1) throw new IllegalArgumentException();
        this.clusterMinP = clusterMinP;
    }
}