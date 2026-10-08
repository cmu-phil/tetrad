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

package edu.cmu.tetrad.search.rlcd;

import edu.cmu.tetrad.data.Knowledge;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntToDoubleFunction;

/**
 * Stage 2 of RLCD over one partition of observed variables: the rank-deficiency search for clusters, translating
 * the module-level functions <code>findClusters</code>, <code>findClusters_at_k</code>,
 * <code>findClusters_at_k_by_nonsinks</code>, <code>structuralRankTest</code>, and
 * <code>findClusters_finish</code> of <code>RLCD_alg.py</code> in causal-learn.
 * <p>
 * The outer loop tests cardinalities k = 1, 2, ..., maxK. For each k it iterates over subsets of the current latent
 * (and known non-leaf observed) covers to "unfold" into their children, and for each unfolding, over subsets of the
 * active non-sinks to condition on. Every candidate set A of covers is tested against the control set B of remaining
 * active covers for rank(Σ[A ∪ C, B ∪ C]) ≤ k, where C is the chosen set of non-sinks. Rank-deficient sets found
 * at a cardinality are merged into clusters, each cluster is placed under a cover of the rank found, and the search
 * restarts at k = 1. When no cardinality yields a cluster the finish step connects what remains: observed roots
 * become parents of the latent roots they are dependent on, or, if no observed roots remain, latent roots are
 * chained in a fixed order.
 * <p>
 * Two properties of the released Python are preserved and worth knowing: (1) a candidate set A whose observed
 * members (plus non-sinks) are not connected in the stage-1 skeleton is skipped, and this skip also applies when A
 * has no observed members at all, so sets consisting only of latent covers are never tested. Latent-latent edges are
 * still found by the rank tests, because unfolding one cluster into its observed children while keeping the other as
 * a cover supplies observed members; what is never tested directly is a set of two or more un-unfolded latent
 * covers. (2) The chain over leftover latent roots in the finish step is not data driven: its order is set-iteration
 * order in the Python and sorted cover-name order here, and the orientation between two latent roots with pure
 * indicators is not identifiable from rank constraints in any case.
 * <p>
 * The Python runs the per-non-sink-set searches in parallel processes; the results are combined only after all
 * finish, so a sequential run is equivalent.
 *
 * @author josephramsey (translation)
 */
public final class RlcdClusterSearch {
    private final List<String> xvars;
    private final RankTester rankTester;
    private final IntToDoubleFunction alphaForRank;
    private final int maxK;
    private final boolean allowNonLeafX;
    private final boolean unfoldCovers;
    private final boolean checkV;
    private Consumer<String> log = s -> {
    };
    private Knowledge knowledge = new Knowledge();

    /**
     * Constructs the search.
     *
     * @param xvars         names of all observed variables, in the column order used by the rank tester.
     * @param rankTester    the rank test.
     * @param alphaForRank  significance level as a function of the hypothesized rank.
     * @param maxK          the largest cardinality to search.
     * @param allowNonLeafX whether observed variables may be used as non-sinks (conditioned on).
     * @param unfoldCovers  whether discovered latent covers are unfolded into their children for further tests.
     * @param checkV        whether a rank-deficient set is discarded when a proper subset is already deficient.
     */
    public RlcdClusterSearch(List<String> xvars, RankTester rankTester, IntToDoubleFunction alphaForRank, int maxK,
                             boolean allowNonLeafX, boolean unfoldCovers, boolean checkV) {
        this.xvars = new ArrayList<>(xvars);
        this.rankTester = rankTester;
        this.alphaForRank = alphaForRank;
        this.maxK = maxK;
        this.allowNonLeafX = allowNonLeafX;
        this.unfoldCovers = unfoldCovers;
        this.checkV = checkV;
    }

    /**
     * Sets a log sink for the search trace.
     *
     * @param log the sink.
     */
    public void setLog(Consumer<String> log) {
        this.log = log == null ? s -> {
        } : log;
    }

