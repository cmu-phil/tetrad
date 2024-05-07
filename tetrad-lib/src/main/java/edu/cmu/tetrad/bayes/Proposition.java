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
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.Arrays;
import java.util.List;

/**
 * Represents propositions over the variables of a particular BayesIm describing an event of a fairly general
 * sort--namely, conjunctions of propositions that particular variables take on values from a particular disjunctive
 * list of categories. For example, X1 = 1 or 2 and X2 = 3 and X3 = 1 or 3 and X4 = 2 or 3 or 5. The proposition is
 * created by allowing or disallowing particular categories. Notice that "knowing nothing" about a variable is the same
 * as saying that all categories for that variable are allowed, so the proposition by default allows all categories for
 * all variables--i.e. it is a tautology.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class Proposition implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The Bayes IM that this is a proposition for.
     */
    private final VariableSource variableSource;

    /**
     * An array indicating whether each category for each variable is allowed.
     */
    private final boolean[][] allowedCategories;

    //===========================CONSTRUCTORS===========================//

    /**
     * Creates a new Proposition which allows all values.
     */
    private Proposition(VariableSource variableSource) {
        if (variableSource == null) {
            throw new NullPointerException();
        }

        this.variableSource = variableSource;

        List<Node> variables = this.variableSource.getVariables();

        for (Node variable : variables) {
            if (!(variable instanceof DiscreteVariable)) {
                throw new IllegalArgumentException(
                        "Variables for Propositions " +
                        "must be DiscreteVariables.");
            }
        }

        this.allowedCategories = new boolean[variables.size()][];

        for (int i = 0; i < variables.size(); i++) {
            DiscreteVariable discreteVariable =
                    (DiscreteVariable) variables.get(i);
            int numCategories = discreteVariable.getNumCategories();
            this.allowedCategories[i] = new boolean[numCategories];
        }

        setToTautology();
    }

    /**
     * Copies the info out of the old proposition into a new proposition for the new BayesIm.
     *
     * @param variableSource a {@link edu.cmu.tetrad.data.VariableSource} object
     * @param proposition    a {@link edu.cmu.tetrad.bayes.Proposition} object
     */
    public Proposition(VariableSource variableSource, Proposition proposition) {
        this(variableSource);

        if (proposition == null) {
            throw new NullPointerException();
        }

        List<Node> variables = variableSource.getVariables();
        List<Node> oldVariables =
                proposition.getVariableSource().getVariables();

        for (int i = 0; i < variables.size(); i++) {
            DiscreteVariable variable = (DiscreteVariable) variables.get(i);
            int oldIndex = -1;

            for (int j = 0; j < oldVariables.size(); j++) {
                DiscreteVariable _variable =
                        (DiscreteVariable) oldVariables.get(j);
                if (variable.equals(_variable)) {
                    oldIndex = j;
                    break;
                }
            }

            if (oldIndex != -1) {
                for (int j = 0; j < this.allowedCategories[i].length; j++) {
                    this.allowedCategories[i][j] =
                            proposition.isAllowed(oldIndex, j);
                }
            }
        }
    }

    /**
     * Copies the info out of the old proposition into a new proposition for the new BayesIm.
     *
     * @param proposition a {@link edu.cmu.tetrad.bayes.Proposition} object
     */
    public Proposition(Proposition proposition) {
        this.variableSource = proposition.variableSource;
        this.allowedCategories =
                new boolean[proposition.allowedCategories.length][];

        for (int i = 0; i < this.allowedCategories.length; i++) {
            this.allowedCategories[i] =
                    new boolean[proposition.allowedCategories[i].length];

            System.arraycopy(proposition.allowedCategories[i], 0,
                    this.allowedCategories[i], 0, this.allowedCategories[i].length);
        }
    }

    /**
     * <p>tautology.</p>
     *
     * @param variableSource a {@link edu.cmu.tetrad.data.VariableSource} object
     * @return a {@link edu.cmu.tetrad.bayes.Proposition} object
     */
    public static Proposition tautology(VariableSource variableSource) {
        return new Proposition(variableSource);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.bayes.Proposition} object
     */
    public static Proposition serializableInstance() {
        return new Proposition(MlBayesIm.serializableInstance());
    }

    //===========================PUBLIC METHODS=========================//

    /**
     * <p>getNumAllowedCategories.</p>
     *
     * @param i a int
     * @return a int
     */
    public int getNumAllowedCategories(int i) {
        int numAllowed = 0;

        for (int j = 0; j < getNumCategories(i); j++) {
            if (isAllowed(i, j)) {
                numAllowed++;
            }
        }

        return numAllowed;
    }

    /**
     * <p>removeCategory.</p>
     *
     * @param variable a int
     * @param category a int
     */
    public void removeCategory(int variable, int category) {
        this.allowedCategories[variable][category] = false;
    }

    /**
     * Without changing the status of the specified category, disallows all other categories for the given variable.
     *
     * @param variable a int
     * @param category a int
     */
    public void disallowComplement(int variable, int category) {
        for (int i = 0; i < this.allowedCategories[variable].length; i++) {
            if (i != category) {
                this.allowedCategories[variable][i] = false;
            }
        }
    }

    /**
     * Sets the given value to true and all other values to false for the given variable.
     *
     * @param variable a int
     * @param category a int
     */
    public void setCategory(int variable, int category) {
        setVariable(variable, false);
        addCategory(variable, category);
    }

    /**
     * <p>isPermissibleCombination.</p>
     *
     * @param point an array of {@link int} objects
     * @return true iff the given point is true for this proposition.
     */
    public boolean isPermissibleCombination(int[] point) {
        for (int i = 0; i < this.allowedCategories.length; i++) {
            if (!this.allowedCategories[i][point[i]]) {
                return false;
            }
        }

        return true;
    }

    /**
     * <p>existsCombination.</p>
     *
     * @return true iff there is some combination of categories for all the variables of the proposition that is
     * allowed.
     */
    public boolean existsCombination() {
        loop:
        for (boolean[] allowedCategory : this.allowedCategories) {
            for (boolean anAllowedCategory : allowedCategory) {
                if (anAllowedCategory) {
                    continue loop;
                }
            }

            return false;
        }

        return true;
    }

    /**
     * <p>getNumAllowed.</p>
     *
     * @param variable a int
     * @return a int
     */
    public int getNumAllowed(int variable) {
        int sum = 0;

        for (int i = 0; i < getNumCategories(variable); i++) {
            if (isAllowed(variable, i)) {
                sum++;
            }
        }

        return sum;
    }

    /**
     * <p>getSingleCategory.</p>
     *
     * @param variable a int
     * @return the single category selected for the given variable, or -1 if it is not the case that a single value is
     * selected.
     */
    public int getSingleCategory(int variable) {
        int count = 0;
        int lastEncountered = -1;

        for (int i = 0; i < this.allowedCategories[variable].length; i++) {
            if (this.allowedCategories[variable][i]) {
                lastEncountered = i;
                count++;
            }
        }

        if (count != 1) {
            return -1;
        } else {
            return lastEncountered;
        }
    }

    /**
     * Restricts this proposition to the categories for each variable that are true in the given proposition.
     *
     * @param proposition a {@link edu.cmu.tetrad.bayes.Proposition} object
     */
    public void restrictToProposition(Proposition proposition) {
        if (proposition.getVariableSource() != this.variableSource) {
            throw new IllegalArgumentException("Can only restrict to " +
                                               "propositions for the same variable source.");
        }

        for (int i = 0; i < this.allowedCategories.length; i++) {
            for (int j = 0; j < this.allowedCategories[i].length; j++) {
                if (!proposition.allowedCategories[i][j]) {
                    this.allowedCategories[i][j] = false;
                }
            }
        }
    }

    /**
     * <p>getNodeIndex.</p>
     *
     * @param name a {@link java.lang.String} object
     * @return the index of the variable with the given name, or -1 if such a variable does not exist.
     */
    public int getNodeIndex(String name) {
        List<Node> variables = getVariableSource().getVariables();

        for (int i = 0; i < variables.size(); i++) {
            Node variable = variables.get(i);
            if (variable.getName().equals(name)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * <p>getCategoryIndex.</p>
     *
     * @param nodeName a {@link java.lang.String} object
     * @param category a {@link java.lang.String} object
     * @return a int
     */
    public int getCategoryIndex(String nodeName, String category) {
        int index = getVariableSource().getVariableNames().indexOf(nodeName);
        DiscreteVariable variable = (DiscreteVariable) getVariableSource().getVariables().get(index);
        return variable.getCategories().indexOf(category);
    }

    /**
     * <p>addCategory.</p>
     *
     * @param variable a int
     * @param category a int
     */
    public void addCategory(int variable, int category) {
        this.allowedCategories[variable][category] = true;
    }

    /**
     * <p>Getter for the field <code>variableSource</code>.</p>
     *
     * @return the Bayes IM that this is a proposition for.
     */
    public VariableSource getVariableSource() {
        return this.variableSource;
    }

    /**
     * <p>getNumVariables.</p>
     *
     * @return the number of variables for the proposition.
     */
    public int getNumVariables() {
        return this.allowedCategories.length;
    }

    /**
     * <p>getNumCategories.</p>
     *
     * @param variable a int
     * @return the number of categories for the given variable.
     */
    public int getNumCategories(int variable) {
        return this.allowedCategories[variable].length;
    }

    /**
     * Specifies that all categories for the given variable are either all allowed (true) or all disallowed (false).
     *
     * @param variable a int
     * @param allowed  a boolean
     */
    public void setVariable(int variable, boolean allowed) {
        Arrays.fill(this.allowedCategories[variable], allowed);
    }

    /**
     * Specifies that all variables in the proposition are either completely allowed (true) or completely disallowed
     * (false) for all of their categories.
     */
    public void setToTautology() {
        for (int i = 0; i < this.allowedCategories.length; i++) {
            setVariable(i, true);
        }
    }

    /**
     * <p>isAllowed.</p>
     *
     * @param variable a int
     * @param category a int
     * @return true iff the given category for the given variable is allowed.
     */
    public boolean isAllowed(int variable, int category) {
        return this.allowedCategories[variable][category];
    }

    /**
     * <p>isConditioned.</p>
     *
     * @param variable a int
     * @return true iff some category for the given variable is disallowed.
     */
    public boolean isConditioned(int variable) {
        for (int j = 0; j < getNumCategories(variable); j++) {
            if (!isAllowed(variable, j)) {
                return true;
            }
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof Proposition proposition)) {
            return false;
        }

        if (!(this.variableSource == proposition.variableSource)) {
            return false;
        }

        for (int i = 0; i < this.allowedCategories.length; i++) {
            for (int j = 0; j < this.allowedCategories[i].length; j++) {
                if (this.allowedCategories[i][j] !=
                    proposition.allowedCategories[i][j]) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * <p>hashCode.</p>
     *
     * @return a int
     */
    public int hashCode() {
        int hashCode = 37;
        hashCode = 19 * hashCode + this.variableSource.hashCode();
        hashCode = 19 * hashCode + Arrays.deepHashCode(this.allowedCategories);
        return hashCode;
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        List<Node> variables = getVariableSource().getVariables();

        buf.append("\n");

        for (int i = 0; i < getNumVariables(); i++) {
            DiscreteVariable variable = (DiscreteVariable) variables.get(i);
            String name = variable.getName();
            buf.append(name);

            for (int j = name.length(); j < 5; j++) {
                buf.append(" ");
            }

            buf.append("\t");
        }

        for (int i = 0; i < getMaxNumCategories(); i++) {
            buf.append("\n");

            for (int j = 0; j < getNumVariables(); j++) {
                if (i < getNumCategories(j)) {
                    boolean allowed = isAllowed(j, i);
                    buf.append(allowed ? "true" : "*   ").append("\t");
                } else {
                    buf.append("    \t");
                }
            }
        }

        return buf.toString();
    }

    //=============================PRIVATE METHODS=======================//

    private int getMaxNumCategories() {
        int max = 0;

        for (int i = 0; i < getNumVariables(); i++) {
            if (getNumCategories(i) > max) {
                max = getNumCategories(i);
            }
        }

        return max;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s a {@link java.io.ObjectInputStream} object
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.variableSource == null) {
            throw new NullPointerException();
        }

        if (this.allowedCategories == null) {
            throw new NullPointerException();
        }
    }
}





