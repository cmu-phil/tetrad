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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A clean-up of Ricardo's tetrad-based purify.
 *
 * @author Joe Ramsey
 */
public class PurifyTetradBased implements IPurify {
    private final boolean outputMessage = true;
    private final TetradTest tetradTest;
    private final int numVars;
    boolean doFdr = false;
    boolean listTetrads = false;

    public PurifyTetradBased(final TetradTest tetradTest) {
        this.tetradTest = tetradTest;
        this.numVars = tetradTest.getVarNames().length;
    }

    public List<List<Node>> purify(final List<List<Node>> clustering) {
        System.out.println("*** " + clustering);
        final List<int[]> _clustering = convertListToInt(clustering);

        System.out.println("&&&");
        printIntPartition(_clustering);

        final List<int[]> _clustering2 = purify2(_clustering);

        System.out.println("%%%");
        printIntPartition(_clustering2);

        return convertIntToList(_clustering2);
    }

    public void setTrueGraph(final Graph mim) {
        throw new UnsupportedOperationException();
    }

    private void printIntPartition(final List<int[]> clustering) {
        for (int i = 0; i < clustering.size(); i++) {
            final int[] cluster = clustering.get(i);
            System.out.print(i + ": ");
            for (int j = 0; j < cluster.length; j++) {
                System.out.print(cluster[j] + " ");
            }

            System.out.println();
        }

        System.out.println();
    }

    private List<int[]> convertListToInt(final List<List<Node>> clustering) {
        final List<Node> nodes = this.tetradTest.getVariables();
        final List<int[]> _clustering = new ArrayList<>();

        for (int i = 0; i < clustering.size(); i++) {
            final List<Node> cluster = clustering.get(i);
            final int[] _cluster = new int[cluster.size()];

            for (int j = 0; j < cluster.size(); j++) {
                for (int k = 0; k < nodes.size(); k++) {
                    if (nodes.get(k).getName().equals(cluster.get(j).getName())) {
                        _cluster[j] = k;
                    }
                }
            }

            _clustering.add(_cluster);
        }

        return _clustering;
    }

    private List<List<Node>> convertIntToList(final List<int[]> clustering) {
        final List<Node> nodes = this.tetradTest.getVariables();
        final List<List<Node>> _clustering = new ArrayList<>();

        for (int i = 0; i < clustering.size(); i++) {
            final int[] cluster = clustering.get(i);
            final List<Node> _cluster = new ArrayList<>();

            for (int j = 0; j < cluster.length; j++) {
                _cluster.add(nodes.get(cluster[j]));
            }

            _clustering.add(_cluster);
        }

        return _clustering;
    }


    private List purify2(final List clustering) {
        return tetradBasedPurify(clustering);
    }


    private List tetradBasedPurify(final List clustering) {
        final boolean[] eliminated = new boolean[this.numVars];
        for (int i = 0; i < this.numVars; i++) {
            eliminated[i] = false;
        }

        printlnMessage("TETRAD-BASED PURIFY:");
        printlnMessage("Finding Unidimensional Measurement Models");
        printlnMessage();
        printlnMessage("Initially Specified Measurement Model");
        printlnMessage();
        printClustering(clustering, eliminated);
        printlnMessage();

        printlnMessage("INTRA-CONSTRUCT PHASE.");
        printlnMessage("----------------------");
        printlnMessage();
        for (final Iterator it = clustering.iterator(); it.hasNext(); ) {
            intraConstructPhase2((int[]) it.next(), eliminated);
        }
        printlnMessage();

        printlnMessage("CROSS-CONSTRUCT PHASE.");
        printlnMessage("----------------------");
        printlnMessage();
        crossConstructPhase2(clustering, eliminated);
        printlnMessage();

        printlnMessage(

                "------------------------------------------------------");
        printlnMessage("Output Measurement Model");
        final List output = buildSolution(clustering, eliminated);
        printClustering(output, eliminated);

        return output;
    }

