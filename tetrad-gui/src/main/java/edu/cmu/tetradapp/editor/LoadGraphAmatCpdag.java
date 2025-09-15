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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphSaveLoadUtils;
import edu.cmu.tetrad.graph.LayoutUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.prefs.Preferences;

/**
 * Loads a graph in the "amat.cpdag" format used by PCALG.
 *
 * @author josephramsey
 */
class LoadGraphAmatCpdag extends AbstractAction {

    /**
     * The component whose image is to be saved.
     */
    private final GraphEditable graphEditable;

    /**
     * <p>Constructor for LoadGraphPcalg.</p>
     *
     * @param graphEditable a {@link GraphEditable} object
     * @param title         a {@link String} object
     */
    public LoadGraphAmatCpdag(GraphEditable graphEditable, String title) {
        super(title);

        if (graphEditable == null) {
            throw new NullPointerException("Component must not be null.");
        }

        this.graphEditable = graphEditable;
    }

    private static JFileChooser getJFileChooser() {
        JFileChooser chooser = new JFileChooser();
        String sessionSaveLocation =
                Preferences.userRoot().get("fileSaveLocation", "");
        chooser.setCurrentDirectory(new File(sessionSaveLocation));
        chooser.resetChoosableFileFilters();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        return chooser;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Performs the action of loading a session from a file.
     */
    public void actionPerformed(ActionEvent e) {
        JFileChooser chooser = LoadGraphAmatCpdag.getJFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.showOpenDialog((Component) this.graphEditable);

        File file = chooser.getSelectedFile();

        if (file == null) {
            JOptionPane.showMessageDialog((Component) this.graphEditable,
                    "No file was selected.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Preferences.userRoot().put("fileSaveLocation", file.getParent());

        Graph graph = GraphSaveLoadUtils.loadGraphAmatCpdag(file);
        LayoutUtil.defaultLayout(graph);
        this.graphEditable.setGraph(graph);
    }
}




