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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.util.JOptionUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Some basic utilities for editor stuff.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class EditorUtils {
    /**
     * <p>getTopLeftPoint.</p>
     *
     * @param modelElements a {@link java.util.List} object
     * @return a {@link java.awt.Point} object
     */
    public static Point getTopLeftPoint(List<Object> modelElements) {
        int x = Integer.MAX_VALUE;
        int y = Integer.MAX_VALUE;

        for (Object modelElement : modelElements) {
            if (modelElement instanceof SessionNodeWrapper) {
                int _x = ((SessionNodeWrapper) modelElement).getCenterX();
                int _y = ((SessionNodeWrapper) modelElement).getCenterY();
                if (_x < x) {
                    x = _x;
                }
                if (_y < y) {
                    y = _y;
                }
            }
        }

        return new Point(x, y);
    }

    private static File nextFile(String _directory, String prefix, String suffix,
                                 boolean overwrite) {
        if (prefix.endsWith(suffix)) {
            prefix = prefix.substring(0, prefix.lastIndexOf('.'));
        }

        File directory = new File(_directory);

        if (!directory.exists()) {
            boolean success = directory.mkdir();

            if (!success) {
                return null;
            }
        }

        if (overwrite) {
            return new File(directory, prefix + "." + suffix);
        }

        List<String> files = Arrays.asList(Objects.requireNonNull(directory.list()));
        String name;
        int i = 0;

        do {
            name = prefix + (++i) + "." + suffix;
        } while (files.contains(name));

        return new File(directory, name);
    }

    /**
     * Modifies the name of the given file if necessary to ensure that it has the given suffix.
     */
    private static File ensureSuffix(File file, String suffix) {
        String fileName = file.getName();

        if (!fileName.endsWith(suffix)) {
            fileName += "." + suffix;
            return new File(file.getParent(), fileName);
        } else {
            return file;
        }
    }

    /**
     * <p>getSaveFileWithPath.</p>
     *
     * @param prefix       a {@link java.lang.String} object
     * @param suffix       a {@link java.lang.String} object
     * @param parent       a {@link java.awt.Component} object
     * @param overwrite    a boolean
     * @param dialogName   a {@link java.lang.String} object
     * @param saveLocation a {@link java.lang.String} object
     * @return a {@link java.io.File} object
     */
    public static File getSaveFileWithPath(String prefix, String suffix,
                                           Component parent, boolean overwrite, String dialogName, String saveLocation) {
        JFileChooser chooser = EditorUtils.createJFileChooser(dialogName, saveLocation);

        String fileSaveLocation;
        if (saveLocation == null) {
            fileSaveLocation = Preferences.userRoot().get(
                    "fileSaveLocation", Preferences.userRoot().absolutePath());
        } else {
            fileSaveLocation = saveLocation;
        }
        File dir = new File(fileSaveLocation);
        chooser.setCurrentDirectory(dir);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        File selectedFile = EditorUtils.nextFile(fileSaveLocation, prefix, suffix, overwrite);

        chooser.setSelectedFile(selectedFile);
        File outfile;

        while (true) {
            int ret = chooser.showSaveDialog(parent);

            if (ret == JFileChooser.ERROR_OPTION) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "There was an error in choosing the file.");
                return null;
            } else if (ret == JFileChooser.CANCEL_OPTION) {
                return null;
            }

            outfile = chooser.getSelectedFile();

            if (outfile.exists()) {
                int ret2 = JOptionPane.showConfirmDialog(JOptionUtils.centeringComp(),
                        "Overwrite existing file?", "", JOptionPane.YES_NO_OPTION);
                if (ret2 == JOptionPane.YES_OPTION) {
                    break;
                }

                continue;
            }

            int ret3 = JOptionPane.showConfirmDialog(JOptionUtils.centeringComp(),
                    "Save to directory " + outfile.getParent() + "?",
                    "Confirm", JOptionPane.OK_CANCEL_OPTION);
            if (ret3 == JOptionPane.OK_OPTION) {
                break;
            }
        }

        outfile = EditorUtils.ensureSuffix(outfile, suffix);
        if (saveLocation == null) {
            Preferences.userRoot().put("fileSaveLocation", outfile.getParent());
        } else {
            Preferences.userRoot().put("sessionSaveLocation", outfile.getParent());
        }

        return outfile;
    }

    /**
     * Displays a save dialog in the getModel save directory and returns the selected file. The file is of form
     * prefix.suffix.
     *
     * @param prefix     The prefix of the file.
     * @param suffix     The suffix of the file.
     * @param parent     The parent that the save dialog should be centered on and in front of.
     * @param overwrite  True iff the file prefix.suffix should be overwritten. If false, the next avialable filename in
     *                   the series prefix{n}.suffix will be suggested.
     * @param dialogName a {@link java.lang.String} object
     * @return null, if the selection was cancelled or there was an error.
     */
    public static File getSaveFile(String prefix, String suffix,
                                   Component parent, boolean overwrite, String dialogName) {
        return EditorUtils.getSaveFileWithPath(prefix, suffix, parent, overwrite, dialogName, null);
    }

    /**
     * @return a new JFileChooser properly set up for Tetrad.
     */
    private static JFileChooser createJFileChooser(String name, String path) {
        if (name == null) {
            name = "Save";
        }

        JFileChooser chooser = new JFileChooser();
        String fileSaveLocation =
                Preferences.userRoot().get("fileSaveLocation", "");
        if (path != null) {
            fileSaveLocation = path;
        }
        chooser.setCurrentDirectory(new File(fileSaveLocation));
        chooser.resetChoosableFileFilters();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setDialogTitle(name);

        return chooser;
    }
}




