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

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphGroup;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.LingUtils;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Vector;
import edu.cmu.tetrad.util.dist.Distribution;
import edu.cmu.tetrad.util.dist.GaussianPower;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static java.lang.Math.*;

/**
 * The code used within this class is largely Gustave Lacerda's, which corresponds to his essay, Discovering Cyclic
 * Causal Models by Independent Components Analysis. The code models the LiNG algorithm.
 * <p>
 * <p>Note: This code is currently broken; please do not use it until it's fixed. 11/24/2015</p>
 */
public class Ling {

    /**
     * Number of samples used when simulating data.
     */
    private int numSamples;

    /**
     * This algorithm uses thresholding to zero out small covariance values. This variable defines the value at which
     * the thresholding occurs.
     */
    private double threshold = .5;

    /**
     * Time needed to process the search method.
     */
    private long elapsedTime = 0L;

    /**
     * Either passed in through the constructor or simulated using a graph.
     */
    private DataSet dataSet;

//    private double pruneFactor = 1.0;

    //=============================CONSTRUCTORS============================//

    /**
     * The algorithm only requires a DataSet to process. Passing in a Dataset and then running the search algorithm is
     * an effetive way to use LiNG.
     *
     * @param d a DataSet over which the algorithm can process
     */
    public Ling(final DataSet d) {
        this.dataSet = d;
    }

    /**
     * When you don't have a Dataset, supply a GraphWithParameters and the number of samples to draw and the algorithm
     * will generate a DataSet.
     *
     * @param graphWP a graph with parameters from GraphWithParameters
     * @param samples the number of samples the algorithm draws in order to generate a DataSet
     */
    public Ling(final GraphWithParameters graphWP, final int samples) {
        this.numSamples = samples;
        makeDataSet(graphWP);
    }

    /**
     * When you don't have a Dataset, supply a Graph and the number of samples to draw and the algorithm will generate a
     * DataSet.
     *
     * @param g       a graph from Graph
     * @param samples the number of samples the algorithm draws in order to generate a DataSet
     */
    public Ling(final Graph g, final int samples) {
        this.numSamples = samples;
        final GraphWithParameters graphWP = new GraphWithParameters(g);
        makeDataSet(graphWP);
    }

    //==============================PUBLIC METHODS=========================//

    /**
     * @return DataSet   Returns a dataset of the data used by the algorithm.
     */
    public DataSet getData() {
        return this.dataSet;
    }

    /**
     * The search method is used to process LiNG. Call search when you want to run the algorithm.
     */
    public StoredGraphs search() {
        final Matrix W;
        StoredGraphs graphs = new StoredGraphs();

        try {
            final long sTime = (new Date()).getTime();

            final boolean fastIca = true;

            if (fastIca) {
                W = getWFastIca();

                System.out.println("W = " + W);

                //this is the heart of our method:
                graphs = findCandidateModels(this.dataSet.getVariables(), W, true);
            } else {
                final double zeta = 1;

                final List<Mapping> allMappings = createMappings(null, null, this.dataSet.getNumColumns());

                W = estimateW(new Matrix(this.dataSet.getDoubleData().transpose()),
                        this.dataSet.getNumColumns(), -zeta, zeta, allMappings);

                System.out.println("W = " + W);

                //this is the heart of our method:
                graphs = findCandidateModel(this.dataSet.getVariables(), W, true);
            }

            this.elapsedTime = (new Date()).getTime() - sTime;
        } catch (final Exception e) {
            e.printStackTrace();
        }

        return graphs;
    }


    private Matrix estimateW(final Matrix matrix, final int numNodes, final double min, final double max, final List<Mapping> allMappings) {
        final Matrix W = initializeW(numNodes);
        maxMappings(matrix, min, max, W, allMappings);
        return W;
    }

