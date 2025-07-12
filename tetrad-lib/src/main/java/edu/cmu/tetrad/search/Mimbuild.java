/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Matrix;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides an implementation of Mimbuild, an algorithm that takes a clustering of variables, each of which is explained
 * by a single latent, then forms the implied covariance matrix over the latent variables, then runs a CPDAG search to
 * in the structure over the latent themselves.
 * <p>
 * Specifically, the search will first infer the covariance matrix over the latents and then will use the BOSS algorithm
 * (see) to infer the structure graph over the latents, using the SEM Bic score with the given penalty discount (default
 * 2).
 * <p>
 * One may wish to obtain the implied correlation matrix over the latents and run one's own choice of CPDAG algorithm on
 * it with one's own test or score; a method is available to return this covariance matrix.
 * <p>
 * A suitable clustering for Mimbuild may be obtained using the BPC or FOFC algorithm (see).
 * <p>
 * This algorithm is described in Spirtes et al., Causation, Prediction, and Search.
 * <p>
 * This class is configured to respect the knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see Bpc
 * @see Fofc
 * @see #getLatentsCov()
 * @see Boss
 * @see Knowledge
 */
public class Mimbuild {
    /**
     * The clustering from BPC or equivalent. Small clusters are removed.
     */
    private List<List<Node>> clustering;
    /**
     * The graph over the latents.
     */
    private Graph structureGraph;
    /**
     * The covariance matrix over the latent variables.
     */
    private ICovarianceMatrix latentsCov;
    /**
     * The minimum function (Fgsl) value achieved.
     */
    private double minimum;
    /**
     * The p value of the optimization.
     */
    private double pValue;
    /**
     * The latents.
     */
    private List<Node> latents;
    /**
     * The penalty discount of the score used to infer the structure graph.
     */
    private double penaltyDiscount = 1;

    /**
     * Constructs a new Mimbuild search.
     */
    public Mimbuild() {
    }

    /**
     * Conducts a search to infer a graph over latent variables based on the provided clustering, measure names, latent
     * variable names, and measures covariance.
     *
     * @param clustering   An array where each subarray represents a cluster of measured variables, with each index
     *                     corresponding to the position of a measure in the measureNames array. Each cluster is assumed
     *                     to be explained by a single latent variable. The clusters must be disjoint.
     * @param measureNames An array of names corresponding to the measured variables.
     * @param latentNames  An array of names for the latent variables, where each name corresponds to a cluster in the
     *                     clustering parameter.
     * @param measuresCov  A two-dimensional double array representing the covariance matrix over the measured
     *                     variables.
     * @return A graph inferred over the latent variables, depicting relationships among measured and latent variables.
     * @throws InterruptedException     If the search process is interrupted.
     * @throws IllegalArgumentException If the clustering contains invalid indices or overlapping clusters.
     * @throws NullPointerException     If any of the arguments are null.
     */
    public Graph search(int[][] clustering, String[] measureNames, String[] latentNames, double[][] measuresCov) throws InterruptedException {

        // Check nullity.
        if (clustering == null || measureNames == null || latentNames == null || measuresCov == null) {
            throw new NullPointerException("Null arguments are not allowed.");
        }

        // Make sure the clustering is valid.
        for (int[] cluster : clustering) {
            for (int i : cluster) {
                if (i < 0 || i >= measureNames.length) {
                    throw new IllegalArgumentException("Cluster index outside range of measure names: " + i);
                }
            }
        }

        // Make sure the clusters don't overlap.
        for (int i = 0; i < clustering.length; i++) {
            for (int j = i + 1; j < clustering.length; j++) {
                for (int k : clustering[i]) {
                    for (int l : clustering[j]) {
                        if (k == l) {
                            throw new IllegalArgumentException("Clusters overlap at index: " + k);
                        }
                    }
                }
            }
        }

        // Convert the measure names to a list of nodes.
        List<Node> measuredNodes = new ArrayList<>();

        for (String name : measureNames) {
            measuredNodes.add(new GraphNode(name));
        }

        // Convert the clustering to a list over lists of measure nodes.
        List<List<Node>> clusteringList = new ArrayList<>();

        for (int[] cluster : clustering) {
            List<Node> clusterList = new ArrayList<>();

            for (int i : cluster) {
                clusterList.add(measuredNodes.get(i));
            }

            clusteringList.add(clusterList);
        }

        // Convert the measure covariance matrix to a CovarianceMatrix.
        if (measuresCov.length != measuresCov[0].length) {
            throw new IllegalArgumentException("Measures covariance matrix must be square.");
        }

        if (measuresCov.length != measuredNodes.size()) {
            throw new IllegalArgumentException("Measures covariance matrix must have the same number of rows as measure names.");
        }

        CovarianceMatrix measuresCovMatrix = new CovarianceMatrix(measuredNodes, new Matrix(measuresCov), measuresCov.length);

        // Convert the latent names to a list of string.
        List<String> latentNodes = new ArrayList<>();
        Collections.addAll(latentNodes, latentNames);

        // Run the search.
        return search(clusteringList, latentNodes, measuresCovMatrix);
    }

