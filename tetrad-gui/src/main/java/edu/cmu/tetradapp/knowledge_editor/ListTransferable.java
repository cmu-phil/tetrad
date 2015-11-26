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
     * The list of graph nodes that constitutes the selection.
     */
    private List list;

    /**
     * Supported dataflavors--only one.
     */
    private final static DataFlavor[] dataFlavors = new DataFlavor[]{
            new DataFlavor(ListTransferable.class, "String List Selection")};


    public final static DataFlavor DATA_FLAVOR = dataFlavors[0];


    /**
     * Constructs a new selection with the given list of graph nodes.
     */
    public ListTransferable(List list) {
        if (list == null) {
            throw new NullPointerException(
                    "List of list must " + "not be null.");
        }

        this.list = list;
    }

    /**
     * @return an object which represents the data to be transferred.  The
     * class of the object returned is defined by the representation class
     * of the flavor.
     *
     * @param flavor the requested flavor for the data
     * @throws java.io.IOException if the data is no longer available
     *                             in the requested flavor.
     * @throws java.awt.datatransfer.UnsupportedFlavorException
     *                             if the requested data flavor is
     *                             not supported.
     * @see DataFlavor#getRepresentationClass
     */
    public Object getTransferData(DataFlavor flavor)
            throws UnsupportedFlavorException, IOException {
        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }

        return list;
    }

    /**
     * @return whether or not the specified data flavor is supported for
     * this object.
     *
     * @param flavor the requested flavor for the data
     * @return boolean indicating whether or not the data flavor is
     *         supported
     */
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return flavor.equals(dataFlavors[0]);
    }

    /**
     * @return an array of DataFlavor objects indicating the flavors the
     * data can be provided in.  The array should be ordered according to
     * preference for providing the data (from most richly descriptive to
     * least descriptive).
     *
     * @return an array of data flavors in which this data can be
     *         transferred
     */
    public DataFlavor[] getTransferDataFlavors() {
        return dataFlavors;
    }
}



