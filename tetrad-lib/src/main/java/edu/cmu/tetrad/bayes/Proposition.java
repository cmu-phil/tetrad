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
import java.util.Arrays;
import java.util.List;

/**
 * Represents propositions over the variables of a particular BayesIm describing
 * an event of a fairly general sort--namely, conjunctions of propositions that
 * particular variables take on values from a particular disjunctive list of
 * categories. For example, X1 = 1 or 2 & X2 = 3 & X3 = 1 or 3 & X4 = 2 or 3 or
 * 5. The proposition is created by allowing or disallowing particular
 * categories. Notice that "knowing nothing" about a variable is the same as
 * saying that all categories for that variable are allowed, so the proposition
 * by default allows all categories for all variables--i.e. it is a tautology.
 *
 * @author Joseph Ramsey
 */
public final class Proposition implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * @serial Cannot be null.
     */
    private final VariableSource variableSource;

    /**
     * @serial Cannot be null.
     */
    private final boolean[][] allowedCategories;

    //===========================CONSTRUCTORS===========================//

    /**
     * Creates a new Proposition which allows all values.
     */
    private Proposition(final VariableSource variableSource) {
        if (variableSource == null) {
            throw new NullPointerException();
        }

        this.variableSource = variableSource;

        final List<Node> variables = this.variableSource.getVariables();

        for (final Node variable : variables) {
            if (!(variable instanceof DiscreteVariable)) {
                throw new IllegalArgumentException(
                        "Variables for Propositions " +
                                "must be DiscreteVariables.");
            }
        }

        this.allowedCategories = new boolean[variables.size()][];

        for (int i = 0; i < variables.size(); i++) {
            final DiscreteVariable discreteVariable =
                    (DiscreteVariable) variables.get(i);
            final int numCategories = discreteVariable.getNumCategories();
            this.allowedCategories[i] = new boolean[numCategories];
        }

        setToTautology();
    }

    public static Proposition tautology(final VariableSource variableSource) {
        return new Proposition(variableSource);
    }

    /**
     * Copies the info out of the old proposition into a new proposition for the
     * new BayesIm.
     */
    public Proposition(final VariableSource variableSource, final Proposition proposition) {
        this(variableSource);

        if (proposition == null) {
            throw new NullPointerException();
        }

        final List<Node> variables = variableSource.getVariables();
        final List<Node> oldVariables =
                proposition.getVariableSource().getVariables();

        for (int i = 0; i < variables.size(); i++) {
            final DiscreteVariable variable = (DiscreteVariable) variables.get(i);
            int oldIndex = -1;

            for (int j = 0; j < oldVariables.size(); j++) {
                final DiscreteVariable _variable =
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
     * Copies the info out of the old proposition into a new proposition for the
     * new BayesIm.
     */
    public Proposition(final Proposition proposition) {
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
     * Generates a simple exemplar of this class to test serialization.
     */
    public static Proposition serializableInstance() {
        return new Proposition(MlBayesIm.serializableInstance());
    }

    //===========================PUBLIC METHODS=========================//

    public int getNumAllowedCategories(final int i) {
        int numAllowed = 0;

        for (int j = 0; j < getNumCategories(i); j++) {
            if (isAllowed(i, j)) {
                numAllowed++;
            }
        }

        return numAllowed;
    }

    public void removeCategory(final int variable, final int category) {
        this.allowedCategories[variable][category] = false;
    }

    /**
     * Without changing the status of the specified category, disallows all
     * other categories for the given variable.
     */
    public void disallowComplement(final int variable, final int category) {
        for (int i = 0; i < this.allowedCategories[variable].length; i++) {
            if (i != category) {
                this.allowedCategories[variable][i] = false;
            }
        }
    }

    /**
     * Sets the given value to true and all other values to false for the
     * given variable.
     */
    public void setCategory(final int variable, final int category) {
        setVariable(variable, false);
        addCategory(variable, category);
    }

    /**
     * @return true iff the given point is true for this proposition.
     */
    public boolean isPermissibleCombination(final int[] point) {
        for (int i = 0; i < this.allowedCategories.length; i++) {
            if (!this.allowedCategories[i][point[i]]) {
                return false;
            }
        }

        return true;
    }

    /**
     * @return true iff there is some combination of categories for all the
     * variables of the proposition that is allowed.
     */
    public boolean existsCombination() {
        loop:
        for (final boolean[] allowedCategory : this.allowedCategories) {
            for (final boolean anAllowedCategory : allowedCategory) {
                if (anAllowedCategory) {
                    continue loop;
                }
            }

            return false;
        }

        return true;
    }

    public int getNumAllowed(final int variable) {
        int sum = 0;

        for (int i = 0; i < getNumCategories(variable); i++) {
            if (isAllowed(variable, i)) {
                sum++;
            }
        }

        return sum;
    }

    /**
     * @return the single category selected for the given variable, or -1 if it
     * is not the case that a single value is selected.
     */
    public int getSingleCategory(final int variable) {
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
     * Restricts this proposition to the categories for each variable that are
     * true in the given proposition.
     */
    public void restrictToProposition(final Proposition proposition) {
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
     * @return the index of the variable with the given name, or -1 if such a
     * variable does not exist.
     */
    public int getNodeIndex(final String name) {
        final List<Node> variables = getVariableSource().getVariables();

        for (int i = 0; i < variables.size(); i++) {
            final Node variable = variables.get(i);
            if (variable.getName().equals(name)) {
                return i;
            }
        }

        return -1;
    }

    public int getCategoryIndex(final String nodeName, final String category) {
        final int index = getVariableSource().getVariableNames().indexOf(nodeName);
        final DiscreteVariable variable = (DiscreteVariable) getVariableSource().getVariables().get(index);
        return variable.getCategories().indexOf(category);
    }

    public void addCategory(final int variable, final int category) {
        this.allowedCategories[variable][category] = true;
    }

    /**
     * @return the Bayes IM that this is a proposition for.
     */
    public VariableSource getVariableSource() {
        return this.variableSource;
    }

    /**
     * @return the number of variables for the proposition.
     */
    public int getNumVariables() {
        return this.allowedCategories.length;
    }

    /**
     * @return the number of categories for the given variable.
     */
    public int getNumCategories(final int variable) {
        return this.allowedCategories[variable].length;
    }

    /**
     * Specifies that all categories for the given variable are either all
     * allowed (true) or all disallowed (false).
     */
    public void setVariable(final int variable, final boolean allowed) {
        Arrays.fill(this.allowedCategories[variable], allowed);
    }

    /**
     * Specifies that all variables in the proposition are either completely
     * allowed (true) or completely disallowed (false) for all of their
     * categories.
     */
    public void setToTautology() {
        for (int i = 0; i < this.allowedCategories.length; i++) {
            setVariable(i, true);
        }
    }

    /**
     * @return true iff the given category for the given variable is allowed.
     */
    public boolean isAllowed(final int variable, final int category) {
        return this.allowedCategories[variable][category];
    }

    /**
     * @return true iff some category for the given variable is disallowed.
     */
    public boolean isConditioned(final int variable) {
        for (int j = 0; j < getNumCategories(variable); j++) {
            if (!isAllowed(variable, j)) {
                return true;
            }
        }

        return false;
    }

    public boolean equals(final Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof Proposition)) {
            return false;
        }

        final Proposition proposition = (Proposition) o;

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

    public int hashCode() {
        int hashCode = 37;
        hashCode = 19 * hashCode + this.variableSource.hashCode();
        hashCode = 19 * hashCode + Arrays.deepHashCode(this.allowedCategories);
        return hashCode;
    }

    public String toString() {
        final StringBuilder buf = new StringBuilder();
        final List<Node> variables = getVariableSource().getVariables();

        buf.append("\n");

        for (int i = 0; i < getNumVariables(); i++) {
            final DiscreteVariable variable = (DiscreteVariable) variables.get(i);
            final String name = variable.getName();
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
                    final boolean allowed = isAllowed(j, i);
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
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     */
    private void readObject(final ObjectInputStream s)
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





