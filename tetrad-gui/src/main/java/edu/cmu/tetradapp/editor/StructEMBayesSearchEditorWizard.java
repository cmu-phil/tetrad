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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.util.SortingComboBox;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * Allows the user to choose a variable in a Bayes net and edit the parameters associated with that variable. Parameters
 * are of the form P(Node=value1|Parent1=value2, Parent2=value2,...); values for these parameters are probabilities
 * ranging from 0.0 to 1.0. For a given combination of parent values for node N, the probabilities for the values of N
 * conditional on that combination of parent values must sum to 1.0
 *
 * @author josephramsey
 * @author Frank Wimberly adapted for EM Bayes estimation and Structural EM bayes estimation.
 */
final class StructEMBayesSearchEditorWizard extends JPanel {
    private final BayesIm bayesIm;
    private final JComboBox varNamesComboBox;
    private final GraphWorkbench workbench;
    private final JPanel tablePanel;
    private BayesEstimatorNodeEditingTable editingTable;

    /**
     * <p>Constructor for StructEMBayesSearchEditorWizard.</p>
     *
     * @param bayesIm   a {@link edu.cmu.tetrad.bayes.BayesIm} object
     * @param workbench a {@link edu.cmu.tetradapp.workbench.GraphWorkbench} object
     */
    public StructEMBayesSearchEditorWizard(BayesIm bayesIm,
                                           GraphWorkbench workbench) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        if (workbench == null) {
            throw new NullPointerException();
        }

        workbench.setAllowDoubleClickActions(false);
        setBorder(new MatteBorder(10, 10, 10, 10, getBackground()));

        setFont(new Font("SanSerif", Font.BOLD, 12));

        // Set up components.
        this.varNamesComboBox = createVarNamesComboBox(bayesIm);
        workbench.scrollWorkbenchToNode(
                (Node) (this.varNamesComboBox.getSelectedItem()));

        JButton nextButton = new JButton("Next");
        nextButton.setMnemonic('N');

        Node node = (Node) (this.varNamesComboBox.getSelectedItem());
        this.editingTable = new BayesEstimatorNodeEditingTable(node, bayesIm);
        this.editingTable.addPropertyChangeListener(evt -> {
            if ("editorValueChanged".equals(evt.getPropertyName())) {
                firePropertyChange("editorValueChanged", null, null);
            }
        });

        JScrollPane scroll = new JScrollPane(this.editingTable);
        scroll.setPreferredSize(new Dimension(0, 150));
        this.tablePanel = new JPanel();
        this.tablePanel.setLayout(new BorderLayout());
        this.tablePanel.add(scroll, BorderLayout.CENTER);
        this.editingTable.grabFocus();

        // Do Layout.
        setLayout(new BorderLayout());
        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Probability table for values of "));
        b2.add(this.varNamesComboBox);
        b2.add(new JLabel(" conditional on values of its"));
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);

        Box b3 = Box.createHorizontalBox();
        b3.add(new JLabel("parents:"));
        b3.add(Box.createHorizontalGlue());
        b1.add(b3);

        Box b4 = Box.createHorizontalBox();
        b4.add(this.tablePanel, BorderLayout.CENTER);
        b1.add(b4);

        Box b5 = Box.createHorizontalBox();
        b5.add(new JLabel("Asterisks in table indicate undefined values."));
        b5.add(Box.createHorizontalGlue());
        b1.add(b5);

        b1.add(Box.createVerticalStrut(15));
        add(b1, BorderLayout.CENTER);

        // Add listeners.
        this.varNamesComboBox.addActionListener(e -> {
            Node node1 = (Node) (StructEMBayesSearchEditorWizard.this.varNamesComboBox.getSelectedItem());
            getWorkbench().scrollWorkbenchToNode(node1);
            setCurrentNode(node1);
        });

        nextButton.addActionListener(e -> {
            int current = StructEMBayesSearchEditorWizard.this.varNamesComboBox.getSelectedIndex();
            int max = StructEMBayesSearchEditorWizard.this.varNamesComboBox.getItemCount();

            ++current;

            if (current == max) {
                JOptionPane.showMessageDialog(
                        StructEMBayesSearchEditorWizard.this,
                        "There are no more variables.");
            }

            int set = (current < max) ? current : 0;

            StructEMBayesSearchEditorWizard.this.varNamesComboBox.setSelectedIndex(set);
        });

        workbench.addPropertyChangeListener(e -> {
            if (e.getPropertyName().equals("selectedNodes")) {
                List selection = (List) (e.getNewValue());

                if (selection.size() == 1) {
                    Node node12 = (Node) (selection.get(0));
                    StructEMBayesSearchEditorWizard.this.varNamesComboBox.setSelectedItem(node12);
                }
            }
        });

        this.bayesIm = bayesIm;
        this.workbench = workbench;
    }

    private JComboBox createVarNamesComboBox(BayesIm bayesIm) {
        JComboBox varNamesComboBox = new SortingComboBox() {
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };

        varNamesComboBox.setBackground(Color.white);

        Graph graph = bayesIm.getBayesPm().getDag();

        for (Node node : graph.getNodes()) {
            varNamesComboBox.addItem(node);
        }

        varNamesComboBox.setSelectedIndex(0);

        return varNamesComboBox;
    }

    /**
     * Sets the getModel display to reflect the stored values of the getModel node.
     */
    private void setCurrentNode(Node node) {
        TableCellEditor cellEditor = this.editingTable.getCellEditor();

        if (cellEditor != null) {
            cellEditor.cancelCellEditing();
        }

        this.editingTable = new BayesEstimatorNodeEditingTable(node, getBayesIm());
        this.editingTable.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("editorValueChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("editorValueChanged", null, null);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(this.editingTable);
        scroll.setPreferredSize(new Dimension(0, 150));

        this.tablePanel.removeAll();
        this.tablePanel.add(scroll, BorderLayout.CENTER);
        this.tablePanel.revalidate();
        this.tablePanel.repaint();

        this.editingTable.grabFocus();
    }

    private BayesIm getBayesIm() {
        return this.bayesIm;
    }

    private GraphWorkbench getWorkbench() {
        return this.workbench;
    }
}






