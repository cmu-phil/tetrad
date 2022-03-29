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

package edu.cmu.tetrad.gene.tetrad.gene.graph;

import edu.cmu.tetrad.gene.tetrad.gene.history.BasicLagGraph;
import edu.cmu.tetrad.gene.tetrad.gene.history.LagGraph;
import edu.cmu.tetrad.gene.tetrad.gene.history.LaggedFactor;
import edu.cmu.tetrad.util.NamingProtocol;
import edu.cmu.tetrad.util.PointXy;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;

/**
 * Constructs as a (manual) update graph.
 */
public final class ManualLagGraph implements LagGraph {
    static final long serialVersionUID = 23L;

    /**
     * @serial
     */
    private final BasicLagGraph lagGraph = new BasicLagGraph();

    //============================CONSTRUCTORS========================//

    /**
     * Using the given parameters, constructs an BasicLagGraph.
     *
     * @param params an LagGraphParams object.
     */
    public ManualLagGraph(final ManualLagGraphParams params) {
        addFactors("G", params.getVarsPerInd());
        setMaxLagAllowable(params.getMlag());

        // Add edges one time step back.
        for (final Iterator it = getFactors().iterator(); it.hasNext(); ) {
            final String factor = (String) it.next();
            final LaggedFactor laggedFactor = new LaggedFactor(factor, 1);
            addEdge(factor, laggedFactor);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see edu.cmu.tetradapp.util.TetradSerializableUtils
     */
    public static ManualLagGraph serializableInstance() {
        return new ManualLagGraph(ManualLagGraphParams.serializableInstance());
    }

    //=============================PUBLIC METHODS=======================//

    public void addEdge(final String factor, final LaggedFactor laggedFactor)
            throws IllegalArgumentException {
        this.lagGraph.addEdge(factor, laggedFactor);
    }

    public void clearEdges() {
        this.lagGraph.clearEdges();
    }

    public void addFactor(final String factor) {
        if (!NamingProtocol.isLegalName(factor)) {
            throw new IllegalArgumentException(
                    NamingProtocol.getProtocolDescription());
        }

        this.lagGraph.addFactor(factor);
    }

    public boolean existsFactor(final String factor) {
        return this.lagGraph.existsFactor(factor);
    }

    public boolean existsEdge(final String factor, final LaggedFactor laggedFactor) {
        return this.lagGraph.existsEdge(factor, laggedFactor);
    }

    public SortedSet getParents(final String factor) {
        return this.lagGraph.getParents(factor);
    }

    public void removeEdge(final String factor, final LaggedFactor laggedFactor) {
        this.lagGraph.removeEdge(factor, laggedFactor);
    }

    public int getMaxLagAllowable() {
        return this.lagGraph.getMaxLagAllowable();
    }

    public void setMaxLagAllowable(final int maxLagAllowable) {
        this.lagGraph.setMaxLagAllowable(maxLagAllowable);
    }

    public int getMaxLag() {
        return this.lagGraph.getMaxLag();
    }

    public void removeFactor(final String factor) {
        this.lagGraph.removeFactor(factor);
    }

    public SortedMap getConnectivity() {
        return this.lagGraph.getConnectivity();
    }

    public void renameFactor(final String oldName, final String newName) {
        this.lagGraph.renameFactor(oldName, newName);
    }

    public int getNumFactors() {
        return this.lagGraph.getNumFactors();
    }

    public SortedSet getFactors() {
        return this.lagGraph.getFactors();
    }

    public String toString() {
        return this.lagGraph.toString();
    }

    public void addFactors(final String base, final int numFactors) {
        this.lagGraph.addFactors(base, numFactors);
    }

    public void setLocation(final String factor, final PointXy point) {
        this.lagGraph.setLocation(factor, point);
    }

    public PointXy getLocation(final String factor) {
        return this.lagGraph.getLocation(factor);
    }

    public Map getLocations() {
        return this.lagGraph.getLocations();
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(final ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.lagGraph == null) {
            throw new NullPointerException();
        }
    }
}





