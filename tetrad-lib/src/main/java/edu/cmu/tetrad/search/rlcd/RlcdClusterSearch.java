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
 * chained in a fixed order by undirected edges.
 * <p>
 * Two properties of the released Python are worth knowing: (1) a candidate set A whose observed members (plus
 * non-sinks) are not connected in the stage-1 skeleton is skipped. In the Python this skip also applies when A has
 * no observed members at all, so sets consisting only of latent covers are never tested, and a latent all of whose
 * children are latent is never found; here such a set is checked through its measured descendants and then tested
 * (see below). (2) The chain over leftover latent roots in the finish step is not data driven: its order is set-iteration
 * order in the Python and sorted cover-name order here, and no test is made of whether consecutive roots in it are
 * dependent.
 * <p>
 * Deliberate differences from the Python. Sets consisting only of latent covers are tested, as just described;
 * the paper's search (Algorithm 3 of Dong et al.) draws its candidate sets from all active covers and has no such
 * exclusion. A set of covers found to have rank 0 against the rest, that is, not
 * found dependent on them at all, is not made a cluster; the Python makes it a 0-cluster under a cover with no
 * variables, which removes its members from the search and discards the clusters of higher rank found in the same
 * round. And the Python writes the finish-step chain as directed edges, in an order that depends on
 * its hash seed. The orientation between two latent roots with pure indicators is not identifiable from rank
 * constraints, so here the chain's edges are undirected. The adjacencies are as in the Python. Finally, the
 * clusters found in one round are added in order of how many observed variables they account for, not in the order
 * found; see {@link LatentGroups#confirmClusters()}.
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
    private boolean observedExplanationCheck = false;
    private double observedExplanationAlpha = 0.05;
    private int observedExplanationMaxSets = 20000;
    private int observedExplanationMaxSize = 3;
    private int explainedAway = 0;

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
     * Sets whether a rank deficiency is checked for an observed explanation before it is accepted as a cluster
     * (default false). A set As found rank deficient against Bs given the non-sinks, at a rank that would introduce
     * t new latents, is accepted only if no set T of t observed variables (from the partition, outside the
     * non-sinks; members of As are allowed, since a choke can sit inside) makes As and Bs rank-independent given T
     * and the non-sinks -- that is, only if no t observed variables explain the deficiency as well as t latents
     * would. The test adds T to both sides, so "rank at most m given T" is "partial cross-rank zero".
     * <p>
     * This is what keeps a Markov chain from being read as a measurement model: the later half of a chain is rank
     * one against the earlier half because the variable between them separates them, and that variable is the T
     * that explains it. It also removes the population-level rank deficiencies that a dense observed DAG implies
     * (about two thirds of the spurious latents on latent-free data, measured October 2026), which no implied-
     * constraint check can remove because they are real. It costs nothing on true measurement models at N = 2000;
     * at small N an underpowered explanation test can explain a true cluster away by accident, which is why its
     * alpha is separate and larger by default: a larger alpha rejects more readily, so fewer accidental
     * explanations.
     *
     * <b>Two halves.</b> The explanation test above is the first; it is kept to sets of at most t variables, since
     * wider sets drawn from inside a cluster explain every true cluster (enough indicators screen the rest). A chain
     * of observed variables defeats it: the chain's segments are rank deficient against everything else, the search
     * underestimates their rank when the chain has weak skip edges, and no explainer narrower than the whole other
     * side exists. The second half catches those: after the deficient sets of a round are merged into clusters and
     * before they are confirmed, each cluster's measured members are tested pairwise for independence given up to
     * two other members. Pure indicators of one latent are never so separated; members of a chain are; a cluster
     * with a separated pair is dropped as observed structure. Three mutually adjacent variables have no separated
     * pair and are a saturated model either way, so a triangle stays a latent whatever it really is.
     *
     * @param check   whether to check.
     * @param alpha   the alpha of both tests (default 0.05; larger is more protective of true clusters).
     * @param maxSize the largest explaining set tried, capped at t (default 3).
     * @param maxSets the most candidate sets tried per deficiency, for either half (default 20000).
     */
    public void setObservedExplanationCheck(boolean check, double alpha, int maxSize, int maxSets) {
        this.observedExplanationCheck = check;
        this.observedExplanationAlpha = alpha;
        this.observedExplanationMaxSize = maxSize;
        this.observedExplanationMaxSets = maxSets;
    }

    /**
     * The number of rank deficiencies this search found and then explained away by observed variables, so declined
     * to make clusters of. Diagnostic.
     *
     * @return the count.
     */
    public int getExplainedAway() {
        return explainedAway;
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
            if (observedExplanationCheck) {
                // The merged cluster is what gets confirmed, so the within-set check applies here: three mutually
                // adjacent chain members pass it one triangle at a time and fail it once merged into a segment.
                int removed = g.filterClusters(vs -> {
                    String separated = separatedWithin(g, vs, List.of());
                    if (separated != null) {
                        log.accept("   Merged cluster " + vs + " has members independent given other members: "
                                   + separated + "; not a measurement cluster, dropped.");
                    }
                    return separated == null;
                });
                explainedAway += removed;
            }
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

            // A set made only of latent covers, with no non-sinks, has no observed members to check. The Python
            // skips every such set, apparently by accident (its connectivity check returns false for an empty
            // list), so a latent whose children are all latent can never be found. Here the check is made on the
            // measured descendants of the covers instead.
            if (observedInAs.isEmpty()) {
                observedInAs.addAll(CoverSets.vars(g.pickAllMeasures(as)));
            }

            if (!connectedInLocalAdj(g, observedInAs)) continue;

            if (CoverSets.setLength(bs) <= k - numNonsinks) continue;
            if (g.containsCluster(as, nonsinks)) continue;
            if (g.overlapPaCh(as)) continue;
            if (g.measuredHasNonSinks(as, nonsinks)) continue;
            if (g.checkNonSinksAreAsChildren(as, nonsinks)) continue;
            if (knowledgeForbids(g, as, nonsinks)) continue;
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
                if (!vStructureFound && test[1] == 0) {
                    // Rank 0: As was not found dependent on Bs at all, so there is no latent to put it under.
                    // Queued as a 0-cluster it would be given a cover with no variables, and, being of the lowest
                    // rank, it would also displace every cluster of rank 1 or more found in this round. (The
                    // Python does queue it, and parks As under an empty cover.) Inside a stage-1 clique this is
                    // usually a test without the power to see a weak dependence, not real independence.
                    log.accept("   " + as + " is not dependent on Bs (rank 0); no cluster. Bs:" + bs);
                } else if (!vStructureFound) {
                    List<String> explanation = observedExplanationCheck
                            ? observedExplanation(g, as, bs, test[1], nonsinks) : null;
                    if (explanation != null) {
                        explainedAway++;
                        log.accept("   " + as + " at rank " + test[1] + " is explained by observed " + explanation
                                   + " given " + nonsinks + "; no cluster.");
                    } else {
                        res.toAdd.add(new Object[]{as, test[1], new ArrayList<>(nonsinks)});
                        res.found = true;
                    }
                }
            }
        }
        return res;
    }

    /**
     * Whether background knowledge forbids some non-sink from being a parent of some observed member of As. The
     * non-sinks of a confirmed cluster become parents of its members, so such a candidate is not tested. Nor is one
     * in which the temporal tiers forbid some non-sink from being an ancestor of an observed descendant of As
     * (through latent members of As); {@link LatentGroups#addCluster} makes the full ancestry check.
     */
    private boolean knowledgeForbids(LatentGroups g, Set<Cover> as, List<String> nonsinks) {
        if (nonsinks.isEmpty()) return false;
        for (Cover a : as) {
            if (!a.isObserved()) continue;
            for (String av : a.getVars()) {
                for (String c : nonsinks) {
                    if (knowledge.isForbidden(c, av)) return true;
                }
            }
        }
        return TierPrecedence.findViolation(knowledge, g.observedAncestors(nonsinks),
                g.observedDescendants(CoverSets.vars(as))) != null;
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
     * Looks for observed variables that explain the rank-m deficiency of As against Bs: a set T, disjoint from the
     * non-sinks, such that with T and the non-sinks added to both sides the cross-covariance has rank at most
     * |T| + |nonsinks|, which is to say the partial cross-rank given T and the non-sinks is zero. Candidates for T
     * come from the observed variables of the partition, members of As included.
     * Sets are tried in order of size from one variable up to t = m - |nonsinks| (and no wider than the configured
     * maximum), smallest first, and the first that explains the deficiency is returned.
     *
     * @return the explaining set, or null if none explains it (or t is 0, or the cap on sets is reached).
     */
    private List<String> observedExplanation(LatentGroups g, Set<Cover> as, Set<Cover> bs, int m,
                                             List<String> nonsinks) {
        int t = m - nonsinks.size();
        if (t <= 0) return null;
        int maxSize = Math.min(t, observedExplanationMaxSize);

        Set<String> aMeasures = new TreeSet<>(CoverSets.vars(g.pickAllMeasures(as)));
        Set<String> bMeasures = new TreeSet<>(CoverSets.vars(g.pickAllMeasures(bs)));

        // Candidates: the partition's observed variables outside the non-sinks, members of As included (a choke can
        // sit inside the set). Sets are never wider than t: a one-factor model itself implies that enough of a
        // cluster's indicators screen the rest from everything, so a wide set drawn from inside a cluster would
        // "explain" every true cluster. Chains, which need wider separators, are caught by separatedWithin instead.
        List<String> candidates = new ArrayList<>();
        for (String v : g.getXNames()) {
            if (nonsinks.contains(v)) continue;
            candidates.add(v);
        }
        Collections.sort(candidates);

        int tried = 0;
        for (int size = 1; size <= maxSize; size++) {
            if (size > candidates.size()) break;
            for (List<String> tSet : CoverSets.combinations(candidates, size)) {
                if (++tried > observedExplanationMaxSets) return null;
                Set<String> a = new TreeSet<>(aMeasures);
                Set<String> b = new TreeSet<>(bMeasures);
                a.addAll(nonsinks);
                b.addAll(nonsinks);
                a.addAll(tSet);
                b.addAll(tSet);
                int shared = nonsinks.size() + tSet.size();
                // With |shared| columns on both sides the cross-covariance has rank at least |shared|; the partial
                // cross-rank given the shared columns is zero exactly when the rank is at most |shared|.
                int[] pcols = a.stream().mapToInt(xvars::indexOf).toArray();
                int[] qcols = b.stream().mapToInt(xvars::indexOf).toArray();
                if (pcols.length <= shared || qcols.length <= shared) continue;
                if (rankTester.failToReject(pcols, qcols, shared, observedExplanationAlpha)) {
                    return tSet;
                }
            }
        }
        return null;
    }

    /**
     * The second half of the observed-explanation check, aimed at what the first half cannot reach. A segment of a
     * chain of observed variables is rank deficient against everything else, and no observed set explains that
     * without conditioning on the whole of the other side, so the first check passes it. But a chain segment is not
     * a measurement cluster, and the difference shows inside the set: pure indicators of one latent are never
     * independent given other indicators (the latent stays open), while members of a chain are separated by the
     * members between them. This tests every pair of measured members of As for independence given up to two other
     * members (and the non-sinks), with the rank test at rank |S|, which is the zero partial correlation. A pair
     * found independent means As is observed structure, and the deficiency is declined.
     * <p>
     * A triangle, three mutually adjacent observed variables, has no separated pair and is a saturated model either
     * way, so it is kept as a latent; nothing in a covariance matrix distinguishes it from one. Two-member sets have
     * no pair to condition on and are left to the first check.
     *
     * @return a description of the separated pair and its separating set, or null if none.
     */
    private String separatedWithin(LatentGroups g, Set<Cover> as, List<String> nonsinks) {
        List<String> members = new ArrayList<>(new TreeSet<>(CoverSets.vars(g.pickAllMeasures(as))));
        if (members.size() < 3) return null;
        int maxCond = Math.min(2, members.size() - 2);
        int tried = 0;
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                String u = members.get(i), v = members.get(j);
                List<String> others = new ArrayList<>(members);
                others.remove(u);
                others.remove(v);
                for (int size = 0; size <= maxCond; size++) {
                    for (List<String> cond : CoverSets.combinations(others, size)) {
                        if (++tried > observedExplanationMaxSets) return null;
                        Set<String> shared = new TreeSet<>(cond);
                        shared.addAll(nonsinks);
                        Set<String> a = new TreeSet<>(shared), b = new TreeSet<>(shared);
                        a.add(u);
                        b.add(v);
                        int[] pcols = a.stream().mapToInt(xvars::indexOf).toArray();
                        int[] qcols = b.stream().mapToInt(xvars::indexOf).toArray();
                        if (rankTester.failToReject(pcols, qcols, shared.size(), observedExplanationAlpha)) {
                            return u + " _||_ " + v + " | " + shared;
                        }
                    }
                }
            }
        }
        return null;
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
                // Nothing here tests which way the link goes; observed --> latent is a convention. Where the
                // temporal tiers rule that direction out, the link is put the other way if the tiers allow that,
                // and is left unoriented otherwise.
                List<Cover> reversed = new ArrayList<>();
                for (Iterator<Cover> it = toAdd.iterator(); it.hasNext(); ) {
                    Cover l = it.next();
                    if (TierPrecedence.findViolation(knowledge, g.observedAncestors(o.getVars()),
                            g.observedDescendants(l.getVars())) != null) {
                        it.remove();
                        reversed.add(l);
                    }
                }
                if (!toAdd.isEmpty()) {
                    g.addOrUpdateCover(o, toAdd);
                    g.updateActiveSet(false);
                }
                for (Cover l : reversed) {
                    Set<String> ancestorsOfL = g.observedAncestors(l.getVars());
                    boolean cycle = !Collections.disjoint(ancestorsOfL, g.observedDescendants(o.getVars()));
                    if (!cycle && TierPrecedence.findViolation(knowledge, ancestorsOfL,
                            g.observedDescendants(o.getVars())) == null) {
                        log.accept("Temporal tiers put " + l + " --> " + o + " in the finish step");
                        g.addOrUpdateCover(l, new LinkedHashSet<>(Collections.singleton(o)));
                    } else {
                        log.accept("Temporal tiers rule out " + o + " --> " + l + "; linked unoriented");
                        g.addUnorientedLink(o, l);
                    }
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
                // Changed from the Python, which makes each of these a directed edge: nothing here tests which
                // way the edge goes, so it is recorded as unoriented and comes out undirected.
                for (int i = 0; i < latents.size() - 1; i++) {
                    g.addUnorientedLink(latents.get(i), latents.get(i + 1));
                    g.updateActiveSet(false);
                }
            }
        }
        log.accept(g.display());
    }
}
