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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.VariableSource;
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
 * Stores information for a variable source about evidence we have for each variable as well as whether each variable
 * has been manipulated.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class Evidence implements TetradSerializable {
    private static final long serialVersionUID = 23L;

    /**
     * A proposition stating what we know for each variable.
     */
    private final Proposition proposition;

    /**
     * A manipulation indicating how the bayes Im should be manipulated before updating.
     */
    private Manipulation manipulation;

    //=============================CONSTRUCTORS=========================//

    /**
     * Tautology constructor--use Evidence.tautology() instead.
     */
    private Evidence(VariableSource variableSource) {
        if (variableSource == null) {
            throw new NullPointerException();
        }

        this.proposition = Proposition.tautology(variableSource);
        this.manipulation = new Manipulation(variableSource);
    }

    /**
     * Copy constructor.
     *
     * @param evidence a {@link edu.cmu.tetrad.bayes.Evidence} object
     */
    public Evidence(Evidence evidence) {
        if (evidence == null) {
            throw new NullPointerException();
        }

        this.proposition = new Proposition(evidence.getVariableSource(),
                evidence.getProposition());
        this.manipulation = new Manipulation(evidence.manipulation);
    }

    /**
     * Wraps the proposition. The Bayes IM and manipulation will be null.
     *
     * @param proposition a {@link edu.cmu.tetrad.bayes.Proposition} object
     */
    public Evidence(Proposition proposition) {
        if (proposition == null) {
            throw new NullPointerException();
        }

        this.proposition = new Proposition(proposition);
    }

    /**
     * <p>Constructor for Evidence.</p>
     *
     * @param evidence       a {@link edu.cmu.tetrad.bayes.Evidence} object
     * @param variableSource a {@link edu.cmu.tetrad.data.VariableSource} object
     */
    public Evidence(Evidence evidence, VariableSource variableSource) {
        if (variableSource == null) {
            throw new NullPointerException();
        }

        if (evidence == null) {
            throw new NullPointerException();
        }

        this.proposition = new Proposition(variableSource, evidence.getProposition());
        this.manipulation = new Manipulation(evidence.manipulation);
    }

    /**
     * <p>tautology.</p>
     *
     * @param variableSource a {@link edu.cmu.tetrad.data.VariableSource} object
     * @return a {@link edu.cmu.tetrad.bayes.Evidence} object
     */
    public static Evidence tautology(VariableSource variableSource) {
        return new Evidence(variableSource);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.bayes.Evidence} object
     */
    public static Evidence serializableInstance() {
        return new Evidence(MlBayesIm.serializableInstance());
    }

    //===========================PUBLIC METHODS=========================//

    /**
     * <p>getVariableSource.</p>
     *
     * @return the Bayes IM that this is evidence for.
     */
    public VariableSource getVariableSource() {
        return this.proposition.getVariableSource();
    }

    /**
     * <p>getNodeIndex.</p>
     *
     * @param nodeName a {@link java.lang.String} object
     * @return a int
     */
    public int getNodeIndex(String nodeName) {
        return this.proposition.getNodeIndex(nodeName);
    }

    /**
     * <p>getCategoryIndex.</p>
     *
     * @param nodeName a {@link java.lang.String} object
     * @param category a {@link java.lang.String} object
     * @return a int
     */
    public int getCategoryIndex(String nodeName, String category) {
        return this.proposition.getCategoryIndex(nodeName, category);
    }

    /**
     * <p>getNumNodes.</p>
     *
     * @return a int
     */
    public int getNumNodes() {
        return this.proposition.getVariableSource().getVariables().size();
    }

    /**
     * <p>getNode.</p>
     *
     * @param nodeIndex a int
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getNode(int nodeIndex) {
        return this.proposition.getVariableSource().getVariables().get(nodeIndex);
    }

    /**
     * <p>getVariable.</p>
     *
     * @param nodeName a {@link java.lang.String} object
     * @return a {@link edu.cmu.tetrad.data.DiscreteVariable} object
     */
    public DiscreteVariable getVariable(String nodeName) {
        int index = this.proposition.getVariableSource().getVariableNames().indexOf(nodeName);
        return (DiscreteVariable) this.proposition.getVariableSource().getVariables().get(index);
    }

    /**
     * <p>getNumCategories.</p>
     *
     * @param variable a int
     * @return a int
     */
    public int getNumCategories(int variable) {
        return this.proposition.getNumCategories(variable);
    }

    /**
     * <p>Getter for the field <code>proposition</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.bayes.Proposition} object
     */
    public Proposition getProposition() {
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
        StringBuilder buf = new StringBuilder();

        buf.append("\nEvidence:");
        buf.append(getProposition());
        buf.append("\n");

        for (int i = 0; i < getNumNodes(); i++) {
            buf.append(isManipulated(i) ? "(Man)" : "     ");
            buf.append("\t");
        }

        return buf.toString();
    }

    /**
     * <p>hasNoEvidence.</p>
     *
     * @param variable a int
     * @return a boolean
     */
    public boolean hasNoEvidence(int variable) {
        for (int i = 0; i < this.proposition.getNumCategories(variable); i++) {
            if (!this.proposition.isAllowed(variable, i)) {
                return false;
            }
        }

        return true;
    }

    /**
     * <p>getVariablesInEvidence.</p>
     *
     * @return the variable for which there is evidence.
     */
    public List<Node> getVariablesInEvidence() {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < getNumNodes(); i++) {
            if (this.proposition.getSingleCategory(i) != -1) {
                nodes.add(getNode(i));
            }
        }

        return nodes;
    }

    /**
     * <p>getCategory.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @param j    a int
     * @return a {@link java.lang.String} object
     */
    public String getCategory(Node node, int j) {
        DiscreteVariable variable = (DiscreteVariable) node;
        return variable.getCategory(j);
    }

    /**
     * Returna true just in case this evidence has a list of variables equal to those of the given variable source.
     *
     * @param variableSource a {@link edu.cmu.tetrad.data.VariableSource} object
     * @return a boolean
     */
    public boolean isIncompatibleWith(VariableSource variableSource) {
        List<Node> variables1 = getVariableSource().getVariables();
        List<Node> variables2 = variableSource.getVariables();

        return !variables1.equals(variables2);
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof Evidence evidence)) {
            return false;
        }

        return this.proposition.equals(evidence.proposition) && this.manipulation.equals(evidence.manipulation);

    }

    /**
     * <p>hashCode.</p>
     *
     * @return a int
     */
    public int hashCode() {
        int hashCode = 37;
        hashCode = 19 * hashCode + this.proposition.hashCode();
        hashCode = 19 * hashCode + this.manipulation.hashCode();
        return hashCode;
    }

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
}






