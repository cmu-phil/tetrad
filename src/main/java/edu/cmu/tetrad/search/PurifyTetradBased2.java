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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A clean-up of Ricardo's tetrad-based purify 2.
 *
 * @author Joe Ramsey
 */
public class PurifyTetradBased2 implements IPurify {
    private boolean outputMessage = true;
    private TetradTest tetradTest;
    private int numVars;
    boolean doFdr = false;
    boolean listTetrads = false;
    private final int PURE = 0;
    private final int IMPURE = 1;
    private final int UNDEFINED = 2;


    public PurifyTetradBased2(TetradTest tetradTest) {
        this.tetradTest = tetradTest;
        this.numVars = tetradTest.getVarNames().length;
    }

    public List<List<Node>> purify(List<List<Node>> clustering) {
        List<int[]> _clustering = convertListToInt(clustering);
        List<int[]> _clustering2 = tetradBasedPurify2(_clustering);
        return convertIntToList(_clustering2);
    }

    public void setTrueGraph(Graph mim) {
        throw new UnsupportedOperationException();
    }

    private List tetradBasedPurify2(List clustering) {
        boolean impurities[][] = tetradBasedMarkImpurities(clustering);
        List solution = findInducedPureGraph(clustering, impurities);
        if (solution != null) {
            printlnMessage(">> SIZE: " + sizeCluster(solution));
            printlnMessage(">> New solution found!");
        }
        return solution;
    }

