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

package edu.cmu.tetrad.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides a static method for sampling without replacement from a dataset to
 * create a new dataset with a sample size supplied by the user.
 *
 * @author Frank Wimberly
 */
public final class RandomSampler {

    /**
     * This method takes a dataset and a sample size and creates a new dataset
     * containing that number of samples by drawing with replacement from the
     * original dataset.
     */
    public static DataSet sample(DataSet dataSet,
                                 int newSampleSize) {
        if (newSampleSize < 1) {
            throw new IllegalArgumentException("Sample size must be > 0.");
        }

        if (dataSet.getNumRows() < 1) {
            throw new IllegalArgumentException("Dataset must contain samples.");
        }

        if (dataSet.getNumRows() < newSampleSize) {
            throw new IllegalArgumentException("Not enough cases in data to " +
                    "generate " + newSampleSize + " samples without replacement.");
        }

        List<Integer> indices = new ArrayList<Integer>(dataSet.getNumRows());

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            indices.add(i);
        }

        Collections.shuffle(indices);

        //Number of samples in input dataset
        int ncols = dataSet.getNumColumns();

        DataSet newDataSet =
                new ColtDataSet(newSampleSize, dataSet.getVariables());

        for (int i = 0; i < newSampleSize; i++) {
            int oldCase = indices.get(i);

            for (int j = 0; j < ncols; j++) {
                newDataSet.setObject(i, j, dataSet.getObject(oldCase, j));
            }
        }

        return newDataSet;
    }
}



