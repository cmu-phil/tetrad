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
import edu.cmu.tetrad.sem.SemEvidence;
import edu.cmu.tetrad.sem.SemUpdater;
import edu.cmu.tetradapp.util.SortingComboBox;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * Allows the user to choose a variable in a Bayes net and edit the parameters
 * associated with that variable.  Parameters are of the form
 * P(Node=value1|Parent1=value2, Parent2=value2,...); values for these
 * parameters are probabilities ranging from 0.0 to 1.0. For a given combination
 * of parent values for selectedNode N, the probabilities for the values of N
 * conditional on that combination of parent values must sum to 1.0
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public final class UpdatedSemImWizard extends JPanel {
    private SemEvidence evidence;
    private GraphWorkbench workbench;
    private SemUpdater semUpdater;

    /**
     * Last node selected.
     */
    private Node selectedNode;

    private JComboBox varNamesComboBox;
    private JComboBox varNamesComboBox2;
    private JPanel marginalsPanel;

    public UpdatedSemImWizard(SemUpdater semUpdater, GraphWorkbench workbench,
            int tab, Node selectedNode) {
        if (semUpdater == null) {
            throw new NullPointerException();
        }

        this.semUpdater = semUpdater;
        //        this.priorMarginals = new CptInvariantMarginalCalculator(semUpdater.getEstimateBayesIm(),
        //                new Evidence(semUpdater.getEstimateBayesIm()));

        this.selectedNode = selectedNode;

        this.evidence = semUpdater.getEvidence();
        this.workbench = workbench;
        this.workbench.setAllowDoubleClickActions(false);

        setLayout(new BorderLayout());

        // Set up components.
        this.varNamesComboBox = makeVarNamesDropdown();
        this.varNamesComboBox2 = makeVarNamesDropdown();

        Node modelNode = (Node) (varNamesComboBox.getSelectedItem());
        workbench.deselectAll();
        workbench.selectNode(modelNode);
        selectedNode = (Node) (varNamesComboBox.getSelectedItem());

        marginalsPanel = new JPanel();
        marginalsPanel.setLayout(new BorderLayout());
        marginalsPanel.add(createMarginalDisplay(selectedNode),
                BorderLayout.CENTER);

        JTabbedPane probsPane = new JTabbedPane(JTabbedPane.TOP);

        setupMarginalsDisplay(probsPane);

        tab = tab < probsPane.getTabCount() ? tab : 0;
        probsPane.setSelectedIndex(tab);

        add(probsPane, BorderLayout.CENTER);

        // Add listeners.
        varNamesComboBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Node node = (Node) (varNamesComboBox.getSelectedItem());
                setCurrentNode(node);
            }
        });

        varNamesComboBox2.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Node node = (Node) (varNamesComboBox2.getSelectedItem());
                setCurrentNode(node);
            }
        });

        workbench.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent e) {
                if (e.getPropertyName().equals("selectedNodes")) {
                    List selection = (List) (e.getNewValue());

                    if (selection.size() == 1) {
                        Node node = (Node) (selection.get(0));
                        varNamesComboBox.setSelectedItem(node);
                    }
                }
            }
        });
    }

    private void setupMarginalsDisplay(JTabbedPane probsPane) {
        probsPane.add("Marginal Probabilities", marginalsPanel);
        probsPane.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                JTabbedPane tabbedPane = (JTabbedPane) e.getSource();
                int tab = tabbedPane.getSelectedIndex();
                firePropertyChange("updatedBayesImWizardTab", null, tab);
            }
        });
    }

    private JComboBox makeVarNamesDropdown() {
        JComboBox varNamesComboBox = new SortingComboBox() {
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };

        varNamesComboBox.setBackground(Color.white);

        Graph graph = semUpdater.getManipulatedGraph();

        for (Object o : graph.getNodes()) {
            varNamesComboBox.addItem(o);
        }

        if (selectedNode != null) {
            varNamesComboBox.setSelectedItem(selectedNode);
        }
        else {
            varNamesComboBox.setSelectedIndex(0);
            this.selectedNode = (Node) varNamesComboBox.getSelectedItem();
        }

        return varNamesComboBox;
    }

    private void addListOfEvidence(Box verticalBox) {
        boolean foundACondition = false;

        for (int i = 0; i < evidence.getNumNodes(); i++) {
            foundACondition = true;

            Node node = evidence.getNode(i);
            Box c = Box.createHorizontalBox();
            c.add(Box.createRigidArea(new Dimension(30, 1)));
            StringBuilder buf = new StringBuilder();

            buf.append("<html>").append(node.getName()).append(" = ");
//            boolean listedOneAlready = false;
//
//            for (int j = 0; j < evidence.getNumSplits(i); j++) {
//                if (evidence.getProposition().isAllowed(i, j)) {
//                    if (listedOneAlready) {
//                        buf.append(" <i>OR</i>  ");
//                    }
//
//                    SemIm manipulatedSemIm = semUpdater.getManipulatedSemIm();
//                    String valueName = manipulatedSemIm.getBayesPm()
//                            .getCategory(node, j);
//                    buf.append(valueName);
//                    listedOneAlready = true;
//                }
//            }

            buf.append("</html>");

            c.add(new JLabel(buf.toString()));
            c.add(Box.createHorizontalGlue());
            verticalBox.add(c);
        }

        if (!foundACondition) {
            Box e = Box.createHorizontalBox();
            e.add(Box.createRigidArea(new Dimension(30, 1)));
            e.add(new JLabel("--No Evidence--"));
            e.add(Box.createHorizontalGlue());
            verticalBox.add(e);
        }
    }

    private JComponent createMarginalDisplay(Node node) {
        if (node == null) {
            throw new NullPointerException();
        }

        Box marginalBox = Box.createVerticalBox();

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Marginal probabilities for variable "));
        b1.add(varNamesComboBox2);
        b1.add(new JLabel(", updated"));
        b1.add(Box.createHorizontalGlue());
        marginalBox.add(b1);

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("to reflect the following evidence:"));
        b2.add(Box.createHorizontalGlue());
        marginalBox.add(b2);

        marginalBox.add(Box.createRigidArea(new Dimension(1, 10)));
        addListOfEvidence(marginalBox);
        marginalBox.add(Box.createRigidArea(new Dimension(1, 20)));

