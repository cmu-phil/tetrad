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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetradapp.model.FactorAnalysisRunner;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * <p>FactorAnalysisEditor class.</p>
 *
 * @author Michael Freenor
 * @version $Id: $Id
 */
public class FactorAnalysisEditor extends AbstractSearchEditor {

    //=========================CONSTRUCTORS============================//

    /**
     * Opens up an editor to let the user view the given PcRunner.
     *
     * @param runner a {@link edu.cmu.tetradapp.model.FactorAnalysisRunner} object
     */
    public FactorAnalysisEditor(FactorAnalysisRunner runner) {
        super(runner, "Factor Analysis");
    }

    //=============================== Public Methods ==================================//

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return getWorkbench().getGraph();
    }

    /**
     * <p>layoutByGraph.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void layoutByGraph(Graph graph) {
        getWorkbench().layoutByGraph(graph);
    }

    /**
     * <p>getVisibleRect.</p>
     *
     * @return a {@link java.awt.Rectangle} object
     */
    public Rectangle getVisibleRect() {
        return getWorkbench().getVisibleRect();
    }

    //==========================PROTECTED METHODS============================//


    /**
     * {@inheritDoc}
     * <p>
     * Sets up the editor, does the layout, and so on.
     */
    protected void setup(String resultLabel) {
        FactorAnalysisRunner runner = (FactorAnalysisRunner) getAlgorithmRunner();
        Graph graph = runner.getGraph();


        JTextArea display = new JTextArea(runner.getOutput());
        JScrollPane scrollPane = new JScrollPane(display);
        scrollPane.setPreferredSize(new Dimension(500, 400));
        display.setEditable(false);
        display.setFont(new Font("Monospaced", Font.PLAIN, 12));

        LayoutUtil.defaultLayout(graph);
        LayoutUtil.fruchtermanReingoldLayout(graph);

        GraphWorkbench workbench = new GraphWorkbench(graph);

        JScrollPane graphPane = new JScrollPane(workbench);
        graphPane.setPreferredSize(new Dimension(500, 400));

        Box box = Box.createHorizontalBox();
        box.add(scrollPane);

        box.add(Box.createHorizontalStrut(3));
        box.add(Box.createHorizontalStrut(5));
        box.add(Box.createHorizontalGlue());

        Box vBox = Box.createVerticalBox();
        vBox.add(Box.createVerticalStrut(15));
        vBox.add(box);
        vBox.add(Box.createVerticalStrut(5));
        box.add(graphPane);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(vBox, BorderLayout.CENTER);

        add(panel);
    }

    /**
     * {@inheritDoc}
     */
    protected void addSpecialMenus(JMenuBar menuBar) {

    }

    /**
     * <p>getSourceGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getSourceGraph() {
        Graph sourceGraph = getWorkbench().getGraph();

        if (sourceGraph == null) {
            sourceGraph = getAlgorithmRunner().getSourceGraph();
        }

        return sourceGraph;
    }

    /**
     * <p>getVarNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getVarNames() {
        return (List<String>) getAlgorithmRunner().getParams().get("varNames", null);
    }

    /**
     * <p>getToolbar.</p>
     *
     * @return a {@link javax.swing.JPanel} object
     */
    public JPanel getToolbar() {
        return null;
    }

    //================================PRIVATE METHODS====================//

    /**
     * {@inheritDoc}
     */
    protected void doDefaultArrangement(Graph resultGraph) {
        if (getLatestWorkbenchGraph() != null) {   //(alreadyLaidOut) {
            LayoutUtil.arrangeBySourceGraph(resultGraph,
                    getLatestWorkbenchGraph());
        } else {
            LayoutUtil.defaultLayout(resultGraph);
        }
    }

}





