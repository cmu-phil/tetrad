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

package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.PcRunner;

import java.io.Serial;

/**
 * The ExpandColumnsWrapper class is a data wrapper utility that provides functionality to expand discrete and
 * continuous columns of a dataset. Discrete columns are expanded by creating indicator variables for each category, and
 * continuous columns are expanded using a truncated basis expansion. This process leverages the embedded data expansion
 * functionality provided by the BasisFunctionScore class.
 * <p>
 * This wrapper operates on tabular datasets and modifies the data model to include the expanded data representations.
 * Additionally, it supports logging of the transformation applied to the data models.
 * <p>
 * Currently this is only being used to expand discrete columns.
 */
public class ExpandColumnsWrapper extends DataWrapper {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Expands discrete columns to give indicators for each category and expands continuous columns to give a truncated
     * basis expansion. This uses the embedded data expansion of BasisFunctionScore.
     *
     * @param data   a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ExpandColumnsWrapper(DataWrapper data, Parameters params) {
        if (data == null) {
            throw new NullPointerException("The given data must not be null");
        }

        DataModelList dataSets = data.getDataModelList();
        DataModelList convertedDataSets = new DataModelList();

        for (DataModel dataModel : dataSets) {
            if (!(dataModel instanceof DataSet originalData)) {
                throw new IllegalArgumentException("Only tabular data sets can be converted to time lagged form.");
            }

            DataSet convertedData;
            convertedData = Embedding.getEmbeddedData(originalData, 1, 1, -1).embeddedData();
            convertedDataSets.add(convertedData);
        }

        setDataModel(convertedDataSets);
        setSourceGraph(data.getSourceGraph());

        LogDataUtils.logDataModelList("Data in which numerical discrete columns of parent node data have been expanded.",
                getDataModelList());

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.PcRunner} object
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }


}


