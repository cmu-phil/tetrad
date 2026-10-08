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

/**
 * The evolving latent structure discovered by RLCD over one partition of observed variables: a dictionary from
 * covers (latent or observed non-sink groups) to their children, together with the bookkeeping sets the search
 * reads (active set, non-sink set, children of non-atomic covers, pending rank-deficient sets and clusters).
 * <p>
 * This is a translation of the parts of <code>LatentGroups.py</code> in the causal-learn RLCD implementation that
 * are reachable from the sample-data entry point <code>RLCD()</code>. Methods of the Python class that are only used
 * by the earlier hierarchical-latent project it was adapted from (dissolving and re-rooting covers, re-discovering
 * non-atomic covers, graph pruning) are not translated.
 * <p>
 * Where the Python depends on set iteration order, this class uses insertion-ordered collections and sorts where a
 * deterministic order is needed. The variable names of latent covers are generated as <code>prefix + i</code>; the
 * prefix is chosen by the caller so that it cannot collide with an observed variable name.
 *
 * @author josephramsey (translation)
 */
public final class LatentGroups {

    /**
     * One recorded rank-deficient set: the covers found rank deficient, the non-sinks used in the test, and the list
     * of original rank-deficient sets merged into it (Python keeps this as the third list element; it is carried
     * along but, as in the Python, not used by the downstream logic).
     */
    public static final class RankDefSet {
        private Set<Cover> vs;
        private final Set<String> nonsinks;
        private final List<Set<Cover>> full = new ArrayList<>();

        RankDefSet(Set<Cover> vs, Set<String> nonsinks) {
            this.vs = new LinkedHashSet<>(vs);
            this.nonsinks = new LinkedHashSet<>(nonsinks);
        }

        /**
         * @return the covers.
         */
        public Set<Cover> getVs() {
            return vs;
        }

        /**
         * @return the non-sinks used.
         */
        public Set<String> getNonsinks() {
            return nonsinks;
        }
    }

    /**
     * The value stored for each cover key: children, strict subcovers, "fake children" (the measured descendants not
     * fully adjacent in the stage-1 skeleton; only used to decide whether an update changed anything), and the
     * refined flag (always false in the reachable code).
     */
    public static final class Entry {
        private final Set<Cover> children;
        private final Set<Cover> subcovers;
        private final Set<Cover> fakeChildren;

        Entry(Set<Cover> children, Set<Cover> subcovers, Set<Cover> fakeChildren) {
            this.children = new LinkedHashSet<>(children);
            this.subcovers = new LinkedHashSet<>(subcovers);
            this.fakeChildren = new LinkedHashSet<>(fakeChildren);
        }

        /**
         * @return the children (mutable view).
         */
        public Set<Cover> getChildren() {
            return children;
        }

        /**
         * @return the strict subcovers.
         */
        public Set<Cover> getSubcovers() {
            return subcovers;
        }
    }

    private final String latentPrefix;
    private int nextLatent = 1;
    private Set<Cover> X;
    private Set<Cover> activeSet;
    private Set<Cover> childrenOfNonAtomicsSet = new LinkedHashSet<>();
    private final LinkedHashMap<Cover, Entry> latentDict = new LinkedHashMap<>();
    private final Map<Integer, List<RankDefSet>> rankDefSets = new TreeMap<>();
    private final Map<Integer, List<RankDefSet>> clusters = new TreeMap<>();
    /**
     * Parent-child links between covers that were added without the data deciding which is the parent; see
     * {@link #addUnorientedLink(Cover, Cover)}.
     */
    private final List<Cover[]> unorientedLinks = new ArrayList<>();
    private Set<String> Xns;
    private Set<String> activeNonSinkSet;
    private Map<String, Cover> xDict;
    private final List<String> xNames;
    private final Set<String> allNbSet;
    private final boolean[][] localAdj;
    private Consumer<String> log = s -> {
    };
    private Knowledge knowledge = new Knowledge();

