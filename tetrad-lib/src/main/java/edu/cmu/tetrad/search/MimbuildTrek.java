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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
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
import java.util.List;

import static java.lang.Math.sqrt;

/**
 * An implemetation of Mimbuild based on the treks and ranks.
 *
 * @author Adam
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
    private double alpha = 0.001;

    /**
     * Background knowledge for CPC.
     */
    private IKnowledge knowledge = new Knowledge2();

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
    private int numParams;
    private List<Node> latents;
    private double epsilon = 1e-4;
    private int penaltyDiscount = 1;
    private int minClusterSize = 3;

    public MimbuildTrek() {
    }

    //=================================== PUBLIC METHODS =========================================//

    public Graph search(final List<List<Node>> clustering, final List<String> latentNames, ICovarianceMatrix measuresCov) {
        final List<String> _latentNames = new ArrayList<>(latentNames);

        final List<String> allVarNames = new ArrayList<>();

        for (final List<Node> cluster : clustering) {
            for (final Node node : cluster) allVarNames.add(node.getName());
        }

        measuresCov = measuresCov.getSubmatrix(allVarNames);

        final List<List<Node>> _clustering = new ArrayList<>();

        for (final List<Node> cluster : clustering) {
            final List<Node> _cluster = new ArrayList<>();

            for (final Node node : cluster) {
                _cluster.add(measuresCov.getVariable(node.getName()));
            }

            _clustering.add(_cluster);
        }

        final List<Node> latents = defineLatents(_latentNames);
        this.latents = latents;

        // This removes the small clusters and their names.
        removeSmallClusters(latents, _clustering, getMinClusterSize());
        this.clustering = _clustering;

        final Node[][] indicators = new Node[latents.size()][];

        for (int i = 0; i < latents.size(); i++) {
            indicators[i] = new Node[_clustering.get(i).size()];

            for (int j = 0; j < _clustering.get(i).size(); j++) {
                indicators[i][j] = _clustering.get(i).get(j);
            }
        }

        final Matrix cov = getCov(measuresCov, latents, indicators);
        final CovarianceMatrix latentscov = new CovarianceMatrix(latents, cov, measuresCov.getSampleSize());
        this.latentsCov = latentscov;
        final Graph graph;

        final Cpc search = new Cpc(new IndTestTrekSep(measuresCov, this.alpha, clustering, latents));
        search.setKnowledge(this.knowledge);
        graph = search.search();

//        try {
//            Ges search = new Ges(latentscov);
//            search.setCorrErrorsAlpha(penaltyDiscount);
//            search.setKnowledge(knowledge);
//            graph = search.search();
//        } catch (Exception e) {
////            e.printStackTrace();
//            CPC search = new CPC(new IndTestFisherZ(latentscov, alpha));
//            search.setKnowledge(knowledge);
//            graph = search.search();
//        }

        this.structureGraph = new EdgeListGraph(graph);
        GraphUtils.fruchtermanReingoldLayout(this.structureGraph);

        return this.structureGraph;
    }

    public List<List<Node>> getClustering() {
        return this.clustering;
    }

    public double getAlpha() {
        return this.alpha;
    }

    public void setAlpha(final double alpha) {
        this.alpha = alpha;
    }

    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(final IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public ICovarianceMatrix getLatentsCov() {
        return this.latentsCov;
    }

    public List<String> getLatentNames(final List<Node> latents) {
        final List<String> latentNames = new ArrayList<>();

        for (final Node node : latents) {
            latentNames.add(node.getName());
        }

        return latentNames;
    }

    public double getMinimum() {
        return this.minimum;
    }

    public double getpValue() {
        return this.pValue;
    }

    /**
     * @return the allowUnfaithfulness discovered graph, with latents and indicators.
     */
    public Graph getFullGraph() {
        final Graph graph = new EdgeListGraph(this.structureGraph);

        for (int i = 0; i < this.latents.size(); i++) {
            final Node latent = this.latents.get(i);
            final List<Node> measuredGuys = getClustering().get(i);

            for (final Node measured : measuredGuys) {
                if (!graph.containsNode(measured)) {
                    graph.addNode(measured);
                }

                graph.addDirectedEdge(latent, measured);
            }
        }

        return graph;
    }

    public double getEpsilon() {
        return this.epsilon;
    }

    /**
     * Parameter convergence threshold. Default = 1e-4.
     */
    public void setEpsilon(final double epsilon) {
        if (epsilon < 0) throw new IllegalArgumentException("Epsilon mut be >= 0: " + epsilon);
        this.epsilon = epsilon;
    }

    public void setPenaltyDiscount(final int penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    //=================================== PRIVATE METHODS =========================================//

    private List<Node> defineLatents(final List<String> names) {
        final List<Node> latents = new ArrayList<>();

        for (final String name : names) {
            final Node node = new GraphNode(name);
            node.setNodeType(NodeType.LATENT);
            latents.add(node);
        }

        return latents;
    }

    private void removeSmallClusters(final List<Node> latents, final List<List<Node>> clustering, final int minimumSize) {
        for (int i = new ArrayList<>(latents).size() - 1; i >= 0; i--) {
            if (clustering.get(i).size() < minimumSize) {
                clustering.remove(clustering.get(i));
                latents.remove(latents.get(i));
            }
        }
    }

    private Matrix getCov(final ICovarianceMatrix _measurescov, final List<Node> latents, final Node[][] indicators) {
        if (latents.size() != indicators.length) {
            throw new IllegalArgumentException();
        }

        final Matrix measurescov = _measurescov.getMatrix();
        final Matrix latentscov = new Matrix(latents.size(), latents.size());

        for (int i = 0; i < latentscov.rows(); i++) {
            for (int j = i; j < latentscov.columns(); j++) {
                if (i == j) latentscov.set(i, j, 1.0);
                else {
                    final double v = .5;
                    latentscov.set(i, j, v);
                    latentscov.set(j, i, v);
                }
            }
        }

        final double[][] loadings = new double[indicators.length][];

        for (int i = 0; i < indicators.length; i++) {
            loadings[i] = new double[indicators[i].length];
        }

        for (int i = 0; i < indicators.length; i++) {
            loadings[i] = new double[indicators[i].length];

            for (int j = 0; j < indicators[i].length; j++) {
                loadings[i][j] = .5;
            }
        }

        final int[][] indicatorIndices = new int[indicators.length][];
        final List<Node> measures = _measurescov.getVariables();

        for (int i = 0; i < indicators.length; i++) {
            indicatorIndices[i] = new int[indicators[i].length];

            for (int j = 0; j < indicators[i].length; j++) {
                indicatorIndices[i][j] = measures.indexOf(indicators[i][j]);
            }
        }

        // Variances of the measures.
        final double[] delta = new double[measurescov.rows()];

        for (int i = 0; i < delta.length; i++) {
            delta[i] = 1;
        }

        int numNonMeasureVarianceParams = 0;

        for (int i = 0; i < latentscov.rows(); i++) {
            for (int j = i; j < latentscov.columns(); j++) {
                numNonMeasureVarianceParams++;
            }
        }

        for (int i = 0; i < indicators.length; i++) {
            numNonMeasureVarianceParams += indicators[i].length;
        }

        final double[] allParams1 = getAllParams(indicators, latentscov, loadings, delta);

        optimizeNonMeasureVariancesQuick(indicators, measurescov, latentscov, loadings, indicatorIndices);

//        for (int i = 0; i < 10; i++) {
//            optimizeNonMeasureVariancesConditionally(indicators, measurescov, latentscov, loadings, indicatorIndices, delta);
//            optimizeMeasureVariancesConditionally(measurescov, latentscov, loadings, indicatorIndices, delta);
//
//            double[] allParams2 = getAllParams(indicators, latentscov, loadings, delta);
//            if (distance(allParams1, allParams2) < epsilon) break;
//            allParams1 = allParams2;
//        }

        this.numParams = allParams1.length;

//        // Very slow but could be done alone.
        optimizeAllParamsSimultaneously(indicators, measurescov, latentscov, loadings, indicatorIndices, delta);

        final double N = _measurescov.getSampleSize();
        final int p = _measurescov.getDimension();

        final int df = (p) * (p + 1) / 2 - (this.numParams);
        final double x = (N - 1) * this.minimum;
        this.pValue = 1.0 - new ChiSquaredDistribution(df).cumulativeProbability(x);

        return latentscov;
    }

    private double distance(final double[] allParams1, final double[] allParams2) {
        double sum = 0;

        for (int i = 0; i < allParams1.length; i++) {
            final double diff = allParams1[i] - allParams2[i];
            sum += diff * diff;
        }

        return sqrt(sum);
    }

    private void optimizeNonMeasureVariancesQuick(final Node[][] indicators, final Matrix measurescov, final Matrix latentscov,
                                                  final double[][] loadings, final int[][] indicatorIndices) {
        int count = 0;

        for (int i = 0; i < indicators.length; i++) {
            for (int j = i; j < indicators.length; j++) {
                count++;
            }
        }

        for (int i = 0; i < indicators.length; i++) {
            for (int j = 0; j < indicators[i].length; j++) {
                count++;
            }
        }

        final double[] values = new double[count];
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

        final Function1 function1 = new Function1(indicatorIndices, measurescov, loadings, latentscov, count);
        final MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);

        final PointValuePair pair = search.optimize(
                new InitialGuess(values),
                new ObjectiveFunction(function1),
                GoalType.MINIMIZE,
                new MaxEval(100000));

        this.minimum = pair.getValue();
    }

    private void optimizeNonMeasureVariancesConditionally(final Node[][] indicators, final Matrix measurescov,
                                                          final Matrix latentscov, final double[][] loadings,
                                                          final int[][] indicatorIndices, final double[] delta) {
        int count = 0;

        for (int i = 0; i < indicators.length; i++) {
            for (int j = i; j < indicators.length; j++) {
                count++;
            }
        }

        for (int i = 0; i < indicators.length; i++) {
            for (int j = 0; j < indicators[i].length; j++) {
                count++;
            }
        }

        final double[] values3 = new double[count];
        count = 0;

        for (int i = 0; i < indicators.length; i++) {
            for (int j = i; j < indicators.length; j++) {
                values3[count] = latentscov.get(i, j);
                count++;
            }
        }

        for (int i = 0; i < indicators.length; i++) {
            for (int j = 0; j < indicators[i].length; j++) {
                values3[count] = loadings[i][j];
                count++;
            }
        }

        final Function2 function2 = new Function2(indicatorIndices, measurescov, loadings, latentscov, delta, count);
        final MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);

        final PointValuePair pair = search.optimize(
                new InitialGuess(values3),
                new ObjectiveFunction(function2),
                GoalType.MINIMIZE,
                new MaxEval(100000));

        this.minimum = pair.getValue();
    }

    private void optimizeMeasureVariancesConditionally(final Matrix measurescov, final Matrix latentscov, final double[][] loadings,
                                                       final int[][] indicatorIndices, final double[] delta) {
        final double[] values2 = new double[delta.length];
        int count = 0;

        for (int i = 0; i < delta.length; i++) {
            values2[count++] = delta[i];
        }

        final Function2 function2 = new Function2(indicatorIndices, measurescov, loadings, latentscov, delta, count);
        final MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);

        final PointValuePair pair = search.optimize(
                new InitialGuess(values2),
                new ObjectiveFunction(function2),
                GoalType.MINIMIZE,
                new MaxEval(100000));

        this.minimum = pair.getValue();
    }

    public int getNumParams() {
        return this.numParams;
    }

    private void optimizeAllParamsSimultaneously(final Node[][] indicators, final Matrix measurescov,
                                                 final Matrix latentscov, final double[][] loadings,
                                                 final int[][] indicatorIndices, final double[] delta) {
        final double[] values = getAllParams(indicators, latentscov, loadings, delta);

        final Function4 function = new Function4(indicatorIndices, measurescov, loadings, latentscov, delta);
        final MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);

        final PointValuePair pair = search.optimize(
                new InitialGuess(values),
                new ObjectiveFunction(function),
                GoalType.MINIMIZE,
                new MaxEval(100000));

        this.minimum = pair.getValue();
    }

    private double[] getAllParams(final Node[][] indicators, final Matrix latentscov, final double[][] loadings, final double[] delta) {
        int count = 0;

        for (int i = 0; i < indicators.length; i++) {
            for (int j = i; j < indicators.length; j++) {
                count++;
            }
        }

        for (int i = 0; i < indicators.length; i++) {
            for (int j = 0; j < indicators[i].length; j++) {
                count++;
            }
        }

        for (int i = 0; i < delta.length; i++) {
            count++;
        }

        final double[] values = new double[count];
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

        for (int i = 0; i < delta.length; i++) {
            values[count] = delta[i];
            count++;
        }

        return values;
    }

    /**
     * jf
     * Clusters smaller than this size will be tossed out.
     */
    public int getMinClusterSize() {
        return this.minClusterSize;
    }

    public void setMinClusterSize(final int minClusterSize) {
        if (minClusterSize < 3)
            throw new IllegalArgumentException("Minimum cluster size must be >= 3: " + minClusterSize);
        this.minClusterSize = minClusterSize;
    }

    private class Function1 implements MultivariateFunction {
        private final int[][] indicatorIndices;
        private final Matrix measurescov;
        private final double[][] loadings;
        private final Matrix latentscov;
        private final int numParams;

        public Function1(final int[][] indicatorIndices, final Matrix measurescov, final double[][] loadings,
                         final Matrix latentscov, final int numParams) {
            this.indicatorIndices = indicatorIndices;
            this.measurescov = measurescov;
            this.loadings = loadings;
            this.latentscov = latentscov;
            this.numParams = numParams;
        }

        @Override
        public double value(final double[] values) {
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

//        public int getNumArguments() {
//            return numParams;
//        }
//
//        public double getLowerBound(int i) {
//            return -100;
//        }
//
//        public double getUpperBound(int i) {
//            return 100;
//        }
    }

    private class Function2 implements MultivariateFunction {
        private final int[][] indicatorIndices;
        private final Matrix measurescov;
        private final Matrix measuresCovInverse;
        private final double[][] loadings;
        private final Matrix latentscov;
        private final int numParams;
        private final double[] delta;
        private final List<Integer> aboveZero = new ArrayList<>();

        public Function2(final int[][] indicatorIndices, final Matrix measurescov, final double[][] loadings, final Matrix latentscov,
                         final double[] delta, final int numNonMeasureVarianceParams) {
            this.indicatorIndices = indicatorIndices;
            this.measurescov = measurescov;
            this.loadings = loadings;
            this.latentscov = latentscov;
            this.numParams = numNonMeasureVarianceParams;
            this.delta = delta;
            this.measuresCovInverse = measurescov.inverse();

            int count = 0;

            for (int i = 0; i < loadings.length; i++) {
                for (int j = i; j < loadings.length; j++) {
                    if (i == j) this.aboveZero.add(count);
                    count++;
                }
            }

            for (int i = 0; i < loadings.length; i++) {
                for (int j = 0; j < loadings[i].length; j++) {
                    count++;
                }
            }
        }

        @Override
        public double value(final double[] values) {
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

            final Matrix implied = impliedCovariance(this.indicatorIndices, this.loadings, this.measurescov, this.latentscov, this.delta);

            final Matrix I = Matrix.identity(implied.rows());
            final Matrix diff = I.minus((implied.times(this.measuresCovInverse)));

            return 0.5 * (diff.times(diff)).trace();
        }
    }

    private class Function3 implements MultivariateFunction {
        private final int[][] indicatorIndices;
        private final Matrix measurescov;
        private final Matrix measuresCovInverse;
        private final double[][] loadings;
        private final Matrix latentscov;
        private final int numParams;
        private final double[] delta;
        private final List<Integer> aboveZero = new ArrayList<>();

        public Function3(final int[][] indicatorIndices, final Matrix measurescov, final double[][] loadings, final Matrix latentscov,
                         final double[] delta, final int numParams) {
            this.indicatorIndices = indicatorIndices;
            this.measurescov = measurescov;
            this.loadings = loadings;
            this.latentscov = latentscov;
            this.numParams = numParams;
            this.delta = delta;
            this.measuresCovInverse = measurescov.inverse();

            int count = 0;

            for (int i = 0; i < delta.length; i++) {
                this.aboveZero.add(count);
                count++;
            }
        }

        public double value(final double[] values) {
            int count = 0;

            for (int i = 0; i < this.delta.length; i++) {
                this.delta[i] = values[count];
                count++;
            }

            final Matrix implied = impliedCovariance(this.indicatorIndices, this.loadings, this.measurescov, this.latentscov, this.delta);

            final Matrix I = Matrix.identity(implied.rows());
            final Matrix diff = I.minus((implied.times(this.measuresCovInverse)));

            return 0.5 * (diff.times(diff)).trace();
        }
    }

    private class Function4 implements MultivariateFunction {
        private final int[][] indicatorIndices;
        private final Matrix measurescov;
        private final Matrix measuresCovInverse;
        private final double[][] loadings;
        private final Matrix latentscov;
        private final int numParams;
        private final double[] delta;
        private final List<Integer> aboveZero = new ArrayList<>();

        public Function4(final int[][] indicatorIndices, final Matrix measurescov, final double[][] loadings, final Matrix latentscov,
                         final double[] delta) {
            this.indicatorIndices = indicatorIndices;
            this.measurescov = measurescov;
            this.loadings = loadings;
            this.latentscov = latentscov;
            this.delta = delta;
            this.measuresCovInverse = measurescov.inverse();

            int count = 0;

            for (int i = 0; i < loadings.length; i++) {
                for (int j = i; j < loadings.length; j++) {
                    if (i == j) this.aboveZero.add(count);
                    count++;
                }
            }

            for (int i = 0; i < loadings.length; i++) {
                for (int j = 0; j < loadings[i].length; j++) {
                    count++;
                }
            }

            for (int i = 0; i < delta.length; i++) {
                this.aboveZero.add(count);
                count++;
            }

            this.numParams = count;
        }

        @Override
        public double value(final double[] values) {
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

            final Matrix implied = impliedCovariance(this.indicatorIndices, this.loadings, this.measurescov, this.latentscov, this.delta);

            final Matrix I = Matrix.identity(implied.rows());
            final Matrix diff = I.minus((implied.times(this.measuresCovInverse)));  // time hog. times().

            return 0.5 * (diff.times(diff)).trace();
        }
    }


    private Matrix impliedCovariance(final int[][] indicatorIndices, final double[][] loadings, final Matrix cov, final Matrix loadingscov,
                                     final double[] delta) {
        final Matrix implied = new Matrix(cov.rows(), cov.columns());

        for (int i = 0; i < loadings.length; i++) {
            for (int j = 0; j < loadings.length; j++) {
                for (int k = 0; k < loadings[i].length; k++) {
                    for (int l = 0; l < loadings[j].length; l++) {
                        final double prod = loadings[i][k] * loadings[j][l] * loadingscov.get(i, j);
                        implied.set(indicatorIndices[i][k], indicatorIndices[j][l], prod);
                    }
                }
            }
        }

        for (int i = 0; i < implied.rows(); i++) {
            implied.set(i, i, implied.get(i, i) + delta[i]);
        }

        return implied;
    }

    private double sumOfDifferences(final int[][] indicatorIndices, final Matrix cov, final double[][] loadings, final Matrix loadingscov) {
        double sum = 0;

        for (int i = 0; i < loadings.length; i++) {
            for (int k = 0; k < loadings[i].length; k++) {
                for (int l = k + 1; l < loadings[i].length; l++) {
                    final double _cov = cov.get(indicatorIndices[i][k], indicatorIndices[i][l]);
                    final double prod = loadings[i][k] * loadings[i][l] * loadingscov.get(i, i);
                    final double diff = _cov - prod;
                    sum += diff * diff;
                }
            }
        }

        for (int i = 0; i < loadings.length; i++) {
            for (int j = i + 1; j < loadings.length; j++) {
                for (int k = 0; k < loadings[i].length; k++) {
                    for (int l = 0; l < loadings[j].length; l++) {
                        final double _cov = cov.get(indicatorIndices[i][k], indicatorIndices[j][l]);
                        final double prod = loadings[i][k] * loadings[j][l] * loadingscov.get(i, j);
                        final double diff = _cov - prod;
                        sum += 2 * diff * diff;
                    }
                }
            }
        }

        return sum;
    }

}



