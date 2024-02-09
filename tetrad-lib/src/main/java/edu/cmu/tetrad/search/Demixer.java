package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

/**
 * Uses expectation-maximization to sort a a data set with data sampled from two or more multivariate Gaussian
 * distributions into its component data sets.
 *
 * @author Madelyn Glymour
 * @version $Id: $Id
 */
public class Demixer {
    // number of variables in the data set
    private final int numVars;
    // number of cases in the data set
    private final int numCases;
    // number of clusters
    private final int numClusters;
    // the data set
    private final DataSet data;
    // the data set as a double array
    private final double[][] dataArray;
    // the means of each variable for each model
    private final Matrix[] variances;
    // the means of each variable for each model
    private final double[][] meansArray;
    // the variances of each variable for each model
    private final Matrix[] variancesArray;
    // the weights of each model
    private final double[] weightsArray;
    // the gamma values for each case in each model
    private final double[][] gammaArray;
    // whether the algorithm has been run
    private boolean demixed = false;

    /**
     * Constructor. Initializes the means, weights, and covariance matrices for each model.
     *
     * @param data the data set
     * @param k the number of models
     */
    public Demixer(DataSet data, int k) {
        this.numClusters = k;
        this.data = data;
        dataArray = data.getDoubleData().toArray();
        numVars = data.getNumColumns();
        numCases = data.getNumRows();
        meansArray = new double[k][numVars];
        weightsArray = new double[k];
        variancesArray = new Matrix[k];
        variances = new Matrix[k];
        gammaArray = new double[k][numCases];

        Random rand = new Random();

        //  initialize the means array to the mean of each variable plus noise
        for (int i = 0; i < numVars; i++) {
            for (int j = 0; j < k; j++) {
                meansArray[j][i] = calcMean(data.getDoubleData().getColumn(i)) + (rand.nextGaussian());
            }
        }

        // initialize the weights array uniformly
        for (int i = 0; i < k; i++) {
            weightsArray[i] = Math.abs((1.0 / k));
        }

        // initialize the covariance matrix array to the actual covariance matrix
        for (int i = 0; i < k; i++) {
            variances[i] = data.getCovarianceMatrix();
        }
    }

    static double getVar(int i, int v, int v2, int numCases, double[][] gammaArray, double[][] dataArray, double[][] meansArray) {
        double varNumerator;
        double varDivisor;
        double var;
        varNumerator = 0;
        varDivisor = 0;

        for (int j = 0; j < numCases; j++) {
            varNumerator += gammaArray[i][j] * (dataArray[j][v] - meansArray[i][v]) * (dataArray[j][v2] - meansArray[i][v2]);
            varDivisor += gammaArray[i][j];
        }

        var = varNumerator / varDivisor;
        return var;
    }

    /**
     * Runs the E-M algorithm iteratively until the weights array converges. Returns a MixtureModel object containing
     * the final values of the means, covariance matrices, weights, and gammas arrays.
     *
     * @return a {@link edu.cmu.tetrad.search.MixtureModel} object
     */
    public MixtureModel demix() {
        double[] tempWeights = new double[numClusters];

        System.arraycopy(weightsArray, 0, tempWeights, 0, numClusters);

        boolean weightsUnequal = true;
        ArrayList<Double> diffsList;
        int iterCounter = 0;

        System.out.println("Weights: " + Arrays.toString(weightsArray));

        // convergence check
        while (weightsUnequal) {
            expectation();
            maximization();

            System.out.println("Weights: " + Arrays.toString(weightsArray));

            diffsList = new ArrayList<>(); // list of differences between new weights and old weights
            for (int i = 0; i < numClusters; i++) {
                diffsList.add(Math.abs(weightsArray[i] - tempWeights[i]));
            }

            Collections.sort(diffsList); // sort the list

            // if the largest difference is below the threshold, or we've passed 100 iterations, converge
            if (diffsList.get(numClusters - 1) < 0.0001 || iterCounter > 100) {
                weightsUnequal = false;
            }

            // new weights are now the old weights
            System.arraycopy(weightsArray, 0, tempWeights, 0, numClusters);

            iterCounter++;
        }

        MixtureModel model = new MixtureModel(data, dataArray, meansArray, weightsArray, variancesArray, gammaArray);
        demixed = true;

        return model;

    }