//        Node node1 = semUpdater.getEstIm().getIndex(node.getName());
//        int nodeIndex = semUpdater.getEstIm().getNodeIndex(node1);
//
//        double[] priorMeans =
//        double[] updatedMeans = calculateUpdatedMarginals(nodeIndex);
//
//        Font font = getFont();
//        FontMetrics fontMetrics = getFontMetrics(font);
//
//        Font smallFont = new Font("Dialog", Font.BOLD, 10);
//        int maxWidth = 0;
//
//        for (int i = 0;
//             i < semUpdater.getVariableSource().getNumColumns(nodeIndex); i++) {
//            String value = semUpdater.getVariableSource().getBayesPm().getCategory(
//                    node, i);
//            String label = node + " = " + value;
//            int width = fontMetrics.stringWidth(label);
//            if (width > maxWidth) {
//                maxWidth = width;
//            }
//        }
//
//        for (int i = 0;
//             i < semUpdater.getVariableSource().getNumColumns(nodeIndex); i++) {
//            String value = semUpdater.getVariableSource().getBayesPm().getCategory(
//                    node, i);
//            Box c = Box.createHorizontalBox();
//            c.add(Box.createRigidArea(new Dimension(10, 1)));
//
//            String label = node + " = " + value;
//            int width = fontMetrics.stringWidth(label);
//
//            c.add(Box.createRigidArea(new Dimension(maxWidth - width, 0)));
//            c.add(new JLabel(label));
//
//            final int priorWidth = (int) (150.0 * priorMeans[i]);
//            final int updatedWidth = (int) (150.0 * updatedMeans[i]);
//
//            JPanel priorBar = makeBar(priorWidth, 6, Color.BLUE.brighter());
//            JPanel updatedBar = makeBar(updatedWidth, 6, Color.RED);
//
//            c.add(Box.createRigidArea(new Dimension(10, 1)));
//
//            Box d = Box.createVerticalBox();
//
//            Box e1 = Box.createHorizontalBox();
//            e1.add(priorBar);
//            e1.add(Box.createHorizontalGlue());
//
//            Box e2 = Box.createHorizontalBox();
//            e2.add(updatedBar);
//            e2.add(Box.createHorizontalGlue());
//
//            d.add(e1);
//            d.add(Box.createVerticalStrut(2));
//            d.add(e2);
//
//            c.add(d);
//            c.add(Box.createHorizontalGlue());
//
//            Box f = Box.createVerticalBox();
//            Box g1 = Box.createHorizontalBox();
//            Box g2 = Box.createHorizontalBox();
//
//            JLabel priorValueLabel = new JLabel(nf.format(priorMeans[i]));
//            JLabel marginalValueLabel = new JLabel(
//                    nf.format(updatedMeans[i]));
//            priorValueLabel.setFont(smallFont);
//
//            g1.add(Box.createHorizontalGlue());
//            g1.add(priorValueLabel);
//
//            g2.add(Box.createHorizontalGlue());
//            g2.add(marginalValueLabel);
//
//            f.add(g1);
//            f.add(g2);
//
//            c.add(f);
//            marginalBox.add(c);
//            marginalBox.add(Box.createRigidArea(new Dimension(1, 5)));
//        }

        marginalBox.add(Box.createGlue());
        return marginalBox;
    }

    /**
     * Sets the getModel display to reflect the stored values of the getModel
     * selectedNode.
     */
    private void setCurrentNode(final Node node) {
        Window owner = (Window) getTopLevelAncestor();

        if (owner == null) {
            setCurrentNodeSub(node);
        }
        else {
            new WatchedProcess(owner) {
                public void watch() {
                    setCurrentNodeSub(node);
                }
            };
        }
    }

    private void setCurrentNodeSub(Node node) {
        if (node == selectedNode) {
            return;
        }

        selectedNode = node;

        getWorkbench().deselectAll();
        getWorkbench().selectNode(selectedNode);

        if (varNamesComboBox.getSelectedItem() != node) {
            varNamesComboBox.setSelectedItem(node);
        }

        if (varNamesComboBox2.getSelectedItem() != node) {
            varNamesComboBox2.setSelectedItem(node);
        }

        marginalsPanel.removeAll();
        marginalsPanel.add(createMarginalDisplay(node), BorderLayout.CENTER);
        marginalsPanel.revalidate();
        marginalsPanel.repaint();
    }

    private GraphWorkbench getWorkbench() {
        return workbench;
    }

    public Node getSelectedNode() {
        return selectedNode;
    }
}



