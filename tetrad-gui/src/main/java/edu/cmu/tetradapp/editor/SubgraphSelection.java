///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Node;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.rmi.MarshalledObject;
import java.util.List;

/**
 * Holds a list of graph nodes for cut/paste operations. Note that a deep clone of the graph elements list is made on
 * creation, and once the data is retrieved, it is deleted.
 *
 * @author josephramsey
 */
class SubgraphSelection implements Transferable {

    /**
     * Supported dataflavors--only one.
     */
    private final DataFlavor[] dataFlavors = {
            new DataFlavor(SubgraphSelection.class, "Subgraph Selection")};
    /**
     * The list of graph nodes that constitutes the selection.
     */
    private List graphElements;

    /**
     * Constructs a new selection with the given list of graph nodes.
     *
     * @param graphElements a {@link java.util.List} object
     */
    public SubgraphSelection(List graphElements) {
        if (graphElements == null) {
            throw new NullPointerException(
                    "List of graph elements must " + "not be null.");
        }

        for (Object graphElement : graphElements) {
            if (!(graphElement instanceof Node ||
                  graphElement instanceof Edge)) {
                throw new IllegalArgumentException("Model node list contains " +
                                                   "an object that is not a Node or an Edge: " +
                                                   graphElement);
            }
        }

        Object result;
        try {
            result = new MarshalledObject(graphElements).get();
        } catch (Exception e1) {
            e1.printStackTrace();
            throw new IllegalStateException("Could not clone.");
        }
        this.graphElements = (List) result;
    }

    /**
     * {@inheritDoc}
     */
    public Object getTransferData(DataFlavor flavor)
            throws UnsupportedFlavorException, IOException {
        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }

        List returnList = this.graphElements;
        this.graphElements = null;
        return returnList;
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






