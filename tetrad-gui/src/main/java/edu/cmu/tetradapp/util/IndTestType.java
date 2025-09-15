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

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.data.DataType;

public enum IndTestType {
    /**
     * Default test.
     */
    DEFAULT("Default", null),
    /**
     * T-test for correlation.
     */
    CORRELATION_T("Correlation T Test", DataType.Continuous),
    /**
     * Pearson's correlation coefficient.
     */
    FISHER_Z("Fisher's Z", DataType.Continuous),
    /**
     * Zero linear coefficient
     */
    LINEAR_REGRESSION("Linear Regression", DataType.Continuous),
    /**
     * Conditional correlation.
     */
    CONDITIONAL_CORRELATION("Conditional Correlation Test", DataType.Continuous),
    /**
     * SEM BIC used as a test.
     */
    SEM_BIC("SEM BIC used as a Test", DataType.Continuous),
    /**
     * Logistic regression.
     */
    LOGISTIC_REGRESSION("Logistic Regression", DataType.Continuous),
    /**
     * Multinomial logistic regression.
     */
    MIXED_MLR("Multinomial Logistic Regression", DataType.Mixed),
    /**
     * G Square.
     */
    G_SQUARE("G Square", DataType.Discrete),
    /**
     * Chi Square.
     */
    CHI_SQUARE("Chi Square", DataType.Discrete),
    /**
     * M-separation.
     */
    M_SEPARATION("M-Separation", DataType.Graph),
    /**
     * Time series.
     */
    TIME_SERIES("Time Series", DataType.Continuous),
    /**
     * Independence facts.
     */
    INDEPENDENCE_FACTS("Independence Facts", DataType.Graph),
    /**
     * Fisher's Z pooled residuals.
     */
    POOL_RESIDUALS_FISHER_Z("Fisher Z Pooled Residuals", DataType.Continuous),
    /**
     * Fisher's pooled p-values.
     */
    FISHER("Fisher (Fisher Z)", DataType.Continuous),
    /**
     * Tippett's pooled p-values.
     */
    TIPPETT("Tippett (Fisher Z)", DataType.Continuous);

    private final String name;
    private final DataType dataType;

    IndTestType(String name, DataType dataType) {
        this.name = name;
        this.dataType = dataType;
    }

    public String toString() {
        return this.name;
    }

    public DataType getDataType() {
        return this.dataType;
    }
}





