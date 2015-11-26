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

import edu.cmu.tetrad.bayes.DirichletBayesIm;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.util.SortingComboBox;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import javax.swing.table.TableCellEditor;
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
 * of parent values for node N, the probabilities for the values of N
 * conditional on that combination of parent values must sum to 1.0
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public final class DirichletBayesImCountsWizard extends JPanel {
    private DirichletBayesIm bayesIm;
    private JComboBox varNamesComboBox;
    private GraphWorkbench workbench;
    private DirichletBayesImNodeCountsTable editingTable;
    private JPanel tablePanel;
    private boolean showProbs = true;

    public DirichletBayesImCountsWizard(DirichletBayesIm bayesIm,
            GraphWorkbench workbench) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        if (workbench == null) {
            throw new NullPointerException();
        }

        workbench.setAllowDoubleClickActions(false);
        setBorder(new MatteBorder(10, 10, 10, 10, getBackground()));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setFont(new Font("SanSerif", Font.BOLD, 12));

        // Set up components.
        this.varNamesComboBox = createVarNamesComboBox(bayesIm);
        workbench.scrollWorkbenchToNode(
                (Node) (varNamesComboBox.getSelectedItem()));

        JButton nextButton = new JButton("Next");
        nextButton.setMnemonic('N');

        Node node = (Node) (varNamesComboBox.getSelectedItem());
        editingTable = new DirichletBayesImNodeCountsTable(node, bayesIm);
        editingTable.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("editorValueChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("editorValueChanged", null, null);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(editingTable);
        scroll.setPreferredSize(new Dimension(0, 150));
        tablePanel = new JPanel();
        tablePanel.setLayout(new BorderLayout());
        tablePanel.add(scroll, BorderLayout.CENTER);
        editingTable.grabFocus();

        // Do Layout.
        Box b1 = Box.createHorizontalBox();

        b1.add(new JLabel("Table of Dirichlet parameters (pseudocounts) for "));
        b1.add(varNamesComboBox);
        b1.add(new JLabel(" conditional"));
        b1.add(Box.createHorizontalGlue());
        add(b1);
        add(Box.createVerticalStrut(1));

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel(
                "on combinations of its parent values (with total pseudocount"));
        b2.add(Box.createHorizontalGlue());
        add(b2);
        add(Box.createVerticalStrut(5));

        Box b3 = Box.createHorizontalBox();
        b3.add(new JLabel("in row shown):"));
        b3.add(Box.createHorizontalGlue());
        add(b3);
        add(Box.createVerticalStrut(10));

        Box b4 = Box.createHorizontalBox();
        b4.add(tablePanel, BorderLayout.CENTER);

        add(b4);

        // Add listeners.
        varNamesComboBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Node node = (Node) (varNamesComboBox.getSelectedItem());
                getWorkbench().scrollWorkbenchToNode(node);
                setCurrentNode(node);
            }
        });

        nextButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int current = varNamesComboBox.getSelectedIndex();
                int max = varNamesComboBox.getItemCount();

                ++current;

                if (current == max) {
                    JOptionPane.showMessageDialog(
                            DirichletBayesImCountsWizard.this,
                            "There are no more variables.");
                }

                int set = (current < max) ? current : 0;

                varNamesComboBox.setSelectedIndex(set);
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

        this.bayesIm = bayesIm;
        this.workbench = workbench;
    }

    private JComboBox createVarNamesComboBox(DirichletBayesIm bayesIm) {
        JComboBox varNamesComboBox = new SortingComboBox() {
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };

        varNamesComboBox.setBackground(Color.white);

        Graph graph = bayesIm.getBayesPm().getDag();

        for (Object o : graph.getNodes()) {
            varNamesComboBox.addItem(o);
        }

        varNamesComboBox.setSelectedIndex(0);

        return varNamesComboBox;
    }

    /**
     * Sets the getModel display to reflect the stored values of the getModel
     * node.
     */
    private void setCurrentNode(Node node) {
        TableCellEditor cellEditor = editingTable.getCellEditor();

        if (cellEditor != null) {
            cellEditor.cancelCellEditing();
        }

        editingTable = new DirichletBayesImNodeCountsTable(node, getBayesIm());
        editingTable.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("editorValueChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("editorValueChanged", null, null);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(editingTable);
        scroll.setPreferredSize(new Dimension(0, 150));

        tablePanel.removeAll();
        tablePanel.add(scroll, BorderLayout.CENTER);
        tablePanel.revalidate();
        tablePanel.repaint();

        editingTable.grabFocus();
    }

    private DirichletBayesIm getBayesIm() {
        return bayesIm;
    }

    private GraphWorkbench getWorkbench() {
        return workbench;
    }

    public boolean isShowProbs() {
        return showProbs;
    }

    public void setShowProbs(boolean showProbs) {
        this.showProbs = showProbs;
    }
}






