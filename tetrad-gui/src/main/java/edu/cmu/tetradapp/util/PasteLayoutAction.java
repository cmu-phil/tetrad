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

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.graph.Graph;

import javax.swing.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;

/**
 * Pastes a layout into a LayoutEditable gadget, which lays out the graph in
 * that gadget according to the stored graph.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class PasteLayoutAction extends AbstractAction
        implements ClipboardOwner {
    /**
     * The LayoutEditable containing the target session editor.
     */
    private LayoutEditable layoutEditable;

    /**
     * Constucts an action for loading the session in the given '.tet' file into
     * the layoutEditable.
     */
    public PasteLayoutAction(LayoutEditable layoutEditable) {
        super("Paste Layout");

        if (layoutEditable == null) {
            throw new NullPointerException();
        }

        this.layoutEditable = layoutEditable;
    }

    /**
     * Copies a parentally closed selection of session nodes in the frontmost
     * session editor to the clipboard.
     */
    public void actionPerformed(ActionEvent e) {
        Transferable transferable = InternalClipboard.getInstance()
                .getContents(null);

        if (!(transferable instanceof LayoutSelection)) {
            return;
        }

        LayoutSelection selection = (LayoutSelection) transferable;
        DataFlavor flavor = new DataFlavor(LayoutSelection.class, "Layout");

        try {
            Graph layoutGraph = (Graph) selection.getTransferData(flavor);

            if (layoutGraph != null) {
                this.layoutEditable.layoutByGraph(layoutGraph);
            }
        }
        catch (Exception e1) {
            throw new RuntimeException(e1);
        }
    }

    /**
     * Notifies this object that it is no longer the owner of the contents of
     * the clipboard.
     *
     * @param clipboard the clipboard that is no longer owned
     * @param contents  the contents which this owner had placed on the
     *                  clipboard
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
}