    /**
     * Does a Mimbuild search.
     *
     * @param clustering  The clustering to use--this clusters the measured variables in such a way that each cluster is
     *                    explained by a single latent variables. The clusters must be disjoint.
     * @param latentNames The names of the latent variables corresponding in order ot each cluster in the clustering.
     *                    These must be unique.
     * @param measurescov The covariance matrix over the measured variables.
     * @return The inferred structure graph over the latent variables.
     * @throws InterruptedException If the search is interrupted.
     */
    public Graph search(List<List<Node>> clustering, List<String> latentNames, ICovarianceMatrix
            measurescov) throws InterruptedException {

        // Check nullity.
        if (clustering == null || latentNames == null || measurescov == null) {
            throw new NullPointerException("Null arguments are not allowed.");
        }

        // Make sure the clusters are disjoint.
        for (List<Node> cluster1 : clustering) {
            for (List<Node> cluster2 : clustering) {
                if (cluster1 != cluster2) {
                    for (Node node : cluster1) {
                        if (cluster2.contains(node)) {
                            throw new IllegalArgumentException("Clusters must be disjoint.");
                        }
                    }
                }
            }
        }

        // Make sure the latent names are distinct.
        for (int i = 0; i < latentNames.size(); i++) {
            for (int j = i + 1; j < latentNames.size(); j++) {
                if (latentNames.get(i).equals(latentNames.get(j))) {
                    throw new IllegalArgumentException("Latent names must be distinct.");
                }
            }
        }

        // Sort each cluster
        for (List<Node> cluster : clustering) {
            Collections.sort(cluster);
        }

        // Grab the names of the clustered measures.
        List<String> clusteredVarNames = new ArrayList<>();

        for (List<Node> cluster : clustering) {
            for (Node node : cluster) clusteredVarNames.add(node.getName());
        }

        // Make the covariance matrix over the clustered measures.
        measurescov = measurescov.getSubmatrix(clusteredVarNames);

        // Grab the variables for the clusters.
        List<List<Node>> _clustering = new ArrayList<>();

        for (List<Node> cluster : clustering) {
            List<Node> _cluster = new ArrayList<>();

            for (Node node : cluster) {
                _cluster.add(measurescov.getVariable(node.getName()));
            }

            _clustering.add(_cluster);
        }

        this.clustering = _clustering;

        // Define the latents.
        List<Node> latents = defineLatents(latentNames);
        this.latents = latents;

        // Define the indicators.
        Node[][] indicators = new Node[latents.size()][];

        for (int i = 0; i < _clustering.size(); i++) {
            indicators[i] = new Node[_clustering.get(i).size()];

            for (int j = 0; j < _clustering.get(i).size(); j++) {
                indicators[i][j] = _clustering.get(i).get(j);
            }
        }

        // Calculate the covariances over the latents by calculating the covariances over the measures and then
        // estimating the covariances over the latents.
        Matrix latentcov = getCov(measurescov, latents, indicators);

        // Check for nans in latentcov.
        for (int i = 0; i < latentcov.getNumRows(); i++) {
            for (int j = 0; j < latentcov.getNumColumns(); j++) {
                if (Double.isNaN(latentcov.get(i, j))) {
                    throw new IllegalArgumentException("NaN in latentcov search A.");
                }
            }
        }

        // Check if there are any zeros on the diagonal in latentcov.
        for (int i = 0; i < latentcov.getNumRows(); i++) {
            if (latentcov.get(i, i) <= 0) {
                throw new IllegalArgumentException("Diagonal element of latentcov is <= 0.");
            }
        }

        ICovarianceMatrix latentscov = new CovarianceMatrix(latents, latentcov, measurescov.getSampleSize());
        this.latentsCov = latentscov;

        try {
            SemBicScore score = new SemBicScore(latentscov);
            score.setPenaltyDiscount(this.penaltyDiscount);
            PermutationSearch search = new PermutationSearch(new Boss(score));

            Graph graph = search.search();
            this.structureGraph = new EdgeListGraph(graph);
            LayoutUtil.fruchtermanReingoldLayout(this.structureGraph);

            return this.structureGraph;
        } catch (NullPointerException e) {
            throw new RuntimeException("Mimbuild could not find a graph over the latents; perhaps that was not a pure model.", e);
        }
    }

