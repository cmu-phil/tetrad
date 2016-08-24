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

package edu.cmu.tetrad.gene.tetrad.gene.history;

import edu.cmu.tetrad.util.PointXy;
import edu.cmu.tetrad.util.TetradSerializable;

import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;

/**
 * @author Joseph Ramsey
 */
public interface LagGraph extends TetradSerializable {

    /**
     * Adds an edge to the given factor at lag 0 from the specified lagged
     * factor.
     *
     * @param factor       a factor name in the graph.
     * @param laggedFactor a lagged factor with factor name in the graph and lag
     *                     >=1.
     * @throws java.lang.IllegalArgumentException
     *          if the edge cannot be added.
     */
    void addEdge(String factor, LaggedFactor laggedFactor)
            throws IllegalArgumentException;

    /**
     * Removes all edges from the graph.
     */
    void clearEdges();

    /**
     * Adds a factor to the graph. If the factor is already in the graph, no
     * action is taken.
     *
     * @param factor the factor (name).
     */
    void addFactor(String factor);

    /**
     * Determines whether the given factor exists in the graph.
     *
     * @param factor the given factor.
     * @return true if the given factor is in the graph, false if not.
     */
    boolean existsFactor(String factor);

    /**
     * Determines whether the edge to 'factor' at time lag 0 from 'laggedFactor'
     * exists in the graph.
     *
     * @param factor       the "to" factor.
     * @param laggedFactor the "from" factor at the given lag.
     * @return true if this edge exists in the graph, false if not.
     */
    boolean existsEdge(String factor, LaggedFactor laggedFactor);

    /**
     * Returns the lagged factors which are into the given factor.
     *
     * @param factor the "into" factor.
     * @return the set of lagged factors into this factor.
     */
    SortedSet<LaggedFactor> getParents(String factor);

    /**
     * Removes the lagged factor from the list of lagged factors associated with
     * the given factor.
     *
     * @param factor       the "into" factor.
     * @param laggedFactor the "outof" lagged factor.
     */
    void removeEdge(String factor, LaggedFactor laggedFactor);

    /**
     * Gets the maximum allowable lag. Edges may not be added with lags greated
     * than this.
     */
    int getMaxLagAllowable();

    /**
     * Sets the maximum allowable lag. Edges may not be added with lags greater
     * than this. This value must be >= the getModel value of getMaxLag().
     */
    void setMaxLagAllowable(int maxLagAllowable);

    /**
     * Maximum lag needed to fully represent the graph, which is the largest lag
     * of any of the lagged factors stored in the graph.
     *
     * @return the maximum lag in the mdoel.
     */
    int getMaxLag();

    /**
     * Removes a factor from the graph.
     *
     * @param factor the name of the factor.
     */
    void removeFactor(String factor);

    /**
     * Returns (a copy of) the sorted map from factors to lagged factors which
     * internally encodes the update graph. The purpose of this method is to
     * allow update functions to store a copy of their own connectivity in a way
     * which does not depend on the original update graph staying the way it is.
     * The way to do this is to use this method to get a copy of the
     * connectivity to store internally in the update function. Because it is a
     * SortedMap, factors and lagged factors can be expected to stay in the same
     * order. </p> <p><i>Note:</i> This strategy is not implemented yet!  Please
     * remove this note when it is implemented.  The idea is to get rid of the
     * classes IndexedParent and Connectivity and use this sorted map to replace
     * them.</p>
     *
     * @return this sorted map.
     */
    SortedMap<String, SortedSet<LaggedFactor>> getConnectivity();

    /**
     * Renames a factor, changing all occurances of the old name to the new one
     */
    void renameFactor(String oldName, String newName);

    /**
     * Returns the number of factors represented in the graph.
     *
     * @return this number.
     */
    int getNumFactors();

    /**
     * Returns a SortedSet of the factors in this graph.
     *
     * @return this set.
     */
    SortedSet<String> getFactors();

    /**
     * Returns a string representation of the graph, indicating for each factor
     * which lagged factors map into it.
     *
     * @return this string.
     */
    String toString();

    void addFactors(String base, int numFactors);

    void setLocation(String factor, PointXy point);

    PointXy getLocation(String factor);

    Map getLocations();
}





