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
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class SaveScreenshot extends AbstractAction {

    /**
     * The component whose image is to be saved.
     */
    private JComponent comp;

    /**
     * True iff the enclosing editor window of a component should be used.
     */
    private boolean editorWindowUsed;

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
        }
        catch (IOException e1) {
            throw new RuntimeException(e1);
        }
    }

    private Component getComp() {
        EditorWindow editorWindow =
                (EditorWindow) SwingUtilities.getAncestorOfClass(
                        EditorWindow.class, comp);

        if (editorWindowUsed && editorWindow != null) {
            return editorWindow.getRootPane().getContentPane();
        }
        else {
            return comp;
        }
    }

//    /**
//     * Filters out all but .png file when loading and saving.
//     *
//     * @author Joseph Ramsey jdramsey@andrew.cmu.edu
//     */
//    private static class PngFileFilter extends FileFilter {
//
//        /**
//         * Accepts a file if its name ends with ".tet".
//         */
//        public boolean accept(File file) {
//            return file.isDirectory() || file.getName().endsWith(".png");
//        }
//
//        /**
//         * @return the description of this file filter that will be displayed in
//         * a JFileChooser.
//         */
//        public String getDescription() {
//            return "PNG Image (.png)";
//        }
//    }
}






