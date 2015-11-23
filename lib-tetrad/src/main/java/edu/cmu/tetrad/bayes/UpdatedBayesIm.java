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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents a Bayes IM in which all of the conditional probability tables have
 * been updated to take into account evidence. Updated conditional probability
 * values of variables with respect to comabinations of their parent values are
 * calculated on the fly. Values that do not change due to being d-separated
 * from a variable conditional on its parents are not calculated; rather, these
 * are simply looked up in the underlying Bayes IM and returned.
 *
 * @author Joseph Ramsey
 */
public final class UpdatedBayesIm implements BayesIm, TetradSerializable {
    static final long serialVersionUID = 23L;
    private static final double ALLOWABLE_DIFFERENCE = 1.0e-10;

    /**
     * The wrapped BayesIm. Unmodified conditional probability values will be
     * retrieved from here.
     *
     * @serial Cannot be null; must be evidence.getEstIm().
     */
    private BayesIm bayesIm;

    /**
     * The evidence updated on.
     *
     * @serial Cannot be null.
     */
    private Evidence evidence;

    /**
     * Stores probs that change with respect to the underlying bayesIm,
     * calculated on the fly.
     *
     * @serial Cannot be null.
     */
    private double[][][] changedProbs;

    /**
     * A boolean array that is true at a position if the node at that index is
     * an ancestor or a child of one of the evidence variables.
     *
     * @serial Cannot be null.
     */
    private boolean[] affectedVars;

    //===========================CONSTRUCTORS===========================//

    /**
     * Constructs Bayes net in which CPT's updated for the given evidence are
     * calculated on the fly.
     */
    public UpdatedBayesIm(BayesIm bayesIm) {

        this(bayesIm, Evidence.tautology(bayesIm));
    }

    /**
     * Constructs Bayes net in which CPT's updated for the given evidence are
     * calculated on the fly.
     */
    public UpdatedBayesIm(BayesIm bayesIm, Evidence evidence) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        if (evidence == null) {
            throw new NullPointerException();
        }

        if (!evidence.isCompatibleWith(bayesIm)) {
            throw new IllegalArgumentException(
                    "Variables for this evidence must be compatible with those " +
                            "of the model Bayes IM");
        }

        this.bayesIm = bayesIm;
        this.evidence = new Evidence(evidence, bayesIm);
        this.changedProbs = new double[bayesIm.getNumNodes()][][];
        this.affectedVars = ancestorsOfEvidence(evidence);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static UpdatedBayesIm serializableInstance() {
        return new UpdatedBayesIm(MlBayesIm.serializableInstance());
    }

    //===============================PUBLIC METHODS=======================//

    public BayesPm getBayesPm() {
        return getBayesIm().getBayesPm();
    }


    private BayesIm getBayesIm() {
        return bayesIm;
    }

    public Dag getDag() {
        return getBayesIm().getDag();
    }

    public int getNumNodes() {
        return getBayesIm().getNumNodes();
    }

    public Node getNode(int nodeIndex) {
        return getBayesIm().getNode(nodeIndex);
    }

    public Node getNode(String name) {
        return getBayesIm().getNode(name);
    }

    public int getNodeIndex(Node node) {
        return getBayesIm().getNodeIndex(node);
    }

    public int getNumColumns(int nodeIndex) {
        return getBayesIm().getNumColumns(nodeIndex);
    }

    public int getNumRows(int nodeIndex) {
        return getBayesIm().getNumRows(nodeIndex);
    }

    public int getNumParents(int nodeIndex) {
        return getBayesIm().getNumParents(nodeIndex);
    }

    public int getParent(int nodeIndex, int parentIndex) {
        return getBayesIm().getParent(nodeIndex, parentIndex);
    }

    public int getParentDim(int nodeIndex, int parentIndex) {
        return getBayesIm().getParentDim(nodeIndex, parentIndex);
    }

