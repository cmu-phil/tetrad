package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.CombinationIterator;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.exception.OutOfRangeException;

import java.util.*;

import static java.lang.Math.pow;

public final class IndTestUniformScatter implements IndependenceTest {
    private final DataSet dataSet;
    private final DataSet transformed;
    private final double[][] data;
    private final double alpha;
    private final double avgCountPerCell;
    private final int numCondCategories;
    private boolean verbose = false;

    public IndTestUniformScatter(DataSet dataSet, double alpha, double avgCountPerCell, int numCondCategories) {
        this.alpha = alpha;
        this.dataSet = dataSet;
        this.transformed = getUniformTransform(dataSet);
        this.avgCountPerCell = avgCountPerCell;
        this.data = transformed.getDoubleData().transpose().toArray();
        this.numCondCategories = numCondCategories;
    }

    public static void main(String... args) {
        Graph graph = RandomGraph.randomGraph(10, 0, 10, 100,
                100, 100, false);
        System.out.println("True graph = " + graph);
        int N = 1000;
        double alpha = 0.001;

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(N, false);

        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());
        MsepTest msepTest = new MsepTest(graph);

        IndTestUniformScatter test = new IndTestUniformScatter(dataSet, alpha, 5, 3);

        List<Node> nodes = graph.getNodes();

        int tp = 0;
        int tn = 0;
        int fp = 0;
        int fn = 0;

        for (int x = 0; x < nodes.size(); x++) {
            for (int y = x + 1; y < nodes.size(); y++) {
                for (int z = 0; z < nodes.size(); z++) {
                    if (z == x || z == y) {
                        continue;
                    }

                    Node X = nodes.get(x);
                    Node Y = nodes.get(y);
                    Node Z = nodes.get(z);

                    Set<Node> cond = new HashSet<>();
                    cond.add(Z);

                    boolean msep = msepTest.checkIndependence(X, Y, cond).isIndependent();
                    boolean marginallyIndependent = test.checkIndependence(X, Y, cond).isIndependent();

                    if (!msep && !marginallyIndependent) tp++;
                    if (msep && marginallyIndependent) tn++;
                    if (marginallyIndependent && !msep) fn++;
                    if (msep && !marginallyIndependent) fp++;

                    System.out.println(X + " _||_ " + Y + " | " + Z + " " + (msep ? "D-DESEPARATED" : "d-connected")
                            + " " + (marginallyIndependent ? "INDEPENDENT" : "dependent"));
                }
            }
        }

        System.out.println("TP = " + tp);
        System.out.println("TN = " + tn);
        System.out.println("FP = " + fp);
        System.out.println("FN = " + fn);

