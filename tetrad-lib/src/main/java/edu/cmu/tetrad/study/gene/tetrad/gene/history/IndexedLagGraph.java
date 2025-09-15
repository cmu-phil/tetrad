/// ////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetrad.study.gene.tetrad.gene.history;

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

/**
 * <p>Stores a "shapshot" of the indexedLagGraph of a lag graph, using indices
 * rather than Strings to refer to factors. Since lag graphs are dynamic, they can't do this directly, as the indices
 * might change from one time to the next. However, for certain uses of lag graphs, the graph itself may be assumed to
 * be static, so this optimization is useful.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class IndexedLagGraph implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The factors in the graph, in the order that they are used.
     */
    private final List<String> factors;

    /**
     * The graph that this "snapshot" of indexedLagGraph was taken from.
     */
    private final IndexedParent[][] parents;

    //===============================CONSTRUCTORS=========================//

    /**
     * Constructs an indexed lag graph for the getModel state of the given lag graph, including all edges.
     *
     * @param lagGraph a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.LagGraph} object
     */
    public IndexedLagGraph(LagGraph lagGraph) {
        this(lagGraph, false);
    }

    /**
     * Constructs an indexed lag graph for the getModel state of the given lag graph.
     *
     * @param excludeSelfOneBack excludes from the lag graph any edge from a gene one time step back to the same gene in
     *                           the getModel time step.
     * @param lagGraph           a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.LagGraph} object
     */
    public IndexedLagGraph(LagGraph lagGraph, boolean excludeSelfOneBack) {
        if (lagGraph == null) {
            throw new NullPointerException("Lag graph must not be null.");
        }

        // Construct factor and parent lists in fixed order.
        this.factors = new ArrayList<>(lagGraph.getFactors());
        this.parents = new IndexedParent[this.factors.size()][];

        for (int i = 0; i < this.factors.size(); i++) {
            String factor = this.factors.get(i);
            SortedSet<LaggedFactor> factorParents = lagGraph.getParents(factor);
            List<IndexedParent> list = new ArrayList<>();

            for (LaggedFactor factorParent1 : factorParents) {
                int index = this.factors.indexOf(factorParent1.getFactor());
                int lag = factorParent1.getLag();

                if (excludeSelfOneBack && index == i && lag == 1) {
                    continue;
                }

                IndexedParent parent = new IndexedParent(index, lag);
                list.add(parent);
            }

            IndexedParent[] _parents = new IndexedParent[list.size()];

            for (int i2 = 0; i2 < list.size(); i2++) {
                _parents[i2] = list.get(i2);
            }

            this.parents[i] = _parents;
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.IndexedLagGraph} object
     */
    public static IndexedLagGraph serializableInstance() {
        return new IndexedLagGraph(BasicLagGraph.serializableInstance());
    }

    //===============================PUBLIC METHODS======================//

    /**
     * Returns the number of factors.
     *
     * @return a int
     */
    public int getNumFactors() {
        return this.factors.size();
    }

    /**
     * Returns the (string name of) the factor at the given index.
     *
     * @param factor a int
     * @return a {@link java.lang.String} object
     */
    public String getFactor(int factor) {
        return this.factors.get(factor);
    }

    /**
     * Returns the index of the given String factor.
     *
     * @param factor a {@link java.lang.String} object
     * @return a int
     */
    public int getIndex(String factor) {
        return this.factors.indexOf(factor);
    }

    /**
     * Returns the index of the given parent for the given factor.
     *
     * @param factor a {@link java.lang.String} object
     * @param parent a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.LaggedFactor} object
     * @return a int
     */
    public int getIndex(String factor, LaggedFactor parent) {
        int factorIndex = this.factors.indexOf(factor);
        int parentIndex = this.factors.indexOf(parent.getFactor());
        IndexedParent indexedParent =
                new IndexedParent(parentIndex, parent.getLag());
        return getIndex(factorIndex, indexedParent);
    }

    /**
     * Returns the index of the parent of the given factor that is equal to the given IndexedParent, or -1 if the given
     * IndexedParent is not equal to any parent.
     *
     * @param factor a int
     * @param parent a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.IndexedParent} object
     * @return a int
     */
    public int getIndex(int factor, IndexedParent parent) {
        for (int i = 0; i < this.parents[factor].length; i++) {
            if (parent.equals(this.parents[factor][i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the number of parents of the given factor. Each parent is a factor at a given lag.
     *
     * @param factor a int
     * @return a int
     */
    public int getNumParents(int factor) {
        return this.parents[factor].length;
    }

    /**
     * Returns the given parent as an IndexedParent.
     *
     * @param factor a int
     * @param parent a int
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.IndexedParent} object
     */
    public IndexedParent getParent(int factor, int parent) {
        return this.parents[factor][parent];
    }

    /**
     * Returns a string representation of the graph, indicating for each factor which lagged factors map into it.
     *
     * @return this string.
     */
    public String toString() {

        StringBuilder buf = new StringBuilder();

        buf.append("\nIndexedLagGraph:\n");

        for (int i = 0; i < getNumFactors(); i++) {
            String factor = getFactor(i);

            buf.append("\n");
            buf.append(factor);
            buf.append("\t<-- ");

            for (int j = 0; j < getNumParents(i); j++) {
                IndexedParent parent = getParent(i, j);
                buf.append("\t");
                buf.append(getFactor(parent.getIndex()));
                buf.append(":");
                buf.append(parent.getLag());
            }
        }

        buf.append("\n");

        return buf.toString();
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
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

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
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






