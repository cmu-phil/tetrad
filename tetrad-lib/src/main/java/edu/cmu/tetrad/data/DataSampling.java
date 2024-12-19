///////////////////////////////////////////////////////////////////////////////
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
package edu.cmu.tetrad.data;

import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.SynchronizedRandomGenerator;
import org.apache.commons.math3.random.Well44497b;

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
     * @param dataSet    dataset to resample
     * @param parameters bootstrap-related parameters
     * @return a list of resampled dataset
     */
    public static List<DataSet> createDataSamples(DataSet dataSet, Parameters parameters) {
        List<DataSet> dataSets = new LinkedList<>();
        // no resampling
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            dataSets.add(dataSet);
        } else {
            // create new random generator if a seed is given
            Long seed = parameters.getLong(Params.SEED);
            RandomGenerator randomGenerator = (seed < 0) ? null : new SynchronizedRandomGenerator(new Well44497b(seed));

            dataSets.addAll(createDataSamples(dataSet, parameters, randomGenerator));
        }

        return dataSets;
    }

    /**
     * Create a list of dataset resampled from the given dataset.
     *
     * @param dataSet         dataset to resample
     * @param parameters      bootstrap-related parameters
     * @param randomGenerator random number generator (optional)
     * @return a list of resampled dataset
     */
    public static List<DataSet> createDataSamples(DataSet dataSet, Parameters parameters, RandomGenerator randomGenerator) {
        List<DataSet> datasets = new LinkedList<>();

        int sampleSize = (int) (dataSet.getNumRows() * (parameters.getInt(Params.PERCENT_RESAMPLE_SIZE) / 100.0));
        boolean isResamplingWithReplacement = parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT);
        int[] selectedColumns = IntStream.range(0, dataSet.getNumColumns()).toArray();  // select all data columns
        for (int i = 0; i < parameters.getInt(Params.NUMBER_RESAMPLING) && !Thread.currentThread().isInterrupted(); i++) {
            // select data rows to create new dataset
            int[] selectedRows = isResamplingWithReplacement
                    ? getRowIndexesWithReplacement(dataSet, sampleSize, randomGenerator)
                    : getRowIndexesWithoutReplacement(dataSet, sampleSize, randomGenerator);

            // create a new dataset containing selected rows and selected columns
            Matrix matrix = dataSet.getDoubleData();
            BoxDataSet boxDataSet = new BoxDataSet(
                    new VerticalDoubleDataBox(matrix.view(selectedRows, selectedColumns).matrix().transpose().toArray()),
                    dataSet.getVariables());
            boxDataSet.setKnowledge(dataSet.getKnowledge());
            datasets.add(boxDataSet);
        }

        // add original dataset if requested
        if (parameters.getBoolean(Params.ADD_ORIGINAL_DATASET)) {
            datasets.add(dataSet);
        }

        return datasets;
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
