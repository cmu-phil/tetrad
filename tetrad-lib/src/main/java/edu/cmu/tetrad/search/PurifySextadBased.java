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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

// EXPERIMENTAL!!

/**
 * A clean-up of Ricardo's IntSextad-based purify.
 *
 * @author Joe Ramsey
 */
public class PurifySextadBased {
    private boolean outputMessage = true;
    private DeltaSextadTest sextadTest;

    private List<Integer> nodes;
    private Graph mim;
    private double alpha = 0.05;

    public PurifySextadBased(DeltaSextadTest sextadTest, double alpha) {
        this.sextadTest = sextadTest;

        this.nodes = new ArrayList<>();

        for (int i = 0; i < sextadTest.getVariables().size(); i++) {
            this.nodes.add(i);
        }
        this.alpha = alpha;
    }

    public List<List<Integer>> purify(List<List<Integer>> clustering) {

        // The input nodes may not be object identical to the ones from the IntSextad test, so we map them over then
        // back by their names.
        for (List<Integer> cluster : clustering) {
            clustering.add(cluster);
        }

        if (clustering.isEmpty()) {
            throw new NullPointerException("Clusters not specified.");
        }

        List<List<Integer>> result = combinedSearch(clustering);
        List<List<Integer>> convertedResult = new ArrayList<List<Integer>>();

        for (List<Integer> cluster : result) {
            convertedResult.add(cluster);
        }

        System.out.println(convertedResult);

        return convertedResult;
    }

    public void setTrueGraph(Graph mim) {
        this.mim = mim;
    }

    private List<List<Integer>> combinedSearch(List<List<Integer>> clustering) {
        Set<Integer> eliminated = new HashSet<Integer>();

        Set<IntSextad> allImpurities = null;
        double cutoff = alpha;
        int count = 0;

        for (List<Integer> cluster : clustering) {
            System.out.println("Within cluster: " + ++count);
            Set<IntSextad> impurities = listSextads(cluster, eliminated, cutoff);

            if (impurities != null) {
                if (allImpurities == null) {
                    allImpurities = new HashSet<IntSextad>();
                }
                allImpurities.addAll(impurities);
            }
        }

        Set<IntSextad> impurities = listCrossConstructSextads(clustering, eliminated, cutoff);

        if (impurities != null) {
            if (allImpurities == null) {
                allImpurities = new HashSet<IntSextad>();
            }
            allImpurities.addAll(impurities);
        }

        if (allImpurities == null) {
            return new ArrayList<List<Integer>>();
        }

        NumberFormat nf = new DecimalFormat("0.####E00");

        while (true) {
            int max = 0;
            Integer maxNode = null;
            Map<Integer, Set<IntSextad>> impuritiesPerNode = getImpuritiesPerNode(allImpurities, eliminated);

            for (Integer node : nodes) {
                if (impuritiesPerNode.get(node).size() > max) {
                    max = impuritiesPerNode.get(node).size();
                    maxNode = node;
                }
            }

            if (max == 0) break;

            double minP = Double.POSITIVE_INFINITY;
            double maxP = Double.NEGATIVE_INFINITY;

            for (IntSextad IntSextad : impuritiesPerNode.get(maxNode)) {
                double pValue = sextadTest.getPValue(IntSextad);

                if (pValue < minP) {
                    minP = pValue;
                }

                if (pValue > maxP) {
                    maxP = pValue;
                }
            }

            impuritiesPerNode.remove(maxNode);
            eliminated.add(maxNode);
            System.out.println("Eliminated " + maxNode + " impurities = " + max +
                    "q = " + nf.format(minP) + " maxP = " + nf.format(maxP));
        }

        return buildSolution(clustering, eliminated);
    }

    private Map<Integer, Set<IntSextad>> getImpuritiesPerNode(Set<IntSextad> allImpurities, Set<Integer> _eliminated) {
        Map<Integer, Set<IntSextad>> impuritiesPerNode = new HashMap<Integer, Set<IntSextad>>();

        for (Integer node : nodes) {
            impuritiesPerNode.put(node, new HashSet<IntSextad>());
        }

        for (IntSextad IntSextad : allImpurities) {
            if (_eliminated.contains(IntSextad.getI())) {
                continue;
            }

            if (_eliminated.contains(IntSextad.getJ())) {
                continue;
            }

            if (_eliminated.contains(IntSextad.getK())) {
                continue;
            }

            if (_eliminated.contains(IntSextad.getL())) {
                continue;
            }

            if (_eliminated.contains(IntSextad.getM())) {
                continue;
            }

            if (_eliminated.contains(IntSextad.getN())) {
                continue;
            }

            impuritiesPerNode.get(IntSextad.getI()).add(IntSextad);
            impuritiesPerNode.get(IntSextad.getJ()).add(IntSextad);
            impuritiesPerNode.get(IntSextad.getK()).add(IntSextad);
            impuritiesPerNode.get(IntSextad.getL()).add(IntSextad);
            impuritiesPerNode.get(IntSextad.getM()).add(IntSextad);
            impuritiesPerNode.get(IntSextad.getN()).add(IntSextad);
        }
        return impuritiesPerNode;
    }

