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
    private final boolean outputMessage = true;
    private final TetradTest tetradTest;

    private final List<Node> nodes;
    private Graph mim;

    public PurifyTetradBased2(final TetradTest tetradTest) {
        this.tetradTest = tetradTest;
        this.nodes = tetradTest.getVariables();
    }

    public List<List<Node>> purify(final List<List<Node>> clustering) {

        // The input nodes may not be object identical to the ones from the tetrad test, so we map them over then
        // back by their names.
        final List<Node> originalNodes = new ArrayList<>();

        for (final List<Node> cluster : clustering) {
            originalNodes.addAll(cluster);
        }

        final List<List<Node>> _clustering = new ArrayList<>();

        for (final List<Node> cluster : clustering) {
            final List<Node> converted = GraphUtils.replaceNodes(cluster, this.nodes);
            _clustering.add(converted);
        }

        if (_clustering.isEmpty()) {
            throw new NullPointerException("Clusters not specified.");
        }

        final List<List<Node>> result = combinedSearch(_clustering);
        final List<List<Node>> convertedResult = new ArrayList<>();

        for (final List<Node> cluster : result) {
            final List<Node> converted = GraphUtils.replaceNodes(cluster, originalNodes);
            convertedResult.add(converted);
        }

        return convertedResult;
    }

    public void setTrueGraph(final Graph mim) {
        this.mim = mim;
    }


    private List<List<Node>> combinedSearch(final List<List<Node>> clustering) {
        final Set<Node> eliminated = new HashSet<>();

        Set<Tetrad> allImpurities = null;
        final double cutoff = this.tetradTest.getSignificance();
        final int count = 0;

        for (final List<Node> cluster : clustering) {
            final Set<Tetrad> impurities = listTetrads(cluster, eliminated, cutoff);

            if (impurities != null) {
                if (allImpurities == null) {
                    allImpurities = new HashSet<>();
                }
                allImpurities.addAll(impurities);
            }
        }

        final Set<Tetrad> impurities = listCrossConstructTetrads(clustering, eliminated, cutoff);

        if (impurities != null) {
            if (allImpurities == null) {
                allImpurities = new HashSet<>();
            }
            allImpurities.addAll(impurities);
        }

        if (allImpurities == null) {
            return new ArrayList<>();
        }

        final NumberFormat nf = new DecimalFormat("0.####E00");

        while (true) {
            int max = 0;
            Node maxNode = null;
            final Map<Node, Set<Tetrad>> impuritiesPerNode = getImpuritiesPerNode(allImpurities, eliminated);

            for (final Node node : this.nodes) {
                if (impuritiesPerNode.get(node).size() > max) {
                    max = impuritiesPerNode.get(node).size();
                    maxNode = node;
                }
            }

            if (max == 0) break;

            double minP = Double.POSITIVE_INFINITY;
            double maxP = Double.NEGATIVE_INFINITY;

            for (final Tetrad tetrad : impuritiesPerNode.get(maxNode)) {
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

    private Map<Node, Set<Tetrad>> getImpuritiesPerNode(final Set<Tetrad> allImpurities, final Set<Node> _eliminated) {
        final Map<Node, Set<Tetrad>> impuritiesPerNode = new HashMap<>();

        for (final Node node : this.nodes) {
            impuritiesPerNode.put(node, new HashSet<Tetrad>());
        }

        for (final Tetrad tetrad : allImpurities) {
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

    private Set<Tetrad> listCrossConstructTetrads(final List<List<Node>> clustering, final Set<Node> eliminated, final double cutoff) {
        final Set<Tetrad> allTetrads = new HashSet<>();
        boolean countable = false;

        for (int p1 = 0; p1 < clustering.size(); p1++) {
            for (int p2 = p1 + 1; p2 < clustering.size(); p2++) {
                final List<Node> cluster1 = clustering.get(p1);
                final List<Node> cluster2 = clustering.get(p2);

                if (cluster1.size() >= 3 && cluster2.size() >= 1) {
                    final ChoiceGenerator gen1 = new ChoiceGenerator(cluster1.size(), 3);
                    int[] choice1;

                    while ((choice1 = gen1.next()) != null) {
                        final ChoiceGenerator gen2 = new ChoiceGenerator(cluster2.size(), 1);
                        int[] choice2;

                        while ((choice2 = gen2.next()) != null) {
                            final List<Node> crossCluster = new ArrayList<>();
                            for (final int i : choice1) crossCluster.add(cluster1.get(i));
                            for (final int i : choice2) crossCluster.add(cluster2.get(i));
                            final Set<Tetrad> tetrads = listTetrads(crossCluster, eliminated, cutoff);

                            if (tetrads != null) {
                                countable = true;
                                allTetrads.addAll(tetrads);
                            }
                        }
                    }
                }

                if (cluster2.size() >= 3 && cluster1.size() >= 1) {
                    final ChoiceGenerator gen1 = new ChoiceGenerator(cluster2.size(), 3);
                    int[] choice1;

                    while ((choice1 = gen1.next()) != null) {
                        final ChoiceGenerator gen2 = new ChoiceGenerator(cluster1.size(), 1);
                        int[] choice2;

                        while ((choice2 = gen2.next()) != null) {
                            final List<Node> crossCluster = new ArrayList<>();
                            for (final int i : choice1) crossCluster.add(cluster2.get(i));
                            for (final int i : choice2) crossCluster.add(cluster1.get(i));

                            final Set<Tetrad> tetrads = listTetrads(crossCluster, eliminated, cutoff);

                            if (tetrads != null) {
                                countable = true;
                                allTetrads.addAll(tetrads);
                            }
                        }
                    }
                }

                if (cluster1.size() >= 2 && cluster2.size() >= 2) {
                    final ChoiceGenerator gen1 = new ChoiceGenerator(cluster1.size(), 2);
                    int[] choice1;

                    while ((choice1 = gen1.next()) != null) {
                        final ChoiceGenerator gen2 = new ChoiceGenerator(cluster2.size(), 2);
                        int[] choice2;

                        while ((choice2 = gen2.next()) != null) {
                            final List<Node> crossCluster = new ArrayList<>();
                            for (final int i : choice1) crossCluster.add(cluster1.get(i));
                            for (final int i : choice2) crossCluster.add(cluster2.get(i));

                            final Set<Tetrad> tetrads = listTetrads2By2(crossCluster, eliminated, cutoff);

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


    private Set<Tetrad> listTetrads(List<Node> cluster, final Set<Node> eliminated, final double cutoff) {
        if (cluster.size() < 4) return null;
        cluster = new ArrayList<>(cluster);
        boolean countable = false;

        final Set<Tetrad> tetrads = new HashSet<>();
        final ChoiceGenerator gen = new ChoiceGenerator(cluster.size(), 4);
        int[] choice;

        while ((choice = gen.next()) != null) {
            final int _i = choice[0];
            final int _j = choice[1];
            final int _k = choice[2];
            final int _l = choice[3];

            final Node ci = cluster.get(_i);
            final Node cj = cluster.get(_j);
            final Node ck = cluster.get(_k);
            final Node cl = cluster.get(_l);

            if (eliminated.contains(ci) || eliminated.contains(cj) || eliminated.contains(ck) || eliminated.contains(cl)) {
                continue;
            }

            countable = true;
            final double p1;
            double p2;
            final double p3;

            p1 = this.tetradTest.tetradPValue(this.nodes.indexOf(ci), this.nodes.indexOf(cj), this.nodes.indexOf(ck), this.nodes.indexOf(cl));
            p2 = this.tetradTest.tetradPValue(this.nodes.indexOf(ci), this.nodes.indexOf(cj), this.nodes.indexOf(cl), this.nodes.indexOf(ck));
            p3 = this.tetradTest.tetradPValue(this.nodes.indexOf(ci), this.nodes.indexOf(ck), this.nodes.indexOf(cl), this.nodes.indexOf(cj));

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

    private Set<Tetrad> listTetrads2By2(List<Node> cluster, final Set<Node> eliminated, final double cutoff) {
        if (cluster.size() < 4) return null;
        cluster = new ArrayList<>(cluster);
        final Set<Tetrad> tetrads = new HashSet<>();

        final Node ci = cluster.get(0);
        final Node cj = cluster.get(1);
        final Node ck = cluster.get(2);
        final Node cl = cluster.get(3);

        if (eliminated.contains(ci) || eliminated.contains(cj) || eliminated.contains(ck) || eliminated.contains(cl)) {
            return null;
        }

        final double p3 = this.tetradTest.tetradPValue(this.nodes.indexOf(ci), this.nodes.indexOf(ck), this.nodes.indexOf(cl), this.nodes.indexOf(cj));
//        double p3 = tetradTest.tetradPValue(nodes.indexOf(ci), nodes.indexOf(cj), nodes.indexOf(cl), nodes.indexOf(ck));

        if (p3 < cutoff) {
            tetrads.add(new Tetrad(ci, ck, cl, cj, p3));
//            tetrads.add(new Tetrad(ci, cj, cl, ck, p3));
        }

        return tetrads;
    }

    private List<List<Node>> buildSolution(final List<List<Node>> clustering, final Set<Node> eliminated) {
        final List<List<Node>> solution = new ArrayList<>();

        for (final List<Node> cluster : clustering) {
            final List<Node> _cluster = new ArrayList<>(cluster);
            _cluster.removeAll(eliminated);
            solution.add(_cluster);
        }

        return solution;
    }
}


