///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.cmu.tetrad.data.missing;

import edu.cmu.tetrad.data.DataSet;

import java.util.List;

/**
 * An interface for multiple-imputation methods: given a dataset with missing values, produce m completed copies in
 * which every missing entry has been replaced by a draw from an imputation model, with observed entries unchanged.
 * Downstream, a search is run on each completed dataset and the results are pooled (see {@link ImputationSearch}).
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see MissingDataPolicy#MULTIPLE_IMPUTATION
 */
public interface MultipleImputer {

    /**
     * Produces m completed copies of the given dataset.
     *
     * @param dataSet The dataset; must contain at least one missing value.
     * @param m       The number of imputations; at least 2.
     * @param seed    A random seed for reproducibility, or -1 for a random seed.
     * @return The m completed datasets.
     */
    List<DataSet> impute(DataSet dataSet, int m, long seed);
}
