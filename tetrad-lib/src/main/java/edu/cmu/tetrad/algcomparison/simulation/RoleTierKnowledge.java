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

package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.Knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds tiered knowledge from variable-name roles for the simulations whose variables are
 * named by role (observational study: C, S, I, Y; designed experiment: F, D, R). Roles are
 * given as name prefixes in causal order; a variable is placed in the tier of the first prefix
 * it matches. An optional bookkeeping column (SUBJECT, CONFIG) that names the block structure
 * of the rows is placed in a tier of its own before all others, which is its fixed-effects
 * reading - a block-level cause of everything; exclude it from the search instead if that is
 * not the intended analysis. Empty tiers are dropped, so the tier numbering is consecutive.
 * Variables matching no role are added to the knowledge without a tier.
 *
 * @author josephramsey
 */
final class RoleTierKnowledge {

    private RoleTierKnowledge() {
    }

    /**
     * Builds the knowledge.
     *
     * @param data        the data model whose variables are to be tiered.
     * @param bookkeeping the exact name of the bookkeeping column, or null if none.
     * @param prefixes    the role prefixes in causal order.
     * @return the knowledge.
     */
    static Knowledge build(DataModel data, String bookkeeping, String... prefixes) {
        List<String> names = data.getVariableNames();
        List<List<String>> tiers = new ArrayList<>();
        List<String> book = new ArrayList<>();
        for (int k = 0; k < prefixes.length; k++) tiers.add(new ArrayList<>());

        for (String name : names) {
            if (bookkeeping != null && name.equals(bookkeeping)) {
                book.add(name);
                continue;
            }
            for (int k = 0; k < prefixes.length; k++) {
                if (name.startsWith(prefixes[k])) {
                    tiers.get(k).add(name);
                    break;
                }
            }
        }

        Knowledge knowledge = new Knowledge(names);
        int tier = 0;
        if (!book.isEmpty()) {
            for (String name : book) knowledge.addToTier(tier, name);
            tier++;
        }
        for (List<String> members : tiers) {
            if (members.isEmpty()) continue;
            for (String name : members) knowledge.addToTier(tier, name);
            tier++;
        }
        return knowledge;
    }
}
