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

package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DataWrapper;

import java.io.Serial;

/**
 * Splits a single tabular data set into several data sets, one per row condition, as specified in the Split Data
 * parameter editor. The output is a list of data sets sharing the same variables, suitable as input to multi-data-set
 * searches such as IMaGES, or to any box that accepts several data sets.
 * <p>
 * Like {@link DataSubsetModel}, this model stores the specification rather than the result, and re-applies it to the
 * parent's current data every time it is constructed, so changes made upstream propagate. See {@link DataSplitter}
 * for the specification language.
 */
public class DataSplitModel extends DataWrapper {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the splits of the given data.
     *
     * @param data   the parent data wrapper; must contain exactly one tabular data set.
     * @param params the parameters, holding the split spec written by the parameter editor.
     * @throws IllegalArgumentException if the parent does not hold exactly one tabular data set, if no spec is
     *                                  present, or if the spec cannot be applied to the parent data (e.g. a condition
     *                                  names a variable or category that no longer exists).
     */
    public DataSplitModel(DataWrapper data, Parameters params) {
        if (data == null) throw new NullPointerException("The given data must not be null");
        if (params == null) throw new NullPointerException("The given parameters must not be null");

        DataModelList dataSets = data.getDataModelList();
        if (dataSets.size() != 1) {
            throw new IllegalArgumentException("For splitting, you need exactly one data set.");
        }

        DataModel parentModel = dataSets.getFirst();
        if (!(parentModel instanceof DataSet parent)) {
            throw new IllegalArgumentException("The data to be split must be a tabular data set.");
        }

        setDataModel(computeSplits(parent, params));
        setSourceGraph(data.getSourceGraph());

        LogDataUtils.logDataModelList("Splits of the parent data.", getDataModelList());
    }

    /**
     * Computes the splits of {@code parent} described by the spec in {@code params}. Static so it can be tested
     * without a session.
     *
     * @param parent the parent data set.
     * @param params the parameters.
     * @return the splits.
     * @throws IllegalArgumentException if no spec is present or the spec cannot be applied.
     */
    static DataModelList computeSplits(DataSet parent, Parameters params) {
        DataSplitter.Spec spec = DataSplitter.Spec.fromParameters(params);

        if (spec == null) {
            throw new IllegalArgumentException("No data split specification found; open Edit Parameters... "
                    + "to specify the splits.");
        }

        return DataSplitter.split(parent, spec);
    }
}
