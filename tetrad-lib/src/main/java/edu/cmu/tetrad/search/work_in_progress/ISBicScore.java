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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.math3.special.Gamma;

/**
 * A class representing the ISBicScore, which calculates BIC scores
 * for a Bayesian network considering different structural changes
 * and their impacts using an information-sharing mechanism.
 */
public class ISBicScore implements ISScore {

    /**
     * The list of variables in the dataset.
     */
    private List<Node> variables;

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
     * Represents a discount applied to penalty terms in the scoring algorithm.
     * The default value is set to 1, indicating no discount.
     */
    private double penaltyDiscount = 1;

    /**
     * A constant addition factor used in the calculation of the ISBicScore.
     * <p>
     * This variable is employed to adjust the scoring function by adding a small
     * constant value during certain calculations. It helps prevent issues such as
     * overfitting by introducing a minor penalty, thereby aiding the model's
     * generalization capability.
     */
    private final double k_addition = 0.1;

    /**
     * The deletion penalty constant used in the ISBicScore class for scoring algorithms.
     * This variable represents the penalty assigned when an edge is removed in the
     * context of a scoring computation, which is used to control the complexity of the
     * network structure.
     */
    private final double k_deletion = 0.1;

    /**
     * The penalty factor applied to the reorientation term in the scoring mechanism of the ISBicScore class.
     *
     * This variable is used to penalize the complexity of model structure changes that involve reorienting
     * the direction of edges between nodes in the graphical model. It ensures that the score appropriately
     * reflects the cost of adding such reorientations to the model, maintaining a balance between model
     * fit and complexity.
     */
    private final double k_reorient = 0.1;

    /**
     * An array storing the number of categories for each variable present
     * in the dataset used by the ISBicScore class.
     */
    private final int[] numCategories;

