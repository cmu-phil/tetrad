///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.ObjectStreamException;

/**
 * A typesafe enumeration of the types of independence tests that are used for basic search algorithm in this package.
 *
 * @author Joseph Ramsey
 */
public final class IndTestType implements TetradSerializable {
    static final long serialVersionUID = 23L;

    public static final IndTestType DEFAULT = new IndTestType("Default", null);
    public static final IndTestType CORRELATION_T =
            new IndTestType("Correlation T Test", DataType.Continuous);
    public static final IndTestType FISHER_Z = new IndTestType("Fisher's Z", DataType.Continuous);
    public static final IndTestType LINEAR_REGRESSION =
            new IndTestType("Linear Regression", DataType.Continuous);
    public static final IndTestType CONDITIONAL_CORRELATION =
            new IndTestType("Conditional Correlation Test", DataType.Continuous);
    public static final IndTestType SEM_BIC =
            new IndTestType("SEM BIC used as a Test", DataType.Continuous);
    public static final IndTestType LOGISTIC_REGRESSION =
            new IndTestType("Logistic Regression", DataType.Continuous);
    public static final IndTestType MIXED_MLR =
            new IndTestType("Multinomial Logistic Regression", DataType.Mixed);
    public static final IndTestType FISHER_ZD =
            new IndTestType("Fisher's Z (Deterministic)", DataType.Continuous);
    public static final IndTestType FISHER_Z_BOOTSTRAP =
            new IndTestType("Fisher's Z (Bootstrap)", DataType.Continuous);
    public static final IndTestType G_SQUARE = new IndTestType("G Square", DataType.Discrete);
    public static final IndTestType CHI_SQUARE = new IndTestType("Chi Square", DataType.Discrete);
    public static final IndTestType D_SEPARATION =
            new IndTestType("D-Separation", DataType.Graph);
    public static final IndTestType TIME_SERIES =
            new IndTestType("Time Series", DataType.Continuous);
    public static final IndTestType INDEPENDENCE_FACTS =
            new IndTestType("Independence Facts", DataType.Graph);
    public static final IndTestType POOL_RESIDUALS_FISHER_Z =
            new IndTestType("Fisher Z Pooled Residuals", DataType.Continuous);
    public static final IndTestType FISHER = new IndTestType("Fisher (Fisher Z)", DataType.Continuous);
    public static final IndTestType TIPPETT = new IndTestType("Tippett (Fisher Z)", DataType.Continuous);
    public static final IndTestType MIXED_REGR_LRT = new IndTestType("Mixed Regression Likelihood Ratio Test",
                DataType.Mixed);
    public static final IndTestType MIXED_CG_LRT
            = new IndTestType("Mixed Conditional Gaussian Likelihood Ratio Test", DataType.Mixed);

    /**
     * The name of this dataType.
     */
    private final transient String name;
    private final DataType dataType;

    /**
     * Protected constructor for the types; this allows for extension in case anyone wants to add formula types.
     */
    protected IndTestType(String name, DataType type) {
        this.name = name;
        this.dataType = type;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static IndTestType serializableInstance() {
        return IndTestType.DEFAULT;
    }

    /**
     * Prints out the name of the dataType.
     */
    public String toString() {
        return name;
    }

    // Declarations required for serialization.
    private static int nextOrdinal = 0;
    private final int ordinal = nextOrdinal++;
    private static final IndTestType[] TYPES = {DEFAULT, CORRELATION_T, FISHER_Z,
            LINEAR_REGRESSION, CONDITIONAL_CORRELATION, SEM_BIC, LOGISTIC_REGRESSION,
            MIXED_MLR, FISHER_ZD,
            FISHER_Z_BOOTSTRAP,
            G_SQUARE, CHI_SQUARE,
            D_SEPARATION, TIME_SERIES,

            INDEPENDENCE_FACTS, POOL_RESIDUALS_FISHER_Z, FISHER, TIPPETT,

    };

    Object readResolve() throws ObjectStreamException {
        return TYPES[ordinal]; // Canonicalize.
    }

    public DataType getDataType() {
        return dataType;
    }
}