    private void intraConstructPhase2(final int[] _cluster, final boolean[] eliminated) {
        final List<Integer> cluster = new ArrayList<>();
        for (final int i : _cluster) cluster.add(i);
        double cutoff = this.tetradTest.getSignificance();

        if (this.doFdr) {
            final List<Double> allPValues = new ArrayList<>(listPValues(cluster, eliminated, Double.MAX_VALUE));
            System.out.println("# p values for this cluster: " + allPValues.size());
            Collections.sort(allPValues);

            cutoff = 1.;

            for (int c = 0; c < allPValues.size(); c++) {
                if (allPValues.get(c) >= this.tetradTest.getSignificance() * (c + 1.) / allPValues.size()) {
                    cutoff = allPValues.get(c);
                    break;
                }
            }

            System.out.println("FDR cutoff = " + cutoff);
        }

        final List<Double> pValues2 = listPValues(cluster, eliminated, cutoff);

        if (pValues2 == null) {
            System.out.println("Nothing to count.");
            return;
        }

        int numImpurities = pValues2.size();

        System.out.println("Num impurities going in = " + numImpurities);

        final int minImpurities = 0;

        while (numImpurities > 0) {
            System.out.println("Num impurities this round = " + numImpurities);

            int min = Integer.MAX_VALUE;
            int minIndex = -1;
            List<Integer> minList = new ArrayList<>();

            for (final int i : cluster) {
                if (eliminated[i]) continue;

                eliminated[i] = true;
                final List<Double> pValues = listPValues(cluster, eliminated, cutoff);

                if (pValues == null) {
                    eliminated[i] = false;
                    continue;
                }

                System.out.println("Tried dropping " + this.tetradTest.getVarNames()[i] + " (" + pValues.size()
                        + " impurities)");

                eliminated[i] = false;

                if (pValues.size() < min) {
                    min = pValues.size();
                    minIndex = i;
                    numImpurities = min;
                    minList = new ArrayList<>();
                    minList.add(i);
                } else if (pValues.size() == min) {
                    minList.add(i);
                }
            }

            if (minList.isEmpty()) {
                break;
            }

            if (minIndex != -1) {
                if (min < minImpurities) break;

                final int index = minList.get(RandomUtil.getInstance().nextInt(minList.size()));

//                eliminated[index] = true;
//                numImpurities = min;
//                System.out.println("Dropped " + tetradTest.getVarNames()[index]);

                for (int m = 0; m < minList.size(); m++) {
                    eliminated[index] = true;
                    numImpurities = min;
                    System.out.println("Dropped " + this.tetradTest.getVarNames()[index]);
                }
            }
        }
    }

    private void crossConstructPhase2(final List<int[]> clustering, final boolean[] eliminated) {
        double cutoff = this.tetradTest.getSignificance();

        if (this.doFdr) {
            final List<Double> allPValues = new ArrayList<>(listCrossConstructPValues(clustering, eliminated, Double.MAX_VALUE));
            System.out.println("Num p values cross clusters: " + allPValues.size());

            if (allPValues.isEmpty()) return;

            Collections.sort(allPValues);
            System.out.println("# p values = " + allPValues.size());
            cutoff = 1.;

            System.out.println("significance = " + this.tetradTest.getSignificance());

            for (int c = 0; c < allPValues.size(); c++) {
                if (allPValues.get(c) >= this.tetradTest.getSignificance() * (c + 1.) / allPValues.size()) {
                    cutoff = allPValues.get(c);
                    break;
                }
            }
        }

        final List<Double> pValues = listCrossConstructPValues(clustering, eliminated, cutoff);

        if (pValues == null) {
            System.out.println("Nothing to count.");
            return;
        }

        int numImpurities = pValues.size();
        final int minImpurities = 0;

        while (numImpurities > 0) {
            System.out.println("Num impurities this round = " + numImpurities);

            int min = Integer.MAX_VALUE;
            int minIndex = -1;
            List<Integer> minList = new ArrayList<>();

            for (int i = 0; i < eliminated.length; i++) {
                if (eliminated[i]) continue;

                eliminated[i] = true;
                final List<Double> pValuesCrossConstruct = listCrossConstructPValues(clustering, eliminated, cutoff);

                if (pValuesCrossConstruct == null) {
                    eliminated[i] = false;
                    continue;
                }

                System.out.println("Tried dropping " + this.tetradTest.getVarNames()[i] + " (" + pValuesCrossConstruct.size()
                        + " impurities)");
                eliminated[i] = false;

                if (pValuesCrossConstruct.size() < min) {
                    min = pValuesCrossConstruct.size();
                    minIndex = i;
                    numImpurities = min;
                    minList = new ArrayList<>();
                    minList.add(i);
                } else if (pValuesCrossConstruct.size() == min) {
                    minList.add(i);
                }
            }

            if (minList.isEmpty()) {
                break;
            }

            if (minIndex != -1) {
                if (min < minImpurities) break;

                final int index = minList.get(RandomUtil.getInstance().nextInt(minList.size()));

                eliminated[index] = true;
                numImpurities = min;
                System.out.println("Dropped " + this.tetradTest.getVarNames()[index]);
            }
        }

        printClustering(clustering, eliminated);
    }

