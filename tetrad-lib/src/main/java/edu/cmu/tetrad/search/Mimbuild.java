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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
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
import java.util.Arrays;
import java.util.List;

/**
 * Provides an implementation of Mimbuild, an algorithm that takes a clustering of variables, each of which is explained
 * by a single latent, then forms the implied covariance matrix over the latent variables, then runs a CPDAG search to
 * in the structure over the latent themselves.
 * <p>
 * Specifically, the search will first infer the covariance matrix over the latents and then will use the GRaSP
 * algorithm (see) to infer the structure graph over the latents, using the SEM Bic score with the given penalty
 * discount (default 2).
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
 * @see Grasp
 * @see Knowledge
 */
public class Mimbuild {
    // The clustering from BPC or equivalent. Small clusters are removed.
    private List<List<Node>> clustering;
    // The graph over the latents.
    private Graph structureGraph;
    // Background knowledge for CPC.
    private Knowledge knowledge = new Knowledge();
    // The covariance matrix over the latent variables.
    private ICovarianceMatrix latentsCov;
    // The minimum function (Fgsl) value achieved.
    private double minimum;
    // The p value of the optimization.
    private double pValue;
    // The latents.
    private List<Node> latents;
    // The penalty discount of the score used to infer the structure graph.
    private double penaltyDiscount = 1;
    // jf Clusters smaller than this size will be tossed out.
    private int minClusterSize = 3;
    private long seed = -1;

    /**
     * Constructs a new Mimbuild search.
     */
    public Mimbuild() {
    }


    /**
     * Does a Mimbuild search.
     *
     * @param clustering  The clustering to use--this clusters the measured variables in such a way that each cluster is
     *                    explained by a single latent variables.
     * @param latentNames The names of the latent variables corresponding in order ot each cluster in the clustering.
     * @param measuresCov The covariance matrix over the measured variables.
     * @return The inferred graph over the latent variables.
     * @throws InterruptedException If the search is interrupted.
     */
    public Graph search(List<List<Node>> clustering, List<String> latentNames, ICovarianceMatrix measuresCov) throws InterruptedException {
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
        CovarianceMatrix latentscov = new CorrelationMatrix(latents, cov, measuresCov.getSampleSize());
        this.latentsCov = latentscov;
        Graph graph;

        SemBicScore score = new SemBicScore(latentscov);
        score.setPenaltyDiscount(this.penaltyDiscount);
        PermutationSearch search = new PermutationSearch(new Boss(score));
        search.setSeed(seed);
        search.setKnowledge(this.knowledge);
        graph = search.search();

        this.structureGraph = new EdgeListGraph(graph);
        LayoutUtil.fruchtermanReingoldLayout(this.structureGraph);

        return this.structureGraph;
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
     * Sets the knowledge to be used in the search.
     *
     * @param knowledge This knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
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
     * <p>Getter for the field <code>pValue</code>.</p>
     *
     * @return The p value of the optimization.
     */
    public double getpValue() {
        return this.pValue;
    }

    /**
     * The full graph inferred, including the edges from latents to measures. And all fo the edges inferred among
     * latents.
     *
     * @return This full graph.
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

    private void removeSmallClusters(List<Node> latents, List<List<Node>> clustering, int minimumSize) {
        for (int i = new ArrayList<>(latents).size() - 1; i >= 0; i--) {
            if (clustering.get(i).size() < minimumSize) {
                clustering.remove(clustering.get(i));
                latents.remove(latents.get(i));
            }
        }
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

        if (df < 1) throw new IllegalStateException(
                "Mimbuild error: The degrees of freedom for this model ((m * (m + 1) / 2) - # estimation params)" +
                "\nwas calculated to be less than 1. Perhaps the model is not a multiple indicator model " +
                "\nor doesn't have enough pure nmeasurements to do a proper estimation.");

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

    private double[] getAllParams(Node[][] indicators, Matrix latentscov, double[][] loadings, double[] delta) {
        int count = 0;

        // Non-redundant elements of cov(latents)
        for (int i = 0; i < latentscov.getNumRows(); i++) {
            for (int j = i; j < latentscov.getNumColumns(); j++) {
                count++;
            }
        }

        System.out.println("# nonredundant elemnts of cov(error) = " + latentscov.getNumRows() * (latentscov.getNumRows() + 1) / 2);

        int _loadings = 0;

        // Loadings
        for (Node[] indicator : indicators) {
            for (int j = 0; j < indicator.length; j++) {
                count++;
                _loadings++;
            }
        }

        System.out.println("# loadings = " + _loadings);

        // Variance of measures
        for (int i = 0; i < delta.length; i++) {
            count++;
        }

        System.out.println("# measure variances = " + delta.length);

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

    /**
     * jf Clusters smaller than this size will be tossed out.
     *
     * @return a int
     */
    public int getMinClusterSize() {
        return this.minClusterSize;
    }

    /**
     * <p>Setter for the field <code>minClusterSize</code>.</p>
     *
     * @param minClusterSize a int
     */
    public void setMinClusterSize(int minClusterSize) {
        if (minClusterSize < 3)
            throw new IllegalArgumentException("Minimum cluster size must be >= 3: " + minClusterSize);
        this.minClusterSize = minClusterSize;
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
     * <p>Setter for the field <code>seed</code>.</p>
     *
     * @param seed a long
     */
    public void setSeed(long seed) {
        this.seed = seed;
    }

    private class Function1 implements org.apache.commons.math3.analysis.MultivariateFunction {
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

    private class Function4 implements org.apache.commons.math3.analysis.MultivariateFunction {
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



