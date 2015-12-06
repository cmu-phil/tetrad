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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Calculates cell probabilities from conditional BayesIm probabilities on the
 * fly without constructing the entire table. (To force the entire table to be
 * constructed, use StoredCellProbs.)
 *
 * @author Joseph Ramsey
 */
public final class BayesImProbs implements DiscreteProbs, TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * @serial Cannot be null.
     */
    private BayesIm bayesIm;

    /**
     * @serial Cannot be null.
     */
    private List<Node> variables;

    //===========================CONSTRUCTORS==========================//

    public BayesImProbs(BayesIm bayesIm) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        this.bayesIm = bayesIm;
        List<Node> variables = new LinkedList<>();
        BayesPm bayesPm = bayesIm.getBayesPm();

        for (int i = 0; i < bayesIm.getNumNodes(); i++) {
            Node node = bayesIm.getNode(i);
            String name = node.getName();

            int numCategories = bayesPm.getNumCategories(node);
            List<String> categories = new LinkedList<>();

            for (int j = 0; j < numCategories; j++) {
                categories.add(bayesPm.getCategory(node, j));
            }

            variables.add(new DiscreteVariable(name, categories));
        }

        this.variables = Collections.unmodifiableList(variables);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static BayesImProbs serializableInstance() {
        return new BayesImProbs(MlBayesIm.serializableInstance());
    }

    //==========================PUBLIC METHODS==========================//

    /**
     * Calculates the probability in the given cell from the conditional
     * probabilities in the BayesIm. It's the product of the probabilities
     * that each variable takes on the value it does given that the other
     * variables take on the values they do in that cell. The returned value
     * will be undefined (Double.NaN) if any of the conditional probabilities
     * being multiplied together is undefined.
     *
     * @return the cell probability, or NaN if this probability is undefined.
     */
    public double getCellProb(int[] variableValues) {
        double p = 1.0;

        VALUES:
        for (int node = 0; node < variableValues.length; node++) {

            // If a value is missing, count its probability as 1.0.
            if (Double.isNaN(variableValues[node])) {
                continue;
            }

            int[] parents = bayesIm.getParents(node);
            int[] parentValues = new int[parents.length];
            for (int parentIndex = 0;
                 parentIndex < parentValues.length; parentIndex++) {
                parentValues[parentIndex] =
                        variableValues[parents[parentIndex]];

                if (parentValues[parentIndex] == DiscreteVariable.MISSING_VALUE) {
                    continue VALUES;
                }
            }

            int rowIndex = bayesIm.getRowIndex(node, parentValues);
            int colIndex = variableValues[node];

            if (colIndex == DiscreteVariable.MISSING_VALUE) {
                continue;
            }

            p *= bayesIm.getProbability(node, rowIndex, colIndex);
        }

        return p;
    }

    public double getProb(Proposition assertion) {

        // Initialize to 0's.
        int[] variableValues = new int[assertion.getNumVariables()];

        for (int i = 0; i < assertion.getNumVariables(); i++) {
            variableValues[i] = nextValue(assertion, i, -1);
        }

        variableValues[variableValues.length - 1] = -1;
        double p = 0.0;

        loop:
        while (true) {
            for (int i = assertion.getNumVariables() - 1; i >= 0; i--) {
                if (hasNextValue(assertion, i, variableValues[i])) {
                    variableValues[i] =
                            nextValue(assertion, i, variableValues[i]);

                    for (int j = i + 1; j < assertion.getNumVariables(); j++) {
                        if (hasNextValue(assertion, j, -1)) {
                            variableValues[j] = nextValue(assertion, j, -1);
                        } else {
                            break loop;
                        }
                    }

                    double cellProb = getCellProb(variableValues);

                    if (Double.isNaN(cellProb)) {
                        continue;
                    }

                    p += cellProb;
                    continue loop;
                }
            }

            break;
        }

        return p;
    }

    public double getConditionalProb(Proposition assertion,
                                     Proposition condition) {
        if (assertion.getVariableSource() != condition.getVariableSource()) {
            throw new IllegalArgumentException(
                    "Assertion and condition must be " +
                            "for the same Bayes IM.");
        }

        int[] variableValues = new int[condition.getNumVariables()];

        for (int i = 0; i < condition.getNumVariables(); i++) {
            variableValues[i] = nextValue(condition, i, -1);
        }

        variableValues[variableValues.length - 1] = -1;
        double conditionTrue = 0.0;
        double assertionTrue = 0.0;

        loop:
        while (true) {
            for (int i = condition.getNumVariables() - 1; i >= 0; i--) {
                if (hasNextValue(condition, i, variableValues[i])) {
                    variableValues[i] =
                            nextValue(condition, i, variableValues[i]);

                    for (int j = i + 1; j < condition.getNumVariables(); j++) {
                        if (hasNextValue(condition, j, -1)) {
                            variableValues[j] = nextValue(condition, j, -1);
                        } else {
                            break loop;
                        }
                    }

                    double cellProb = getCellProb(variableValues);

                    if (Double.isNaN(cellProb)) {
                        continue;
                    }

                    boolean assertionHolds = true;

                    for (int j = 0; j < assertion.getNumVariables(); j++) {
                        if (!assertion.isAllowed(j, variableValues[j])) {
                            assertionHolds = false;
                            break;
                        }
                    }

                    if (assertionHolds) {
                        assertionTrue += cellProb;
                    }

                    conditionTrue += cellProb;
                    continue loop;
                }
            }

            break;
        }

        return assertionTrue / conditionTrue;
    }

    public boolean isMissingValueCaseFound() {
        return false;
    }

    public List<Node> getVariables() {
        return this.variables;
    }

    private static boolean hasNextValue(Proposition proposition, int variable,
                                        int currentIndex) {
        return nextValue(proposition, variable, currentIndex) != -1;
    }

    private static int nextValue(Proposition proposition, int variable,
                                 int currentIndex) {
        for (int i = currentIndex + 1;
             i < proposition.getNumCategories(variable); i++) {
            if (proposition.isAllowed(variable, i)) {
                return i;
            }
        }

        return -1;
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

        if (bayesIm == null) {
            throw new NullPointerException();
        }

        if (variables == null) {
            throw new NullPointerException();
        }
    }
}





