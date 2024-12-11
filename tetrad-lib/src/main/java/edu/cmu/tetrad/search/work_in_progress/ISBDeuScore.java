package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.special.Gamma;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Added by Fattaneh Calculates the Instance-Specific BDeu score.
 */
public class ISBDeuScore implements ISScore {
    private static final boolean verbose = false;
    private final int[][] data;
    private final int sampleSize;
    private final int[][] test;
    private final int[] numCategories;

    //	private Graph graph_pop;
    private List<Node> variables;
    private double samplePrior = 1;
    private double structurePrior = 1;
    private double k_addition = 0.1;
    private double k_deletion = 0.1;
    private double k_reorient = 0.1;

    /**
     * Initializes the ISBDeuScore with the given dataset and test case.
     *
     * @param dataSet the dataset to be used for scoring. Must not be null.
     * @param testCase the test case to evaluate. Must not be null.
     * @throws NullPointerException if either dataSet or testCase is null.
     */
    public ISBDeuScore(DataSet dataSet, DataSet testCase) {

        if (dataSet == null || testCase == null) {
            throw new NullPointerException("Dataset or test case was not provided.");
        }

        if (dataSet instanceof BoxDataSet) {
            DataBox dataBox = ((BoxDataSet) dataSet).getDataBox();

            this.variables = dataSet.getVariables();

            if (!(dataBox instanceof VerticalIntDataBox)) {
                dataBox = new VerticalIntDataBox(dataBox);
            }

            VerticalIntDataBox box = (VerticalIntDataBox) dataBox;

            data = box.getVariableVectors();
            this.sampleSize = box.numRows();
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
            numCategories[i] = (getVariable(i)).getNumCategories();
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
     * Computes the row index based on the given dimensions and values.
     *
     * @param dim    an array representing the dimensions.
     * @param values an array representing the values corresponding to each dimension.
     * @return the computed row index as an integer.
     */
    private static int getRowIndex(int[] dim, int[] values) {
        int rowIndex = 0;
        for (int i = 0; i < dim.length; i++) {
            rowIndex *= dim[i];
            rowIndex += values[i];
        }
        return rowIndex;
    }

    private DiscreteVariable getVariable(int i) {
        return (DiscreteVariable) variables.get(i);
    }

    @Override
    public double localScore(int node, int[] parents_is, int[] parents_pop, int[] children_pop) {

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
                continue;
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
        Map<List<Integer>, Double> row_priors = new HashMap<>();

        for (int i = 0; i < r_all; i++) {
            int[] rowValues = getParentValuesForCombination(i, dims_all);
            row_priors.put(Arrays.stream(rowValues).boxed().collect(Collectors.toList()), 1.0 / r_all);
        }

        double scoreIS = 0.0, scorePop = 0.0, score;

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

//		System.out.println("scoreIS: " + scoreIS);
        scoreIS += getPriorForStructure(node, parents_is, parents_pop, children_pop);
//		System.out.println("scoreIS prior: " + getPriorForStructure(node, parents_is, parents_pop, children_pop));
//		System.out.println("scorePop: " + scorePop);

//		scorePop += getPriorForStructure(parents_pop.length);
        score = scorePop + scoreIS;
        return score;
    }

    /**
     * Computes the prior for a specific row based on its parent values and the provided priors map.
     *
     * @param parents       An array of integers representing the parent indices for the current row.
     * @param parent_values An array of integers representing the values of the parents for the current row.
     * @param parents_all   An array of all parent indices available.
     * @param row_priors    A map where the key is a list of integers representing parent combinations and the value is
     *                      the prior associated with that combination.
     * @return The computed prior value for the current row.
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
     * Finds the indices of specified elements in the `parents_all` array.
     *
     * @param parents     the array of elements whose indices need to be found.
     * @param parents_all the array in which to find the indices of the elements.
     * @return an array of indices where each element of `parents` is found in the `parents_all` array.
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
     * Computes the product of dimensions corresponding to an array of parent indices.
     *
     * @param parents An array of parent indices.
     * @param dims    An array of dimensions where each element represents the dimension size of the corresponding
     *                parent.
     * @return The product of the dimensions of the specified parents.
     */
    private int computeAllParentStates(int[] parents, int[] dims) {
        int r = 1;
        for (int p = 0; p < parents.length; p++) {
            r *= dims[p];
        }
        return r;
    }

    /**
     * Computes the dimensions array based on the categories of the parent elements.
     *
     * @param parents An array of parent indices where each index correlates to an element.
     * @return An array of dimensions where each dimension corresponds to the number of categories associated with each
     * parent.
     */
    private int[] getDimentions(int[] parents) {
        int[] dims = new int[parents.length];

        for (int p = 0; p < parents.length; p++) {
            dims[p] = numCategories[parents[p]];
        }
        return dims;
    }

    /**
     * Calculates the prior probability for a given node structure in a Bayesian network. This method evaluates the
     * changes in the parent-child relationships of the node by calculating the number of additions, deletions, and
     * reversals required to transition from the prior structure to the current structure.
     *
     * @param nodeIndex    The index of the node in the network.
     * @param parents      Array representing the current parents of the node.
     * @param parents_pop  Array representing the prior parents of the node.
     * @param children_pop Array representing the prior children of the node.
     * @return The computed prior probability for the given node structure.
     */
    private double getPriorForStructure(int nodeIndex, int[] parents, int[] parents_pop, int[] children_pop) {
        List<Integer> added = new ArrayList<>();
        List<Integer> reversed = new ArrayList<>();

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
        List<Integer> removed = new ArrayList<>();
        for (int i : parents_pop) {
            if (!copyParents_is.contains(i))
                removed.add(i);
        }
        if (verbose) {
            System.out.println("node: " + nodeIndex);
            System.out.println("parents is:   " + Arrays.toString(parents));
            System.out.println("parents pop:  " + Arrays.toString(parents_pop));
            System.out.println("childern pop: " + Arrays.toString(children_pop));
            System.out.println("added: " + added);
            System.out.println("removed: " + removed);
            System.out.println("reversed: " + reversed);
            System.out.println("------------------");
        }
        return added.size() * Math.log(getKAddition()) + removed.size() * Math.log(getKDeletion()) + reversed.size() * Math.log(getKReorientation());
    }

    /**
     * Computes the parent values for a given node index and row index according to the specified dimensions.
     *
     * @param nodeIndex The index of the node for which parent values are computed.
     * @param rowIndex  The row index used for computations.
     * @param dims      An array representing the dimensions of the parent values.
     * @return An array of integers representing the computed parent values.
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
     * Calculates the parent values for a given combination of dimensions.
     *
     * @param rowIndex the index representing the combination in a linearized form
     * @param dims     an array indicating the size of each dimension
     * @return an array of integers where each element corresponds to the value of the dimension at that index
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
     * Computes the difference in local scores when element x is appended to the array z.
     *
     * @param x         the element to be appended to the array z
     * @param y         the dependent variable whose score is being calculated
     * @param z         the initial array of elements
     * @param z_pop     the population related to z
     * @param child_pop the population related to the dependent variable y
     * @return the difference in local scores after appending x to z
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z, int[] z_pop, int[] child_pop) {
        double S1 = localScore(y, append(z, x), z_pop, child_pop);
        double S2 = localScore(y, z, z_pop, child_pop);
        return S1 - S2;
    }

    /**
     * Appends an integer to the end of an array.
     *
     * @param parents the original array of integers
     * @param extra   the integer to append to the array
     * @return a new array containing all elements of the original array, followed by the appended integer
     */
    private int[] append(int[] parents, int extra) {
        int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    /**
     * Retrieves the list of variables associated with this instance.
     *
     * @return a list of Node objects representing the variables
     */
    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Sets the list of variables after validating that each variable in the provided list has the same name as the
     * corresponding variable in the existing list.
     *
     * @param variables a list of Node objects to be set as the new variables
     * @throws IllegalArgumentException if any variable in the provided list does not have the same name as the variable
     *                                  it is replacing
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
     * Retrieves a DataSet object.
     *
     * @return a DataSet object
     * @throws UnsupportedOperationException if the method is not supported
     */
    @Override
    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
    }

    /**
     * Retrieves the value of the structure prior.
     *
     * @return the structure prior as a double.
     */
    @Override
    public double getStructurePrior() {
        return structurePrior;
    }

    /**
     * Sets the structure prior for the model.
     *
     * @param structurePrior the prior value to be set, typically controlling the influence of structure in the model
     */
    @Override
    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }

    /**
     * Retrieves the prior value of the sample.
     *
     * @return the prior value of the sample as a double.
     */
    @Override
    public double getSamplePrior() {
        return samplePrior;
    }

    /**
     * Sets the value for the samplePrior field.
     *
     * @param samplePrior The prior sample value to be set.
     */
    @Override
    public void setSamplePrior(double samplePrior) {
        this.samplePrior = samplePrior;
    }

    /**
     * Retrieves the value of the variable representing the k_addition.
     *
     * @return the current value of the k_addition variable as a double.
     */
    public double getKAddition() {
        return k_addition;
    }

    /**
     * Sets the value of k_addition.
     *
     * @param k_addition The value to set for k_addition.
     */
    public void setKAddition(double k_addition) {
        this.k_addition = k_addition;
    }

    /**
     * Retrieves the value of the k_deletion field.
     *
     * @return the current value of k_deletion.
     */
    public double getKDeletion() {
        return k_deletion;
    }

    /**
     * Sets the value of k_deletion.
     *
     * @param k_deletion the new value to set for k_deletion
     */
    public void setKDeletion(double k_deletion) {
        this.k_deletion = k_deletion;
    }

    /**
     * Retrieves the value of the k_reorient variable.
     *
     * @return The value of k_reorient as a double.
     */
    public double getKReorientation() {
        return k_reorient;
    }

    /**
     * Sets the value of the K-reorientation parameter.
     *
     * @param k_reorient the new value for the K-reorientation parameter
     */
    public void setKReorientation(double k_reorient) {
        this.k_reorient = k_reorient;
    }

    /**
     * Retrieves a Node from the variables list that matches the specified target name.
     *
     * @param targetName the name of the target Node to be retrieved
     * @return the Node that matches the target name, or null if no match is found
     */
    public Node getVariable(String targetName) {
        for (Node node : variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }

        return null;
    }

    /**
     * Calculates the maximum degree based on the sample size.
     *
     * @return the maximum degree as an integer.
     */
    @Override
    public int getMaxDegree() {
        return (int) Math.ceil(Math.log(sampleSize));
    }

    /**
     * Determines whether a given node y is determined by a list of nodes z.
     *
     * @param z the list of nodes to be evaluated
     * @param y the node to be checked
     * @return true if y is determined by the list of nodes z, false otherwise
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        return false;
    }

    /**
     * Computes the local score for a given node considering both population and context-specific parents.
     * <p>
     * This function is used to score a node in a dag without using structure prior
     *
     * @param node         The index of the node for which the score is being calculated.
     * @param parents_is   Array representing the indices of the context-specific parents.
     * @param parents_pop  Array representing the indices of the population-based parents.
     * @param children_pop Array representing the indices of the population-based children.
     * @return The computed local score for the specified node.
     */
    public double localScore1(int node, int[] parents_is, int[] parents_pop, int[] children_pop) {

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
                continue;
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
        Map<List<Integer>, Double> row_priors = new HashMap<>();

        for (int i = 0; i < r_all; i++) {
            int[] rowValues = getParentValuesForCombination(i, dims_all);
            row_priors.put(Arrays.stream(rowValues).boxed().collect(Collectors.toList()), 1.0 / r_all);
        }

        double scoreIS = 0.0, scorePop = 0.0, score;

        // compute IS score
        if (parents_is.length > 0) {
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
            int[] parentValuesPop = getParentValuesForCombination(j, dims_p);
            double rowPrior_p = computeRowPrior(parents_pop, parentValuesPop, parents_all, row_priors);
            rowPrior_p = getSamplePrior() * rowPrior_p;
            double cellPrior_p = rowPrior_p / K;

            if (rowPrior_p > 0) {
                scorePop -= Gamma.logGamma(rowPrior_p + np_j[j]);
                for (int k = 0; k < K; k++) {
                    scorePop += Gamma.logGamma(cellPrior_p + np_jk[j][k]);
                    scorePop -= Gamma.logGamma(cellPrior_p);
                }
                scorePop += Gamma.logGamma(rowPrior_p);
            }
        }

        score = scorePop + scoreIS;
        return score;
    }
}
