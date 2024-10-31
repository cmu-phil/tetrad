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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Calculates cell probabilities from conditional BayesIm probabilities on the fly without constructing the entire
 * table. (To force the entire table to be constructed, use StoredCellProbs.)
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class BayesImProbs implements DiscreteProbs, TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Represents a variable of the BayesIm class.
     */
    private final BayesIm bayesIm;

    /**
     * Represents a list of nodes.
     */
    private final List<Node> variables;

    //===========================CONSTRUCTORS==========================//

    /**
     * Constructs a BayesImProbs object from the given BayesIm.
     *
     * @param bayesIm Ibid.
     */
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
     *
     * @return a simple exemplar of this class to test serialization.
     */
    public static BayesImProbs serializableInstance() {
        return new BayesImProbs(MlBayesIm.serializableInstance());
    }

    //==========================PUBLIC METHODS==========================//

    private static boolean hasNextValue(Proposition proposition, int variable,
                                        int currentIndex) {
        return BayesImProbs.nextValue(proposition, variable, currentIndex) != -1;
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
     * Calculates the probability in the given cell from the conditional probabilities in the BayesIm. It's the product
     * of the probabilities that each variable takes on the value it does given that the other variables take on the
     * values they do in that cell. The returned value will be undefined (Double.NaN) if any of the conditional
     * probabilities being multiplied together is undefined.
     *
     * @param variableValues the values of the variables in the cell
     * @return the cell probability, or NaN if this probability is undefined.
     */
    public double getCellProb(int[] variableValues) {
        double p = 1.0;

        VALUES:
        for (int node = 0; node < variableValues.length; node++) {
            int[] parents = this.bayesIm.getParents(node);
            int[] parentValues = new int[parents.length];
            for (int parentIndex = 0;
                 parentIndex < parentValues.length; parentIndex++) {
                parentValues[parentIndex] =
                        variableValues[parents[parentIndex]];

                if (parentValues[parentIndex] == DiscreteVariable.MISSING_VALUE) {
                    continue VALUES;
                }
            }

            int rowIndex = this.bayesIm.getRowIndex(node, parentValues);
            int colIndex = variableValues[node];

            if (colIndex == DiscreteVariable.MISSING_VALUE) {
                continue;
            }

            p *= this.bayesIm.getProbability(node, rowIndex, colIndex);
        }

        return p;
    }

    /**
     * Calculates the probability of a given proposition.
     *
     * @param assertion the proposition for which we want to calculate the probability
     * @return the probability of the given proposition
     */
    public double getProb(Proposition assertion) {

        // Initialize to 0's.
        int[] variableValues = new int[assertion.getNumVariables()];

        for (int i = 0; i < assertion.getNumVariables(); i++) {
            variableValues[i] = BayesImProbs.nextValue(assertion, i, -1);
        }

        variableValues[variableValues.length - 1] = -1;
        double p = 0.0;

        loop:
        while (true) {
            for (int i = assertion.getNumVariables() - 1; i >= 0; i--) {
                if (BayesImProbs.hasNextValue(assertion, i, variableValues[i])) {
                    variableValues[i] =
                            BayesImProbs.nextValue(assertion, i, variableValues[i]);

                    for (int j = i + 1; j < assertion.getNumVariables(); j++) {
                        if (BayesImProbs.hasNextValue(assertion, j, -1)) {
                            variableValues[j] = BayesImProbs.nextValue(assertion, j, -1);
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

    /**
     * Calculates the conditional probability of an assertion given a condition.
     *
     * @param assertion the proposition representing the assertion
     * @param condition the proposition representing the condition
     * @return the conditional probability of the assertion given the condition
     * @throws IllegalArgumentException if the assertion and condition are not for the same Bayes IM
     */
    public double getConditionalProb(Proposition assertion,
                                     Proposition condition) {
        if (assertion.getVariableSource() != condition.getVariableSource()) {
            throw new IllegalArgumentException(
                    "Assertion and condition must be " +
                    "for the same Bayes IM.");
        }

        int[] variableValues = new int[condition.getNumVariables()];

        for (int i = 0; i < condition.getNumVariables(); i++) {
            variableValues[i] = BayesImProbs.nextValue(condition, i, -1);
        }

        variableValues[variableValues.length - 1] = -1;
        double conditionTrue = 0.0;
        double assertionTrue = 0.0;

        loop:
        while (true) {
            for (int i = condition.getNumVariables() - 1; i >= 0; i--) {
                if (BayesImProbs.hasNextValue(condition, i, variableValues[i])) {
                    variableValues[i] =
                            BayesImProbs.nextValue(condition, i, variableValues[i]);

                    for (int j = i + 1; j < condition.getNumVariables(); j++) {
                        if (BayesImProbs.hasNextValue(condition, j, -1)) {
                            variableValues[j] = BayesImProbs.nextValue(condition, j, -1);
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

    /**
     * <p>Getter for the field <code>variables</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getVariables() {
        return this.variables;
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
}





