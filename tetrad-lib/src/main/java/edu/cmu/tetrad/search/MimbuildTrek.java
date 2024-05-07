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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndTestTrekSep;
import edu.cmu.tetrad.util.Matrix;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implements Mimbuild using the theory of treks and ranks.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author adambrodie
 * @version $Id: $Id
 * @see Knowledge
 * @see Mimbuild
 */
public class MimbuildTrek {
    /**
     * The clustering from BPC or equivalent. Small clusters are removed.
     */
    private List<List<Node>> clustering;
    /**
     * The graph over the latents.
     */
    private Graph structureGraph;
    /**
     * The alpha level used for CPC
     */
    private double alpha;
    /**
     * Background knowledge for CPC.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * The estimated covariance matrix over the latents.
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
     * The minimum cluster size.
     */
    private int minClusterSize = 3;

    /**
     * Empty constructor.
     */
    public MimbuildTrek() {
        alpha = 0.001;
    }

    /**
     * Does the search and returns the graph.
     *
     * @param clustering  A clustering of the variables, each of which is explained by a single latent.
     * @param latentNames The names of the latents, which cannot be known by the clustering algorithm.
     * @param measuresCov The covariance matrix over the measured variables, from the data.
     * @return A graph over the latents.
     */
    public Graph search(List<List<Node>> clustering, List<String> latentNames, ICovarianceMatrix measuresCov) {
        List<String> _latentNames = new ArrayList<>(latentNames);

        List<String> allVarNames = new ArrayList<>();

        for (List<Node> cluster : clustering) {
            for (Node node : cluster) allVarNames.add(node.getName());
        }

        measuresCov = measuresCov.getSubmatrix(allVarNames);

        List<List<Node>> _clustering = new ArrayList<>();

        for (List<Node> cluster : clustering) {
            List<Node> _cluster = new ArrayList<>();

            for (Node node : cluster) {
                _cluster.add(measuresCov.getVariable(node.getName()));
            }

            _clustering.add(_cluster);
        }

        List<Node> latents = defineLatents(_latentNames);
        this.latents = latents;

        // This removes the small clusters and their names.
        removeSmallClusters(latents, _clustering, getMinClusterSize());
        this.clustering = _clustering;

        Node[][] indicators = new Node[latents.size()][];

        for (int i = 0; i < latents.size(); i++) {
            indicators[i] = new Node[_clustering.get(i).size()];

            for (int j = 0; j < _clustering.get(i).size(); j++) {
                indicators[i][j] = _clustering.get(i).get(j);
            }
        }

        Matrix cov = getCov(measuresCov, latents, indicators);
        this.latentsCov = new CovarianceMatrix(latents, cov, measuresCov.getSampleSize());
        Graph graph;

        Cpc search = new Cpc(new IndTestTrekSep(measuresCov, this.alpha, clustering, latents));
        search.setKnowledge(this.knowledge);
        graph = search.search();

        this.structureGraph = new EdgeListGraph(graph);
        LayoutUtil.fruchtermanReingoldLayout(this.structureGraph);

        return this.structureGraph;
    }

    /**
     * The clustering used.
     *
     * @return This clustering.
     */
    public List<List<Node>> getClustering() {
        return this.clustering;
    }

    /**
     * The alpha to use.
     *
     * @param alpha This alpha.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * The knowledge to use in the search.
     *
     * @param knowledge This knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * The covariance matrix over the latents that is implied by the clustering.
     *
     * @return This covariance matrix.
     */
    public ICovarianceMatrix getLatentsCov() {
        return this.latentsCov;
    }

    /**
     * The p-value of the model.
     *
     * @return This p-value.
     */
    public double getpValue() {
        return this.pValue;
    }

    /**
     * The full graph discovered.
     *
     * @return the allowUnfaithfulness discovered graph, with latents and indicators.
     */
    public Graph getFullGraph() {
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

        return graph;
    }

