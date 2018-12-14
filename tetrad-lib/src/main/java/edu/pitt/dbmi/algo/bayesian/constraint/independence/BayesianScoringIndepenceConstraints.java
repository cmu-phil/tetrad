/**
 * 
 */
package edu.pitt.dbmi.algo.bayesian.constraint.independence;

import java.util.List;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndTestProbabilistic;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.TetradMatrix;

/**
 * Dec 14, 2018 3:05:35 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class BayesianScoringIndepenceConstraints implements IndependenceTest {

    /**
     * The data set for which conditional  independence judgments are requested.
     */
    private final DataSet data;

    boolean threshold = false;

	public BayesianScoringIndepenceConstraints(DataSet data, boolean threshold) {
		IndTestProbabilistic BSCtest = new IndTestProbabilistic(data);
		BSCtest.setThreshold(threshold);
		
		this.data = data;
		this.threshold = threshold;
	}
	
	@Override
	public IndependenceTest indTestSubset(List<Node> vars) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isIndependent(Node x, Node y, List<Node> z) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isIndependent(Node x, Node y, Node... z) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isDependent(Node x, Node y, List<Node> z) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isDependent(Node x, Node y, Node... z) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public double getPValue() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public List<Node> getVariables() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Node getVariable(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getVariableNames() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean determines(List<Node> z, Node y) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public double getAlpha() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setAlpha(double alpha) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public DataModel getData() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ICovarianceMatrix getCov() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<DataSet> getDataSets() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getSampleSize() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public List<TetradMatrix> getCovMatrices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public double getScore() {
		// TODO Auto-generated method stub
		return 0;
	}

}
