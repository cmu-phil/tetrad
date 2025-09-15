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

package edu.cmu.tetrad.study.gene.tetrad.gene.graph;

import edu.cmu.tetrad.study.gene.tetrad.gene.history.BasicLagGraph;
import edu.cmu.tetrad.study.gene.tetrad.gene.history.LagGraph;
import edu.cmu.tetrad.study.gene.tetrad.gene.history.LaggedFactor;
import edu.cmu.tetrad.util.NamingProtocol;
import edu.cmu.tetrad.util.PointXy;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;

/**
 * Constructs as a (manual) update graph.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class ManualLagGraph implements LagGraph {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The lag graph.
     */
    private final BasicLagGraph lagGraph = new BasicLagGraph();

    //============================CONSTRUCTORS========================//

    /**
     * Using the given parameters, constructs an BasicLagGraph.
     *
     * @param params an LagGraphParams object.
     */
    public ManualLagGraph(ManualLagGraphParams params) {
        addFactors("G", params.getVarsPerInd());
        setMaxLagAllowable(params.getMlag());

        // Add edges one time step back.
        for (Object o : getFactors()) {
            String factor = (String) o;
            LaggedFactor laggedFactor = new LaggedFactor(factor, 1);
            addEdge(factor, laggedFactor);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.graph.ManualLagGraph} object
     */
    public static ManualLagGraph serializableInstance() {
        return new ManualLagGraph(ManualLagGraphParams.serializableInstance());
    }

    //=============================PUBLIC METHODS=======================//

    /**
     * {@inheritDoc}
     */
    public void addEdge(String factor, LaggedFactor laggedFactor)
            throws IllegalArgumentException {
        this.lagGraph.addEdge(factor, laggedFactor);
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
    public void addFactor(String factor) {
        if (!NamingProtocol.isLegalName(factor)) {
            throw new IllegalArgumentException(
                    NamingProtocol.getProtocolDescription());
        }

        this.lagGraph.addFactor(factor);
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
    public SortedSet getParents(String factor) {
        return this.lagGraph.getParents(factor);
    }

    /**
     * {@inheritDoc}
     */
    public void removeEdge(String factor, LaggedFactor laggedFactor) {
        this.lagGraph.removeEdge(factor, laggedFactor);
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
     */
    public void setMaxLagAllowable(int maxLagAllowable) {
        this.lagGraph.setMaxLagAllowable(maxLagAllowable);
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
     * {@inheritDoc}
     */
    public void removeFactor(String factor) {
        this.lagGraph.removeFactor(factor);
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
     * {@inheritDoc}
     */
    public void renameFactor(String oldName, String newName) {
        this.lagGraph.renameFactor(oldName, newName);
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
    public SortedSet getFactors() {
        return this.lagGraph.getFactors();
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return this.lagGraph.toString();
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
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }
}





