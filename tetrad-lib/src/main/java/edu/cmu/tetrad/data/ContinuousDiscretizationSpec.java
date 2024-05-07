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
 * remap; for a continuous column the mapping is double[] cutoffs. The categories are the string labels for the
 * categories. This is just a small immutable class that columns can map to in order to remember how discretizations
 * were done so that the user doesn't have to keep typing in information over and over again.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class ContinuousDiscretizationSpec implements TetradSerializable, DiscretizationSpec {
    /**
     * The types of discretization
     */
    public static final int EVENLY_DISTRIBUTED_VALUES = 1;
    /**
     * Constant <code>EVENLY_DISTRIBUTED_INTERVALS=2</code>
     */
    public static final int EVENLY_DISTRIBUTED_INTERVALS = 2;
    /**
     * Constant <code>NONE=3</code>
     */
    public static final int NONE = 3;
    private static final long serialVersionUID = 23L;
    /**
     * Breakpoints, for continuous data.
     */
    private final double[] breakpoints;
    /**
     * Categories, for discrete data.
     */
    private final List<String> categories;
    /**
     * The method used.
     */
    private int method;

    /**
     * Constructor for creating a ContinuousDiscretizationSpec object.
     *
     * @param breakpoints The array of breakpoints used for discretization.
     * @param categories  The list of categories for the discretized data.
     */
    public ContinuousDiscretizationSpec(double[] breakpoints, List<String> categories) {
        this(breakpoints, categories, ContinuousDiscretizationSpec.EVENLY_DISTRIBUTED_INTERVALS);
    }

    /**
     * Creates a ContinuousDiscretizationSpec object with the given breakpoints, categories, and method.
     *
     * @param breakpoints The array of breakpoints used for discretization.
     * @param categories  The list of categories for the discretized data.
     * @param method      The method used for discretization. Possible values are: - EVENLY_DISTRIBUTED_VALUES: 0
     *                    (evenly distributed values) - EVENLY_DISTRIBUTED_INTERVALS: 1 (evenly distributed intervals) -
     *                    NONE: 2 (no discretization)
     * @throws NullPointerException     if breakpoints is null.
     * @throws IllegalArgumentException if method is not one of the valid values.
     */
    public ContinuousDiscretizationSpec(double[] breakpoints, List<String> categories, int method) {
        if (breakpoints == null) {
            throw new NullPointerException();
        }
        if (method != ContinuousDiscretizationSpec.EVENLY_DISTRIBUTED_VALUES && method != ContinuousDiscretizationSpec.EVENLY_DISTRIBUTED_INTERVALS && method != ContinuousDiscretizationSpec.NONE) {
            throw new IllegalArgumentException();
        }
        this.breakpoints = breakpoints;
        this.categories = categories;
        this.method = method;
    }


    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.data.DiscretizationSpec} object
     */
    @SuppressWarnings("ZeroLengthArrayAllocation")
    public static DiscretizationSpec serializableInstance() {
        return new ContinuousDiscretizationSpec(new double[0], new ArrayList<>());
    }

    /**
     * Returns the value of the method used for discretization.
     *
     * @return The method used for discretization.
     */
    public int getMethod() {
        return this.method;
    }

    /**
     * Sets the method used for discretization.
     *
     * @param method The method used for discretization. Possible values are: -
     *               ContinuousDiscretizationSpec.EVENLY_DISTRIBUTED_VALUES (0): for evenly distributed values -
     *               ContinuousDiscretizationSpec.EVENLY_DISTRIBUTED_INTERVALS (1): for evenly distributed intervals -
     *               ContinuousDiscretizationSpec.NONE (2): for no discretization
     * @throws IllegalArgumentException if method is not one of the valid values.
     */
    public void setMethod(int method) {
        if (method != ContinuousDiscretizationSpec.EVENLY_DISTRIBUTED_VALUES && method != ContinuousDiscretizationSpec.EVENLY_DISTRIBUTED_INTERVALS && method != ContinuousDiscretizationSpec.NONE) {
            throw new IllegalArgumentException();
        }
        this.method = method;
    }

    /**
     * Retrieves the list of categories for the discretized data.
     *
     * @return The list of categories.
     */
    public List<String> getCategories() {
        return this.categories;
    }

    /**
     * Retrieves the array of breakpoints used for discretization.
     *
     * @return The array of breakpoints.
     */
    public double[] getBreakpoints() {
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

        if (this.categories == null) {
            throw new NullPointerException();
        }

        if (this.method != ContinuousDiscretizationSpec.EVENLY_DISTRIBUTED_VALUES && this.method != ContinuousDiscretizationSpec.EVENLY_DISTRIBUTED_INTERVALS) {
            this.method = ContinuousDiscretizationSpec.EVENLY_DISTRIBUTED_INTERVALS;
        }
    }
}