    public int[] getParentDims(int nodeIndex) {
        return getBayesIm().getParentDims(nodeIndex);
    }

    public int[] getParents(int nodeIndex) {
        return getBayesIm().getParents(nodeIndex);
    }

    public int[] getParentValues(int nodeIndex, int rowIndex) {
        return getBayesIm().getParentValues(nodeIndex, rowIndex);
    }

    public int getParentValue(int nodeIndex, int rowIndex, int colIndex) {
        return getBayesIm().getParentValue(nodeIndex, rowIndex, colIndex);
    }

    /**
     * Calculates the probability for the given node, at the given row and
     * column of the conditional probability table (CPT), updated for the
     * evidence provided in the constuctor.
     */
    public double getProbability(int nodeIndex, int rowIndex, int colIndex) {
        if (!affectedVars[nodeIndex]) {
            return getBayesIm().getProbability(nodeIndex, rowIndex, colIndex);
        }

        if (changedProbs[nodeIndex] == null) {
            int numRows = getNumRows(nodeIndex);
            int numCols = getNumColumns(nodeIndex);
            double[][] table = new double[numRows][numCols];

            for (double[] aTable : table) {
                Arrays.fill(aTable, -99.0);
            }

            changedProbs[nodeIndex] = table;
        }

        if (changedProbs[nodeIndex][rowIndex][colIndex] == -99.0) {
            changedProbs[nodeIndex][rowIndex][colIndex] =
                    calcUpdatedProb(nodeIndex, rowIndex, colIndex);
        }

        return changedProbs[nodeIndex][rowIndex][colIndex];
    }

    public int getRowIndex(int nodeIndex, int[] values) {
        return getBayesIm().getRowIndex(nodeIndex, values);
    }

    public void normalizeAll() {
        getBayesIm().normalizeAll();
    }

    public void normalizeNode(int nodeIndex) {
        getBayesIm().normalizeNode(nodeIndex);
    }

    public void normalizeRow(int nodeIndex, int rowIndex) {
        getBayesIm().normalizeRow(nodeIndex, rowIndex);
    }

    public void setProbability(int nodeIndex, int rowIndex, int colIndex,
            double value) {
        getBayesIm().setProbability(nodeIndex, rowIndex, colIndex, value);
    }

    public int getCorrespondingNodeIndex(int nodeIndex, BayesIm otherBayesIm) {
        return getBayesIm().getCorrespondingNodeIndex(nodeIndex, otherBayesIm);
    }

    public void clearRow(int nodeIndex, int rowIndex) {
        getBayesIm().clearRow(nodeIndex, rowIndex);
    }

    public void randomizeRow(int nodeIndex, int rowIndex) {
        getBayesIm().randomizeRow(nodeIndex, rowIndex);
    }

    public void randomizeIncompleteRows(int nodeIndex) {
        getBayesIm().randomizeIncompleteRows(nodeIndex);
    }

    public void randomizeTable(int nodeIndex) {
        getBayesIm().randomizeTable(nodeIndex);
    }

    public void clearTable(int nodeIndex) {
        getBayesIm().clearTable(nodeIndex);
    }

    public boolean isIncomplete(int nodeIndex, int rowIndex) {
        return getBayesIm().isIncomplete(nodeIndex, rowIndex);
    }

    public boolean isIncomplete(int nodeIndex) {
        return getBayesIm().isIncomplete(nodeIndex);
    }

    public DataSet simulateData(int sampleSize, boolean latentDataSaved) {
        return getBayesIm().simulateData(sampleSize, latentDataSaved);
    }

    public DataSet simulateData(int sampleSize, long seed, boolean latentDataSaved) {
        return getBayesIm().simulateData(sampleSize, seed, latentDataSaved);
    }

    public DataSet simulateData(DataSet dataSet, boolean latentDataSaved) {
        throw new UnsupportedOperationException();
    }