    private List<Double> listCrossConstructPValues(final List<int[]> clustering, final boolean[] eliminated, final double cutoff) {
        final List<Double> allPValues = new ArrayList<>();
        boolean countable = false;

        for (int p1 = 0; p1 < clustering.size(); p1++) {
            for (int p2 = p1 + 1; p2 < clustering.size(); p2++) {
                final int[] cluster1 = clustering.get(p1);
                final int[] cluster2 = clustering.get(p2);

                if (cluster1.length >= 3 && cluster2.length >= 1) {
                    final ChoiceGenerator gen1 = new ChoiceGenerator(cluster1.length, 3);
                    int[] choice1;

                    while ((choice1 = gen1.next()) != null) {
                        final ChoiceGenerator gen2 = new ChoiceGenerator(cluster2.length, 1);
                        int[] choice2;

                        while ((choice2 = gen2.next()) != null) {
                            final List<Integer> crossCluster = new ArrayList<>();
                            for (final int i : choice1) crossCluster.add(cluster1[i]);
                            for (final int i : choice2) crossCluster.add(cluster2[i]);
                            final List<Double> pValues = listPValues(crossCluster, eliminated, cutoff);

                            if (pValues != null) {
                                countable = true;
                                allPValues.addAll(pValues);
                            }
                        }
                    }
                }

                if (cluster2.length >= 3 && cluster1.length >= 1) {
                    final ChoiceGenerator gen1 = new ChoiceGenerator(cluster2.length, 3);
                    int[] choice1;

                    while ((choice1 = gen1.next()) != null) {
                        final ChoiceGenerator gen2 = new ChoiceGenerator(cluster1.length, 1);
                        int[] choice2;

                        while ((choice2 = gen2.next()) != null) {
                            final List<Integer> crossCluster = new ArrayList<>();
                            for (final int i : choice1) crossCluster.add(cluster2[i]);
                            for (final int i : choice2) crossCluster.add(cluster1[i]);
                            final List<Double> pValues = listPValues(crossCluster, eliminated, cutoff);

                            if (pValues != null) {
                                countable = true;
                                allPValues.addAll(pValues);
                            }
                        }
                    }
                }

                if (cluster1.length >= 2 && cluster2.length >= 2) {
                    final ChoiceGenerator gen1 = new ChoiceGenerator(cluster1.length, 2);
                    int[] choice1;

                    while ((choice1 = gen1.next()) != null) {
                        final ChoiceGenerator gen2 = new ChoiceGenerator(cluster2.length, 2);
                        int[] choice2;

                        while ((choice2 = gen2.next()) != null) {
                            final List<Integer> crossCluster = new ArrayList<>();
                            for (final int i : choice1) crossCluster.add(cluster1[i]);
                            for (final int i : choice2) crossCluster.add(cluster2[i]);

                            final List<Double> pValues = listPValues2by2(crossCluster, eliminated, cutoff);

                            if (pValues != null) {
                                countable = true;
                                allPValues.addAll(pValues);
                            }
                        }
                    }
                }
            }
        }

        return countable ? allPValues : null;
    }


