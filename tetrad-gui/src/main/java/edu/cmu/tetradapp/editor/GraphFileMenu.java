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

package edu.cmu.tetradapp.editor;

import javax.swing.*;


/**
 * Displays a workbench editing workbench area together with a toolbench for editing tetrad-style graphs.
 *
 * @author Aaron Powers
 * @author josephramsey
 * @version $Id: $Id
 */
public final class GraphFileMenu extends JMenu {

    private static final long serialVersionUID = 8003709852565658589L;

    /**
     * <p>Constructor for GraphFileMenu.</p>
     *
     * @param editable a {@link edu.cmu.tetradapp.editor.GraphEditable} object
     * @param comp     a {@link javax.swing.JComponent} object
     * @param saveOnly a boolean
     */
    public GraphFileMenu(GraphEditable editable, JComponent comp, boolean saveOnly) {
        super("File");

        if (!saveOnly) {
            JMenu load = new JMenu("Load...");
            add(load);

            load.add(new LoadGraphTxt(editable, "Text..."));
            load.add(new LoadGraph(editable, "XML..."));
            load.add(new LoadGraphJson(editable, "Json..."));
            load.add(new LoadGraphAmatCpdag(editable, "amat.cpdag..."));
            load.add(new LoadGraphAmatPag(editable, "amat.pag..."));
        }

        JMenu save = new JMenu("Save...");
        add(save);

        save.add(new SaveGraph(editable, "Text...", SaveGraph.Type.text));
        save.add(new SaveGraph(editable, "XML...", SaveGraph.Type.xml));
        save.add(new SaveGraph(editable, "Json...", SaveGraph.Type.json));
        save.add(new SaveGraph(editable, "R...", SaveGraph.Type.r));
        save.add(new SaveGraph(editable, "Dot...", SaveGraph.Type.dot));
        save.add(new SaveGraph(editable, "amat.cpdag...", SaveGraph.Type.amatCpdag));
        save.add(new SaveGraph(editable, "amat.pag...", SaveGraph.Type.amatPag));
//        save.add(new SaveGraph(editable, "PCALG...", SaveGraph.Type.pcalg));
        save.add(new SaveGraph(editable, "lavaan...", SaveGraph.Type.lavaan));

        addSeparator();
        add(new SaveComponentImage(comp, "Save Graph Image..."));
    }
}