    private void maxMappings(final Matrix matrix, final double min,
                             final double max, final Matrix W, final List<Mapping> allMappings) {

        final int numNodes = W.rows();

        for (int i = 0; i < numNodes; i++) {
            final double maxScore = Double.NEGATIVE_INFINITY;
            double[] maxRow = new double[numNodes];

            for (final Mapping mapping : mappingsForRow(i, allMappings)) {
                W.set(mapping.getI(), mapping.getJ(), 0);
            }

            try {
                optimizeNonGaussianity(i, matrix, W, allMappings);
//                optimizeOrthogonality(i, min, max, W, allMappings, W.length);
            } catch (final IllegalStateException e) {
                e.printStackTrace();
                continue;
            }

            final double v = ngFullData(i, matrix, W);

            if (Double.isNaN(v)) continue;
            if (v >= 9999) continue;

            final double[] row = new double[numNodes];
            for (int k = 0; k < numNodes; k++) row[k] = W.get(i, k);

            if (v > maxScore) {
                maxRow = row;
            }

            for (int k = 0; k < numNodes; k++) W.set(i, k, maxRow[k]);
        }
    }

    private void optimizeNonGaussianity(final int rowIndex, final Matrix dataSetTetradMatrix,
                                        final Matrix W, final List<Mapping> allMappings) {
        final List<Mapping> mappings = mappingsForRow(rowIndex, allMappings);

        final MultivariateFunction function = new MultivariateFunction() {
            public double value(final double[] values) {
                for (int i = 0; i < values.length; i++) {
                    final Mapping mapping = mappings.get(i);
                    W.set(mapping.getI(), mapping.getJ(), values[i]);
                }

                final double v = ngFullData(rowIndex, dataSetTetradMatrix, W);

                if (Double.isNaN(v)) return 10000;

                return -(v);
            }
        };

        {
            double[] values = new double[mappings.size()];

            for (int k = 0; k < mappings.size(); k++) {
                final Mapping mapping = mappings.get(k);
                values[k] = W.get(mapping.getI(), mapping.getJ());
            }

            final MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);

            final PointValuePair pair = search.optimize(
                    new InitialGuess(values),
                    new ObjectiveFunction(function),
                    GoalType.MINIMIZE,
                    new MaxEval(100000));

            values = pair.getPoint();

            for (int k = 0; k < mappings.size(); k++) {
                final Mapping mapping = mappings.get(k);
                W.set(mapping.getI(), mapping.getJ(), values[k]);
            }
        }

    }

    public double ngFullData(final int rowIndex, final Matrix data, final Matrix W) {
        double[] col = new double[data.rows()];

        for (int i = 0; i < data.rows(); i++) {
            double d = 0.0;

            // Node _x given parents. Its coefficient is fixed at 1. Also, coefficients for all
            // other variables not neighbors of _x are fixed at zero.
            for (int j = 0; j < data.columns(); j++) {
                final double coef = W.get(rowIndex, j);
                final double value = data.get(i, j);
                d += coef * value;
            }

            col[i] = d;
        }

        col = removeNaN(col);

        if (col.length == 0) {
            System.out.println();
            return Double.NaN;
        }

        double sum = 0;

        for (int i = 0; i < col.length; i++) {
            sum += log(cosh(col[i]));
        }

        return sum / col.length;
//        return new AndersonDarlingTest(col).getASquaredStar();
    }

    private double[] removeNaN(final double[] data) {
        final List<Double> _leaveOutMissing = new ArrayList<>();

        for (int i = 0; i < data.length; i++) {
            if (!Double.isNaN(data[i])) {
                _leaveOutMissing.add(data[i]);
            }
        }

        final double[] _data = new double[_leaveOutMissing.size()];

        for (int i = 0; i < _leaveOutMissing.size(); i++) _data[i] = _leaveOutMissing.get(i);

        return _data;
    }

    private List<Mapping> mappingsForRow(final int rowIndex, final List<Mapping> allMappings) {
        final List<Mapping> mappings = new ArrayList<>();

        for (final Mapping mapping : allMappings) {
            if (mapping.getI() == rowIndex) mappings.add(mapping);
        }
        return mappings;
    }

    private Matrix initializeW(final int numNodes) {

        // Initialize W to I.
        final Matrix W = new Matrix(numNodes, numNodes);

        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numNodes; j++) {
                if (i == j) {
                    W.set(i, j, 1.0);
                } else {
                    W.set(i, j, 0.0);
                }
            }
        }
        return W;
    }

    private List<Mapping> createMappings(final Graph graph, final List<Node> nodes, final int numNodes) {

        // Mark as parameters all non-adjacencies from the graph, excluding self edges.
        final List<Mapping> allMappings = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numNodes; j++) {
                if (i == j) continue;
                allMappings.add(new Mapping(i, j));
            }
        }

        return allMappings;
    }

    private static class Mapping {
        private int i = -1;
        private int j = -1;

        public Mapping(final int i, final int j) {
            this.i = i;
            this.j = j;
        }

        public int getI() {
            return this.i;
        }

        public int getJ() {
            return this.j;
        }
    }

    private Matrix getWFastIca() {
        final Matrix W;// Using this Fast ICA to get the logging.
        final Matrix data = new Matrix(this.dataSet.getDoubleData().toArray()).transpose();
        final FastIca fastIca = new FastIca(data, 30);
        fastIca.setVerbose(false);
        fastIca.setAlgorithmType(FastIca.DEFLATION);
        fastIca.setFunction(FastIca.LOGCOSH);
        fastIca.setTolerance(.01);
        fastIca.setMaxIterations(1000);
        fastIca.setAlpha(1.0);
        final FastIca.IcaResult result = fastIca.findComponents();
        W = new Matrix(result.getW());
        return W.transpose();
    }

    /**
     * Calculates the time used when processing the search method.
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * Sets the value at which thresholding occurs on Fast ICA data. Default is .05.
     *
     * @param t The value at which the thresholding is set
     */
    public void setThreshold(final double t) {
        this.threshold = t;
    }

    //==============================PRIVATE METHODS====================//

    /**
     * This is the method used in Patrik's code.
     */
    public Matrix pruneEdgesByResampling(final Matrix data) {
        final Matrix X = new Matrix(data.transpose().toArray());

        final int npieces = 10;
        final int cols = X.columns();
        final int rows = X.rows();
        final int piecesize = (int) Math.floor(cols / npieces);

        final List<Matrix> bpieces = new ArrayList<>();
        final List<Vector> diststdpieces = new ArrayList<>();
        final List<Vector> cpieces = new ArrayList<>();

        for (int p = 0; p < npieces; p++) {

//          % Select subset of data, and permute the variables to the causal order
//          Xp = X(k,((p-1)*piecesize+1):(p*piecesize));

            final int p0 = (p) * piecesize;
            final int p1 = (p + 1) * piecesize - 1;
            final int[] range = range(p0, p1);


            final Matrix Xp = X;

//          % Remember to subract out the mean
//          Xpm = mean(Xp,2);
//          Xp = Xp - Xpm*ones(1,size(Xp,2));
//
//          % Calculate covariance matrix
//          cov = (Xp*Xp')/size(Xp,2);

            final Vector Xpm = new Vector(rows);

            for (int i = 0; i < rows; i++) {
                double sum = 0.0;

                for (int j = 0; j < Xp.columns(); j++) {
                    sum += Xp.get(i, j);
                }

                Xpm.set(i, sum / Xp.columns());
            }

            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < Xp.columns(); j++) {
                    Xp.set(i, j, Xp.get(i, j) - Xpm.get(i));
                }
            }


            final Matrix Xpt = Xp.transpose();

            final Matrix cov = Xp.times(Xpt);

            for (int i = 0; i < cov.rows(); i++) {
                for (int j = 0; j < cov.columns(); j++) {
                    cov.set(i, j, cov.get(i, j) / Xp.columns());
                }
            }

