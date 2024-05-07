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

package edu.cmu.tetrad.study.gene.tetrad.gene.graph;

import edu.cmu.tetrad.study.gene.tetrad.gene.history.BasicLagGraph;
import edu.cmu.tetrad.study.gene.tetrad.gene.history.LagGraph;
import edu.cmu.tetrad.study.gene.tetrad.gene.history.LaggedEdge;
import edu.cmu.tetrad.study.gene.tetrad.gene.history.LaggedFactor;
import edu.cmu.tetrad.util.NamingProtocol;
import edu.cmu.tetrad.util.PointXy;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;

/**
 * Adds Javabean property change events so that it can be used in a MVC type architecture. Does not throw any
 * exceptions, but it will only fire PropertyChange events if the function successfully completed This version of a lag
 * graph also differs from a standard BasicLagGraph in that it allows edges with lags &gt; maxLagAllowable to be added.
 * In such a case, maxLagAllowable will be increased
 *
 * @author Gregory Li
 * @version $Id: $Id
 */
public class ActiveLagGraph implements LagGraph {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Underlying graph representing the update graph.
     *
     * @serial
     */
    private final LagGraph lagGraph = new BasicLagGraph();

    /**
     * Fires property changes.
     */
    private transient PropertyChangeSupport propertyChangeManager;

    //=============================CONSTRUCTOR===========================//

    /**
     * Creates new ActiveLagGraph
     */
    public ActiveLagGraph() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a simple exemplar of this class to test serialization.
     */
    public static ActiveLagGraph serializableInstance() {
        return new ActiveLagGraph();
    }

    //============================PUBLIC METHODS=========================//

    /**
     * Registers a listener to events concerning the lag graph.
     *
     * @param l the listener
     */
    public void addPropertyChangeListener(PropertyChangeListener l) {
        getPropertyChangeManager().addPropertyChangeListener(l);
    }