    /**
     * Constructs an ISBicScore instance with the provided data sets.
     *
     * @param dataSet The primary DataSet instance for training, must not be null.
     * @param testCase The test DataSet instance used for validation or testing.
     * @throws NullPointerException if dataSet is null.
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
        if (testCase instanceof BoxDataSet ) {
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
     * @param node The index of the node to calculate the score for.
     * @param parents_is Array of indices representing the parents of the node in the IS model.
     * @param parents_pop Array of indices representing the parents of the node in the POP model.
     * @param children_pop Array of indices representing the children of the node in the POP model.
     * @return The local score for the given node.
     */
    public double localScore(int node, int[] parents_is, int[] parents_pop, int[] children_pop) {
        if (!(variables.get(node) instanceof  DiscreteVariable)) {
            throw new IllegalArgumentException("Not discrete: " + variables.get(node));
        }

        for (int t : parents_is) {
            if (!(variables.get(t) instanceof  DiscreteVariable)) {
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
        int np_j[] = new int[r_p];

        // Conditional cell coefs of data for node given context specific parents(node).
        int ni_jk[] = new int[K];
        int ni_j = 0;

        int[] parentValuesTest = new int[parents_is.length];
        for (int i = 0; i < parents_is.length ; i++){
            parentValuesTest[i] =  test[parents_is[i]][0];
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

            if (childValue == -99){
                continue ROW;
            }

            if (Arrays.equals(parentValues, parentValuesTest) && parentValuesTest.length > 0){
                ni_jk[childValue]++;
                ni_j++;
            }

            else{
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
        List<Integer> parents_all_list = new ArrayList<Integer>(IntStream.of(parents_pop).boxed().collect(Collectors.toList()));
        for (int k = 0; k < parents_is.length; k++) {
            if (!parents_all_list.contains(parents_is[k])){
                parents_all_list.add(parents_is[k]);
            }
        }
        int[] parents_all = parents_all_list.stream().mapToInt(i->i).toArray();
        Arrays.sort(parents_all);
        int[] dims_all = getDimentions(parents_all);

        // Number of parent states  in POP, IS, and both.
        int r_all = computeAllParentStates(parents_all, dims_all);
        Map<List<Integer>, Double> row_priors = new HashMap<List<Integer>, Double>();

        for (int i = 0; i < r_all; i++){
            int[] rowValues = getParentValuesForCombination(i, dims_all);
            row_priors.put(Arrays.stream(rowValues).boxed().collect(Collectors.toList()), 1.0/r_all);
        }

        double scoreIS = 0.0, scorePop = 0.0, score = 0.0;

        // compute IS score
        if (parents_is.length>0){

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
            double cellPrior_p = rowPrior_p / K ;

            if(rowPrior_p > 0){
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

    @Override
    public double localScoreDiff(int x, int y, int[] z, int[] z_pop, int[] child_pop) {

        double S1 = localScore(y, append(z, x), z_pop, child_pop);
        double S2 = localScore(y, z, z_pop, child_pop);
        double diff = S1 - S2;
        //	System.out.println("y: " + y);
        //	System.out.println("x: " + x);
        //	System.out.println("PA_is: " + Arrays.toString(z));
        //	System.out.println("PA_pop: " + Arrays.toString(z_pop));
        //
        //	System.out.println("S1: " + S1);
        //	System.out.println("S2: " + S2);
        //	System.out.println("diff: " + diff);
        //	System.out.println("-------------------");
        return diff;
        //		return localScore(y, append(z, x), z_pop)-localScore(y, z, z_pop);
    }


    private double computeRowPrior(int[] parents, int[] parent_values, int[] parents_all, Map<List<Integer>, Double> row_priors) {
        double rowPrior = 0.0;
        int [] indecies = findIndex(parents, parents_all);

        for (List<Integer> k: row_priors.keySet()){
            boolean equalKeys = true;
            for (int i = 0; i < parents.length; i++){
                if (k.get(indecies[i]) != parent_values[i]){
                    equalKeys = false;
                    break;
                }
            }
            if (equalKeys){
                rowPrior += row_priors.get(k);
                row_priors.put(k, 0.0);
            }
        }
        return rowPrior;
    }

    private int[] findIndex(int[] parents, int[] parents_all) {
        int[] index = new int[parents.length];
        for (int i = 0; i < parents.length; i++){
            for (int j = 0; j < parents_all.length; j++){
                if (parents_all[j]==parents[i]){
                    index[i] = j;
                    break;
                }
            }
        }
        return index;
    }

    private int computeAllParentStates(int[] parents, int[] dims) {
        int r = 1;
        for (int p = 0; p < parents.length; p++) {
            r *= dims[p];
        }
        return r;
    }
    private int[] getDimentions(int[] parents) {
        int[] dims = new int[parents.length];

        for (int p = 0; p < parents.length; p++) {
            dims[p] = numCategories[parents[p]];
        }
        return dims;
    }


    private double getPriorForStructure(int nodeIndex, int[] parents, int[] parents_pop, int[] children_pop) {
        List<Integer> added = new ArrayList<Integer>();
        List<Integer> reversed = new ArrayList<Integer>();

        List<Integer> copyParents_pop = IntStream.of(parents_pop).boxed().collect(Collectors.toList());
        List<Integer> copyChildren_pop = IntStream.of(children_pop).boxed().collect(Collectors.toList());

        for (int k = 0; k < parents.length; k++) {
            if (!copyParents_pop.contains(parents[k])){
                if (!copyChildren_pop.contains(parents[k]))
                    added.add(parents[k]);
                else
                    reversed.add(parents[k]);
            }
        }

        List<Integer> copyParents_is = IntStream.of(parents).boxed().collect(Collectors.toList());
        List<Integer> removed = new ArrayList<Integer>();
        for (int k = 0; k < parents_pop.length; k++){
            if (!copyParents_is.contains(parents_pop[k]))
                removed.add(parents_pop[k]);
        }
        //		if (this.verbose){
        //				System.out.println("node: " + nodeIndex);
        //				System.out.println("parents is:   " + Arrays.toString(parents));
        //				System.out.println("parents pop:  " + Arrays.toString(parents_pop));
        //				System.out.println("childern pop: " + Arrays.toString(children_pop));
        //				System.out.println("added: " + added);
        //				System.out.println("removed: " + removed);
        //				System.out.println("reversed: " + reversed);
        //				System.out.println("------------------");
        //		}
        return added.size() * Math.log(getKAddition())+removed.size() * Math.log(getKDeletion())+reversed.size()*Math.log(getKReorientation());
    }

    public int[] getParentValues(int nodeIndex, int rowIndex, int[] dims) {
        int[] values = new int[dims.length];

        for (int i = dims.length - 1; i >= 0; i--) {
            values[i] = rowIndex % dims[i];
            rowIndex /= dims[i];
        }

        return values;
    }

    public int[] getParentValuesForCombination(int rowIndex, int[] dims) {
        int[] values = new int[dims.length];

        for (int i = dims.length - 1; i >= 0; i--) {
            values[i] = rowIndex % dims[i];
            rowIndex /= dims[i];
        }

        return values;
    }

    private static int getRowIndex(int[] dim, int[] values) {
        int rowIndex = 0;
        for (int i = 0; i < dim.length; i++) {
            rowIndex *= dim[i];
            rowIndex += values[i];
        }
        return rowIndex;
    }
    public double getKAddition() {
        return k_addition;
    }

    public double getKDeletion() {
        return k_deletion;
    }

    public double getKReorientation() {
        return k_reorient;
    }
    //    @Override
    //    public double localScore(int node, int parents[]) {
    //
    //        if (!(variables.get(node) instanceof  DiscreteVariable)) {
    //            throw new IllegalArgumentException("Not discrete: " + variables.get(node));
    //        }
    //
    //        for (int t : parents) {
    //            if (!(variables.get(t) instanceof  DiscreteVariable)) {
    //                throw new IllegalArgumentException("Not discrete: " + variables.get(t));
    //            }
    //        }
    //
    //        // Number of categories for node.
    //        int c = numCategories[node];
    //
    //        // Numbers of categories of parents.
    //        int[] dims = new int[parents.length];
    //
    //        for (int p = 0; p < parents.length; p++) {
    //            dims[p] = numCategories[parents[p]];
    //        }
    //
    //        // Number of parent states.
    //        int r = 1;
    //
    //        for (int p = 0; p < parents.length; p++) {
    //            r *= dims[p];
    //        }
    //
    //        // Conditional cell coefs of data for node given parents(node).
    //        int n_jk[][] = new int[r][c];
    //        int n_j[] = new int[r];
    //
    //        int[] parentValues = new int[parents.length];
    //
    //        int[][] myParents = new int[parents.length][];
    //        for (int i = 0; i < parents.length; i++) {
    //            myParents[i] = data[parents[i]];
    //        }
    //
    //        int[] myChild = data[node];
    //
    //        ROW:
    //        for (int i = 0; i < sampleSize; i++) {
    //            for (int p = 0; p < parents.length; p++) {
    //                if (myParents[p][i] == -99) continue ROW;
    //                parentValues[p] = myParents[p][i];
    //            }
    //
    //            int childValue = myChild[i];
    //
    //            if (childValue == -99) {
    //                continue ROW;
    ////                throw new IllegalStateException("Please remove or impute missing " +
    ////                        "values (record " + i + " column " + i + ")");
    //            }
    //
    //            int rowIndex = getRowIndex(dims, parentValues);
    //
    //            n_jk[rowIndex][childValue]++;
    //            n_j[rowIndex]++;
    //        }
    //
    //        //Finally, compute the score
    //        double lik = 0.0;
    //
    //        for (int rowIndex = 0; rowIndex < r; rowIndex++) {
    //            for (int childValue = 0; childValue < c; childValue++) {
    //                int cellCount = n_jk[rowIndex][childValue];
    //                int rowCount = n_j[rowIndex];
    //
    //                if (cellCount == 0) continue;
    //                lik += cellCount * Math.log(cellCount / (double) rowCount);
    //            }
    //        }
    //
    //        int params = r * (c - 1);
    //        int n = getSampleSize();
    //
    //        return 2 * lik - penaltyDiscount * params * Math.log(n);
    //    }

    private double getPriorForStructure(int numParents) {
        double e = getStructurePrior();
        int vm = data.length - 1;
        return numParents * Math.log(e / (vm)) + (vm - numParents) * Math.log(1.0 - (e / (vm)));
    }


    int[] append(int[] parents, int extra) {
        int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    public int getSampleSize() {
        return sampleSize;
    }

    /**
     * Must be called directly after the corresponding scoring call.
     */
    public boolean isEffectEdge(double bump) {
        return bump > 0;//lastBumpThreshold;
    }

    @Override
    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
    }


    @Override
    public double getStructurePrior() {
        throw new UnsupportedOperationException();
    }

    @Override
    public double getSamplePrior() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setStructurePrior(double structurePrior) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSamplePrior(double samplePrior) {
        throw new UnsupportedOperationException();
    }

    public void setVariables(List<Node> variables) {
        for (int i = 0; i < variables.size(); i++) {
            if (!variables.get(i).getName().equals(this.variables.get(i).getName())) {
                throw new IllegalArgumentException("Variable in index " + (i + 1) + " does not have the same name " +
                                                   "as the variable being substituted for it.");
            }
        }

        this.variables = variables;
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    @Override
    public Node getVariable(String targetName) {
        for (Node node : variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }

        return null;
    }

    @Override
    public int getMaxDegree() {
        return 1000;
    }

    @Override
    public boolean determines(List<Node> z, Node y) {
        return false;
    }

    @Override
    public double localScore1(int node, int[] parents_is, int[] parents_pop, int[] children_pop) {
        // TODO Auto-generated method stub
        return 0;
    }

}



