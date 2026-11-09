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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.util.NaturalSort;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * An additional, named tier structure over (a subset of) the variables of a {@link Knowledge}
 * object, independent of the main temporal tiers and of every other tier structure. Within one
 * structure, edges from a later tier to an earlier tier are forbidden, and a tier may additionally
 * forbid edges within itself; variables of different structures (or of a structure and the main
 * tiers) constrain one another only through whatever other knowledge mentions them.
 *
 * <p>This exists for data sets whose variables carry several temporal orderings at once -- for
 * example a course data set with one ordering over the exams, another over the module checkpoints,
 * and another over the unit completions -- which a single tier list cannot represent without also
 * forbidding edges between the orderings.
 *
 * <p>Unlike the main tiers, the within-tier and ordering constraints of a tier structure are stored
 * here directly, as the structure itself, and {@link Knowledge} consults the structure when asked
 * whether an edge is forbidden; nothing is compiled into pairwise rules.
 *
 * <p>A variable may belong to at most one tier of a given structure, but may appear in any number
 * of structures and in the main tiers at the same time. Variable names are held as plain names;
 * wildcards are not interpreted here.
 *
 * <p>Mutable; instances are shared with editors, which edit them in place.
 *
 * @author josephramsey
 */
public final class KnowledgeTierStructure implements TetradSerializable {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The display name of this structure -- e.g., "Exams". May contain spaces.
     */
    private String name;

    /**
     * The tiers, lowest first. Insertion order of names is kept for stable rendering.
     */
    private final List<Set<String>> tiers = new ArrayList<>();

    /**
     * For each tier, whether edges within that tier are forbidden. Kept parallel to
     * {@link #tiers}.
     */
    private final List<Boolean> forbiddenWithin = new ArrayList<>();

    /**
     * Constructs an empty tier structure with the given name.
     *
     * @param name the display name; may contain spaces but not be null or blank
     */
    public KnowledgeTierStructure(String name) {
        setName(name);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.data.KnowledgeTierStructure} object
     */
    public static KnowledgeTierStructure serializableInstance() {
        return new KnowledgeTierStructure("Structure");
    }

    /**
     * Returns the display name of this structure.
     *
     * @return the name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the display name of this structure.
     *
     * @param name the new name; may contain spaces but not be null or blank
     */
    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("A tier structure needs a nonempty name.");
        }

        this.name = name.trim();
    }

    /**
     * Returns the number of tiers in this structure.
     *
     * @return the number of tiers
     */
    public int getNumTiers() {
        return this.tiers.size();
    }

    /**
     * Ensures that tiers 0..tier exist.
     *
     * @param tier the largest tier index that must exist
     */
    public void ensureTiers(int tier) {
        for (int i = this.tiers.size(); i <= tier; i++) {
            this.tiers.add(new LinkedHashSet<>());
            this.forbiddenWithin.add(false);
        }
    }

    /**
     * Adds the given variable to the given tier of this structure, removing it from any other tier
     * of this structure first.
     *
     * @param tier the tier index, a non-negative integer
     * @param var  the variable name
     */
    public void addToTier(int tier, String var) {
        if (tier < 0) {
            throw new IllegalArgumentException("Tier must be non-negative: " + tier);
        }

        if (var == null) {
            throw new NullPointerException();
        }

        ensureTiers(tier);
        removeFromTiers(var);
        this.tiers.get(tier).add(var);
    }

    /**
     * Removes the given variable from every tier of this structure.
     *
     * @param var the variable name
     */
    public void removeFromTiers(String var) {
        for (Set<String> tier : this.tiers) {
            tier.remove(var);
        }
    }

    /**
     * Returns a copy of the given tier, in natural sort order.
     *
     * @param tier the tier index
     * @return the variables in that tier
     */
    public List<String> getTier(int tier) {
        ensureTiers(tier);
        List<String> list = new ArrayList<>(this.tiers.get(tier));
        list.sort(NaturalSort.naturalComparator());
        return list;
    }

    /**
     * Returns the index of the tier of this structure containing the variable, or -1.
     *
     * @param var the variable name
     * @return the tier index, or -1 if the variable is in no tier of this structure
     */
    public int isInWhichTier(String var) {
        for (int i = 0; i < this.tiers.size(); i++) {
            if (this.tiers.get(i).contains(var)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Returns whether edges within the given tier are forbidden.
     *
     * @param tier the tier index
     * @return true iff edges within the tier are forbidden
     */
    public boolean isTierForbiddenWithin(int tier) {
        ensureTiers(tier);
        return this.forbiddenWithin.get(tier);
    }

    /**
     * Forbids edges within the given tier, or cancels this forbidding.
     *
     * @param tier      the tier index
     * @param forbidden true to forbid edges within the tier
     */
    public void setTierForbiddenWithin(int tier, boolean forbidden) {
        ensureTiers(tier);
        this.forbiddenWithin.set(tier, forbidden);
    }

    /**
     * Returns whether the edge var1 --&gt; var2 is forbidden by this structure: var1 lies in a
     * later tier than var2, or they lie in the same tier and that tier forbids edges within
     * itself. Distinct variables only; a variable never forbids itself.
     *
     * @param var1 the name of the would-be parent
     * @param var2 the name of the would-be child
     * @return true iff the edge is forbidden by this structure
     */
    public boolean isForbidden(String var1, String var2) {
        if (var1.equals(var2)) {
            return false;
        }

        int t1 = isInWhichTier(var1);
        if (t1 < 0) {
            return false;
        }

        int t2 = isInWhichTier(var2);
        if (t2 < 0) {
            return false;
        }

        if (t1 > t2) {
            return true;
        }

        return t1 == t2 && this.forbiddenWithin.get(t1);
    }

    /**
     * Returns the set of variable names appearing in some tier of this structure.
     *
     * @return the union of the tiers
     */
    public Set<String> getVariables() {
        Set<String> vars = new HashSet<>();
        this.tiers.forEach(vars::addAll);
        return vars;
    }

    /**
     * Returns whether no tier of this structure contains a variable.
     *
     * @return true iff every tier is empty
     */
    public boolean isEmpty() {
        return this.tiers.stream().allMatch(Set::isEmpty);
    }

    /**
     * Removes empty tiers from the end of the structure, so that saved and rendered forms do not
     * accumulate trailing blanks. Interior empty tiers are kept, since tier indices are
     * meaningful.
     */
    public void trimTrailingEmptyTiers() {
        for (int i = this.tiers.size() - 1; i >= 0; i--) {
            if (this.tiers.get(i).isEmpty() && !this.forbiddenWithin.get(i)) {
                this.tiers.remove(i);
                this.forbiddenWithin.remove(i);
            } else {
                break;
            }
        }
    }

    /**
     * Computes a hashcode.
     *
     * @return a hashcode
     */
    public int hashCode() {
        return Objects.hash(this.name, this.tiers, this.forbiddenWithin);
    }

    /**
     * Compares this structure with the given object for equality of name, tiers, and within-tier
     * flags.
     *
     * @param o the object to compare with
     * @return true iff the two are equal
     */
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof KnowledgeTierStructure that)) {
            return false;
        }

        return this.name.equals(that.name)
               && this.tiers.equals(that.tiers)
               && this.forbiddenWithin.equals(that.forbiddenWithin);
    }

    /**
     * Returns a short description of this structure.
     *
     * @return a string naming the structure and its tiers
     */
    public String toString() {
        return "Tier structure " + this.name + ": " + this.tiers;
    }
}
