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

import edu.cmu.tetrad.data.ColtDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphGroup;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.LingUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;
import edu.cmu.tetrad.util.dist.Distribution;
import edu.cmu.tetrad.util.dist.GaussianPower;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.linear.*;
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
import java.util.Vector;

import static java.lang.Math.cosh;
import static java.lang.Math.log;
import static java.lang.Math.min;

/**
 * The code used within this class is largely Gustave Lacerda's, which corresponds to his essay, Discovering Cyclic
 * Causal Models by Independent Components Analysis. The code models the LiNG algorithm.
 *
 * <p>Note: This code is currently broken; please do not use it until it's fixed. 11/24/2015</p>
 */
public class Ling implements GraphGroupSearch {

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
    public Ling(DataSet d) {
        dataSet = d;
    }

    /**
     * When you don't have a Dataset, supply a GraphWithParameters and the number of samples to draw and the algorithm
     * will generate a DataSet.
     *
     * @param graphWP a graph with parameters from GraphWithParameters
     * @param samples the number of samples the algorithm draws in order to generate a DataSet
     */
    public Ling(GraphWithParameters graphWP, int samples) {
        numSamples = samples;
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
        numSamples = samples;
        GraphWithParameters graphWP = new GraphWithParameters(g);
        makeDataSet(graphWP);
    }

    //==============================PUBLIC METHODS=========================//

    /**
     * @return DataSet   Returns a dataset of the data used by the algorithm.
     */
    public DataSet getData() {
        return dataSet;
    }

