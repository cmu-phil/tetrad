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

/**
 * A clean-up of Ricardo's tetrad-based purify.
 *
 * @author Joe Ramsey
 */
public class PurifyTetradBased2 implements IPurify {
    private boolean outputMessage = true;
    private TetradTest tetradTest;

    private List<Node> nodes;
    private Graph mim;

    public PurifyTetradBased2(TetradTest tetradTest) {
        this.tetradTest = tetradTest;
        this.nodes = tetradTest.getVariables();
    }

    public List<List<Node>> purify(List<List<Node>> clustering) {

        // The input nodes may not be object identical to the ones from the tetrad test, so we map them over then
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

        return convertedResult;
    }

    public void setTrueGraph(Graph mim) {
        this.mim = mim;
    }


    private List<List<Node>> combinedSearch(List<List<Node>> clustering) {
        Set<Node> eliminated = new HashSet<Node>();

        Set<Tetrad> allImpurities = null;
        double cutoff = tetradTest.getSignificance();
        int count = 0;

        for (List<Node> cluster : clustering) {
            Set<Tetrad> impurities = listTetrads(cluster, eliminated, cutoff);

            if (impurities != null) {
                if (allImpurities == null) {
                    allImpurities = new HashSet<Tetrad>();
                }
                allImpurities.addAll(impurities);
            }
        }

        Set<Tetrad> impurities = listCrossConstructTetrads(clustering, eliminated, cutoff);

        if (impurities != null) {
            if (allImpurities == null) {
                allImpurities = new HashSet<Tetrad>();
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
            Map<Node, Set<Tetrad>> impuritiesPerNode = getImpuritiesPerNode(allImpurities, eliminated);

            for (Node node : nodes) {
                if (impuritiesPerNode.get(node).size() > max) {
                    max = impuritiesPerNode.get(node).size();
                    maxNode = node;
                }
            }

            if (max == 0) break;

            double minP = Double.POSITIVE_INFINITY;
            double maxP = Double.NEGATIVE_INFINITY;

            for (Tetrad tetrad : impuritiesPerNode.get(maxNode)) {
                if (tetrad.getPValue() < minP) {
                    minP = tetrad.getPValue();
                }

                if (tetrad.getPValue() > maxP) {
                    maxP = tetrad.getPValue();
                }
            }

            impuritiesPerNode.remove(maxNode);
            eliminated.add(maxNode);
        }

        return buildSolution(clustering, eliminated);
    }

    private Map<Node, Set<Tetrad>> getImpuritiesPerNode(Set<Tetrad> allImpurities, Set<Node> _eliminated) {
        Map<Node, Set<Tetrad>> impuritiesPerNode = new HashMap<Node, Set<Tetrad>>();

        for (Node node : nodes) {
            impuritiesPerNode.put(node, new HashSet<Tetrad>());
        }

        for (Tetrad tetrad : allImpurities) {
            if (_eliminated.contains(tetrad.getI())) {
                continue;
            }

            if (_eliminated.contains(tetrad.getJ())) {
                continue;
            }

            if (_eliminated.contains(tetrad.getK())) {
                continue;
            }

            if (_eliminated.contains(tetrad.getL())) {
                continue;
            }

            impuritiesPerNode.get(tetrad.getI()).add(tetrad);
            impuritiesPerNode.get(tetrad.getJ()).add(tetrad);
            impuritiesPerNode.get(tetrad.getK()).add(tetrad);
            impuritiesPerNode.get(tetrad.getL()).add(tetrad);
        }
        return impuritiesPerNode;
    }

    private Set<Tetrad> listCrossConstructTetrads(List<List<Node>> clustering, Set<Node> eliminated, double cutoff) {
        Set<Tetrad> allTetrads = new HashSet<Tetrad>();
        boolean countable = false;

        for (int p1 = 0; p1 < clustering.size(); p1++) {
            for (int p2 = p1 + 1; p2 < clustering.size(); p2++) {
                List<Node> cluster1 = clustering.get(p1);
                List<Node> cluster2 = clustering.get(p2);

                if (cluster1.size() >= 3 && cluster2.size() >= 1) {
                    ChoiceGenerator gen1 = new ChoiceGenerator(cluster1.size(), 3);
                    int[] choice1;

                    while ((choice1 = gen1.next()) != null) {
                        ChoiceGenerator gen2 = new ChoiceGenerator(cluster2.size(), 1);
                        int[] choice2;

                        while ((choice2 = gen2.next()) != null) {
                            List<Node> crossCluster = new ArrayList<Node>();
                            for (int i : choice1) crossCluster.add(cluster1.get(i));
                            for (int i : choice2) crossCluster.add(cluster2.get(i));
                            Set<Tetrad> tetrads = listTetrads(crossCluster, eliminated, cutoff);

                            if (tetrads != null) {
                                countable = true;
                                allTetrads.addAll(tetrads);
                            }
                        }
                    }
                }

                if (cluster2.size() >= 3 && cluster1.size() >= 1) {
                    ChoiceGenerator gen1 = new ChoiceGenerator(cluster2.size(), 3);
                    int[] choice1;

                    while ((choice1 = gen1.next()) != null) {
                        ChoiceGenerator gen2 = new ChoiceGenerator(cluster1.size(), 1);
                        int[] choice2;

                        while ((choice2 = gen2.next()) != null) {
                            List<Node> crossCluster = new ArrayList<Node>();
                            for (int i : choice1) crossCluster.add(cluster2.get(i));
                            for (int i : choice2) crossCluster.add(cluster1.get(i));

                            Set<Tetrad> tetrads = listTetrads(crossCluster, eliminated, cutoff);

                            if (tetrads != null) {
                                countable = true;
                                allTetrads.addAll(tetrads);
                            }
                        }
                    }
                }

                if (cluster1.size() >= 2 && cluster2.size() >= 2) {
                    ChoiceGenerator gen1 = new ChoiceGenerator(cluster1.size(), 2);
                    int[] choice1;

                    while ((choice1 = gen1.next()) != null) {
                        ChoiceGenerator gen2 = new ChoiceGenerator(cluster2.size(), 2);
                        int[] choice2;

                        while ((choice2 = gen2.next()) != null) {
                            List<Node> crossCluster = new ArrayList<Node>();
                            for (int i : choice1) crossCluster.add(cluster1.get(i));
                            for (int i : choice2) crossCluster.add(cluster2.get(i));

                            Set<Tetrad> tetrads = listTetrads2By2(crossCluster, eliminated, cutoff);

                            if (tetrads != null) {
                                countable = true;
                                allTetrads.addAll(tetrads);
                            }
                        }
                    }
                }
            }
        }

        return countable ? allTetrads : null;
    }


    private Set<Tetrad> listTetrads(List<Node> cluster, Set<Node> eliminated, double cutoff) {
        if (cluster.size() < 4) return null;
        cluster = new ArrayList<Node>(cluster);
        boolean countable = false;

        Set<Tetrad> tetrads = new HashSet<Tetrad>();
        ChoiceGenerator gen = new ChoiceGenerator(cluster.size(), 4);
        int[] choice;

        while ((choice = gen.next()) != null) {
            int _i = choice[0];
            int _j = choice[1];
            int _k = choice[2];
            int _l = choice[3];

            Node ci = cluster.get(_i);
            Node cj = cluster.get(_j);
            Node ck = cluster.get(_k);
            Node cl = cluster.get(_l);

            if (eliminated.contains(ci) || eliminated.contains(cj) || eliminated.contains(ck) || eliminated.contains(cl)) {
                continue;
            }

            countable = true;
            double p1, p2, p3;

            p1 = tetradTest.tetradPValue(nodes.indexOf(ci), nodes.indexOf(cj), nodes.indexOf(ck), nodes.indexOf(cl));
            p2 = tetradTest.tetradPValue(nodes.indexOf(ci), nodes.indexOf(cj), nodes.indexOf(cl), nodes.indexOf(ck));
            p3 = tetradTest.tetradPValue(nodes.indexOf(ci), nodes.indexOf(ck), nodes.indexOf(cl), nodes.indexOf(cj));

            if (p1 < cutoff) {
                tetrads.add(new Tetrad(ci, cj, ck, cl, p1));
            }

            if (p2 < cutoff) {
                tetrads.add(new Tetrad(ci, cj, cl, ck, p2));
            }

            if (p3 < cutoff) {
                tetrads.add(new Tetrad(ci, ck, cl, cj, p3));
            }
        }

        return countable ? tetrads : null;
    }

    private Set<Tetrad> listTetrads2By2(List<Node> cluster, Set<Node> eliminated, double cutoff) {
        if (cluster.size() < 4) return null;
        cluster = new ArrayList<Node>(cluster);
        Set<Tetrad> tetrads = new HashSet<Tetrad>();

        Node ci = cluster.get(0);
        Node cj = cluster.get(1);
        Node ck = cluster.get(2);
        Node cl = cluster.get(3);

        if (eliminated.contains(ci) || eliminated.contains(cj) || eliminated.contains(ck) || eliminated.contains(cl)) {
            return null;
        }

        double p3 = tetradTest.tetradPValue(nodes.indexOf(ci), nodes.indexOf(ck), nodes.indexOf(cl), nodes.indexOf(cj));
//        double p3 = tetradTest.tetradPValue(nodes.indexOf(ci), nodes.indexOf(cj), nodes.indexOf(cl), nodes.indexOf(ck));

        if (p3 < cutoff) {
            tetrads.add(new Tetrad(ci, ck, cl, cj, p3));
//            tetrads.add(new Tetrad(ci, cj, cl, ck, p3));
        }

        return tetrads;
    }

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


