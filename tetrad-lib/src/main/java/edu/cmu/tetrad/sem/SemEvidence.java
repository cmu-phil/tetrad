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

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores information for a SemIm about evidence we have for each variable as well as whether each variable has been
 * manipulated.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class SemEvidence implements TetradSerializable {
    private static final long serialVersionUID = 23L;

    /**
     * Bayes IM that this is evidence for.
     *
     * @serial
     */
    private final SemIm semIm;

    /**
     * A proposition stating what we know for each variable.
     *
     * @serial Cannot be null.
     */
    private final SemProposition proposition;

    /**
     * A manipulation indicating how the bayes Im should be manipulated before updating.
     *
     * @serial
     */
    private final SemManipulation manipulation;

    /**
     * Constructs a container for evidence for the given Bayes IM.
     *
     * @param semIm a {@link edu.cmu.tetrad.sem.SemIm} object
     */
    public SemEvidence(SemIm semIm) {
        if (semIm == null) {
            throw new NullPointerException();
        }

        this.semIm = semIm;
        this.proposition = SemProposition.tautology(semIm);
        this.manipulation = new SemManipulation(semIm);
    }

    /**
     * <p>Constructor for SemEvidence.</p>
     *
     * @param evidence a {@link edu.cmu.tetrad.sem.SemEvidence} object
     */
    public SemEvidence(SemEvidence evidence) {
        this.semIm = evidence.semIm;
        this.proposition = new SemProposition(evidence.proposition);
        this.manipulation = new SemManipulation(evidence.manipulation);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.sem.SemEvidence} object
     */
    public static SemEvidence serializableInstance() {
        return new SemEvidence(SemIm.serializableInstance());
    }

    /**
     * <p>Getter for the field <code>semIm</code>.</p>
     *
     * @return the Bayes IM that this is evidence for.
     */
    public SemIm getSemIm() {
        return this.semIm;
    }

    /**
     * <p>getNodeIndex.</p>
     *
     * @param nodeName a {@link java.lang.String} object
     * @return a int
     */
    public int getNodeIndex(String nodeName) {
        List<Node> nodes = this.semIm.getSemPm().getVariableNodes();

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);

            if (node.getName().equals(nodeName)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * <p>getNodeIndex.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return a int
     */
    public int getNodeIndex(Node node) {
        List<Node> nodes = this.semIm.getSemPm().getVariableNodes();

        for (int i = 0; i < nodes.size(); i++) {
            Node _node = nodes.get(i);

            if (node == _node) {
                return i;
            }
        }

        return -1;
    }

    /**
     * <p>getNumNodes.</p>
     *
     * @return a int
     */
    public int getNumNodes() {
        return this.semIm.getVariableNodes().size();
    }

    /**
     * <p>getNode.</p>
     *
     * @param nodeIndex a int
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getNode(int nodeIndex) {
        return this.semIm.getVariableNodes().get(nodeIndex);
    }

    /**
     * <p>Getter for the field <code>proposition</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.SemProposition} object
     */
    public SemProposition getProposition() {
        return this.proposition;
    }

    /**
     * <p>isManipulated.</p>
     *
     * @param nodeIndex a int
     * @return a boolean
     */
    public boolean isManipulated(int nodeIndex) {
        return this.manipulation.isManipulated(nodeIndex);
    }

    /**
     * <p>setManipulated.</p>
     *
     * @param nodeIndex   a int
     * @param manipulated a boolean
     */
    public void setManipulated(int nodeIndex, boolean manipulated) {
        this.manipulation.setManipulated(nodeIndex, manipulated);
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        List<Node> nodes = this.semIm.getVariableNodes();
        StringBuilder buf = new StringBuilder();
        buf.append("\nEvidence: ");

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            buf.append("\n").append(node).append(" = ")
                    .append(this.proposition.getValue(i));

            if (isManipulated(i)) {
                buf.append("\tManipulated");
            }
        }

        return buf.toString();
    }

    /**
     * <p>getNodesInEvidence.</p>
     *
     * @return the variable for which there is evidence.
     */
    public List<Node> getNodesInEvidence() {
        List<Node> nodes = this.semIm.getVariableNodes();
        List<Node> nodesInEvidence = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            if (!Double.isNaN(this.proposition.getValue(i))) {
                nodesInEvidence.add(nodes.get(i));
            }
        }

        return nodesInEvidence;
    }

    /**
     * This method checks if the given object is equal to the current instance of SemEvidence. Two SemEvidence objects
     * are considered equal if they have the same semIm, proposition, and manipulation.
     *
     * @param o the object to be compared with the current instance of SemEvidence
     * @return true if the given object is equal to the current instance of SemEvidence, false otherwise
     * @throws IllegalArgumentException if the given object is not an instance of SemEvidence
     */
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof SemEvidence evidence)) {
            throw new IllegalArgumentException();
        }

        return this.semIm == evidence.semIm && this.proposition.equals(evidence.proposition) && this.manipulation.equals(evidence.manipulation);

    }

    /**
     * <p>hashCode.</p>
     *
     * @return a int
     */
    public int hashCode() {
        int hashCode = 37;
        hashCode = 19 * hashCode + this.semIm.hashCode();
        hashCode = 19 * hashCode + this.proposition.hashCode();
        hashCode = 19 * hashCode + this.manipulation.hashCode();
        return hashCode;
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to serialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to deserialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }
}