    /**
     * Constructs the structure over a partition of observed variables.
     *
     * @param xNames       the observed variable names in this partition (the order fixes the indices of localAdj).
     * @param xns          the observed names that are candidate non-sinks (normally all of xNames).
     * @param allNbSet     observed names outside the partition adjacent to a member in the stage-1 graph.
     * @param localAdj     the stage-1 skeleton restricted to xNames (true = adjacent).
     * @param latentPrefix prefix for generated latent names (e.g. "L"); must not collide with observed names.
     */
    public LatentGroups(List<String> xNames, Collection<String> xns, Set<String> allNbSet, boolean[][] localAdj,
                        String latentPrefix) {
        this.xNames = new ArrayList<>(xNames);
        this.X = new LinkedHashSet<>();
        this.activeSet = new LinkedHashSet<>();
        for (String x : xNames) {
            this.X.add(Cover.observed(x));
            this.activeSet.add(Cover.observed(x));
        }
        this.Xns = new TreeSet<>(xns);
        this.activeNonSinkSet = new TreeSet<>(xns);
        this.allNbSet = new TreeSet<>(allNbSet);
        this.localAdj = localAdj;
        this.latentPrefix = latentPrefix;
        updateXDict();
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
     * Sets background knowledge over the observed variables; see {@link #addCluster}.
     *
     * @param knowledge the knowledge; null means none.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge == null ? new Knowledge() : knowledge;
    }

    // ---------------------------------------------------------------- accessors

    /**
     * @return the dictionary from covers to their entries (insertion-ordered).
     */
    public LinkedHashMap<Cover, Entry> getLatentDict() {
        return latentDict;
    }

    /**
     * @return the observed covers.
     */
    public Set<Cover> getX() {
        return X;
    }

    /**
     * @return the current active set.
     */
    public Set<Cover> getActiveSet() {
        return activeSet;
    }

    /**
     * Replaces the active set (used by the unfolding step of the cluster search).
     *
     * @param activeSet the new active set.
     */
    public void setActiveSet(Set<Cover> activeSet) {
        this.activeSet = new LinkedHashSet<>(activeSet);
    }

    /**
     * @return the children of non-atomic covers, as of the last {@link #updateActiveSet(boolean)}.
     */
    public Set<Cover> getChildrenOfNonAtomicsSet() {
        return childrenOfNonAtomicsSet;
    }

    /**
     * @return the current active non-sink names.
     */
    public Set<String> getActiveNonSinkSet() {
        return activeNonSinkSet;
    }

    /**
     * Replaces the active non-sink set.
     *
     * @param set the new set.
     */
    public void setActiveNonSinkSet(Set<String> set) {
        this.activeNonSinkSet = new TreeSet<>(set);
    }

    /**
     * @return the names of observed variables in this partition, in index order of the local adjacency.
     */
    public List<String> getXNames() {
        return xNames;
    }

    /**
     * @return the observed cover for a name, or null if the name is not an observed variable of this partition.
     */
    public Cover getObservedCoverByName(String name) {
        return xDict.get(name);
    }

    /**
     * @return the map from observed name to its cover.
     */
    public Map<String, Cover> getXDict() {
        return xDict;
    }

    /**
     * @return the observed names outside the partition adjacent to a member in the stage-1 graph.
     */
    public Set<String> getAllNbSet() {
        return allNbSet;
    }

    /**
     * Whether two observed variables of the partition are adjacent in the stage-1 skeleton.
     *
     * @param i index into {@link #getXNames()}.
     * @param j index into {@link #getXNames()}.
     * @return the answer.
     */
    public boolean isLocallyAdjacent(int i, int j) {
        return localAdj[i][j] || localAdj[j][i];
    }

    private void updateXDict() {
        xDict = new LinkedHashMap<>();
        for (Cover x : X) xDict.put(x.takeOne(), x);
    }

    // ---------------------------------------------------------------- rank-deficient sets and clusters

    /**
     * Saves a rank-deficient set of covers for merging into clusters later (Python <code>addRankDefSet</code>).
     *
     * @param vs           the covers found rank deficient.
     * @param k            the rank found.
     * @param usedNonsinks the non-sinks used in the test.
     */
    public void addRankDefSet(Set<Cover> vs, int k, Collection<String> usedNonsinks) {
        rankDefSets.computeIfAbsent(k, kk -> new ArrayList<>())
                .add(new RankDefSet(vs, new LinkedHashSet<>(usedNonsinks)));
    }

    /**
     * From the saved rank-deficient sets of the lowest recorded rank, merges any pair of sets that overlap in at
     * least min(|A|, |B|) − 1 covers and were found with the same non-sinks (Python <code>determineClusters</code>).
     * Rank-deficient sets of higher rank are discarded.
     */
    public void determineClusters() {
        int k = Collections.min(rankDefSets.keySet());
        List<RankDefSet> cl = new ArrayList<>(rankDefSets.remove(k));
        for (RankDefSet c : cl) c.full.add(new LinkedHashSet<>(c.vs));

        int n = cl.size();
        while (true) {
            int i = 0, j = 1;
            while (j < cl.size()) {
                RankDefSet a = cl.get(i), b = cl.get(j);
                if (CoverSets.setIntersection(a.vs, b.vs).size() >= Math.min(a.vs.size(), b.vs.size()) - 1
                    && a.nonsinks.equals(b.nonsinks)) {
                    Set<Cover> merged = new LinkedHashSet<>(a.vs);
                    merged.addAll(b.vs);
                    a.vs = merged;
                    a.full.addAll(b.full);
                    cl.remove(j);
                }
                if (j >= cl.size() - 1) {
                    i++;
                    j = i + 1;
                } else {
                    j++;
                }
            }
            if (n == cl.size()) break;
            n = cl.size();
        }

        clusters.put(k, cl);
        rankDefSets.clear();
    }

    /**
     * Adds each determined cluster as the children of a new (or updated) cover (Python
     * <code>confirmClusters</code>), and updates the leaf flags of observed variables: non-sinks used become known
     * non-leaves, singleton observed children become known leaves. Flags are set only when still undecided.
     * <p>
     * The clusters of a round are added in order of how many observed variables each accounts for (its measured
     * members together with the non-sinks it was found with), most first. They compete: once one is added, a later
     * one that overlaps it is usually rejected or reshaped by it. The Python adds them in the order found, which
     * puts clusters found with more non-sinks first whatever their size. That let a few sets that happened to pass
     * a rank test given some observed variable claim that variable as a parent, ahead of a cluster of the same rank
     * built from many more sets and covering every variable; the result was an observed variable standing in for
     * one of the latents of a cover with two or more latents. A cluster with an observed parent that is real covers
     * as many variables as the all-latent reading of the same variables, ties with it, and still goes first.
     *
     * @return whether any cluster was successfully added.
     */
    public boolean confirmClusters() {
        int k = Collections.min(clusters.keySet());
        boolean success = false;
        List<RankDefSet> cl = clusters.remove(k);

        // The cluster that accounts for the most observed variables first; see the method comment. The sort is
        // stable, so clusters that tie keep the order in which they were found, with more non-sinks first.
        Map<RankDefSet, Integer> coverage = new IdentityHashMap<>();
        for (RankDefSet c : cl) {
            Set<String> covered = new TreeSet<>(CoverSets.vars(pickAllMeasures(c.vs)));
            covered.addAll(c.nonsinks);
            coverage.put(c, covered.size());
        }
        cl.sort((x, y) -> coverage.get(y) - coverage.get(x));

        for (RankDefSet c : cl) {
            boolean current = addCluster(new LinkedHashSet<>(c.vs), c.full, k, new ArrayList<>(c.nonsinks));
            if (current) {
                for (Cover x : X) {
                    boolean hit = false;
                    for (String ns : c.nonsinks) if (x.getVars().contains(ns)) hit = true;
                    if (hit && x.getLeaf() == null) {
                        x.setLeaf(false);
                        Xns.removeAll(x.getVars());
                    }
                }
                for (Cover v : c.vs) {
                    if (v.size() == 1) {
                        for (Cover x : X) {
                            if (x.intersects(v) && x.getLeaf() == null) {
                                x.setLeaf(true);
                                Xns.removeAll(x.getVars());
                            }
                        }
                    }
                }
                updateXDict();
                log.accept("Current Xns " + Xns);
            }
            success = success || current;
        }
        clusters.clear();
        return success;
    }

    /**
     * Splits the measured descendants of Vs into those fully adjacent (in the stage-1 skeleton, with self-adjacency)
     * to all of them and the rest (Python <code>splitfullVs</code>). Only the second set is used, as "fake
     * children".
     */
    private Set<Cover>[] splitFullVs(Set<Cover> vs) {
        Set<Cover> measures = pickAllMeasures(vs);
        List<Integer> idx = new ArrayList<>();
        for (Cover v : measures) idx.add(xNames.indexOf(v.takeOne()));
        Set<Cover> vs1 = new LinkedHashSet<>(), vs2 = new LinkedHashSet<>();
        for (Cover v : measures) {
            int i = xNames.indexOf(v.takeOne());
            boolean all = true;
            for (int j : idx) {
                if (i != j && !localAdj[i][j]) {
                    all = false;
                    break;
                }
            }
            if (all) vs1.add(v);
            else vs2.add(v);
        }
        @SuppressWarnings("unchecked") Set<Cover>[] out = new Set[]{vs1, vs2};
        return out;
    }

    /**
     * For a discovered cluster Vs of rank k, creates a new cover over it, or extends an existing one (Python
     * <code>addCluster</code>). The new cover's variables are the variables of the existing parents of Vs, then the
     * used non-sinks not already among them, then fresh latent names, up to k variables in total. The cluster is
     * rejected if a used non-sink is already a child of it (a cycle), or if an observed variable of the new cover
     * is forbidden by knowledge from being a parent of an observed child.
     *
     * @param vs           the cluster.
     * @param fullVs       the original rank-deficient sets merged into it (unused, as in the Python).
     * @param k            the rank.
     * @param usedNonsinks the non-sinks used in the tests.
     * @return whether a new latent relationship was recorded.
     */
    public boolean addCluster(Set<Cover> vs, List<Set<Cover>> fullVs, int k, List<String> usedNonsinks) {
        Set<Cover>[] split = splitFullVs(vs);
        Set<Cover> vs2 = split[1];

        // A used non-sink must not be a child of Vs (cycle check).
        Set<String> vsVars = CoverSets.vars(vs);
        for (String ns : usedNonsinks) {
            Cover nsCover = getObservedCoverByName(ns);
            if (nsCover == null) continue;
            Set<String> parentVars = CoverSets.vars(findParents(Collections.singleton(nsCover), false, false));
            parentVars.retainAll(vsVars);
            if (!parentVars.isEmpty()) {
                log.accept("Rejecting " + vs + " as a cluster because " + ns + " is a child of " + vs);
                return false;
            }
        }

        Set<Cover> parents = findParents(vs, false, false);
        int parentsSize = CoverSets.setLength(parents);
        int gap = k - parentsSize;
        log.accept("Trying to add to Dict " + vs + " with k=" + k);

        if (gap < 0) {
            log.accept("Rejecting " + vs + " as a cluster because it is rank " + k
                       + " but has parents of cardinality " + parentsSize);
            return false;
        }

        SortedSet<String> parentVars = CoverSets.vars(parents);
        List<String> additionalNonsinks = new ArrayList<>(new TreeSet<>(usedNonsinks));
        additionalNonsinks.removeAll(parentVars);
        List<String> newCoverNames = new ArrayList<>(parentVars);
        if (gap < additionalNonsinks.size()) return false;

        for (int j = 0; j < gap; j++) {
            if (j < additionalNonsinks.size()) {
                newCoverNames.add(additionalNonsinks.get(j));
            } else {
                newCoverNames.add(latentPrefix + nextLatent);
                nextLatent++;
            }
        }

        Set<String> xStrSet = CoverSets.vars(X);
        boolean isObserved = xStrSet.containsAll(newCoverNames);

        // The Python computes an atomic flag here but addOrUpdateCover overrides it with !isNonAtomic(L) for a
        // new key and leaves an existing key's flag untouched, so only that rule is implemented.
        Cover newCover = new Cover(newCoverNames, true, isObserved, false);
        log.accept("--- Adding " + vs + " as a " + k + "-cluster under " + newCover + ", is_observed:" + isObserved);

        // Remove children who belong to a subcover.
        Set<Cover> vsWork = new LinkedHashSet<>(vs);
        for (Cover sub : findSubcovers(newCover, false)) {
            Entry e = latentDict.get(sub);
            if (e != null) vsWork.removeAll(e.children);
        }
        for (String name : newCoverNames) {
            Cover c = getObservedCoverByName(name);
            if (c != null) vsWork.remove(c);
        }
        vsWork = CoverSets.deduplicate(vsWork);

        // Observed variables in the new cover become parents of the children; reject if knowledge forbids one.
        for (String c : newCoverNames) {
            if (!xDict.containsKey(c)) continue;
            for (Cover ch : vsWork) {
                for (String a : ch.getVars()) {
                    if (xDict.containsKey(a) && knowledge.isForbidden(c, a)) {
                        log.accept("Rejecting " + vs + " as a cluster because knowledge forbids " + c + " --> " + a);
                        return false;
                    }
                }
            }
        }

        return addOrUpdateCover(newCover, vsWork, vs2);
    }

    // ---------------------------------------------------------------- queries

    /**
     * Finds the parents of a set of covers: all keys having any of them as a child (Python
     * <code>findParents</code>), deduplicated.
     *
     * @param vs        the covers.
     * @param atomic    whether to take only atomic parents.
     * @param nonAtomic whether to take only non-atomic parents.
     * @return the parents.
     */
    public Set<Cover> findParents(Collection<Cover> vs, boolean atomic, boolean nonAtomic) {
        if (atomic && nonAtomic) throw new IllegalArgumentException("Specify atomic or nonAtomic, not both.");
        Set<Cover> parents = new LinkedHashSet<>();
        for (Map.Entry<Cover, Entry> e : latentDict.entrySet()) {
            Cover parent = e.getKey();
            if (atomic && !parent.isAtomic()) continue;
            if (nonAtomic && parent.isAtomic()) continue;
            for (Cover v : vs) {
                if (e.getValue().children.contains(v)) {
                    parents.add(parent);
                }
            }
        }
        return CoverSets.deduplicate(parents);
    }

    /**
     * Resets the active non-sink set to the current candidate non-sinks (Python
     * <code>updateactiveNonSinkSet</code>).
     */
    public void updateActiveNonSinkSet() {
        activeNonSinkSet = new TreeSet<>(Xns);
    }

    /**
     * Refreshes the active set after covers are added (Python <code>updateActiveSet</code>): all observed covers
     * plus atomic cover keys (all keys when <code>forFinish</code>), minus anything that is a child of a key, then
     * deduplicated. Also recomputes the children of non-atomic covers.
     *
     * @param forFinish whether non-atomic keys are also included.
     */
    public void updateActiveSet(boolean forFinish) {
        Set<Cover> active = new LinkedHashSet<>(X);
        childrenOfNonAtomicsSet = new LinkedHashSet<>();
        for (Cover p : latentDict.keySet()) {
            if (forFinish || p.isAtomic()) active.add(p);
        }
        for (Map.Entry<Cover, Entry> e : latentDict.entrySet()) {
            active = CoverSets.setDifference(active, e.getValue().children);
            if (!e.getKey().isAtomic()) childrenOfNonAtomicsSet.addAll(e.getValue().children);
        }
        activeSet = CoverSets.deduplicate(active);
        log.accept("Active Set (forFinish:" + forFinish + "): " + activeSet);
    }

    /**
     * Whether Vs together with the non-sinks contain more than k elements from an existing k-cover's children
     * (Python <code>containsCluster</code>).
     *
     * @param vs       the covers.
     * @param nonsinks the non-sinks.
     * @return the answer.
     */
    public boolean containsCluster(Set<Cover> vs, Collection<String> nonsinks) {
        for (Cover l : latentDict.keySet()) {
            int k = l.size();
            Set<Cover> children = findChildren(l, true);
            int count = CoverSets.setIntersection(vs, children).size();
            for (String ns : nonsinks) if (l.getVars().contains(ns)) count++;
            if (count > k) return true;
        }
        return false;
    }

    /**
     * Whether any key cover appearing in Vs has one of the non-sinks among its children's variables (Python
     * <code>checkNonSinksAreAsChildren</code>).
     *
     * @param vs       the covers.
     * @param nonsinks the non-sinks.
     * @return the answer.
     */
    public boolean checkNonSinksAreAsChildren(Set<Cover> vs, Collection<String> nonsinks) {
        for (Cover l : latentDict.keySet()) {
            if (!CoverSets.setIntersection(vs, Collections.singleton(l)).isEmpty()) {
                Set<String> childVars = CoverSets.vars(findChildren(l, true));
                for (String ns : nonsinks) if (childVars.contains(ns)) return true;
            }
        }
        return false;
    }

    /**
     * Whether the measured descendants of As include a non-sink (Python <code>MeassuredHasNonSinks</code>).
     *
     * @param as       the covers.
     * @param nonsinks the non-sinks.
     * @return the answer.
     */
    public boolean measuredHasNonSinks(Set<Cover> as, Collection<String> nonsinks) {
        for (Cover m : pickAllMeasures(as)) {
            for (String ns : nonsinks) if (m.getVars().contains(ns)) return true;
        }
        return false;
    }

    /**
     * Whether some cover in Vs shares a variable with a parent of another cover in Vs (Python
     * <code>overlapPaCh</code>).
     *
     * @param vs the covers.
     * @return the answer.
     */
    public boolean overlapPaCh(Set<Cover> vs) {
        for (Cover v : vs) {
            Set<Cover> pa = findParents(Collections.singleton(v), false, false);
            Set<Cover> others = new LinkedHashSet<>(vs);
            others.remove(v);
            if (!CoverSets.setIntersection(others, pa).isEmpty()) return true;
        }
        return false;
    }

    /**
     * The cardinality of Vs after replacing any cluster within it by its atomic latent parent, recursively (Python
     * <code>parentCardinality</code>).
     *
     * @param vsIn the covers.
     * @return the cardinality.
     */
    public int parentCardinality(Set<Cover> vsIn) {
        Set<Cover> vs = new LinkedHashSet<>(vsIn);
        int k1 = CoverSets.setLength(vs);
        for (Cover l : latentDict.keySet()) {
            if (!l.isAtomic()) continue;
            int k = l.size();
            Set<Cover> children = findChildren(l, true);
            Set<String> vsVars = CoverSets.vars(vs);
            Set<String> inL = new TreeSet<>(vsVars);
            inL.retainAll(l.getVars());
            if (CoverSets.setIntersection(vs, children).size() + inL.size() > k) {
                vs.removeAll(children);
                vs.remove(l);
                Set<String> inL2 = CoverSets.vars(vs);
                inL2.retainAll(l.getVars());
                for (String s : inL2) {
                    Cover c = xDict.get(s);
                    if (c != null) vs.remove(c);
                }
                vs.add(l);
            }
        }
        int k2 = CoverSets.setLength(vs);
        return k2 < k1 ? parentCardinality(vs) : k1;
    }

    /**
     * The union of children over all keys whose variables lie within the variables of Ls (Python
     * <code>findChildrenOfAllSubSets</code>).
     *
     * @param ls the covers.
     * @return the children.
     */
    public Set<Cover> findChildrenOfAllSubSets(Collection<Cover> ls) {
        Set<String> lsVars = CoverSets.vars(ls);
        Set<Cover> children = new LinkedHashSet<>();
        for (Map.Entry<Cover, Entry> e : latentDict.entrySet()) {
            if (lsVars.containsAll(e.getKey().getVars())) children.addAll(e.getValue().children);
        }
        return children;
    }

    /**
     * The children of a cover (Python <code>findChildren</code>). In rigorous mode: the cover's own children plus,
     * recursively, the children of its recorded subcovers. In non-rigorous mode: the children of every key sharing a
     * variable with the cover.
     *
     * @param l        the cover.
     * @param rigorous the mode.
     * @return the children.
     */
    public Set<Cover> findChildren(Cover l, boolean rigorous) {
        Set<Cover> children = new LinkedHashSet<>();
        if (rigorous) {
            Entry e = latentDict.get(l);
            if (e != null) {
                children.addAll(e.children);
                for (Cover sub : new ArrayList<>(e.subcovers)) children.addAll(findChildren(sub, true));
            }
        } else {
            for (Map.Entry<Cover, Entry> e : latentDict.entrySet()) {
                if (e.getKey().intersects(l)) children.addAll(e.getValue().children);
            }
        }
        return children;
    }

    /**
     * All descendants of a cover under {@link #findChildren(Cover, boolean)} (Python <code>findDescendants</code>).
     *
     * @param l        the cover.
     * @param rigorous the children mode.
     * @return the descendants.
     */
    public Set<Cover> findDescendants(Cover l, boolean rigorous) {
        Set<Cover> out = new LinkedHashSet<>();
        findDescendants(l, rigorous, new HashSet<>(), out);
        return out;
    }

    private void findDescendants(Cover l, boolean rigorous, Set<Cover> visited, Set<Cover> out) {
        if (!visited.add(l)) return;
        Set<Cover> children = findChildren(l, rigorous);
        out.addAll(children);
        for (Cover c : children) findDescendants(c, rigorous, visited, out);
    }

    /**
     * The observed covers whose names appear among the cover's variables (Python <code>findMeassuredSubset</code>).
     *
     * @param l the cover.
     * @return the observed covers.
     */
    public Set<Cover> findMeasuredSubset(Cover l) {
        Set<Cover> out = new LinkedHashSet<>();
        for (String s : l.getVars()) {
            Cover c = xDict.get(s);
            if (c != null) out.add(c);
        }
        return out;
    }

    /**
     * All measured (observed) descendants of a set of covers, including observed variables named inside mixed
     * covers and descendants reached through non-atomic covers whose variables have all been visited (Python
     * <code>pickAllMeasures</code>).
     *
     * @param ls the covers.
     * @return the observed covers.
     */
    public Set<Cover> pickAllMeasures(Collection<Cover> ls) {
        Set<Cover> measures = new LinkedHashSet<>();
        pickAllMeasures(ls, new LinkedHashSet<>(), new LinkedHashSet<>(), measures);
        return measures;
    }

    private void pickAllMeasures(Collection<Cover> ls, Set<Cover> visitedA, Set<Cover> visitedNA, Set<Cover> measures) {
        Deque<Cover> queue = new ArrayDeque<>();
        for (Cover l : ls) {
            if (l.isObserved()) {
                measures.add(l);
            } else {
                for (String s : l.getVars()) {
                    Cover c = xDict.get(s);
                    if (c != null) measures.add(c);
                }
                queue.add(l);
            }
        }

        // BFS among descendants. The Python has no visited check in this loop; the check added here only matters if
        // the structure is cyclic, in which case the Python would not terminate.
        Set<Cover> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            Cover l = queue.poll();
            visitedA.add(l);
            if (!seen.add(l)) continue;
            for (Cover c : findChildren(l, true)) {
                if (c.isObserved()) {
                    measures.add(c);
                } else {
                    for (String s : c.getVars()) {
                        Cover x = xDict.get(s);
                        if (x != null) measures.add(x);
                    }
                    queue.add(c);
                }
            }
        }

        // Non-atomic covers whose variables are all visited: descend into their children too.
        for (Cover cover : new ArrayList<>(latentDict.keySet())) {
            if (cover.isAtomic() || visitedNA.contains(cover)) continue;
            if (cover.isSubsetOf(visitedA, false)) {
                visitedNA.add(cover);
                Set<Cover> cs = new LinkedHashSet<>();
                for (Cover c : latentDict.get(cover).children) {
                    if (c.isObserved()) measures.add(c);
                    else cs.add(c);
                }
                pickAllMeasures(cs, visitedA, visitedNA, measures);
            }
        }
    }

