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
package edu.cmu.tetradapp.knowledge_editor;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.NodeVariableType;
import edu.cmu.tetrad.util.NamingProtocol;
import edu.cmu.tetrad.util.TetradSerializableExcluded;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.beans.PropertyChangeListener;
import java.io.Serial;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a node that's just a string name.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class KnowledgeModelNode implements Node, TetradSerializableExcluded {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Attributes of this node variable
     */
    private final Map<String, Object> attributes = new HashMap<>();
    /**
     * Name of this node variable
     */
    private String name;
    /**
     * Node variable type (domain, interventional status, interventional value..) of this node variable
     */
    private NodeVariableType nodeVariableType = NodeVariableType.DOMAIN;
    /**
     * Center x coordinate of this node variable
     */
    private int centerX;
    /**
     * Center y coordinate of this node variable
     */
    private int centerY;

    //=============================CONSTRUCTORS=========================//

    /**
     * <p>Constructor for KnowledgeModelNode.</p>
     *
     * @param varName a {@link java.lang.String} object
     */
    public KnowledgeModelNode(String varName) {
        if (varName == null) {
            throw new NullPointerException();
        }

        this.name = varName;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.knowledge_editor.KnowledgeModelNode} object
     * @see TetradSerializableUtils
     */
    public static KnowledgeModelNode serializableInstance() {
        return new KnowledgeModelNode("X");
    }

    //=============================PUBLIC METHODS=======================//

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     */
    public void setName(String name) {
        if (name == null) {
            throw new NullPointerException();
        }

        if (!NamingProtocol.isLegalName(name)) {
            throw new IllegalArgumentException(
                    NamingProtocol.getProtocolDescription());
        }

        this.name = name;
    }

    /**
     * <p>getNodeType.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.NodeType} object
     */
    public NodeType getNodeType() {
        return NodeType.NO_TYPE;
    }

    /**
     * {@inheritDoc}
     */
    public void setNodeType(NodeType nodeType) {
        throw new UnsupportedOperationException();
    }

    /**
     * <p>Getter for the field <code>centerX</code>.</p>
     *
     * @return a int
     */
    public int getCenterX() {
        return this.centerX;
    }

    /**
     * {@inheritDoc}
     */
    public void setCenterX(int centerX) {
        this.centerX = centerX;
    }

    /**
     * <p>Getter for the field <code>centerY</code>.</p>
     *
     * @return a int
     */
    public int getCenterY() {
        return this.centerY;
    }

    /**
     * {@inheritDoc}
     */
    public void setCenterY(int centerY) {
        this.centerY = centerY;
    }

    /**
     * {@inheritDoc}
     */
    public void setCenter(int centerX, int centerY) {
        this.centerX = centerX;
        this.centerY = centerY;
    }

    /**
     * {@inheritDoc}
     */
    public void addPropertyChangeListener(PropertyChangeListener l) {
        // Ignore.
    }

    /**
     * {@inheritDoc}
     */
    public Node like(String name) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return getName();
    }

//    @Override
//    public int compareTo(Node node) {
//        String name = getName();
//        String[] tokens1 = name.split(":");
//        String _name = node.getName();
//        String[] tokens2 = _name.split(":");
//
//        if (tokens1.length == 1) {
//            tokens1 = new String[]{tokens1[0], "0"};
//        }
//
//        if (tokens2.length == 1) {
//            tokens2 = new String[]{tokens2[0], "0"};
//        }
//
//        int i1 = tokens1[1].compareTo(tokens2[1]);
//        int i2 = tokens1[0].compareTo(tokens2[0]);
//
//        if (i1 == 0) {
//            return i2;
//        } else {
//            return i1;
//        }
//    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeVariableType getNodeVariableType() {
        return this.nodeVariableType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNodeVariableType(NodeVariableType nodeVariableType) {
        this.nodeVariableType = nodeVariableType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> getAllAttributes() {
        return this.attributes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeAttribute(String key) {
        this.attributes.remove(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

}
