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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataFilter;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.LinkedList;
import java.util.List;

/**
 * @return a data set in which missing values in each column are filled using
 * the mean of that column.
 *
 * @author Joseph Ramsey
 */
public final class MeanInterpolator implements DataFilter {
    public DataSet filter(DataSet dataSet) {
        List<Node> variables = new LinkedList<Node>();

        for (int i = 0; i < dataSet.getNumColumns(); i++) {
            Node variable = dataSet.getVariable(i);
            variables.add(variable);
        }

        DataSet newDataSet = dataSet.copy();

        for (int j = 0; j < newDataSet.getNumColumns(); j++) {
            if (newDataSet.getVariable(j) instanceof ContinuousVariable) {
                double sum = 0.0;
                int count = 0;

                for (int i = 0; i < newDataSet.getNumRows(); i++) {
                    if (!Double.isNaN(newDataSet.getDouble(i, j))) {
                        sum += newDataSet.getDouble(i, j);
                        count++;
                    }
                }

                double mean = sum / count;

                for (int i = 0; i < newDataSet.getNumRows(); i++) {
                    if (Double.isNaN(newDataSet.getDouble(i, j))) {
                        newDataSet.setDouble(i, j, mean);
                    }
                }
            }
        }

        return newDataSet;
    }
}



