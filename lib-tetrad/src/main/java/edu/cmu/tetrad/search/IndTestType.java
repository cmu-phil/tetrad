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

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.ObjectStreamException;

/**
 * A typesafe enumeration of the types of independence tests that are used for basic search algorithms in this package.
 *
 * @author Joseph Ramsey
 */
public final class IndTestType implements TetradSerializable {
    static final long serialVersionUID = 23L;

    public static final IndTestType DEFAULT = new IndTestType("Default");
    public static final IndTestType CORRELATION_T =
            new IndTestType("Correlation T Test");
    public static final IndTestType FISHER_Z = new IndTestType("Fisher's Z");
    public static final IndTestType LINEAR_REGRESSION =
            new IndTestType("Linear Regression");
    public static final IndTestType CONDITIONAL_CORRELATION =
            new IndTestType("Conditional Correlation Test");
    public static final IndTestType LOGISTIC_REGRESSION =
            new IndTestType("Logistic Regression");
    public static final IndTestType MULTINOMIAL_LOGISTIC_REGRESSION =
            new IndTestType("Multinomial Logistic Regression");
    public static final IndTestType FISHER_ZD =
            new IndTestType("Fisher's Z (Deterministic)");
    public static final IndTestType FISHER_Z_BOOTSTRAP =
            new IndTestType("Fisher's Z (Bootstrap)");
    public static final IndTestType G_SQUARE = new IndTestType("G Square");
    public static final IndTestType CHI_SQUARE = new IndTestType("Chi Square");
    public static final IndTestType D_SEPARATION =
            new IndTestType("D-Separation");
    public static final IndTestType TIME_SERIES =
            new IndTestType("Time Series");
    public static final IndTestType INDEPENDENCE_FACTS =
            new IndTestType("Independence Facts");
    public static final IndTestType POOL_RESIDUALS_FISHER_Z =
            new IndTestType("Fisher Z Pooled Residuals");
    public static final IndTestType FISHER = new IndTestType("Fisher (Fisher Z)");
    public static final IndTestType TIPPETT = new IndTestType("Tippett (Fisher Z)");

    /**
     * The name of this type.
     */
    private final transient String name;

    /**
     * Protected constructor for the types; this allows for extension in case anyone wants to add formula types.
     */
    protected IndTestType(String name) {
        this.name = name;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static IndTestType serializableInstance() {
        return IndTestType.DEFAULT;
    }

    /**
     * Prints out the name of the type.
     */
    public String toString() {
        return name;
    }

    // Declarations required for serialization.
    private static int nextOrdinal = 0;
    private final int ordinal = nextOrdinal++;
    private static final IndTestType[] TYPES = {DEFAULT, CORRELATION_T, FISHER_Z,
            LINEAR_REGRESSION, CONDITIONAL_CORRELATION, LOGISTIC_REGRESSION,
            MULTINOMIAL_LOGISTIC_REGRESSION, FISHER_ZD,
            FISHER_Z_BOOTSTRAP,
            G_SQUARE, CHI_SQUARE,
            D_SEPARATION, TIME_SERIES,

            INDEPENDENCE_FACTS, POOL_RESIDUALS_FISHER_Z, FISHER, TIPPETT,

    };

    Object readResolve() throws ObjectStreamException {
        return TYPES[ordinal]; // Canonicalize.
    }
}





