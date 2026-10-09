/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.test.IndependenceTest;

/**
 * Deprecated source-compatibility shell for the class now named {@link StarFci}. This class was renamed to
 * StarFci so that the class name matches the algorithm family of the StarFCI paper (Ramsey, Andrews, and
 * Spirtes); all behavior lives in {@link StarFci}, and this shell adds nothing beyond a bridge from the old
 * abstract method name, {@code getMarkovDag(boolean)}, to the new one, {@link StarFci#getMarkovCpdag(boolean)}.
 * Existing subclasses of this class continue to compile and behave identically; new code should extend
 * {@link StarFci} directly.
 *
 * @author josephramsey
 * @author bryanandrews
 * @see StarFci
 * @deprecated Renamed to {@link StarFci}; extend that class and override {@link StarFci#getMarkovCpdag(boolean)}
 * instead.
 */
@Deprecated
public abstract class StarFciGuaranteePag extends StarFci {

    /**
     * Constructs a new instance with the given independence test.
     *
     * @param test The independence test to use.
     */
    public StarFciGuaranteePag(IndependenceTest test) {
        super(test);
    }

    /**
     * Bridges the renamed abstract method: delegates to {@link #getMarkovDag(boolean)}, the abstract method of
     * this class before the rename, so existing subclasses keep working unchanged.
     *
     * @param verbose a boolean flag indicating whether to enable verbose logging.
     * @return the graph returned by {@link #getMarkovDag(boolean)}.
     * @throws InterruptedException if the operation is interrupted during execution.
     */
    @Override
    public final Graph getMarkovCpdag(boolean verbose) throws InterruptedException {
        return getMarkovDag(verbose);
    }

    /**
     * Returns the initial Markov model for the search: a Markov CPDAG, or any DAG in its Markov equivalence
     * class (the two are interchangeable; see {@link StarFci#getMarkovCpdag(boolean)}).
     *
     * @param verbose a boolean flag indicating whether to enable verbose logging.
     * @return a Markov CPDAG, or a DAG in its Markov equivalence class.
     * @throws InterruptedException if the operation is interrupted during execution.
     * @deprecated Override {@link StarFci#getMarkovCpdag(boolean)} on {@link StarFci} instead.
     */
    @Deprecated
    public abstract Graph getMarkovDag(boolean verbose) throws InterruptedException;
}
