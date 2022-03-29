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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemUpdater;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Lets the user visually add and remove evidence, perform updates, and view SEM
 * updating results.
 *
 * @author William Taysom -- Jun 14, 2003
 * @author Joseph Ramsey
 */
class SemEvidenceWizardSingle extends JPanel {
    private final SemUpdater bayesUpdater;
    private final GraphWorkbench workbench;
    private final SemEvidenceEditor evidenceEditor;

    /**
     * This is the wizard for the BayesUpdateEditor class.  It allows you to add
     * and remove evidence, and to updater based on it.  Parameters are of the
     * form P(Node=c1|Parent1=c2, Parent2=c2,...); values for these parameters
     * are probabilities ranging from 0.0 to 1.0.
     */
    public SemEvidenceWizardSingle(SemUpdater semUpdater,
                                   GraphWorkbench workbench) {
        if (semUpdater == null) {
            throw new NullPointerException();
        }

        if (workbench == null) {
            throw new NullPointerException();
        }

        Node node = workbench.getGraph().getNodes().get(0);
        workbench.deselectAll();
        workbench.selectNode(node);

        // Components.
        bayesUpdater = semUpdater;
        this.workbench = workbench;

        workbench.setAllowDoubleClickActions(false);
        this.setBorder(new MatteBorder(10, 10, 10, 10, this.getBackground()));
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JButton updateButton = new JButton("Do Update Now");

        // Do Layout.
        Box b0 = Box.createHorizontalBox();
        b0.add(new JLabel("<html>" +
                "In the list below, specify values for variables you have evidence " +
                "<br>for. Click the 'Do Update Now' button to view updated means. " +
                "<br>(Other parameters remain the same.)."));
        b0.add(Box.createHorizontalGlue());
        this.add(b0);
        this.add(Box.createVerticalStrut(10));

        evidenceEditor = new SemEvidenceEditor(semUpdater.getEvidence());
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
                DisplayNode graphNode = SemEvidenceWizardSingle.this.getWorkbench().getSelectedNode();

                if (graphNode == null) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "Please select exactly one node in the graph.");
                    return;
                }

                Node tetradNode = graphNode.getModelNode();
                String selectedNodeName = tetradNode.getName();

                SemEvidenceWizardSingle.this.getSemUpdater().setEvidence(evidenceEditor.getEvidence());

                Graph updatedGraph = SemEvidenceWizardSingle.this.getSemUpdater().getManipulatedGraph();
                Node selectedNode = updatedGraph.getNode(selectedNodeName);

                SemEvidenceWizardSingle.this.getWorkbench().setGraph(updatedGraph);
                SemEvidenceWizardSingle.this.getWorkbench().deselectAll();
                SemEvidenceWizardSingle.this.getWorkbench().selectNode(selectedNode);

                SemEvidenceWizardSingle.this.firePropertyChange("updateButtonPressed", null, null);
                SemEvidenceWizardSingle.this.firePropertyChange("modelChanged", null, null);
            }
        });
    }

    public SemIm getSemIm() {
        return this.getSemUpdater().getUpdatedSemIm();
    }

    private SemUpdater getSemUpdater() {
        return bayesUpdater;
    }

    private GraphWorkbench getWorkbench() {
        return workbench;
    }
}




