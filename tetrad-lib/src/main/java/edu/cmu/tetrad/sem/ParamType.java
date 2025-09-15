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

package edu.cmu.tetrad.sem;

/**
 * An enum of the free parameter types for SEM models (COEF, MEAN, VAR, COVAR). COEF freeParameters are edge
 * coefficients in the linear SEM model; VAR parmaeters are variances among the error terms; COVAR freeParameters are
 * (non-variance) covariances among the error terms.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public enum ParamType {
    /**
     * Enum representing the free parameter type for structural equation modeling (SEM) models. COEF free parameters are
     * edge coefficients in the linear SEM model.
     */
    COEF("Linear Coefficient"),
    /**
     * Variable Mean parameter type for SEM models.
     */
    MEAN("Variable Mean"),
    /**
     * Represents the error variance parameter in a structural equation modeling (SEM) model.
     */
    VAR("Error Variance"),
    /**
     * Represents a free parameter type for structural equation modeling (SEM) models. Specifically, the COVAR free
     * parameter type is used to represent non-variance covariances among the error terms in the SEM model.
     * <p>
     * This enum type is a part of the ParamType enum, which is used to categorize different types of free parameters
     * for SEM models.
     * <p>
     * The COVAR free parameter type is associated with the description "Error Covariance".
     */
    COVAR("Error Covariance"),
    /**
     * Represents a free parameter type for structural equation modeling (SEM) models. Specifically, the DIST free
     * parameter type is used to represent distribution parameters in the SEM model. It is associated with the
     * description "Distribution Parameter".
     */
    DIST("Distribution Parameter");

    private final String name;

    ParamType(String name) {
        this.name = name;
    }

    public String toString() {
        return this.name;
    }
}






