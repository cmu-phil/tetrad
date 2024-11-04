package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.special.Gamma;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A class representing the ISBicScore, which calculates BIC scores for a Bayesian network considering different
 * structural changes and their impacts using an information-sharing mechanism.
 */
public class ISBicScore implements ISScore {

    /**
     * A 2D array storing the dataset for scoring computations.
     */
    private final int[][] data;
    /**
     * A 2-dimensional array of integers used for testing purposes.
     */
    private final int[][] test;
    /**
     * The sample size used for score computation.
     */
    private final int sampleSize;
    /**
     * A constant addition factor used in the calculation of the ISBicScore.
     * <p>
     * This variable is employed to adjust the scoring function by adding a small constant value during certain
     * calculations. It helps prevent issues such as overfitting by introducing a minor penalty, thereby aiding the
     * model's generalization capability.
     */
    private final double k_addition = 0.1;
    /**
     * The deletion penalty constant used in the ISBicScore class for scoring algorithms. This variable represents the
     * penalty assigned when an edge is removed in the context of a scoring computation, which is used to control the
     * complexity of the network structure.
     */
    private final double k_deletion = 0.1;
    /**
     * The penalty factor applied to the reorientation term in the scoring mechanism of the ISBicScore class.
     * <p>
     * This variable is used to penalize the complexity of model structure changes that involve reorienting the
     * direction of edges between nodes in the graphical model. It ensures that the score appropriately reflects the
     * cost of adding such reorientations to the model, maintaining a balance between model fit and complexity.
     */
    private final double k_reorient = 0.1;
    /**
     * An array storing the number of categories for each variable present in the dataset used by the ISBicScore class.
     */
    private final int[] numCategories;
    /**
     * The list of variables in the dataset.
     */
    private List<Node> variables;
    /**
     * Represents a discount applied to penalty terms in the scoring algorithm. The default value is set to 1,
     * indicating no discount.
     */
    private double penaltyDiscount = 1;

