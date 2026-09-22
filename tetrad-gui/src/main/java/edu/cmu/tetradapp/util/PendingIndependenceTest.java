///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A placeholder independence test installed by the Markov Check and Vertex Check editors when the chosen test
 * cannot be constructed for the current data and parameters--typically because the data contain missing values
 * and no missing-data policy has been chosen yet (see MissingDataUtils.gate). It lets the editor open normally,
 * exposing the data's variable list so the surrounding MarkovCheck machinery can be built; any attempt to
 * actually test independence throws an IllegalArgumentException carrying the original construction error's
 * message, which points the user at the parameter that needs to be set. The editors replace this placeholder
 * with the real test as soon as construction succeeds (e.g., after the user chooses a missing-data policy in
 * the Params dialog).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class PendingIndependenceTest implements IndependenceTest {

    /**
     * A short, user-facing name for the test that was requested (e.g., "Fisher Z Test").
     */
    private final String shortLabel;

    /**
     * The message from the exception thrown when the real test's construction was attempted.
     */
    private final String message;

    /**
     * The data model the test was to be constructed over.
     */
    private final DataModel dataModel;

    /**
     * The variables of the data model.
     */
    private final List<Node> variables;

    /**
     * Whether verbose output is requested (stored but unused).
     */
    private boolean verbose = false;

    /**
     * Constructs a placeholder for a test that could not be constructed.
     *
     * @param shortLabel A short, user-facing name for the requested test (e.g., "Fisher Z Test").
     * @param message    The message of the exception thrown by the attempted construction.
     * @param dataModel  The data model the test was to be constructed over.
     */
    public PendingIndependenceTest(String shortLabel, String message, DataModel dataModel) {
        this.shortLabel = shortLabel == null ? "Independence test" : shortLabel;
        this.message = message == null ? "The independence test could not be constructed." : message;
        this.dataModel = dataModel;
        this.variables = new ArrayList<>(dataModel.getVariables());
    }

    /**
     * Returns the message of the exception thrown when the real test's construction was attempted.
     *
     * @return The message.
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always throws, since the real test could not be constructed; the message says why and how to fix it.
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        throw new IllegalArgumentException(this.message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(this.variables);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataModel getData() {
        return this.dataModel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return this.shortLabel + " (not configured -- see Params)";
    }
}
