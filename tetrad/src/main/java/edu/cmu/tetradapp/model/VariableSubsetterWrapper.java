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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.util.TetradSerializableUtils;

/**
 * Creates a data set from the set of selected columns in the given data set.
 * (Makes a copy of the selected data.)
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class VariableSubsetterWrapper extends DataWrapper {
    static final long serialVersionUID = 23L;

    public VariableSubsetterWrapper(DataWrapper wrapper) {
        DataSet dataSet =
                (DataSet) wrapper.getSelectedDataModel();
        DataSet selection =
                dataSet.subsetColumns(dataSet.getSelectedIndices());

        DataSet selectionCopy;

        if (selection.isDiscrete()) {
            selectionCopy = selection;
        }
        else if (selection.isContinuous()) {
            selectionCopy = selection;
        }
        else {
            selectionCopy = selection;
        }

        setDataModel(selectionCopy);
        setSourceGraph(wrapper.getSourceGraph());

        LogDataUtils.logDataModelList("Restriction of parent data to selected variables.", getDataModelList());

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        return new VariableSubsetterWrapper(DataWrapper.serializableInstance());
    }
}





