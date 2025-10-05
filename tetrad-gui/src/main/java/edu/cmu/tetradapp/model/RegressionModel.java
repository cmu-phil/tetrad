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

package edu.cmu.tetradapp.model;

import java.util.List;

/**
 * Methods common to regression models.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface RegressionModel {
    /**
     * <p>getVariableNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    List<String> getVariableNames();

    /**
     * <p>getRegressorNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    List<String> getRegressorNames();

    /**
     * <p>setRegressorName.</p>
     *
     * @param predictors a {@link java.util.List} object
     */
    void setRegressorName(List<String> predictors);

    /**
     * <p>getTargetName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    String getTargetName();

    /**
     * <p>setTargetName.</p>
     *
     * @param target a {@link java.lang.String} object
     */
    void setTargetName(String target);
}