//          % Do QL decomposition on the inverse square root of cov
//          [Q,L] = tridecomp(cov^(-0.5),'ql');

            final boolean posDef = LingUtils.isPositiveDefinite(cov);
//            TetradLogger.getInstance().log("lingamDetails","Positive definite = " + posDef);

            if (!posDef) {
                System.out.println("Covariance matrix is not positive definite.");
            }

            final Matrix sqrt = cov.sqrt();

            final Matrix I = Matrix.identity(rows);
            final Matrix AI = I.copy();
            final Matrix invSqrt = sqrt.inverse();

            final QRDecomposition qr = new QRDecomposition(new BlockRealMatrix(invSqrt.toArray()));
            final RealMatrix r = qr.getR();

//          % The estimated disturbance-stds are one over the abs of the diag of L
//          newestdisturbancestd = 1./diag(abs(L));

            final Vector newestdisturbancestd = new Vector(rows);

            for (int t = 0; t < rows; t++) {
                newestdisturbancestd.set(t, 1.0 / Math.abs(r.getEntry(t, t)));
            }

//          % Normalize rows of L to unit diagonal
//          L = L./(diag(L)*ones(1,dims));
//
            for (int s = 0; s < rows; s++) {
                for (int t = 0; t < min(s, cols); t++) {
                    r.setEntry(s, t, r.getEntry(s, t) / r.getEntry(s, s));
                }
            }

