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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetradapp.model.EditorUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.prefs.Preferences;

/**
 * Saves out a PNG image for a component.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class SaveGraph extends AbstractAction {

    /**
     * The component whose image is to be saved.
     */
    private GraphEditable graphEditable;

    /**
     * A reference to the title, to be used a dialog title.
     */
    private String title = "";

    /**
     * True if the graph should be saved in XML, false if in text.
     */
    private Type type = Type.xml;
    
    public enum Type{text, xml, r}

    public SaveGraph(GraphEditable graphEditable, String title, Type type) {
        super(title);
        this.title = title;
        this.type = type;

        if (this.title == null) this.title = "Save";

        if (graphEditable == null) {
            throw new NullPointerException("Component must not be null.");
        }

        this.graphEditable = graphEditable;
    }

    /**
     * Performs the action of loading a session from a file.
     */
    public void actionPerformed(ActionEvent e) {
        Graph graph = getGraphEditable().getGraph();
        Component parent = (Component) getGraphEditable();

        if (type == Type.xml) {
            File file = EditorUtils.getSaveFile("graph", "xml", parent, false, title);
            PrintWriter out = GraphUtils.saveGraph(graph, file, true);
            Preferences.userRoot().put("fileSaveLocation", file.getParent());
            out.close();
        }
        else if (type == Type.text) {
            File file = EditorUtils.getSaveFile("graph", "txt", parent, false, title);
            PrintWriter out = GraphUtils.saveGraph(graph, file, false);
            Preferences.userRoot().put("fileSaveLocation", file.getParent());
            out.close();
        }
        else if (type == Type.r) {
            File file = EditorUtils.getSaveFile("graph", "r.txt", parent, false, title);
            try {
                String text = GraphUtils.rMatrix(graph);

                PrintWriter out = new PrintWriter(file);
                out.println(text);
                Preferences.userRoot().put("fileSaveLocation", file.getParent());
                out.close();
            } catch (FileNotFoundException e1) {
                e1.printStackTrace();
                throw new RuntimeException("Not a directed graph.", e1);
            } catch (IllegalArgumentException e1) {

                // Probably not a directed graph.
                JOptionPane.showMessageDialog(getGraphEditable().getWorkbench(), e1.getMessage());
            }
        }
    }

    private GraphEditable getGraphEditable() {
        return graphEditable;
    }
}






