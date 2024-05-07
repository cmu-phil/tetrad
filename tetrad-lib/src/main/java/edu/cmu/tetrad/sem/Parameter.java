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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NamingProtocol;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.dist.Distribution;
import edu.cmu.tetrad.util.dist.Normal;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;

/**
 * Stores information about the identity of a SEM parameter--its name, its type (COEF, COVAR), and the node(s) it is
 * associated with.
 *
 * @author Don Crimbchin (djc2@andrew.cmu.edu)
 * @author josephramsey
 * @version $Id: $Id
 */
public final class Parameter implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The default distribution from which initial values are drawn for this distribution.
     */
    private static final Distribution DEFAULT_DISTRIBUTION =
            new Normal(0.0, 1.0);

    /**
     * One of the anchor endpoints specifying the parameter this is.
     *
     * @serial Cannot be null.
     */
    private final Node nodeA;

    /**
     * One of the anchor endpoints specifying the parameter this is.
     *
     * @serial Cannot be null.
     */
    private final Node nodeB;
    /**
     * The type of parameter--coefficient, covariance, or variance.
     *
     * @serial Cannot be null. Should be ParamType.VAR if nodeA != nodeB and ParamType.COVAR if nodeA == nodeB.
     */
    private final ParamType type;
    /**
     * The name of the parameter.
     *
     * @serial Cannot be null.
     */
    private String name;
    /**
     * True iff this parameter is fixed in estimation.
     *
     * @serial Any value.
     */
    private boolean fixed;

    /**
     * True iff this parameter should be initialized randomly.
     *
     * @serial Any value.
     */
    private boolean initializedRandomly = true;

    /**
     * If this parameter is initialized randomly, the initial value is drawn from this distribution.
     *
     * @serial Cannot be null.
     */
    private Distribution distribution = Parameter.DEFAULT_DISTRIBUTION;

    /**
     * If this parameter is either fixed or not initialized randomly, returns its starting value.
     *
     * @serial Any value.
     */
    private double startingValue = 1.0d;

    /**
     * <p>Constructor for Parameter.</p>
     *
     * @param name  The name of the parameter.
     * @param type  The type of the parameter--ParamType.COEF, ParamType.VAR, or ParamType.COVAR.
     * @param nodeA The "from" node.
     * @param nodeB The "to" node. (For variance freeParameters, this must be the same as the "from" node. For
     *              covariance freeParameters, it must be different from the "from" node.)
     */
    public Parameter(String name, ParamType type, Node nodeA, Node nodeB) {
        if (name == null) {
            throw new NullPointerException("Name must not be null.");
        }
        if (type == null) {
            throw new NullPointerException("ParamType must not be null.");
        }
        if (nodeA == null) {
            throw new NullPointerException("Node A must not be null.");
        }
        if (nodeB == null) {
            throw new NullPointerException("Node B must not be null.");
        }

        if (type == ParamType.VAR && nodeA != nodeB) {
            throw new IllegalArgumentException(
                    "Variance parameters must have " +
                    "nodeA and nodeB the same.");
        }

        if (type == ParamType.COVAR && nodeA == nodeB) {
            throw new IllegalArgumentException(
                    "Covariance parameters must have " +
                    "nodeA and nodeB different.");
        }

        this.name = name;
        this.type = type;
        this.nodeA = nodeA;
        this.nodeB = nodeB;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.sem.Parameter} object
     */
    public static Parameter serializableInstance() {
        return new Parameter("X", ParamType.COEF, new GraphNode("X"),
                new GraphNode("Y"));
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return the name of the parameter.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name for this parameter.
     *
     * @param name a {@link java.lang.String} object
     * @throws java.lang.IllegalArgumentException if the name does not begin with a letter.
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
     * <p>toString.</p>
     *
     * @return a string representation for this parameter.
     */
    public String toString() {
        return "<" + this.name + ", " + this.type + ", " + this.nodeA + ", " +
               this.nodeB + ", " + (this.fixed ? "fixed" : "free") + ">";
    }

    /**
     * <p>Getter for the field <code>nodeA</code>.</p>
     *
     * @return the "from" node for the edge this parameter is associated with.
     */
    public Node getNodeA() {
        return this.nodeA;
    }

    /**
     * <p>Getter for the field <code>nodeB</code>.</p>
     *
     * @return the "to" node for the edge this parameter is associated with.
     */
    public Node getNodeB() {
        return this.nodeB;
    }

    /**
     * <p>Getter for the field <code>type</code>.</p>
     *
     * @return the type of this parameter--ParamType.COEF or ParamType.COVAR. This is set at construction time.
     */
    public ParamType getType() {
        return this.type;
    }

    /**
     * <p>Getter for the field <code>distribution</code>.</p>
     *
     * @return the distributions that initial values should be drawn from for this parameter.
     */
    public Distribution getDistribution() {
        return this.distribution;
    }

    /**
     * Sets the distribution that initial values should be drawn from for this parameter. To set the parameter to always
     * use the same initial value, use tetrad.util.SingleValue.
     *
     * @param distribution a {@link edu.cmu.tetrad.util.dist.Distribution} object
     * @see edu.cmu.tetrad.util.dist.SingleValue
     */
    public void setDistribution(Distribution distribution) {
        if (distribution == null) {
            throw new NullPointerException("Distribution must not be null.");
        }
        this.distribution = distribution;
    }

    /**
     * <p>isFixed.</p>
     *
     * @return true iff this parameter should be held fixed during estimation.
     */
    public boolean isFixed() {
        return this.fixed;
    }

    /**
     * Sets whether this parameter should be held fixed during estimation.
     *
     * @param fixed True if the parameter will be held fixed, false if not.
     */
    public void setFixed(boolean fixed) {
        this.fixed = fixed;
    }

    /**
     * <p>Getter for the field <code>startingValue</code>.</p>
     *
     * @return the starting value if this is a fixed parameter.
     */
    public double getStartingValue() {
        return this.startingValue;
    }

    /**
     * Sets the starting value in case this is a fixed parameter.
     *
     * @param startingValue a double
     */
    public void setStartingValue(double startingValue) {
        this.startingValue = startingValue;
    }

    /**
     * <p>isInitializedRandomly.</p>
     *
     * @return true iff this parameter should be initialized randomly by drawing an initial value from its preset random
     * distribution.
     */
    public boolean isInitializedRandomly() {
        return this.initializedRandomly;
    }

    /**
     * Set to true iff this parameter should be initialized randomly by drawing an initial value from its preset random
     * distribution.
     *
     * @param initializedRandomly a boolean
     */
    public void setInitializedRandomly(boolean initializedRandomly) {
        this.initializedRandomly = initializedRandomly;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s The stream to read from.
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.name == null) {
            throw new NullPointerException();
        }

        if (this.distribution == null) {
            throw new NullPointerException();
        }

        if (this.type == ParamType.VAR && this.nodeA != this.nodeB) {
            throw new IllegalStateException();
        }

        if (this.type == ParamType.COVAR && this.nodeA == this.nodeB) {
            throw new IllegalStateException();
        }
    }
}





