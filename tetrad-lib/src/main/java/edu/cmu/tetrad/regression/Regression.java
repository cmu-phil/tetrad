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

package edu.cmu.tetrad.regression;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Implements a multiple regression model, allowing data to be specified either as a tabular data set or as a covariance
 * matrix plus list of means.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface Regression {

    /**
     * Sets the significance level at which coefficients are judged to be significant.
     *
     * @param alpha the significance level.
     */
    void setAlpha(double alpha);

    /**
     * <p>getGraph.</p>
     *
     * @return This graph.
     */
    Graph getGraph();

    /**
     * Regresses <code>target</code> on the <code>regressors</code>, yielding a regression plane.
     *
     * @param target     the target variable, being regressed.
     * @param regressors the list of variables being regressed on.
     * @return the regression plane.
     */
    RegressionResult regress(Node target, List<Node> regressors);

    /**
     * Regresses <code>target</code> on the <code>regressors</code>, yielding a regression plane.
     *
     * @param target     the target variable, being regressed.
     * @param regressors the list of variables being regressed on.
     * @return the regression plane.
     */
    RegressionResult regress(Node target, Node... regressors);
}




