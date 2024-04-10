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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.bayes.BayesBifParser;
import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetradapp.model.BayesImWrapper;
import nu.xom.Document;
import nu.xom.Serializer;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.prefs.Preferences;


class LoadBayesImBifAction extends AbstractAction {
    private final BayesImWrapper bayesImWrapper;
    private final BayesImEditor bayesImEditor;

    /**
     * <p>Constructor for LoadBayesImXmlAction.</p>
     *
     * @param wrapper       a {@link BayesImWrapper} object
     * @param bayesImEditor a {@link BayesImEditor} object
     */
    public LoadBayesImBifAction(BayesImWrapper wrapper, BayesImEditor bayesImEditor) {
        super("Load Bayes IM as BIF");
        if (bayesImEditor == null) {
            throw new NullPointerException(
                    "BayesImEditorWizard must not be null.");
        }
        this.bayesImWrapper = wrapper;
        this.bayesImEditor = bayesImEditor;
    }

    private static JFileChooser getJFileChooser() {
        JFileChooser chooser = new JFileChooser();
        String sessionSaveLocation = Preferences.userRoot().get(
                "fileSaveLocation", Preferences.userRoot().absolutePath());
        chooser.setCurrentDirectory(new File(sessionSaveLocation));
        chooser.resetChoosableFileFilters();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".bif");
            }

            @Override
            public String getDescription() {
                return "Bayes IM BIF Files";
            }
        });
        return chooser;
    }

    private static void printDocument(Document document) {
        Serializer serializer = new Serializer(System.out);

        serializer.setLineSeparator("\n");
        serializer.setIndent(2);

        try {
            serializer.write(document);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void actionPerformed(ActionEvent e) {
        if (this.bayesImWrapper == null) {
            throw new RuntimeException("Not a Bayes IM.");
        }

        JFileChooser chooser = LoadBayesImBifAction.getJFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

        chooser.showOpenDialog(null);

        File file = chooser.getSelectedFile();

        if (file == null) {
            throw new NullPointerException("No file selected.");
        }

        String text;

        // Open the file, read in all text, and put it in a String variable.
        try {
            text = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        BayesIm bayesIm = BayesBifParser.makeBayesIm(text);

        System.out.println(bayesIm);

        boolean allSpecified = true;

        for (edu.cmu.tetrad.graph.Node node : bayesIm.getBayesPm().getDag().getNodes()) {
            if (node.getCenterX() == -1 || node.getCenterY() == -1) {
                allSpecified = false;
            }
        }

        if (!allSpecified) {
            LayoutUtil.defaultLayout(bayesIm.getBayesPm().getDag());
        }

        this.bayesImWrapper.setBayesIm(bayesIm);
        this.bayesImEditor.retrieveBayesIm();
    }
}


