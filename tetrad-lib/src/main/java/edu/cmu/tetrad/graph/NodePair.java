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

package edu.cmu.tetrad.graph;

/**
 * An unordered pair of nodes.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class NodePair {


    /**
     * The "First" node.
     */
    private final Node first;


    /**
     * The "second" node.
     */
    private final Node second;


    /**
     * <p>Constructor for NodePair.</p>
     *
     * @param first  a {@link edu.cmu.tetrad.graph.Node} object
     * @param second a {@link edu.cmu.tetrad.graph.Node} object
     */
    public NodePair(Node first, Node second) {
        if (first == null) {
            throw new NullPointerException("First node must not be null.");
        }
        if (second == null) {
            throw new NullPointerException("Second node must not be null.");
        }
        this.first = first;
        this.second = second;
    }

    //============================== Public methods =============================//

    /**
     * <p>Getter for the field <code>first</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getFirst() {
        return this.first;
    }

    /**
     * <p>Getter for the field <code>second</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getSecond() {
        return this.second;
    }

    /**
     * <p>hashCode.</p>
     *
     * @return a int
     */
    public int hashCode() {
        return this.first.hashCode() + this.second.hashCode();
    }


    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof NodePair thatPair)) {
            return false;
        }
        //        return this.first.equals(thatPair.first) && this.second.equals(thatPair.second) || this.first.equals(thatPair.second) && this.second.equals(thatPair.first);
        return (this.first == thatPair.first && this.second == thatPair.second) || (this.first == thatPair.second && this.second == thatPair.first);
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "{" + this.first + ", " + this.second + "}";
    }

}




