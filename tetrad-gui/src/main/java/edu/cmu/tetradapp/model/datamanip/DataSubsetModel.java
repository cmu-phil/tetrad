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
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.util.WatchedProcess;

import java.io.Serial;

/**
 * Creates a subset of a given dataset (a subset of the variables, plus a subset of the rows, optionally conditioned,
 * shuffled, subsampled, or bootstrapped) as specified in the Data Subset parameter editor.
 * <p>
 * The subset is recomputed from the parent data set every time this model is constructed, by applying the stored
 * {@link DataSubsetter.Spec} to the parent's current data. Previously the editor baked the finished subset into the
 * parameters and this constructor simply reinstalled it, so propagating a change made upstream (e.g. removing a
 * variable) left the subset stale. The baked subset is now used only as a fallback for sessions saved before the
 * spec was stored.
 */
public class DataSubsetModel extends DataWrapper {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the subset of the given data.
     *
     * @param data   the parent data wrapper; must contain exactly one data set.
     * @param params the parameters, holding the subset spec written by the parameter editor.
     * @throws IllegalArgumentException if the parent does not hold exactly one data set, if no spec (or legacy
     *                                  subset) is present, or if the spec cannot be applied to the parent data (e.g. a
     *                                  condition names a variable that no longer exists).
     */
    public DataSubsetModel(DataWrapper data, Parameters params) {
        if (data == null) throw new NullPointerException("The given data must not be null");

        DataModelList dataSets = data.getDataModelList();
        if (dataSets.size() != 1) {
            throw new IllegalArgumentException("For data subsetting, you need exactly one data set.");
        }

        DataModel parentModel = dataSets.getFirst();
        if (!(parentModel instanceof DataSet parent)) {
            throw new IllegalArgumentException("The data to be subsetted must be a tabular data set.");
        }

        DataSet subset = computeSubset(parent, params);

        new WatchedProcess() {
            @Override
            public void watch() {
                DataModelList out = new DataModelList();
                out.add(subset);
                out.getFirst().setName("Data Subset");
                setDataModel(out);
            }
        };
    }

    /**
     * Computes the subset of {@code parent} described by the spec in {@code params}, falling back to a legacy baked
     * subset if no spec is present. Package-private and static so it can be tested without a session.
     *
     * @param parent the parent data set.
     * @param params the parameters.
     * @return the subset.
     */
    static DataSet computeSubset(DataSet parent, Parameters params) {
        DataSubsetter.Spec spec = DataSubsetter.Spec.fromParameters(params);

        if (spec != null) {
            return DataSubsetter.subset(parent, spec);
        }

        Object legacy = params.getParametersNames().contains(DataSubsetter.KEY_LEGACY_SUBSET)
                ? params.get(DataSubsetter.KEY_LEGACY_SUBSET, null) : null;

        if (legacy instanceof DataSet legacySubset) {
            return legacySubset;
        }

        throw new IllegalArgumentException("No data subset specification found; open Edit Parameters... "
                + "to specify the subset.");
    }
}