    /**
     * <p>isDemixed.</p>
     *
     * @return a boolean
     */
    public boolean isDemixed() {
        return demixed;
    }

    /*
     * Computes the probability that each case belongs to each model (the gamma), given the current values of the mean,
     * weight, and covariance arrays
     */
    private void expectation() {

        double gamma;
        double divisor;

        for (int i = 0; i < numClusters; i++) {
            for (int j = 0; j < numCases; j++) {
                gamma = weightsArray[i] * normalPDF(j, i);
                divisor = gamma;

                for (int w = 0; w < numClusters; w++) {
                    if (w != i) {
                        divisor += (weightsArray[w] * normalPDF(j, w));
                    }
                }
                gamma = gamma / divisor;
                gammaArray[i][j] = gamma;
            }
        }
    }

    /*
     * Estimates the means, covariances, and weight of each model, given the current values of the gamma array
     */
    private void maximization() {

        // the weight of each model is the sum of the gamma for each case in that model, divided by the number of cases
        double weight;

        for (int i = 0; i < numClusters; i++) {
            weight = 0;
            for (int j = 0; j < numCases; j++) {
                weight += gammaArray[i][j];
            }
            weight = weight / numCases;
            weightsArray[i] = weight;
        }

        // the mean for each variable in each model is determined by the weighted mean of that variable in the model
        // (where each case i in the variable in model k is weighted by the gamma(i, k)
        double meanNumerator;
        double meanDivisor;
        double mean;

        for (int i = 0; i < numClusters; i++) {
            for (int v = 0; v < numVars; v++) {
                meanNumerator = 0;
                meanDivisor = 0;
                for (int j = 0; j < numCases; j++) {

                    meanNumerator += gammaArray[i][j] * dataArray[j][v];
                    meanDivisor += gammaArray[i][j];
                }
                mean = meanNumerator / meanDivisor;
                meansArray[i][v] = mean;
            }
        }

        // the covariance matrix for each model is determined by the covariance matrix of the data, weighted by the
        // gamma values for that model
        double var;

        for (int i = 0; i < numClusters; i++) {
            for (int v = 0; v < numVars; v++) {
                for (int v2 = v; v2 < numVars; v2++) {
                    var = getVar(i, v, v2, numCases, gammaArray, dataArray, meansArray);
                    // if(Math.abs(var) >= 0.5) {
                    variancesArray[i].set(v, v2, var);
                    variancesArray[i].set(v2, v, var);

                    // Reset the variances if things start to go awry with the algorithm; turns out not to be necessary
                    //  } else{
                    //      Random rand = new Random();
                    //      double temp = 0.5 + rand.nextDouble();
                    //      variancesArray[i][v][v2] = temp;
                    //      variancesArray[i][v2][v] = temp;
                    //  }
                }
            }
            variances[i] = new Matrix(variancesArray[i]);
        }

    }

    /*
     * For an input case and model, returns the value of the model's normal PDF for that case, using the current
     * estimations of the means and covariance matrix
     */
    private double normalPDF(int caseIndex, int weightIndex) {
        Matrix cov = variances[weightIndex];

        Matrix covIn = cov.inverse();
        double[] mu = meansArray[weightIndex];
        double[] thisCase = dataArray[caseIndex];

        double[][] diffs = new double[1][numVars];

        for (int i = 0; i < numVars; i++) {
            diffs[0][i] = thisCase[i] - mu[i];
        }

        Matrix diffsMatrix = new Matrix(diffs);
        Matrix diffsTranspose = diffsMatrix.transpose();

        Matrix distance = covIn.times(diffsTranspose); // inverse of the covariance matrix * (x - mu)

        distance = diffsMatrix.times(distance); // squared

        double distanceScal = distance.get(0, 0); // distance is a scalar, but in matrix representation
        distanceScal = distanceScal * (-.5);
        distanceScal = Math.exp(distanceScal);
        distanceScal = distanceScal / Math.sqrt(2 * Math.PI * cov.det()); // exp(-.5 * distance) / sqrt(2 * pi * cov)

        return distanceScal;
    }

    /*
     * Returns the mean of a variable, input as a Vector
     */
    private double calcMean(Vector dataPoints) {
        double sum = 0;

        for (int i = 0; i < dataPoints.size(); i++) {
            sum += dataPoints.get(i);
        }

        return sum / dataPoints.size();
    }
}
