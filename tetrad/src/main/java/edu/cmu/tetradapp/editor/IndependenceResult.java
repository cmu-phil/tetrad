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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.text.NumberFormat;

/**
 * Stores the result of an independence test.
 */
public final class IndependenceResult implements TetradSerializable {
    static final long serialVersionUID = 23L;

    public enum Type {
        INDEPENDENT, DEPENDENT, UNDETERMINED
    }

    private static NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    private int index;
    private String fact;
    private Type indep;
    private double pValue;

    public IndependenceResult(int index, String fact, Type indep, double pValue) {
        this.index = index;
        this.fact = fact;
        this.indep = indep;
        this.pValue = pValue;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static IndependenceResult serializableInstance() {
        return new IndependenceResult(1, "X _||_ Y", Type.DEPENDENT, 0.0001);
    }

    public int getIndex() {
        return index;
    }

    public String getFact() {
        return fact;
    }

    public Type getType() {
        return indep;
    }

    public double getpValue() {
        return pValue;
    }

    public String toString() {
        return "Result: " + getFact() + "\t" + getType() + "\t" + nf.format(getpValue());
    }
}



