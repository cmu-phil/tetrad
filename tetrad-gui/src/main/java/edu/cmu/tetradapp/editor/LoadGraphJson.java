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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.prefs.Preferences;

/**
 * Nov 30, 2016 5:54:33 PM
 *
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * @version $Id: $Id
 */
public class LoadGraphJson extends AbstractAction {

    private static final long serialVersionUID = 2580641970762892159L;

    /**
     * The component whose image is to be saved.
     */
    private final GraphEditable graphEditable;

    /**
     * <p>Constructor for LoadGraphJson.</p>
     *
     * @param graphEditable a {@link edu.cmu.tetradapp.editor.GraphEditable} object
     * @param title         a {@link java.lang.String} object
     */
    public LoadGraphJson(GraphEditable graphEditable, String title) {
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
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        JFileChooser chooser = LoadGraphJson.getJFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.showOpenDialog((Component) this.graphEditable);

        File file = chooser.getSelectedFile();

        if (file == null) {
            JOptionPane.showMessageDialog((Component) this.graphEditable,
                    "No file was selected.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Preferences.userRoot().put("fileSaveLocation", file.getParent());

        Graph graph = GraphSaveLoadUtils.loadGraphJson(file);

        // The saved node positions should be used instead of just arranging the
        // nodes in a circle.
//        LayoutUtil.circleLayout(graph, 200, 200, 150);
        this.graphEditable.setGraph(graph);
    }

}

