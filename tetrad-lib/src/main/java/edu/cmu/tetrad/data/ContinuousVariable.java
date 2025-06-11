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
package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.NodeVariableType;
import edu.cmu.tetrad.util.TetradLogger;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a real-valued variable. The values are doubles, and the default missing value marker for is Double.NaN.
 *
 * @author Willie Wheeler 07/99
 * @author josephramsey modifications 12/00
 * @version $Id: $Id
 */
public final class ContinuousVariable extends AbstractVariable implements Variable {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * This is the value which represents missing data in data columns for this variable.
     */
    private static final double MISSING_VALUE = Double.NaN;

    /**
     * The node type.
     */
    private final Map<String, Object> attributes = new HashMap<>();
    /**
     * The node type.
     */
    private NodeType nodeType = NodeType.MEASURED;
    /**
     * Node variable type (domain, interventional status, interventional value..) of this node variable
     */
    private NodeVariableType nodeVariableType = NodeVariableType.DOMAIN;
    /**
     * The x coordinate of the center of the node.
     */
    private int centerX = -1;
    /**
     * The y coordinate of the center of the node.
     */
    private int centerY = -1;
    /**
     * Fires property change events.
     */
    private transient PropertyChangeSupport pcs;

    /**
     * Constructs a new continuous variable with the given name.
     *
     * @param name the name of the variable.
     */
    public ContinuousVariable(String name) {
        super(name);
    }

    /**
     * Copy constructor.
     *
     * @param variable a {@link edu.cmu.tetrad.data.ContinuousVariable} object
     */
    public ContinuousVariable(ContinuousVariable variable) {
        super(variable.getName());
        this.nodeType = variable.nodeType;
        this.centerX = variable.centerX;
        this.centerY = variable.centerY;
        this.nodeVariableType = variable.nodeVariableType;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.data.ContinuousVariable} object
     */
    public static ContinuousVariable serializableInstance() {
        return new ContinuousVariable("X");
    }

    /**
     * <p>getDoubleMissingValue.</p>
     *
     * @return the missing value marker.
     */
    public static double getDoubleMissingValue() {
        return ContinuousVariable.MISSING_VALUE;
    }

    /**
     * Determines whether the argument is equal to the missing value marker.
     *
     * @param value the Object to test--should be a wrapped version of the missing value marker.
     * @return true iff it really is a wrapped version of the missing value marker.
     */
    public static boolean isDoubleMissingValue(double value) {
        return Double.isNaN(value);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Checks the value to make sure it's a legitimate value for this column.
     */
    public boolean checkValue(Object value) {
        if (value instanceof Double) {
            return true;
        } else if (value instanceof String) {
            try {
                Double.parseDouble((String) value);
                return true;
            } catch (Exception e) {
                return false;
            }
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * {@inheritDoc}
     */
    public Node like(String name) {
        ContinuousVariable continuousVariable = new ContinuousVariable(name);
        continuousVariable.setNodeType(getNodeType());
        return continuousVariable;
    }

    /**
     * <p>getMissingValueMarker.</p>
     *
     * @return the missing value marker, wrapped as a Double.
     */
    public Object getMissingValueMarker() {
        return ContinuousVariable.MISSING_VALUE;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Determines whether the argument is equal to the missing value marker.
     */
    public boolean isMissingValue(Object value) {
        if (value instanceof Double) {
            double doubleValue = (Double) value;
            return Double.isNaN(doubleValue);
        }

        return false;
    }

    /**
     * <p>hashCode.</p>
     *
     * @return a int
     */
    public int hashCode() {
        return this.getName().hashCode();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Two continuous variables are equal if they have the same name and the same missing value marker.
     */
    // The identity of a node can't be changed by changing its name.
    public boolean equals(Object o) {
        if (o == null) return false;
        if (!(o instanceof ContinuousVariable)) return false;
        if (!getName().equals(((Node) o).getName()))  return false;
        return getNodeType() == ((ContinuousVariable) o).getNodeType();
    }

    /**
     * <p>Getter for the field <code>nodeType</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.NodeType} object
     */
    public NodeType getNodeType() {
        if (nodeType == null) {
            throw new IllegalArgumentException("Node type cannot be null.");
        }

        return this.nodeType;
    }

    /**
     * {@inheritDoc}
     */
    public void setNodeType(NodeType nodeType) {
        if (nodeType == null) {
            throw new IllegalArgumentException("Node type cannot be null.");
        }

        this.nodeType = nodeType;
    }

    @Override
    public boolean getSelectionBias() {
        return false;
    }

    /**
     * <p>Getter for the field <code>centerX</code>.</p>
     *
     * @return the x coordinate of the center of the node.
     */
    public int getCenterX() {
        return this.centerX;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the x coordinate of the center of this node.
     */
    public void setCenterX(int centerX) {
        this.centerX = centerX;
    }

    /**
     * <p>Getter for the field <code>centerY</code>.</p>
     *
     * @return the y coordinate of the center of the node.
     */
    public int getCenterY() {
        return this.centerY;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the y coordinate of the center of this node.
     */
    public void setCenterY(int centerY) {
        this.centerY = centerY;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the (x, y) coordinates of the center of this node.
     */
    public void setCenter(int centerX, int centerY) {
        setCenterX(centerX);
        setCenterY(centerY);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Adds a property change listener.
     */
    public void addPropertyChangeListener(PropertyChangeListener l) {
        getPcs().addPropertyChangeListener(l);
    }

    private PropertyChangeSupport getPcs() {
        if (this.pcs == null) {
            this.pcs = new PropertyChangeSupport(this);
        }

        return this.pcs;
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

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
