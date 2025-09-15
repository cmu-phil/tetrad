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

import edu.cmu.tetradapp.model.EditorUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Saves out a PNG image for a component.
 *
 * @author josephramsey
 */
class SaveScreenshot extends AbstractAction {

    /**
     * The component whose image is to be saved.
     */
    private final JComponent comp;

    /**
     * True iff the enclosing editor window of a component should be used.
     */
    private final boolean editorWindowUsed;

    /**
     * <p>Constructor for SaveScreenshot.</p>
     *
     * @param comp             a {@link javax.swing.JComponent} object
     * @param editorWindowUsed a boolean
     * @param title            a {@link java.lang.String} object
     */
    public SaveScreenshot(JComponent comp, boolean editorWindowUsed,
                          String title) {
        super(title);

        if (comp == null) {
            throw new NullPointerException("Component must not be null.");
        }

        this.comp = comp;
        this.editorWindowUsed = editorWindowUsed;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Performs the action of loading a session from a file.
     */
    public void actionPerformed(ActionEvent e) {
        File file = EditorUtils.getSaveFile("image", "png", getComp(), false, "Save");

        // Create the image.
        Dimension size = getComp().getSize();
        BufferedImage image = new BufferedImage(size.width, size.height,
                BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = image.getGraphics();
        getComp().paint(graphics);

        // Write the image to file.
        try {
            ImageIO.write(image, "png", file);
        } catch (IOException e1) {
            throw new RuntimeException(e1);
        }
    }

    private Component getComp() {
        EditorWindow editorWindow =
                (EditorWindow) SwingUtilities.getAncestorOfClass(
                        EditorWindow.class, this.comp);

        if (this.editorWindowUsed && editorWindow != null) {
            return editorWindow.getRootPane().getContentPane();
        } else {
            return this.comp;
        }
    }

}







