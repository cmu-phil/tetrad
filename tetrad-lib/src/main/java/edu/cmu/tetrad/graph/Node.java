///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.util.TetradSerializable;

import java.beans.PropertyChangeListener;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Represents an object with a name, node type, and position that can serve as a node in a graph.
 *
 * @author josephramsey
 * @see NodeType
 */
public interface Node extends TetradSerializable, Comparable<Node> {

    Pattern ALPHA = Pattern.compile("^[a-zA-Z]+$");
    Pattern ALPHA_NUM = Pattern.compile("^[a-zA-Z]+[0-9]+$");
    Pattern LAG = Pattern.compile("^.+:[0-9]+$");

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
     * Returns the hashcode for this node.
     *
     * @param node the object to be compared.
     * @return the hashcode for this node.
     */
    default int compareTo(Node node) {
        String node1 = getName();
        String node2 = node.getName();

        boolean isAlpha1 = Node.ALPHA.matcher(node1).matches();
        boolean isAlpha2 = Node.ALPHA.matcher(node2).matches();
        boolean isAlphaNum1 = Node.ALPHA_NUM.matcher(node1).matches();
        boolean isAlphaNum2 = Node.ALPHA_NUM.matcher(node2).matches();
        boolean isLag1 = Node.LAG.matcher(node1).matches();
        boolean isLag2 = Node.LAG.matcher(node2).matches();

        if (isAlpha1) {
            if (isLag2) {
                return -1;
            }
        } else if (isAlphaNum1) {
            if (isAlphaNum2) {
                String s1 = node1.replaceAll("\\d+", "");
                String s2 = node2.replaceAll("\\d+", "");
                if (s1.equals(s2)) {
                    String n1 = node1.replaceAll("\\D+", "");
                    String n2 = node2.replaceAll("\\D+", "");

                    return Integer.valueOf(n1).compareTo(Integer.valueOf(n2));
                } else {
                    return s1.compareTo(s2);
                }
            } else if (isLag2) {
                return -1;
            }
        } else if (isLag1) {
            if (isAlpha2 || isAlphaNum2) {
                return 1;
            } else if (isLag2) {
                String l1 = node1.replaceAll(":", "");
                String l2 = node2.replaceAll(":", "");
                String s1 = l1.replaceAll("\\d+", "");
                String s2 = l2.replaceAll("\\d+", "");
                if (s1.equals(s2)) {
                    String n1 = l1.replaceAll("\\D+", "");
                    String n2 = l2.replaceAll("\\D+", "");

                    return Integer.valueOf(n1).compareTo(Integer.valueOf(n2));
                } else {
                    return s1.compareTo(s2);
                }
            }
        }

        return node1.compareTo(node2);
    }

    Map<String, Object> getAllAttributes();

    Object getAttribute(String key);

    void removeAttribute(String key);

    void addAttribute(String key, Object value);

}