//          % Calculate corresponding B
//          bnewest = eye(dims)-L;

            Matrix bnewest = Matrix.identity(rows);
            bnewest = bnewest.minus(new Matrix(r.getData()));

            final Vector cnewest = new Matrix(r.getData()).times(Xpm);

            bpieces.add(bnewest);
            diststdpieces.add(newestdisturbancestd);
            cpieces.add(cnewest);
        }


//
//        for i=1:dims,
//          for j=1:dims,
//
//            themean = mean(Bpieces(i,j,:));
//            thestd = std(Bpieces(i,j,:));
//            if abs(themean)<prunefactor*thestd,
//          Bfinal(i,j) = 0;
//            else
//          Bfinal(i,j) = themean;
//            end
//
//          end
//        end

        final Matrix means = new Matrix(rows, rows);
        final Matrix stds = new Matrix(rows, rows);

        final Matrix BFinal = new Matrix(rows, rows);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < rows; j++) {
                double sum = 0.0;

                for (int y = 0; y < npieces; y++) {
                    sum += bpieces.get(y).get(i, j);
                }

                final double themean = sum / (npieces);

                double sumVar = 0.0;

                for (int y = 0; y < npieces; y++) {
                    sumVar += Math.pow((bpieces.get(y).get(i, j)) - themean, 2);
                }

                final double thestd = Math.sqrt(sumVar / (npieces));

                means.set(i, j, themean);
                stds.set(i, j, thestd);

                if (Math.abs(themean) < this.threshold * thestd) {//  getPruneFactor() * thestd) {
                    BFinal.set(i, j, 0);
                } else {
                    BFinal.set(i, j, themean);
                }
            }
        }

        return BFinal;
    }

    private void makeDataSet(final GraphWithParameters graphWP) {

        //define the "Gaussian-squared" distribution
        final Distribution gp2 = new GaussianPower(2);

        //the coefficients of the error terms  (here, all 1s)
        final Vector errorCoefficients = getErrorCoeffsIdentity(graphWP.getGraph().getNumNodes());

        //generate data from the SEM
        final Matrix inVectors = simulateCyclic(graphWP, errorCoefficients, this.numSamples, gp2);

        //reformat it
        this.dataSet = new BoxDataSet(new DoubleDataBox(inVectors.transpose().toArray()), graphWP.getGraph().getNodes());
    }

    private int[] range(final int i1, final int i2) {
        if (i2 < i1) throw new IllegalArgumentException("i2 must be >=  i2 " + i1 + ", " + i2);
        final int[] series = new int[i2 - i1 + 1];
        for (int j = i1; j <= i2; j++) series[j - i1] = j;
        return series;
    }

    /**
     * Processes the search algorithm.
     *
     * @param n The number of variables.
     * @return StoredGraphs
     */
    private static Vector getErrorCoeffsIdentity(final int n) {
        final Vector errorCoefficients = new Vector(n);
        for (int i = 0; i < n; i++) {
            errorCoefficients.set(i, 1);
        }
        return errorCoefficients;
    }

    // used to produce dataset if one is not provided as the input to the constructor

    private static Matrix simulateCyclic(final GraphWithParameters dwp, final Vector errorCoefficients, final int n, final Distribution distribution) {
        final Matrix reducedForm = reducedForm(dwp);

        final Matrix vectors = new Matrix(dwp.getGraph().getNumNodes(), n);
        for (int j = 0; j < n; j++) {
            final Vector vector = simulateReducedForm(reducedForm, errorCoefficients, distribution);
            vectors.assignColumn(j, vector);
        }
        return vectors;
    }

    // graph matrix is B
    // mixing matrix (reduced form) is A

    private static Matrix reducedForm(final GraphWithParameters graph) {
        final Matrix graphMatrix = new Matrix(graph.getGraphMatrix().getDoubleData().toArray());
        final int n = graphMatrix.rows();
//        TetradMatrix identityMinusGraphTetradMatrix = TetradMatrixUtils.linearCombination(TetradMatrixUtils.identityTetradMatrix(n), 1, graphTetradMatrix, -1);
        final Matrix identityMinusGraphTetradMatrix = Matrix.identity(n).minus(graphMatrix);
        return identityMinusGraphTetradMatrix.inverse();
    }

    //check against model in which: A =  ..... / (1 - xyzw)

    private static Vector simulateReducedForm(final Matrix reducedForm, final Vector errorCoefficients, final Distribution distr) {
        final int n = reducedForm.rows();
        final Vector vector = new Vector(n);
        final Vector samples = new Vector(n);

        for (int j = 0; j < n; j++) { //sample from each noise term
            final double sample = distr.nextRandom();
            final double errorCoefficient = errorCoefficients.get(j);
            samples.set(j, sample * errorCoefficient);
        }

        for (int i = 0; i < n; i++) { //for each observed variable, i.e. dimension
            double sum = 0;
            for (int j = 0; j < n; j++) {
                final double coefficient = reducedForm.get(i, j);
                final double sample = samples.get(j);
                sum += coefficient * sample;
            }
            vector.set(i, sum);
        }
        return vector;
    }

    //given the W matrix, outputs the list of SEMs consistent with the observed distribution.

    private StoredGraphs findCandidateModels(final List<Node> variables, final Matrix matrixW, final boolean approximateZeros) {

        Matrix normalizedZldW;
        final List<PermutationMatrixPair> zldPerms;

        final StoredGraphs gs = new StoredGraphs();

        System.out.println("Calculating zeroless diagonal permutations...");

        TetradLogger.getInstance().log("lingDetails", "Calculating zeroless diagonal permutations.");
        zldPerms = zerolessDiagonalPermutations(matrixW, approximateZeros, variables, this.dataSet);

        System.out.println("Calculated zeroless diagonal permutations.");

        //for each W~, compute a candidate B, and score it
        for (final PermutationMatrixPair zldPerm : zldPerms) {
            TetradLogger.getInstance().log("lingDetails", "" + zldPerm);
            System.out.println(zldPerm);

            normalizedZldW = LingUtils.normalizeDiagonal(zldPerm.getMatrixW());
            // Note: add method to deal with this data
            zldPerm.setMatrixBhat(computeBhatTetradMatrix(normalizedZldW, variables)); //B~ = I - W~
            final Matrix doubleData = zldPerm.getMatrixBhat().getDoubleData();
            final boolean isStableTetradMatrix = allEigenvaluesAreSmallerThanOneInModulus(new Matrix(doubleData.toArray()));
            final GraphWithParameters graph = new GraphWithParameters(zldPerm.getMatrixBhat());

            gs.addGraph(graph.getGraph());
            gs.addStable(isStableTetradMatrix);
            gs.addData(zldPerm.getMatrixBhat());

        }

        TetradLogger.getInstance().log("stableGraphs", "Stable Graphs:");

        for (int d = 0; d < gs.getNumGraphs(); d++) {
            if (!gs.isStable(d)) {
                continue;
            }

            TetradLogger.getInstance().log("stableGraphs", "" + gs.getGraph(d));

            if (TetradLogger.getInstance().getLoggerConfig() != null &&
                    TetradLogger.getInstance().getLoggerConfig().isEventActive("stableGraphs")) {
                TetradLogger.getInstance().log("wMatrices", "" + gs.getData(d));
            }
        }

        TetradLogger.getInstance().log("unstableGraphs", "Unstable Graphs:");

        for (int d = 0; d < gs.getNumGraphs(); d++) {
            if (gs.isStable(d)) {
                continue;
            }

            TetradLogger.getInstance().log("unstableGraphs", "" + gs.getGraph(d));

            if (TetradLogger.getInstance().getLoggerConfig() != null &&
                    TetradLogger.getInstance().getLoggerConfig().isEventActive("unstableGraphs")) {
                TetradLogger.getInstance().log("wMatrices", "" + gs.getData(d));
            }
        }

        return gs;
    }

    private StoredGraphs findCandidateModel(final List<Node> variables, final Matrix matrixW, final boolean approximateZeros) {

        Matrix normalizedZldW;
        final List<PermutationMatrixPair> zldPerms;

        final StoredGraphs gs = new StoredGraphs();

        System.out.println("Calculating zeroless diagonal permutations...");

        TetradLogger.getInstance().log("lingDetails", "Calculating zeroless diagonal permutations.");
        zldPerms = zerolessDiagonalPermutation(matrixW, approximateZeros, variables, this.dataSet);


//        zldPerms = zerolessDiagonalPermutations(matrixW, approximateZeros, variables, dataSet);

        System.out.println("Calculated zeroless diagonal permutations.");

        //for each W~, compute a candidate B, and score it
        for (final PermutationMatrixPair zldPerm : zldPerms) {
            TetradLogger.getInstance().log("lingDetails", "" + zldPerm);
            System.out.println(zldPerm);

            normalizedZldW = LingUtils.normalizeDiagonal(zldPerm.getMatrixW());
            // Note: add method to deal with this data
            zldPerm.setMatrixBhat(computeBhatTetradMatrix(normalizedZldW, variables)); //B~ = I - W~
            final Matrix doubleData = zldPerm.getMatrixBhat().getDoubleData();
            final boolean isStableTetradMatrix = allEigenvaluesAreSmallerThanOneInModulus(new Matrix(doubleData.toArray()));
            final GraphWithParameters graph = new GraphWithParameters(zldPerm.getMatrixBhat());

            gs.addGraph(graph.getGraph());
            gs.addStable(isStableTetradMatrix);
            gs.addData(zldPerm.getMatrixBhat());

        }

        TetradLogger.getInstance().log("stableGraphs", "Stable Graphs:");

        for (int d = 0; d < gs.getNumGraphs(); d++) {
            if (!gs.isStable(d)) {
                continue;
            }

            TetradLogger.getInstance().log("stableGraphs", "" + gs.getGraph(d));

            if (TetradLogger.getInstance().getLoggerConfig() != null &&
                    TetradLogger.getInstance().getLoggerConfig().isEventActive("stableGraphs")) {
                TetradLogger.getInstance().log("wMatrices", "" + gs.getData(d));
            }
        }

        TetradLogger.getInstance().log("unstableGraphs", "Unstable Graphs:");

        for (int d = 0; d < gs.getNumGraphs(); d++) {
            if (gs.isStable(d)) {
                continue;
            }

            TetradLogger.getInstance().log("unstableGraphs", "" + gs.getGraph(d));

            if (TetradLogger.getInstance().getLoggerConfig() != null &&
                    TetradLogger.getInstance().getLoggerConfig().isEventActive("unstableGraphs")) {
                TetradLogger.getInstance().log("wMatrices", "" + gs.getData(d));
            }
        }

        return gs;
    }


    private List<PermutationMatrixPair> zerolessDiagonalPermutations(Matrix ica_W, final boolean approximateZeros,
                                                                     final List<Node> vars, final DataSet dataSet) {

        final List<PermutationMatrixPair> permutations = new java.util.Vector();

        if (approximateZeros) {
//            setInsignificantEntriesToZero(ica_W);
            pruneEdgesByResampling(dataSet.getDoubleData());
            ica_W = removeZeroRowsAndCols(ica_W, vars);
        }

        //find assignments
        final Matrix mat = ica_W.transpose();
        //returns all zeroless-diagonal column-permutations

        final List<List<Integer>> nRookAssignments = nRookColumnAssignments(mat, makeAllRows(mat.rows()));

        //for each assignment, add the corresponding permutation to 'permutations'
        for (final List<Integer> permutation : nRookAssignments) {
            final Matrix matrixW = permuteRows(ica_W, permutation).transpose();
            final PermutationMatrixPair permTetradMatrixPair = new PermutationMatrixPair(permutation, matrixW);
            permutations.add(permTetradMatrixPair);
        }

        return permutations;
    }

    private List<PermutationMatrixPair> zerolessDiagonalPermutation(Matrix ica_W, final boolean approximateZeros,
                                                                    final List<Node> vars, final DataSet dataSet) {

        final List<PermutationMatrixPair> permutations = new java.util.Vector();

        if (approximateZeros) {
//            setInsignificantEntriesToZero(ica_W);
            ica_W = pruneEdgesByResampling(ica_W);
            ica_W = removeZeroRowsAndCols(ica_W, vars);
        }

//        List<PermutationMatrixPair > zldPerms = new ArrayList<PermutationMatrixPair >();

        final List<Integer> perm = new ArrayList<>();

        for (int i = 0; i < vars.size(); i++) perm.add(i);

        final Matrix matrixW = ica_W.transpose();

        final PermutationMatrixPair pair = new PermutationMatrixPair(perm, matrixW);

        permutations.add(pair);


//        //find assignments
//        TetradMatrix mat = ica_W.transpose();
//        //returns all zeroless-diagonal column-permutations
//
//        List<List<Integer>> nRookAssignments = nRookColumnAssignments(mat, makeAllRows(mat.rows()));
//
//        //for each assignment, add the corresponding permutation to 'permutations'
//        for (List<Integer> permutation : nRookAssignments) {
//            TetradMatrix matrixW = permuteRows(ica_W, permutation).transpose();
//            PermutationMatrixPair  permTetradMatrixPair = new PermutationMatrixPair (permutation, matrixW, vars);
//            permutations.add(permTetradMatrixPair);
//        }

        return permutations;
    }

    private Matrix removeZeroRowsAndCols(Matrix w, final List<Node> variables) {

        final Matrix _W = w.copy();
        final List<Node> _variables = new ArrayList<>(variables);
        final List<Integer> remove = new ArrayList<>();

        ROW:
        for (int i = 0; i < _W.rows(); i++) {
            final Vector row = _W.getRow(i);

            for (int j = 0; j < row.size(); j++) {
                if (row.get(j) != 0) continue ROW;
            }

            remove.add(i);

        }

        COLUMN:
        for (int i = 0; i < _W.rows(); i++) {
            final Vector col = _W.getColumn(i);

            for (int j = 0; j < col.size(); j++) {
                if (col.get(j) != 0) continue COLUMN;
            }

            if (!remove.contains((i))) {
                remove.add(i);
            }
        }

        final int[] rows = new int[_W.rows() - remove.size()];

        int count = -1;
        for (int k = 0; k < w.rows(); k++) {
            if (remove.contains(k)) {
                variables.remove(_variables.get(k));
            } else {
                if (!remove.contains(k)) rows[++count] = k;
            }
        }

        w = w.getSelection(rows, rows);

        return w;
    }

    // uses the thresholding criterion

    private void setInsignificantEntriesToZero(final Matrix mat) {
        final int n = mat.rows();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (Math.abs(mat.get(i, j)) < this.threshold) {
                    mat.set(i, j, 0);
                }
            }
        }

        System.out.println("Thresholded W = " + mat);
    }

    private static List<Integer> makeAllRows(final int n) {
        final List<Integer> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            l.add(i);
        }
        return l;
    }

    private static List<List<Integer>> nRookColumnAssignments(final Matrix mat, final List<Integer> availableRows) {
        final List<List<Integer>> concats = new ArrayList<>();

        System.out.println("mat = " + mat);

        final int n = availableRows.size();

        for (int i = 0; i < n; i++) {
            final int currentRowIndex = availableRows.get(i);

            if (mat.get(currentRowIndex, 0) != 0) {
                if (mat.columns() > 1) {
                    final java.util.Vector newAvailableRows = (new java.util.Vector(availableRows));
                    newAvailableRows.removeElement(currentRowIndex);
                    final Matrix subMat = mat.getPart(0, mat.rows() - 1, 1, mat.columns() - 2);
                    final List<List<Integer>> allLater = nRookColumnAssignments(subMat, newAvailableRows);

                    for (final List<Integer> laterPerm : allLater) {
                        laterPerm.add(0, currentRowIndex);
                        concats.add(laterPerm);
                    }
                } else {
                    final List<Integer> l = new ArrayList<>();
                    l.add(currentRowIndex);
                    concats.add(l);
                }
            }
        }

        return concats;
    }

    private static Matrix permuteRows(final Matrix mat, final List<Integer> permutation) {
        final Matrix permutedMat = mat.like();

        for (int j = 0; j < mat.rows(); j++) {
            final Vector row = mat.getRow(j);
            permutedMat.assignRow(permutation.get(j), row);
        }

        return permutedMat;
    }

    //	B^ = I - W~'

    private static DataSet computeBhatTetradMatrix(final Matrix normalizedZldW, final List<Node> nodes) {//, List<Integer> perm) {
        final int size = normalizedZldW.rows();
        final Matrix mat = Matrix.identity(size).minus(normalizedZldW);
        return new BoxDataSet(new DoubleDataBox(mat.toArray()), nodes);
    }

    private static boolean allEigenvaluesAreSmallerThanOneInModulus(final Matrix mat) {

        final EigenDecomposition dec = new EigenDecomposition(new BlockRealMatrix(mat.toArray()));
        final double[] realEigenvalues = dec.getRealEigenvalues();
        final double[] imagEigenvalues = dec.getImagEigenvalues();

        double sum = 0.0;

//        boolean allEigenvaluesSmallerThanOneInModulus = true;
        for (int i = 0; i < realEigenvalues.length; i++) {
            final double realEigenvalue = realEigenvalues[i];
            final double imagEigenvalue = imagEigenvalues[i];
            final double modulus = Math.sqrt(Math.pow(realEigenvalue, 2) + Math.pow(imagEigenvalue, 2));
//			double argument = Math.atan(imagEigenvalue/realEigenvalue);
//			double modulusCubed = Math.pow(modulus, 3);
//			System.out.println("eigenvalue #"+i+" = " + realEigenvalue + "+" + imagEigenvalue + "i");
//			System.out.println("eigenvalue #"+i+" has argument = " + argument);
//			System.out.println("eigenvalue #"+i+" has modulus = " + modulus);
//			System.out.println("eigenvalue #"+i+" has modulus^3 = " + modulusCubed);

            sum += modulus;

            if (modulus >= 1.5) {
                return false;
//                allEigenvaluesSmallerThanOneInModulus = false;
            }
        }
        return true;
//        return allEigenvaluesSmallerThanOneInModulus;

//        return sum / realEigenvalues.size() < 1;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(final ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

    }

