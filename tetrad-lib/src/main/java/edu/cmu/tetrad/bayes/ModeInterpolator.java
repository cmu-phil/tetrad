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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataFilter;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;

import java.util.Arrays;

/**
 * Creates a data set in which missing values in each column are filled using
 * the mode of that column.
 *
 * @author Joseph Ramsey
 */
public final class ModeInterpolator implements DataFilter {


    public DataSet filter(DataSet dataSet) {
        DataSet newDataSet = dataSet.copy();

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Node var = dataSet.getVariable(j);
            if (var instanceof DiscreteVariable) {
                DiscreteVariable variable = (DiscreteVariable) var;
                int numCategories = variable.getNumCategories();
                int[] categoryCounts = new int[numCategories];

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    if (dataSet.getInt(i, j) == DiscreteVariable.MISSING_VALUE) {
                        continue;
                    }

                    categoryCounts[dataSet.getInt(i, j)]++;
                }

                int mode = -1;
                int max = -1;

                for (int i = 0; i < numCategories; i++) {
                    if (categoryCounts[i] > max) {
                        max = categoryCounts[i];
                        mode = i;
                    }
                }

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    if (dataSet.getInt(i, j) == DiscreteVariable.MISSING_VALUE) {
                        newDataSet.setInt(i, j, mode);
                    }
//                    else {
//                        newDataSet.setInt(i, j, dataSet.getInt(i, j));
//                    }
                }
            } else if (dataSet.getVariable(j) instanceof ContinuousVariable) {
                double[] data = new double[dataSet.getNumRows()];
                int k = -1;

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    if (!Double.isNaN(dataSet.getDouble(i, j))) {
                        data[++k] = dataSet.getDouble(i, j);
                    }
                }

                Arrays.sort(data);

                double mode = Double.NaN;

                if (k >= 0) {
                    mode = (data[(k + 1) / 2] + data[k / 2]) / 2.d;
                }

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    if (Double.isNaN(dataSet.getDouble(i, j))) {
                        newDataSet.setDouble(i, j, mode);
                    }
//                    else {
//                        newDataSet.setDouble(i, j, dataSet.getDouble(i, j));
//                    }
                }
            }
        }

        return newDataSet;
    }
}