    private Set<IntSextad> listCrossConstructSextads(List<List<Integer>> clustering, Set<Integer> eliminated, double cutoff) {
        Set<IntSextad> allSextads = new HashSet<IntSextad>();
        boolean countable = false;

        for (int p1 = 0; p1 < clustering.size(); p1++) {
            for (int p2 = p1 + 1; p2 < clustering.size(); p2++) {
                List<Integer> cluster1 = clustering.get(p1);
                List<Integer> cluster2 = clustering.get(p2);

                if (cluster1.size() >= 5 && cluster2.size() >= 1) {
                    ChoiceGenerator gen1 = new ChoiceGenerator(cluster1.size(), 5);
                    int[] choice1;

                    while ((choice1 = gen1.next()) != null) {
                        ChoiceGenerator gen2 = new ChoiceGenerator(cluster2.size(), 1);
                        int[] choice2;

                        while ((choice2 = gen2.next()) != null) {
                            List<Integer> crossCluster = new ArrayList<Integer>();
                            for (int i : choice1) crossCluster.add(cluster1.get(i));
                            for (int i : choice2) crossCluster.add(cluster2.get(i));
                            Set<IntSextad> Sextads = listSextads(crossCluster, eliminated, cutoff);

                            if (Sextads != null) {
                                countable = true;
                                allSextads.addAll(Sextads);
                            }
                        }
                    }
                }

                if (cluster2.size() >= 5 && cluster1.size() >= 1) {
                    ChoiceGenerator gen1 = new ChoiceGenerator(cluster2.size(), 5);
                    int[] choice1;

                    while ((choice1 = gen1.next()) != null) {
                        ChoiceGenerator gen2 = new ChoiceGenerator(cluster1.size(), 1);
                        int[] choice2;

                        while ((choice2 = gen2.next()) != null) {
                            List<Integer> crossCluster = new ArrayList<Integer>();
                            for (int i : choice1) crossCluster.add(cluster2.get(i));
                            for (int i : choice2) crossCluster.add(cluster1.get(i));

                            Set<IntSextad> Sextads = listSextads(crossCluster, eliminated, cutoff);

                            if (Sextads != null) {
                                countable = true;
                                allSextads.addAll(Sextads);
                            }
                        }
                    }
                }

//                if (cluster1.size() >= 2 && cluster2.size() >= 2) {
//                    ChoiceGenerator gen1 = new ChoiceGenerator(cluster1.size(), 2);
//                    int[] choice1;
//
//                    while ((choice1 = gen1.next()) != null) {
//                        ChoiceGenerator gen2 = new ChoiceGenerator(cluster2.size(), 2);
//                        int[] choice2;
//
//                        while ((choice2 = gen2.next()) != null) {
//                            List<Integer> crossCluster = new ArrayList<Integer>();
//                            for (int i : choice1) crossCluster.add(cluster1.get(i));
//                            for (int i : choice2) crossCluster.add(cluster2.get(i));
//
//                            Set<IntSextad> Sextads = listSextads2By2(crossCluster, eliminated, cutoff);
//
//                            if (Sextads != null) {
//                                countable = true;
//                                allSextads.addAll(Sextads);
//                            }
//                        }
//                    }
//                }
            }
        }

        return countable ? allSextads : null;
    }


