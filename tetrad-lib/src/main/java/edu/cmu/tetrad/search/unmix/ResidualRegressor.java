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

package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Interface for local regressors used to compute residual signatures: X ~ f(Pa). Implementations should provide a
 * fit(...) to estimate parameters and a predict(...) to produce fitted values for all rows in the dataset.
 */
public interface ResidualRegressor {
    /**
     * Fits a local mechanism for the target variable using its parents from the provided dataset.
     *
     * @param data    the dataset containing the variables and rows
     * @param target  the variable to be predicted
     * @param parents the list of parent variables used as predictors
     */
    void fit(DataSet data, Node target, List<Node> parents);

    /**
     * Predicts fitted values for all rows for the given target using the current fitted model. Implementations may call
     * fit(...) lazily if not yet fitted.
     *
     * @param data    the dataset containing the variables and rows
     * @param target  the variable to be predicted
     * @param parents the list of parent variables used as predictors
     * @return an array of length n with fitted values for each row
     */
    double[] predict(DataSet data, Node target, List<Node> parents);

    /**
     * Convenience method computing residuals = y - yhat for all rows.
     *
     * @param data    the dataset containing the variables and rows
     * @param target  the variable whose residuals are to be computed
     * @param parents the list of parent variables used as predictors
     * @return an array of residuals for each row
     */
    default double[] residuals(DataSet data, Node target, List<Node> parents) {
        double[] yhat = predict(data, target, parents);
        double[] y = data.getDoubleData().getColumn(data.getColumn(target)).toArray();
        double[] r = new double[y.length];
        for (int i = 0; i < y.length; i++) r[i] = y[i] - yhat[i];
        return r;
    }
}
