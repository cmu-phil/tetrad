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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.util.ChoiceGenerator;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

// EXPERIMENTAL!!

/**
 * Implments a sextad-based Purify method.
 *
 * @author ricardosilva
 * @version $Id: $Id
 */
public class PurifySextadBased {
    private final DeltaSextadTest sextadTest;

    private final List<Integer> nodes;
    private final double alpha;

    /**
     * <p>Constructor for PurifySextadBased.</p>
     *
     * @param sextadTest a {@link edu.cmu.tetrad.search.utils.DeltaSextadTest} object
     * @param alpha      a double
     */
    public PurifySextadBased(DeltaSextadTest sextadTest, double alpha) {
        this.sextadTest = sextadTest;

        this.nodes = new ArrayList<>();

        for (int i = 0; i < sextadTest.getVariables().size(); i++) {
            this.nodes.add(i);
        }
        this.alpha = alpha;
    }

    // The input nodes may not be object-identical to the ones from the IntSextad test.

    /**
     * <p>purify.</p>
     *
     * @param clustering a {@link java.util.List} object
     * @return a {@link java.util.List} object
     */
    public List<List<Integer>> purify(List<List<Integer>> clustering) {

        if (clustering.isEmpty()) {
            throw new NullPointerException("Clusters not specified.");
        }

        List<List<Integer>> result = combinedSearch(clustering);

        List<List<Integer>> convertedResult = new ArrayList<>(result);

        System.out.println(convertedResult);

        return convertedResult;
    }

