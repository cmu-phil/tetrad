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
     * Testing all possible independence facts implied by the graph.  Some independence facts obtained in this way may
     * be for implied dependencies.
     */
    GLOBAL_MARKOV,

    /**
     * Testing independence facts implied by the graph, conditioning on the parents of each variable in the graph. Some
     * independence facts obtained in this way may be for implied dependencies.
     */
    LOCAL_MARKOV,

    /**
     * Conditioning on the parents and neighbors of each variable in the graph. Some independence facts obtained in this
     * way may be for implied dependencies.
     */
    PARENTS_AND_NEIGHBORS,

    /**
     * Conditioning on the Markov blanket of each variable in the graph. These are all conditional independence facts,
     * so no conditional dependence facts will be listed if this option is selected.
     */
    MARKOV_BLANKET,

    /**
     * Conditioning on variables in the recursive order of a depth-first M-separation search. Some independence facts
     * obtained in this way may be for implied dependencies.
     */
    RECURSIVE_BLOCKING,

    /**
     * Conditioning on variables in the recursive order of a depth-first M-separation search. Some independence facts
     * obtained in this way may be for implied dependencies.
     */
    RECURSIVE_ADJUSTMENT,

    /**
     * Conditioning on noncolliders only. Some independence facts obtained in this way may be for implied dependencies.
     * This is equivalent to the "noncolliders only" option in the PC algorithm.
     */
    NONCOLLIDERS_ONLY,

    /**
     * Testing independence facts implied by the graph, conditioning on the parents of each variable in the graph, in a
     * causal order of the graph. Some independence facts obtained in this way may be for implied dependencies.
     */
    ORDERED_LOCAL_MARKOV,

    /**
     * Generates a set of independence facts that implies Global Markov for MAG. Taking a MAG in the given PAG in the
     * calling method.
     *
     * @see AndrewsOrderedLocalMarkovProperty
     */
    ANDREWS_ORDERED_LOCAL_MARKOV_PROPERTY,

    /**
     * Specifies a conditioning set type that generates a set of independence facts conforming to the
     * Richardson Ordered Local Markov Property for Markov equivalent models such as MAGs (Maximal Ancestral Graphs).
     * This method leverages a specific causal ordering in the graph to verify causal and conditional independence
     * relationships, taking into account the structure of the underlying Markov equivalence class.
     */
    RICHARDSON_ORDERED_LOCAL_MARKOV_PROPERTY,

    /**
     * Represents the assumption or property in probabilistic graphical models that
     * given any two variables in the model, they are conditionally independent
     * given a direct connection or edge between them. This property is widely
     * applicable to pairwise Markov random fields where the edges encode
     * direct probabilistic relationships between the connected variables.
     */
    PAIRWISE_MARKOV_PROPERTY

}

