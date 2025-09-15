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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.util.JOptionUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
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

    public static void addTabCompleteLogic(JTextField textField, List<String> words) {
        SwingUtilities.invokeLater(() -> {

            // Remove default Tab key focus traversal
            textField.setFocusTraversalKeysEnabled(false);

            // Add key binding for Tab key
            textField.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "tabCompletion");
            textField.getActionMap().put("tabCompletion", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String text = textField.getText();
                    int caretPosition = textField.getCaretPosition();
                    String beforeCaret = text.substring(0, caretPosition);
                    String afterCaret = text.substring(caretPosition);

                    // Find the start of the current word
                    int wordStart = beforeCaret.lastIndexOf(' ') + 1;
                    String currentWord = beforeCaret.substring(wordStart);

                    String completion = getLongestCommonPrefix(currentWord, words);
                    if (completion != null && !completion.equals(currentWord)) {
                        String completedText = beforeCaret.substring(0, wordStart) + completion + afterCaret;
                        textField.setText(completedText);
                        textField.setCaretPosition(wordStart + completion.length());
                    }
                }
            });
        });
    }

    private static String getLongestCommonPrefix(String text, List<String> words) {
        List<String> matches = new ArrayList<>();
        for (String word : words) {
            if (word.startsWith(text)) {
                matches.add(word);
            }
        }

        if (matches.isEmpty()) {
            return null;
        }

        String commonPrefix = matches.get(0);
        for (String match : matches) {
            commonPrefix = commonPrefix(commonPrefix, match);
        }

        return commonPrefix;
    }

    private static String commonPrefix(String s1, String s2) {
        int minLength = Math.min(s1.length(), s2.length());
        int i = 0;
        while (i < minLength && s1.charAt(i) == s2.charAt(i)) {
            i++;
        }
        return s1.substring(0, i);
    }

    /**
     * A JTextFieldWithPrompt is a custom JTextField that displays a prompt text when no text has been entered and the
     * component does not have focus.
     */
    public static class JTextFieldWithPrompt extends JTextField {
        /**
         * The prompt text.
         */
        private final String promptText;
        /**
         * The color of the prompt text.
         */
        private final Color promptColor;

        public JTextFieldWithPrompt(String promptText) {
            this(promptText, Color.GRAY);
        }

        public JTextFieldWithPrompt(String promptText, Color promptColor) {
            this.promptText = promptText;
            this.promptColor = promptColor;

            // Set focus listener to repaint the component when focus is gained or lost
            this.addFocusListener(new FocusListener() {

                @Override
                public void focusGained(FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    repaint();
                }
            });
        }

        /**
         * This method is responsible for painting the component. It overrides the paintComponent method from the
         * JTextField class. It checks if the text in the component is empty and if it does not have focus. If both
         * conditions are true, it paints the prompt text on the component using the specified prompt color and font
         * style.
         *
         * @param g the Graphics object used for painting
         */
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            setDoubleBuffered(true);

            if (getText().isEmpty() && !isFocusOwner()) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setColor(promptColor);
                g2d.setFont(getFont().deriveFont(Font.ITALIC));
                int padding = (getHeight() - getFont().getSize()) / 2;
                g2d.drawString(promptText, getInsets().left, getHeight() - padding - 1);
                g2d.dispose();
            }
        }
    }
}





