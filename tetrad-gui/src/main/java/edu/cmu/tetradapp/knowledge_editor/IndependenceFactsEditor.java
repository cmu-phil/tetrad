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

package edu.cmu.tetradapp.knowledge_editor;

import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.model.IndependenceFactsModel;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.CharArrayReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.prefs.Preferences;

/**
 * Edits knowledge by letting the user put variable names into tiers. The number of tiers may be set. By default, it is
 * assumed that the structure of the tiers is Tier0 --&gt; Tier1 --&gt; Tier2 --&gt; ..., but special graphical
 * structures among the tiers may be set as well.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class IndependenceFactsEditor extends JPanel {

    /**
     * The knowledge to be edited.
     */
    private final IndependenceFactsModel facts;

    /**
     * The text area in which the knowledge is displayed and edited.
     */
    private JTextArea textArea;

    /**
     * Constructs a Knowledge editor for the given knowledge, variable names (that is, the list of all variable names to
     * be considered, which may vary from object to object even for the same knowledge), and possible source graph. The
     * source graph is used only to arrange nodes in the edge panel.
     *
     * @param facts a {@link edu.cmu.tetradapp.model.IndependenceFactsModel} object
     */
    public IndependenceFactsEditor(IndependenceFactsModel facts) {
        if (facts == null) {
            throw new NullPointerException();
        }

        this.facts = facts;

        setLayout(new BorderLayout());
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("Text", textDisplay());

        tabbedPane.addChangeListener(e -> resetTextDisplay());

        add(tabbedPane, BorderLayout.CENTER);
        setPreferredSize(new Dimension(550, 500));
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

    private Component textDisplay() {
        JButton loadButton = new JButton("Load from File");
        JButton parseButton = new JButton("Parse Text");

        loadButton.addActionListener(e -> {

            JFileChooser chooser = IndependenceFactsEditor.getJFileChooser();
            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            chooser.showOpenDialog(IndependenceFactsEditor.this);

            File file = chooser.getSelectedFile();

            // Can this happen?
            if (file == null) {
                return;
            }

            Preferences.userRoot().put("fileSaveLocation", file.getParent());

            try {
                IndependenceFactsEditor.this.facts.setFacts(IndependenceFactsModel.loadFacts(new FileReader(file)).getFacts());
                getTextArea().setText(IndependenceFactsEditor.this.facts.toString());
            } catch (IOException e1) {
                throw new RuntimeException("Couldn't find that file: " + file.getAbsolutePath());
            }

        });

        parseButton.addActionListener(e -> {
            try {
                String text = getTextArea().getText();
                IndependenceFactsEditor.this.facts.setFacts(IndependenceFactsModel.loadFacts(new CharArrayReader(text.toCharArray())).getFacts());
                getTextArea().setText(IndependenceFactsEditor.this.facts.toString());
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        e1.getMessage());
            }
        });

        Box b = Box.createVerticalBox();

        this.textArea = new JTextArea();
        resetTextDisplay();

        b.add(getTextArea());

        Box b2 = Box.createHorizontalBox();
        b2.add(Box.createHorizontalGlue());
        b2.add(loadButton);
        b2.add(parseButton);
        b.add(b2);

        return b;
    }

    private void resetTextDisplay() {
        getTextArea().setFont(new Font("Monospaced", Font.PLAIN, 12));
        getTextArea().setBorder(new CompoundBorder(new LineBorder(Color.black),
                new EmptyBorder(3, 3, 3, 3)));
        getTextArea().setText(this.facts.toString());
    }

    private JTextArea getTextArea() {
        return this.textArea;
    }
}




