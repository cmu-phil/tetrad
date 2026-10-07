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

import java.util.Collection;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A cover in the sense of RLCD (Dong et al., ICLR 2024): a set of variable names that is treated as a single node
 * group during the rank-deficiency search. A cover over a single observed variable is a measured cover; a cover over
 * one or more latent names (possibly mixed with observed non-sink names) is a latent cover.
 * <p>
 * A cover is <b>atomic</b> if its variables cannot be split into a disjoint set of smaller atomic covers; for example,
 * if {L1} and {L2} are both atomic covers then {L1, L2} is non-atomic. Only atomic covers can appear as children.
 * <p>
 * Equality and hashing are by the variable set only, mirroring the Python <code>Cover</code> class; the flags
 * <code>atomic</code> and <code>leaf</code> are mutable bookkeeping and do not participate in equality.
 * <p>
 * This is a translation of <code>Cover.py</code> from the causal-learn implementation of RLCD.
 *
 * @author josephramsey (translation)
 */
public final class Cover {
    private final SortedSet<String> vars;
    private final String key;
    private final boolean observed;
    private boolean atomic;
    /**
     * Tri-state leaf flag for measured covers: null = not yet decided, TRUE = known sink (leaf), FALSE = known
     * non-sink. Mirrors the Python <code>is_leaf</code> attribute, which is None/True/False.
     */
    private Boolean leaf;

    /**
     * Constructs a cover.
     *
     * @param vars     the variable names covered.
     * @param atomic   whether the cover is atomic.
     * @param observed whether every variable in the cover is an observed variable.
     * @param leaf     tri-state leaf flag (may be null).
     */
    public Cover(Collection<String> vars, boolean atomic, boolean observed, Boolean leaf) {
        if (vars == null || vars.isEmpty()) {
            throw new IllegalArgumentException("A cover must have at least one variable.");
        }
        this.vars = Collections.unmodifiableSortedSet(new TreeSet<>(vars));
        this.key = String.join("\u0001", this.vars);
        this.atomic = atomic;
        this.observed = observed;
        this.leaf = leaf;
    }

    /**
     * Creates a measured (observed) cover over a single variable, with the leaf flag undecided.
     *
     * @param name the variable name.
     * @return the cover.
     */
    public static Cover observed(String name) {
        return new Cover(Collections.singletonList(name), true, true, null);
    }

    /**
     * @return the (sorted, unmodifiable) variable names in this cover.
     */
    public SortedSet<String> getVars() {
        return vars;
    }

    /**
     * @return the number of variables in this cover (Python <code>len(cover)</code>).
     */
    public int size() {
        return vars.size();
    }

    /**
     * @return one variable name from this cover; for singleton covers this is the only name.
     */
    public String takeOne() {
        return vars.first();
    }

    /**
     * @return whether this cover is atomic.
     */
    public boolean isAtomic() {
        return atomic;
    }

    /**
     * Sets the atomic flag.
     *
     * @param atomic the new value.
     */
    public void setAtomic(boolean atomic) {
        this.atomic = atomic;
    }

    /**
     * @return whether this cover consists only of observed variables.
     */
    public boolean isObserved() {
        return observed;
    }

    /**
     * @return the tri-state leaf flag; null means undecided.
     */
    public Boolean getLeaf() {
        return leaf;
    }

    /**
     * Sets the tri-state leaf flag.
     *
     * @param leaf the new value (may be null).
     */
    public void setLeaf(Boolean leaf) {
        this.leaf = leaf;
    }

    /**
     * @param other another cover.
     * @return whether the two covers share at least one variable.
     */
    public boolean intersects(Cover other) {
        for (String v : vars) {
            if (other.vars.contains(v)) return true;
        }
        return false;
    }

    /**
     * @param other another cover.
     * @return whether this cover's variables are a strict subset of the other's.
     */
    public boolean isStrictSubsetOf(Cover other) {
        return vars.size() < other.vars.size() && other.vars.containsAll(vars);
    }

    /**
     * @param other another cover.
     * @return whether this cover's variables are a subset (not necessarily strict) of the other's.
     */
    public boolean isSubsetOf(Cover other) {
        return other.vars.containsAll(vars);
    }

    /**
     * Whether this cover's variables are a subset of the union of the variables of the given covers.
     *
     * @param covers a collection of covers.
     * @param strict whether a strict subset is required.
     * @return the answer.
     */
    public boolean isSubsetOf(Collection<Cover> covers, boolean strict) {
        var union = CoverSets.vars(covers);
        if (!union.containsAll(vars)) return false;
        return !strict || union.size() > vars.size();
    }

    /**
     * A sortable key for this cover: its variable names, sorted and joined.
     *
     * @return the key.
     */
    public String key() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cover other)) return false;
        return key.equals(other.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        if (vars.size() == 1) return vars.first();
        return "{" + String.join(",", vars) + "}";
    }
}
