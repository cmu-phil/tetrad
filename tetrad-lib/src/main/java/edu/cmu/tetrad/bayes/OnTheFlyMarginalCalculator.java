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
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.VariableSource;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Calculated updated marginals for the given parametric  Bayes net on the fly
 * from data, based on a given Bayes net model.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public final class OnTheFlyMarginalCalculator
        implements BayesUpdater, VariableSource {
    static final long serialVersionUID = 23L;

    /**
     * The associated Bayes PM model.
     *
     * @serial Cannot be null.
     */
    private BayesPm bayesPm;

    /**
     * The array of nodes from the graph.  Order is important.
     *
     * @serial Cannot be null.
     */
    private Node[] nodes;

    /**
     * The list of parents for each node from the graph.  Order or nodes
     * corresponds to the order of nodes in 'nodes', and order in subarrays is
     * important.
     *
     * @serial Cannot be null.
     */
    private int[][] parents;

    /**
     * The array of dimensionality (number of values for each node) for each of
     * the subarrays of 'parents'.
     *
     * @serial Cannot be null.
     */
    private int[][] parentDims;

    /**
     * The data set used for discreteProbs.
     *
     * @serial Cannot be null.
     */
    private DataSet dataSet;

    /**
     * The evidence being updated on.
     *
     * @serial Cannot be null.
     */
    private Evidence evidence;

    /**
     * Estimates probabilities for arbitrary event from a data set. (Note to
     * future self: This field does not need to be serialized.)
     */
    private transient DiscreteProbs discreteProbs;

    //===============================CONSTRUCTORS=========================//

    /**
     * Constructs a new on-the-fly BayesIM that will calculate conditional
     * probabilities on the fly from the given discrete data set, for the given
     * Bayes PM.
     *
     * @param bayesPm the given Bayes PM, which specifies a directed acyclic
     *                graph for a Bayes net and parametrization for the Bayes
     *                net, but not actual values for the parameters.
     * @param dataSet the discrete data set from which conditional probabilities
     *                should be estimated on the fly.
     */
    public OnTheFlyMarginalCalculator(BayesPm bayesPm,
                                      DataSet dataSet) throws IllegalArgumentException {
        if (bayesPm == null) {
            throw new NullPointerException();
        }

        if (dataSet == null) {
            throw new NullPointerException();
        }

        // Make sure all of the variables in the PM are in the data set;
        // otherwise, estimation is impossible.
        BayesUtils.ensureVarsInData(bayesPm.getVariables(), dataSet);
        //        DataUtils.ensureVariablesExist(bayesPm, dataSet);

        this.bayesPm = new BayesPm(bayesPm);

        // Get the nodes from the BayesPm. This fixes the order of the nodes
        // in the BayesIm, independently of any change to the BayesPm.
        // (This order must be maintained.)
        Graph graph = bayesPm.getDag();
        this.nodes = graph.getNodes().toArray(new Node[graph.getNodes().size()]);

        // Initialize.
        initialize();

        // Create a subset of the data set with the variables of the IM, in
        // the order of the IM.
        List<Node> variables = getVariables();
        this.dataSet = dataSet.subsetColumns(variables);

        // Create a tautologous proposition for evidence.
        this.evidence = new Evidence(Proposition.tautology(this));
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static OnTheFlyMarginalCalculator serializableInstance() {
        return new OnTheFlyMarginalCalculator(BayesPm.serializableInstance(),
                DataUtils.discreteSerializableInstance());
    }

    //===============================PUBLIC METHODS========================//

    public void setEvidence(Evidence evidence) {
        if (evidence == null) {
            throw new NullPointerException();
        }

        if (evidence.getProposition().getVariableSource() != this) {
            throw new IllegalArgumentException("Can only take evidence for " +
                    "this particular object; please convert the evidence.");
        }

        this.evidence = evidence;
    }

    /**
     * @param node the given node.
     * @return the index for that node, or -1 if the node is not in the
     * BayesIm.
     */
    private int getNodeIndex(Node node) {
        for (int i = 0; i < nodes.length; i++) {
            if (node == nodes[i]) {
                return i;
            }
        }

        return -1;
    }

    public List<Node> getVariables() {
        List<Node> variables = new LinkedList<>();

        for (int i = 0; i < getNumNodes(); i++) {
            Node node = getNode(i);
            variables.add(bayesPm.getVariable(node));
        }

        return variables;
    }

    public List<String> getVariableNames() {
        List<String> variableNames = new LinkedList<>();

        for (int i = 0; i < getNumNodes(); i++) {
            Node node = getNode(i);
            variableNames.add(bayesPm.getVariable(node).getName());
        }

        return variableNames;
    }

    /**
     * @return this array of parent dimensions.
     */
    public int[] getParentDims(int nodeIndex) {
        int[] dims = parentDims[nodeIndex];
        int[] copy = new int[dims.length];
        System.arraycopy(dims, 0, copy, 0, dims.length);
        return copy;
    }

    /**
     * @return the updated marginal for the given category of the given variable
     * with respect to the evidence set via the setEvidence() method, as
     * predicted by a Bayes net with the given parameterization, where all of
     * the relevant conditional probabilities are computed on the fly from the
     * given discrete data set.
     */
    public double getMarginal(int variable, int category) {
        if (category >= getNumCategories(variable)) {
            throw new IllegalArgumentException();
        }

        return getUpdatedMarginalFromModel(variable, category);
    }

    public boolean isJointMarginalSupported() {
        return false;
    }

    public double getJointMarginal(int[] variables, int[] values) {
        throw new UnsupportedOperationException();
    }

    public BayesIm getBayesIm() {
        throw new UnsupportedOperationException();
    }

    public double[] calculatePriorMarginals(int nodeIndex) {
        Evidence evidence = getEvidence();
        setEvidence(Evidence.tautology(evidence.getVariableSource()));

        double[] marginals = new double[evidence.getNumCategories(nodeIndex)];

        for (int i = 0;
             i < getBayesIm().getNumColumns(nodeIndex); i++) {
            marginals[i] = getMarginal(nodeIndex, i);
        }

        setEvidence(evidence);
        return marginals;
    }

    public double[] calculateUpdatedMarginals(int nodeIndex) {
        double[] marginals = new double[evidence.getNumCategories(nodeIndex)];

        for (int i = 0;
             i < getBayesIm().getNumColumns(nodeIndex); i++) {
            marginals[i] = getMarginal(nodeIndex, i);
        }

        return marginals;
    }

    //=============================PRIVATE METHODS=======================//

    /**
     * @return the underlying Bayes PM.
     */
    private BayesPm getBayesPm() {
        return bayesPm;
    }

    /**
     * @return an evidence object containing the proposition in evidence, with
     * null Bayes IM and null manipulation.
     */
    private Evidence getEvidence() {
        return new Evidence(evidence.getProposition());
    }

    /**
     * @return the number of nodes in the model.
     */
    private int getNumNodes() {
        return nodes.length;
    }

    /**
     * @param nodeIndex
     * @return this node.
     */
    private Node getNode(int nodeIndex) {
        return nodes[nodeIndex];
    }

    /**
     * @param nodeIndex
     * @return this number.
     */
    private int getNumCategories(int nodeIndex) {
        Node node = nodes[nodeIndex];
        return getBayesPm().getNumCategories(node);
    }

    /**
     * @return (a defensive copy of) the array containing all of the parents of
     * a given node in the order in which they are stored internally.
     * @see #getParentDims
     */
    private int[] getParents(int nodeIndex) {
        int[] nodeParents = parents[nodeIndex];
        int[] copy = new int[nodeParents.length];
        System.arraycopy(nodeParents, 0, copy, 0, nodeParents.length);
        return copy;
    }

    /**
     * @return the class being used to calculate discrete probabilities from the
     * underlying data set.
     */
    private DiscreteProbs getDiscreteProbs() {
        if (discreteProbs == null) {
            this.discreteProbs = new IntAveDataSetProbs(dataSet);
            //        this.discreteProbs = new DataSetProbs(dataSet3);
            //        this.discreteProbs = new InterpolatedDataSetProbs2(dataSet3);
            //        this.discreteProbs = new DirichletDataSetProbs(dataSet3, 0.1);
        }

        return discreteProbs;
    }

    /**
     * Initializes the lists of parents and parent dimension for each variable.
     */
    private void initialize() {
        parents = new int[this.nodes.length][];
        parentDims = new int[this.nodes.length][];

        for (int nodeIndex = 0; nodeIndex < this.nodes.length; nodeIndex++) {
            initializeNode(nodeIndex);
        }
    }

    /**
     * Initializes the lists of parents and parent dimension for the given
     * variable.
     */
    private void initializeNode(int nodeIndex) {
        Node node = nodes[nodeIndex];

        // Set up parents array.  Should store the parents of
        // each node as ints in a particular order.
        Graph graph = getBayesPm().getDag();
        List<Node> parentList = new ArrayList<>(graph.getParents(node));
        int[] parentArray = new int[parentList.size()];

        for (int i = 0; i < parentList.size(); i++) {
            parentArray[i] = getNodeIndex(parentList.get(i));
        }

        // Sort parent array.
        Arrays.sort(parentArray);

        parents[nodeIndex] = parentArray;

        // Setup dimensions array for parents.
        int[] dims = new int[parentArray.length];

        for (int i = 0; i < dims.length; i++) {
            Node parNode = nodes[parentArray[i]];
            dims[i] = getBayesPm().getNumCategories(parNode);
        }

        parentDims[nodeIndex] = dims;
    }

//    /**
//     * @return the probability that the given variable has the given value,
//     * given the evidence.
//     */
//    private double getUpdatedMarginalFromModel1(int variable, int category) {
//        Proposition evidence = getEvidence().getProposition();
//
//        double p = 1.0;
//
//        for (int m = 0; m < getNumNodes(); m++) {
//            Proposition assertion = Proposition.tautology(this);
//            assertion.setCategory(variable, category);
//
//            Proposition condition = Proposition.tautology(this);
//            int[] parents = getParents(m);
//
//            for (int n = 0; n < parents.length; n++) {
//                condition.restrictToProposition(evidence, parents[n]);
//            }
//
//            if (condition.existsCombination()) {
//                p *= getDiscreteProbs().getConditionalProb(assertion, condition);
//            }
//        }
//
//        return p;
//    }

    private double getUpdatedMarginalFromModel(int variable, int category) {
        Proposition evidence = getEvidence().getProposition();
        int[] variableValues = new int[evidence.getNumVariables()];

        for (int i = 0; i < evidence.getNumVariables(); i++) {
            variableValues[i] = nextValue(evidence, i, -1);
        }

        variableValues[variableValues.length - 1] = -1;
        double sum = 0.0;

        loop:
        while (true) {
            for (int i = evidence.getNumVariables() - 1; i >= 0; i--) {
                if (hasNextValue(evidence, i, variableValues[i])) {
                    variableValues[i] = nextValue(evidence, i, variableValues[i]);

                    for (int j = i + 1; j < evidence.getNumVariables(); j++) {
                        if (hasNextValue(evidence, j, -1)) {
                            variableValues[j] = nextValue(evidence, j, -1);
                        } else {
                            break loop;
                        }
                    }

                    double product = 1.0;

                    for (int m = 0; m < getNumNodes(); m++) {
                        Proposition assertion = Proposition.tautology(this);
                        assertion.setCategory(variable, category);

                        Proposition condition = new Proposition(evidence);
                        int[] parents = getParents(m);

                        for (int parent : parents) {
                            condition.disallowComplement(parent,
                                    variableValues[parent]);
                        }

                        if (condition.existsCombination()) {
                            product *= getDiscreteProbs().getConditionalProb(
                                    assertion, condition);
                        }
                    }

                    sum += product;
                    continue loop;
                }
            }

            break;
        }

        return sum;
    }


//    public double getUpdatedMarginalFromModel3(int variable, int category) {
//        Proposition evidence = getEvidence().getProposition();
//        Proposition assertion = Proposition.tautology(this);
//        assertion.setCategory(variable, category);
//
//        Proposition condition = new Proposition(evidence);
//
//
//        int[] variableValues = new int[condition.getNumVariables()];
//
//        for (int i = 0; i < condition.getNumVariables(); i++) {
//            variableValues[i] = nextValue(condition, i, -1);
//        }
//
//        variableValues[variableValues.length - 1] = -1;
//        double conditionTrue = 0.0;
//        double assertionTrue = 0.0;
//
//        loop:
//        while (true) {
//            for (int i = condition.getNumVariables() - 1; i >= 0; i--) {
//                if (hasNextValue(condition, i, variableValues[i])) {
//                    variableValues[i] =
//                            nextValue(condition, i, variableValues[i]);
//
//                    for (int j = i + 1; j < condition.getNumVariables(); j++) {
//                        if (hasNextValue(condition, j, -1)) {
//                            variableValues[j] = nextValue(condition, j, -1);
//                        }
//                        else {
//                            break loop;
//                        }
//                    }
//
//                    double cellProb =
//                            getDiscreteProbs().getCellProb(variableValues);
//
//                    if (Double.isNaN(cellProb)) {
//                        continue;
//                    }
//
//                    boolean assertionHolds = true;
//
//                    for (int j = 0; j < assertion.getNumVariables(); j++) {
//                        if (!assertion.isAllowed(j, variableValues[j])) {
//                            assertionHolds = false;
//                            break;
//                        }
//                    }
//
//                    if (assertionHolds) {
//                        assertionTrue += cellProb;
//                    }
//
//                    conditionTrue += cellProb;
//                    continue loop;
//                }
//            }
//
//            break;
//        }
//
//        return assertionTrue / conditionTrue;
//    }

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

        if (bayesPm == null) {
            throw new NullPointerException();
        }

        if (nodes == null) {
            throw new NullPointerException();
        }

        if (dataSet == null) {
            throw new NullPointerException();
        }

        if (evidence == null) {
            throw new NullPointerException();
        }

        if (parents == null) {
            throw new NullPointerException();
        }

        if (parentDims == null) {
            throw new NullPointerException();
        }
    }
}





