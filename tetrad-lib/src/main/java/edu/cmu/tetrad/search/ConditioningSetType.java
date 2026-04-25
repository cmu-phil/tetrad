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

/**
 * The type of conditioning set to use for the Markov check. The default is LOCAL_MARKOV, which uses the parents of the
 * target variable to predict the separation set.
 * <p>
 * All of these options are available for DAG models as well as latent variable models. M-separation is used to
 * determine if two variables are independent given a conditioning set or dependent given a conditioning set, which is a
 * correct procedure in both cases. The conditioning set is the set of variables that are conditioned on in the
 * independence test.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see MarkovCheck
 */
public enum ConditioningSetType {
    /**
     * Full ordered local Markov property  (Richardson)
     */
    ORDERED_LOCAL_MARKOV_PROPERTY("Ordered Local Markov Property"),
    /**
     * Sink elimination ordered local Markov property (Andrews)
     */
    ORDERED_LOCAL_MARKOV_PROPERTY_SINK_ELIMINATION("Ordered Local Markov (Sink Elimination)"),
    /**
     * Pairwise Markov property, conditioing on the anteriority of the endpoints.
     */
    PAIRWISE_MARKOV_PROPERTY("Pairwise Markov Property"),
    /**
     * The Markov blanket of the target variable.
     */
    MARKOV_BLANKET("Markov Blanket"),
    /**
     * Recursive blocking.
     */
    RECURSIVE_BLOCKING("Recursive Blocking"),
    /**
     * Local Markov property, conditioning on the parents of the target variable.
     */
    LOCAL_MARKOV("Local Markov (Parents)"),
    /**
     * Causal Markov property, conditioning on the parents and neighbors of the target variable.
     */
    PARENTS_AND_NEIGHBORS("Parents and Neighbors"),
    /**
     * Global Markov property, conditioning on all subsets implied by global Markov. For small models only.
     */
    GLOBAL_MARKOV("All Subsets (Global Markov)");

    private final String displayName;

    ConditioningSetType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}