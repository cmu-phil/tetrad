package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.cluster.KMeans;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by user on 2/27/18.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class DemixerMMLKun {

    private final double minWeight;

    /**
     * <p>Constructor for DemixerMMLKun.</p>
     */
    public DemixerMMLKun() {
        minWeight = 1e-3;
    }

    /**
     * <p>main.</p>
     *
     * @param args a {@link java.lang.String} object
     */
    public static void main(String... args) {
        DataSet dataSet;

        try {
            dataSet = SimpleDataLoader.loadContinuousData(new File("/Users/josephramsey/Downloads/15.txt"),
                    "//", '\"', "*", true, Delimiter.TAB, false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        DemixerMMLKun pedro = new DemixerMMLKun();
        long startTime = System.currentTimeMillis();
        MixtureModel model = pedro.demix(dataSet, 25);
        long elapsed = System.currentTimeMillis() - startTime;

        double[] weights = model.getWeights();
        for (double weight : weights) {
            System.out.print(weight + "\t");
        }

        try {
            FileWriter writer = new FileWriter("/Users/josephramsey/Downloads/DemixTesting/15.txt");
            BufferedWriter bufferedWriter = new BufferedWriter(writer);

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                bufferedWriter.write(model.getDistribution(i) + "\n");
            }
            bufferedWriter.flush();
            bufferedWriter.close();

            DataSet[] dataSets = model.getDemixedData();

            for (int i = 0; i < dataSets.length; i++) {
                writer = new FileWriter("/Users/josephramsey/Downloads/DemixTesting/sub_1500_4var_3comp_demixed_" + (i + 1) + ".txt");
                bufferedWriter = new BufferedWriter(writer);
                bufferedWriter.write(dataSets[i].toString());
                bufferedWriter.flush();
                bufferedWriter.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        System.out.println("Elapsed: " + elapsed / 1000);
    }

    private MixtureModel demix(DataSet data, int k) {
        double[][] dataArray = data.getDoubleData().toArray();
        int numVars = data.getNumColumns();
        int numCases = data.getNumRows();
        double lambda2 = Math.sqrt((Math.log(numCases) - Math.log(Math.log(numCases))) / 2.0);
        double lambda = lambda2 * ((Math.pow(numVars, 2)) * 0.5 + 1.5 * numVars + 1); // part of the MML score
        double epsilon = 1e-6; // part of the MML score
        double threshold = 1e-8; // threshold for MML score convergence

        System.out.println("Lambda: " + lambda);

        // Initialize clusterings with kmeans
        KMeans kMeans = KMeans.randomClusters(k);
        kMeans.cluster(data.getDoubleData());

        // Use initial clusterings to get initial means, variances, and gamma arrays
        double[][] meansArray = new double[k][numVars];
        double[] weightsArray = new double[k];
        Matrix[] variancesArray = new Matrix[k];
        Matrix[] variances = new Matrix[k];
        double[][] gammaArray = new double[k][numCases];

        List<List<Integer>> clusters = kMeans.getClusters();
        List<Integer> cluster;
        int clusterSize;
        double[] means;

        double[][] clusterMatrixArray;

        for (int i = 0; i < clusters.size(); i++) {
            cluster = clusters.get(i);
            clusterSize = cluster.size();
            means = new double[numVars];

            for (int j = 0; j < numVars; j++) {
                means[j] = 0;
            }

            clusterMatrixArray = new double[clusterSize][numVars];

            for (int j = 0; j < clusterSize; j++) {
                //      System.out.print(Integer.toString(cluster.get(j)) + "\t");
                MatrixUtils.sum(means, dataArray[cluster.get(j)]);
                clusterMatrixArray[j] = dataArray[cluster.get(j)];
            }

            // Initial mean is mean of cluster
            means = MatrixUtils.scalarProduct(1.0 / clusterSize, means);
            meansArray[i] = means;

            // Initial weight is percentage of rows taken up by cluster
            weightsArray[i] = ((double) clusterSize) / ((double) numCases);

            // Initial covariance matrix is cov matrix of cluster, unless cluster cov matrix has 0 determinant
            DoubleDataBox box = new DoubleDataBox(clusterMatrixArray);
            List<Node> variables = data.getVariables();
            BoxDataSet clusterData = new BoxDataSet(box, variables);
            Matrix clusterCovMatrix = clusterData.getCovarianceMatrix();
            if (MatrixUtils.determinant(clusterCovMatrix.toArray()) == 0) {
                variances[i] = MatrixUtils.cholesky(data.getCovarianceMatrix());
                variancesArray[i] = data.getCovarianceMatrix();
            } else {
                variances[i] = MatrixUtils.cholesky(clusterCovMatrix);
                variancesArray[i] = clusterCovMatrix;
            }

        }

        double gamma;
        double divisor;

        for (int z = 0; z < k; z++) {
            for (int j = 0; j < numCases; j++) {
                gamma = weightsArray[z] * normalPDF(j, z, variances, meansArray, dataArray, numVars);
                divisor = gamma;

                for (int w = 0; w < k; w++) {
                    if (w != z) {
                        divisor += (weightsArray[w] * normalPDF(j, w, variances, meansArray, dataArray, numVars));
                    }
                }

                //Initial gamma is weighted probability for the case in cluster k, divided by the sum of weighted probabilities in all clusters
                gamma = gamma / divisor;
                gammaArray[z][j] = gamma;
            }
        }

        // Verbose debugging output
        System.out.println("Clusters: " + k);
        System.out.println("Weights: " + Arrays.toString(weightsArray));

        // oldLogL and newLogL determine convergence
        double oldLogL = Double.POSITIVE_INFINITY;
        double newLogL;

        DeterminingStats stats;

        while (true) {

            // maximization step
            stats = innerStep(data, dataArray, weightsArray, meansArray, variancesArray, variances, gammaArray, numCases, numVars, lambda);
            meansArray = stats.getMeans();
            weightsArray = stats.getWeights();
            variancesArray = stats.getVariances();
            variances = stats.getVarMatrixArray();

            k = weightsArray.length;

            // fail if there are no clusters
            if (k == 0) {
                break;
            }

            // verbose debugging output
            System.out.println("Clusters: " + k);
            System.out.println("Weights: " + Arrays.toString(weightsArray));

            // expectation step; gamma computed as above, I should probably make a separate method for it
            for (int i = 0; i < k; i++) {

                for (int j = 0; j < numCases; j++) {

                    double pdf = normalPDF(j, i, variances, meansArray, dataArray, numVars);

                    gamma = weightsArray[i] * pdf;

                    divisor = gamma;

                    for (int w = 0; w < k; w++) {
                        if (w != i) {
                            divisor += (weightsArray[w] * normalPDF(j, w, variances, meansArray, dataArray, numVars));
                        }
                    }
                    gamma = gamma / divisor;


                    gammaArray[i][j] = gamma;
                }
            }

            // check for convergence
            double mml = 0;
            double gammaMean;
            for (int i = 0; i < weightsArray.length; i++) {
                gammaMean = 0;
                for (int j = 0; j < numCases; j++) {
                    gammaMean += gammaArray[i][j];
                }
                gammaMean /= numCases;
                mml += Math.log(gammaMean);
            }

            mml /= weightsArray.length;

            double weightSum = 0;

            for (double v : weightsArray) {
                weightSum += Math.log(v / epsilon + 1);
            }

            weightSum *= lambda / numCases;

            newLogL = mml + weightSum;

            // if oldLogL and newLogL converge, end; otherwise, set oldLogL to newLogL
            if (Math.abs(oldLogL / (newLogL) - 1) < threshold) {
                break;
            } else {
                oldLogL = newLogL;
            }

        }

        return new MixtureModel(data, dataArray, meansArray, weightsArray, variancesArray, gammaArray);
    }

    /**
     * Performs the maximization step
     */
    private DeterminingStats innerStep(DataSet data, double[][] dataArray, double[] weightsArray, double[][] meansArray, Matrix[] variancesArray, Matrix[] variances, double[][] gammaArray, int numCases, int numVars, double lambda) {

        double weight;
        double pSum; // sum of all gammas for a case
        double meanNumerator;
        double mean;
        Matrix tempVar;

        ArrayList<double[]> meansList = new ArrayList<>();
        ArrayList<Matrix> varsLilst = new ArrayList<>();
        ArrayList<Matrix> varMatList = new ArrayList<>();

        for (int i = 0; i < weightsArray.length; i++) {

            // maximize weights
            pSum = 0;
            for (int j = 0; j < numCases; j++) {
                pSum += gammaArray[i][j];
            }

            weight = (pSum - lambda) / (numCases - (lambda * weightsArray.length));
            weightsArray[i] = weight;

            // maximize covariance matrices
            tempVar = new Matrix(numVars, numVars);

            for (int v = 0; v < numVars; v++) {

                // maximize means
                meanNumerator = 0;
                for (int j = 0; j < numCases; j++) {

                    meanNumerator += gammaArray[i][j] * dataArray[j][v];
                }
                mean = meanNumerator / pSum;
                meansArray[i][v] = mean;

                for (int v2 = v; v2 < numVars; v2++) {
                    double var = Demixer.getVar(i, v, v2, numCases, gammaArray, dataArray, meansArray);
                    tempVar.set(v, v2, var);
                    tempVar.set(v2, v, var);
                }
            }

            Matrix varMatrix = new Matrix(tempVar);
            if (varMatrix.det() != 0) {
                variancesArray[i] = MatrixUtils.cholesky(tempVar);
                variances[i] = MatrixUtils.cholesky(varMatrix);
            } else {
                variances[i] = MatrixUtils.cholesky(data.getCovarianceMatrix());
                variancesArray[i] = data.getCovarianceMatrix();
            }

        }

        System.out.println();

        // check weights, and remove any clusters with weights below threshold
        ArrayList<Double> weightsList = new ArrayList<>();

        for (int i = 0; i < weightsArray.length; i++) {

            if (weightsArray[i] >= minWeight) {
                weightsList.add(weightsArray[i]);
                meansList.add(meansArray[i]);
                varsLilst.add(variancesArray[i]);
                varMatList.add(variances[i]);
            }
        }

        double[] tempWeightsArray = new double[weightsList.size()];
        double[][] tempMeansArray = new double[weightsList.size()][numVars];
        Matrix[] tempVarsArray = new Matrix[weightsList.size()];
        Matrix[] tempVariances = new Matrix[weightsList.size()];
        for (int i = 0; i < weightsList.size(); i++) {
            tempWeightsArray[i] = weightsList.get(i);
            tempMeansArray[i] = meansList.get(i);
            tempVarsArray[i] = varsLilst.get(i);
            tempVariances[i] = varMatList.get(i);
        }

        weightsArray = tempWeightsArray;
        meansArray = tempMeansArray;
        variancesArray = tempVarsArray;
        variances = tempVariances;

        return new DeterminingStats(meansArray, weightsArray, variancesArray, variances);
    }

    /**
     * Returns the value of the Normal PDF for a given case if it belongs to a given cluster
     */
    private double normalPDF(int caseIndex, int weightIndex, Matrix[] variances, double[][] meansArray, double[][] dataArray, int numVars) {
        Matrix cov = variances[weightIndex];
        cov = cov.transpose();

        Matrix covIn = cov.inverse();
        double[] mu = meansArray[weightIndex];
        double[] thisCase = dataArray[caseIndex];

        double[][] diffs = new double[1][numVars];

        for (int i = 0; i < numVars; i++) {
            diffs[0][i] = thisCase[i] - mu[i];
        }

        Matrix diffsMatrix = new Matrix(diffs);
        Matrix mah = diffsMatrix.times(covIn);

        double val;
        double mahScal = 0;
        for (int i = 0; i < mah.getNumRows(); i++) {
            for (int j = 0; j < mah.getNumColumns(); j++) {
                val = mah.get(i, j);
                val = val * val;
                mahScal += val;
                mah.set(i, j, val);
            }
        }

        double distanceScal = Math.pow(2 * Math.PI, -(numVars) / 2.0);
        distanceScal = distanceScal / cov.det();
        distanceScal = distanceScal * Math.exp(-.5 * mahScal);

        return distanceScal;
    }

    /**
     * Private wrapper class for statistics to be maximized
     */
    private static class DeterminingStats {
        private final double[][] meansArray;
        private final double[] weightsArray;
        private final Matrix[] variancesArray;
        private final Matrix[] varMatrixArray;

        public DeterminingStats(double[][] meansArray, double[] weightsArray, Matrix[] variancesArray, Matrix[] varMatrixArray) {
            this.meansArray = meansArray;
            this.weightsArray = weightsArray;
            this.variancesArray = variancesArray;
            this.varMatrixArray = varMatrixArray;
        }

        public double[] getWeights() {
            return weightsArray;
        }

        public double[][] getMeans() {
            return meansArray;
        }

        public Matrix[] getVariances() {
            return variancesArray;
        }

        public Matrix[] getVarMatrixArray() {
            return varMatrixArray;
        }
    }
}
