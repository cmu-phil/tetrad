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


    // private TetradLogger logger = TetradLogger.getInstance();

    private boolean withoutReplacements;


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
        //   this.logger.log("sampleSize", String.valueOf(newSampleSize));
        //Number of samples in input dataset
        int oldSampleSize = dataSet.getNumRows();
        int ncols = dataSet.getNumColumns();

        DataSet newDataSet = new BoxDataSet(new VerticalDoubleDataBox(newSampleSize, dataSet.getVariables().size()), dataSet.getVariables());
        Set<Integer> oldCases = new HashSet<>();

        // (not keeping order)
        for (int row = 0; row < newSampleSize; row++) {
            int oldCase = RandomUtil.getInstance().nextInt(oldSampleSize);

            if (isWithoutReplacements()) {
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
    public boolean isWithoutReplacements() {
        return this.withoutReplacements;
    }

    /**
     * This method takes a dataset and a sample size and creates a new dataset containing that number of samples by
     * drawing with replacement from the original dataset.
     *
     * @param withoutReplacements the sample size
     */
    public void setWithoutReplacements(boolean withoutReplacements) {
        this.withoutReplacements = withoutReplacements;
    }
}