    /**
     * Adds a cover with the given children, or extends an existing one (Python <code>addOrUpdateCover</code>). The
     * atomic flag of a new key is set to whether it cannot be decomposed into existing atomic subcovers; an existing
     * key keeps its flag.
     *
     * @param l            the cover.
     * @param children     the children.
     * @param fakeChildren the fake children (see {@link Entry}).
     * @return true if anything changed.
     */
    public boolean addOrUpdateCover(Cover l, Set<Cover> children, Set<Cover> fakeChildren) {
        Set<Cover> subcovers = findSubcovers(l, false);
        l.setAtomic(!isNonAtomic(l));
        Entry e = latentDict.get(l);
        if (e != null) {
            if (children.equals(e.children) && subcovers.equals(e.subcovers) && fakeChildren.equals(e.fakeChildren)) {
                return false;
            }
            e.children.addAll(children);
            e.subcovers.addAll(subcovers);
            e.fakeChildren.addAll(fakeChildren);
            return true;
        }
        latentDict.put(l, new Entry(children, subcovers, fakeChildren));
        return true;
    }

    /**
     * Links two covers as {@link #addOrUpdateCover(Cover, Set)} does, with the first as parent of the second, and
     * records that the choice of parent was not made by the data, so that {@link #toAdjacency()} writes the link
     * as an undirected edge. The cover structure itself stays a parent-child one, which is what the rest of this
     * class works with.
     *
     * @param parent the cover placed as the parent.
     * @param child  the cover placed as the child.
     */
    public void addUnorientedLink(Cover parent, Cover child) {
        addOrUpdateCover(parent, new LinkedHashSet<>(Collections.singleton(child)));
        unorientedLinks.add(new Cover[]{parent, child});
    }