    /**
     * Returns the clustering of measured variables, each of which is explained by a single latent.
     *
     * @return This clustering.
     */
    public List<List<Node>> getClustering() {
        return this.clustering;
    }

    /**
     * Returns the inferred covariance matrix over the latent variables.
     *
     * @return This covariance matrix.
     */
    public ICovarianceMatrix getLatentsCov() {
        return this.latentsCov;
    }

    /**
     * <p>Getter for the field <code>minimum</code>.</p>
     *
     * @return The minimum function (Fgsl) value achieved.
     */
    public double getMinimum() {
        return this.minimum;
    }

    /**
     * Returns the p-value associated with the resulting statistical test or computation. The p-value helps to determine
     * the statistical significance of the result.
     *
     * @return The p-value as a double.
     */
    public double getPValue() {
        return this.pValue;
    }

    /**
     * The full graph inferred, including the edges from latents to measures. And all fo the edges inferred among
     * latents.
     *
     * @param includeNodes The nodes to include.
     * @return This full graph.
     */
    public Graph getFullGraph(List<Node> includeNodes) {
        Graph graph = new EdgeListGraph(this.structureGraph);

        for (int i = 0; i < this.latents.size(); i++) {
            Node latent = this.latents.get(i);
            List<Node> measuredGuys = getClustering().get(i);

            for (Node measured : measuredGuys) {
                if (!graph.containsNode(measured)) {
                    graph.addNode(measured);
                }

                graph.addDirectedEdge(latent, measured);
            }
        }

        // These should not be included, as they mess with the counts for degrees of freedom.
//        for (Node node : includeNodes) {
//            if (graph.getNode(node.getName()) == null) {
//                graph.addNode(new GraphNode(node.getName()));
//            }
//        }

        LayoutUtil.fruchtermanReingoldLayout(graph);

        return graph;
    }

