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
 * A clean-up of Ricardo's Sextad-based purify.
 *
 * @author Joe Ramsey
 */
public class PurifySextadBased implements IPurify {
    private boolean outputMessage = true;
    private IDeltaSextadTest sextadTest;

    private List<Node> nodes;
    private Graph mim;
    private double alpha = 0.05;

    public PurifySextadBased(IDeltaSextadTest sextadTest, double alpha) {
        this.sextadTest = sextadTest;
        this.nodes = sextadTest.getVariables();
        this.alpha = alpha;
    }

    public List<List<Node>> purify(List<List<Node>> clustering) {

        // The input nodes may not be object identical to the ones from the Sextad test, so we map them over then
        // back by their names.
        List<Node> originalNodes = new ArrayList<Node>();

        for (List<Node> cluster : clustering) {
            originalNodes.addAll(cluster);
        }

        List<List<Node>> _clustering = new ArrayList<List<Node>>();

        for (List<Node> cluster : clustering) {
            List<Node> converted = GraphUtils.replaceNodes(cluster, nodes);
            _clustering.add(converted);
        }

        if (_clustering.isEmpty()) {
            throw new NullPointerException("Clusters not specified.");
        }

        List<List<Node>> result = combinedSearch(_clustering);
        List<List<Node>> convertedResult = new ArrayList<List<Node>>();

        for (List<Node> cluster : result) {
            List<Node> converted = GraphUtils.replaceNodes(cluster, originalNodes);
            convertedResult.add(converted);
        }

        System.out.println(convertedResult);

        return convertedResult;
    }

    public void setTrueGraph(Graph mim) {
        this.mim = mim;
    }