    /**
     * @return the links recorded by {@link #addUnorientedLink(Cover, Cover)}, each as {parent, child}.
     */
    public List<Cover[]> getUnorientedLinks() {
        return Collections.unmodifiableList(unorientedLinks);
    }

    /**
     * Convenience overload with no fake children.
     *
     * @param l        the cover.
     * @param children the children.
     * @return true if anything changed.
     */
    public boolean addOrUpdateCover(Cover l, Set<Cover> children) {
        return addOrUpdateCover(l, children, new LinkedHashSet<>());
    }

    /**
     * All covers (keys and observed covers) whose variables are a strict subset of the cover's (Python
     * <code>findSubcovers</code>).
     *
     * @param l          the cover.
     * @param onlyAtomic whether to restrict to atomic covers.
     * @return the subcovers.
     */
    public Set<Cover> findSubcovers(Cover l, boolean onlyAtomic) {
        Set<Cover> pool = new LinkedHashSet<>(latentDict.keySet());
        pool.addAll(X);
        Set<Cover> out = new LinkedHashSet<>();
        for (Cover c : pool) {
            if (onlyAtomic && !c.isAtomic()) continue;
            if (c.isStrictSubsetOf(l)) out.add(c);
        }
        return out;
    }

    /**
     * Whether the cover can be subdivided into existing atomic covers (Python <code>isNonAtomic</code>).
     *
     * @param l the cover.
     * @return the answer.
     */
    public boolean isNonAtomic(Cover l) {
        return CoverSets.vars(findSubcovers(l, true)).equals(l.getVars());
    }

