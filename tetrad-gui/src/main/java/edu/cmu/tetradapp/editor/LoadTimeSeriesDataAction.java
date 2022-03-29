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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.prefs.Preferences;

/**
 * Loads a session from a file.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
class LoadTimeSeriesDataAction extends AbstractAction {

    private final DataEditor editor;

    public LoadTimeSeriesDataAction(final DataEditor editor) {
        super("Load Time Series Data");

        if (editor == null) {
            throw new NullPointerException("Data Editor must not be null.");
        }

        this.editor = editor;
    }

    /**
     * Performs the action of loading a session from a file.
     */
    public void actionPerformed(final ActionEvent e) {
        loadTimeSeriesDataSet();
    }

    /**
     * Method loadDataSet_TabDelim
     */
    private void loadTimeSeriesDataSet() {

        // select a file to load using the file chooser
        final JFileChooser chooser = getJFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.showOpenDialog(this.editor);

        // get the file
        final File file = chooser.getSelectedFile();
        Preferences.userRoot().put("fileSaveLocation", file.getParent());

        try {
            final BufferedReader in = new BufferedReader(new FileReader(file));
            String line;
            StringTokenizer st;

            // read in variable name and set up DataSet.
            final List<Node> variables = new LinkedList<>();

            st = new StringTokenizer(in.readLine());

            while (st.hasMoreTokens()) {
                final String name = st.nextToken();
                final ContinuousVariable var = new ContinuousVariable(name);
                variables.add(var);
            }

            final DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(0, variables.size()), variables);

            final int row = -1;

            while ((line = in.readLine()) != null) {
                int col = -1;

                st = new StringTokenizer(line);

                while (st.hasMoreTokens()) {
                    final String literal = st.nextToken();
                    if (literal.length() == 0) {
                        continue;
                    }

                    dataSet.setObject(row, ++col, literal);
                }
            }

            final TimeSeriesData dataSet3 = new TimeSeriesData(
                    dataSet.getDoubleData(), dataSet.getVariableNames());

            this.editor.getDataWrapper().setDataModel(dataSet3);
            firePropertyChange("modelChanged", null, null);
            this.editor.reset();
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private JFileChooser getJFileChooser() {
        final JFileChooser chooser = new JFileChooser();
        final String sessionSaveLocation = Preferences.userRoot().get(
                "fileSaveLocation", Preferences.userRoot().absolutePath());
        chooser.setCurrentDirectory(new File(sessionSaveLocation));
        chooser.resetChoosableFileFilters();

        /*
        for (int i = 0; i < persistenceFormats.length; i++) {
            chooser.addChoosableFileFilter(persistenceFormats[i]);
        }

        chooser.setFileFilter(persistenceFormats[0]);
        */
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

        return chooser;
    }
}