//    public double getPruneFactor() {
//        return pruneFactor;
//    }

    /**
     * This small class is used to store graph permutations. It contains basic methods for adding and accessing graphs.
     * <p>
     * It is likely that this class will move elesewhere once the role of algorithm that produce multiple graphs is
     * better defined.
     */

    public static class StoredGraphs implements GraphGroup {

        /**
         * Graph permutations are stored here.
         */
        private final List<Graph> graphs = new ArrayList<>();

        /**
         * Store data for each graph in case the data is needed later
         */
        private final List<DataSet> dataSet = new ArrayList<>();

        /**
         * Boolean valued vector that contains the stability information for its corresponding graph. stable = true
         * means the graph has all eigenvalues with modulus < 1.
         */
        private final List<Boolean> stable = new ArrayList<>();

        /**
         * Gets the number of graphs stored by the class.
         *
         * @return Returns the number of graphs stored in the class
         */
        public int getNumGraphs() {
            return this.graphs.size();
        }

        /**
         * @param g The index of the graph to be returned
         * @return Returns a Graph
         */
        public Graph getGraph(final int g) {
            return this.graphs.get(g);
        }

        /**
         * @param d The index of the graph for which the DataSet is being returned
         * @return Returns a DataSet
         */
        public DataSet getData(final int d) {
            return this.dataSet.get(d);
        }

        /**
         * @param s The index of the graph at which to return the boolean stability information for the permutation
         * @return Returns the shriknig variable value for a specific graph.
         */
        public boolean isStable(final int s) {
            return this.stable.get(s);
        }

        /**
         * Gives a method for adding classes to the class.
         *
         * @param g The graph to add
         */
        public void addGraph(final Graph g) {
            this.graphs.add(g);
        }

        /**
         * A method for adding graph data to the class.
         *
         * @param d The graph to add
         */
        public void addData(final DataSet d) {
            this.dataSet.add(d);
        }

        /**
         * Allows for the adding of shinking information to its corresponding graph. This should be used at the same time as
         * addGraph() if it is to be used. Otherwise, add a method to add data at a specific index.
         *
         * @param s The stability value to set for a graph.
         */
        public void addStable(final Boolean s) {
            this.stable.add(s);
        }
    }

}