    /**
     * A readable dump of the current dictionary (Python <code>misc.display</code>).
     *
     * @return the text.
     */
    public String display() {
        StringBuilder sb = new StringBuilder();
        sb.append("========== Current LatentDict ==========\n");
        sb.append("Active Set: ").append(activeSet).append('\n');
        for (Map.Entry<Cover, Entry> e : latentDict.entrySet()) {
            sb.append("   ").append(e.getKey()).append(" : ").append(e.getValue().children);
            if (!e.getValue().subcovers.isEmpty()) sb.append(" | ").append(e.getValue().subcovers);
            sb.append('\n');
        }
        sb.append("========================================");
        return sb.toString();
    }

    /**
     * Writes the discovered structure as a signed adjacency matrix over the partition's observed variables followed
     * by the latent variables (Python <code>getLfromLatentGroups</code>). Encoding: for a parent variable P and child
     * variable C, A[P][C] = −1 and A[C][P] = 1; co-members of an atomic cover with more than one variable are marked
     * −2 in both directions (meaning "same atomic cover, no edge"); and a link whose direction the data did not
     * decide (see {@link #addUnorientedLink(Cover, Cover)}) is marked −1 in both directions, the encoding of an
     * undirected edge.
     *
     * @return the matrix and the variable names it is indexed by.
     */
    public AdjacencyResult toAdjacency() {
        SortedSet<String> latentNames = new TreeSet<>(Comparator.comparing(this::latentIndex).thenComparing(s -> s));
        Set<String> xset = new HashSet<>(xNames);
        for (Map.Entry<Cover, Entry> e : latentDict.entrySet()) {
            for (String v : e.getKey().getVars()) if (!xset.contains(v)) latentNames.add(v);
            for (Cover ch : e.getValue().children) {
                for (String v : ch.getVars()) if (!xset.contains(v)) latentNames.add(v);
            }
        }
        List<String> all = new ArrayList<>(xNames);
        all.addAll(latentNames);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < all.size(); i++) index.put(all.get(i), i);
        int[][] A = new int[all.size()][all.size()];

