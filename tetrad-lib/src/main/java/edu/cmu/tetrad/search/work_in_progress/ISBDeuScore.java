package edu.cmu.tetrad.search.work_in_progress;
///////////////////////////////////////////////////////////////////////////////
//For information as to what this class does, see the Javadoc, below.       //
//Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
//2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
//Ramsey, and Clark Glymour.                                                //
// //
//This program is free software; you can redistribute it and/or modify      //
//it under the terms of the GNU General Public License as published by      //
//the Free Software Foundation; either version 2 of the License, or         //
//(at your option) any later version.                                       //
// //
//This program is distributed in the hope that it will be useful,           //
//but WITHOUT ANY WARRANTY; without even the implied warranty of            //
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
//GNU General Public License for more details.                              //
// //
//You should have received a copy of the GNU General Public License         //
//along with this program; if not, write to the Free Software               //
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;

import org.apache.commons.math3.special.Gamma;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Added by Fattaneh
 * Calculates the Instance-Specific BDeu score.
 *
 */
public class ISBDeuScore implements ISScore {
    private static final boolean verbose = false;
    private List<Node> variables;
    private int[][] data;

    private int sampleSize;
    private int[][] test;

    //	private Graph graph_pop;

    private double samplePrior = 1;
    private double structurePrior = 1;
    private double k_addition = 0.1;
    private double k_deletion = 0.1;
    private double k_reorient = 0.1;

    private int[] numCategories;