    private List<Double> listPValues(final List<Integer> cluster, final boolean[] eliminated, final double cutoff) {
        if (cluster.size() < 4) return null;
        boolean countable = false;

        final List<Double> pValues = new ArrayList<>();
        final ChoiceGenerator gen = new ChoiceGenerator(cluster.size(), 4);
        int[] choice;

        while ((choice = gen.next()) != null) {
            final int i = choice[0];
            final int j = choice[1];
            final int k = choice[2];
            final int l = choice[3];

            final int ci = cluster.get(i);
            final int cj = cluster.get(j);
            final int ck = cluster.get(k);
            final int cl = cluster.get(l);

            if (eliminated[ci] || eliminated[cj] || eliminated[ck] || eliminated[cl]) {
                continue;
            }

            countable = true;

            final double p1 = this.tetradTest.tetradPValue(ci, cj, ck, cl);
            final double p2 = this.tetradTest.tetradPValue(ci, cl, cj, ck);
            final double p3 = this.tetradTest.tetradPValue(ci, ck, cj, cl);

            if (p1 < cutoff) {
                printTetrad(ci, cj, ck, cl, p1);
                pValues.add(p1);
            }

            if (p2 < cutoff) {
                printTetrad(ci, cl, cj, ck, p2);
                pValues.add(p2);
            }

            if (p3 < cutoff) {
                printTetrad(ci, ck, cj, cl, p3);
                pValues.add(p3);
            }
        }

        return countable ? pValues : null;
    }

    private void printTetrad(final int ci, final int cj, final int ck, final int cl, final double p1) {
        if (this.listTetrads) {
            final String[] varNames = this.tetradTest.getVarNames();
            System.out.println("Tetrad <" + varNames[ci] + ", " + varNames[cj] + ", " + varNames[ck] + ", " + varNames[cl] + "> p = " + p1);
        }
    }

    private List<Double> listPValues2by2(final List<Integer> cluster, final boolean[] eliminated, final double cutoff) {
        if (cluster.size() < 4) return new ArrayList<>();

        final List<Double> pValues = new ArrayList<>();

        final int x = cluster.get(0);
        final int z = cluster.get(1);
        final int y = cluster.get(2);
        final int w = cluster.get(3);

        if (eliminated[x] || eliminated[z] || eliminated[y] || eliminated[w]) {
            return null;
        }

        final double p1 = this.tetradTest.tetradPValue(x, y, w, z);

        if (p1 < cutoff) {
            printTetrad(x, y, w, z, p1);
            pValues.add(p1);
        }


        return pValues;
    }


    private void printMessage(final String message) {
        if (this.outputMessage) {
            System.out.print(message);
        }
    }

    private void printlnMessage(final String message) {
        if (this.outputMessage) {
            System.out.println(message);
        }
    }

    private void printlnMessage() {
        if (this.outputMessage) {
            System.out.println();
        }
    }

    private void printClustering(final List clustering, final boolean[] eliminated) {
        final Iterator it = clustering.iterator();
        while (it.hasNext()) {
            final int[] c = (int[]) it.next();
            printCluster(c, eliminated);
        }
    }

    private void printCluster(final int[] c, final boolean[] eliminated) {
        final String[] sorted = new String[c.length];
        for (int i = 0; i < c.length; i++) {
            sorted[i] = this.tetradTest.getVarNames()[c[i]];
            if (eliminated[c[i]]) sorted[i] = sorted[i] + "X";
        }
        for (int i = 0; i < sorted.length - 1; i++) {
            String min = sorted[i];
            int min_idx = i;
            for (int j = i + 1; j < sorted.length; j++) {
                if (sorted[j].compareTo(min) < 0) {
                    min = sorted[j];
                    min_idx = j;
                }
            }
            final String temp = sorted[i];
            sorted[i] = min;
            sorted[min_idx] = temp;
        }
        for (int i = 0; i < sorted.length; i++) {
            printMessage(sorted[i] + " ");
        }
        printlnMessage();
    }

    private List buildSolution(final List clustering, final boolean[] eliminated) {
        final List solution = new ArrayList();
        final Iterator it = clustering.iterator();
        while (it.hasNext()) {
            final int[] next = (int[]) it.next();
            final int[] draftArea = new int[next.length];
            int draftCount = 0;
            for (int i = 0; i < next.length; i++) {
                if (!eliminated[next[i]]) {
                    draftArea[draftCount++] = next[i];
                }
            }
            final int[] realCluster = new int[draftCount];
            System.arraycopy(draftArea, 0, realCluster, 0, draftCount);
            solution.add(realCluster);
        }
        return solution;
    }
}


