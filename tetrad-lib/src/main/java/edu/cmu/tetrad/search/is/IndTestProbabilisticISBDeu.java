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

package edu.cmu.tetrad.search.is;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.RandomUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Uses BCInference by Cooper and Bui to calculate probabilistic conditional independence judgments.
 *
 * @author Fattaneh Jabbari 9/2019
 */
public class IndTestProbabilisticISBDeu implements IndependenceTest {

    private boolean threshold = false;

    /**
     * The data set for which conditional  independence judgments are requested.
     */
    private final DataSet data;
    private final DataSet test;
    private final int[][] data_array;
    private final int[][] test_array;
    private double prior = 0.5;
    /**
     * The nodes of the data set.
     */
    private List<Node> nodes;

    private final int[] nodeDimensions ;

    /**
     * Indices of the nodes.
     */
    private Map<Node, Integer> indices;

    private Map<IndependenceFact, Double> H;
    private double posterior;
    private boolean verbose = false;

    private double cutoff = 0.5;

    //==========================CONSTRUCTORS=============================//
    /**
     * Initializes the test using a discrete data sets.
     */
    public IndTestProbabilisticISBDeu(DataSet dataSet, DataSet test, double prior) {
        if (!dataSet.isDiscrete()) {
            throw new IllegalArgumentException("Not a discrete data set.");

        }

        this.prior = prior;
        this.data = dataSet;
        this.test = test;

        //  convert the data and the test case to an array
        this.test_array = new int[this.test.getNumRows()][this.test.getNumColumns()];
        for (int i = 0; i < test.getNumRows(); i++) {
            for (int j = 0; j < test.getNumColumns(); j++) {
                this.test_array[i][j] = test.getInt(i, j);
            }
        }

        this.data_array = new int[dataSet.getNumRows()][dataSet.getNumColumns()];

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                this.data_array[i][j] = dataSet.getInt(i, j);
            }
        }


        this.nodeDimensions = new int[dataSet.getNumColumns() + 2];

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            DiscreteVariable variable = (DiscreteVariable) (dataSet.getVariable(j));
            int numCategories = variable.getNumCategories();
            this.nodeDimensions[j + 1] = numCategories;
        }

        nodes = dataSet.getVariables();

        indices = new HashMap<>();

        for (int i = 0; i < nodes.size(); i++) {
            indices.put(nodes.get(i), i);
        }

        this.setH(new HashMap<>());
    }

    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Node... z) {
        IndependenceFact key = new IndependenceFact(x, y, z);


        double pInd = Double.NaN;

        if (!getH().containsKey(key)) {

            // convert set z to an array of indicies
            int[] _z = new int[z.length];
            for (int i = 0; i < z.length; i++) {
                _z[i] = indices.get(z[i]);
            }

            if (_z.length == 0){
                BDeuScoreWOprior bic = new BDeuScoreWOprior(this.data);
                pInd = computeInd(bic, this.prior, x, y, z);
//				pInd = computeIndWithMultipleStructures(bic, x, y, z);
            }

            else{
                // split the data based on array _z
                DataSet data_is = new BoxDataSet((BoxDataSet) this.data);
                DataSet data_rest = new BoxDataSet((BoxDataSet) this.data);
                splitData(data_is, data_rest, _z);

                BDeuScoreWOprior bic_res = new BDeuScoreWOprior(data_rest);
                double priorP = computeInd(bic_res, this.prior, x, y, z);

                BDeuScoreWOprior bic_is = new BDeuScoreWOprior(data_is);
                pInd = computeInd(bic_is, priorP, x, y, z);

//				// compute BSC based on D that matches values of _z in the test case
//				if(data_is.getNumRows() > 0){ 
//					BDeuScoreWOprior bic_is = new BDeuScoreWOprior(data_is);
//					pInd = computeInd(bic_is, priorP, x, y, z);
//				}
//				else{
//					pInd = priorP;
//				}

            }

            getH().put(key, pInd);

        }else {
            pInd = getH().get(key);
        }

        //        System.out.println("pInd_old: " + pInd_old);
        //        System.out.println("pInd: " + pInd);
        //        System.out.println("--------------------");
        double p = pInd;

        this.posterior = p;

        boolean ind ;
        if (this.threshold){
            ind = (p >= cutoff);
        }
        else{
            ind = RandomUtil.getInstance().nextDouble() < p;
        }

        if (ind) {
            return new IndependenceResult(new IndependenceFact(x, y, z), true, p, Double.NaN);
        } else {
            return new IndependenceResult(new IndependenceFact(x, y, z), false, p, Double.NaN);
        }
    }



    private double computeInd(BDeuScoreWOprior bic, double prior, Node x, Node y, Node... z) {
        double pInd = Double.NaN;
        List<Node> _z = new ArrayList<>();
        _z.add(x);
        _z.add(y);
        Collections.addAll(_z, z);

        Graph indBN = new EdgeListGraph(_z);
        for (Node n : z){
            indBN.addDirectedEdge(n, x);
            indBN.addDirectedEdge(n, y);
        }

        Graph depBN = new EdgeListGraph(_z);
        depBN.addDirectedEdge(x, y);
        for (Node n : z){
            depBN.addDirectedEdge(n, x);
            depBN.addDirectedEdge(n, y);
        }
        double indPrior = Math.log(prior);
        double indScore = scoreDag(indBN,bic);
        //      double indScore = scoreDag(indBN, bic, false, null, null);
        double scoreIndAll = indScore + indPrior;


        double depScore = scoreDag(depBN, bic);
        //      double depScore = scoreDag(depBN, bic, true, x, y);
        double depPrior = Math.log(1 - indPrior);
        double scoreDepAll = depScore + depPrior;

        double scoreAll = lnXpluslnY(scoreIndAll, scoreDepAll);
        //  	System.out.println("scoreDepAll: " + scoreDepAll);
        //      System.out.println("scoreIndAll: " + scoreIndAll);
        //      System.out.println("scoreAll: " + scoreAll);

        pInd = Math.exp(scoreIndAll - scoreAll);

        return pInd;
    }


    //        double indPrior = Math.log(0.5);
    //        double indScore = scoreDag(indBN, bic_is);
    //        double scoreIndAll = indScore + indPrior;
    //
    //
    //        double depScore = scoreDag(depBN, bic_is);
    //        double depPrior = Math.log(1 - indPrior);
    //        double scoreDepAll = depScore + depPrior;
    //
    //        double scoreAll = lnXpluslnY(scoreIndAll, scoreDepAll);
    ////    	System.out.println("scoreDepAll: " + scoreDepAll);
    ////        System.out.println("scoreIndAll: " + scoreIndAll);
    ////        System.out.println("scoreAll: " + scoreAll);
    //
    //        pInd = Math.exp(scoreIndAll - scoreAll);
    //
    //        return pInd;
    //	}
    private void splitData(DataSet data_xy, DataSet data_rest, int[] parents){
        int sampleSize = data.getNumRows();

        Set<Integer> rows_is = new HashSet<>();
        Set<Integer> rows_res = new HashSet<>();

        for (int i = 0; i < data.getNumRows(); i++){
            rows_res.add(i);
        }

//		for(IndependenceFact f : H_xy.keySet()){

        for (int i = 0; i < sampleSize; i++){
            int[] parentValuesTest = new int[parents.length];
            int[] parentValuesCase = new int[parents.length];

            for (int p = 0; p < parents.length ; p++){
                parentValuesTest[p] =  test_array[0][parents[p]];
                parentValuesCase[p] = data_array[i][parents[p]];
            }

            if (Arrays.equals(parentValuesCase, parentValuesTest)){
                rows_is.add(i);
                rows_res.remove(i);
            }
        }
//		}

        int[] is_array = new int[rows_is.size()];
        int c = 0;
        for(int row : rows_is) is_array[c++] = row;

        int[] res_array = new int[rows_res.size()];
        c = 0;
        for(int row : rows_res) res_array[c++] = row;

        Arrays.sort(is_array);
        Arrays.sort(res_array);

        data_xy.removeRows(res_array);
        data_rest.removeRows(is_array);
        //		System.out.println("data_xy: " + data_xy.getNumRows());
        //		System.out.println("data_rest: " + data_rest.getNumRows());

    }

    public Map<IndependenceFact, Double> groupbyXYI(Map<IndependenceFact, Double> H, Node x, Node y){
        Map<IndependenceFact, Double> H_xy = new HashMap<IndependenceFact, Double>();
        for (IndependenceFact k : H.keySet()){
            if ((k.getX().equals(x) && k.getY().equals(y) && k.getZ().size() > 0) ||(k.getX().equals(y) && k.getY().equals(x) && k.getZ().size() > 0)){
                H_xy.put(k, H.get(k));
            }
        }
        return H_xy;
    }
    public Map<IndependenceFact, Double> groupbyXYP(Map<IndependenceFact, Double> H, Node x, Node y){
        Map<IndependenceFact, Double> H_xy = new HashMap<IndependenceFact, Double>();
        for (IndependenceFact k : H.keySet()){
            if ((k.getX().equals(x) && k.getY().equals(y)) ||(k.getX().equals(y) && k.getY().equals(x))){
                H_xy.put(k, H.get(k));
            }
        }
        return H_xy;
    }

    public void splitDatabyXY(DataSet data, DataSet data_xy, DataSet data_rest, Map<IndependenceFact, Double> H_xy){

        int sampleSize = data.getNumRows();

        Set<Integer> rows_is = new HashSet<>();
        Set<Integer> rows_res = new HashSet<>();
        for (int i = 0; i < data.getNumRows(); i++){
            rows_res.add(i);
        }

        for(IndependenceFact f : H_xy.keySet()){
            Node[] z = f.getZ().toArray(new Node[f.getZ().size()]);
            int[] parents = new int[z.length];
            for (int i = 0; i < z.length; i++) {
                parents[i] = indices.get(z[i]);
            }

            for (int i = 0; i < sampleSize; i++){
                int[] parentValuesTest = new int[parents.length];
                int[] parentValuesCase = new int[parents.length];

                for (int p = 0; p < parents.length ; p++){
                    parentValuesTest[p] =  test_array[0][parents[p]];
                    parentValuesCase[p] = data_array[i][parents[p]];
                }

                if (Arrays.equals(parentValuesCase, parentValuesTest)){
                    rows_is.add(i);
                    rows_res.remove(i);
                }
            }
        }

        int[] is_array = new int[rows_is.size()];
        int c = 0;
        for(int row : rows_is) is_array[c++] = row;
        int[] res_array = new int[rows_res.size()];
        c = 0;
        for(int row : rows_res) res_array[c++] = row;
        Arrays.sort(is_array);
        Arrays.sort(res_array);
        data_xy.removeRows(res_array);
        data_rest.removeRows(is_array);
        //		System.out.println("data_xy: " + data_xy.getNumRows());
        //		System.out.println("data_rest: " + data_rest.getNumRows());

    }

    public double scoreDag(Graph dag, BDeuScoreWOprior bic_is) {

        double _score = 0.0;

        for (Node y : dag.getNodes()) {
            Set<Node> parents = new HashSet<>(dag.getParents(y));
            int parentIndices[] = new int[parents.size()];
            Iterator<Node> pi = parents.iterator();
            int count = 0;

            while (pi.hasNext()) {
                Node nextParent = pi.next();
                parentIndices[count++] = this.indices.get(nextParent);
            }

            int yIndex = this.indices.get(y);
            _score += bic_is.localScore(yIndex, parentIndices);
        }

        return _score;
    }

    /**
     * Takes ln(x) and ln(y) as input, and returns ln(x + y)
     *
     * @param lnX is natural log of x
     * @param lnY is natural log of y
     * @return natural log of x plus y
     */
    private static final int MININUM_EXPONENT = -1022;
    protected double lnXpluslnY(double lnX, double lnY) {
        double lnYminusLnX, temp;

        if (lnY > lnX) {
            temp = lnX;
            lnX = lnY;
            lnY = temp;
        }

        lnYminusLnX = lnY - lnX;

        if (lnYminusLnX < MININUM_EXPONENT) {
            return lnX;
        } else {
            return Math.log1p(Math.exp(lnYminusLnX)) + lnX;
        }
    }

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        return null;
    }

    @Override
    public List<Node> getVariables() {
        return nodes;
    }

    @Override
    public Node getVariable(String name) {
        for (Node node : nodes) {
            if (name.equals(node.getName())) return node;
        }

        return null;
    }

    @Override
    public List<String> getVariableNames() {
        List<String> names = new ArrayList<>();

        for (Node node : nodes) {
            names.add(node.getName());
        }
        return names;
    }

    @Override
    public double getAlpha() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAlpha(double alpha) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataModel getData() {
        return data;
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

    /**
     * A map from independence facts to their probabilities of independence.
     */
    public Map<IndependenceFact, Double> getH() {
        return new HashMap<>(H);
    }

    public double getPosterior() {
        return posterior;
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * @param noRandomizedGeneratingConstraints
     */
    public void setThreshold(boolean noRandomizedGeneratingConstraints) {
        this.threshold = noRandomizedGeneratingConstraints;
    }

    public void setCutoff(double cutoff) {
        this.cutoff = cutoff;
    }

    public void setH(Map<IndependenceFact, Double> h) {
        H = h;
    }
}