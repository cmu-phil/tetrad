///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores information for a SemIm about evidence we have for each variable as
 * well as whether each variable has been manipulated.
 *
 * @author Joseph Ramsey
 */
public final class SemEvidence implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * Bayes IM that this is evidence for.
     *
     * @serial
     */
    private SemIm semIm;

    /**
     * A proposition stating what we know for each variable.
     *
     * @serial Cannot be null.
     */
    private SemProposition proposition;

    /**
     * A manipulation indicating how the bayes Im should be manipulated before
     * updating.
     *
     * @serial
     */
    private SemManipulation manipulation;

    //=============================CONSTRUCTORS=========================//

    /**
     * Constructs a container for evidence for the given Bayes IM.
     */
    public SemEvidence(SemIm semIm) {
        if (semIm == null) {
            throw new NullPointerException();
        }

        this.semIm = semIm;
        this.proposition = SemProposition.tautology(semIm);
        this.manipulation = new SemManipulation(semIm);
    }

//    /**
//     * Copy constructor.
//     */
//    public SemEvidence(SemEvidence evidence) {
//        if (evidence == null) {
//            throw new NullPointerException();
//        }
//
//        this.semIm = evidence.getSemIm();
//        this.proposition = new SemProposition(semIm, evidence.getProposition());
//        this.manipulation = new SemManipulation(evidence.manipulation);
//    }

//    /**
//     * Wraps the proposition. The Bayes IM and manipulation will be null.
//     */
//    public SemEvidence(SemProposition proposition) {
//        if (proposition == null) {
//            throw new NullPointerException();
//        }
//
//        this.semIm = proposition.getSemIm();
//        this.proposition = new SemProposition(proposition);
//        this.manipulation = new SemManipulation(semIm);
//    }

//    public SemEvidence(SemEvidence evidence, SemIm semIm) {
//        if (semIm == null) {
//            throw new NullPointerException();
//        }
//
//        if (evidence == null) {
//            throw new NullPointerException();
//        }
//
//        this.semIm = semIm;
//        this.proposition = new SemProposition(semIm, evidence.getProposition());
//        this.manipulation = new SemManipulation(evidence.manipulation);
//    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static SemEvidence serializableInstance() {
        return new SemEvidence(SemIm.serializableInstance());
    }

    //===========================PUBLIC METHODS=========================//

    /**
     * @return the Bayes IM that this is evidence for.
     */
    public SemIm getSemIm() {
        return this.semIm;
    }

    public int getNodeIndex(String nodeName) {
        List<Node> nodes = semIm.getSemPm().getVariableNodes();

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);

            if (node.getName().equals(nodeName)) {
                return i;
            }
        }

        return -1;
    }

    public int getNodeIndex(Node node) {
        List<Node> nodes = semIm.getSemPm().getVariableNodes();

        for (int i = 0; i < nodes.size(); i++) {
            Node _node = nodes.get(i);

            if (node == _node) {
                return i;
            }
        }

        return -1;
    }

    public int getNumNodes() {
        return semIm.getVariableNodes().size();
    }

    public Node getNode(int nodeIndex) {
        return semIm.getVariableNodes().get(nodeIndex);
    }

    public SemProposition getProposition() {
        return this.proposition;
    }

    public boolean isManipulated(int nodeIndex) {
        return manipulation.isManipulated(nodeIndex);
    }

    public void setManipulated(int nodeIndex, boolean manipulated) {
        manipulation.setManipulated(nodeIndex, manipulated);
    }

    public String toString() {
        List<Node> nodes = semIm.getVariableNodes();
        StringBuilder buf = new StringBuilder();
        buf.append("\nEvidence: ");

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            buf.append("\n").append(node).append(" = ")
                    .append(proposition.getValue(i));

            if (isManipulated(i)) {
                buf.append("\tManipulated");
            }
        }

        return buf.toString();
    }

    /**
     * @return the variable for which there is evidence.
     */
    public List<Node> getNodesInEvidence() {
        List<Node> nodes = semIm.getVariableNodes();
        List<Node> nodesInEvidence = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            if (!Double.isNaN(proposition.getValue(i))) {
                nodesInEvidence.add(nodes.get(i));
            }
        }

        return nodesInEvidence;
    }

    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof SemEvidence)) {
            throw new IllegalArgumentException();
        }

        SemEvidence evidence = (SemEvidence) o;

        return semIm == evidence.semIm && proposition.equals(evidence.proposition) && manipulation.equals(evidence.manipulation);

    }

    public int hashCode() {
        int hashCode = 37;
        hashCode = 19 * hashCode + semIm.hashCode();
        hashCode = 19 * hashCode + proposition.hashCode();
        hashCode = 19 * hashCode + manipulation.hashCode();
        return hashCode;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (proposition == null) {
            throw new NullPointerException();
        }
    }
}






