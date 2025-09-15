///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Matrix;

/**
 * Represents a Gaussian mixture model -- a dataset with data sampled from two or more multivariate Gaussian
 * distributions.
 *
 * @author Madelyn Glymour
 * @version $Id: $Id
 */
public class MixtureModel {
    // The mixed data set
    private final DataSet data;
    // The individual data sets
    private final int[] cases;
    // The number of cases in each individual data set
    private final int[] caseCounts;
    // The data set in array form
    private final double[][] dataArray;
    // The means matrix
    private final double[][] meansArray;
    // The weights array
    private final double[] weightsArray;
    // The gamma matrix
    private final double[][] gammaArray;
    // The variance matrix
    private final Matrix[] variancesArray;
    // The number of models in the mixture
    private final int numModels;

    /**
     * Constructs a mixture model from a mixed data set, a means matrix, a weights array, a variance matrix, and a gamma
     * matrix.
     *
     * @param data           the mixed data set
     * @param dataArray      the mixed data set in array form
     * @param meansArray     the means matrix
     * @param weightsArray   the weights array
     * @param variancesArray the variance matrix
     * @param gammaArray     the gamma matrix
     */
    public MixtureModel(DataSet data, double[][] dataArray, double[][] meansArray, double[] weightsArray, Matrix[] variancesArray, double[][] gammaArray) {
        this.data = data;
        this.dataArray = dataArray;
        this.meansArray = meansArray;
        this.weightsArray = weightsArray;
        this.variancesArray = variancesArray;
        this.numModels = weightsArray.length;
        this.gammaArray = gammaArray;
        this.cases = new int[data.getNumRows()];

        // set the individual model for each case
        for (int i = 0; i < cases.length; i++) {
            cases[i] = getDistribution(i);
        }

        this.caseCounts = new int[numModels];

        // count the number of cases in each individual data set
        for (int i = 0; i < numModels; i++) {
            caseCounts[i] = 0;
        }

        for (int aCase : cases) {
            for (int j = 0; j < numModels; j++) {
                if (aCase == j) {
                    caseCounts[j]++;
                    break;
                }
            }
        }
    }

    /**
     * <p>Getter for the field <code>data</code>.</p>
     *
     * @return the mixed data set in array form
     */
    public double[][] getData() {
        return dataArray;
    }

    /**
     * <p>getMeans.</p>
     *
     * @return the means matrix
     */
    public double[][] getMeans() {
        return meansArray;
    }

    /**
     * <p>getWeights.</p>
     *
     * @return the weights array
     */
    public double[] getWeights() {
        return weightsArray;
    }

    /**
     * <p>getVariances.</p>
     *
     * @return the variance matrix
     */
    public Matrix[] getVariances() {
        return variancesArray;
    }

    /**
     * <p>Getter for the field <code>cases</code>.</p>
     *
     * @return an array assigning each case an integer corresponding to a model
     */
    public int[] getCases() {
        return cases;
    }

    /**
     * Classifies a given case into a model, based on which model has the highest gamma value for that case.
     *
     * @param caseNum a int
     * @return a int
     */
    public int getDistribution(int caseNum) {

        // hard classification
        int dist = 0;
        double highest = 0;

        for (int i = 0; i < numModels; i++) {
            if (gammaArray[i][caseNum] > highest) {
                highest = gammaArray[i][caseNum];
                dist = i;
            }

        }

        return dist;

        // soft classification, deprecated because it doesn't classify as well

        /*int gammaSum = 0;

        for (int i = 0; i < k; i++) {
            gammaSum += gammaArray[i][caseNum];
        }

        Random rand = new Random();
        double test = gammaSum * rand.nextDouble();

        if(test < gammaArray[0][caseNum]){
            return 0;
        }

        double sum = gammaArray[0][caseNum];

        for (int i = 1; i < k; i++){
            sum = sum+gammaArray[i][caseNum];
            if(test < sum){
                return i;
            }
        }

        return k - 1; */
    }

    /*
     * Sort the mixed data set into its component data sets.
     *
     * @return a list of data sets
     */

    /**
     * <p>getDemixedData.</p>
     *
     * @return an array of {@link edu.cmu.tetrad.data.DataSet} objects
     */
    public DataSet[] getDemixedData() {
        DoubleDataBox[] dataBoxes = new DoubleDataBox[numModels];
        int[] caseIndices = new int[numModels];

        for (int i = 0; i < numModels; i++) {
            dataBoxes[i] = new DoubleDataBox(caseCounts[i], data.getNumColumns());
            caseIndices[i] = 0;
        }

        int index;
        DoubleDataBox box;
        int count;
        for (int i = 0; i < cases.length; i++) {

            // get the correct data set and corresponding case count for this case
            index = cases[i];
            box = dataBoxes[index];
            count = caseIndices[index];

            // set the [count]th row of the given data set to the ith row of the mixed data set
            for (int j = 0; j < data.getNumColumns(); j++) {
                box.set(count, j, data.getDouble(i, j));
            }

            dataBoxes[index] = box; //make sure that the changes get carried to the next iteration of the loop
            caseIndices[index] = count + 1; //increment case count of this data set
        }

        // create list of data sets
        DataSet[] dataSets = new DataSet[numModels];
        for (int i = 0; i < numModels; i++) {
            dataSets[i] = new BoxDataSet(dataBoxes[i], data.getVariables());
        }

        return dataSets;
    }

    /**
     * Perform an FGES search on each of the demixed data sets.
     *
     * @return the BIC scores of the graphs returned by searches.
     * @throws java.lang.InterruptedException if any.
     */
    public double[] searchDemixedData() throws InterruptedException {
        DataSet[] dataSets = getDemixedData();
        SemBicScore score;
        edu.cmu.tetrad.search.Fges fges;
        DataSet dataSet;
        double bic;
        double[] bicScores = new double[numModels];

        for (int i = 0; i < numModels; i++) {
            dataSet = dataSets[i];
            score = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
            score.setPenaltyDiscount(2.0);
            fges = new edu.cmu.tetrad.search.Fges(score);
            fges.search();
            bic = fges.getModelScore();
            bicScores[i] = bic;
        }

        return bicScores;
    }
}