    /**
     * Sets the parameter convergence threshold. Default = 1e-4.
     *
     * @param epsilon This threshold.
     */
    public void setEpsilon(double epsilon) {
        if (epsilon < 0) throw new IllegalArgumentException("Epsilon mut be >= 0: " + epsilon);
    }

    /**
     * Defines latent nodes based on the given names.
     *
     * @param names A list of names for the latent nodes.
     * @return A list of Node objects representing the defined latent nodes.
     */
    private List<Node> defineLatents(List<String> names) {
        List<Node> latents = new ArrayList<>();

        for (String name : names) {
            Node node = new GraphNode(name);
            node.setNodeType(NodeType.LATENT);
            latents.add(node);
        }

        return latents;
    }

    /**
     * Removes small clusters from a clustering structure.
     *
     * @param latents     The list of latent nodes.
     * @param clustering  The clustering structure.
     * @param minimumSize The minimum size of clusters to retain.
     */
    private void removeSmallClusters(List<Node> latents, List<List<Node>> clustering, int minimumSize) {
        for (int i = new ArrayList<>(latents).size() - 1; i >= 0; i--) {
            if (clustering.get(i).size() < minimumSize) {
                clustering.remove(clustering.get(i));
                latents.remove(latents.get(i));
            }
        }
    }