    public ISBDeuScore(DataSet dataSet, DataSet testCase) {

        if (dataSet == null || testCase == null) {
            throw new NullPointerException("Dataset or test case was not provided.");
        }

        if (dataSet instanceof BoxDataSet ) {
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
        int np_jk[][] = new int[r_p][K];
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

//		System.out.println("scoreIS: " + scoreIS);
        scoreIS += getPriorForStructure(node, parents_is, parents_pop, children_pop);
//		System.out.println("scoreIS prior: " + getPriorForStructure(node, parents_is, parents_pop, children_pop));
//		System.out.println("scorePop: " + scorePop);

//		scorePop += getPriorForStructure(parents_pop.length);
        score = scorePop + scoreIS;
        return score;
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
        if (this.verbose){
            System.out.println("node: " + nodeIndex);
            System.out.println("parents is:   " + Arrays.toString(parents));
            System.out.println("parents pop:  " + Arrays.toString(parents_pop));
            System.out.println("childern pop: " + Arrays.toString(children_pop));
            System.out.println("added: " + added);
            System.out.println("removed: " + removed);
            System.out.println("reversed: " + reversed);
            System.out.println("------------------");
        }
        return added.size() * Math.log(getKAddition())+removed.size() * Math.log(getKDeletion())+reversed.size()*Math.log(getKReorientation());
    }

    private double getPriorForStructure(int numParents) {
        double e = getStructurePrior();
        int vm = data.length - 1;
        return numParents * Math.log(e / (vm)) + (vm - numParents) * Math.log(1.0 - (e / (vm)));
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

//	private double getPriorForStructure(int numParents) {
//		double e = getStructurePrior();
//		int vm = data.length - 1;
//		return numParents * Math.log(e / (vm)) + (vm - numParents) * Math.log(1.0 - (e / (vm)));
//	}

    @Override
    public double localScoreDiff(int x, int y, int[] z, int[] z_pop, int[] child_pop) {

        double S1 = localScore(y, append(z, x), z_pop, child_pop);
        double S2 = localScore(y, z, z_pop, child_pop);
        double diff = S1 - S2;
//		System.out.println("y: " + y);
//		System.out.println("x: " + x);
//		System.out.println("PA_is: " + Arrays.toString(z));
//		System.out.println("PA_pop: " + Arrays.toString(z_pop));

//		System.out.println("S1: " + S1);
//		System.out.println("S2: " + S2);
//		System.out.println("diff: " + diff);
//		System.out.println("-------------------");
        return diff;
        //		return localScore(y, append(z, x), z_pop)-localScore(y, z, z_pop);
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

    private static int getRowIndex(int[] dim, int[] values) {
        int rowIndex = 0;
        for (int i = 0; i < dim.length; i++) {
            rowIndex *= dim[i];
            rowIndex += values[i];
        }
        return rowIndex;
    }

//	private static int getNumRows(int[] dim, int[] values) {
//		int rowIndex = 0;
//		for (int i = 0; i < dim.length; i++) {
//			rowIndex *= dim[i];
//			rowIndex += values[i];
//		}
//		return rowIndex;
//	}

    @Override
    public double getStructurePrior() {
        return structurePrior;
    }

    @Override
    public double getSamplePrior() {
        return samplePrior;
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

    @Override
    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }

    @Override
    public void setSamplePrior(double samplePrior) {
        this.samplePrior = samplePrior;
    }

    public void setKAddition(double k_addition) {
        this.k_addition = k_addition;
    }

    public void setKDeletion(double k_deletion) {
        this.k_deletion = k_deletion;
    }

    public void setKReorientation(double k_reorient) {
        this.k_reorient = k_reorient;
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
        return (int) Math.ceil(Math.log(sampleSize));
    }

    @Override
    public boolean determines(List<Node> z, Node y) {
        return false;
    }

    //	@Override
//	public double localScore1(int node, int[] parents_is, int[] parents_pop) {
//		// Number of categories for node.
//				int K = numCategories[node];
//
//				// Numbers of categories of parents in POP and IS models.
//				int[] dims_p = getDimentions(parents_pop);
//
//				// Number of parent states  in POP, IS, and both.
//				int r_p = computeAllParentStates(parents_pop, dims_p);
//
//				// Conditional cell coefs of data for node given population parents(node).
//				int np_jk[][] = new int[r_p][K];
//				int np_j[] = new int[r_p];
//
//				// Conditional cell coefs of data for node given context specific parents(node).
//				int ni_jk[] = new int[K];
//				int ni_j = 0;
//
//				int[] parentValuesTest = new int[parents_is.length];
//				for (int i = 0; i < parents_is.length ; i++){
//					parentValuesTest[i] =  test[parents_is[i]][0];
//				}
//
//
//				int[] myChild = data[node];
//
//				ROW:
//					for (int i = 0; i < sampleSize; i++) {
//						int[] parentValues = new int[parents_is.length];
//						for (int p = 0; p < parents_is.length; p++) {
//							if (data[parents_is[p]][i] == -99) continue ROW;
//							parentValues[p] = data[parents_is[p]][i];
//						}
//
//						int childValue = myChild[i];
//
//						if (childValue == -99){
//							continue ROW;
//						}
//
//						if (Arrays.equals(parentValues, parentValuesTest) && parentValuesTest.length > 0){
//							ni_jk[childValue]++;
//							ni_j++;
//						}
//
//						else{
//							int[] parentValuesPop = new int[parents_pop.length];
//							for (int p = 0; p < parents_pop.length; p++) {
//								if (data[parents_pop[p]][i] == -99) continue ROW;
//								parentValuesPop[p] = data[parents_pop[p]][i];
//							}
//
//							int rowIndex = getRowIndex(dims_p, parentValuesPop);
//
//							np_jk[rowIndex][childValue]++;
//							np_j[rowIndex]++;
//						}
//					}
//
//				// computing priors
//				List<Integer> parents_all_list = new ArrayList<Integer>(IntStream.of(parents_pop).boxed().collect(Collectors.toList()));
//				for (int k = 0; k < parents_is.length; k++) {
//					if (!parents_all_list.contains(parents_is[k])){
//						parents_all_list.add(parents_is[k]);
//					}
//				}
//				int[] parents_all = parents_all_list.stream().mapToInt(i->i).toArray();
//				Arrays.sort(parents_all);
//				int[] dims_all = getDimentions(parents_all);
//
//				// Number of parent states  in POP, IS, and both.
//				int r_all = computeAllParentStates(parents_all, dims_all);
//				Map<List<Integer>, Double> row_priors = new HashMap<List<Integer>, Double>();//[r_all][parents_all.length + 1];
//
//				for (int i = 0; i < r_all; i++){
//					int[] rowValues = getParentValuesForCombination(i, dims_all);
//					row_priors.put(Arrays.stream(rowValues).boxed().collect(Collectors.toList()), 1.0/r_all);
//				}
////				System.out.println(row_priors);
//				double scoreIS = 0.0, scorePop = 0.0, score = 0.0;
//
//				if (parents_is.length>0){
//					double rowPrior_i = computeRowPrior(parents_is, parentValuesTest, parents_all, row_priors);
//					rowPrior_i = getSamplePrior() * rowPrior_i;
////					System.out.println("rowPrior_i "+ rowPrior_i);
//					double cellPrior_i = rowPrior_i / K;
//					for (int k = 0; k < K; k++) {
//						scoreIS += Gamma.logGamma(cellPrior_i + ni_jk[k]);
//					}
//
//					scoreIS -= K * Gamma.logGamma(cellPrior_i);
//					scoreIS -= Gamma.logGamma(rowPrior_i + ni_j);
//					scoreIS += Gamma.logGamma(rowPrior_i);
//				}
////				System.out.println("parents_is " + Arrays.toString(parents_is));
////				System.out.println("n_i " + Arrays.toString(ni_jk));
////				System.out.println("parents_pop " + Arrays.toString(parents_pop));
////				System.out.println("n_p " + Arrays.deepToString(np_jk));
//				for (int j = 0; j < r_p; j++) {
//					int[] parentValuesPop = new int[parents_pop.length];
//					parentValuesPop = getParentValuesForCombination(j, dims_p);
//					double rowPrior_p = computeRowPrior(parents_pop, parentValuesPop, parents_all, row_priors);
//					rowPrior_p = getSamplePrior() * rowPrior_p;
////					System.out.println("rowPrior_p "+ rowPrior_p);
//					double cellPrior_p = rowPrior_p / K ;
//
//					if(rowPrior_p > 0){
//						scorePop -= Gamma.logGamma(rowPrior_p + np_j[j]);
//						for (int k = 0; k < K; k++) {
////							if(np_jk[j][k] > 0){
//								scorePop += Gamma.logGamma(cellPrior_p + np_jk[j][k]);
////							}
//							scorePop -= Gamma.logGamma(cellPrior_p);
//						}
//						scorePop += Gamma.logGamma(rowPrior_p);
//					}
//				}
////				System.out.println("-------------");
//				score = scorePop + scoreIS;
//				return score;
//	}
    // This function is used to score a node in a dag without using structure prior
    public double localScore1(int node, int[] parents_is, int[] parents_pop, int[] children_pop) {

        // Number of categories for node.
        int K = numCategories[node];

        // Numbers of categories of parents in POP and IS models.
        int[] dims_p = getDimentions(parents_pop);

        // Number of parent states  in POP, IS, and both.
        int r_p = computeAllParentStates(parents_pop, dims_p);

        // Conditional cell coefs of data for node given population parents(node).
        int np_jk[][] = new int[r_p][K];
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
//				double rowPrior_i = getSamplePrior() * K;
//				double cellPrior_i = getSamplePrior();

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
//				double rowPrior_p = getSamplePrior() * K;
//				double cellPrior_p = getSamplePrior();

            int[] parentValuesPop = new int[parents_pop.length];
            parentValuesPop = getParentValuesForCombination(j, dims_p);
            double rowPrior_p = computeRowPrior(parents_pop, parentValuesPop, parents_all, row_priors);
            rowPrior_p = getSamplePrior() * rowPrior_p;
            double cellPrior_p = rowPrior_p / K ;

            if(rowPrior_p > 0){
                scorePop -= Gamma.logGamma(rowPrior_p + np_j[j]);
                for (int k = 0; k < K; k++) {
//						if(np_jk[j][k] > 0){
                    scorePop += Gamma.logGamma(cellPrior_p + np_jk[j][k]);
//						}
                    scorePop -= Gamma.logGamma(cellPrior_p);
                }
                scorePop += Gamma.logGamma(rowPrior_p);
            }
        }


//			scoreIS += getPriorForStructure(node, parents_is, parents_pop, children_pop);
//			scorePop += getPriorForStructure(parents_pop.length);
        score = scorePop + scoreIS;
        return score;
    }

}

//@Override
//public double localScore(int node, int[] parents, int[] parents_pop, int[] children_pop) {
//	// old prior
//	// Number of categories for node.
//	int K = numCategories[node];
//
//	// Numbers of categories of parents in population-wide model.
//	int[] dims = new int[parents_pop.length];
//
//	for (int p = 0; p < parents_pop.length; p++) {
//		dims[p] = numCategories[parents_pop[p]];
//	}
//
//	List<Integer> notSameParents = new ArrayList<Integer>();
//	List<Integer> sameParents = new ArrayList<Integer>();
//	List<Integer> copyParents_pop = IntStream.of(parents_pop).boxed().collect(Collectors.toList());
//
//	for (int k = 0; k < parents.length; k++) {
//		if (!copyParents_pop.contains(parents[k])){
//			notSameParents.add(parents[k]);
//		}
//		else{
//			sameParents.add(parents[k]);
//		}
//	}
//
//	// Number of parent states.
//	int r = 1;
//	int rr = 1;
//	for (int p = 0; p < parents_pop.length; p++) {
//		r *= dims[p];
//		if (!sameParents.contains(parents_pop[p])){
//			rr *= dims[p];
//		}
//	}
//
//	// Conditional cell coefs of data for node given context specific parents(node).
//	int ni_jk[] = new int[K];
//	int ni_j = 0;
//
//	// Conditional cell coefs of data for node given parents(node).
//	int np_jk[][] = new int[r][K];
//	int np_j[] = new int[r];
//
//	int[] parentValuesTest = new int[parents.length];
//	for (int i = 0; i < parents.length ; i++){
//		parentValuesTest[i] =  test[parents[i]][0];
//	}
//
//
//	int[] myChild = data[node];
//
//	ROW:
//		for (int i = 0; i < sampleSize; i++) {
//			int[] parentValues = new int[parents.length];
//			for (int p = 0; p < parents.length; p++) {
//				if (data[parents[p]][i] == -99) continue ROW;
//				parentValues[p] = data[parents[p]][i];
//			}
//
//			int childValue = myChild[i];
//
//			if (childValue == -99){
//				continue ROW;
//			}
//
//			if (Arrays.equals(parentValues, parentValuesTest) && parentValuesTest.length > 0){
//				ni_jk[childValue]++;
//				ni_j++;
//			}
//
//			else{
//				int[] parentValuesPop = new int[parents_pop.length];
//				for (int p = 0; p < parents_pop.length; p++) {
//					if (data[parents_pop[p]][i] == -99) continue ROW;
//					parentValuesPop[p] = data[parents_pop[p]][i];
//				}
//
//				int rowIndex = getRowIndex(dims, parentValuesPop);
//
//				np_jk[rowIndex][childValue]++;
//				np_j[rowIndex]++;
//			}
//		}
//
//
//
//	int r_pop = r;
//	if (sameParents.size() > 0 && notSameParents.size() == 0){
//		r_pop = r - rr;
//	}
//
//	int r_tot = r_pop;
//	boolean pullCases = false;
//	if (pullCases){
//		if (parents.length == 0){
//			r_pop = 0;
//			r_tot = 1;
//		}
//		else{
//			r_tot = 1 + r_pop;
//		}
//	}
//	else{
//		if(parents.length > 0){
//			r_tot = 1 + r_pop;
//		}
//	}
//
////	System.out.println("node: "+ node);
////	System.out.println("Pop parents" + Arrays.toString(parents_pop));
////	System.out.println("PS parents" + Arrays.toString(parents));
////	System.out.println("rows: " + r_tot);
////	System.out.println(Arrays.toString(ni_jk));
////	System.out.println(Arrays.deepToString(np_jk));
//
//	double scoreIS = 0.0, scorePop = 0.0, score = 0.0;
//	final double cellPrior = getSamplePrior() / (K * r_tot);
//	final double rowPrior = getSamplePrior() / (r_tot);
//
//	for (int j = 0; j < r; j++) {
//		if(np_j[j]>0){
//			scorePop -= Gamma.logGamma(rowPrior + np_j[j]);
//
//			for (int k = 0; k < K; k++) {
//				if(np_jk[j][k] > 0){
//					scorePop += Gamma.logGamma(cellPrior + np_jk[j][k]);
//				}
//			}
//		}
//	}
//
//
//	scorePop += (r_pop) * Gamma.logGamma(rowPrior);
//	scorePop -= K * (r_pop) * Gamma.logGamma(cellPrior);
//
//	for (int k = 0; k < K; k++) {
//		scoreIS += Gamma.logGamma(cellPrior + ni_jk[k]);
//	}
//
//	scoreIS -= K * Gamma.logGamma(cellPrior);
//	scoreIS -= Gamma.logGamma(rowPrior + ni_j);
//	scoreIS += Gamma.logGamma(rowPrior);
//
//	scoreIS += getPriorForStructure(node, parents, parents_pop, children_pop);
////	scoreIS += getPriorForStructure(parents.length);
//
//	score = scorePop + scoreIS;
////	System.out.println("Pop: " + scorePop + " + " + "PS: " + scoreIS);
//
//	return score;
//}