    /**
     * Constructs an ISBicScore instance with the provided data sets.
     *
     * @param dataSet  The primary DataSet instance for training, must not be null.
     * @param testCase The test DataSet instance used for validation or testing.
     * @throws NullPointerException     if dataSet is null.
     * @throws IllegalArgumentException if dataBox within dataSet is not an instance of VerticalIntDataBox.
     */
    public ISBicScore(DataSet dataSet, DataSet testCase) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        if (dataSet instanceof BoxDataSet) {
            DataBox dataBox = ((BoxDataSet) dataSet).getDataBox();

            this.variables = dataSet.getVariables();

            if (!(((BoxDataSet) dataSet).getDataBox() instanceof VerticalIntDataBox)) {
                throw new IllegalArgumentException();
            }

            VerticalIntDataBox box = (VerticalIntDataBox) dataBox;

            data = box.getVariableVectors();
            this.sampleSize = dataSet.getNumRows();
        } else {
            data = new int[dataSet.getNumColumns()][];
            this.variables = dataSet.getVariables();

            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                data[j] = new int[dataSet.getNumRows()];

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    data[j][i] = dataSet.getInt(i, j);
                }
            }

            this.sampleSize = dataSet.getNumRows();
        }

        final List<Node> variables = dataSet.getVariables();
        numCategories = new int[variables.size()];
        for (int i = 0; i < variables.size(); i++) {
            DiscreteVariable variable = getVariable(i);

            if (variable != null) {
                numCategories[i] = variable.getNumCategories();
            }
        }
        // convert test case to an array
        if (testCase instanceof BoxDataSet) {
            DataBox testBox = ((BoxDataSet) testCase).getDataBox();

            this.variables = dataSet.getVariables();

            if (!(testBox instanceof VerticalIntDataBox)) {
                testBox = new VerticalIntDataBox(testBox);
            }

            VerticalIntDataBox box = (VerticalIntDataBox) testBox;
            test = box.getVariableVectors();
            // this.sampleSize = dataSet.getNumRows();
        } else {
            test = new int[testCase.getNumColumns()][];
            // this.variables = dataSet.getVariables();

            for (int j = 0; j < testCase.getNumColumns(); j++) {
                test[j] = new int[testCase.getNumRows()];

                for (int i = 0; i < testCase.getNumRows(); i++) {
                    test[j][i] = testCase.getInt(i, j);
                }
            }
            // this.sampleSize = dataSet.getNumRows();
        }
    }

    /**
     * Calculates the composite row index based on the provided dimensions and values.
     *
     * @param dim    An array of integers representing the dimensions. Each element in the array corresponds to the size of a specific dimension.
     * @param values An array of integers representing the values. Each element in the array corresponds to a value in the respective dimension specified in the dim array.
     * @return The computed row index based on the provided dimensions and values.
     */
    private static int getRowIndex(int[] dim, int[] values) {
        int rowIndex = 0;
        for (int i = 0; i < dim.length; i++) {
            rowIndex *= dim[i];
            rowIndex += values[i];
        }
        return rowIndex;
    }

    /**
     * Retrieves the variable at the specified index if it is an instance of DiscreteVariable.
     *
     * @param i the index of the variable to retrieve
     * @return the variable at the specified index if it is a DiscreteVariable, otherwise null
     */
    private DiscreteVariable getVariable(int i) {
        if (variables.get(i) instanceof DiscreteVariable) {
            return (DiscreteVariable) variables.get(i);
        } else {
            return null;
        }
    }

    /**
     * Calculates the local score for a given node considering its parents.
     *
     * @param node         The index of the node to calculate the score for.
     * @param parents_is   Array of indices representing the parents of the node in the IS model.
     * @param parents_pop  Array of indices representing the parents of the node in the POP model.
     * @param children_pop Array of indices representing the children of the node in the POP model.
     * @return The local score for the given node.
     */
    public double localScore(int node, int[] parents_is, int[] parents_pop, int[] children_pop) {
        if (!(variables.get(node) instanceof DiscreteVariable)) {
            throw new IllegalArgumentException("Not discrete: " + variables.get(node));
        }

        for (int t : parents_is) {
            if (!(variables.get(t) instanceof DiscreteVariable)) {
                throw new IllegalArgumentException("Not discrete: " + variables.get(t));
            }
        }

        // Number of categories for node.
        int K = numCategories[node];

        // Numbers of categories of parents in POP and IS models.
        int[] dims_p = getDimentions(parents_pop);

        // Number of parent states  in POP, IS, and both.
        int r_p = computeAllParentStates(parents_pop, dims_p);

        // Conditional cell coefs of data for node given population parents(node).
        int[][] np_jk = new int[r_p][K];
        int[] np_j = new int[r_p];

        // Conditional cell coefs of data for node given context specific parents(node).
        int[] ni_jk = new int[K];
        int ni_j = 0;

        int[] parentValuesTest = new int[parents_is.length];
        for (int i = 0; i < parents_is.length; i++) {
            parentValuesTest[i] = test[parents_is[i]][0];
        }


        int[] myChild = data[node];

        ROW:
        for (int i = 0; i < sampleSize; i++) {
            int[] parentValues = new int[parents_is.length];
            for (int p = 0; p < parents_is.length; p++) {
                if (data[parents_is[p]][i] == -99) continue ROW;
                parentValues[p] = data[parents_is[p]][i];
            }

            int childValue = myChild[i];

            if (childValue == -99) {
                continue ROW;
            }

            if (Arrays.equals(parentValues, parentValuesTest) && parentValuesTest.length > 0) {
                ni_jk[childValue]++;
                ni_j++;
            } else {
                int[] parentValuesPop = new int[parents_pop.length];
                for (int p = 0; p < parents_pop.length; p++) {
                    if (data[parents_pop[p]][i] == -99) continue ROW;
                    parentValuesPop[p] = data[parents_pop[p]][i];
                }

                int rowIndex = getRowIndex(dims_p, parentValuesPop);

                np_jk[rowIndex][childValue]++;
                np_j[rowIndex]++;
            }
        }

        // computing priors
        List<Integer> parents_all_list = IntStream.of(parents_pop).boxed().collect(Collectors.toList());
        for (int parentsI : parents_is) {
            if (!parents_all_list.contains(parentsI)) {
                parents_all_list.add(parentsI);
            }
        }
        int[] parents_all = parents_all_list.stream().mapToInt(i -> i).toArray();
        Arrays.sort(parents_all);
        int[] dims_all = getDimentions(parents_all);

        // Number of parent states  in POP, IS, and both.
        int r_all = computeAllParentStates(parents_all, dims_all);
        Map<List<Integer>, Double> row_priors = new HashMap<List<Integer>, Double>();

        for (int i = 0; i < r_all; i++) {
            int[] rowValues = getParentValuesForCombination(i, dims_all);
            row_priors.put(Arrays.stream(rowValues).boxed().collect(Collectors.toList()), 1.0 / r_all);
        }

        double scoreIS = 0.0, scorePop = 0.0, score = 0.0;

        // compute IS score
        if (parents_is.length > 0) {

            // K2 prior
            //			double rowPrior_i = getSamplePrior() * K;
            //			double cellPrior_i = getSamplePrior();

            double rowPrior_i = computeRowPrior(parents_is, parentValuesTest, parents_all, row_priors);
            rowPrior_i = getSamplePrior() * rowPrior_i;
            double cellPrior_i = rowPrior_i / K;

            for (int k = 0; k < K; k++) {
                scoreIS += Gamma.logGamma(cellPrior_i + ni_jk[k]);
            }

            scoreIS -= K * Gamma.logGamma(cellPrior_i);
            scoreIS -= Gamma.logGamma(rowPrior_i + ni_j);
            scoreIS += Gamma.logGamma(rowPrior_i);
        }

        // re-compute pop score
        for (int j = 0; j < r_p; j++) {

            // K2 prior
            //			double rowPrior_p = getSamplePrior() * K;
            //			double cellPrior_p = getSamplePrior();

            int[] parentValuesPop = new int[parents_pop.length];
            parentValuesPop = getParentValuesForCombination(j, dims_p);
            double rowPrior_p = computeRowPrior(parents_pop, parentValuesPop, parents_all, row_priors);
            rowPrior_p = getSamplePrior() * rowPrior_p;
            double cellPrior_p = rowPrior_p / K;

            if (rowPrior_p > 0) {
                scorePop -= Gamma.logGamma(rowPrior_p + np_j[j]);
                for (int k = 0; k < K; k++) {
                    //					if(np_jk[j][k] > 0){
                    scorePop += Gamma.logGamma(cellPrior_p + np_jk[j][k]);
                    //					}
                    scorePop -= Gamma.logGamma(cellPrior_p);
                }
                scorePop += Gamma.logGamma(rowPrior_p);
            }
        }


        scoreIS += getPriorForStructure(node, parents_is, parents_pop, children_pop);
        scorePop += getPriorForStructure(parents_pop.length);
        score = scorePop + scoreIS;
        return score;
    }

    /**
     * Calculates the difference in local scores for a given variable when it is added to or removed from a set of parent variables.
     *
     * @param x         The variable to be added or removed.
     * @param y         The target variable whose score is being calculated.
     * @param z         The set of parent variables excluding the variable x.
     * @param z_pop     The set of population parent variables excluding the variable x.
     * @param child_pop The set of population children variables.
     * @return The difference in local scores after adding/removing the variable x.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z, int[] z_pop, int[] child_pop) {
        double S1 = localScore(y, append(z, x), z_pop, child_pop);
        double S2 = localScore(y, z, z_pop, child_pop);
        return S1 - S2;
    }

    /**
     * Computes the row prior based on the given parents, parent values, and row priors map.
     *
     * @param parents      An array of indices representing the parent nodes.
     * @param parent_values An array of values corresponding to each parent node.
     * @param parents_all  An array of all possible parent nodes.
     * @param row_priors   A map where the key is a list of integers representing parent values and the value is the corresponding row prior.
     * @return The computed row prior for the given parent values.
     */
    private double computeRowPrior(int[] parents, int[] parent_values, int[] parents_all, Map<List<Integer>, Double> row_priors) {
        double rowPrior = 0.0;
        int[] indecies = findIndex(parents, parents_all);

        for (List<Integer> k : row_priors.keySet()) {
            boolean equalKeys = true;
            for (int i = 0; i < parents.length; i++) {
                if (k.get(indecies[i]) != parent_values[i]) {
                    equalKeys = false;
                    break;
                }
            }
            if (equalKeys) {
                rowPrior += row_priors.get(k);
                row_priors.put(k, 0.0);
            }
        }
        return rowPrior;
    }

    /**
     * Finds the indices of the elements in the `parents` array within the `parents_all` array.
     *
     * @param parents An array of integers representing a subset of parent nodes.
     * @param parents_all An array of integers representing all possible parent nodes.
     * @return An array of integers where each value is the index in `parents_all` that corresponds to each element in `parents`.
     */
    private int[] findIndex(int[] parents, int[] parents_all) {
        int[] index = new int[parents.length];
        for (int i = 0; i < parents.length; i++) {
            for (int j = 0; j < parents_all.length; j++) {
                if (parents_all[j] == parents[i]) {
                    index[i] = j;
                    break;
                }
            }
        }
        return index;
    }

    /**
     * Computes the total number of parent states given arrays of parent indices and their respective dimensions.
     *
     * @param parents An array of integers representing the indices of the parent nodes.
     * @param dims    An array of integers representing the dimension sizes of each parent node.
     *
     * @return The total number of possible parent states, calculated as the product of the dimensions.
     */
    private int computeAllParentStates(int[] parents, int[] dims) {
        int r = 1;
        for (int p = 0; p < parents.length; p++) {
            r *= dims[p];
        }
        return r;
    }

    /**
     * Calculates the dimensions array based on the parent indices.
     * Each dimension corresponds to the number of categories of the respective parent node.
     *
     * @param parents An array of indices representing the parent nodes.
     * @return An array of integers where each value corresponds to the number
     *         of categories for each respective parent node.
     */
    private int[] getDimentions(int[] parents) {
        int[] dims = new int[parents.length];

        for (int p = 0; p < parents.length; p++) {
            dims[p] = numCategories[parents[p]];
        }
        return dims;
    }

    /**
     * Calculates the prior probability for the given structure of a node.
     * This calculation involves analyzing the additions, deletions, and reorientations
     * of parent nodes between the current structure and a given population structure.
     *
     * @param nodeIndex     The index of the node for which the prior is being calculated.
     * @param parents       An array representing the parent nodes of the current structure.
     * @param parents_pop   An array representing the parent nodes in the population structure.
     * @param children_pop  An array representing the children nodes in the population structure.
     *
     * @return The prior probability for the given structure based on additions, deletions,
     *         and reorientations of parent nodes.
     */
    private double getPriorForStructure(int nodeIndex, int[] parents, int[] parents_pop, int[] children_pop) {
        List<Integer> added = new ArrayList<Integer>();
        List<Integer> reversed = new ArrayList<Integer>();

        List<Integer> copyParents_pop = IntStream.of(parents_pop).boxed().toList();
        List<Integer> copyChildren_pop = IntStream.of(children_pop).boxed().toList();

        for (int parent : parents) {
            if (!copyParents_pop.contains(parent)) {
                if (!copyChildren_pop.contains(parent))
                    added.add(parent);
                else
                    reversed.add(parent);
            }
        }

        List<Integer> copyParents_is = IntStream.of(parents).boxed().toList();
        List<Integer> removed = new ArrayList<Integer>();
        for (int i : parents_pop) {
            if (!copyParents_is.contains(i))
                removed.add(i);
        }

        return added.size() * Math.log(getKAddition()) + removed.size() * Math.log(getKDeletion()) + reversed.size() * Math.log(getKReorientation());
    }

    /**
     * Calculates the parent values at given node and row indices based on the provided dimensions.
     *
     * @param nodeIndex The index of the node for which parent values are being calculated.
     * @param rowIndex The row index that needs to be converted to parent values.
     * @param dims An array representing the dimensions of the parents.
     * @return An array of integers where each value corresponds to a parent value at the given row index.
     */
    public int[] getParentValues(int nodeIndex, int rowIndex, int[] dims) {
        int[] values = new int[dims.length];

        for (int i = dims.length - 1; i >= 0; i--) {
            values[i] = rowIndex % dims[i];
            rowIndex /= dims[i];
        }

        return values;
    }

    /**
     * Calculates the parent values for a given row index based on the provided dimensions.
     *
     * @param rowIndex The row index that needs to be converted to parent values.
     * @param dims An array representing the dimensions of the parents.
     * @return An array of integers where each value corresponds to a parent value at the given row index.
     */
    public int[] getParentValuesForCombination(int rowIndex, int[] dims) {
        int[] values = new int[dims.length];

        for (int i = dims.length - 1; i >= 0; i--) {
            values[i] = rowIndex % dims[i];
            rowIndex /= dims[i];
        }

        return values;
    }

    /**
     * Retrieves the current value of the k_addition parameter.
     *
     * @return The value of the k_addition parameter.
     */
    public double getKAddition() {
        return k_addition;
    }

    /**
     * Retrieves the current value of the k_deletion parameter.
     *
     * @return The value of the k_deletion parameter.
     */
    public double getKDeletion() {
        return k_deletion;
    }

    /**
     * Retrieves the current value of the k_reorientation parameter.
     *
     * @return The value of the k_reorientation parameter.
     */
    public double getKReorientation() {
        return k_reorient;
    }

    /**
     * Calculates the prior for a specific structural configuration.
     *
     * @param numParents the number of parent nodes in the structural configuration
     * @return the prior probability of the given structure based on the number of parents
     */
    private double getPriorForStructure(int numParents) {
        double e = getStructurePrior();
        int vm = data.length - 1;
        return numParents * Math.log(e / (vm)) + (vm - numParents) * Math.log(1.0 - (e / (vm)));
    }

    /**
     * Appends an extra integer to the end of the provided array of parent integers.
     *
     * @param parents An array of integers representing parent nodes.
     * @param extra   An integer to append to the end of the parents array.
     * @return A new array that includes all elements from the parents array followed by the extra integer.
     */
    private int[] append(int[] parents, int extra) {
        int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    /**
     * Retrieves the list of variables associated with this ISBicScore instance.
     *
     * @return a List of Node objects representing the variables.
     */
    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Sets the list of variables for the instance. All nodes in the new list must
     * have the same names as the corresponding nodes in the existing list.
     *
     * @param variables the new list of variables to set
     * @throws IllegalArgumentException if any variable in the new list has a different
     *                                  name from the corresponding variable in the existing list
     */
    public void setVariables(List<Node> variables) {
        for (int i = 0; i < variables.size(); i++) {
            if (!variables.get(i).getName().equals(this.variables.get(i).getName())) {
                throw new IllegalArgumentException("Variable in index " + (i + 1) + " does not have the same name " +
                                                   "as the variable being substituted for it.");
            }
        }

        this.variables = variables;
    }

    /**
     * Retrieves the current sample size.
     *
     * @return the sample size as an integer.
     */
    public int getSampleSize() {
        return sampleSize;
    }

    /**
     * Must be called directly after the corresponding scoring call.
     */
    public boolean isEffectEdge(double bump) {
        return bump > 0;//lastBumpThreshold;
    }

    /**
     * Retrieves the data set from the current context.
     *
     * @return DataSet object containing the data.
     * @throws UnsupportedOperationException if the operation is not supported.
     */
    @Override
    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
    }

    /**
     * Retrieves the prior probability of the given structure.
     *
     * @return the prior probability of the structure as a double.
     * @throws UnsupportedOperationException if the operation is not supported.
     */
    @Override
    public double getStructurePrior() {
        throw new UnsupportedOperationException();
    }

    /**
     * Sets the structure prior value for the current configuration.
     *
     * @param structurePrior the value to be set as the structure prior.
     */
    @Override
    public void setStructurePrior(double structurePrior) {
        throw new UnsupportedOperationException();
    }

    /**
     * Calculates and returns the sample prior value.
     *
     * @return The sample prior value as a double.
     * @throws UnsupportedOperationException if the operation is not supported.
     */
    @Override
    public double getSamplePrior() {
        throw new UnsupportedOperationException();
    }

    /**
     * Sets the value of the sample prior.
     *
     * @param samplePrior The value to be set as the sample prior.
     * @throws UnsupportedOperationException if the operation is not supported.
     */
    @Override
    public void setSamplePrior(double samplePrior) {
        throw new UnsupportedOperationException();
    }

    /**
     * Retrieves the penalty discount associated with this entity.
     *
     * @return the penalty discount as a double
     */
    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    /**
     * Sets the penalty discount.
     *
     * @param penaltyDiscount The discount to be applied as a penalty, represented as a decimal.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * Retrieves a Node from the variables list that matches the specified target name.
     *
     * @param targetName the name of the variable to search for
     * @return the Node that matches the target name, or null if no match is found
     */
    @Override
    public Node getVariable(String targetName) {
        for (Node node : variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }

        return null;
    }

    /**
     * Retrieves the maximum degree value.
     *
     * @return the maximum degree, which is a constant value of 1000.
     */
    @Override
    public int getMaxDegree() {
        return 1000;
    }

    /**
     * Determines if the given list of nodes can determine the specified node.
     *
     * @param z the list of nodes that are tested to determine the node y
     * @param y the node whose determination is being tested
     * @return true if the list of nodes z can determine the node y, otherwise false
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        return false;
    }

    /**
     * Computes a local score based on the given node, its parents, and its children's populations.
     *
     * @param node an integer representing the current node for which the score is being computed
     * @param parents_is an array of integers representing the indices of a node's parents
     * @param parents_pop an array of integers representing the population of each parent
     * @param children_pop an array of integers representing the population of each child
     * @return a double representing the computed local score
     */
    @Override
    public double localScore1(int node, int[] parents_is, int[] parents_pop, int[] children_pop) {
        // TODO Auto-generated method stub
        return 0;
    }

}



