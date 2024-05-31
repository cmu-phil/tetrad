///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.data.DataType;

public enum IndTestType {
    DEFAULT("Default", null),
    CORRELATION_T("Correlation T Test", DataType.Continuous),
    FISHER_Z("Fisher's Z", DataType.Continuous),
    LINEAR_REGRESSION("Linear Regression", DataType.Continuous),
    CONDITIONAL_CORRELATION("Conditional Correlation Test", DataType.Continuous),
    SEM_BIC("SEM BIC used as a Test", DataType.Continuous),
    LOGISTIC_REGRESSION("Logistic Regression", DataType.Continuous),
    MIXED_MLR("Multinomial Logistic Regression", DataType.Mixed),
    G_SQUARE("G Square", DataType.Discrete),
    CHI_SQUARE("Chi Square", DataType.Discrete),
    M_SEPARATION("M-Separation", DataType.Graph),
    TIME_SERIES("Time Series", DataType.Continuous),
    INDEPENDENCE_FACTS("Independence Facts", DataType.Graph),
    POOL_RESIDUALS_FISHER_Z("Fisher Z Pooled Residuals", DataType.Continuous),
    FISHER("Fisher (Fisher Z)", DataType.Continuous),
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




