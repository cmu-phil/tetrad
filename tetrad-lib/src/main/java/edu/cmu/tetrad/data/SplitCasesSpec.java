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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Specifies how a column (continuous or discrete) should be discretized. For a discrete column the mapping is int[]
 * remap; for a continuous column the mapping is double[] cutoffs. The splitNames are the string labels for the
 * splitNames. This is just a small immutable class that columns can map to in order to remember how discretizations
 * were done so that the user doesn't have to keep typing in information over and over again.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class SplitCasesSpec implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Breakpoints, for continuous data.
     */
    private final int[] breakpoints;

    /**
     * Split names.
     */
    private final List<String> splitNames;

    /**
     * Sample size.
     */
    private final int sampleSize;

    /**
     * <p>Constructor for SplitCasesSpec.</p>
     *
     * @param sampleSize  a int
     * @param breakpoints an array of {@link int} objects
     * @param splits      a {@link java.util.List} object
     */
    public SplitCasesSpec(int sampleSize, int[] breakpoints,
                          List<String> splits) {
        this.sampleSize = sampleSize;
        this.breakpoints = breakpoints;
        this.splitNames = splits;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.data.SplitCasesSpec} object
     */
    public static SplitCasesSpec serializableInstance() {
        return new SplitCasesSpec(0, new int[0], new ArrayList<>());
    }

    /**
     * <p>Getter for the field <code>splitNames</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getSplitNames() {
        return this.splitNames;
    }

    /**
     * <p>Getter for the field <code>breakpoints</code>.</p>
     *
     * @return an array of {@link int} objects
     */
    public int[] getBreakpoints() {
        return this.breakpoints;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s a {@link java.io.ObjectInputStream} object
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.breakpoints == null) {
            throw new NullPointerException();
        }

        if (this.splitNames == null) {
            throw new NullPointerException();
        }
    }

    /**
     * <p>Getter for the field <code>sampleSize</code>.</p>
     *
     * @return a int
     */
    public int getSampleSize() {
        return this.sampleSize;
    }
}



