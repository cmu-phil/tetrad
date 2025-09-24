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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.util.RandomUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * Provides a static method for sampling with replacement from a dataset to create a new dataset with a sample size
 * supplied by the user.
 * <p>
 * Since sampling is done with replacement, the output dataset can have more samples than the input.
 *
 * @author Frank Wimberly
 * @version $Id: $Id
 */
public final class BootstrapSampler {
    /**
     * Whether to sample with replacement.
     */
    private boolean withReplacement;

    /**
     * Constructs a sample that does not do any logging.
     */
    public BootstrapSampler() {

    }

    /**
     * This method takes a dataset and a sample size and creates a new dataset containing that number of samples by
     * drawing with replacement from the original dataset.
     *
     * @param dataSet       the dataset to sample from
     * @param newSampleSize the number of samples to draw
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet sample(DataSet dataSet, int newSampleSize) {
        if (newSampleSize < 1) {
            throw new IllegalArgumentException("Sample size must be > 0.");
        }

        if (dataSet.getNumRows() < 1) {
            throw new IllegalArgumentException("Dataset must contain samples.");
        }

        //Number of samples in input dataset
        int oldSampleSize = dataSet.getNumRows();
        int ncols = dataSet.getNumColumns();

        DataSet newDataSet = new BoxDataSet(new VerticalDoubleDataBox(newSampleSize, dataSet.getVariables().size()), dataSet.getVariables());
        Set<Integer> oldCases = new HashSet<>();

        if (!isWithReplacement() && newSampleSize > oldSampleSize) {
            throw new IllegalArgumentException("For without replacement, sample size must be <= the number of samples in the dataset.");
        }

        // (not keeping order)
        for (int row = 0; row < newSampleSize; row++) {
            int oldCase = RandomUtil.getInstance().nextInt(oldSampleSize);

            if (!isWithReplacement()) {
                if (oldCases.contains(oldCase)) {
                    row--;
                    continue;
                }
                oldCases.add(oldCase);
            }

            for (int col = 0; col < ncols; col++) {
                newDataSet.setObject(row, col, dataSet.getObject(oldCase, col));
            }
        }

        newDataSet.setKnowledge(dataSet.getKnowledge().copy());

        return newDataSet;
    }

    /**
     * This method takes a dataset and a sample size and creates a new dataset containing that number of samples by
     *
     * @return the sample size
     */
    public boolean isWithReplacement() {
        return this.withReplacement;
    }

    /**
     * This method takes a dataset and a sample size and creates a new dataset containing that number of samples by
     * drawing with replacement from the original dataset.
     *
     * @param withReplacement the sample size
     */
    public void setWithReplacement(boolean withReplacement) {
        this.withReplacement = withReplacement;
    }
}






