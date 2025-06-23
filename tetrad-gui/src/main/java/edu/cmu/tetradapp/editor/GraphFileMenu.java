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

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

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

    public GraphFileMenu(GraphWorkbench workbench) {
        super("File");

        JMenu save = new JMenu("Save...");
        add(save);

        save.add(new SaveGraph(workbench, "Text...", SaveGraph.Type.text));
        save.add(new SaveGraph(workbench, "XML...", SaveGraph.Type.xml));
        save.add(new SaveGraph(workbench, "Json...", SaveGraph.Type.json));
        save.add(new SaveGraph(workbench, "R...", SaveGraph.Type.r));
        save.add(new SaveGraph(workbench, "Dot...", SaveGraph.Type.dot));
        save.add(new SaveGraph(workbench, "amat.cpdag...", SaveGraph.Type.amatCpdag));
        save.add(new SaveGraph(workbench, "amat.pag...", SaveGraph.Type.amatPag));
//        save.add(new SaveGraph(editable, "PCALG...", SaveGraph.Type.pcalg));
        save.add(new SaveGraph(workbench, "lavaan...", SaveGraph.Type.lavaan));

        Graph graph = workbench.getGraph();

        if (graph instanceof EdgeListGraph) {
            if (((EdgeListGraph) graph).getAncillaryGraph("samplingGraph") != null) {
                SaveGraph sampling = new SaveGraph(workbench, "Sampling Graph...", SaveGraph.Type.text);
                sampling.setSamplingGraph(true);
                save.add(sampling);
            }
        }
    }

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

        save.add(new SaveGraph(editable.getWorkbench(), "Text...", SaveGraph.Type.text));
        save.add(new SaveGraph(editable.getWorkbench(), "XML...", SaveGraph.Type.xml));
        save.add(new SaveGraph(editable.getWorkbench(), "Json...", SaveGraph.Type.json));
        save.add(new SaveGraph(editable.getWorkbench(), "R...", SaveGraph.Type.r));
        save.add(new SaveGraph(editable.getWorkbench(), "Dot...", SaveGraph.Type.dot));
        save.add(new SaveGraph(editable.getWorkbench(), "amat.cpdag...", SaveGraph.Type.amatCpdag));
        save.add(new SaveGraph(editable.getWorkbench(), "amat.pag...", SaveGraph.Type.amatPag));
//        save.add(new SaveGraph(editable, "PCALG...", SaveGraph.Type.pcalg));
        save.add(new SaveGraph(editable.getWorkbench(), "lavaan...", SaveGraph.Type.lavaan));

        Graph graph = editable.getWorkbench().getGraph();

        if (graph instanceof EdgeListGraph) {
            if (((EdgeListGraph) graph).getAncillaryGraph("samplingGraph") != null) {
                SaveGraph sampling = new SaveGraph(editable.getWorkbench(), "Sampling Graph...", SaveGraph.Type.text);
                sampling.setSamplingGraph(true);
                save.add(sampling);
            }
        }

        addSeparator();
        add(new SaveComponentImage(comp, "Save Graph Image..."));
    }
}


