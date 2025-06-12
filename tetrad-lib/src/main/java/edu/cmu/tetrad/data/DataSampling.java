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
/// ////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetrad.data;

import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.random.RandomGenerator;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * A utility for resampling dataset.
 * <p>
 * Feb 20, 2024 6:57:28 PM
 *
 * @author Kevin V. Bui (kvb2univpitt@gmail.com)
 */
public final class DataSampling {

    private DataSampling() {
    }

    /**
     * Create a list of dataset resampled from the given dataset.
     *
     * @param dataSet         dataset to resample
     * @param randomGenerator the random number generate to use.
     * @param parameters      bootstrap-related parameters
     * @return a list of resampled dataset
     */
    public static List<DataSet> createDataSamples(RandomGenerator randomGenerator, DataSet dataSet, Parameters parameters) {
        List<DataSet> dataSets = new LinkedList<>();
        // no resampling
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            dataSets.add(dataSet);
        } else {
            dataSets.addAll(createDataSamples(dataSet, randomGenerator, parameters));
        }

        return dataSets;
    }

    /**
     * Create a list of dataset resampled from the given dataset.
     *
     * @param dataSet         dataset to resample
     * @param randomGenerator random number generator (optional)
     * @param parameters      bootstrap-related parameters
     * @return a list of resampled dataset
     */
    public static List<DataSet> createDataSamples(DataSet dataSet, RandomGenerator randomGenerator, Parameters parameters) {
        List<DataSet> datasets = new LinkedList<>();

        int[] selectedColumns = IntStream.range(0, dataSet.getNumColumns()).toArray();  // select all data columns
        for (int i = 0; i < parameters.getInt(Params.NUMBER_RESAMPLING) && !Thread.currentThread().isInterrupted(); i++) {
            // select data rows to create new dataset
            double r = parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE);
            DataSet sample = createDataSample(dataSet, randomGenerator, selectedColumns, parameters, r);
            datasets.add(sample);
        }

        // add original dataset if requested
        if (parameters.getBoolean(Params.ADD_ORIGINAL_DATASET)) {
            datasets.add(dataSet);
        }

        return datasets;
    }

    /**
     * Creates a resampled dataset from the given dataset based on the specified parameters.
     *
     * @param dataSet         the input dataset from which the sample will be created
     * @param randomGenerator the random number generator used for sampling
     * @param selectedColumns an array of column indices to include in the sampled dataset
     * @param parameters      the parameters for sampling, including sampling fraction and resampling method
     * @return a new dataset containing the selected rows and columns
     */
    public static DataSet createDataSample(DataSet dataSet, RandomGenerator randomGenerator, int[] selectedColumns,
                                           Parameters parameters, double percentResamplingSize) {
        if (percentResamplingSize < 10.0 || percentResamplingSize > 100.0) {
            throw new IllegalArgumentException("Invalid percent resample size: " + percentResamplingSize
                                               + "; should be >= 10% and <= 100%");
        }
        double r = percentResamplingSize / 100.0;
        int sampleSize = (int) (dataSet.getNumRows() * (r));
        boolean isResamplingWithReplacement = parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT);
        int[] selectedRows = isResamplingWithReplacement ? getRowIndexesWithReplacement(dataSet, sampleSize, randomGenerator) : getRowIndexesWithoutReplacement(dataSet, sampleSize, randomGenerator);

        // create a new dataset containing selected rows and selected columns
        Matrix matrix = dataSet.getDoubleData();
        BoxDataSet boxDataSet = new BoxDataSet(new VerticalDoubleDataBox(matrix.view(selectedRows, selectedColumns).mat().transpose().toArray()), dataSet.getVariables());
        boxDataSet.setKnowledge(dataSet.getKnowledge());
        return boxDataSet;
    }

    /**
     * Get the unique, randomly-selected row indexes of the dataset.
     *
     * @param dataSet         dataset to select rows from
     * @param sampleSize      number of rows to select
     * @param randomGenerator random number generator (optional)
     * @return unique, randomly-selected row indexes
     */
    private static int[] getRowIndexesWithoutReplacement(DataSet dataSet, int sampleSize, RandomGenerator randomGenerator) {
        // ensure we don't go out-of-bound
        int numOfData = dataSet.getNumRows();
        if (numOfData < sampleSize) {
            sampleSize = numOfData;
        }

        int[] rows = IntStream.range(0, numOfData).toArray();
        shuffle(rows, randomGenerator);

        // copy sample-size row indices
        int[] rowIndexes = new int[sampleSize];
        System.arraycopy(rows, 0, rowIndexes, 0, sampleSize);

        return rowIndexes;
    }

    /**
     * Get the non-unique, randomly-selected row indexes of the dataset.
     *
     * @param dataSet         dataset to select rows from
     * @param sampleSize      number of rows to select
     * @param randomGenerator random number generator (optional)
     * @return non-unique, randomly-selected row indexes
     */
    private static int[] getRowIndexesWithReplacement(DataSet dataSet, int sampleSize, RandomGenerator randomGenerator) {
        int[] rowIndexes = new int[sampleSize];

        int numOfData = dataSet.getNumRows();
        if (randomGenerator == null) {
            for (int i = 0; i < rowIndexes.length; i++) {
                rowIndexes[i] = RandomUtil.getInstance().nextInt(numOfData);
            }
        } else {
            for (int i = 0; i < rowIndexes.length; i++) {
                rowIndexes[i] = randomGenerator.nextInt(numOfData);
            }
        }

        return rowIndexes;
    }

    /**
     * In-line array shuffle.
     *
     * @param array           the array to shuffle
     * @param randomGenerator random number generator (optional)
     */
    private static void shuffle(int[] array, RandomGenerator randomGenerator) {
        if (randomGenerator == null) {
            for (int i = 0; i < array.length - 1; i++) {
                int randIndex = RandomUtil.getInstance().nextInt(array.length - i) + i;

                // swap
                int currValue = array[i];
                array[i] = array[randIndex];
                array[randIndex] = currValue;
            }
        } else {
            for (int i = 0; i < array.length - 1; i++) {
                int randIndex = randomGenerator.nextInt(array.length - i) + i;

                // swap
                int currValue = array[i];
                array[i] = array[randIndex];
                array[randIndex] = currValue;
            }
        }
    }

}
