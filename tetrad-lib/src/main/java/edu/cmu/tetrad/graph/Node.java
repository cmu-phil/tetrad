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

import edu.cmu.tetrad.util.NaturalSort;
import edu.cmu.tetrad.util.TetradSerializable;

import java.beans.PropertyChangeListener;
import java.io.Serial;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Represents an object with a name, node type, and position that can serve as a node in a graph.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see NodeType
 */
public interface Node extends TetradSerializable, Comparable<Node> {

    /**
     * Constant <code>ALPHA</code>
     */
    Pattern ALPHA = Pattern.compile("^[a-zA-Z]+$");
    /**
     * Constant <code>ALPHA_NUM</code>
     */
    Pattern ALPHA_NUM = Pattern.compile("^[a-zA-Z]+[0-9]+$");
    /**
     * Constant <code>LAG</code>
     */
    Pattern LAG = Pattern.compile("^.+:[0-9]+$");

    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    @Serial
    long serialVersionUID = 23L;

    /**
     * Returns the name of this node.
     *
     * @return the name of the node.
     */
    String getName();

    /**
     * Sets the name of this node.
     *
     * @param name the name of this node.
     */
    void setName(String name);

    /**
     * Returns the rank, or -1 if no rank is set.
     *
     * @return the rank of the node
     */
    default int getRank() {
        return -1;
    }

    /**
     * Sets the rank, or -1 if no rank is set.
     *
     * @param rank the rank to set
     * @throws UnsupportedOperationException if the node type does not support setting a rank.
     */
    default void setRank(int rank) {
        throw new UnsupportedOperationException("Rank is not supported for this node type.");
    }

    /**
     * Returns the display name.
     *
     * @return the display name
     */
    default String getDisplayName() {
        boolean displayRank = !(getRank() == -1); // || getRank() == 1);
        return getName() + (displayRank ? "(" + getRank() + ")" : "");
    }

    /**
     * Returns the node type for this node.
     *
     * @return the node type for this node.
     */
    NodeType getNodeType();

    /**
     * Sets the node type for this node.
     *
     * @param nodeType the node type for this node.
     */
    void setNodeType(NodeType nodeType);

    /**
     * Returns the selection bias status for this node.
     *
     * @return the selection bias status for this node.
     */
    boolean getSelectionBias();

    /**
     * Returns the selection bias status for this node.
     *
     * @param selectionBias the selection bias status for this node.
     */
    void setSelectionBias(boolean selectionBias);

    /**
     * Returns the node shape for this node.
     *
     * @return the intervention type
     */
    NodeVariableType getNodeVariableType();

    /**
     * Sets the type (domain, interventional status, interventional value..) for this node variable
     *
     * @param nodeVariableType the type (domain, interventional status, interventional value..) for this node variable
     */
    void setNodeVariableType(NodeVariableType nodeVariableType);

    /**
     * Returns the intervention type for this node.
     *
     * @return a string representation of the node.
     */
    String toString();

    /**
     * Returns the x coordinate of the center of this node.
     *
     * @return the x coordinate of the center of the node.
     */
    int getCenterX();

    /**
     * Sets the x coordinate of the center of this node.
     *
     * @param centerX This coordinate.
     */
    void setCenterX(int centerX);

    /**
     * Returns the y coordinate of the center of this node.
     *
     * @return the y coordinate of the center of the node.
     */
    int getCenterY();

    /**
     * Sets the y coordinate of the center of this node.
     *
     * @param centerY This coordinate.
     */
    void setCenterY(int centerY);

    /**
     * Sets the (x, y) coordinates of the center of this node.
     *
     * @param centerX The x coordinate.
     * @param centerY The y coordinate.
     */
    void setCenter(int centerX, int centerY);

    /**
     * Adds a property change listener.
     *
     * @param l This listener.
     */
    void addPropertyChangeListener(PropertyChangeListener l);

    /**
     * Removes a property change listener.
     *
     * @return a hashcode for this variable.
     */
    int hashCode();

    /**
     * Tests whether this variable is equal to the given variable.
     *
     * @param o a {@link java.lang.Object} object
     * @return true iff this variable is equal to the given variable.
     */
    boolean equals(Object o);

    /**
     * Creates a new node of the same type as this one with the given name.
     *
     * @param name the name of the new node.
     * @return the new node.
     */
    Node like(String name);

    /**
     * Compares this node to the given node by name, using the shared display order for
     * possibly-lagged names: unlagged names first, then lagged names in order of increasing lag,
     * with natural ordering of base names within a lag group and the raw name as a final
     * tiebreaker, so distinct names never compare equal. Never throws.
     * <p>
     * This replaces a bucketed comparison that ordered names by concatenating all their digits,
     * under which distinct lagged names such as "X12:3" and "X1:23" compared equal (both reduce
     * to the digit string "123"), names with digit runs too long for an int crashed with
     * NumberFormatException, and cross-bucket comparisons fell back to raw string order in ways
     * that could violate transitivity.
     *
     * @param node the node to be compared.
     * @return a negative integer, zero, or a positive integer as this node's name orders before,
     * the same as, or after the given node's name.
     */
    default int compareTo(Node node) {
        return NaturalSort.lagAscendingComparator().compare(getName(), node.getName());
    }

    /**
     * <p>getAllAttributes.</p>
     *
     * @return a {@link java.util.Map} object
     */
    Map<String, Object> getAllAttributes();

    /**
     * <p>getAttribute.</p>
     *
     * @param key a {@link java.lang.String} object
     * @return a {@link java.lang.Object} object
     */
    Object getAttribute(String key);

    /**
     * <p>removeAttribute.</p>
     *
     * @param key a {@link java.lang.String} object
     */
    void removeAttribute(String key);

    /**
     * <p>addAttribute.</p>
     *
     * @param key   a {@link java.lang.String} object
     * @param value a {@link java.lang.Object} object
     */
    void addAttribute(String key, Object value);

}

