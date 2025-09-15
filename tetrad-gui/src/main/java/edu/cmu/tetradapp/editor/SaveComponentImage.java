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
 * @version $Id: $Id
 */
public class SaveComponentImage extends AbstractAction {

    /**
     * The component whose image is to be saved.
     */
    private final JComponent comp;

    /**
     * The action name, to be used as a title to the save dialog.
     */
    private final String actionName;

    /**
     * <p>Constructor for SaveComponentImage.</p>
     *
     * @param comp       a {@link javax.swing.JComponent} object
     * @param actionName a {@link java.lang.String} object
     */
    public SaveComponentImage(JComponent comp, String actionName) {
        super(actionName);
        this.actionName = actionName;

        if (comp == null) {
            throw new NullPointerException("Component must not be null.");
        }

        this.comp = comp;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Performs the action of loading a session from a file.
     */
    public void actionPerformed(ActionEvent e) {
        File file = EditorUtils.getSaveFile("image", "png", getComp(), false, this.actionName);

        // Create the image.
        Dimension size = getComp().getSize();
        BufferedImage image = new BufferedImage(size.width, size.height,
                BufferedImage.TYPE_INT_ARGB_PRE);
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
        return this.comp;
    }

}