    private Set<IntSextad> listSextads(List<Integer> cluster, Set<Integer> eliminated, double cutoff) {
        if (cluster.size() < 6) return null;
        cluster = new ArrayList<Integer>(cluster);
        boolean countable = false;

        Set<IntSextad> Sextads = new HashSet<IntSextad>();
        ChoiceGenerator gen = new ChoiceGenerator(cluster.size(), 6);
        int[] choice;

        while ((choice = gen.next()) != null) {
            int _i = choice[0];
            int _j = choice[1];
            int _k = choice[2];
            int _l = choice[3];
            int _m = choice[4];
            int _n = choice[5];

            int m1 = cluster.get(_i);
            int m2 = cluster.get(_j);
            int m3 = cluster.get(_k);
            int m4 = cluster.get(_l);
            int m5 = cluster.get(_m);
            int m6 = cluster.get(_n);

            if (eliminated.contains(m1) || eliminated.contains(m2) || eliminated.contains(m3) || eliminated.contains(m4)
                    || eliminated.contains(m5) || eliminated.contains(m6)) {
                continue;
            }

            countable = true;
            double p1, p2, p3, p4, p5, p6, p7, p8, p9, p10;

            IntSextad t1 = new IntSextad(m1, m2, m3, m4, m5, m6);
            IntSextad t2 = new IntSextad(m1, m2, m4, m3, m5, m6);
            IntSextad t3 = new IntSextad(m1, m2, m5, m3, m4, m6);
            IntSextad t4 = new IntSextad(m1, m2, m6, m3, m4, m5);
            IntSextad t5 = new IntSextad(m1, m3, m4, m2, m5, m6);
            IntSextad t6 = new IntSextad(m1, m3, m5, m2, m4, m6);
            IntSextad t7 = new IntSextad(m1, m3, m6, m2, m4, m5);
            IntSextad t8 = new IntSextad(m1, m4, m5, m2, m3, m6);
            IntSextad t9 = new IntSextad(m1, m4, m6, m2, m3, m5);
            IntSextad t10 = new IntSextad(m1, m5, m6, m2, m3, m4);

            p1 = sextadTest.getPValue(t1);
            p2 = sextadTest.getPValue(t2);
            p3 = sextadTest.getPValue(t3);
            p4 = sextadTest.getPValue(t4);
            p5 = sextadTest.getPValue(t5);
            p6 = sextadTest.getPValue(t6);
            p7 = sextadTest.getPValue(t7);
            p8 = sextadTest.getPValue(t8);
            p9 = sextadTest.getPValue(t9);
            p10 = sextadTest.getPValue(t10);

            if (p1 < cutoff) {
                Sextads.add(t1);
            }

            if (p2 < cutoff) {
                Sextads.add(t2);
            }

            if (p3 < cutoff) {
                Sextads.add(t3);
            }

            if (p4 < cutoff) {
                Sextads.add(t4);
            }

            if (p5 < cutoff) {
                Sextads.add(t5);
            }

            if (p6 < cutoff) {
                Sextads.add(t6);
            }

            if (p7 < cutoff) {
                Sextads.add(t7);
            }

            if (p8 < cutoff) {
                Sextads.add(t8);
            }
            if (p9 < cutoff) {
                Sextads.add(t9);
            }

            if (p10 < cutoff) {
                Sextads.add(t10);
            }

        }

        return countable ? Sextads : null;
    }

//    private Set<IntSextad> listSextads2By2(List<Integer> cluster, Set<Integer> eliminated, double cutoff) {
//        if (cluster.size() < 4) return null;
//        cluster = new ArrayList<Integer>(cluster);
//        Set<IntSextad> Sextads = new HashSet<IntSextad>();
//
//        Node ci = cluster.get(0);
//        Node cj = cluster.get(1);
//        Node ck = cluster.get(2);
//        Node cl = cluster.get(3);
//
//        if (eliminated.contains(ci) || eliminated.contains(cj) || eliminated.contains(ck) || eliminated.contains(cl)) {
//            return null;
//        }
//
//        double p3 = SextadTest.SextadPValue(nodes.indexOf(ci), nodes.indexOf(ck), nodes.indexOf(cl), nodes.indexOf(cj));
////        double p3 = SextadTest.SextadPValue(nodes.indexOf(ci), nodes.indexOf(cj), nodes.indexOf(cl), nodes.indexOf(ck));
//
//        if (p3 < cutoff) {
//            Sextads.add(new IntSextad(ci, ck, cl, cj, p3));
////            Sextads.add(new IntSextad(ci, cj, cl, ck, p3));
//        }
//
//        return Sextads;
//    }

    private List<List<Integer>> buildSolution(List<List<Integer>> clustering, Set<Integer> eliminated) {
        List<List<Integer>> solution = new ArrayList<List<Integer>>();

        for (List<Integer> cluster : clustering) {
            List<Integer> _cluster = new ArrayList<Integer>(cluster);
            _cluster.removeAll(eliminated);
            solution.add(_cluster);
        }

        return solution;
    }
}