    /**
     * Sets background knowledge over the observed variables. A non-sink c is not tested as a parent of a candidate
     * set containing an observed variable a when c → a is forbidden, and a cluster whose new cover would create a
     * forbidden observed-to-observed edge is rejected. Knowledge cannot refer to latent variables, which are created
     * by the search.
     *
     * @param knowledge the knowledge; null means none.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge == null ? new Knowledge() : knowledge;
    }

    /**
     * Runs the full stage-2 search on the structure (Python <code>findClusters</code> followed by the finish step).
     *
     * @param g the structure for one partition; modified in place.
     * @return the same structure.
     * @throws InterruptedException if interrupted.
     */
    public LatentGroups findClusters(LatentGroups g) throws InterruptedException {
        g.setLog(log);
        g.setKnowledge(knowledge);
        int k = 1;
        while (true) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            log.accept("--------------- Test Cardinality now k=" + k + " ---------------");

            List<List<Cover>> lPowerSet;
            if (unfoldCovers) {
                lPowerSet = generateLatentPowersetFromActiveSet(g);
            } else {
                lPowerSet = Collections.singletonList(Collections.emptyList());
            }
            Set<Cover> activeSetCopy = new LinkedHashSet<>(g.getActiveSet());
            Set<String> activeNonSinkSetCopy = new TreeSet<>(g.getActiveNonSinkSet());

            boolean found = false;
            boolean terminate = false;

            for (List<Cover> ls : lPowerSet) {
                Set<Cover> vPrime = new LinkedHashSet<>(activeSetCopy);
                Set<String> tPrime = new TreeSet<>(activeNonSinkSetCopy);

                for (Cover l : ls) {
                    Set<Cover> children = g.findChildren(l, true);
                    Set<Cover> measuredSubset = g.findMeasuredSubset(l);
                    for (Cover c : measuredSubset) tPrime.addAll(c.getVars());
                    // If L has only one child or none, do not replace it.
                    if (children.size() + measuredSubset.size() > 1) {
                        vPrime.remove(l);
                        vPrime.addAll(children);
                        vPrime.addAll(measuredSubset);
                    }
                }
                vPrime.addAll(g.findChildrenOfAllSubSets(ls));

                // Note: the Python reads G.activeSet here, which is the previous unfolding's V', not the copy.
                for (Cover cover : g.getActiveSet()) {
                    if (cover.size() == 1) {
                        Cover x = g.getObservedCoverByName(cover.takeOne());
                        if (x != null && !Boolean.TRUE.equals(x.getLeaf())) tPrime.addAll(cover.getVars());
                    }
                }

                g.setActiveSet(vPrime);
                g.setActiveNonSinkSet(allowNonLeafX ? tPrime : new TreeSet<>());

                log.accept("Unfolding " + ls);
                log.accept("G.activeSet " + g.getActiveSet());
                log.accept("G.activeNonSinkSet " + g.getActiveNonSinkSet());

                boolean[] result = findClustersAtK(g, k);
                found = result[0];
                terminate = result[1];

                if (found) {
                    g.updateActiveSet(false);
                    g.updateActiveNonSinkSet();
                }

                if (terminate) break;

                if (found) {
                    k = 1;
                    break;
                }
            }

            if (!found) {
                log.accept("Nothing found!");
                k++;
            }

            if (k > maxK) {
                log.accept("Procedure ending...");
                break;
            }
        }