        for (Map.Entry<Cover, Entry> e : latentDict.entrySet()) {
            Cover cover = e.getKey();
            if (cover.isAtomic()) {
                for (String a : cover.getVars()) {
                    for (String b : cover.getVars()) {
                        if (!a.equals(b)) A[index.get(a)][index.get(b)] = -2;
                    }
                }
            }
            for (String a : cover.getVars()) {
                int ia = index.get(a);
                for (Cover ch : e.getValue().children) {
                    for (String c : ch.getVars()) {
                        int ic = index.get(c);
                        A[ia][ic] = -1;
                        A[ic][ia] = 1;
                    }
                }
            }
        }

        for (Cover[] link : unorientedLinks) {
            for (String a : link[0].getVars()) {
                for (String c : link[1].getVars()) {
                    A[index.get(a)][index.get(c)] = -1;
                    A[index.get(c)][index.get(a)] = -1;
                }
            }
        }

        return new AdjacencyResult(A, all);
    }

    private int latentIndex(String name) {
        if (name.startsWith(latentPrefix)) {
            try {
                return Integer.parseInt(name.substring(latentPrefix.length()));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * A signed adjacency matrix with its variable names.
     *
     * @param adjacency the matrix.
     * @param names     the names.
     */
    public record AdjacencyResult(int[][] adjacency, List<String> names) {
    }
}
