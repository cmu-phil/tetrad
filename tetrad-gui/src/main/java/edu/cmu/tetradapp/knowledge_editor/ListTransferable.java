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

package edu.cmu.tetradapp.knowledge_editor;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.List;

/**
 * Tyler was lazy and didn't document this....
 *
 * @author Tyler Gibson
 */
class ListTransferable implements Transferable {

    /**
     * Supported dataflavors--only one.
     */
    private static final DataFlavor[] dataFlavors = {
            new DataFlavor(ListTransferable.class, "String List Selection")};
    /**
     * Constant <code>DATA_FLAVOR</code>
     */
    public static final DataFlavor DATA_FLAVOR = ListTransferable.dataFlavors[0];
    /**
     * The list of graph nodes that constitutes the selection.
     */
    private final List list;


    /**
     * Constructs a new selection with the given list of graph nodes.
     *
     * @param list a {@link java.util.List} object
     */
    public ListTransferable(List list) {
        if (list == null) {
            throw new NullPointerException(
                    "List of list must " + "not be null.");
        }

        this.list = list;
    }

    /**
     * {@inheritDoc}
     */
    public Object getTransferData(DataFlavor flavor)
            throws UnsupportedFlavorException, IOException {
        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }

        return this.list;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return flavor.equals(ListTransferable.dataFlavors[0]);
    }

    /**
     * <p>getTransferDataFlavors.</p>
     *
     * @return an array of DataFlavor objects indicating the flavors the data can be provided in.  The array should be
     * ordered according to preference for providing the data (from most richly descriptive to least descriptive).
     */
    public DataFlavor[] getTransferDataFlavors() {
        return ListTransferable.dataFlavors;
    }
}




