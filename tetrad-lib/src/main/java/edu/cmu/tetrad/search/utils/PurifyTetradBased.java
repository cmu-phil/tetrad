///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.*;

/**
 * Implements a tetrad-based purify method.
 *
 * @author ricardosilva
 * @version $Id: $Id
 */
public class PurifyTetradBased implements IPurify {
    private final TetradTest tetradTest;

    private final List<Node> nodes;

    /**
     * <p>Constructor for PurifyTetradBased.</p>
     *
     * @param tetradTest a {@link edu.cmu.tetrad.search.utils.TetradTest} object
     */
    public PurifyTetradBased(TetradTest tetradTest) {
        this.tetradTest = tetradTest;
        this.nodes = tetradTest.getVariables();
    }

    /**
     * {@inheritDoc}
     */
    public List<List<Node>> purify(List<List<Node>> clustering) {

        // The input nodes may not be object-identical to the ones from the tetrad test, so we map them over then
        // back by their names.
        List<Node> originalNodes = new ArrayList<>();

        for (List<Node> cluster : clustering) {
            originalNodes.addAll(cluster);
        }

        List<List<Node>> _clustering = new ArrayList<>();

        for (List<Node> cluster : clustering) {
            List<Node> converted = GraphUtils.replaceNodes(cluster, this.nodes);
            _clustering.add(converted);
        }

        if (_clustering.isEmpty()) {
            throw new NullPointerException("Clusters not specified.");
        }

        List<List<Node>> result = combinedSearch(_clustering);
        List<List<Node>> convertedResult = new ArrayList<>();

        for (List<Node> cluster : result) {
            List<Node> converted = GraphUtils.replaceNodes(cluster, originalNodes);
            convertedResult.add(converted);
        }

        return convertedResult;
    }

    /**
     * {@inheritDoc}
     */
    public void setTrueGraph(Graph mim) {
    }


