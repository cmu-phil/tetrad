///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.utils.EstimateRank;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Vector;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks d-separations in a structural model using t-separations over indicators.
 *
 * @author Adam Brodie
 * @version $Id: $Id
 */
public final class IndTestTrekSep implements IndependenceTest {
    /**
     * The variables of the covariance matrix, in order. (Unmodifiable list.)
     */
    private static final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    /**
     * The covariance matrix.
     */
    private final ICovarianceMatrix covMatrix;
    /**
     * The latents in order. (Unmodifiable list.)
     */
    private final List<Node> latents;
    /**
     * The variables clusterings.
     */
    private final List<List<Node>> clustering;
    /**
     * A hash of nodes to indices.
     */
    private final Map<Node, Integer> indexMap;
    /**
     * A hash of nodes to names.
     */
    private final Map<String, Node> nameMap;
    /**
     * A cache of results for independence facts.
     */
    private final Map<IndependenceFact, IndependenceResult> facts = new ConcurrentHashMap<>();
    /**
     * True if verbose output should be printed.
     */
    private boolean verbose;
    /**
     * The variables of the covariance matrix, in order. (Unmodifiable list.)
     */
    private List<Node> variables;
    /**
     * The significance level of the independence tests.
     */
    private double alpha;
    private int effectiveSampleSize;

    /**
     * Constructs a new independence test that will determine conditional independence facts using the given correlation
     * matrix and the given significance level.
     *
     * @param covMatrix  The covariance over the measures.
     * @param alpha      The significance level.
     * @param clustering The clustering of the measured variables. In each cluster, all measured variable in the cluster
     *                   are explained by a single latent.
     * @param latents    The list of latent variables for the clusters, in order.
     */
    public IndTestTrekSep(ICovarianceMatrix covMatrix, double alpha, List<List<Node>> clustering, List<Node> latents) {
        this.clustering = clustering;
        this.covMatrix = covMatrix;
        this.variables = Collections.unmodifiableList(covMatrix.getVariables());
        this.indexMap = indexMap(this.variables, latents);
        this.nameMap = nameMap(this.variables);
        this.latents = latents;
        setAlpha(alpha);
    }


    /**
     * Determines independence between variables in a given subset.
     *
     * @param vars The sublist of variables to test for independence.
     * @return An IndependenceTest object representing the result of the independence test.
     * @throws IllegalArgumentException If the subset of variables is empty or contains variables that are not part of
     *                                  the original variables.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        if (vars.isEmpty()) {
            throw new IllegalArgumentException("Subset may not be empty.");
        }

        for (Node var : vars) {
            if (!this.variables.contains(var)) {
                throw new IllegalArgumentException(
                        "All vars must be original vars");
            }
        }

        int[] indices = new int[vars.size()];

        for (int i = 0; i < indices.length; i++) {
            indices[i] = this.indexMap.get(vars.get(i));
        }

        ICovarianceMatrix newCovMatrix = this.covMatrix.getSubmatrix(indices);

        double alphaNew = getAlpha();
        return new IndTestTrekSep(newCovMatrix, alphaNew, this.clustering, this.latents);
    }

    /**
     * Determines independence between variables x and y, given the set of variables z.
     *
     * @param x The first variable.
     * @param y The second variable.
     * @param z The set of variables.
     * @return An IndependenceResult object representing the result of the independence test.
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        if (facts.containsKey(new IndependenceFact(x, y, z))) {
            return facts.get(new IndependenceFact(x, y, z));
        }

        int n = sampleSize();
        int xi = this.latents.indexOf(x);
        int yi = this.latents.indexOf(y);
        int nA = this.clustering.get(xi).size();
        int nB = this.clustering.get(yi).size();

        for (Node node : z) {
            int s = this.latents.indexOf(node);
            int m = this.clustering.get(s).size() / 2;
            nA += m;
            nB += m;
        }

        int[] A = new int[nA];
        int[] B = new int[nB];
        int a = 0;
        int b = 0;

        for (int i = 0; i < this.clustering.get(xi).size(); i++) {
            A[i] = this.variables.indexOf(this.clustering.get(xi).get(i));
            a++;
        }

        for (int i = 0; i < this.clustering.get(yi).size(); i++) {
            B[i] = this.variables.indexOf(this.clustering.get(yi).get(i));
            b++;
        }

        for (Node node : z) {
            int s = this.latents.indexOf(node);
            int m = this.clustering.get(s).size() / 2;
            for (int j = 1; j <= m; j++) {
                A[a] = this.variables.indexOf(this.clustering.get(s).get(j - 1));
                a++;
                B[b] = this.variables.indexOf(this.clustering.get(s).get(m + j - 1));
                b++;
            }
        }

        //With one indicator per latent per set.
        double[][] CovMatrix = this.covMatrix.getMatrix().toArray();
        int rank = EstimateRank.estimate(A, B, CovMatrix, n, this.alpha);
        boolean independent = rank <= z.size();
        final IndependenceResult result = new IndependenceResult(new IndependenceFact(x, y, z), independent, Double.NaN, Double.NaN);
        facts.put(new IndependenceFact(x, y, z), result);
        return result;

    }

    /**
     * Gets the model significance level.
     *
     * @return This alpha.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level for the independence test.
     *
     * @param alpha The significance level. Must be between 0.0 and 1.0 (inclusive).
     * @throws IllegalArgumentException If the significance level is out of range.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range.");
        }

        this.alpha = alpha;
    }

    /**
     * Returns the list of variables over which this independence checker is capable of determinine independence
     * relations-- that is, all the variables in the given graph or the given data set.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return this.latents;
    }

    /**
     * Sets the varialbe to this list (of the same length). Useful is multiple test are used that need the same
     * object-identical lists of variables.
     *
     * @param variables This list.
     */
    public void setVariables(List<Node> variables) {
        if (variables.size() != this.variables.size()) throw new IllegalArgumentException("Wrong # of variables.");
        this.variables = new ArrayList<>(variables);
        this.covMatrix.setVariables(variables);
    }

