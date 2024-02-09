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
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.ObjectStreamException;
import java.io.Serial;

/**
 * A typesafe enumeration of the types of independence tests that are used for basic search algorithm in this package.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class IndTestType implements TetradSerializable {
    /** Constant <code>DEFAULT</code> */
    public static final IndTestType DEFAULT = new IndTestType("Default", null);
    /** Constant <code>CORRELATION_T</code> */
    public static final IndTestType CORRELATION_T =
            new IndTestType("Correlation T Test", DataType.Continuous);
    /** Constant <code>FISHER_Z</code> */
    public static final IndTestType FISHER_Z = new IndTestType("Fisher's Z", DataType.Continuous);
    /** Constant <code>LINEAR_REGRESSION</code> */
    public static final IndTestType LINEAR_REGRESSION =
            new IndTestType("Linear Regression", DataType.Continuous);
    /** Constant <code>CONDITIONAL_CORRELATION</code> */
    public static final IndTestType CONDITIONAL_CORRELATION =
            new IndTestType("Conditional Correlation Test", DataType.Continuous);
    /** Constant <code>SEM_BIC</code> */
    public static final IndTestType SEM_BIC =
            new IndTestType("SEM BIC used as a Test", DataType.Continuous);
    /** Constant <code>LOGISTIC_REGRESSION</code> */
    public static final IndTestType LOGISTIC_REGRESSION =
            new IndTestType("Logistic Regression", DataType.Continuous);
    /** Constant <code>MIXED_MLR</code> */
    public static final IndTestType MIXED_MLR =
            new IndTestType("Multinomial Logistic Regression", DataType.Mixed);
    /** Constant <code>FISHER_ZD</code> */
    public static final IndTestType FISHER_ZD =
            new IndTestType("Fisher's Z (Deterministic)", DataType.Continuous);
    /** Constant <code>G_SQUARE</code> */
    public static final IndTestType G_SQUARE = new IndTestType("G Square", DataType.Discrete);
    /** Constant <code>CHI_SQUARE</code> */
    public static final IndTestType CHI_SQUARE = new IndTestType("Chi Square", DataType.Discrete);
    /** Constant <code>M_SEPARATION</code> */
    public static final IndTestType M_SEPARATION =
            new IndTestType("M-Separation", DataType.Graph);
    /** Constant <code>TIME_SERIES</code> */
    public static final IndTestType TIME_SERIES =
            new IndTestType("Time Series", DataType.Continuous);
    /** Constant <code>INDEPENDENCE_FACTS</code> */
    public static final IndTestType INDEPENDENCE_FACTS =
            new IndTestType("Independence Facts", DataType.Graph);
    /** Constant <code>POOL_RESIDUALS_FISHER_Z</code> */
    public static final IndTestType POOL_RESIDUALS_FISHER_Z =
            new IndTestType("Fisher Z Pooled Residuals", DataType.Continuous);
    /** Constant <code>FISHER</code> */
    public static final IndTestType FISHER = new IndTestType("Fisher (Fisher Z)", DataType.Continuous);
    /** Constant <code>TIPPETT</code> */
    public static final IndTestType TIPPETT = new IndTestType("Tippett (Fisher Z)", DataType.Continuous);
    @Serial
    private static final long serialVersionUID = 23L;
    private static final IndTestType[] TYPES = {IndTestType.DEFAULT, IndTestType.CORRELATION_T, IndTestType.FISHER_Z,
            IndTestType.LINEAR_REGRESSION, IndTestType.CONDITIONAL_CORRELATION, IndTestType.SEM_BIC, IndTestType.LOGISTIC_REGRESSION,
            IndTestType.MIXED_MLR, //IndTestType.FISHER_ZD,
            IndTestType.G_SQUARE, IndTestType.CHI_SQUARE,
            IndTestType.M_SEPARATION, IndTestType.TIME_SERIES,

            IndTestType.INDEPENDENCE_FACTS, IndTestType.POOL_RESIDUALS_FISHER_Z, IndTestType.FISHER, IndTestType.TIPPETT,

    };
    // Declarations required for serialization.
    private static int nextOrdinal;
    /**
     * The name of this dataType.
     */
    private final transient String name;
    private final DataType dataType;
    private final int ordinal = IndTestType.nextOrdinal++;

    /**
     * Protected constructor for the types; this allows for extension in case anyone wants to add formula types.
     */
    private IndTestType(String name, DataType type) {
        this.name = name;
        this.dataType = type;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.util.IndTestType} object
     */
    public static IndTestType serializableInstance() {
        return IndTestType.DEFAULT;
    }

    /**
     * Prints out the name of the dataType.
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return this.name;
    }

    Object readResolve() throws ObjectStreamException {
        return IndTestType.TYPES[this.ordinal]; // Canonicalize.
    }

    /**
     * <p>Getter for the field <code>dataType</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataType} object
     */
    public DataType getDataType() {
        return this.dataType;
    }
}