    private List<List<Integer>> combinedSearch(List<List<Integer>> clustering) {
        Set<Integer> eliminated = new HashSet<>();

        Set<Sextad> allImpurities = null;
        double cutoff = this.alpha;
        int count = 0;

        for (List<Integer> cluster : clustering) {
            System.out.println("Within cluster: " + ++count);
            Set<Sextad> impurities = listSextads(cluster, eliminated, cutoff);

            if (impurities != null) {
                if (allImpurities == null) {
                    allImpurities = new HashSet<>();
                }
                allImpurities.addAll(impurities);
            }
        }

        Set<Sextad> impurities = listCrossConstructSextads(clustering, eliminated, cutoff);

        if (impurities != null) {
            if (allImpurities == null) {
                allImpurities = new HashSet<>();
            }
            allImpurities.addAll(impurities);
        }

        if (allImpurities == null) {
            return new ArrayList<>();
        }

        NumberFormat nf = new DecimalFormat("0.####E00");

        while (true) {
            int max = 0;
            Integer maxNode = null;
            Map<Integer, Set<Sextad>> impuritiesPerNode = getImpuritiesPerNode(allImpurities, eliminated);

            for (Integer node : this.nodes) {
                if (impuritiesPerNode.get(node).size() > max) {
                    max = impuritiesPerNode.get(node).size();
                    maxNode = node;
                }
            }

            if (max == 0) break;

            double minP = Double.POSITIVE_INFINITY;
            double maxP = Double.NEGATIVE_INFINITY;

            for (Sextad Sextad : impuritiesPerNode.get(maxNode)) {
                double pValue = this.sextadTest.getPValue(Sextad);

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

    private Map<Integer, Set<Sextad>> getImpuritiesPerNode(Set<Sextad> allImpurities, Set<Integer> _eliminated) {
        Map<Integer, Set<Sextad>> impuritiesPerNode = new HashMap<>();

        for (Integer node : this.nodes) {
            impuritiesPerNode.put(node, new HashSet<>());
        }

        for (Sextad Sextad : allImpurities) {
            if (_eliminated.contains(Sextad.getI())) {
                continue;
            }

            if (_eliminated.contains(Sextad.getJ())) {
                continue;
            }

            if (_eliminated.contains(Sextad.getK())) {
                continue;
            }

            if (_eliminated.contains(Sextad.getL())) {
                continue;
            }

            if (_eliminated.contains(Sextad.getM())) {
                continue;
            }

            if (_eliminated.contains(Sextad.getN())) {
                continue;
            }

            impuritiesPerNode.get(Sextad.getI()).add(Sextad);
            impuritiesPerNode.get(Sextad.getJ()).add(Sextad);
            impuritiesPerNode.get(Sextad.getK()).add(Sextad);
            impuritiesPerNode.get(Sextad.getL()).add(Sextad);
            impuritiesPerNode.get(Sextad.getM()).add(Sextad);
            impuritiesPerNode.get(Sextad.getN()).add(Sextad);
        }
        return impuritiesPerNode;
    }

    private Set<Sextad> listCrossConstructSextads(List<List<Integer>> clustering, Set<Integer> eliminated, double cutoff) {
        Set<Sextad> allSextads = new HashSet<>();
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
                            List<Integer> crossCluster = new ArrayList<>();
                            for (int i : choice1) crossCluster.add(cluster1.get(i));
                            for (int i : choice2) crossCluster.add(cluster2.get(i));
                            Set<Sextad> Sextads = listSextads(crossCluster, eliminated, cutoff);

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
                            List<Integer> crossCluster = new ArrayList<>();
                            for (int i : choice1) crossCluster.add(cluster2.get(i));
                            for (int i : choice2) crossCluster.add(cluster1.get(i));

                            Set<Sextad> Sextads = listSextads(crossCluster, eliminated, cutoff);

                            if (Sextads != null) {
                                countable = true;
                                allSextads.addAll(Sextads);
                            }
                        }
                    }
                }

            }
        }

        return countable ? allSextads : null;
    }


    private Set<Sextad> listSextads(List<Integer> cluster, Set<Integer> eliminated, double cutoff) {
        if (cluster.size() < 6) return null;
        cluster = new ArrayList<>(cluster);
        boolean countable = false;

        Set<Sextad> Sextads = new HashSet<>();
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
            double p1;
            double p2;
            double p3;
            double p4;
            double p5;
            double p6;
            double p7;
            double p8;
            double p9;
            double p10;

            Sextad t1 = new Sextad(m1, m2, m3, m4, m5, m6);
            Sextad t2 = new Sextad(m1, m2, m4, m3, m5, m6);
            Sextad t3 = new Sextad(m1, m2, m5, m3, m4, m6);
            Sextad t4 = new Sextad(m1, m2, m6, m3, m4, m5);
            Sextad t5 = new Sextad(m1, m3, m4, m2, m5, m6);
            Sextad t6 = new Sextad(m1, m3, m5, m2, m4, m6);
            Sextad t7 = new Sextad(m1, m3, m6, m2, m4, m5);
            Sextad t8 = new Sextad(m1, m4, m5, m2, m3, m6);
            Sextad t9 = new Sextad(m1, m4, m6, m2, m3, m5);
            Sextad t10 = new Sextad(m1, m5, m6, m2, m3, m4);

            p1 = this.sextadTest.getPValue(t1);
            p2 = this.sextadTest.getPValue(t2);
            p3 = this.sextadTest.getPValue(t3);
            p4 = this.sextadTest.getPValue(t4);
            p5 = this.sextadTest.getPValue(t5);
            p6 = this.sextadTest.getPValue(t6);
            p7 = this.sextadTest.getPValue(t7);
            p8 = this.sextadTest.getPValue(t8);
            p9 = this.sextadTest.getPValue(t9);
            p10 = this.sextadTest.getPValue(t10);

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

    private List<List<Integer>> buildSolution(List<List<Integer>> clustering, Set<Integer> eliminated) {
        List<List<Integer>> solution = new ArrayList<>();

        for (List<Integer> cluster : clustering) {
            List<Integer> _cluster = new ArrayList<>(cluster);
            _cluster.removeAll(eliminated);
            solution.add(_cluster);
        }

        return solution;
    }
}


