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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.model.UpdaterWrapper;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Lets the user visually add and remove evidence, perform updates, and view
 * Bayes updating results.
 *
 * @author William Taysom -- Jun 14, 2003
 * @author Joseph Ramsey
 */
class EvidenceWizardSingle extends JPanel {
    private final UpdaterWrapper updaterWrapper;
    private final GraphWorkbench workbench;
    private final EvidenceEditor evidenceEditor;

    /**
     * This is the wizard for the BayesUpdateEditor class.  It allows you to add
     * and remove evidence, and to updater based on it.  Parameters are of the
     * form P(Node=c1|Parent1=c2, Parent2=c2,...); values for these parameters
     * are probabilities ranging from 0.0 to 1.0.
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
        this.setBorder(new MatteBorder(10, 10, 10, 10, this.getBackground()));
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JButton updateButton = new JButton("Do Update Now");

        // Do Layout.
        Box b0 = Box.createHorizontalBox();
        b0.add(new JLabel("<html>" +
                "Select the node in the graph that you would like to see updated" +
                "<br>probabilities for. In the list below, select the evidence that" +
                "<br>you would like to update on. Click the 'Do Update Now' button" +
                "<br>to view updated probabilities." + "</html>"));
        b0.add(Box.createHorizontalGlue());
        this.add(b0);
        this.add(Box.createVerticalStrut(10));

        evidenceEditor = new EvidenceEditor(updaterWrapper.getBayesUpdater().getEvidence());
        this.getUpdaterWrapper().getParams().set("evidence", evidenceEditor.getEvidence());
        this.add(evidenceEditor);
        this.add(Box.createVerticalStrut(10));

        Box b2 = Box.createHorizontalBox();
        b2.add(Box.createHorizontalGlue());
        b2.add(updateButton);
        this.add(b2);
        this.add(Box.createVerticalGlue());

        // Add listeners.
        updateButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                DisplayNode graphNode = EvidenceWizardSingle.this.getWorkbench().getSelectedNode();

                if (graphNode == null) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "Please select exactly one node in the graph.");
                    return;
                }

                Node tetradNode = graphNode.getModelNode();
                String selectedNodeName = tetradNode.getName();

                EvidenceWizardSingle.this.getUpdaterWrapper().getParams().set("evidence", evidenceEditor.getEvidence());
                EvidenceWizardSingle.this.getUpdaterWrapper().getParams().set("variable", updaterWrapper.getBayesUpdater().getBayesIm().getBayesPm().getVariable(tetradNode));
                EvidenceWizardSingle.this.getUpdaterWrapper().getBayesUpdater().setEvidence(evidenceEditor.getEvidence());


                Graph updatedGraph = EvidenceWizardSingle.this.getUpdaterWrapper().getBayesUpdater().getManipulatedGraph();
                Node selectedNode = updatedGraph.getNode(selectedNodeName);

                EvidenceWizardSingle.this.getWorkbench().setGraph(updatedGraph);
                EvidenceWizardSingle.this.getWorkbench().deselectAll();
                EvidenceWizardSingle.this.getWorkbench().selectNode(selectedNode);

                EvidenceWizardSingle.this.firePropertyChange("updateButtonPressed", null, null);
                EvidenceWizardSingle.this.firePropertyChange("modelChanged", null, null);
            }
        });
    }

    public BayesIm getBayesIM() {
        return this.getUpdaterWrapper().getBayesUpdater().getUpdatedBayesIm();
    }

    private UpdaterWrapper getUpdaterWrapper() {
        return updaterWrapper;
    }

    private GraphWorkbench getWorkbench() {
        return workbench;
    }
}




