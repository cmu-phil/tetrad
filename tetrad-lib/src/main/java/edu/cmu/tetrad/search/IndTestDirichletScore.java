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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;

import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Checks conditional independence of variable in a continuous data set using Fisher's Z test. See Spirtes, Glymour, and
 * Scheines, "Causation, Prediction and Search," 2nd edition, page 94.
 *
 * @author Joseph Ramsey
 * @author Frank Wimberly adapted IndTestCramerT for Fisher's Z
 */
public final class IndTestDirichletScore implements IndependenceTest {

    /**
     * The covariance matrix.
     */
    private ICovarianceMatrix covMatrix;

    /**
     * The variables of the covariance matrix, in order. (Unmodifiable list.)
     */
    private List<Node> variables;

    /**
     * Formats as 0.0000.
     */
    private static NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    /**
     * Stores a reference to the dataset being analyzed.
     */
    private DataSet dataSet;

    private PrintStream pValueLogger;
    private Map<Node, Integer> indexMap;
    private Map<String, Node> nameMap;
    private boolean verbose = true;

    private double bump;
    private DirichletScore score;
    private double samplePrior = 1;
    private double structurePrior = 1;

    // Legacy
    private double alpha;


    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new Independence test which checks independence facts based on the correlation matrix implied by the
     * given data set (must be continuous). The given significance level is used.
     *
     * @param dataSet A data set containing only continuous columns.
     */
    public IndTestDirichletScore(DataSet dataSet, double samplePrior, double structurePrior) {
        if (!(dataSet.isDiscrete())) {
            throw new IllegalArgumentException("Data set must be discrete.");
        }
        this.dataSet = dataSet;
        this.samplePrior = samplePrior;
        this.structurePrior = structurePrior;
        this.score = new DirichletScore(dataSet);
        this.score.setSamplePrior(samplePrior);
        this.score.setStructurePrior(structurePrior);
        this.variables = this.score.getVariables();
    }

    //==========================PUBLIC METHODS=============================//

    /**
     * Creates a new independence test instance for a subset of the variables.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        if (vars.isEmpty()) {
            throw new IllegalArgumentException("Subset may not be empty.");
        }

        for (Node var : vars) {
            if (!variables.contains(var)) {
                throw new IllegalArgumentException(
                        "All vars must be original vars");
            }
        }

        int[] indices = new int[vars.size()];

        for (int i = 0; i < indices.length; i++) {
            indices[i] = indexMap.get(vars.get(i));
        }

        DataSet newDataSet = dataSet.subsetColumns(indices);
        return new IndTestDirichletScore(newDataSet, getSamplePrior(), getStructurePrior());
    }

    /**
     * Determines whether variable x is independent of variable y given a list of conditioning variables z.
     *
     * @param x the one variable being compared.
     * @param y the second variable being compared.
     * @param z the list of conditioning variables.
     * @return true iff x _||_ y | z.
     * @throws RuntimeException if a matrix singularity is encountered.
     */
    public boolean isIndependent(Node x, Node y, List<Node> z) {
        double v = -this.score.localScoreDiff(variables.indexOf(x), variables.indexOf(y), varIndices(z));
        this.bump = v;
        return v > 0;
    }

    private int[] varIndices(List<Node> z) {
        int[] indices = new int[z.size()];

        for (int i = 0; i < z.size(); i++) {
            indices[i] = variables.indexOf(z.get(i));
        }

        return indices;
    }

    public boolean isIndependent(Node x, Node y, Node... z) {
        return isIndependent(x, y, Arrays.asList(z));
    }

    public boolean isDependent(Node x, Node y, List<Node> z) {
        return !isIndependent(x, y, z);
    }

    public boolean isDependent(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return isDependent(x, y, zList);
    }

    /**
     * @return the probability associated with the most recently computed independence test.
     */
    public double getPValue() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the list of variables over which this independence checker is capable of determinine independence
     * relations-- that is, all the variables in the given graph or the given data set.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * @return the variable with the given name.
     */
    public Node getVariable(String name) {
        return nameMap.get(name);
    }

    /**
     * @return the list of variable varNames.
     */
    public List<String> getVariableNames() {
        List<Node> variables = getVariables();
        List<String> variableNames = new ArrayList<String>();
        for (Node variable1 : variables) {
            variableNames.add(variable1.getName());
        }
        return variableNames;
    }

    /**
     * If <code>isDeterminismAllowed()</code>, deters to IndTestFisherZD; otherwise throws
     * UnsupportedOperationException.
     */
    public boolean determines(List<Node> z, Node x) throws UnsupportedOperationException {
        int[] parents = new int[z.size()];

        for (int j = 0; j < parents.length; j++) {
            parents[j] = covMatrix.getVariables().indexOf(z.get(j));
        }

        int i = covMatrix.getVariables().indexOf(x);

        double variance = covMatrix.getValue(i, i);

        if (parents.length > 0) {

            // Regress z onto i, yielding regression coefficients b.
            TetradMatrix Czz = covMatrix.getSelection(parents, parents);
            TetradMatrix inverse;

            try {
                inverse = Czz.inverse();
            } catch (Exception e) {
                return true;
            }

            TetradVector Cyz = covMatrix.getSelection(parents, new int[]{i}).getColumn(0);
            TetradVector b = inverse.times(Cyz);

            variance -= Cyz.dotProduct(b);
        }

        return variance < 1e-20;
    }

    @Override
    public double getAlpha() {
        return this.alpha;
    }

    @Override
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * @return the data set being analyzed.
     */
    public DataSet getData() {
        return dataSet;
    }

    /**
     * @return a string representation of this test.
     */
    public String toString() {
        return "Fisher's Z, alpha = " + nf.format(getAlpha());
    }

    //==========================PRIVATE METHODS============================//

    public void setVariables(List<Node> variables) {
        if (variables.size() != this.variables.size()) throw new IllegalArgumentException("Wrong # of variables.");
        this.variables = new ArrayList<Node>(variables);
        covMatrix.setVariables(variables);
    }

    public ICovarianceMatrix getCov() {
        return covMatrix;
    }

    @Override
    public List<DataSet> getDataSets() {

        List<DataSet> dataSets = new ArrayList<DataSet>();

        dataSets.add(dataSet);

        return dataSets;
    }

    @Override
    public int getSampleSize() {
        return covMatrix.getSampleSize();
    }

    @Override
    public List<TetradMatrix> getCovMatrices() {
        return null;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public double getScore() {
        return this.bump;
    }

    public double getSamplePrior() {
        return samplePrior;
    }

    public void setSamplePrior(double samplePrior) {
        this.samplePrior = samplePrior;
    }

    public double getStructurePrior() {
        return structurePrior;
    }

    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }
}