    private List<List<Node>> combinedSearch(List<List<Node>> clustering) {
        Set<Node> eliminated = new HashSet<>();

        Set<TetradNode> allImpurities = null;
        double cutoff = this.tetradTest.getSignificance();

        for (List<Node> cluster : clustering) {
            Set<TetradNode> impurities = listTetrads(cluster, eliminated, cutoff);

            if (impurities != null) {
                if (allImpurities == null) {
                    allImpurities = new HashSet<>();
                }
                allImpurities.addAll(impurities);
            }
        }

        Set<TetradNode> impurities = listCrossConstructTetrads(clustering, eliminated, cutoff);

        if (impurities != null) {
            if (allImpurities == null) {
                allImpurities = new HashSet<>();
            }
            allImpurities.addAll(impurities);
        }

        if (allImpurities == null) {
            return new ArrayList<>();
        }

        while (true) {
            int max = 0;
            Node maxNode = null;
            Map<Node, Set<TetradNode>> impuritiesPerNode = getImpuritiesPerNode(allImpurities, eliminated);

            for (Node node : this.nodes) {
                if (impuritiesPerNode.get(node).size() > max) {
                    max = impuritiesPerNode.get(node).size();
                    maxNode = node;
                }
            }

            if (max == 0) break;

            double minP = Double.POSITIVE_INFINITY;
            double maxP = Double.NEGATIVE_INFINITY;

            for (TetradNode tetrad : impuritiesPerNode.get(maxNode)) {
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

    private Map<Node, Set<TetradNode>> getImpuritiesPerNode(Set<TetradNode> allImpurities, Set<Node> _eliminated) {
        Map<Node, Set<TetradNode>> impuritiesPerNode = new HashMap<>();

        for (Node node : this.nodes) {
            impuritiesPerNode.put(node, new HashSet<>());
        }

        for (TetradNode tetrad : allImpurities) {
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

    private Set<TetradNode> listCrossConstructTetrads(List<List<Node>> clustering, Set<Node> eliminated, double cutoff) {
        Set<TetradNode> allTetrads = new HashSet<>();
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
                            List<Node> crossCluster = new ArrayList<>();
                            for (int i : choice1) crossCluster.add(cluster1.get(i));
                            for (int i : choice2) crossCluster.add(cluster2.get(i));
                            Set<TetradNode> tetrads = listTetrads(crossCluster, eliminated, cutoff);

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
                            List<Node> crossCluster = new ArrayList<>();
                            for (int i : choice1) crossCluster.add(cluster2.get(i));
                            for (int i : choice2) crossCluster.add(cluster1.get(i));

                            Set<TetradNode> tetrads = listTetrads(crossCluster, eliminated, cutoff);

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
                            List<Node> crossCluster = new ArrayList<>();
                            for (int i : choice1) crossCluster.add(cluster1.get(i));
                            for (int i : choice2) crossCluster.add(cluster2.get(i));

                            Set<TetradNode> tetrads = listTetrads2By2(crossCluster, eliminated, cutoff);

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


    private Set<TetradNode> listTetrads(List<Node> cluster, Set<Node> eliminated, double cutoff) {
        if (cluster.size() < 4) return null;
        cluster = new ArrayList<>(cluster);
        boolean countable = false;

        Set<TetradNode> tetrads = new HashSet<>();
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
            double p1;
            double p2;
            double p3;

            p1 = this.tetradTest.tetradPValue(this.nodes.indexOf(ci), this.nodes.indexOf(cj), this.nodes.indexOf(ck), this.nodes.indexOf(cl));
            p2 = this.tetradTest.tetradPValue(this.nodes.indexOf(ci), this.nodes.indexOf(cj), this.nodes.indexOf(cl), this.nodes.indexOf(ck));
            p3 = this.tetradTest.tetradPValue(this.nodes.indexOf(ci), this.nodes.indexOf(ck), this.nodes.indexOf(cl), this.nodes.indexOf(cj));

            if (p1 < cutoff) {
                tetrads.add(new TetradNode(ci, cj, ck, cl, p1));
            }

            if (p2 < cutoff) {
                tetrads.add(new TetradNode(ci, cj, cl, ck, p2));
            }

            if (p3 < cutoff) {
                tetrads.add(new TetradNode(ci, ck, cl, cj, p3));
            }
        }

        return countable ? tetrads : null;
    }

    private Set<TetradNode> listTetrads2By2(List<Node> cluster, Set<Node> eliminated, double cutoff) {
        if (cluster.size() < 4) return null;
        cluster = new ArrayList<>(cluster);
        Set<TetradNode> tetrads = new HashSet<>();

        Node ci = cluster.get(0);
        Node cj = cluster.get(1);
        Node ck = cluster.get(2);
        Node cl = cluster.get(3);

        if (eliminated.contains(ci) || eliminated.contains(cj) || eliminated.contains(ck) || eliminated.contains(cl)) {
            return null;
        }

        double p3 = this.tetradTest.tetradPValue(this.nodes.indexOf(ci), this.nodes.indexOf(ck), this.nodes.indexOf(cl), this.nodes.indexOf(cj));
//        double p3 = tetradTest.tetradPValue(nodes.indexOf(ci), nodes.indexOf(cj), nodes.indexOf(cl), nodes.indexOf(ck));

        if (p3 < cutoff) {
            tetrads.add(new TetradNode(ci, ck, cl, cj, p3));
//            tetrads.add(new Tetrad(ci, cj, cl, ck, p3));
        }

        return tetrads;
    }

    private List<List<Node>> buildSolution(List<List<Node>> clustering, Set<Node> eliminated) {
        List<List<Node>> solution = new ArrayList<>();

        for (List<Node> cluster : clustering) {
            List<Node> _cluster = new ArrayList<>(cluster);
            _cluster.removeAll(eliminated);
            solution.add(_cluster);
        }

        return solution;
    }
}



