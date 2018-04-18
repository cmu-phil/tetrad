/*
 * Copyright (C) 2015 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.pitt.dbmi.algo.rcit;

import java.util.List;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.TetradMatrix;

/**
 * 
 * Apr 10, 2018 5:10:44 PM
 * 
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public final class RandomizedConditionalIndependenceTest implements IndependenceTest {

    /**
     * The data set this test uses.
     */
    private DataSet dataSet;

    private RandomIndApproximateMethod approx = RandomIndApproximateMethod.lpd4;
	
    private int num_feature = 25;
	
    public RandomizedConditionalIndependenceTest(final DataSet dataSet) {
    	this.dataSet = dataSet;
    }
    
	@Override
	public IndependenceTest indTestSubset(List<Node> vars) {
		return null;
	}

	@Override
	public boolean isIndependent(Node x, Node y, List<Node> z) {
		return false;
	}

	@Override
	public boolean isIndependent(Node x, Node y, Node... z) {
		return false;
	}

	@Override
	public boolean isDependent(Node x, Node y, List<Node> z) {
		return false;
	}

	@Override
	public boolean isDependent(Node x, Node y, Node... z) {
		return false;
	}

	@Override
	public double getPValue() {
		return 0;
	}

	@Override
	public List<Node> getVariables() {
		return null;
	}

	@Override
	public Node getVariable(String name) {
		return null;
	}

	@Override
	public List<String> getVariableNames() {
		return null;
	}

	@Override
	public boolean determines(List<Node> z, Node y) {
		return false;
	}

	@Override
	public double getAlpha() {
		return 0;
	}

	@Override
	public void setAlpha(double alpha) {

	}

	@Override
	public DataModel getData() {
		return null;
	}

	@Override
	public ICovarianceMatrix getCov() {
		return null;
	}

	@Override
	public List<DataSet> getDataSets() {
		return null;
	}

	@Override
	public int getSampleSize() {
		return 0;
	}

	@Override
	public List<TetradMatrix> getCovMatrices() {
		return null;
	}

	@Override
	public double getScore() {
		return 0;
	}

	public DataSet getDataSet() {
		return dataSet;
	}

	public RandomIndApproximateMethod getApprox() {
		return approx;
	}

	public void setApprox(RandomIndApproximateMethod approx) {
		this.approx = approx;
	}

	public int getNum_feature() {
		return num_feature;
	}

	public void setNum_feature(int num_feature) {
		this.num_feature = num_feature;
	}

}
