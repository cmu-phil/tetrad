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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * Utility to attach singleton (measured) variables as children of latent blocks using a Wilks rank-drop criterion,
 * analogous to the latent->latent hierarchy test.
 * <p>
 * For each singleton s and each latent L with indicator set C_L, we form D = V \ (C_L âª {s}) and compute:
 * <p>
 * drop_L(s) = rank({s}, D) - rank({s}, D | C_L)
 * <p>
 * If max_L drop_L(s) >= minDrop, we add the edge L* -> s, where L* maximizes the drop. Ties are broken by latent name
 * for determinism.
 */
public final class SingletonAttacher {

    private SingletonAttacher() {
    }

    /**
     * Returns latent->singleton edges to add.
     *
     * @param S              correlation/covariance matrix (SimpleMatrix)
     * @param sampleSize     number of rows
     * @param alpha          alpha for Wilks rank tests
     * @param minDrop        minimum required drop (e.g., 1)
     * @param p              number of observed variables (columns)
     * @param latentBlocks   list of indicator column indices for each latent (size>1)
     * @param latentNodes    parallel list of latent meta-nodes (same order as latentBlocks)
     * @param singletonCols  list of singleton column indices
     * @param singletonNodes parallel list of singleton meta-nodes (same order as singletonCols)
     * @return list of directed edges (latent -> singleton); does not modify any graph.
     */
    public static List<Edge> attachSingletonChildren(
            SimpleMatrix S,
            int sampleSize,
            double alpha,
            int minDrop,
            int p,
            List<List<Integer>> latentBlocks,
            List<Node> latentNodes,
            List<Integer> singletonCols,
            List<Node> singletonNodes
    ) {
        Objects.requireNonNull(S, "S");
        Objects.requireNonNull(latentBlocks, "latentBlocks");
        Objects.requireNonNull(latentNodes, "latentNodes");
        Objects.requireNonNull(singletonCols, "singletonCols");
        Objects.requireNonNull(singletonNodes, "singletonNodes");
        if (latentBlocks.size() != latentNodes.size())
            throw new IllegalArgumentException("latentBlocks and latentNodes must have same size");
        if (singletonCols.size() != singletonNodes.size())
            throw new IllegalArgumentException("singletonCols and singletonNodes must have same size");
        if (p <= 0) throw new IllegalArgumentException("p must be > 0");
        if (minDrop < 1) return Collections.emptyList(); // nothing to do

        // Precompute ALL = {0..p-1}
        final int[] ALL = new int[p];
        for (int i = 0; i < p; i++) ALL[i] = i;

        // BitSets for fast D = V \ (C_L âª {s})
        final List<BitSet> latentCovers = new ArrayList<>(latentBlocks.size());
        for (List<Integer> C : latentBlocks) {
            BitSet bs = new BitSet(p);
            for (int c : C) bs.set(c);
            latentCovers.add(bs);
        }

        List<Edge> edges = new ArrayList<>();

        for (int sPos = 0; sPos < singletonCols.size(); sPos++) {
            int s = singletonCols.get(sPos);
            Node sNode = singletonNodes.get(sPos);

            int bestDrop = Integer.MIN_VALUE;
            int bestLatentIdx = -1;

            for (int L = 0; L < latentBlocks.size(); L++) {
                List<Integer> C = latentBlocks.get(L);
                if (C.isEmpty()) continue; // should not happen (latents are size>1)

                // Build D = V \ (C âª {s})
                BitSet cover = (BitSet) latentCovers.get(L).clone();
                cover.set(s); // union {s}
                int[] D = complement(ALL, cover);
                if (D.length == 0) continue; // no valid right side

                int[] Sarr = new int[]{s};
                int r0 = RankTests.estimateWilksRank(S, Sarr, D, sampleSize, alpha);
                if (r0 <= 0) continue; // nothing to drop from

                int[] Carr = C.stream().mapToInt(Integer::intValue).toArray();
                int r1 = RankTests.estimateWilksRankConditioned(S, Sarr, D, Carr, sampleSize, alpha);

                int drop = r0 - r1;
                if (drop > bestDrop) {
                    bestDrop = drop;
                    bestLatentIdx = L;
                } else if (drop == bestDrop && drop >= minDrop && bestLatentIdx >= 0) {
                    // deterministic tie-breaker by latent name
                    String cur = latentNodes.get(L).getName();
                    String best = latentNodes.get(bestLatentIdx).getName();
                    if (cur.compareTo(best) < 0) bestLatentIdx = L;
                }
            }

            if (bestLatentIdx >= 0 && bestDrop >= minDrop) {
                Node parent = latentNodes.get(bestLatentIdx);
                edges.add(Edges.directedEdge(parent, sNode));
            }
        }

        return edges;
    }

    // --- helpers ---

    private static int[] complement(int[] universe, BitSet remove) {
        int keep = universe.length - remove.cardinality();
        if (keep <= 0) return new int[0];
        int[] out = new int[keep];
        int k = 0;
        for (int v : universe) if (!remove.get(v)) out[k++] = v;
        return out;
    }
}
