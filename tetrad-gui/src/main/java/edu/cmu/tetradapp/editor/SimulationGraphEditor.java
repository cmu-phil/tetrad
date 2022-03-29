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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetradapp.model.GraphWrapper;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.util.List;

/**
 * Displays a list of graphs with tabs to select among them, similar to the Data
 * Editor. For use in displaying simulations.
 *
 * @author jdramsey
 */
public final class SimulationGraphEditor extends JPanel {

    private static final long serialVersionUID = -8394516826928341168L;

    /**
     * The data wrapper being displayed.
     */
    private final List<Graph> graphs;

    /**
     * A tabbed pane containing displays for all data models and displaying
     * 'dataModel' currently.
     */
    private JTabbedPane tabbedPane = new JTabbedPane();

    //==========================CONSTUCTORS===============================//

    /**
     * Constructs the editor. Edited by Zhou on 8/20/18
     */
    public SimulationGraphEditor(List<Graph> graphs) {
        tabbedPane = new JTabbedPane(SwingConstants.LEFT);
        this.graphs = graphs;

        this.setLayout(new BorderLayout());
        this.reset();
    }

    //==========================PUBLIC METHODS=============================//

    /**
     * 2
     * Replaces the getModel Datamodels with the given one. Note, that by
     * calling this you are removing ALL the getModel data-models, they will be
     * lost forever!
     *
     * @param graphs - The graphs to display now.
     */
    public final void replace(List<Graph> graphs) {
        tabbedPane.removeAll();
        this.setPreferredSize(new Dimension(600, 400));

        // now rebuild
        this.removeAll();

        if (graphs.isEmpty()) {

            // Do nothing.
        } else if (graphs.size() > 1) {
            for (int i = 0; i < graphs.size(); i++) {
                tabbedPane.addTab(tabName(i + 1), new JScrollPane(this.graphDisplay(graphs.get(i))));
            }

            this.add(tabbedPane, BorderLayout.CENTER);
        } else {
            tabbedPane.addTab(tabName(1), new JScrollPane(this.graphDisplay(graphs.get(0))));
            this.add(tabbedPane, BorderLayout.CENTER);

        }

        tabbedPane.validate();
        tabbedPane.repaint();
    }

    /**
     * Sets this editor to display contents of the given data model wrapper.
     */
    private void reset() {
        this.tabbedPane().removeAll();
        this.setPreferredSize(new Dimension(600, 400));

        this.removeAll();

        final int selectedIndex = -1;

        for (int i = 0; i < graphs.size(); i++) {
            Graph graph = graphs.get(i);
            this.tabbedPane().addTab(tabName(i + 1), this.graphDisplay(graph));
        }

        this.tabbedPane().setSelectedIndex(selectedIndex);

        this.add(this.tabbedPane(), BorderLayout.CENTER);

        this.validate();
    }

    public DataModel getSelectedDataModel() {
        Component selectedComponent = this.tabbedPane().getSelectedComponent();
        DataModelContainer scrollPane = (DataModelContainer) selectedComponent;

        if (scrollPane == null) {
            return null;
        }

        return scrollPane.getDataModel();
    }

    public void selectFirstTab() {
        this.tabbedPane().setSelectedIndex(0);
    }

    public void propertyChange(PropertyChangeEvent evt) {
        this.firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
    }

    //=============================PRIVATE METHODS======================//
    private static String tabName(int i) {
        return "" + i;
    }

    /**
     * @return the data display for the given model.
     */
    private JComponent graphDisplay(Graph graph) {
        GraphEditor graphEditor = new GraphEditor(new GraphWrapper(graph));
        graphEditor.enableEditing(false);

        return graphEditor.getWorkbench();
    }

    private JTabbedPane tabbedPane() {
        return tabbedPane;
    }
}