    private List<List<Node>> combinedSearch(List<List<Node>> clustering) {
        Set<Node> eliminated = new HashSet<Node>();

        Set<Sextad> allImpurities = null;
        double cutoff = alpha;
        int count = 0;

        for (List<Node> cluster : clustering) {
            System.out.println("Within cluster: " + ++count);
            Set<Sextad> impurities = listSextads(cluster, eliminated, cutoff);

            if (impurities != null) {
                if (allImpurities == null) {
                    allImpurities = new HashSet<Sextad>();
                }
                allImpurities.addAll(impurities);
            }
        }

        Set<Sextad> impurities = listCrossConstructSextads(clustering, eliminated, cutoff);

        if (impurities != null) {
            if (allImpurities == null) {
                allImpurities = new HashSet<Sextad>();
            }
            allImpurities.addAll(impurities);
        }

        if (allImpurities == null) {
            return new ArrayList<List<Node>>();
        }

        NumberFormat nf = new DecimalFormat("0.####E00");

        while (true) {
            int max = 0;
            Node maxNode = null;
            Map<Node, Set<Sextad>> impuritiesPerNode = getImpuritiesPerNode(allImpurities, eliminated);

            for (Node node : nodes) {
                if (impuritiesPerNode.get(node).size() > max) {
                    max = impuritiesPerNode.get(node).size();
                    maxNode = node;
                }
            }

            if (max == 0) break;

            double minP = Double.POSITIVE_INFINITY;
            double maxP = Double.NEGATIVE_INFINITY;

            for (Sextad sextad : impuritiesPerNode.get(maxNode)) {
                double pValue = sextadTest.getPValue(sextad);

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

    private Map<Node, Set<Sextad>> getImpuritiesPerNode(Set<Sextad> allImpurities, Set<Node> _eliminated) {
        Map<Node, Set<Sextad>> impuritiesPerNode = new HashMap<Node, Set<Sextad>>();

        for (Node node : nodes) {
            impuritiesPerNode.put(node, new HashSet<Sextad>());
        }

        for (Sextad sextad : allImpurities) {
            if (_eliminated.contains(sextad.getI())) {
                continue;
            }

            if (_eliminated.contains(sextad.getJ())) {
                continue;
            }

            if (_eliminated.contains(sextad.getK())) {
                continue;
            }

            if (_eliminated.contains(sextad.getL())) {
                continue;
            }

            if (_eliminated.contains(sextad.getM())) {
                continue;
            }

            if (_eliminated.contains(sextad.getN())) {
                continue;
            }

            impuritiesPerNode.get(sextad.getI()).add(sextad);
            impuritiesPerNode.get(sextad.getJ()).add(sextad);
            impuritiesPerNode.get(sextad.getK()).add(sextad);
            impuritiesPerNode.get(sextad.getL()).add(sextad);
            impuritiesPerNode.get(sextad.getM()).add(sextad);
            impuritiesPerNode.get(sextad.getN()).add(sextad);
        }
        return impuritiesPerNode;
    }

    private Set<Sextad> listCrossConstructSextads(List<List<Node>> clustering, Set<Node> eliminated, double cutoff) {
        Set<Sextad> allSextads = new HashSet<Sextad>();
        boolean countable = false;

        for (int p1 = 0; p1 < clustering.size(); p1++) {
            for (int p2 = p1 + 1; p2 < clustering.size(); p2++) {
                List<Node> cluster1 = clustering.get(p1);
                List<Node> cluster2 = clustering.get(p2);

                if (cluster1.size() >= 5 && cluster2.size() >= 1) {
                    ChoiceGenerator gen1 = new ChoiceGenerator(cluster1.size(), 5);
                    int[] choice1;

                    while ((choice1 = gen1.next()) != null) {
                        ChoiceGenerator gen2 = new ChoiceGenerator(cluster2.size(), 1);
                        int[] choice2;

                        while ((choice2 = gen2.next()) != null) {
                            List<Node> crossCluster = new ArrayList<Node>();
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
                            List<Node> crossCluster = new ArrayList<Node>();
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

//                if (cluster1.size() >= 2 && cluster2.size() >= 2) {
//                    ChoiceGenerator gen1 = new ChoiceGenerator(cluster1.size(), 2);
//                    int[] choice1;
//
//                    while ((choice1 = gen1.next()) != null) {
//                        ChoiceGenerator gen2 = new ChoiceGenerator(cluster2.size(), 2);
//                        int[] choice2;
//
//                        while ((choice2 = gen2.next()) != null) {
//                            List<Node> crossCluster = new ArrayList<Node>();
//                            for (int i : choice1) crossCluster.add(cluster1.get(i));
//                            for (int i : choice2) crossCluster.add(cluster2.get(i));
//
//                            Set<Sextad> Sextads = listSextads2By2(crossCluster, eliminated, cutoff);
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


    private Set<Sextad> listSextads(List<Node> cluster, Set<Node> eliminated, double cutoff) {
        if (cluster.size() < 6) return null;
        cluster = new ArrayList<Node>(cluster);
        boolean countable = false;

        Set<Sextad> Sextads = new HashSet<Sextad>();
        ChoiceGenerator gen = new ChoiceGenerator(cluster.size(), 6);
        int[] choice;

        while ((choice = gen.next()) != null) {
            int _i = choice[0];
            int _j = choice[1];
            int _k = choice[2];
            int _l = choice[3];
            int _m = choice[4];
            int _n = choice[5];

            Node m1 = cluster.get(_i);
            Node m2 = cluster.get(_j);
            Node m3 = cluster.get(_k);
            Node m4 = cluster.get(_l);
            Node m5 = cluster.get(_m);
            Node m6 = cluster.get(_n);

            if (eliminated.contains(m1) || eliminated.contains(m2) || eliminated.contains(m3) || eliminated.contains(m4)
                    || eliminated.contains(m5) || eliminated.contains(m6)) {
                continue;
            }

            countable = true;
            double p1, p2, p3, p4, p5, p6, p7, p8, p9, p10;

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

//    private Set<Sextad> listSextads2By2(List<Node> cluster, Set<Node> eliminated, double cutoff) {
//        if (cluster.size() < 4) return null;
//        cluster = new ArrayList<Node>(cluster);
//        Set<Sextad> Sextads = new HashSet<Sextad>();
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
//            Sextads.add(new Sextad(ci, ck, cl, cj, p3));
////            Sextads.add(new Sextad(ci, cj, cl, ck, p3));
//        }
//
//        return Sextads;
//    }

    private List<List<Node>> buildSolution(List<List<Node>> clustering, Set<Node> eliminated) {
        List<List<Node>> solution = new ArrayList<List<Node>>();

        for (List<Node> cluster : clustering) {
            List<Node> _cluster = new ArrayList<Node>(cluster);
            _cluster.removeAll(eliminated);
            solution.add(_cluster);
        }

        return solution;
    }
}