    /**
     * Verify if a pair of indicators is impure, or if there is no evidence they are pure.
     */
    private boolean[][] tetradBasedMarkImpurities(List clustering) {
        printlnMessage("   (searching for impurities....)");
        int relations[][] = new int[numVars][numVars];
        for (int i = 0; i < numVars; i++) {
            for (int j = 0; j < numVars; j++) {
                if (i == j) {
                    relations[i][j] = PURE;
                } else {
                    relations[i][j] = UNDEFINED;
                }
            }
        }

        //Find intra-construct impurities
        for (int i = 0; i < clustering.size(); i++) {
            int cluster1[] = (int[]) clustering.get(i);
            if (cluster1.length < 3) {
                continue;
            }
            for (int j = 0; j < cluster1.length - 1; j++) {
                for (int k = j + 1; k < cluster1.length; k++) {
                    if (relations[cluster1[j]][cluster1[k]] == UNDEFINED) {
                        boolean found = false;
                        //Try to find a 3x1 foursome that includes j and k
                        for (int q = 0; q < cluster1.length && !found; q++) {
                            if (j == q || k == q) {
                                continue;
                            }
                            for (int l = 0; l < clustering.size() && !found; l++) {
                                int cluster2[] = (int[]) clustering.get(l);
                                for (int w = 0; w < cluster2.length && !found; w++) {
                                    if (l == i && (j == w || k == w || q == w)) {
                                        continue;
                                    }
                                    if (tetradTest.tetradScore3(cluster1[j],
                                            cluster1[k], cluster1[q],
                                            cluster2[w])) {
                                        found = true;
                                        relations[cluster1[j]][cluster1[k]] =
                                                relations[cluster1[k]][cluster1[j]] =
                                                        PURE;
                                        relations[cluster1[j]][cluster1[q]] =
                                                relations[cluster1[q]][cluster1[j]] =
                                                        PURE;
                                        relations[cluster1[k]][cluster1[q]] =
                                                relations[cluster1[q]][cluster1[k]] =
                                                        PURE;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        //Find cross-construct impurities
        for (int i = 0; i < clustering.size(); i++) {
            int cluster1[] = (int[]) clustering.get(i);
            for (int j = 0; j < clustering.size(); j++) {
                if (i == j) {
                    continue;
                }
                int cluster2[] = (int[]) clustering.get(j);
                for (int v1 = 0; v1 < cluster1.length; v1++) {
                    for (int v2 = 0; v2 < cluster2.length; v2++) {
                        if (relations[cluster1[v1]][cluster2[v2]] == UNDEFINED) {
                            boolean found1 = false;
                            //Try first to find a 3x1 foursome, with 3 elements
                            //in cluster1
                            if (cluster1.length < 3) {
                                found1 = true;
                            }
                            for (int v3 = 0;
                                 v3 < cluster1.length && !found1; v3++) {
                                if (v3 == v1 ||
                                        relations[cluster1[v1]][cluster1[v3]] ==
                                                IMPURE ||
                                        relations[cluster2[v2]][cluster1[v3]] ==
                                                IMPURE) {
                                    continue;
                                }
                                for (int v4 = 0;
                                     v4 < cluster1.length && !found1; v4++) {
                                    if (v4 == v1 || v4 == v3 ||
                                            relations[cluster1[v1]][cluster1[v4]] ==
                                                    IMPURE ||
                                            relations[cluster2[v2]][cluster1[v4]] ==
                                                    IMPURE ||
                                            relations[cluster1[v3]][cluster1[v4]] ==
                                                    IMPURE) {
                                        continue;
                                    }
                                    if (tetradTest.tetradScore3(cluster1[v1],
                                            cluster2[v2], cluster1[v3],
                                            cluster1[v4])) {
                                        found1 = true;
                                    }
                                }
                            }
                            if (!found1) {
                                continue;
                            }
                            boolean found2 = false;
                            //Try to find a 3x1 foursome, now with 3 elements
                            //in cluster2
                            if (cluster2.length < 3) {
                                found2 = true;
                                relations[cluster1[v1]][cluster2[v2]] =
                                        relations[cluster2[v2]][cluster1[v1]] =
                                                PURE;
                                continue;
                            }
                            for (int v3 = 0;
                                 v3 < cluster2.length && !found2; v3++) {
                                if (v3 == v2 ||
                                        relations[cluster1[v1]][cluster2[v3]] ==
                                                IMPURE ||
                                        relations[cluster2[v2]][cluster2[v3]] ==
                                                IMPURE) {
                                    continue;
                                }
                                for (int v4 = 0;
                                     v4 < cluster2.length && !found2; v4++) {
                                    if (v4 == v2 || v4 == v3 ||
                                            relations[cluster1[v1]][cluster2[v4]] ==
                                                    IMPURE ||
                                            relations[cluster2[v2]][cluster2[v4]] ==
                                                    IMPURE ||
                                            relations[cluster2[v3]][cluster2[v4]] ==
                                                    IMPURE) {
                                        continue;
                                    }
                                    if (tetradTest.tetradScore3(cluster1[v1],
                                            cluster2[v2], cluster2[v3],
                                            cluster2[v4])) {
                                        found2 = true;
                                        relations[cluster1[v1]][cluster2[v2]] =
                                                relations[cluster2[v2]][cluster1[v1]] =
                                                        PURE;
                                    }
                                }
                            }

                        }
                    }
                }
            }
        }
        boolean impurities[][] = new boolean[numVars][numVars];
        for (int i = 0; i < numVars; i++) {
            for (int j = 0; j < numVars; j++) {
                if (relations[i][j] == IMPURE) {    // was undefined???
                    impurities[i][j] = true;
                } else {
                    impurities[i][j] = false;
                }
            }
        }
        return impurities;
    }


    private List findInducedPureGraph(List clustering, boolean impurities[][]) {
        //Store the ID of all elements for fast access
        int elements[][] = new int[sizeCluster(clustering)][3];
        int clusteringCount[] = new int[clustering.size()];
        int countElements = 0;
        for (int p = 0; p < clustering.size(); p++) {
            int cluster[] = (int[]) clustering.get(p);
            clusteringCount[p] = 0;
            for (int i = 0; i < cluster.length; i++) {
                elements[countElements][0] = cluster[i]; // global ID
                elements[countElements][1] = p;       // set mimClustering ID
                countElements++;
                clusteringCount[p]++;
            }
        }
        //Count how many impure relations are entailed by each indicator
        for (int i = 0; i < elements.length; i++) {
            elements[i][2] = 0;
            for (int j = 0; j < elements.length; j++) {
                if (impurities[elements[i][0]][elements[j][0]]) {
                    elements[i][2]++; // number of impure relations
                }
            }
        }

        //Iteratively eliminate impurities till some solution (or no solution) is found
        boolean eliminated[] = new boolean[this.numVars];
        for (int i = 0; i < elements.length; i++) {
            eliminated[elements[i][0]] = impurities[elements[i][0]][elements[i][0]];
        }

        return buildSolution2(elements, eliminated, clustering);
    }

    private List buildSolution2(int elements[][], boolean eliminated[],
                                List clustering) {
        List solution = new ArrayList();
        Iterator it = clustering.iterator();
        while (it.hasNext()) {
            int next[] = (int[]) it.next();
            int draftArea[] = new int[next.length];
            int draftCount = 0;
            for (int i = 0; i < next.length; i++) {
                for (int j = 0; j < elements.length; j++) {
                    if (elements[j][0] == next[i] &&
                            !eliminated[elements[j][0]]) {
                        draftArea[draftCount++] = next[i];
                        break; //? jdramsey 5/20/10
                    }
                }
            }
            if (draftCount > 0) {
                int realCluster[] = new int[draftCount];
                System.arraycopy(draftArea, 0, realCluster, 0, draftCount);
                solution.add(realCluster);
            }
        }
        if (solution.size() > 0) {
            return solution;
        } else {
            return null;
        }
    }


    private int sizeCluster(List cluster) {
        int total = 0;
        Iterator it = cluster.iterator();
        while (it.hasNext()) {
            int next[] = (int[]) it.next();
            total += next.length;
        }
        return total;
    }

    private List<int[]> convertListToInt(List<List<Node>> clustering) {
        List<Node> nodes = tetradTest.getVariables();
        List<int[]> _clustering = new ArrayList<int[]>();

        for (int i = 0; i < clustering.size(); i++) {
            List<Node> cluster = clustering.get(i);
            int[] _cluster = new int[cluster.size()];

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

    private List<List<Node>> convertIntToList(List<int[]> clustering) {
        List<Node> nodes = tetradTest.getVariables();
        List<List<Node>> _clustering = new ArrayList<List<Node>>();

        for (int i = 0; i < clustering.size(); i++) {
            int[] cluster = clustering.get(i);
            List<Node> _cluster = new ArrayList<Node>();

            for (int j = 0; j < cluster.length; j++) {
                _cluster.add(nodes.get(cluster[j]));
            }

            _clustering.add(_cluster);
        }

        return _clustering;
    }


    private void printlnMessage(String message) {
        if (outputMessage) {
            System.out.println(message);
        }
    }
}