    /**
     * Calculate the covariance matrix for latents.
     *
     * @param _measurescov The covariance matrix over the measured variables.
     * @param latents      A list of latent nodes.
     * @param indicators   A 2D array of indicator nodes.
     * @return The covariance matrix for latents.
     */
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
                    final double v = .5;
                    latentscov.set(i, j, v);
                    latentscov.set(j, i, v);
                }
            }
        }

        double[][] loadings = new double[indicators.length][];

        for (int i = 0; i < indicators.length; i++) {
            loadings[i] = new double[indicators[i].length];
        }

        for (int i = 0; i < indicators.length; i++) {
            loadings[i] = new double[indicators[i].length];

            for (int j = 0; j < indicators[i].length; j++) {
                loadings[i][j] = .5;
            }
        }

        int[][] indicatorIndices = new int[indicators.length][];
        List<Node> measures = _measurescov.getVariables();

        for (int i = 0; i < indicators.length; i++) {
            indicatorIndices[i] = new int[indicators[i].length];

            for (int j = 0; j < indicators[i].length; j++) {
                indicatorIndices[i][j] = measures.indexOf(indicators[i][j]);
            }
        }

        // Variances of the measures.
        double[] delta = new double[measurescov.getNumRows()];

        Arrays.fill(delta, 1);

        double[] allParams1 = getAllParams(indicators, latentscov, loadings, delta);

        optimizeNonMeasureVariancesQuick(indicators, measurescov, latentscov, loadings, indicatorIndices);

        int numParams = allParams1.length;

        optimizeAllParamsSimultaneously(indicators, measurescov, latentscov, loadings, indicatorIndices, delta);

        double N = _measurescov.getSampleSize();
        int p = _measurescov.getDimension();

        int df = (p) * (p + 1) / 2 - (numParams);
        double x = (N - 1) * this.minimum;
        this.pValue = 1.0 - new ChiSquaredDistribution(df).cumulativeProbability(x);

        return latentscov;
    }

    /**
     * Optimizes the non-measure variances quickly.
     *
     * @param indicators       A 2D array of indicator nodes.
     * @param measurescov      The covariance matrix over the measured variables.
     * @param latentscov       The covariance matrix over the latent variables.
     * @param loadings         A 2D array of loadings.
     * @param indicatorIndices A 2D array of indicator indices.
     */
    private void optimizeNonMeasureVariancesQuick(Node[][] indicators, Matrix measurescov, Matrix latentscov,
                                                  double[][] loadings, int[][] indicatorIndices) {
        int count = 0;

        for (int i = 0; i < indicators.length; i++) {
            for (int j = i; j < indicators.length; j++) {
                count++;
            }
        }

        for (Node[] indicator : indicators) {
            for (int j = 0; j < indicator.length; j++) {
                count++;
            }
        }

        double[] values = new double[count];
        count = 0;

        for (int i = 0; i < indicators.length; i++) {
            for (int j = i; j < indicators.length; j++) {
                values[count++] = latentscov.get(i, j);
            }
        }

        for (int i = 0; i < indicators.length; i++) {
            for (int j = 0; j < indicators[i].length; j++) {
                values[count++] = loadings[i][j];
            }
        }

        Function1 function1 = new Function1(indicatorIndices, measurescov, loadings, latentscov);
        MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);

        PointValuePair pair = search.optimize(
                new InitialGuess(values),
                new ObjectiveFunction(function1),
                GoalType.MINIMIZE,
                new MaxEval(100000));

        this.minimum = pair.getValue();
    }

    /**
     * Optimizes all parameters simultaneously.
     *
     * @param indicators       A 2D array of indicator nodes.
     * @param measurescov      The covariance matrix over the measured variables.
     * @param latentscov       The covariance matrix over the latent variables.
     * @param loadings         A 2D array of loadings.
     * @param indicatorIndices A 2D array of indicator indices.
     * @param delta            An array of delta values.
     */
    private void optimizeAllParamsSimultaneously(Node[][] indicators, Matrix measurescov,
                                                 Matrix latentscov, double[][] loadings,
                                                 int[][] indicatorIndices, double[] delta) {
        double[] values = getAllParams(indicators, latentscov, loadings, delta);

        Function4 function = new Function4(indicatorIndices, measurescov, loadings, latentscov, delta);
        MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);

        PointValuePair pair = search.optimize(
                new InitialGuess(values),
                new ObjectiveFunction(function),
                GoalType.MINIMIZE,
                new MaxEval(100000));

        this.minimum = pair.getValue();
    }

    /**
     * Returns an array containing all the parameters required for optimization.
     *
     * @param indicators The 2D array of indicator nodes.
     * @param latentscov The covariance matrix over the latent variables.
     * @param loadings   The 2D array of loadings.
     * @param delta      The array of delta values.
     * @return An array containing all the parameters required for optimization.
     */
    private double[] getAllParams(Node[][] indicators, Matrix latentscov, double[][] loadings, double[] delta) {
        int count = 0;

        for (int i = 0; i < indicators.length; i++) {
            for (int j = i; j < indicators.length; j++) {
                count++;
            }
        }

        for (Node[] indicator : indicators) {
            for (int j = 0; j < indicator.length; j++) {
                count++;
            }
        }

        for (int i = 0; i < delta.length; i++) {
            count++;
        }

        double[] values = new double[count];
        count = 0;

        for (int i = 0; i < indicators.length; i++) {
            for (int j = i; j < indicators.length; j++) {
                values[count] = latentscov.get(i, j);
                count++;
            }
        }

        for (int i = 0; i < indicators.length; i++) {
            for (int j = 0; j < indicators[i].length; j++) {
                values[count] = loadings[i][j];
                count++;
            }
        }

        for (double v : delta) {
            values[count] = v;
            count++;
        }

        return values;
    }

    /**
     * Clusters smaller than this size will be tossed out.
     *
     * @return a int
     */
    public int getMinClusterSize() {
        return this.minClusterSize;
    }

    /**
     * Sets the minimum cluster size.
     *
     * @param minClusterSize The minimum cluster size to be set.
     * @throws IllegalArgumentException If the minimum cluster size is less than 3.
     */
    public void setMinClusterSize(int minClusterSize) {
        if (minClusterSize < 3)
            throw new IllegalArgumentException("Minimum cluster size must be >= 3: " + minClusterSize);
        this.minClusterSize = minClusterSize;
    }

    /**
     * Calculates the implied covariance matrix for latent variables based on indicator variables.
     *
     * @param indicatorIndices A 2D array of indicator indices.
     * @param loadings         A 2D array of loadings.
     * @param cov              The covariance matrix over the measured variables.
     * @param loadingscov      The covariance matrix over the latent variables.
     * @param delta            An array of delta values.
     * @return The implied covariance matrix for the latent variables.
     */
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

    /**
     * Calculates the sum of differences between the covariance matrix and the product of loadings for a given set of
     * indicator indices, covariance matrix, loadings, and loadings covariance matrix.
     *
     * @param indicatorIndices A 2D array of indicator indices.
     * @param cov              The covariance matrix over the measured variables.
     * @param loadings         A 2D array of loadings.
     * @param loadingscov      The covariance matrix over the latent variables.
     * @return The sum of differences.
     */
    private double sumOfDifferences(int[][] indicatorIndices, Matrix cov, double[][] loadings, Matrix loadingscov) {
        double sum = 0;

        for (int i = 0; i < loadings.length; i++) {
            for (int k = 0; k < loadings[i].length; k++) {
                for (int l = k + 1; l < loadings[i].length; l++) {
                    double _cov = cov.get(indicatorIndices[i][k], indicatorIndices[i][l]);
                    double prod = loadings[i][k] * loadings[i][l] * loadingscov.get(i, i);
                    double diff = _cov - prod;
                    sum += diff * diff;
                }
            }
        }

        for (int i = 0; i < loadings.length; i++) {
            for (int j = i + 1; j < loadings.length; j++) {
                for (int k = 0; k < loadings[i].length; k++) {
                    for (int l = 0; l < loadings[j].length; l++) {
                        double _cov = cov.get(indicatorIndices[i][k], indicatorIndices[j][l]);
                        double prod = loadings[i][k] * loadings[j][l] * loadingscov.get(i, j);
                        double diff = _cov - prod;
                        sum += 2 * diff * diff;
                    }
                }
            }
        }

        return sum;
    }

    /**
     * Private class Function1 implements the MultivariateFunction interface. It represents a function that calculates
     * the sum of differences between the covariance matrix and the product of loadings for a given set of indicator
     * indices, covariance matrix, loadings, and loadings covariance matrix.
     */
    private class Function1 implements MultivariateFunction {
        private final int[][] indicatorIndices;
        private final Matrix measurescov;
        private final double[][] loadings;
        private final Matrix latentscov;

        public Function1(int[][] indicatorIndices, Matrix measurescov, double[][] loadings,
                         Matrix latentscov) {
            this.indicatorIndices = indicatorIndices;
            this.measurescov = measurescov;
            this.loadings = loadings;
            this.latentscov = latentscov;
        }

        @Override
        public double value(double[] values) {
            int count = 0;

            for (int i = 0; i < this.loadings.length; i++) {
                for (int j = i; j < this.loadings.length; j++) {
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

            return sumOfDifferences(this.indicatorIndices, this.measurescov, this.loadings, this.latentscov);
        }
    }

    /**
     * Private class Function4 implements the MultivariateFunction interface. It is used in the MimbuildTrek class to
     * perform optimization calculations.
     */
    private class Function4 implements MultivariateFunction {
        private final int[][] indicatorIndices;
        private final Matrix measurescov;
        private final Matrix measuresCovInverse;
        private final double[][] loadings;
        private final Matrix latentscov;
        private final double[] delta;

        public Function4(int[][] indicatorIndices, Matrix measurescov, double[][] loadings, Matrix latentscov,
                         double[] delta) {
            this.indicatorIndices = indicatorIndices;
            this.measurescov = measurescov;
            this.loadings = loadings;
            this.latentscov = latentscov;
            this.delta = delta;
            this.measuresCovInverse = measurescov.inverse();
        }

        @Override
        public double value(double[] values) {
            int count = 0;

            for (int i = 0; i < this.loadings.length; i++) {
                for (int j = i; j < this.loadings.length; j++) {
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
            Matrix diff = I.minus((implied.times(this.measuresCovInverse)));  // time hog. times().

            return 0.5 * (diff.times(diff)).trace();
        }
    }
}



