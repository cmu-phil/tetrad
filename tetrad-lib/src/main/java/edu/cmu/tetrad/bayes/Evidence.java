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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.VariableSource;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores information for a variable source about evidence we have for each variable as
 * well as whether each variable has been manipulated.
 *
 * @author Joseph Ramsey
 */
public final class Evidence implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * Unused field. Keep to avoid breaking serialization.
     *
     * @serial
     * @deprecated
     */
    private BayesIm bayesIm;

    /**
     * A proposition stating what we know for each variable.
     *
     * @serial Cannot be null.
     */
    private Proposition proposition;

    /**
     * A manipulation indicating how the bayes Im should be manipulated before
     * updating.
     *
     * @serial
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
     */
    public Evidence(Proposition proposition) {
        if (proposition == null) {
            throw new NullPointerException();
        }

        this.proposition = new Proposition(proposition);
    }

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

    public static Evidence tautology(VariableSource variableSource) {
        return new Evidence(variableSource);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static Evidence serializableInstance() {
        return new Evidence(MlBayesIm.serializableInstance());
    }

    //===========================PUBLIC METHODS=========================//

    /**
     * @return the Bayes IM that this is evidence for.
     */
    public VariableSource getVariableSource() {
        return proposition.getVariableSource();
    }

    public int getNodeIndex(String nodeName) {
        return proposition.getNodeIndex(nodeName);
    }

    public int getCategoryIndex(String nodeName, String category) {
        return proposition.getCategoryIndex(nodeName, category);
    }

    public int getNumNodes() {
        return proposition.getVariableSource().getVariables().size();
    }

    public Node getNode(int nodeIndex) {
        return proposition.getVariableSource().getVariables().get(nodeIndex);
    }

    public DiscreteVariable getVariable(String nodeName) {
        int index = proposition.getVariableSource().getVariableNames().indexOf(nodeName);
        return (DiscreteVariable) proposition.getVariableSource().getVariables().get(index);
    }

    public int getNumCategories(int variable) {
        return proposition.getNumCategories(variable);
    }

    public Proposition getProposition() {
        return this.proposition;
    }

    public boolean isManipulated(int nodeIndex) {
        return manipulation.isManipulated(nodeIndex);
    }

    public void setManipulated(int nodeIndex, boolean manipulated) {
        manipulation.setManipulated(nodeIndex, manipulated);
    }

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

    public boolean hasNoEvidence(int variable) {
        for (int i = 0; i < proposition.getNumCategories(variable); i++) {
            if (!proposition.isAllowed(variable, i)) {
                return false;
            }
        }

        return true;
    }

    /**
     * @return the variable for which there is evidence.
     */
    public List<Node> getVariablesInEvidence() {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < getNumNodes(); i++) {
            if (proposition.getSingleCategory(i) != -1) {
                nodes.add(getNode(i));
            }
        }

        return nodes;
    }

    public String getCategory(Node node, int j) {
        DiscreteVariable variable = (DiscreteVariable) node;
        return variable.getCategory(j);
    }

    /**
     * Returna true just in case this evidence has a list of variables
     * equal to those of the given variable source.
     */
    public boolean isIncompatibleWith(VariableSource variableSource) {
        List<Node> variables1 = getVariableSource().getVariables();
        List<Node> variables2 = variableSource.getVariables();

        return !variables1.equals(variables2);
    }

    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof Evidence)) {
            return false;
        }

        Evidence evidence = (Evidence) o;

        return proposition.equals(evidence.proposition) && manipulation.equals(evidence.manipulation);

    }

    public int hashCode() {
        int hashCode = 37;
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






