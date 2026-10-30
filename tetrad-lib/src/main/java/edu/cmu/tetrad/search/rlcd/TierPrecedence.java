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
import edu.cmu.tetrad.data.KnowledgeTierStructure;

import java.util.Collection;

/**
 * The part of background knowledge that constrains ancestry, not only adjacency: a variable in a strictly later
 * temporal tier than another cannot be its cause, directly or through any intermediary, latent ones included. This
 * holds for the main tiers and for each additional tier structure. The other kinds of knowledge (explicitly forbidden
 * edges, forbidden groups, forbidding edges within a tier, "can only cause the next tier") constrain direct edges
 * only and are deliberately not treated as constraining ancestry here.
 *
 * @author josephramsey
 */
public final class TierPrecedence {

    private TierPrecedence() {
    }

    /**
     * Whether knowledge places {@code later} in a strictly later tier than {@code earlier}, in the main tiers or in
     * some additional tier structure, so that {@code later} cannot be an ancestor of {@code earlier}.
     *
     * @param knowledge the knowledge.
     * @param later     the would-be ancestor.
     * @param earlier   the would-be descendant.
     * @return the answer.
     */
    public static boolean isLater(Knowledge knowledge, String later, String earlier) {
        if (knowledge == null || later.equals(earlier)) return false;

        int t1 = -1, t2 = -1;
        int numTiers = knowledge.getNumTiers();
        for (int i = 0; i < numTiers; i++) {
            Collection<String> tier = knowledge.getTier(i);
            if (t1 < 0 && tier.contains(later)) t1 = i;
            if (t2 < 0 && tier.contains(earlier)) t2 = i;
        }
        if (t1 >= 0 && t2 >= 0 && t1 > t2) return true;

        for (KnowledgeTierStructure s : knowledge.getTierStructures()) {
            int s1 = s.isInWhichTier(later), s2 = s.isInWhichTier(earlier);
            if (s1 >= 0 && s2 >= 0 && s1 > s2) return true;
        }
        return false;
    }

    /**
     * Whether some member of {@code ancestors} is in a strictly later tier than some member of {@code descendants}.
     *
     * @param knowledge   the knowledge.
     * @param ancestors   would-be ancestors.
     * @param descendants would-be descendants.
     * @return a violating pair as {ancestor, descendant}, or null if there is none.
     */
    public static String[] findViolation(Knowledge knowledge, Collection<String> ancestors,
                                         Collection<String> descendants) {
        if (knowledge == null || knowledge.isEmpty()) return null;
        for (String a : ancestors) {
            for (String d : descendants) {
                if (isLater(knowledge, a, d)) return new String[]{a, d};
            }
        }
        return null;
    }
}