    /**
     * The search method is used to process LiNG. Call search when you want to run the algorithm.
     */
    public StoredGraphs search() {
        TetradMatrix A, W;
        StoredGraphs graphs = new StoredGraphs();

        try {
            long sTime = (new Date()).getTime();

            boolean fastIca = true;

            if (fastIca) {
                W = getWFastIca();

                System.out.println("W = " + W);

                //this is the heart of our method:
                graphs = findCandidateModels(dataSet.getVariables(), W, true);
            } else {
                double zeta = 1;

                final List<Mapping> allMappings = createMappings(null, null, dataSet.getNumColumns());

                double[][] _w = estimateW(new TetradMatrix(dataSet.getDoubleData().toArray()),
                        dataSet.getNumColumns(), -zeta, zeta, allMappings);
                W = new TetradMatrix(_w);

                System.out.println("W = " + W);

                //this is the heart of our method:
                graphs = findCandidateModel(dataSet.getVariables(), W, true);
            }

            elapsedTime = (new Date()).getTime() - sTime;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return graphs;
    }


    private double[][] estimateW(TetradMatrix matrix, int numNodes, double min, double max, List<Mapping> allMappings) {
        double[][] W = initializeW(numNodes);
        maxMappings(matrix, min, max, W, allMappings);
        return W;
    }

    private void maxMappings(final TetradMatrix matrix, final double min,
                             final double max, final double[][] W, final List<Mapping> allMappings) {

        final int numNodes = W.length;

        for (int i = 0; i < numNodes; i++) {
            double maxScore = Double.NEGATIVE_INFINITY;
            double[] maxRow = new double[numNodes];

            for (Mapping mapping : mappingsForRow(i, allMappings)) {
                W[mapping.getI()][mapping.getJ()] = 0;
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
            for (int k = 0; k < numNodes; k++) row[k] = W[i][k];

            if (v > maxScore) {
                maxRow = row;
            }

            for (int k = 0; k < numNodes; k++) W[i][k] = maxRow[k];
        }
    }

    private void optimizeNonGaussianity(final int rowIndex, final TetradMatrix dataSetTetradMatrix,
                                        final double[][] W, List<Mapping> allMappings) {
        final List<Mapping> mappings = mappingsForRow(rowIndex, allMappings);

        MultivariateFunction function = new MultivariateFunction() {
            public double value(double[] values) {
                for (int i = 0; i < values.length; i++) {
                    Mapping mapping = mappings.get(i);
                    W[mapping.getI()][mapping.getJ()] = values[i];
                }

                double v = ngFullData(rowIndex, dataSetTetradMatrix, W);

                if (Double.isNaN(v)) return 10000;

                return -(v);
            }
        };

        {
            double[] values = new double[mappings.size()];

            for (int k = 0; k < mappings.size(); k++) {
                Mapping mapping = mappings.get(k);
                values[k] = W[mapping.getI()][mapping.getJ()];
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
                W[mapping.getI()][mapping.getJ()] = values[k];
            }
        }

    }

    public double ngFullData(int rowIndex, TetradMatrix data, double[][] W) {
        double[] col = new double[data.rows()];

        for (int i = 0; i < data.rows(); i++) {
            double d = 0.0;

            // Node _x given parents. Its coefficient is fixed at 1. Also, coefficients for all
            // other variables not neighbors of _x are fixed at zero.
            for (int j = 0; j < data.columns(); j++) {
                double coef = W[rowIndex][j];
                Double value = data.get(i, j);
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

    private double[] removeNaN(double[] data) {
        List<Double> _leaveOutMissing = new ArrayList<Double>();

        for (int i = 0; i < data.length; i++) {
            if (!Double.isNaN(data[i])) {
                _leaveOutMissing.add(data[i]);
            }
        }

        double[] _data = new double[_leaveOutMissing.size()];

        for (int i = 0; i < _leaveOutMissing.size(); i++) _data[i] = _leaveOutMissing.get(i);

        return _data;
    }

    private List<Mapping> mappingsForRow(int rowIndex, List<Mapping> allMappings) {
        final List<Mapping> mappings = new ArrayList<Mapping>();

        for (Mapping mapping : allMappings) {
            if (mapping.getI() == rowIndex) mappings.add(mapping);
        }
        return mappings;
    }

    private double[][] initializeW(int numNodes) {

        // Initialize W to I.
        double[][] W = new double[numNodes][numNodes];

        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numNodes; j++) {
                if (i == j) {
                    W[i][j] = 1.0;
                } else {
                    W[i][j] = 0.0;
                }
            }
        }
        return W;
    }

    private List<Mapping> createMappings(Graph graph, List<Node> nodes, int numNodes) {

        // Mark as parameters all non-adjacencies from the graph, excluding self edges.
        final List<Mapping> allMappings = new ArrayList<Mapping>();

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

        public Mapping(int i, int j) {
            this.i = i;
            this.j = j;
        }

        public int getI() {
            return i;
        }

        public int getJ() {
            return j;
        }
    }

    private TetradMatrix getWFastIca() {
        TetradMatrix A;
        TetradMatrix W;// Using this Fast ICA to get the logging.
        TetradMatrix data = new TetradMatrix(dataSet.getDoubleData().toArray());
        FastIca fastIca = new FastIca(data, data.columns());
        fastIca.setVerbose(false);
        fastIca.setAlgorithmType(FastIca.DEFLATION);
        fastIca.setFunction(FastIca.LOGCOSH);
        fastIca.setTolerance(1e-20);
        fastIca.setMaxIterations(500);
        fastIca.setAlpha(1.0);
        FastIca.IcaResult result = fastIca.findComponents();
        A = new TetradMatrix(result.getA().transpose().toArray());
        W = A.inverse();
        return W;
    }

    /**
     * Calculates the time used when processing the search method.
     */
    public long getElapsedTime() {
        return elapsedTime;
    }

    /**
     * Sets the value at which thresholding occurs on Fast ICA data. Default is .05.
     *
     * @param t The value at which the thresholding is set
     */
    public void setThreshold(double t) {
        threshold = t;
    }

    //==============================PRIVATE METHODS====================//

    /**
     * This is the method used in Patrik's code.
     */
    public TetradMatrix pruneEdgesByResampling(TetradMatrix data) {
        TetradMatrix X = new TetradMatrix(data.transpose().toArray());

        int npieces = 10;
        int cols = X.columns();
        int rows = X.rows();
        int piecesize = (int) Math.floor(cols / npieces);

        List<TetradMatrix> bpieces = new ArrayList<TetradMatrix>();
        List<TetradVector> diststdpieces = new ArrayList<TetradVector>();
        List<TetradVector> cpieces = new ArrayList<TetradVector>();

        for (int p = 0; p < npieces; p++) {

//          % Select subset of data, and permute the variables to the causal order
//          Xp = X(k,((p-1)*piecesize+1):(p*piecesize));

            int p0 = (p) * piecesize;
            int p1 = (p + 1) * piecesize - 1;
            int[] range = range(p0, p1);


            TetradMatrix Xp = X;

//          % Remember to subract out the mean
//          Xpm = mean(Xp,2);
//          Xp = Xp - Xpm*ones(1,size(Xp,2));
//
//          % Calculate covariance matrix
//          cov = (Xp*Xp')/size(Xp,2);

            TetradVector Xpm = new TetradVector(rows);

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


            TetradMatrix Xpt = Xp.transpose();

            TetradMatrix cov = Xp.times(Xpt);

            for (int i = 0; i < cov.rows(); i++) {
                for (int j = 0; j < cov.columns(); j++) {
                    cov.set(i, j, cov.get(i, j) / Xp.columns());
                }
            }

//          % Do QL decomposition on the inverse square root of cov
//          [Q,L] = tridecomp(cov^(-0.5),'ql');

            boolean posDef = LingUtils.isPositiveDefinite(cov);
//            TetradLogger.getInstance().log("lingamDetails","Positive definite = " + posDef);

            if (!posDef) {
                System.out.println("Covariance matrix is not positive definite.");
            }

            TetradMatrix sqrt = cov.sqrt();;

            TetradMatrix I = TetradMatrix.identity(rows);
            TetradMatrix AI = I.copy();
            TetradMatrix invSqrt = sqrt.inverse();

            QRDecomposition qr = new QRDecomposition(invSqrt.getRealMatrix());
            RealMatrix r = qr.getR();

//          % The estimated disturbance-stds are one over the abs of the diag of L
//          newestdisturbancestd = 1./diag(abs(L));

            TetradVector newestdisturbancestd = new TetradVector(rows);

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

            TetradMatrix bnewest = TetradMatrix.identity(rows);
            bnewest = bnewest.minus(new TetradMatrix(r));

            TetradVector cnewest = new TetradMatrix(r).times(Xpm);

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

        TetradMatrix means = new TetradMatrix(rows, rows);
        TetradMatrix stds = new TetradMatrix(rows, rows);

        TetradMatrix BFinal = new TetradMatrix(rows, rows);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < rows; j++) {
                double sum = 0.0;

                for (int y = 0; y < npieces; y++) {
                    sum += bpieces.get(y).get(i, j);
                }

                double themean = sum / (npieces);

                double sumVar = 0.0;

                for (int y = 0; y < npieces; y++) {
                    sumVar += Math.pow((bpieces.get(y).get(i, j)) - themean, 2);
                }

                double thestd = Math.sqrt(sumVar / (npieces));

                means.set(i, j, themean);
                stds.set(i, j, thestd);

                if (Math.abs(themean) < threshold * thestd) {//  getPruneFactor() * thestd) {
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
        TetradVector errorCoefficients = getErrorCoeffsIdentity(graphWP.getGraph().getNumNodes());

        //generate data from the SEM
        TetradMatrix inVectors = simulateCyclic(graphWP, errorCoefficients, numSamples, gp2);

        //reformat it
        dataSet = ColtDataSet.makeContinuousData(graphWP.getGraph().getNodes(), new TetradMatrix(inVectors.transpose().toArray()));
    }

    private int[] range(int i1, int i2) {
        if (i2 < i1) throw new IllegalArgumentException("i2 must be >=  i2 " + i1 + ", " + i2);
        int series[] = new int[i2 - i1 + 1];
        for (int j = i1; j <= i2; j++) series[j - i1] = j;
        return series;
    }

    /**
     * Processes the search algorithm.
     *
     * @param n The number of variables.
     * @return StoredGraphs
     */
    private static TetradVector getErrorCoeffsIdentity(int n) {
        TetradVector errorCoefficients = new TetradVector(n);
        for (int i = 0; i < n; i++) {
            errorCoefficients.set(i, 1);
        }
        return errorCoefficients;
    }

    // used to produce dataset if one is not provided as the input to the constructor

    private static TetradMatrix simulateCyclic(GraphWithParameters dwp, TetradVector errorCoefficients, int n, Distribution distribution) {
        TetradMatrix reducedForm = reducedForm(dwp);

        TetradMatrix vectors = new TetradMatrix(dwp.getGraph().getNumNodes(), n);
        for (int j = 0; j < n; j++) {
            TetradVector vector = simulateReducedForm(reducedForm, errorCoefficients, distribution);
            vectors.assignColumn(j, vector);
        }
        return vectors;
    }

    // graph matrix is B
    // mixing matrix (reduced form) is A

    private static TetradMatrix reducedForm(GraphWithParameters graph) {
        TetradMatrix graphMatrix = new TetradMatrix(graph.getGraphMatrix().getDoubleData().toArray());
        int n = graphMatrix.rows();
//        TetradMatrix identityMinusGraphTetradMatrix = TetradMatrixUtils.linearCombination(TetradMatrixUtils.identityTetradMatrix(n), 1, graphTetradMatrix, -1);
        TetradMatrix identityMinusGraphTetradMatrix = TetradMatrix.identity(n).minus(graphMatrix);
        return identityMinusGraphTetradMatrix.inverse();
    }

    //check against model in which: A =  ..... / (1 - xyzw)

    private static TetradVector simulateReducedForm(TetradMatrix reducedForm, TetradVector errorCoefficients, Distribution distr) {
        int n = reducedForm.rows();
        TetradVector vector = new TetradVector(n);
        TetradVector samples = new TetradVector(n);

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

    private StoredGraphs findCandidateModels(List<Node> variables, TetradMatrix matrixW, boolean approximateZeros) {

        TetradMatrix normalizedZldW;
        List<PermutationMatrixPair> zldPerms;

        StoredGraphs gs = new StoredGraphs();

        System.out.println("Calculating zeroless diagonal permutations...");

        TetradLogger.getInstance().log("lingDetails", "Calculating zeroless diagonal permutations.");
        zldPerms = zerolessDiagonalPermutations(matrixW, approximateZeros, variables, dataSet);

        System.out.println("Calculated zeroless diagonal permutations.");

        //for each W~, compute a candidate B, and score it
        for (PermutationMatrixPair zldPerm : zldPerms) {
            TetradLogger.getInstance().log("lingDetails", "" + zldPerm);
            System.out.println(zldPerm);

            normalizedZldW = LingUtils.normalizeDiagonal(zldPerm.getMatrixW());
            // Note: add method to deal with this data
            zldPerm.setMatrixBhat(computeBhatTetradMatrix(normalizedZldW, variables)); //B~ = I - W~
            TetradMatrix doubleData = zldPerm.getMatrixBhat().getDoubleData();
            boolean isStableTetradMatrix = allEigenvaluesAreSmallerThanOneInModulus(new TetradMatrix(doubleData.toArray()));
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

    private StoredGraphs findCandidateModel(List<Node> variables, TetradMatrix matrixW, boolean approximateZeros) {

        TetradMatrix normalizedZldW;
        List<PermutationMatrixPair > zldPerms;

        StoredGraphs gs = new StoredGraphs();

        System.out.println("Calculating zeroless diagonal permutations...");

        TetradLogger.getInstance().log("lingDetails", "Calculating zeroless diagonal permutations.");
        zldPerms = zerolessDiagonalPermutation(matrixW, approximateZeros, variables, dataSet);


//        zldPerms = zerolessDiagonalPermutations(matrixW, approximateZeros, variables, dataSet);

        System.out.println("Calculated zeroless diagonal permutations.");

        //for each W~, compute a candidate B, and score it
        for (PermutationMatrixPair  zldPerm : zldPerms) {
            TetradLogger.getInstance().log("lingDetails", "" + zldPerm);
            System.out.println(zldPerm);

            normalizedZldW = LingUtils.normalizeDiagonal(zldPerm.getMatrixW());
            // Note: add method to deal with this data
            zldPerm.setMatrixBhat(computeBhatTetradMatrix(normalizedZldW, variables)); //B~ = I - W~
            TetradMatrix doubleData = zldPerm.getMatrixBhat().getDoubleData();
            boolean isStableTetradMatrix = allEigenvaluesAreSmallerThanOneInModulus(new TetradMatrix(doubleData.toArray()));
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


    private List<PermutationMatrixPair> zerolessDiagonalPermutations(TetradMatrix ica_W, boolean approximateZeros,
                                                                           List<Node> vars, DataSet dataSet) {

        List<PermutationMatrixPair> permutations = new Vector<PermutationMatrixPair>();

        if (approximateZeros) {
//            setInsignificantEntriesToZero(ica_W);
            pruneEdgesByResampling(dataSet.getDoubleData());
            ica_W = removeZeroRowsAndCols(ica_W, vars);
        }

        //find assignments
        TetradMatrix mat = ica_W.transpose();
        //returns all zeroless-diagonal column-permutations

        System.out.println("AAA");

        List<List<Integer>> nRookAssignments = nRookColumnAssignments(mat, makeAllRows(mat.rows()));

        System.out.println("BBB");

        //for each assignment, add the corresponding permutation to 'permutations'
        for (List<Integer> permutation : nRookAssignments) {
            TetradMatrix matrixW = permuteRows(ica_W, permutation).transpose();
            PermutationMatrixPair permTetradMatrixPair = new PermutationMatrixPair(permutation, matrixW);
            permutations.add(permTetradMatrixPair);
        }

        System.out.println("CCC");

        return permutations;
    }

    private List<PermutationMatrixPair > zerolessDiagonalPermutation(TetradMatrix ica_W, boolean approximateZeros,
                                                                          List<Node> vars, DataSet dataSet) {

        List<PermutationMatrixPair > permutations = new Vector<PermutationMatrixPair >();

        if (approximateZeros) {
//            setInsignificantEntriesToZero(ica_W);
            ica_W = pruneEdgesByResampling(ica_W);
            ica_W = removeZeroRowsAndCols(ica_W, vars);
        }

//        List<PermutationMatrixPair > zldPerms = new ArrayList<PermutationMatrixPair >();

        List<Integer> perm = new ArrayList<Integer>();

        for (int i = 0; i < vars.size(); i++) perm.add(i);

        TetradMatrix matrixW = ica_W.transpose();

        PermutationMatrixPair pair = new PermutationMatrixPair(perm, matrixW);

        permutations.add(pair);


//        //find assignments
//        TetradMatrix mat = ica_W.transpose();
//        //returns all zeroless-diagonal column-permutations
//
//        System.out.println("AAA");
//
//        List<List<Integer>> nRookAssignments = nRookColumnAssignments(mat, makeAllRows(mat.rows()));
//
//        System.out.println("BBB");
//
//        //for each assignment, add the corresponding permutation to 'permutations'
//        for (List<Integer> permutation : nRookAssignments) {
//            TetradMatrix matrixW = permuteRows(ica_W, permutation).transpose();
//            PermutationMatrixPair  permTetradMatrixPair = new PermutationMatrixPair (permutation, matrixW, vars);
//            permutations.add(permTetradMatrixPair);
//        }
//
//        System.out.println("CCC");

        return permutations;
    }

    private TetradMatrix removeZeroRowsAndCols(TetradMatrix w, List<Node> variables) {

        TetradMatrix _W = w.copy();
        List<Node> _variables = new ArrayList<Node>(variables);
        List<Integer> remove = new ArrayList<Integer>();

        ROW:
        for (int i = 0; i < _W.rows(); i++) {
            TetradVector row = _W.getRow(i);

            for (int j = 0; j < row.size(); j++) {
                if (row.get(j) != 0) continue ROW;
            }

            remove.add(i);

        }

        COLUMN:
        for (int i = 0; i < _W.rows(); i++) {
            TetradVector col = _W.getColumn(i);

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

    private void setInsignificantEntriesToZero(TetradMatrix mat) {
        int n = mat.rows();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (Math.abs(mat.get(i, j)) < threshold) {
                    mat.set(i, j, 0);
                }
            }
        }

        System.out.println("Thresholded W = " + mat);
    }

    private static List<Integer> makeAllRows(int n) {
        List<Integer> l = new ArrayList<Integer>();
        for (int i = 0; i < n; i++) {
            l.add(i);
        }
        return l;
    }

    private static List<List<Integer>> nRookColumnAssignments(TetradMatrix mat, List<Integer> availableRows) {
        List<List<Integer>> concats = new ArrayList<List<Integer>>();

        System.out.println("mat = " + mat);

        int n = availableRows.size();

        for (int i = 0; i < n; i++) {
            int currentRowIndex = availableRows.get(i);

            if (mat.get(currentRowIndex, 0) != 0) {
                if (mat.columns() > 1) {
                    Vector<Integer> newAvailableRows = (new Vector<Integer>(availableRows));
                    newAvailableRows.removeElement(currentRowIndex);
                    TetradMatrix subMat = mat.getPart(0, mat.rows() - 1, 1, mat.columns() - 2);
                    List<List<Integer>> allLater = nRookColumnAssignments(subMat, newAvailableRows);

                    for (List<Integer> laterPerm : allLater) {
                        laterPerm.add(0, currentRowIndex);
                        concats.add(laterPerm);
                    }
                } else {
                    List<Integer> l = new ArrayList<Integer>();
                    l.add(currentRowIndex);
                    concats.add(l);
                }
            }
        }

        return concats;
    }

    private static TetradMatrix permuteRows(TetradMatrix mat, List<Integer> permutation) {
        TetradMatrix permutedMat = mat.like();

        for (int j = 0; j < mat.rows(); j++) {
            TetradVector row = mat.getRow(j);
            permutedMat.assignRow(permutation.get(j), row);
        }

        return permutedMat;
    }

    //	B^ = I - W~'

    private static DataSet computeBhatTetradMatrix(TetradMatrix normalizedZldW, List<Node> nodes) {//, List<Integer> perm) {
        int size = normalizedZldW.rows();
        TetradMatrix mat = TetradMatrix.identity(size).minus(normalizedZldW);
        return ColtDataSet.makeContinuousData(nodes, new TetradMatrix(mat.toArray()));
    }

    private static boolean allEigenvaluesAreSmallerThanOneInModulus(TetradMatrix mat) {

        EigenDecomposition dec = new EigenDecomposition(mat.getRealMatrix());
        double[] realEigenvalues = dec.getRealEigenvalues();
        double[] imagEigenvalues = dec.getImagEigenvalues();

        double sum = 0.0;

//        boolean allEigenvaluesSmallerThanOneInModulus = true;
        for (int i = 0; i < realEigenvalues.length; i++) {
            double realEigenvalue = realEigenvalues[i];
            double imagEigenvalue = imagEigenvalues[i];
            double modulus = Math.sqrt(Math.pow(realEigenvalue, 2) + Math.pow(imagEigenvalue, 2));
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
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

    }

//    public double getPruneFactor() {
//        return pruneFactor;
//    }

    /**
     * This small class is used to store graph permutations. It contains basic methods for adding and accessing graphs.
     * <p>
     * It is likely that this class will move elesewhere once the role of algorithms that produce multiple graphs is
     * better defined.
     */

    public static class StoredGraphs implements GraphGroup {

        /**
         * Graph permutations are stored here.
         */
        private List<Graph> graphs = new ArrayList<Graph>();

        /**
         * Store data for each graph in case the data is needed later
         */
        private List<DataSet> dataSet = new ArrayList<DataSet>();

        /**
         * Boolean valued vector that contains the stability information for its corresponding graph. stable = true
         * means the graph has all eigenvalues with modulus < 1.
         */
        private List<Boolean> stable = new ArrayList<Boolean>();

        /**
         * Gets the number of graphs stored by the class.
         *
         * @return Returns the number of graphs stored in the class
         */
        public int getNumGraphs() {
            return graphs.size();
        }

        /**
         * @param g The index of the graph to be returned
         * @return Returns a Graph
         */
        public Graph getGraph(int g) {
            return graphs.get(g);
        }

        /**
         * @param d The index of the graph for which the DataSet is being returned
         * @return Returns a DataSet
         */
        public DataSet getData(int d) {
            return dataSet.get(d);
        }

        /**
         * @param s The index of the graph at which to return the boolean stability information for the permutation
         * @return Returns the shriknig variable value for a specific graph.
         */
        public boolean isStable(int s) {
            return stable.get(s);
        }

        /**
         * Gives a method for adding classes to the class.
         *
         * @param g The graph to add
         */
        public void addGraph(Graph g) {
            graphs.add(g);
        }

        /**
         * A method for adding graph data to the class.
         *
         * @param d The graph to add
         */
        public void addData(DataSet d) {
            dataSet.add(d);
        }

        /**
         * Allows for the adding of shinking information to its corresponding graph. This should be used at the same time as
         * addGraph() if it is to be used. Otherwise, add a method to add data at a specific index.
         *
         * @param s The stability value to set for a graph.
         */
        public void addStable(Boolean s) {
            stable.add(s);
        }
    }

}