    public List<Node> getVariables() {
        return getBayesIm().getVariables();
    }

    public List<String> getVariableNames() {
        return getBayesIm().getVariableNames();
    }

    public List<Node> getMeasuredNodes() {
        throw new UnsupportedOperationException();
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof BayesIm)) {
            return false;
        }

        BayesIm otherIm = (BayesIm) o;

        if (getNumNodes() != otherIm.getNumNodes()) {
            return false;
        }

        for (int i = 0; i < getNumNodes(); i++) {
            int otherIndex = otherIm.getCorrespondingNodeIndex(i, otherIm);

            if (otherIndex == -1) {
                return false;
            }

            if (getNumColumns(i) != otherIm.getNumColumns(otherIndex)) {
                return false;
            }

            if (getNumRows(i) != otherIm.getNumRows(otherIndex)) {
                return false;
            }

            for (int j = 0; j < getNumRows(i); j++) {
                for (int k = 0; k < getNumColumns(i); k++) {
                    double prob = getProbability(i, j, k);
                    double otherProb = otherIm.getProbability(i, j, k);

                    if (Double.isNaN(prob) && Double.isNaN(otherProb)) {
                        continue;
                    }

                    if (Math.abs(prob - otherProb) > ALLOWABLE_DIFFERENCE) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Prints out the probability table for each variable.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        buf.append("\nBayesIm:");

        for (int i = 0; i < getNumNodes(); i++) {
            buf.append("\n\nNode #").append(i);

            buf.append("\n");
            for (int k = 0; k < getNumParents(i); k++) {
                buf.append("#").append(getParent(i, k)).append("\t");
            }

            for (int j = 0; j < getNumRows(i); j++) {
                buf.append("\n");
                for (int k = 0; k < getNumParents(i); k++) {
                    buf.append(getParentValue(i, j, k)).append("\t");
                }

                buf.append(":\t");

                for (int k = 0; k < getNumColumns(i); k++) {
                    buf.append(nf.format(getProbability(i, j, k))).append("\t");
                }
            }
        }

        return buf.toString();
    }

    public Evidence getEvidence() {
        return new Evidence(evidence, this);
    }

    /**
     * Calculates the set of variables whose CPT's change in response to the
     * evidence provided.
     */
    private boolean[] ancestorsOfEvidence(Evidence evidence) {
        List<Node> variablesInEvidence = evidence.getVariablesInEvidence();
        List<Node> nodesInEvidence = new LinkedList<Node>();

        for (Node _node : variablesInEvidence) {
            String nodeName = _node.getName();
            nodesInEvidence.add(bayesIm.getBayesPm().getNode(nodeName));
        }

        List<Node> nodesInGraph = getBayesIm().getDag().getNodes();
        boolean[] ancestorsOfEvidence = new boolean[getBayesIm().getNumNodes()];

        for (int i = 0; i < nodesInGraph.size(); i++) {
            for (Node node2 : nodesInEvidence) {
                Node node1 = nodesInGraph.get(i);

                if (getBayesIm().getDag().isAncestorOf(node1, node2)
                        || getBayesIm().getDag().isChildOf(node1, node2)) {
                    ancestorsOfEvidence[i] = true;
                }
            }
        }

        return ancestorsOfEvidence;
    }

    private double calcUpdatedProb(int nodeIndex, int rowIndex, int colIndex) {
        if (!affectedVars[nodeIndex]) {
            throw new IllegalStateException("Should not be calculating a " +
                    "probability for a table that's not an ancestor of " +
                    "evidence.");
        }

        Proposition assertion = Proposition.tautology(getBayesIm());
        Proposition condition = new Proposition(evidence.getProposition());

        boolean[] relevantVars = calcRelevantVars(nodeIndex);
        assertion.setCategory(nodeIndex, colIndex);
        int[] parents = getBayesIm().getParents(nodeIndex);
        int[] parentValues = getBayesIm().getParentValues(nodeIndex, rowIndex);

        for (int k = 0; k < parents.length; k++) {
            condition.disallowComplement(parents[k], parentValues[k]);
        }

        if (condition.existsCombination()) {
            double conditionalProb = getConditionalProb(assertion, condition, relevantVars);
            return conditionalProb;
        }
        else {
            return Double.NaN;
        }
    }

    private double getConditionalProb(Proposition assertion,
            Proposition condition, boolean[] relevantVars) {
        if (assertion.getVariableSource() != condition.getVariableSource()) {
            throw new IllegalArgumentException(
                    "Assertion and condition must be " +
                            "for the same Bayes IM.");
        }

        for (int i = 0; i < relevantVars.length; i++) {
            if (!relevantVars[i]) {
                condition.setCategory(i, 0);
            }
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
                        if (!hasNextValue(condition, j, -1)) {
                            break loop;
                        }

                        variableValues[j] = nextValue(condition, j, -1);
                    }

                    double cellProb = getCellProb(variableValues);

                    if (!Double.isNaN(cellProb)) {
                        conditionTrue += cellProb;

                        if (assertion.isPermissibleCombination(variableValues))
                        {
                            assertionTrue += cellProb;
                        }
                    }

                    continue loop;
                }
            }

            break;
        }

        return assertionTrue / conditionTrue;
    }

    private static boolean hasNextValue(Proposition proposition, int variable,
            int curIndex) {
        return nextValue(proposition, variable, curIndex) != -1;
    }

    private static int nextValue(Proposition proposition, int variable,
            int curIndex) {
        for (int i = curIndex + 1;
                i < proposition.getNumCategories(variable); i++) {
            if (proposition.isAllowed(variable, i)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Calculates the probability in the given cell from the conditional
     * probabilities in the BayesIm. It's the product of the probabilities
     * that each variable takes on the value it does given that the other
     * variables take on the values they do in that cell.
     */
    private double getCellProb(int[] variableValues) {
        double p = 1.0;

        for (int node = 0; node < variableValues.length; node++) {
            int[] parents = getBayesIm().getParents(node);
            int[] parentValues = new int[parents.length];
            for (int parentIndex = 0;
                    parentIndex < parentValues.length; parentIndex++) {
                parentValues[parentIndex] =
                        variableValues[parents[parentIndex]];
            }

            int rowIndex = getBayesIm().getRowIndex(node, parentValues);
            int colIndex = variableValues[node];

            p *= getBayesIm().getProbability(node, rowIndex, colIndex);
        }

        return p;
    }

    /**
     * Calculates the set of descendants of the given node that are
     * d-connected to the node given its parents and all evidence
     * variables.
     */
    private boolean[] calcRelevantVars(int nodeIndex) {
        boolean[] relevantVars = new boolean[evidence.getNumNodes()];

        for (int i = 0; i < relevantVars.length; i++) {
            relevantVars[i] = false;
        }

        Node node = bayesIm.getNode(nodeIndex);

        List<Node> variablesInEvidence = evidence.getVariablesInEvidence();

        List<Node> nodesInEvidence = new LinkedList<Node>();

        for (Node _node : variablesInEvidence) {
            nodesInEvidence.add(bayesIm.getBayesPm().getNode(_node.getName()));
        }

        List<Node> conditionedNodes =
                new LinkedList<Node>(nodesInEvidence);
        conditionedNodes.addAll(bayesIm.getDag().getParents(node));

        for (int i = 0; i < bayesIm.getNumNodes(); i++) {
            Node node2 = bayesIm.getNode(i);

            // Added the condition node == node2 since the updater was corrected to exclude this.
            // jdramsey 12.13.2014
            if (node == node2 || bayesIm.getDag().isDConnectedTo(node, node2, conditionedNodes)) {
                relevantVars[i] = true;
            }
        }

        return relevantVars;
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
    }
}