        finish(g);
        g.updateActiveSet(true);
        return g;
    }

    /**
     * The subsets of the active covers known not to be leaves (latent covers and observed non-sinks), largest
     * subset first (Python <code>generateLatentPowersetFromActiveSet</code>).
     */
    private List<List<Cover>> generateLatentPowersetFromActiveSet(LatentGroups g) {
        List<Cover> ls = new ArrayList<>();
        for (Cover v : g.getActiveSet()) {
            if (Boolean.FALSE.equals(v.getLeaf())) ls.add(v);
        }
        ls.sort(Comparator.comparing(c -> CoverSets.orderedVarsString(Collections.singleton(c))));
        List<List<Cover>> power = CoverSets.powerset(ls);
        Collections.reverse(power);
        return power;
    }

    /**
     * One round of the search at cardinality k over all non-sink subsets (Python <code>findClusters_at_k</code>).
     *
     * @return {found, terminate}
     */
    private boolean[] findClustersAtK(LatentGroups g, int k) {
        log.accept("Starting searchClusters k=" + k + "...");
        boolean globalTerminate = true;
        boolean foundDeficiency = false;

        List<String> nonSinkList = new ArrayList<>(g.getActiveNonSinkSet());
        nonSinkList.sort(Comparator.reverseOrder());

        for (int numNonsinks = k; numNonsinks >= 0; numNonsinks--) {
            for (List<String> nonsinks : CoverSets.combinations(nonSinkList, numNonsinks)) {
                NonsinkResult r = findClustersAtKByNonsinks(g, k, nonsinks);
                foundDeficiency = foundDeficiency || r.found;
                globalTerminate = globalTerminate && r.terminate;
                for (Object[] add : r.toAdd) {
                    @SuppressWarnings("unchecked") Set<Cover> as = (Set<Cover>) add[0];
                    @SuppressWarnings("unchecked") List<String> ns = (List<String>) add[2];
                    g.addRankDefSet(as, (Integer) add[1], ns);
                }
            }
        }

        boolean globalFound = false;
        if (foundDeficiency) {
            g.determineClusters();
            boolean found = g.confirmClusters();
            globalFound = found;
            if (globalFound) {
                g.updateActiveSet(false);
                g.updateActiveNonSinkSet();
                log.accept(g.display());
            }
        }
        return new boolean[]{globalFound, globalTerminate};
    }

    private static final class NonsinkResult {
        boolean found;
        boolean terminate;
        final List<Object[]> toAdd = new ArrayList<>();
    }

    /**
     * The search at cardinality k for one choice of non-sinks (Python <code>findClusters_at_k_by_nonsinks</code>).
     */
    private NonsinkResult findClustersAtKByNonsinks(LatentGroups g, int k, List<String> nonsinks) {
        NonsinkResult res = new NonsinkResult();
        int numNonsinks = nonsinks.size();

        Set<Cover> currentActiveSet = new LinkedHashSet<>(g.getActiveSet());
        Set<Cover> currentChildrenOfNonAtomics = new LinkedHashSet<>(g.getChildrenOfNonAtomicsSet());
        for (String ns : nonsinks) {
            Cover c = g.getObservedCoverByName(ns);
            if (c != null) {
                currentActiveSet.remove(c);
                currentChildrenOfNonAtomics.remove(c);
            }
        }

        // To test rank k we need n >= 2k + 2 active variables.
        if (k - numNonsinks > CoverSets.setLength(currentActiveSet) / 2.0 - 1) {
            res.terminate = true;
            return res;
        }

        if (k != numNonsinks) {
            // Neighbors outside the partition are never in X_dict, so this is a no-op in practice; kept for fidelity.
            for (String nb : g.getAllNbSet()) {
                Cover c = g.getObservedCoverByName(nb);
                if (c != null) currentActiveSet.remove(c);
            }
        }

        List<Set<Cover>> allSubsets = new ArrayList<>(CoverSets.generateSubsetMinimal(currentActiveSet, k - numNonsinks));

        for (Cover v : currentActiveSet) {
            if (v.size() >= k - numNonsinks + 1 && k - numNonsinks != 0) {
                Set<Cover> temp = new LinkedHashSet<>(currentActiveSet);
                temp.remove(v);
                for (Set<Cover> x : CoverSets.generateSubsetMinimal(temp, 0)) {
                    Set<Cover> with = new LinkedHashSet<>(x);
                    with.add(v);
                    allSubsets.add(with);
                }
            }
        }

        if (allSubsets.size() == 1 && allSubsets.get(0).isEmpty()) {
            res.terminate = true;
            return res;
        }

        Set<Cover> nonsinkCovers = new LinkedHashSet<>();
        for (String ns : nonsinks) {
            Cover c = g.getObservedCoverByName(ns);
            if (c != null) nonsinkCovers.add(c);
        }

        for (Set<Cover> as : allSubsets) {
            Set<Cover> effectiveChildrenOfNonAtomics = new LinkedHashSet<>(currentChildrenOfNonAtomics);
            Set<Cover> tempSet = new LinkedHashSet<>(as);
            tempSet.addAll(nonsinkCovers);
            for (Cover cover : tempSet) {
                effectiveChildrenOfNonAtomics.removeAll(g.findDescendants(cover, false));
            }

            Set<Cover> union = new LinkedHashSet<>(currentActiveSet);
            union.addAll(effectiveChildrenOfNonAtomics);
            Set<Cover> bs = CoverSets.setDifference(union, as);

            // The observed members of As, with the non-sinks, must be connected in the stage-1 skeleton.
            Set<String> observedInAs = new TreeSet<>();
            for (Cover x : as) if (x.isObserved()) observedInAs.addAll(x.getVars());
            observedInAs.addAll(nonsinks);
            if (!connectedInLocalAdj(g, observedInAs)) continue;

            if (CoverSets.setLength(bs) <= k - numNonsinks) continue;
            if (g.containsCluster(as, nonsinks)) continue;
            if (g.overlapPaCh(as)) continue;
            if (g.measuredHasNonSinks(as, nonsinks)) continue;
            if (g.checkNonSinksAreAsChildren(as, nonsinks)) continue;
            if (knowledgeForbids(as, nonsinks)) continue;
            // Bs must have parent cardinality > k - |nonsinks|, otherwise rank <= k regardless of As.
            if (g.parentCardinality(bs) <= k - numNonsinks) continue;

            int[] test = structuralRankTest(g, as, bs, k, nonsinks);
            if (test[0] == 1) {
                log.accept("   " + as + " is rank deficient! given " + nonsinks + ", Bs:" + bs);
                boolean vStructureFound = false;
                if (checkV) {
                    for (int numCollider = 1; numCollider <= k - numNonsinks; numCollider++) {
                        int numSubAs = k - numNonsinks + 1 - numCollider;
                        for (Set<Cover> subAs : CoverSets.generateSubsetMinimal(as, numSubAs - 1)) {
                            int[] sub = structuralRankTest(g, subAs, bs, numNonsinks + numSubAs - 1, nonsinks);
                            if (sub[0] == 1) {
                                log.accept("   " + as + " has v structure! subAs:" + subAs + " given " + nonsinks
                                           + ", Bs:" + bs);
                                vStructureFound = true;
                            }
                        }
                    }
                }
                if (!vStructureFound) {
                    res.toAdd.add(new Object[]{as, test[1], new ArrayList<>(nonsinks)});
                    res.found = true;
                }
            }
        }
        return res;
    }

    /**
     * Whether background knowledge forbids some non-sink from being a parent of some observed member of As. The
     * non-sinks of a confirmed cluster become parents of its members, so such a candidate is not tested.
     */
    private boolean knowledgeForbids(Set<Cover> as, List<String> nonsinks) {
        if (nonsinks.isEmpty()) return false;
        for (Cover a : as) {
            if (!a.isObserved()) continue;
            for (String av : a.getVars()) {
                for (String c : nonsinks) {
                    if (knowledge.isForbidden(c, av)) return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the named observed variables form a connected subgraph of the partition's stage-1 skeleton (Python
     * <code>check_dsu</code>). As in the Python, an empty name set is not connected.
     */
    private boolean connectedInLocalAdj(LatentGroups g, Set<String> names) {
        List<Integer> idx = new ArrayList<>();
        for (String s : names) idx.add(g.getXNames().indexOf(s));
        int n = idx.size();
        if (n == 0) return false;
        int[] root = new int[n];
        for (int i = 0; i < n; i++) root[i] = i;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j && g.isLocallyAdjacent(idx.get(i), idx.get(j))) {
                    int a = find(root, i), b = find(root, j);
                    if (a != b) root[b] = a;
                }
            }
        }
        int r0 = find(root, 0);
        for (int i = 1; i < n; i++) if (find(root, i) != r0) return false;
        return true;
    }

    private static int find(int[] root, int k) {
        while (root[k] != k) {
            root[k] = root[root[k]];
            k = root[k];
        }
        return k;
    }

    /**
     * Tests whether As forms a cluster of rank at most k against Bs given the non-sinks (Python
     * <code>structuralRankTest</code>): both sides are expanded to their measured descendants and the non-sinks are
     * added to both. If the null is not rejected, the lowest rank not rejected is found by testing downward.
     *
     * @return {1 if not rejected else 0, lowest rank not rejected (or −1)}
     */
    private int[] structuralRankTest(LatentGroups g, Set<Cover> as, Set<Cover> bs, int k, List<String> nonLeafs) {
        Set<String> aMeasures = new TreeSet<>(CoverSets.vars(g.pickAllMeasures(as)));
        Set<String> bMeasures = new TreeSet<>(CoverSets.vars(g.pickAllMeasures(bs)));
        aMeasures.addAll(nonLeafs);
        bMeasures.addAll(nonLeafs);

        int[] pcols = aMeasures.stream().mapToInt(xvars::indexOf).toArray();
        int[] qcols = bMeasures.stream().mapToInt(xvars::indexOf).toArray();

        boolean failToReject = rankTester.failToReject(pcols, qcols, k, alphaForRank.applyAsDouble(k));
        if (!failToReject) return new int[]{0, -1};

        int minRank = k;
        for (int h0 = k - 1; h0 >= 0; h0--) {
            if (rankTester.failToReject(pcols, qcols, h0, alphaForRank.applyAsDouble(h0))) {
                minRank = h0;
            } else {
                break;
            }
        }
        return new int[]{1, minRank};
    }

    /**
     * Completes the structure when no more clusters can be found (Python <code>findClusters_finish</code>).
     */
    private void finish(LatentGroups g) {
        log.accept("--------------- Finishing ... ---------------");
        g.updateActiveSet(true);
        if (g.getActiveSet().size() == 1) {
            log.accept(g.display());
            return;
        }

        g.updateActiveSet(false);
        List<Cover> remain = CoverSets.sorted(g.getActiveSet());
        List<Cover> remainLatent = new ArrayList<>();
        List<Cover> remainObserved = new ArrayList<>();
        for (Cover c : remain) {
            if (!c.isObserved()) {
                if (c.isAtomic()) remainLatent.add(c);
            } else {
                remainObserved.add(c);
            }
        }

        if (!remainObserved.isEmpty()) {
            for (Cover o : remainObserved) {
                Set<Cover> toAdd = new LinkedHashSet<>();
                for (Cover l : remainLatent) {
                    int[] test = structuralRankTest(g, Collections.singleton(o), Collections.singleton(l), 0,
                            Collections.emptyList());
                    if (test[0] == 0) toAdd.add(l);
                }
                if (!toAdd.isEmpty()) {
                    g.addOrUpdateCover(o, toAdd);
                    g.updateActiveSet(false);
                }
            }
        } else {
            g.updateActiveSet(true);
            List<Cover> latents = new ArrayList<>();
            for (Cover c : CoverSets.sorted(g.getActiveSet())) {
                if (!c.isObserved()) latents.add(c);
            }
            if (latents.size() >= 2) {
                for (int i = 0; i < latents.size() - 1; i++) {
                    g.addOrUpdateCover(latents.get(i), new LinkedHashSet<>(Collections.singleton(latents.get(i + 1))));
                    g.updateActiveSet(false);
                }
            }
        }
        log.accept(g.display());
    }
}
