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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.session.SessionNode;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * A node in a SessionWrapper; wraps a SessionNode and presents it as a GraphNode.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see SessionNode
 * @see edu.cmu.tetrad.graph.GraphNode
 * @see SessionWrapper
 */
public class SessionNodeWrapper extends GraphNode {
    private static final long serialVersionUID = 23L;

    /**
     * The SessionNode being wrapped.
     *
     * @serial Cannot be null.
     */
    private final SessionNode sessionNode;

    /**
     * The button type of the session node (some string defined in the config file). "???" indicates that no button type
     * has been set.
     *
     * @serial Cannot be null.
     */
    private String buttonType = "???";

    //==========================CONSTRUCTORS==========================//

    /**
     * Wraps the given SessionNode as a SessionNodeWrapper for use in a SessionWrapper. The name of the SessionNode is
     * used as the name of the SessionNodeWrapper. A button type may optionally be set.
     *
     * @param sessionNode a {@link SessionNode} object
     */
    public SessionNodeWrapper(SessionNode sessionNode) {
        super(sessionNode.getDisplayName());
        this.sessionNode = sessionNode;
        setNodeType(NodeType.SESSION);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.graph.GraphNode} object
     * @see TetradSerializableUtils
     */
    public static GraphNode serializableInstance() {
        return new SessionNodeWrapper(SessionNode.serializableInstance());
    }

    //===========================PUBLIC METHODS=======================//

    /**
     * <p>getSessionName.</p>
     *
     * @return the session name. (Should return the same as getNode.)
     */
    public String getSessionName() {
        return this.sessionNode.getDisplayName();
    }

    /**
     * Sets the session name. This method should be used in preference to setName, which only sets the name in the
     * superclass.
     *
     * @param name a {@link java.lang.String} object
     */
    public void setSessionName(String name) {
//        if (!NamingProtocol.isLegalName(name)) {
//            throw new IllegalArgumentException(
//                    NamingProtocol.getProtocolDescription() + ": " + name);
//        }

        // Implementation note: the test for null is necessary to
        // account for the fact that when first constructed the
        // session node cannot be set before the superclass calls this
        // (overriding) method. jdramsey 12/29/01
        if (this.sessionNode != null) {
            this.setName(name);
            this.sessionNode.setDisplayName(name);
        }
    }

    /**
     * Gets the buttonType of the node, which is the buttonType of the wrapped SessionNode.
     *
     * @return a {@link java.lang.String} object
     */
    public String getButtonType() {
        return this.buttonType;
    }

    /**
     * Sets the buttonType of the node, which is the buttonType of the wrapped SessionNode.
     *
     * @param buttonType a {@link java.lang.String} object
     */
    public void setButtonType(String buttonType) {
        if (buttonType == null) {
            throw new NullPointerException("Button type cannot be null.");
        }

        this.buttonType = buttonType;
    }

    /**
     * <p>Getter for the field <code>sessionNode</code>.</p>
     *
     * @return the SessionNode being wrapped.
     */
    public SessionNode getSessionNode() {
        return this.sessionNode;
    }

    /**
     * <p>getRepetition.</p>
     *
     * @return a int
     */
    public int getRepetition() {
        return this.sessionNode.getRepetition();
    }

    /**
     * <p>setRepetition.</p>
     *
     * @param repetition a int
     */
    public void setRepetition(int repetition) {
        this.sessionNode.setRepetition(repetition);
    }

    /**
     * Must override hashCode in GraphNode to make sure that this object doesn't get hashed differently if its name
     * changes. If it does, session box deletion won't work.
     *
     * @return a int
     */
    public int hashCode() {
        return 1;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Must override equals in GraphNode to make sure equality is object identity. In particular, change of name does
     * not constitute change of identity.
     */
    public boolean equals(Object o) {
        return this == o;
    }

    /**
     * <p>toString.</p>
     *
     * @return a string representation of the node.
     */
    public String toString() {
        return "SessionNodewrapper (type " + getButtonType() + ", name " +
               getSessionName() + ")";
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s The object input stream.
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.sessionNode == null) {
            throw new NullPointerException();
        }

        if (this.buttonType == null) {
            throw new NullPointerException();
        }

        setNodeType(NodeType.SESSION);
    }
}





