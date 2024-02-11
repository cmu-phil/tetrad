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

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.graph.Graph;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.rmi.MarshalledObject;

/**
 * Holds a graph for purposes of layout out another graph later on.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LayoutSelection implements Transferable {

    /**
     * The list of session nodes that constitutes the selection.
     */
    private final Graph layoutGraph;

    /**
     * Supported dataflavors--only one.
     */
    private final DataFlavor[] dataFlavors =
            {new DataFlavor(LayoutSelection.class, "Layout")};

    /**
     * Constructs a new selection with the given list of session nodes.
     *
     * @param layoutGraph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public LayoutSelection(Graph layoutGraph) {
        if (layoutGraph == null) {
            throw new NullPointerException("Layout graph must not be null.");
        }

        Object result;
        try {
            result = new MarshalledObject(layoutGraph).get();
        } catch (Exception e1) {
            e1.printStackTrace();
            throw new IllegalStateException("Could not clone.");
        }
        this.layoutGraph = (Graph) result;
    }

    /**
     * {@inheritDoc}
     */
    public Object getTransferData(DataFlavor flavor)
            throws UnsupportedFlavorException, IOException {
        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }

        return this.layoutGraph;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return flavor.equals(getTransferDataFlavors()[0]);
    }

    /**
     * <p>getTransferDataFlavors.</p>
     *
     * @return an array of DataFlavor objects indicating the flavors the data can be provided in.  The array should be
     * ordered according to preference for providing the data (from most richly descriptive to least descriptive).
     */
    public DataFlavor[] getTransferDataFlavors() {
        return this.dataFlavors;
    }
}





