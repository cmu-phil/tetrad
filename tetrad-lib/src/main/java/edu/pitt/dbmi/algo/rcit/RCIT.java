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
public final class RCIT implements IndependenceTest {

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.search.IndependenceTest#indTestSubset(java.util.List)
	 */
	@Override
	public IndependenceTest indTestSubset(List<Node> vars) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.search.IndependenceTest#isIndependent(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node, java.util.List)
	 */
	@Override
	public boolean isIndependent(Node x, Node y, List<Node> z) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.search.IndependenceTest#isIndependent(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node[])
	 */
	@Override
	public boolean isIndependent(Node x, Node y, Node... z) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.search.IndependenceTest#isDependent(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node, java.util.List)
	 */
	@Override
	public boolean isDependent(Node x, Node y, List<Node> z) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.search.IndependenceTest#isDependent(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node[])
	 */
	@Override
	public boolean isDependent(Node x, Node y, Node... z) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.search.IndependenceTest#getPValue()
	 */
	@Override
	public double getPValue() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.search.IndependenceTest#getVariables()
	 */
	@Override
	public List<Node> getVariables() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.search.IndependenceTest#getVariable(java.lang.String)
	 */
	@Override
	public Node getVariable(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.search.IndependenceTest#getVariableNames()
	 */
	@Override
	public List<String> getVariableNames() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.search.IndependenceTest#determines(java.util.List, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean determines(List<Node> z, Node y) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.search.IndependenceTest#getAlpha()
	 */
	@Override
	public double getAlpha() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.search.IndependenceTest#setAlpha(double)
	 */
	@Override
	public void setAlpha(double alpha) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.search.IndependenceTest#getData()
	 */
	@Override
	public DataModel getData() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.search.IndependenceTest#getCov()
	 */
	@Override
	public ICovarianceMatrix getCov() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.search.IndependenceTest#getDataSets()
	 */
	@Override
	public List<DataSet> getDataSets() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.search.IndependenceTest#getSampleSize()
	 */
	@Override
	public int getSampleSize() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.search.IndependenceTest#getCovMatrices()
	 */
	@Override
	public List<TetradMatrix> getCovMatrices() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.search.IndependenceTest#getScore()
	 */
	@Override
	public double getScore() {
		// TODO Auto-generated method stub
		return 0;
	}

}
