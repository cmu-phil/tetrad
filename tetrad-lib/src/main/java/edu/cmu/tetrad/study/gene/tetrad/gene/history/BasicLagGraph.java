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

package edu.cmu.tetrad.study.gene.tetrad.gene.history;

import edu.cmu.tetrad.util.NamingProtocol;
import edu.cmu.tetrad.util.PointXy;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.text.NumberFormat;
import java.util.*;

/**
 * <P>Stores a time series in the "update" (rather than, say, the "repeated")
 * form--that is, for a given set of factors (the word "factor" is being used here to avoid ambiguity), only lags behind
 * the getModel time step are recorded temporally, with causal edges extending from lagged factors with lags &gt;= 1 to
 * factors in the getModel time step (lag = 0) only. This "update graph" is viewed as a repeating structure; for each
 * time step, the influences from previous time steps of other factors are as the update graph specifies.
 * <P>Factor names in this model are distinct String's. The form of these
 * String's is left entirely up to the code using this package. Lags are int's &gt;= 0, although of course lagged
 * factors used for edge specifications must have lags &gt;= 1.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class BasicLagGraph implements LagGraph {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * For each factor, stores the set of lagged factors which map into it. (Maps Strings to SortedSets of Strings.)
     * This is the main data structure for the graph.
     */
    private final SortedMap<String, SortedSet<LaggedFactor>> connectivity;

    /**
     * The maximum allowable lag. edges may not be added with lags greater than this. The value must be &gt;= 1.
     */
    private int maxLagAllowable = Integer.MAX_VALUE;

    /**
     * Stores the locations of the points for a directed graph.
     */
    private Map<String, PointXy> locations;

    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs an empty update graph--that is, a graph with no factors (and therefore no edges).
     */
    public BasicLagGraph() {
        this.connectivity = new TreeMap<>();
    }

    /**
     * Constructs a copy of the given lag graph.
     *
     * @param lagGraph the lag graph to copy.
     */
    public BasicLagGraph(LagGraph lagGraph) {
        this.connectivity = lagGraph.getConnectivity();
        this.maxLagAllowable = lagGraph.getMaxLagAllowable();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a simple exemplar of this class to test serialization.
     */
    public static BasicLagGraph serializableInstance() {
        BasicLagGraph lagGraph = new BasicLagGraph();
        lagGraph.addFactor("X");
        lagGraph.setMaxLagAllowable(2);
        return lagGraph;
    }

    //===========================PUBLIC METHODS===========================//

    /**
     * {@inheritDoc}
     * <p>
     * Adds an edge to the given factor at lag 0 from the specified lagged factor.
     */
    public void addEdge(String factor, LaggedFactor laggedFactor)
            throws IllegalArgumentException {

        int lag = laggedFactor.getLag();

        if (lag < 1 || lag > this.maxLagAllowable) {
            throw new IllegalArgumentException(
                    "Illegal lag specified: " + laggedFactor);
        }

        TreeSet<LaggedFactor> list =
                (TreeSet<LaggedFactor>) this.connectivity.get(factor);

        if (list != null) {
            list.add(laggedFactor);
        } else {
            throw new IllegalArgumentException("Either factor not in graph (" +
                                               factor +
                                               ") or lagged factor not in graph or not into factor (" +
                                               laggedFactor + ").");
        }
    }

    /**
     * Removes all edges from the graph.
     */
    public void clearEdges() {
        for (String s : this.connectivity.keySet()) {
            SortedSet<LaggedFactor> set = this.connectivity.get(s);
            set.clear();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Adds a factor to the graph. If the factor is already in the graph, no action is taken.
     */
    public void addFactor(String factor) {
        if (!NamingProtocol.isLegalName(factor)) {
            throw new IllegalArgumentException(
                    NamingProtocol.getProtocolDescription());
        }

        if (!this.connectivity.containsKey(factor)) {
            SortedSet<LaggedFactor> laggedFactors = new TreeSet<>();
            this.connectivity.put(factor, laggedFactors);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Determines whether the given factor exists in the graph.
     */
    public boolean existsFactor(String factor) {
        return this.connectivity.containsKey(factor);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Determines whether the edge to 'factor' at time lag 0 from 'laggedFactor' exists in the graph.
     */
    public boolean existsEdge(String factor, LaggedFactor laggedFactor) {

        if (laggedFactor.getLag() < 1) {
            throw new IllegalArgumentException(
                    "Illegal lag specified: " + laggedFactor);
        }

        TreeSet<LaggedFactor> list = (TreeSet<LaggedFactor>) this.connectivity.get(factor);

        if (list != null) {
            return list.contains(laggedFactor);
        } else {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the lagged factors which are into the given factor.
     */
    public SortedSet<LaggedFactor> getParents(String factor) {
        return this.connectivity.get(factor);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Removes the lagged factor from the list of lagged factors associated with the given factor.
     */
    public void removeEdge(String factor, LaggedFactor laggedFactor) {

        TreeSet<LaggedFactor> list = (TreeSet<LaggedFactor>) this.connectivity.get(factor);

        if (list != null) {
            list.remove(laggedFactor);
        } else {
            throw new IllegalArgumentException("Either factor not in graph (" +
                                               factor + ") or lagged factor not in graph or not into " +
                                               "factor (" + laggedFactor + ").");
        }
    }

    /**
     * Gets the maximum allowable lag. Edges may not be added with lags greated than this.
     *
     * @return a int
     */
    public int getMaxLagAllowable() {
        return this.maxLagAllowable;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the maximum allowable lag. Edges may not be added with lags greater than this. This value must be &gt;= the
     * getModel value of getMaxLag().
     */
    public void setMaxLagAllowable(int maxLagAllowable) {
        if (maxLagAllowable >= getMaxLag()) {
            this.maxLagAllowable = maxLagAllowable;
        }
    }

    /**
     * Maximum lag needed to fully represent the graph, which is the largest lag of any of the lagged factors stored in
     * the graph.
     *
     * @return the maximum lag in the mdoel.
     */
    public int getMaxLag() {
        int max = 0;

        for (String factor : this.connectivity.keySet()) {
            for (LaggedFactor laggedFactor : this.connectivity.get(factor)) {
                int lag = laggedFactor.getLag();

                if (lag > max) {
                    max = lag;
                }
            }
        }

        return max;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Removes a factor from the graph.
     */
    public void removeFactor(String factor) {

        Object o = this.connectivity.remove(factor);

        if (o == null) {
            throw new IllegalArgumentException(
                    "Factor not in graph: " + factor);
        }
    }

    /**
     * Returns (a copy of) the sorted map from factors to lagged factors which internally encodes the update graph. The
     * purpose of this method is to allow update functions to store a copy of their own connectivity in a way which does
     * not depend on the original update graph staying the way it is. The way to do this is to use this method to get a
     * copy of the connectivity to store internally in the update function. Because it is a SortedMap, factors and
     * lagged factors can be expected to stay in the same order. <p><i>Note:</i> This strategy is not implemented yet!
     * Please remove this note when it is implemented.  The idea is to get rid of the classes IndexedParent and
     * Connectivity and use this sorted map to replace them.&gt; 0
     *
     * @return this sorted map.
     */
    public SortedMap<String, SortedSet<LaggedFactor>> getConnectivity() {
        return new TreeMap<>(this.connectivity);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Renames a factor, changing all occurances of the old name to the new one
     */
    public void renameFactor(String oldName, String newName) {
        // two steps- rename the factor in the connectivity, then search
        // throughout the parents in the connectivity and rename the
        // LaggedFactors

        if (existsFactor(newName)) {
            throw new IllegalArgumentException(
                    "A factor named " + newName + " already exists in graph");
        }

        // step one
        SortedSet<LaggedFactor> transfer = this.connectivity.remove(oldName);
        this.connectivity.put(newName, transfer);

        // step two
        for (SortedSet<LaggedFactor> parents : this.connectivity.values()) {
            for (LaggedFactor itm : parents) {
                if (itm.getFactor().equals(oldName)) {
                    itm.setFactor(newName);
                }
            }
        }
    }

    /**
     * Returns the number of factors represented in the graph.
     *
     * @return this number.
     */
    public int getNumFactors() {
        return this.connectivity.size();
    }

    /**
     * Returns a SortedSet of the factors in this graph.
     *
     * @return this set.
     */
    public SortedSet<String> getFactors() {
        return new TreeSet<>(this.connectivity.keySet());
    }

    /**
     * Returns a string representation of the graph, indicating for each factor which lagged factors map into it.
     *
     * @return this string.
     */
    public String toString() {

        StringBuilder buf = new StringBuilder();

        buf.append("\nUpdate graph:\n");

        Collection<String> factors = this.connectivity.keySet();

        for (String factor : factors) {
            buf.append("\n");
            buf.append(factor);
            buf.append("\t<-- ");

            Collection<LaggedFactor> edges = this.connectivity.get(factor);

            for (LaggedFactor edge : edges) {
                buf.append("\t");
                buf.append(edge);
            }
        }

        buf.append("\n");

        return buf.toString();
    }

    /**
     * {@inheritDoc}
     */
    public void addFactors(String base, int numFactors) {

        NumberFormat nf = NumberFormat.getInstance();

        // Find the max number of digits needed to count the
        // variables. The variables need to be of the form v001,
        // v002,...,v999, so they can sort alphabetically.
        int numDigits = 0;
        int m = numFactors;

        while (m > 0) {
            m /= 10;
            numDigits++;
        }

        nf.setMinimumIntegerDigits(numDigits);
        nf.setGroupingUsed(false);

        for (int i = 0; i < numFactors; i++) {

            // Pad the variable index so that the variables
            // alphabetize correctly.
            String factor = base + nf.format(i + 1);

            // add the factor to the model.
            addFactor(factor);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setLocation(String factor, PointXy point) {
        getLocations().put(factor, point);
    }

    /**
     * {@inheritDoc}
     */
    public PointXy getLocation(String factor) {
        return getLocations().get(factor);
    }

    /**
     * <p>Getter for the field <code>locations</code>.</p>
     *
     * @return a {@link java.util.Map} object
     */
    public Map<String, PointXy> getLocations() {

        // This is to accomodate folks using a previous version.
        if (this.locations == null) {
            this.locations = new HashMap<>();
        }
        return this.locations;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s an {@link java.io.ObjectInputStream} object
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.connectivity == null) {
            throw new NullPointerException();
        }

        if (this.maxLagAllowable < 1) {
            throw new IllegalStateException();
        }
    }
}





