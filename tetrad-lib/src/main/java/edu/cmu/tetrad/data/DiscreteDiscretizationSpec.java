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

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
public final class DiscreteDiscretizationSpec implements TetradSerializable, DiscretizationSpec {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Discrete remapping of categories, for discrete data.
     */
    private final int[] remap;

    /**
     * Categories, for discrete data.
     */
    private final List<String> categories;

    /**
     * <p>Constructor for DiscreteDiscretizationSpec.</p>
     *
     * @param remap      an array of {@link int} objects
     * @param categories a {@link java.util.List} object
     */
    public DiscreteDiscretizationSpec(int[] remap, List<String> categories) {
        if (remap == null) {
            throw new NullPointerException();
        }

        if (categories == null) {
            throw new NullPointerException();
        }

        this.remap = remap;
        this.categories = categories;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.data.DiscreteDiscretizationSpec} object
     */
    public static DiscreteDiscretizationSpec serializableInstance() {
        return new DiscreteDiscretizationSpec(new int[0],
                new ArrayList<>());
    }

    /**
     * <p>Getter for the field <code>categories</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getCategories() {
        return this.categories;
    }

    /**
     * <p>Getter for the field <code>remap</code>.</p>
     *
     * @return an array of {@link int} objects
     */
    public int[] getRemap() {
        return this.remap;
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to serialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to deserialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }
}





