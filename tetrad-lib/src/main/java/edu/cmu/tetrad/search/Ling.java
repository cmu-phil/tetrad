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
    private long elapsedTime;

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
    public Ling(DataSet d) {
        this.dataSet = d;
    }

    /**
     * When you don't have a Dataset, supply a GraphWithParameters and the number of samples to draw and the algorithm
     * will generate a DataSet.
     *
     * @param graphWP a graph with parameters from GraphWithParameters
     * @param samples the number of samples the algorithm draws in order to generate a DataSet
     */
    public Ling(GraphWithParameters graphWP, int samples) {
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
    public Ling(Graph g, int samples) {
        this.numSamples = samples;
        GraphWithParameters graphWP = new GraphWithParameters(g);
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
        Matrix W;
        StoredGraphs graphs = new StoredGraphs();

        try {
            long sTime = (new Date()).getTime();

            final boolean fastIca = true;

            if (fastIca) {
                W = getWFastIca();

                System.out.println("W = " + W);

                //this is the heart of our method:
                graphs = findCandidateModels(this.dataSet.getVariables(), W);
            } else {
                List<Mapping> allMappings = createMappings(this.dataSet.getNumColumns());

                W = estimateW(new Matrix(this.dataSet.getDoubleData().transpose()),
                        this.dataSet.getNumColumns(), allMappings);

                System.out.println("W = " + W);

                //this is the heart of our method:
                graphs = findCandidateModel(this.dataSet.getVariables(), W);
            }

            this.elapsedTime = (new Date()).getTime() - sTime;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return graphs;
    }


    private Matrix estimateW(Matrix matrix, int numNodes, List<Mapping> allMappings) {
        Matrix W = initializeW(numNodes);
        maxMappings(matrix, W, allMappings);
        return W;
    }

    private void maxMappings(Matrix matrix, Matrix W, List<Mapping> allMappings) {

        int numNodes = W.rows();

        for (int i = 0; i < numNodes; i++) {
            final double maxScore = Double.NEGATIVE_INFINITY;
            double[] maxRow = new double[numNodes];

            for (Mapping mapping : mappingsForRow(i, allMappings)) {
                W.set(mapping.getI(), mapping.getJ(), 0);
            }

            try {
                optimizeNonGaussianity(i, matrix, W, allMappings);
//                optimizeOrthogonality(i, min, max, W, allMappings, W.length);
            } catch (IllegalStateException e) {
                e.printStackTrace();
                continue;
            }

            double v = ngFullData(i, matrix, W);

            if (Double.isNaN(v)) continue;
            if (v >= 9999) continue;

            double[] row = new double[numNodes];
            for (int k = 0; k < numNodes; k++) row[k] = W.get(i, k);

            if (v > maxScore) {
                maxRow = row;
            }

            for (int k = 0; k < numNodes; k++) W.set(i, k, maxRow[k]);
        }
    }

    private void optimizeNonGaussianity(int rowIndex, Matrix dataSetTetradMatrix,
                                        Matrix W, List<Mapping> allMappings) {
        List<Mapping> mappings = mappingsForRow(rowIndex, allMappings);

        MultivariateFunction function = values -> {
            for (int i = 0; i < values.length; i++) {
                Mapping mapping = mappings.get(i);
                W.set(mapping.getI(), mapping.getJ(), values[i]);
            }

            double v = ngFullData(rowIndex, dataSetTetradMatrix, W);

            if (Double.isNaN(v)) return 10000;

            return -(v);
        };

        {
            double[] values = new double[mappings.size()];

            for (int k = 0; k < mappings.size(); k++) {
                Mapping mapping = mappings.get(k);
                values[k] = W.get(mapping.getI(), mapping.getJ());
            }

            MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);

            PointValuePair pair = search.optimize(
                    new InitialGuess(values),
                    new ObjectiveFunction(function),
                    GoalType.MINIMIZE,
                    new MaxEval(100000));

            values = pair.getPoint();

            for (int k = 0; k < mappings.size(); k++) {
                Mapping mapping = mappings.get(k);
                W.set(mapping.getI(), mapping.getJ(), values[k]);
            }
        }

    }

    public double ngFullData(int rowIndex, Matrix data, Matrix W) {
        double[] col = new double[data.rows()];

        for (int i = 0; i < data.rows(); i++) {
            double d = 0.0;

            // Node _x given parents. Its coefficient is fixed at 1. Also, coefficients for all
            // other variables not neighbors of _x are fixed at zero.
            for (int j = 0; j < data.columns(); j++) {
                double coef = W.get(rowIndex, j);
                double value = data.get(i, j);
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

        for (double v : col) {
            sum += log(cosh(v));
        }

        return sum / col.length;
//        return new AndersonDarlingTest(col).getASquaredStar();
    }

    private double[] removeNaN(double[] data) {
        List<Double> _leaveOutMissing = new ArrayList<>();

        for (double datum : data) {
            if (!Double.isNaN(datum)) {
                _leaveOutMissing.add(datum);
            }
        }

        double[] _data = new double[_leaveOutMissing.size()];

        for (int i = 0; i < _leaveOutMissing.size(); i++) _data[i] = _leaveOutMissing.get(i);

        return _data;
    }

    private List<Mapping> mappingsForRow(int rowIndex, List<Mapping> allMappings) {
        List<Mapping> mappings = new ArrayList<>();

        for (Mapping mapping : allMappings) {
            if (mapping.getI() == rowIndex) mappings.add(mapping);
        }
        return mappings;
    }

    private Matrix initializeW(int numNodes) {

        // Initialize W to I.
        Matrix W = new Matrix(numNodes, numNodes);

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

    private List<Mapping> createMappings(int numNodes) {

        // Mark as parameters all non-adjacencies from the graph, excluding self edges.
        List<Mapping> allMappings = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numNodes; j++) {
                if (i == j) continue;
                allMappings.add(new Mapping(i, j));
            }
        }

        return allMappings;
    }

    private static class Mapping {
        private final int i;
        private final int j;

        public Mapping(int i, int j) {
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
        Matrix W;// Using this Fast ICA to get the logging.
        Matrix data = new Matrix(this.dataSet.getDoubleData().toArray()).transpose();
        FastIca fastIca = new FastIca(data, 30);
        fastIca.setVerbose(false);
        fastIca.setAlgorithmType(FastIca.DEFLATION);
        fastIca.setFunction(FastIca.LOGCOSH);
        fastIca.setTolerance(.01);
        fastIca.setMaxIterations(1000);
        fastIca.setAlpha(1.0);
        FastIca.IcaResult result = fastIca.findComponents();
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
    public void setThreshold(double t) {
        this.threshold = t;
    }

    //==============================PRIVATE METHODS====================//

    /**
     * This is the method used in Patrik's code.
     */
    public Matrix pruneEdgesByResampling(Matrix data) {
        Matrix X = new Matrix(data.transpose().toArray());

        final int npieces = 10;
        int cols = X.columns();
        int rows = X.rows();

        List<Matrix> bpieces = new ArrayList<>();

        for (int p = 0; p < npieces; p++) {
            Vector Xpm = new Vector(rows);

            for (int i = 0; i < rows; i++) {
                double sum = 0.0;

                for (int j = 0; j < X.columns(); j++) {
                    sum += X.get(i, j);
                }

                Xpm.set(i, sum / X.columns());
            }

            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < X.columns(); j++) {
                    X.set(i, j, X.get(i, j) - Xpm.get(i));
                }
            }

            Matrix Xpt = X.transpose();

            Matrix cov = X.times(Xpt);

            for (int i = 0; i < cov.rows(); i++) {
                for (int j = 0; j < cov.columns(); j++) {
                    cov.set(i, j, cov.get(i, j) / X.columns());
                }
            }

            boolean posDef = LingUtils.isPositiveDefinite(cov);

            if (!posDef) {
                System.out.println("Covariance matrix is not positive definite.");
            }

            Matrix sqrt = cov.sqrt();

            Matrix I = Matrix.identity(rows);
            I.copy();
            Matrix invSqrt = sqrt.inverse();

            QRDecomposition qr = new QRDecomposition(new BlockRealMatrix(invSqrt.toArray()));
            RealMatrix r = qr.getR();

            Vector newestdisturbancestd = new Vector(rows);

            for (int t = 0; t < rows; t++) {
                newestdisturbancestd.set(t, 1.0 / abs(r.getEntry(t, t)));
            }

            for (int s = 0; s < rows; s++) {
                for (int t = 0; t < min(s, cols); t++) {
                    r.setEntry(s, t, r.getEntry(s, t) / r.getEntry(s, s));
                }
            }

            Matrix bnewest = Matrix.identity(rows);
            bnewest = bnewest.minus(new Matrix(r.getData()));

            bpieces.add(bnewest);
        }

        Matrix means = new Matrix(rows, rows);
        Matrix stds = new Matrix(rows, rows);

        Matrix BFinal = new Matrix(rows, rows);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < rows; j++) {
                double sum = 0.0;

                for (int y = 0; y < npieces; y++) {
                    sum += bpieces.get(y).get(i, j);
                }

                double themean = sum / (npieces);

                double sumVar = 0.0;

                for (int y = 0; y < npieces; y++) {
                    sumVar += pow((bpieces.get(y).get(i, j)) - themean, 2);
                }

                double thestd = sqrt(sumVar / (npieces));

                means.set(i, j, themean);
                stds.set(i, j, thestd);

                if (abs(themean) < this.threshold * thestd) {//  getPruneFactor() * thestd) {
                    BFinal.set(i, j, 0);
                } else {
                    BFinal.set(i, j, themean);
                }
            }
        }

        return BFinal;
    }

    private void makeDataSet(GraphWithParameters graphWP) {

        //define the "Gaussian-squared" distribution
        Distribution gp2 = new GaussianPower(2);

        //the coefficients of the error terms  (here, all 1s)
        Vector errorCoefficients = Ling.getErrorCoeffsIdentity(graphWP.getGraph().getNumNodes());

        //generate data from the SEM
        Matrix inVectors = Ling.simulateCyclic(graphWP, errorCoefficients, this.numSamples, gp2);

        //reformat it
        this.dataSet = new BoxDataSet(new DoubleDataBox(inVectors.transpose().toArray()), graphWP.getGraph().getNodes());
    }

    /**
     * Processes the search algorithm.
     *
     * @param n The number of variables.
     * @return StoredGraphs
     */
    private static Vector getErrorCoeffsIdentity(int n) {
        Vector errorCoefficients = new Vector(n);
        for (int i = 0; i < n; i++) {
            errorCoefficients.set(i, 1);
        }
        return errorCoefficients;
    }

    // used to produce dataset if one is not provided as the input to the constructor

    private static Matrix simulateCyclic(GraphWithParameters dwp, Vector errorCoefficients, int n, Distribution distribution) {
        Matrix reducedForm = Ling.reducedForm(dwp);

        Matrix vectors = new Matrix(dwp.getGraph().getNumNodes(), n);
        for (int j = 0; j < n; j++) {
            Vector vector = Ling.simulateReducedForm(reducedForm, errorCoefficients, distribution);
            vectors.assignColumn(j, vector);
        }
        return vectors;
    }

    // graph matrix is B
    // mixing matrix (reduced form) is A

    private static Matrix reducedForm(GraphWithParameters graph) {
        Matrix graphMatrix = new Matrix(graph.getGraphMatrix().getDoubleData().toArray());
        int n = graphMatrix.rows();
//        TetradMatrix identityMinusGraphTetradMatrix = TetradMatrixUtils.linearCombination(TetradMatrixUtils.identityTetradMatrix(n), 1, graphTetradMatrix, -1);
        Matrix identityMinusGraphTetradMatrix = Matrix.identity(n).minus(graphMatrix);
        return identityMinusGraphTetradMatrix.inverse();
    }

    //check against model in which: A =  ..... / (1 - xyzw)

    private static Vector simulateReducedForm(Matrix reducedForm, Vector errorCoefficients, Distribution distr) {
        int n = reducedForm.rows();
        Vector vector = new Vector(n);
        Vector samples = new Vector(n);

        for (int j = 0; j < n; j++) { //sample from each noise term
            double sample = distr.nextRandom();
            double errorCoefficient = errorCoefficients.get(j);
            samples.set(j, sample * errorCoefficient);
        }

        for (int i = 0; i < n; i++) { //for each observed variable, i.e. dimension
            double sum = 0;
            for (int j = 0; j < n; j++) {
                double coefficient = reducedForm.get(i, j);
                double sample = samples.get(j);
                sum += coefficient * sample;
            }
            vector.set(i, sum);
        }
        return vector;
    }

    //given the W matrix, outputs the list of SEMs consistent with the observed distribution.

    private StoredGraphs findCandidateModels(List<Node> variables, Matrix matrixW) {

        Matrix normalizedZldW;
        List<PermutationMatrixPair> zldPerms;

        StoredGraphs gs = new StoredGraphs();

        System.out.println("Calculating zeroless diagonal permutations...");

        TetradLogger.getInstance().log("lingDetails", "Calculating zeroless diagonal permutations.");
        zldPerms = zerolessDiagonalPermutations(matrixW, variables, this.dataSet);

        System.out.println("Calculated zeroless diagonal permutations.");

        //for each W~, compute a candidate B, and score it
        for (PermutationMatrixPair zldPerm : zldPerms) {
            TetradLogger.getInstance().log("lingDetails", "" + zldPerm);
            System.out.println(zldPerm);

            normalizedZldW = LingUtils.normalizeDiagonal(zldPerm.getMatrixW());
            // Note: add method to deal with this data
            zldPerm.setMatrixBhat(Ling.computeBhatTetradMatrix(normalizedZldW, variables)); //B~ = I - W~
            Matrix doubleData = zldPerm.getMatrixBhat().getDoubleData();
            boolean isStableTetradMatrix = Ling.allEigenvaluesAreSmallerThanOneInModulus(new Matrix(doubleData.toArray()));
            GraphWithParameters graph = new GraphWithParameters(zldPerm.getMatrixBhat());

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

    private StoredGraphs findCandidateModel(List<Node> variables, Matrix matrixW) {

        Matrix normalizedZldW;
        List<PermutationMatrixPair> zldPerms;

        StoredGraphs gs = new StoredGraphs();

        System.out.println("Calculating zeroless diagonal permutations...");

        TetradLogger.getInstance().log("lingDetails", "Calculating zeroless diagonal permutations.");
        zldPerms = zerolessDiagonalPermutation(matrixW, variables);


//        zldPerms = zerolessDiagonalPermutations(matrixW, approximateZeros, variables, dataSet);

        System.out.println("Calculated zeroless diagonal permutations.");

        //for each W~, compute a candidate B, and score it
        for (PermutationMatrixPair zldPerm : zldPerms) {
            TetradLogger.getInstance().log("lingDetails", "" + zldPerm);
            System.out.println(zldPerm);

            normalizedZldW = LingUtils.normalizeDiagonal(zldPerm.getMatrixW());
            // Note: add method to deal with this data
            zldPerm.setMatrixBhat(Ling.computeBhatTetradMatrix(normalizedZldW, variables)); //B~ = I - W~
            Matrix doubleData = zldPerm.getMatrixBhat().getDoubleData();
            boolean isStableTetradMatrix = Ling.allEigenvaluesAreSmallerThanOneInModulus(new Matrix(doubleData.toArray()));
            GraphWithParameters graph = new GraphWithParameters(zldPerm.getMatrixBhat());

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


    private List<PermutationMatrixPair> zerolessDiagonalPermutations(Matrix ica_W, List<Node> vars,
                                                                     DataSet dataSet) {

        List<PermutationMatrixPair> permutations = new java.util.Vector<>();

        pruneEdgesByResampling(dataSet.getDoubleData());
        ica_W = removeZeroRowsAndCols(ica_W, vars);

        //find assignments
        Matrix mat = ica_W.transpose();
        //returns all zeroless-diagonal column-permutations

        List<List<Integer>> nRookAssignments = Ling.nRookColumnAssignments(mat, Ling.makeAllRows(mat.rows()));

        //for each assignment, add the corresponding permutation to 'permutations'
        for (List<Integer> permutation : nRookAssignments) {
            Matrix matrixW = Ling.permuteRows(ica_W, permutation).transpose();
            PermutationMatrixPair permTetradMatrixPair = new PermutationMatrixPair(permutation, matrixW);
            permutations.add(permTetradMatrixPair);
        }

        return permutations;
    }

    private List<PermutationMatrixPair> zerolessDiagonalPermutation(Matrix ica_W, List<Node> vars) {

        List<PermutationMatrixPair> permutations = new java.util.Vector<>();

        ica_W = pruneEdgesByResampling(ica_W);
        ica_W = removeZeroRowsAndCols(ica_W, vars);

        List<Integer> perm = new ArrayList<>();

        for (int i = 0; i < vars.size(); i++) perm.add(i);

        Matrix matrixW = ica_W.transpose();

        PermutationMatrixPair pair = new PermutationMatrixPair(perm, matrixW);
        permutations.add(pair);

        return permutations;
    }

    private Matrix removeZeroRowsAndCols(Matrix w, List<Node> variables) {

        Matrix _W = w.copy();
        List<Node> _variables = new ArrayList<>(variables);
        List<Integer> remove = new ArrayList<>();

        ROW:
        for (int i = 0; i < _W.rows(); i++) {
            Vector row = _W.getRow(i);

            for (int j = 0; j < row.size(); j++) {
                if (row.get(j) != 0) continue ROW;
            }

            remove.add(i);

        }

        COLUMN:
        for (int i = 0; i < _W.rows(); i++) {
            Vector col = _W.getColumn(i);

            for (int j = 0; j < col.size(); j++) {
                if (col.get(j) != 0) continue COLUMN;
            }

            if (!remove.contains((i))) {
                remove.add(i);
            }
        }

        int[] rows = new int[_W.rows() - remove.size()];

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

    private static List<Integer> makeAllRows(int n) {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            l.add(i);
        }
        return l;
    }

    private static List<List<Integer>> nRookColumnAssignments(Matrix mat, List<Integer> availableRows) {
        List<List<Integer>> concats = new ArrayList<>();

        System.out.println("mat = " + mat);

        int n = availableRows.size();

        for (int i = 0; i < n; i++) {
            int currentRowIndex = availableRows.get(i);

            if (mat.get(currentRowIndex, 0) != 0) {
                if (mat.columns() > 1) {
                    java.util.Vector<Integer> newAvailableRows = (new java.util.Vector<>(availableRows));
                    newAvailableRows.removeElement(currentRowIndex);
                    Matrix subMat = mat.getPart(0, mat.rows() - 1, 1, mat.columns() - 2);
                    List<List<Integer>> allLater = Ling.nRookColumnAssignments(subMat, newAvailableRows);

                    for (List<Integer> laterPerm : allLater) {
                        laterPerm.add(0, currentRowIndex);
                        concats.add(laterPerm);
                    }
                } else {
                    List<Integer> l = new ArrayList<>();
                    l.add(currentRowIndex);
                    concats.add(l);
                }
            }
        }

        return concats;
    }

    private static Matrix permuteRows(Matrix mat, List<Integer> permutation) {
        Matrix permutedMat = mat.like();

        for (int j = 0; j < mat.rows(); j++) {
            Vector row = mat.getRow(j);
            permutedMat.assignRow(permutation.get(j), row);
        }

        return permutedMat;
    }

    //	B^ = I - W~'

    private static DataSet computeBhatTetradMatrix(Matrix normalizedZldW, List<Node> nodes) {//, List<Integer> perm) {
        int size = normalizedZldW.rows();
        Matrix mat = Matrix.identity(size).minus(normalizedZldW);
        return new BoxDataSet(new DoubleDataBox(mat.toArray()), nodes);
    }

    private static boolean allEigenvaluesAreSmallerThanOneInModulus(Matrix mat) {

        EigenDecomposition dec = new EigenDecomposition(new BlockRealMatrix(mat.toArray()));
        double[] realEigenvalues = dec.getRealEigenvalues();
        double[] imagEigenvalues = dec.getImagEigenvalues();

        for (int i = 0; i < realEigenvalues.length; i++) {
            double realEigenvalue = realEigenvalues[i];
            double imagEigenvalue = imagEigenvalues[i];
            double modulus = sqrt(pow(realEigenvalue, 2) + pow(imagEigenvalue, 2));

            if (modulus >= 1.5) {
                return false;
            }
        }

        return true;
    }

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
        public Graph getGraph(int g) {
            return this.graphs.get(g);
        }

        /**
         * @param d The index of the graph for which the DataSet is being returned
         * @return Returns a DataSet
         */
        public DataSet getData(int d) {
            return this.dataSet.get(d);
        }

        /**
         * @param s The index of the graph at which to return the boolean stability information for the permutation
         * @return Returns the shriknig variable value for a specific graph.
         */
        public boolean isStable(int s) {
            return this.stable.get(s);
        }

        /**
         * Gives a method for adding classes to the class.
         *
         * @param g The graph to add
         */
        public void addGraph(Graph g) {
            this.graphs.add(g);
        }

        /**
         * A method for adding graph data to the class.
         *
         * @param d The graph to add
         */
        public void addData(DataSet d) {
            this.dataSet.add(d);
        }

        /**
         * Allows for the adding of shinking information to its corresponding graph. This should be used at the same time as
         * addGraph() if it is to be used. Otherwise, add a method to add data at a specific index.
         *
         * @param s The stability value to set for a graph.
         */
        public void addStable(Boolean s) {
            this.stable.add(s);
        }
    }

}


