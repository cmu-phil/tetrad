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

package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.util.JOptionUtils;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Copies a selection of session nodes in the frontmost session editor, to the
 * clipboard.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
final class FontSelectionAction extends AbstractAction
        implements ClipboardOwner {

    /**
     * The desktop containing the target session editor.
     */
    private JComboBox fontFamilyBox;
    private JComboBox styleBox;
    private JComboBox sizesBox;
    private JTextArea testArea;
    private Font font;

    /**
     * Creates a new copy subsession action for the given desktop and
     * clipboard.
     */
    public FontSelectionAction() {
        super("Font...");
    }

    /**
     * Copies a parentally closed selection of session nodes in the frontmost
     * session editor to the clipboard.
     */
    public void actionPerformed(final ActionEvent e) {
        final GraphicsEnvironment graphicsEnvironment =
                GraphicsEnvironment.getLocalGraphicsEnvironment();

        final String[] fontFamilies =
                graphicsEnvironment.getAvailableFontFamilyNames();
        this.fontFamilyBox = new JComboBox(fontFamilies);
        this.fontFamilyBox.setBackground(Color.white);
        this.fontFamilyBox.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                resetFont();
            }
        });

        final String[] styles = {"Plain", "Italic", "Bold"};
        this.styleBox = new JComboBox(styles);
        this.styleBox.setBackground(Color.white);
        this.styleBox.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                resetFont();
            }
        });

        final String[] sizes = {"8", "9", "10", "11", "12", "14", "16",
                "18", "20", "22", "24", "26", "28", "36", "48", "72"};
        this.sizesBox = new JComboBox(sizes);
        this.sizesBox.setBackground(Color.white);
        this.sizesBox.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                resetFont();
            }
        });

        this.testArea = new JTextArea() {
            public Dimension getPreferredSize() {
                return new Dimension(100, 50);
            }
        };
        this.testArea.setText("Sample...");
        this.testArea.setBorder(new CompoundBorder(new LineBorder(Color.DARK_GRAY),
                new MatteBorder(3, 3, 3, 3, Color.WHITE)));

        final Box b1 = Box.createHorizontalBox();

        final Box b2 = Box.createVerticalBox();
        final Box b3 = Box.createHorizontalBox();
        b3.add(new JLabel("Font family:"));
        b3.add(Box.createHorizontalGlue());
        b2.add(b3);

        final Box b4 = Box.createHorizontalBox();
        b4.add(Box.createHorizontalStrut(5));
        b4.add(this.fontFamilyBox);
        b2.add(b4);

        final Box b5 = Box.createVerticalBox();
        final Box b6 = Box.createHorizontalBox();
        b6.add(new JLabel("Font style:"));
        b6.add(Box.createHorizontalGlue());
        b5.add(b6);

        final Box b7 = Box.createHorizontalBox();
        b7.add(Box.createHorizontalStrut(5));
        b7.add(this.styleBox);
        b5.add(b7);

        final Box b8 = Box.createVerticalBox();
        final Box b9 = Box.createHorizontalBox();
        b9.add(new JLabel("Size:"));
        b9.add(Box.createHorizontalGlue());
        b8.add(b9);

        final Box b10 = Box.createHorizontalBox();
        b10.add(Box.createHorizontalStrut(5));
        b10.add(this.sizesBox);
        b8.add(b10);

        b1.add(b2);
        b1.add(Box.createHorizontalStrut(5));
        b1.add(b5);
        b1.add(Box.createHorizontalStrut(5));
        b1.add(b8);

        final Box b11 = Box.createVerticalBox();
        b11.add(b1);
        b11.add(Box.createVerticalStrut(5));

        final Box b12 = Box.createHorizontalBox();
        b12.add(new JLabel("Preview:"));
        b12.add(Box.createHorizontalGlue());

        final Box b13 = Box.createHorizontalBox();
        b13.add(Box.createHorizontalStrut(5));
        b13.add(this.testArea);

        b11.add(b12);
        b11.add(b13);

        final JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(b11, BorderLayout.CENTER);
        panel.setBorder(new TitledBorder("Select the 'Plain' font:"));

        JOptionPane.showOptionDialog(JOptionUtils.centeringComp(), panel,
                "Font Selector", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, null, null);
    }

    private void resetFont() {
        final String family = (String) this.fontFamilyBox.getSelectedItem();
        final String styleString = (String) this.styleBox.getSelectedItem();
        final String sizeString = (String) this.sizesBox.getSelectedItem();

        final int style;

        if ("Plain".equals(styleString)) {
            style = Font.PLAIN;
        } else if ("Italic".equals(styleString)) {
            style = Font.ITALIC;
        } else if ("Bold".equals(styleString)) {
            style = Font.BOLD;
        } else {
            throw new IllegalArgumentException(
                    "Unrecognized styleString: " + styleString);
        }

        final int size = Integer.parseInt(sizeString);

        if (size < 1) {
            throw new IllegalArgumentException("Size Must be greater than or equal to 1: " + size);
        }

        this.font = new Font(family, style, size);
        this.testArea.setFont(getFont());
    }

    /**
     * Required by the AbstractAction interface; does nothing.
     */
    public void lostOwnership(final Clipboard clipboard, final Transferable contents) {
    }

    private Font getFont() {
        return this.font;
    }
}





