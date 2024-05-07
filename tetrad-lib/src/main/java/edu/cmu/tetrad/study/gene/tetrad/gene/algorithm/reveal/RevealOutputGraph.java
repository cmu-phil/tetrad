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

package edu.cmu.tetrad.study.gene.tetrad.gene.algorithm.reveal;

import edu.cmu.tetrad.study.gene.tetrad.gene.algorithm.util.OutputGraph;

/**
 * <p>RevealOutputGraph class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class RevealOutputGraph implements OutputGraph {
    private final int ngenes;
    private final int[][] parents;
    private final int[][] lags;
    String[] names;
    String graphName;

    /**
     * <p>Constructor for RevealOutputGraph.</p>
     *
     * @param ngenes    a int
     * @param parents   an array of {@link int} objects
     * @param lags      an array of {@link int} objects
     * @param names     an array of {@link java.lang.String} objects
     * @param graphName a {@link java.lang.String} object
     */
    public RevealOutputGraph(int ngenes, int[][] parents, int[][] lags,
                             String[] names, String graphName) {
        this.ngenes = ngenes;
        this.parents = parents;
        this.lags = lags;
        this.names = names;
        this.graphName = graphName;
    }

    /**
     * <p>getSize.</p>
     *
     * @return a int
     */
    public int getSize() {
        return this.ngenes;
    }

    /**
     * {@inheritDoc}
     */
    public int[] getParents(int index) {
        return this.parents[index];
    }

    /**
     * {@inheritDoc}
     */
    public int[] getLags(int index) {
        return this.lags[index];
    }

    /**
     * {@inheritDoc}
     */
    public String getNodeName(int index) {
        return this.names[index];
    }

    /**
     * <p>Getter for the field <code>graphName</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getGraphName() {
        return this.graphName;
    }
}