    /**
     * Unregisters a listener for events concerning the lag graph.
     *
     * @param l the listener
     */
    public void removePropertyChangeListener(PropertyChangeListener l) {
        getPropertyChangeManager().removePropertyChangeListener(l);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Attempts to add an edge to the graph. If the lag of the edge is greater than maxLagAllowable, maxLagAllowable
     * will automatically be increased so that the edge can be added. Will throw a propertyChange event of (null,
     * (LaggedEdge) newEdge)
     */
    public void addEdge(String factor, LaggedFactor laggedFactor) {
        // super class does not care if edge is already in the graph, therefore
        // we need to check manually
        if (!existsEdge(factor, laggedFactor)) {
            try {
                if (laggedFactor.getLag() > getMaxLagAllowable()) {
                    setMaxLagAllowable(laggedFactor.getLag());
                }

                this.lagGraph.addEdge(factor, laggedFactor);
                getPropertyChangeManager().firePropertyChange("edgeAdded", null, new LaggedEdge(factor, laggedFactor));
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Attempts to add a factor to the graph. Will throw a propertyChange event of (null, (String) factor).
     */
    public void addFactor(String factor) {
        if (!NamingProtocol.isLegalName(factor)) {
            throw new IllegalArgumentException(NamingProtocol.getProtocolDescription());
        }

        // no exception is thrown if the factor is already in the graph
        if (!existsFactor(factor)) {
            try {
                this.lagGraph.addFactor(factor);
                getPropertyChangeManager().firePropertyChange("nodeAdded", null, factor);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Attempts to remove an edge from the graph. Will throw a propertyChange event of ((LaggedEdge) edge_removed,
     * null).
     */
    public void removeEdge(String factor, LaggedFactor laggedFactor) {
        if (existsEdge(factor, laggedFactor)) {
            try {
                this.lagGraph.removeEdge(factor, laggedFactor);
                getPropertyChangeManager().firePropertyChange("edgeRemoved", new LaggedEdge(factor, laggedFactor), null);
            } catch (Exception e) {
                // Igore.
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Attempts to remove a factor from the graph. Will also search through and remove any edges that involve this edge.
     * Will throw a propertyChange event of ((String) factor_removed, null).
     */
    public void removeFactor(String factor) {
        try {
            this.lagGraph.removeFactor(factor);
            getPropertyChangeManager().firePropertyChange("nodeRemoved", factor, null);

            // search through and find edges which were sourced by this factor
            // and remove them
            ArrayList<LaggedFactor> toDelete = new ArrayList<>();
            SortedSet<String> factors = getFactors();
            // have to search through all destination factors to find edges to remove
            for (String value : factors) {
                SortedSet<LaggedFactor> parents = this.lagGraph.getParents(value);

                // find edges sourced by factor
                for (LaggedFactor parent : parents) {
                    if (parent.getFactor().equals(factor)) {
                        toDelete.add(parent);
                    }
                }

                // remove those edges
                for (Object o : toDelete) {
                    removeEdge(value, (LaggedFactor) o);
                }
                toDelete.clear();
            }
        } catch (Exception e) {
            // Ignore.
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Attempts to rename a factor. Will throw a propertyChange event of ((String) oldName, (String) newName).
     */
    public void renameFactor(String oldName, String newName) {
        try {
            this.lagGraph.renameFactor(oldName, newName);
            getPropertyChangeManager().firePropertyChange("factorRenamed", oldName, newName);
        } catch (IllegalArgumentException e) {
            // ignore
        }
    }

    private PropertyChangeSupport getPropertyChangeManager() {
        if (this.propertyChangeManager == null) {
            this.propertyChangeManager = new PropertyChangeSupport(this);
        }
        return this.propertyChangeManager;
    }

    /**
     * <p>clearEdges.</p>
     */
    public void clearEdges() {
        this.lagGraph.clearEdges();
    }

    /**
     * {@inheritDoc}
     */
    public boolean existsFactor(String factor) {
        return this.lagGraph.existsFactor(factor);
    }

    /**
     * {@inheritDoc}
     */
    public boolean existsEdge(String factor, LaggedFactor laggedFactor) {
        return this.lagGraph.existsEdge(factor, laggedFactor);
    }

    /**
     * {@inheritDoc}
     */
    public SortedSet<LaggedFactor> getParents(String factor) {
        return this.lagGraph.getParents(factor);
    }

    /**
     * <p>getMaxLagAllowable.</p>
     *
     * @return a int
     */
    public int getMaxLagAllowable() {
        return this.lagGraph.getMaxLagAllowable();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Attemps to set the maximum allowable lag of an edge in the graph. This really is not necessary to use publicly
     * anymore since the addEdge function will now automatically increase the MaxAllowableLag of the graph if an edge's
     * lag is greater than MaxAllowableLag. Will throw a propertyChange event of (null, (Integer) newMaxLagAllowable).
     */
    public void setMaxLagAllowable(int maxLagAllowable) {
        if (maxLagAllowable >= getMaxLag()) {
            this.lagGraph.setMaxLagAllowable(maxLagAllowable);
            this.lagGraph.setMaxLagAllowable(maxLagAllowable);
            getPropertyChangeManager().firePropertyChange("maxLagAllowable", null, getMaxLagAllowable());
        }
    }

    /**
     * <p>getMaxLag.</p>
     *
     * @return a int
     */
    public int getMaxLag() {
        return this.lagGraph.getMaxLag();
    }

    /**
     * <p>getConnectivity.</p>
     *
     * @return a {@link java.util.SortedMap} object
     */
    public SortedMap getConnectivity() {
        return this.lagGraph.getConnectivity();
    }

    /**
     * <p>getNumFactors.</p>
     *
     * @return a int
     */
    public int getNumFactors() {
        return this.lagGraph.getNumFactors();
    }

    /**
     * <p>getFactors.</p>
     *
     * @return a {@link java.util.SortedSet} object
     */
    public SortedSet<String> getFactors() {
        return this.lagGraph.getFactors();
    }

    /**
     * {@inheritDoc}
     */
    public void addFactors(String base, int numFactors) {
        this.lagGraph.addFactors(base, numFactors);
    }

    /**
     * {@inheritDoc}
     */
    public void setLocation(String factor, PointXy point) {
        this.lagGraph.setLocation(factor, point);
    }

    /**
     * {@inheritDoc}
     */
    public PointXy getLocation(String factor) {
        return this.lagGraph.getLocation(factor);
    }

    /**
     * <p>getLocations.</p>
     *
     * @return a {@link java.util.Map} object
     */
    public Map getLocations() {
        return this.lagGraph.getLocations();
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
    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }
}