    /**
     * Gets the variable with the given name.
     *
     * @param name The name of the variable to get.
     * @return The Node object representing the variable.
     */
    public Node getVariable(String name) {
        return this.nameMap.get(name);
    }

    /**
     * Determines the independence between a set of variables z and a variable x.
     *
     * @param z The set of variables to determine independence with x.
     * @param x The variable to determine independence with z.
     * @return true if the variable x is conditionally independent from the set of variables z, false otherwise.
     * @throws UnsupportedOperationException if the covariance matrix is singular or not invertible.
     */
    public boolean determines(List<Node> z, Node x) throws UnsupportedOperationException {
        int[] parents = new int[z.size()];

        for (int j = 0; j < parents.length; j++) {
            parents[j] = this.covMatrix.getVariables().indexOf(z.get(j));
        }

        int i = this.covMatrix.getVariables().indexOf(x);

        Matrix matrix2D = this.covMatrix.getMatrix();
        double variance = matrix2D.get(i, i);

        if (parents.length > 0) {

            // Regress z onto i, yielding regression coefficients b.
            Matrix Czz = matrix2D.view(parents, parents).mat();
            Matrix inverse;

            try {
                inverse = Czz.inverse();
            } catch (Exception e) {
                return true;
            }

            Vector Cyz = matrix2D.getColumn(i);
            Cyz = Cyz.getSelection(parents);
            Vector b = inverse.times(Cyz);

            variance -= Cyz.dotProduct(b);
        }

        return variance < 1e-20;
    }

    /**
     * Gets the data set used for the independence test.
     *
     * @return The data set used for the independence test.
     */
    public DataSet getData() {
        throw new UnsupportedOperationException("Dataset not available.");
    }

    /**
     * Returns a string representation of this test.
     *
     * @return This string.
     */
    public String toString() {
        return "t-Separation test, alpha = " + IndTestTrekSep.nf.format(getAlpha());
    }

    /**
     * Returns the covariance matrix.
     *
     * @return This matrix.
     */
    public ICovarianceMatrix getCov() {
        return this.covMatrix;
    }

    /**
     * Returns the data sets used for the independence test.
     *
     * @return The list of data sets used for the independence test.
     */
    @Override
    public List<DataSet> getDataSets() {
        throw new UnsupportedOperationException("Dataset not available.");
    }

    @Override
    public void setEffectiveSampleSize(int effectiveSampleSize) {
        this.effectiveSampleSize = effectiveSampleSize;
    }

    /**
     * Returns the sample size used in the covariance matrix.
     *
     * @return The sample size.
     */
    @Override
    public int getSampleSize() {
        return this.covMatrix.getSampleSize();
    }

    /**
     * Checks whether verbose output is enabled.
     *
     * @return true if verbose output is enabled, false otherwise.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets the verbose output flag.
     *
     * @param verbose True, if verbose output is enabled. False otherwise.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns the sample size used in the covariance matrix.
     *
     * @return The sample size.
     */
    private int sampleSize() {
        return covMatrix().getSampleSize();
    }

    /**
     * Returns the covariance matrix used in the independence test.
     *
     * @return The covariance matrix.
     */
    private ICovarianceMatrix covMatrix() {
        return this.covMatrix;
    }

    /**
     * Creates a map of variable names to Node objects.
     *
     * @param variables The list of Node objects representing variables.
     * @return A Map object that maps variable names to Node objects.
     */
    private Map<String, Node> nameMap(List<Node> variables) {
        Map<String, Node> nameMap = new ConcurrentHashMap<>();

        for (Node node : variables) {
            nameMap.put(node.getName(), node);
        }

        return nameMap;
    }

    /**
     * Constructs a map that maps each Node object from the "variables" and "latents" lists to an integer index.
     *
     * @param variables The list of Node objects representing variables.
     * @param latents   The list of Node objects representing latent variables.
     * @return A Map object that maps Node objects to their corresponding index.
     */
    private Map<Node, Integer> indexMap(List<Node> variables, List<Node> latents) {
        Map<Node, Integer> indexMap = new ConcurrentHashMap<>();

        int index = 0;

        for (Node variable : variables) {
            indexMap.put(variable, index++);
        }

        for (Node latent : latents) {
            indexMap.put(latent, index++);
        }

        return indexMap;
    }
}