        System.out.println("Precision = " + ((double) tp) / (tp + fp));
        System.out.println("Recall = " + ((double) tp) / (tp + fn));

    }

    /**
     * Returns a transformed data set where each variable is transformed to a uniform distribution using a rank
     * transformation. This is similar in spirit to the nonparanormal transformation, but without the inverse Gaussian
     * CDF.
     *
     * @param dataSet The continuous dataset to transform.
     * @return The transformed data, where each variable is distributed as Uniform.
     */
    private static DataSet getUniformTransform(DataSet dataSet) {
        try {
            Matrix data = dataSet.getDoubleData();
            Matrix X = data.like();
            double N = dataSet.getNumRows();

//            UniformRealDistribution uniformDistribution = new UniformRealDistribution(0, 1);

            for (int j = 0; j < data.getNumColumns(); j++) {
                double[] x1 = Arrays.copyOf(data.getColumn(j).toArray(), data.getNumRows());

                if (dataSet.getVariable(j) instanceof DiscreteVariable) {
                    X.assignColumn(j, new Vector(x1));
                    continue;
                }

                double[] xTransformed = DataUtils.ranks(x1);

                for (int i = 0; i < xTransformed.length; i++) {
                    xTransformed[i] -= 1.0;
                    xTransformed[i] /= N;
//                    xTransformed[i] = uniformDistribution.inverseCumulativeProbability(xTransformed[i]);
                }

                X.assignColumn(j, new Vector(xTransformed));
            }

            return new BoxDataSet(new VerticalDoubleDataBox(X.transpose().toArray()), dataSet.getVariables());
        } catch (OutOfRangeException e) {
            e.printStackTrace();
            return dataSet;
        }
    }

    /**
     * Returns true if the two uniformly distributed columns of data in a dataset are marginally independent, false
     * otherwise.
     *
     * @param data The data set containing the two variables, uniformly distributed.
     * @param x    The index of the first variable.
     * @param y    The index of the second variable.
     * @param z    The indeces of the conditioning variables
     * @return True if the two variables are marginally independent, false otherwise.
     */
    private static double getConditionallyIndependentUniformPvalue(double[][] data, int x, int y, int[] z, int m, int numCondCategories) {
        // Count the data points in each cell
        // Calculate the chi-square statistic
        double chiSquare = 0.0;
        int[][][] dataCounts = countConditionalDataOnGrid(data, x, y, z, m, numCondCategories);
        int dof = 0;
        int N = data[0].length;

        double expectedCount = (double) N / (m * m * dataCounts.length);

        for (int[][] dataCount : dataCounts) {

            for (int[] row : dataCount) {
                for (double observedCount : row) {
                    double contribution = (observedCount - expectedCount) * (observedCount - expectedCount) / expectedCount;
                    chiSquare += contribution;
                    dof++;
                }
            }
        }

        dof -= 1;
        if (dof < 1) return Double.NaN;

        // Perform chi-square test using critical value from chi-square distribution
        ChiSquaredDistribution chiSquaredDistribution = new ChiSquaredDistribution(dof);
        return 1.0 - chiSquaredDistribution.cumulativeProbability(chiSquare);
    }

    private static double count(int[][] counts) {
        int count = 0;

        for (int[] doubles : counts) {
            for (int j = 0; j < counts[0].length; j++) {
                count += doubles[j];
            }
        }

        return count;
    }


    /**
     * Returns true if the two uniformly distributed columns of data in a dataset are marginally independent, false
     * otherwise.
     *
     * @param data            The data set containing the two variables, uniformly distributed.
     * @param x               The index of the first variable.
     * @param y               The index of the second variable.
     * @param avgCountPerCell The average number of data points per cell.
     * @param alpha           The significance level.
     * @return True if the two variables are marginally independent, false otherwise.
     */
    private static boolean isMarginallyIndependentUniform(double[][] data, int x, int y, double avgCountPerCell, double alpha) {
        int N = data[0].length;
        double numCells = N / avgCountPerCell;
        int m = (int) pow(numCells, 0.5);

//        System.out.println("m = " + m + " N = " + N + " avgCountPerCell = " + avgCountPerCell);

        // Count the data points in each cell
        // Calculate the chi-square statistic
        double chiSquare = 0.0;
        double[][] dataCounts = countDataOnGrid(data, x, y, m, m);

        for (int k = 0; k < m; k++) {
            for (int l = 0; l < m; l++) {
                // Calculate the expected count per cell
                double expectedCount = (double) N / (m * m);
                double observedCount = dataCounts[k][l];
                double contribution = (observedCount - expectedCount) * (observedCount - expectedCount) / expectedCount;
                chiSquare += contribution;
            }
        }

        // Determine the degrees of freedom
        int degreesOfFreedom = (m * m) - 1;

        // Perform chi-square test using critical value from chi-square distribution
        ChiSquaredDistribution chiSquaredDistribution = new ChiSquaredDistribution(degreesOfFreedom);
        double criticalValue = chiSquaredDistribution.inverseCumulativeProbability(1.0 - alpha);

        return (chiSquare <= criticalValue);
    }

    // Function to count data points on the grid
    private static double[][] countDataOnGrid(double[][] data, int x, int y, int m1, int m2) {
        double[] xData = data[x];
        double[] yData = data[y];

        // Set up the grid
        double[][] dataCounts = new double[m1][m2];

        // Count the data points in each cell
        for (int i = 0; i < xData.length; i++) {
            int row = (int) (xData[i] * m1);
            int column = (int) (yData[i] * m2);

            // Increment the count for the corresponding cell
            dataCounts[row][column]++;
        }

        return dataCounts;
    }

    // Function to count data points on the grid
    private static int[][][] countConditionalDataOnGrid(double[][] data, int x, int y, int[] z, int m, int numCondCategories) {
        int[] dimensions = new int[z.length];

        Arrays.fill(dimensions, numCondCategories);

        int[][][] dataCounts = new int[(int) pow(numCondCategories, z.length)][m][m];
        int slice = -1;
        CombinationIterator combinationIterator = new CombinationIterator(dimensions);

        while (combinationIterator.hasNext()) {
            int[] combination = combinationIterator.next();
            ++slice;

            dataCounts[slice] = new int[m][m];

            boolean hasInSlice = false;

            for (int i = 0; i < data[x].length; i++) {
                boolean inSlice = true;

                if (m == 0) {
                    inSlice = false;
                } else {
                    for (int j = 0; j < combination.length; j++) {
                        if ((int) (data[z[j]][i] * numCondCategories) != combination[j]) {
                            inSlice = false;
                            break;
                        }
                    }
                }

                if (inSlice) {
                    int row = (int) (data[x][i] * m);
                    int column = (int) (data[y][i] * m);
                    dataCounts[slice][row][column]++;
                    hasInSlice = true;
                }
            }

            if (!hasInSlice) dataCounts[slice] = new int[0][0];
        }

        return dataCounts;
    }

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        List<Node> nodes = transformed.getVariables();
        List<Node> zz = new ArrayList<>(z);

        int xIndex = nodes.indexOf(x);
        int yIndex = nodes.indexOf(y);
        int[] _z = new int[z.size()];

        for (int i = 0; i < z.size(); i++) {
            _z[i] = nodes.indexOf(zz.get(i));
        }

        int N = data[0].length;
        double numCells = N / avgCountPerCell;
        double numCellsPerTable = numCells / pow(numCondCategories, z.size());
        int m = (int) pow(numCellsPerTable, 0.5);

        double p = getConditionallyIndependentUniformPvalue(data, xIndex, yIndex, _z, m, numCondCategories);

        if (Double.isNaN(p)) {
            throw new RuntimeException("Undefined p-value encountered when testing " +
                    LogUtilsSearch.independenceFact(x, y, z));
        }

        return new IndependenceResult(new IndependenceFact(x, y, z), p > alpha, p, p);
    }

    @Override
    public List<Node> getVariables() {
        return this.dataSet.getVariables();
    }

    @Override
    public DataModel getData() {
        return this.dataSet;
    }

    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}
