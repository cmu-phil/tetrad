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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.text.NumberFormat;

/**
 * Stores the result of an independence test.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class IndependenceResultIndFacts implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    private static final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    /**
     * The index of the result.
     */
    private final int index;
    /**
     * The fact.
     */
    private final String fact;
    /**
     * The type of independence.
     */
    private final Type indep;
    /**
     * The p-value.
     */
    private final double pValue;

    /**
     * <p>Constructor for IndependenceResultIndFacts.</p>
     *
     * @param index  a int
     * @param fact   a {@link java.lang.String} object
     * @param indep  a {@link edu.cmu.tetradapp.model.IndependenceResultIndFacts.Type} object
     * @param pValue a double
     */
    public IndependenceResultIndFacts(int index, String fact, Type indep, double pValue) {
        this.index = index;
        this.fact = fact;
        this.indep = indep;
        this.pValue = pValue;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.IndependenceResultIndFacts} object
     * @see TetradSerializableUtils
     */
    public static IndependenceResultIndFacts serializableInstance() {
        return new IndependenceResultIndFacts(1, "X _||_ Y", Type.DEPENDENT, 0.0001);
    }

    /**
     * <p>Getter for the field <code>index</code>.</p>
     *
     * @return a int
     */
    public int getIndex() {
        return this.index;
    }

    /**
     * <p>Getter for the field <code>fact</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getFact() {
        return this.fact;
    }

    /**
     * <p>getType.</p>
     *
     * @return a {@link edu.cmu.tetradapp.model.IndependenceResultIndFacts.Type} object
     */
    public Type getType() {
        return this.indep;
    }

    /**
     * <p>Getter for the field <code>pValue</code>.</p>
     *
     * @return a double
     */
    public double getpValue() {
        return this.pValue;
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "Result: " + getFact() + "\t" + getType() + "\t" + IndependenceResultIndFacts.nf.format(getpValue());
    }

    /**
     * An enum of fact types.
     */
    public enum Type {

        /**
         * INDEPENDENT.
         */
        INDEPENDENT,

        /**
         * DEPENDENT.
         */
        DEPENDENT,

        /**
         * UNDETERMINED.
         */
        UNDETERMINED
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }
}