    /**
     * Sets the penalty discount of the score used to infer the structure graph.
     *
     * @param penaltyDiscount The penalty discount.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }


    private List<Node> defineLatents(List<String> names) {
        List<Node> latents = new ArrayList<>();

        for (String name : names) {
            Node node = new GraphNode(name);
            node.setNodeType(NodeType.LATENT);
            latents.add(node);
        }

        return latents;
    }

    private Matrix getCov(ICovarianceMatrix _measurescov, List<Node> latents, Node[][] indicators) {
        if (latents.size() != indicators.length) {
            throw new IllegalArgumentException();
        }

        Matrix measurescov = _measurescov.getMatrix();
        Matrix latentscov = new Matrix(latents.size(), latents.size());

        for (int i = 0; i < latentscov.getNumRows(); i++) {
            for (int j = i; j < latentscov.getNumColumns(); j++) {
                if (i == j) latentscov.set(i, j, 1.0);
                else {
                    latentscov.set(i, j, 0);
                    latentscov.set(j, i, 0);
                }
            }
        }

        double[][] loadings = new double[latents.size()][];

        for (int i = 0; i < indicators.length; i++) {
            loadings[i] = new double[indicators[i].length];

            for (int j = 0; j < indicators[i].length; j++) {
                loadings[i][j] = 1;
            }
        }

        int[][] indicatorIndices = new int[indicators.length][];
        List<Node> measures = _measurescov.getVariables();

        for (int i = 0; i < indicators.length; i++) {
            indicatorIndices[i] = new int[indicators[i].length];

            for (int j = 0; j < indicators[i].length; j++) {
                indicatorIndices[i][j] = measures.indexOf(clustering.get(i).get(j));
            }
        }

        // Variances of the measures.
        double[] delta = new double[measurescov.getNumRows()];

        for (int i = 0; i < delta.length; i++) {
            delta[i] = measurescov.get(i, i);
        }

        int numParams = optimizeAllParamsSimultaneously(indicators, measurescov, latentscov, loadings, indicatorIndices, delta);

        double N = _measurescov.getSampleSize();
        int p = _measurescov.getDimension();

        int df = (p) * (p + 1) / 2 - numParams;
        double x = (N - 1) * this.minimum;

        if (df < 1)
            throw new IllegalStateException(
                    """
                            Mimbuild error: The degrees of freedom for this model ((m * (m + 1) / 2) - # estimation params)\
                            
                            was calculated to be less than 1. Perhaps the model is not a multiple indicator model \
                            
                            or doesn't have enough pure nmeasurements to do a proper estimation.""");

        ChiSquaredDistribution chisq = new ChiSquaredDistribution(df);

        double _p;

        if (Double.isInfinite(x)) {
            _p = 0.0;
        } else if (x == 0.0) {
            _p = 1.0;
        } else {
            _p = 1.0 - chisq.cumulativeProbability(x);
        }

        this.pValue = _p;
        return latentscov;
    }

//    private void optimizeNonMeasureVariancesQuick(Matrix latentscov, double[][] loadings, Node[][] indicators, Matrix measurescov,
//                                                  int[][] indicatorIndices) {
//        int count = 0;
//
//        for (int i = 0; i < latentscov.getNumRows(); i++) {
//            for (int j = i; j < latentscov.getNumRows(); j++) {
//                if (i == j) {
//                    if (latentscov.get(i, j) <= 0) {
//                        throw new IllegalArgumentException("Diagonal element of latentcov is <= 0.");
//                    }
//                }
//
//                count++;
//            }
//        }
//
//        for (double[] loading : loadings) {
//            for (int j = 0; j < loading.length; j++) {
//                count++;
//            }
//        }
//
//        double[] values = new double[count];
//        count = 0;
//
//        for (int i = 0; i < indicators.length; i++) {
//            for (int j = i; j < indicators.length; j++) {
//                values[count++] = latentscov.get(i, j);
//            }
//        }
//
//        for (int i = 0; i < indicators.length; i++) {
//            for (int j = 0; j < indicators[i].length; j++) {
//                values[count++] = loadings[i][j];
//            }
//        }
//
//        Function1 function1 = new Function1(latentscov, loadings, indicatorIndices, measurescov);
//        MultivariateOptimizer search = new PowellOptimizer(1e-5, 1e-5);
//
//        PointValuePair pair = search.optimize(
//                new InitialGuess(values),
//                new ObjectiveFunction(function1),
//                GoalType.MINIMIZE,
//                new MaxEval(100000));
//
//        this.minimum = pair.getValue();
//    }

    private int optimizeAllParamsSimultaneously(Node[][] indicators, Matrix measurescov,
                                                Matrix latentscov, double[][] loadings,
                                                int[][] indicatorIndices, double[] delta) {
        double[] values = getAllParams(indicators, latentscov, loadings, delta);

        Function2 function = new Function2(latentscov, loadings, delta, indicatorIndices, measurescov);
        MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);

        PointValuePair pair = search.optimize(
                new InitialGuess(values),
                new ObjectiveFunction(function),
                GoalType.MINIMIZE,
                new MaxEval(100000));

        this.minimum = pair.getValue();
        return values.length;
    }

    private double[] getAllParams(Node[][] indicators, Matrix latentscov, double[][] loadings, double[] delta) {
        int count = 0;

        for (int i = 0; i < latentscov.getNumRows(); i++) {
            for (int j = i; j < latentscov.getNumColumns(); j++) {
                if (i == j) {
                    if (latentscov.get(i, j) <= 0) {
                        throw new IllegalArgumentException("Diagonal element of latentcov is <= 0.");
                    }
                }

                count++;
            }
        }

        // Loadings
        for (Node[] indicator : indicators) {
            for (int j = 0; j < indicator.length; j++) {
                count++;
            }
        }

        // Variance of measures
        for (int i = 0; i < delta.length; i++) {
            count++;
        }

        double[] values = new double[count];
        count = 0;

        for (int i = 0; i < latentscov.getNumRows(); i++) {
            for (int j = i; j < latentscov.getNumRows(); j++) {
                values[count++] = latentscov.get(i, j);
            }
        }

        for (int i = 0; i < indicators.length; i++) {
            for (int j = 0; j < indicators[i].length; j++) {
                values[count++] = loadings[i][j];
            }
        }

        for (double v : delta) {
            values[count++] = v;
        }

        return values;
    }

    private Matrix impliedCovariance(int[][] indicatorIndices, double[][] loadings, Matrix cov, Matrix loadingscov,
                                     double[] delta) {
        Matrix implied = new Matrix(cov.getNumRows(), cov.getNumColumns());

        for (int i = 0; i < loadings.length; i++) {
            for (int j = 0; j < loadings.length; j++) {
                for (int k = 0; k < loadings[i].length; k++) {
                    for (int l = 0; l < loadings[j].length; l++) {
                        double prod = loadings[i][k] * loadings[j][l] * loadingscov.get(i, j);
                        implied.set(indicatorIndices[i][k], indicatorIndices[j][l], prod);
                    }
                }
            }
        }

        for (int i = 0; i < implied.getNumRows(); i++) {
            implied.set(i, i, implied.get(i, i) + delta[i]);
        }

        return implied;
    }

    private double sumOfDifferences(Matrix latentscov, double[][] loadings, int[][] indicatorIndices, Matrix measurescov) {
        double sum = 0;

        for (int i = 0; i < loadings.length; i++) {
            for (int k = 0; k < loadings[i].length; k++) {
                for (int l = k + 1; l < loadings[i].length; l++) {
                    double _cov = measurescov.get(indicatorIndices[i][k], indicatorIndices[i][l]);
                    double prod = loadings[i][k] * loadings[i][l] * latentscov.get(i, i);
                    double diff = _cov - prod;
                    sum += diff * diff;
                }
            }
        }

        for (int i = 0; i < loadings.length; i++) {
            for (int j = i + 1; j < loadings.length; j++) {
                for (int k = 0; k < loadings[i].length; k++) {
                    for (int l = 0; l < loadings[j].length; l++) {
                        double _cov = measurescov.get(indicatorIndices[i][k], indicatorIndices[j][l]);
                        double prod = loadings[i][k] * loadings[j][l] * latentscov.get(i, j);
                        double diff = _cov - prod;
                        sum += 2 * diff * diff;
                    }
                }
            }
        }

        return sum;
    }

    private class Function1 implements org.apache.commons.math3.analysis.MultivariateFunction {
        private final Matrix latentscov;
        private final double[][] loadings;
        private final int[][] indicatorIndices;
        private final Matrix measurescov;

        public Function1(Matrix latentscov, double[][] loadings, int[][] indicatorIndices, Matrix measurescov) {
            this.loadings = loadings;
            this.latentscov = latentscov;
            this.indicatorIndices = indicatorIndices;
            this.measurescov = measurescov;
        }

        @Override
        public double value(double[] values) {
            for (double value : values) {
                if (Double.isNaN(value)) {
                    return Double.POSITIVE_INFINITY;
                }
            }

            // We need to make sure these variances are always >= 0.
            int count = 0;

            for (int i = 0; i < this.latentscov.getNumRows(); i++) {
                for (int j = i; j < this.latentscov.getNumColumns(); j++) {
                    if (i == j) {
                        if (values[count] <= 0) {
                            return Double.POSITIVE_INFINITY;
                        }
                    }
                    count++;
                }
            }

            count = 0;

            for (int i = 0; i < this.latentscov.getNumRows(); i++) {
                for (int j = i; j < this.latentscov.getNumColumns(); j++) {
                    this.latentscov.set(i, j, values[count]);
                    this.latentscov.set(j, i, values[count]);
                    count++;
                }
            }

            for (int i = 0; i < this.loadings.length; i++) {
                for (int j = 0; j < this.loadings[i].length; j++) {
                    this.loadings[i][j] = values[count];
                    count++;
                }
            }

            return sumOfDifferences(this.latentscov, this.loadings, this.indicatorIndices, this.measurescov);
        }
    }

    private class Function2 implements org.apache.commons.math3.analysis.MultivariateFunction {
        private final Matrix latentscov;
        private final double[][] loadings;
        private final double[] delta;
        private final int[][] indicatorIndices;
        private final Matrix measurescov;
        private final Matrix measuresCovInverse;

        public Function2(Matrix latentscov, double[][] loadings, double[] delta, int[][] indicatorIndices, Matrix measurescov) {
            this.indicatorIndices = indicatorIndices;
            this.measurescov = measurescov;
            this.loadings = loadings;
            this.latentscov = latentscov;
            this.delta = delta;
            this.measuresCovInverse = measurescov.inverse();
        }

        @Override
        public double value(double[] values) {

            // We need to make sure these variances are always >= 0.
            int _count = 0;

            for (int i = 0; i < this.latentscov.getNumRows(); i++) {
                for (int j = i; j < this.latentscov.getNumColumns(); j++) {
                    if (i == j) {
                        if (values[_count] <= 0) {
                            return Double.POSITIVE_INFINITY;
                        }
                    }
                    _count++;
                }
            }

            int count = 0;

            for (int i = 0; i < this.latentscov.getNumRows(); i++) {
                for (int j = i; j < this.latentscov.getNumColumns(); j++) {
                    this.latentscov.set(i, j, values[count]);
                    this.latentscov.set(j, i, values[count]);
                    count++;
                }
            }

            for (int i = 0; i < this.loadings.length; i++) {
                for (int j = 0; j < this.loadings[i].length; j++) {
                    this.loadings[i][j] = values[count];
                    count++;
                }
            }

            for (int i = 0; i < this.delta.length; i++) {
                this.delta[i] = values[count];
                count++;
            }

            Matrix implied = impliedCovariance(this.indicatorIndices, this.loadings, this.measurescov, this.latentscov, this.delta);

            Matrix I = Matrix.identity(implied.getNumRows());
            Matrix diff = I.minus((implied.times(this.measuresCovInverse)));

            return -0.5 * (diff.times(diff)).trace();
        }
    }
}



