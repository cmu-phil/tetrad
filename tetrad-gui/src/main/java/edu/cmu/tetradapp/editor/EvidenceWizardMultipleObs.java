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
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.model.UpdaterWrapper;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * Lets the user visually add and remove evidence, perform updates, and view
 * Bayes updating results.
 *
 * @author William Taysom -- Jun 14, 2003
 * @author Joseph Ramsey
 */
class EvidenceWizardMultipleObs extends JPanel {
    private final UpdaterWrapper updaterWrapper;
    private final GraphWorkbench workbench;
    private final EvidenceEditorObs evidenceEditor;
    private JTextArea textArea = new JTextArea("Nothing to display");

    /**
     * This is the wizard for the BayesUpdateEditor class.  It allows you to add
     * and remove evidence, and to updater based on it.
     */
    public EvidenceWizardMultipleObs(final UpdaterWrapper updaterWrapper,
                                     final GraphWorkbench workbench) {
        if (updaterWrapper == null) {
            throw new NullPointerException();
        }

        if (workbench == null) {
            throw new NullPointerException();
        }

        // Components.
        this.updaterWrapper = updaterWrapper;
        this.workbench = workbench;

        workbench.setAllowDoubleClickActions(false);
        setBorder(new MatteBorder(10, 10, 10, 10, getBackground()));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        final JButton calcMarginalsAndJointButton =
                new JButton("Calculate Marginals and Joint");

        // Do Layout.
        final Box b0 = Box.createHorizontalBox();
        b0.add(new JLabel("<html>" +
                "Select a set of nodes (by holding down the shift key) whose" +
                "<br>marginals you would like to see given the evidence indicated" +
                "<br>above.  Click the 'Calculate Marginals' button to view" +
                "<br>marginals and log odds results."));
        b0.add(Box.createHorizontalGlue());
        add(b0);
        add(Box.createVerticalStrut(10));
        this.evidenceEditor = new EvidenceEditorObs(updaterWrapper.getBayesUpdater().getEvidence());
        getUpdaterWrapper().getParams().set("evidence", this.evidenceEditor.getEvidence());
        add(this.evidenceEditor);
        add(Box.createVerticalStrut(10));

        final Box b2 = Box.createHorizontalBox();
        b2.add(Box.createHorizontalGlue());
        b2.add(calcMarginalsAndJointButton);
        add(b2);
        add(Box.createVerticalGlue());

        // Add listeners.
        calcMarginalsAndJointButton.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final List<DisplayNode> selectedGraphNodes =
                        getWorkbench().getSelectedNodes();

                getUpdaterWrapper().getBayesUpdater().setEvidence(EvidenceWizardMultipleObs.this.evidenceEditor.getEvidence());

                final Graph updatedGraph = getUpdaterWrapper().getBayesUpdater().getManipulatedGraph();
                getWorkbench().setGraph(updatedGraph);

                final BayesIm manipulatedIm = getUpdaterWrapper()
                        .getBayesUpdater().getManipulatedBayesIm();

                final List<Node> selectedNodes = new LinkedList<>();

                for (final DisplayNode selectedGraphNode : selectedGraphNodes) {
                    final Node tetradNode = selectedGraphNode.getModelNode();
                    final String selectedNodeName = tetradNode.getName();
                    final Node selectedNode = updatedGraph.getNode(selectedNodeName);
                    // skip latent variables
                    if (selectedNode.getNodeType() == NodeType.MEASURED) {
                        selectedNodes.add(selectedNode);
                    }
                }

                for (final Node node : selectedNodes) {
                    getWorkbench().selectNode(node);
                }

                Collections.sort(selectedNodes, new Comparator<Node>() {
                    public int compare(final Node o1, final Node o2) {
                        final String name1 = o1.getName();
                        final String name2 = o2.getName();
                        return name1.compareTo(name2);
                    }
                });

                final JTextArea marginalsArea = new JTextArea();
                marginalsArea.setEditable(false);

                final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

                if (selectedNodes.size() == 0) {
                    marginalsArea.append("\nNo nodes selected.");
                } else {
                    appendMarginals(selectedNodes, marginalsArea, manipulatedIm,
                            nf);
                    appendJoint(selectedNodes, marginalsArea, manipulatedIm,
                            nf);
                }

                EvidenceWizardMultipleObs.this.textArea = marginalsArea;
                firePropertyChange("updateButtonPressed", null, null);
            }
        });
    }

    private void appendMarginals(final List<Node> selectedNodes,
                                 final JTextArea marginalsArea, final BayesIm manipulatedIm, final NumberFormat nf) {
        final BayesPm bayesPm = manipulatedIm.getBayesPm();

        marginalsArea.append("MARGINALS FOR SELECTED VARIABLES:\n");

        for (final Node selectedNode : selectedNodes) {
            marginalsArea.append(
                    "\nVariable " + selectedNode.getName() + ":\n");

            final int nodeIndex = manipulatedIm.getNodeIndex(selectedNode);

            for (int j = 0; j < bayesPm.getNumCategories(selectedNode); j++) {
                final double prob = getUpdaterWrapper().getBayesUpdater().getMarginal(nodeIndex, j);

                // identifiability returns -1 if the requested prob is unidentifiable
                if (prob < 0.0) {
                    marginalsArea.append("Category " +
                            bayesPm.getCategory(selectedNode, j) + ": p = " +
                            "Unidentifiable" + ",  log odds = " +
                            "*" + "\n");
                } else {
                    final double logOdds = Math.log(prob / (1. - prob));

                    marginalsArea.append("Category " +
                            bayesPm.getCategory(selectedNode, j) + ": p = " +
                            nf.format(prob) + ",  log odds = " +
                            nf.format(logOdds) + "\n");
                }
            }

        }
    }

    private void appendJoint(final List<Node> selectedNodes, final JTextArea marginalsArea,
                             final BayesIm manipulatedIm, final NumberFormat nf) {
        if (!getUpdaterWrapper().getBayesUpdater().isJointMarginalSupported()) {
            marginalsArea.append("\n\n(Calculation of joint not supported " +
                    "for this updater.)");
            return;
        }

        final BayesPm bayesPm = manipulatedIm.getBayesPm();
        final int numNodes = selectedNodes.size();
        final int[] dims = new int[numNodes];
        final int[] variables = new int[numNodes];
        int numRows = 1;

        for (int i = 0; i < numNodes; i++) {
            final Node node = selectedNodes.get(i);
            final int numCategories = bayesPm.getNumCategories(node);
            variables[i] = manipulatedIm.getNodeIndex(node);
            dims[i] = numCategories;
            numRows *= numCategories;
        }

        marginalsArea.append("\n\nJOINT OVER SELECTED VARIABLES:\n\n");

        for (int i = 0; i < numNodes; i++) {
            marginalsArea.append(selectedNodes.get(i) + "\t");
        }

        marginalsArea.append("Joint\tLog odds\n");

        for (int row = 0; row < numRows; row++) {
            final int[] values = getCategories(row, dims);
            final double prob = getUpdaterWrapper().getBayesUpdater().getJointMarginal(variables, values);

            marginalsArea.append("\n");

            for (int j = 0; j < numNodes; j++) {
                final Node node = selectedNodes.get(j);
                marginalsArea.append(bayesPm.getCategory(node, values[j]));
                marginalsArea.append("\t");
            }

            // identifiability returns -1 if the requested prob is unidentifiable
            if (prob < 0.0) {
                marginalsArea.append("Unidentifiable" + "\t");
                marginalsArea.append("*");
            } else {
                final double logOdds = Math.log(prob / (1. - prob));
                marginalsArea.append(nf.format(prob) + "\t");
                marginalsArea.append(nf.format(logOdds));
            }
        }
    }

    private int[] getCategories(int row, final int[] dims) {
        final int[] values = new int[dims.length];

        for (int i = dims.length - 1; i >= 0; i--) {
            values[i] = row % dims[i];
            row /= dims[i];
        }

        return values;
    }

    public BayesIm getBayesIM() {
        return getUpdaterWrapper().getBayesUpdater().getUpdatedBayesIm();
    }

    private UpdaterWrapper getUpdaterWrapper() {
        return this.updaterWrapper;
    }

    private GraphWorkbench getWorkbench() {
        return this.workbench;
    }

    public JTextArea getTextArea() {
        return this.textArea;
    }
}




