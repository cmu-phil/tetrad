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

package edu.cmu.tetradapp.editor;

import javax.swing.*;


/**
 * Displays a workbench editing workbench area together with a toolbench for
 * editing tetrad-style graphs.
 *
 * @author Aaron Powers
 * @author Joseph Ramsey
 */
public final class GraphFileMenu extends JMenu {

    public GraphFileMenu(GraphEditable editable, JComponent comp) {
        super("File");

        JMenu load = new JMenu("Load...");
        add(load);

        load.add(new LoadGraph(editable, "XML..."));
        load.add(new LoadGraphTxt(editable, "Text..."));

        JMenu save = new JMenu("Save...");
        add(save);

        save.add(new SaveGraph(editable, "XML...", SaveGraph.Type.xml));
        save.add(new SaveGraph(editable, "Text...", SaveGraph.Type.text));
        save.add(new SaveGraph(editable, "R...", SaveGraph.Type.r));

//        add(new SaveGraph(editable, "Save Graph..."));
//        file.add(new SaveScreenshot(this, true, "Save Screenshot..."));
        add(new SaveComponentImage(comp, "Save Graph Image..."));
    }
}


