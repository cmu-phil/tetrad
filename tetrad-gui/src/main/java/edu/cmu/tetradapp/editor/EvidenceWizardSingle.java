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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.model.UpdaterWrapper;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.MatteBorder;

/**
 * Lets the user visually add and remove evidence, perform updates, and view Bayes updating results.
 *
 * @author William Taysom -- Jun 14, 2003
 * @author josephramsey
 */
class EvidenceWizardSingle extends JPanel {
    private final UpdaterWrapper updaterWrapper;
    private final GraphWorkbench workbench;
    private final EvidenceEditor evidenceEditor;

    /**
     * This is the wizard for the BayesUpdateEditor class.  It allows you to add and remove evidence, and to updater
     * based on it.  Parameters are of the form P(Node=c1|Parent1=c2, Parent2=c2,...); values for these parameters are
     * probabilities ranging from 0.0 to 1.0.
     *
     * @param updaterWrapper a {@link edu.cmu.tetradapp.model.UpdaterWrapper} object
     * @param workbench      a {@link edu.cmu.tetradapp.workbench.GraphWorkbench} object
     */
    public EvidenceWizardSingle(UpdaterWrapper updaterWrapper,
                                GraphWorkbench workbench) {
        if (updaterWrapper == null) {
            throw new NullPointerException();
        }

        if (workbench == null) {
            throw new NullPointerException();
        }

        Node node = workbench.getGraph().getNodes().get(0);
        workbench.deselectAll();
        workbench.selectNode(node);

        // Components.
        this.updaterWrapper = updaterWrapper;
        this.workbench = workbench;

        workbench.setAllowDoubleClickActions(false);
        setBorder(new MatteBorder(10, 10, 10, 10, getBackground()));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JButton updateButton = new JButton("Do Update Now");

        // Do Layout.
        Box b0 = Box.createHorizontalBox();
        b0.add(new JLabel("<html>" +
                          "Select the node in the graph that you would like to see updated" +
                          "<br>probabilities for. In the list below, select the evidence that" +
                          "<br>you would like to update on. Click the 'Do Update Now' button" +
                          "<br>to view updated probabilities." + "</html>"));
        b0.add(Box.createHorizontalGlue());
        add(b0);
        add(Box.createVerticalStrut(10));

        this.evidenceEditor = new EvidenceEditor(updaterWrapper.getBayesUpdater().getEvidence());
        getUpdaterWrapper().getParams().set("evidence", this.evidenceEditor.getEvidence());
        add(this.evidenceEditor);
        add(Box.createVerticalStrut(10));

        Box b2 = Box.createHorizontalBox();
        b2.add(Box.createHorizontalGlue());
        b2.add(updateButton);
        add(b2);
        add(Box.createVerticalGlue());

        // Add listeners.
        updateButton.addActionListener(e -> {
            DisplayNode graphNode = getWorkbench().getSelectedNode();

            if (graphNode == null) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Please select exactly one node in the graph.");
                return;
            }

            Node tetradNode = graphNode.getModelNode();
            String selectedNodeName = tetradNode.getName();

            getUpdaterWrapper().getParams().set("evidence", EvidenceWizardSingle.this.evidenceEditor.getEvidence());
            getUpdaterWrapper().getParams().set("variable", updaterWrapper.getBayesUpdater().getBayesIm().getBayesPm().getVariable(tetradNode));
            getUpdaterWrapper().getBayesUpdater().setEvidence(EvidenceWizardSingle.this.evidenceEditor.getEvidence());


            Graph updatedGraph = getUpdaterWrapper().getBayesUpdater().getManipulatedGraph();
            Node selectedNode = updatedGraph.getNode(selectedNodeName);

            getWorkbench().setGraph(updatedGraph);
            getWorkbench().deselectAll();
            getWorkbench().selectNode(selectedNode);

            firePropertyChange("updateButtonPressed", null, null);
            firePropertyChange("modelChanged", null, null);
        });
    }

    private UpdaterWrapper getUpdaterWrapper() {
        return this.updaterWrapper;
    }

    private GraphWorkbench getWorkbench() {
        return this.workbench;
    }
}




