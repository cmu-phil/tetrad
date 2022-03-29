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

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.util.StringTextField;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A wizard to let the user go through a workbench systematically and set the
 * number of categories for each node along with the names of each category.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
final class BayesPmEditorWizard extends JPanel {

    /**
     * The BayesPm model being edited.
     */
    private final BayesPm bayesPm;

    /**
     * Lets the user select the variable they want to edit.
     */
    private JComboBox<Node> variableChooser;

    /**
     * True iff the editing of measured variables is allowed.
     */
    private boolean editingMeasuredVariablesAllowed;

    /**
     * True iff the editing of latent variables is allowed.
     */
    private boolean editingLatentVariablesAllowed;

    /**
     * Lets the user see graphically which variable is being edited and click to
     * another variable.
     */
    private final GraphWorkbench workbench;

    /**
     * A reference to the category editor.
     */
    private final CategoryEditor categoryEditor;

    /**
     * A reference to the spinner model.
     */
    private SpinnerNumberModel spinnerModel;

    /**
     * The preset strings that will be used.
     */
    private final String[][] presetStrings = {{"Low", "High"},
            {"Low", "Medium", "High"}, {"On", "Off"}, {"Yes", "No"}};

    /**
     * ?
     */
    private List copiedCategories;

    /**
     * ?
     */
    private final Map<Object, Integer> labels = new HashMap<>();

    /**
     * ?
     */
    private JSpinner numCategoriesSpinner;

    /**
     *
     */
    private JMenu presetMenu;

    /**
     * This is the wizard for the PMEditor class. Its function is to allow the
     * user to enter, for each variable in the associated Graph, the number of
     * categories it may take on and the string names for each of those
     * categories.
     */
    public BayesPmEditorWizard(BayesPm bayesPm, GraphWorkbench workbench) {
        if (bayesPm == null) {
            throw new NullPointerException();
        }

        if (workbench == null) {
            throw new NullPointerException();
        }

        this.bayesPm = bayesPm;
        this.workbench = workbench;

        this.workbench().setAllowDoubleClickActions(false);

        // Construct components.
        this.createVariableChooser(this.getBayesPm(), this.workbench());

        JButton nextButton = new JButton("Next");
        nextButton.setMnemonic('N');

        int numCategories = this.numCategories();

        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Edit categories for: "));
        b2.add(variableChooser);
        b2.add(nextButton);
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(10));

        if (numCategories != 0) {
            spinnerModel = new SpinnerNumberModel(numCategories, 2, 1000, 1);
            numCategoriesSpinner = new JSpinner(spinnerModel) {

                private static final long serialVersionUID = -7932603602816371347L;

                @Override
                public Dimension getMaximumSize() {
                    return this.getPreferredSize();
                }

            };
            numCategoriesSpinner.setFont(new Font("Serif", Font.PLAIN, 12));
            numCategoriesSpinner.addChangeListener((e) -> {
                JSpinner spinner = (JSpinner) e.getSource();
                SpinnerNumberModel model
                        = (SpinnerNumberModel) spinner.getModel();
                this.setNumCategories(model.getNumber().intValue());
            });

            Box b3 = Box.createHorizontalBox();
            b3.add(new JLabel("Number of categories:  "));
            b3.add(numCategoriesSpinner);
            b3.add(Box.createHorizontalGlue());
            b1.add(b3);

            b1.add(Box.createVerticalStrut(10));
        }

        categoryEditor = new CategoryEditor(bayesPm, this.getNode());

        Box b4 = Box.createHorizontalBox();
        b4.add(new JLabel("Category names: "));
        b4.add(Box.createHorizontalGlue());
        b1.add(b4);

        b1.add(Box.createVerticalStrut(10));

        Box b5 = Box.createHorizontalBox();
        b5.add(categoryEditor);
        b1.add(b5);

        Box b6 = Box.createHorizontalBox();
        b6.add(Box.createRigidArea(new Dimension(400, 0)));
        b1.add(b6);
        b1.add(Box.createVerticalGlue());

        JMenuBar menuBar = this.createMenuBar();

        b1.setBorder(new EmptyBorder(10, 10, 0, 10));

        this.setLayout(new BorderLayout());
        this.add(b1, BorderLayout.CENTER);
        this.add(menuBar, BorderLayout.NORTH);

        workbench.addPropertyChangeListener((evt) -> {
            if (evt.getPropertyName().equals("selectedNodes")) {
                List selection = (List) (evt.getNewValue());
                if (selection.size() == 1) {
                    Node node = (Node) (selection.get(0));
                    variableChooser.setSelectedItem(node);
                }
            }
        });

        variableChooser.addActionListener((e) -> {
            Node n = (Node) (variableChooser.getSelectedItem());
            this.workbench().scrollWorkbenchToNode(n);
            this.setNode(n);
        });

        nextButton.addActionListener((e) -> {
            int current = variableChooser.getSelectedIndex();
            int max = variableChooser.getItemCount();

            ++current;

            if (current == max) {
                JOptionPane.showMessageDialog(this,
                        "There are no more variables.");
            }

            int set = (current < max) ? current : 0;

            variableChooser.setSelectedIndex(set);
        });

        this.enableByNodeType();
    }

    private void enableByNodeType() {
        if (!this.isEditingMeasuredVariablesAllowed() && categoryEditor.getNode().getNodeType() == NodeType.MEASURED) {
            this.setEnabled(false);
        } else
            this.setEnabled(this.isEditingLatentVariablesAllowed() || categoryEditor.getNode().getNodeType() != NodeType.LATENT);
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        numCategoriesSpinner.setEnabled(enabled);
        categoryEditor.setEnabled(enabled);
    }

    private void setNumCategories(int numCategories) {
        categoryEditor.setNumCategories(numCategories);
        this.firePropertyChange("modelChanged", null, null);
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu presetMenu = new JMenu("Presets");
        this.presetMenu = presetMenu;
        menuBar.add(presetMenu);

        for (int i = 0; i < presetStrings.length; i++) {
            StringBuilder buf = new StringBuilder();

            for (int j = 0; j < presetStrings[i].length; j++) {
                buf.append(presetStrings[i][j]);

                if (j < presetStrings[i].length - 1) {
                    buf.append("-");
                }
            }

            Action action = new IndexedAction(buf.toString(), i) {

                private static final long serialVersionUID = 5052478563546335636L;

                @Override
                public void actionPerformed(ActionEvent e) {
                    BayesPmEditorWizard.this.setCategories(Arrays.asList(presetStrings[this.getIndex()]));
                }

            };

            presetMenu.add(action);
        }

        presetMenu.addSeparator();

        Action sequence = new AbstractAction("x1, x2, x3, ...") {

            private static final long serialVersionUID = 4377386270269629176L;

            @Override
            public void actionPerformed(ActionEvent e) {
                List categories = new ArrayList();
                String ret = JOptionPane.showInputDialog(
                        JOptionUtils.centeringComp(),
                        "Please input a prefix string for the sequence: ",
                        "category");

                int numCategories = BayesPmEditorWizard.this.numCategories();

                for (int i = 0; i < numCategories; i++) {
                    categories.add(ret + (i + 1));
                }

                BayesPmEditorWizard.this.setCategories(categories);
            }
        };

        presetMenu.add(sequence);

        JMenu transfer = new JMenu("Transfer");
        JMenuItem copy = new JMenuItem("Copy categories");
        JMenuItem paste = new JMenuItem("Paste categories");

        copy.addActionListener((e) -> {
            this.copyCategories();

            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "<html>"
                            + "The categories for this node have been copied; to transfer "
                            + "<br>these categories, choose another node and paste. You may"
                            + "<br>paste multiple times." + "</html>");
        });

        paste.addActionListener((e) -> {
            this.pasteCategories();
        });

        copy.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
        paste.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK));

        transfer.add(copy);
        transfer.add(paste);

        menuBar.add(transfer);

        return menuBar;
    }

    private void copyCategories() {
        Node node = (Node) variableChooser.getSelectedItem();
        DiscreteVariable variable
                = (DiscreteVariable) bayesPm.getVariable(node);
        copiedCategories = variable.getCategories();
    }

    private void pasteCategories() {
        if (copiedCategories != null) {
            this.setCategories(copiedCategories);
        }
    }

    private void setCategories(List categories) {
        categoryEditor.setCategories(categories);
        spinnerModel.setValue(categories.size());
        this.firePropertyChange("modelChanged", null, null);
    }

    private void createVariableChooser(BayesPm bayesPm, GraphWorkbench workbench) {
        variableChooser = new JComboBox<>();
        variableChooser.setBackground(Color.white);

        Graph graphModel = bayesPm.getDag();

        List<Node> nodes = graphModel.getNodes().stream().collect(Collectors.toList());
        Collections.sort(nodes);
        nodes.forEach(variableChooser::addItem);

        if (variableChooser.getItemCount() > 0) {
            variableChooser.setSelectedIndex(0);
        }

        workbench.scrollWorkbenchToNode((Node) variableChooser.getSelectedItem());
    }

    private void setNode(Node node) {
        categoryEditor.setNode(node);
        int numCategories = bayesPm.getNumCategories(node);
        spinnerModel.setValue(numCategories);
        this.firePropertyChange("modelChanged", null, null);
        this.enableByNodeType();
    }

    private GraphWorkbench workbench() {
        return workbench;
    }

    private int numCategories() {
        return this.getBayesPm().getNumCategories(this.getNode());
    }

    private BayesPm getBayesPm() {
        return bayesPm;
    }

    private Node getNode() {
        Node selectedItem = (Node) variableChooser.getSelectedItem();

        if (selectedItem == null) {
            throw new NullPointerException();
        }

        return selectedItem;
    }

    private boolean isEditingMeasuredVariablesAllowed() {
        return editingMeasuredVariablesAllowed;
    }

    public void setEditingMeasuredVariablesAllowed(boolean editingMeasuredVariablesAllowed) {
        this.editingMeasuredVariablesAllowed = editingMeasuredVariablesAllowed;
        this.setNode(categoryEditor.getNode());

        presetMenu.setEnabled(editingMeasuredVariablesAllowed);
    }

    private boolean isEditingLatentVariablesAllowed() {
        return editingLatentVariablesAllowed;
    }

    public void setEditingLatentVariablesAllowed(boolean editingLatentVariablesAllowed) {
        this.editingLatentVariablesAllowed = editingLatentVariablesAllowed;
        this.setNode(categoryEditor.getNode());

        if (!editingLatentVariablesAllowed) {
            presetMenu.setEnabled(false);
        }
    }

    /**
     * Edits categories for each variable of a Bayes PM.
     *
     * @author Joseph Ramsey
     */
    class CategoryEditor extends JPanel {

        private static final long serialVersionUID = -7488118975131239436L;

        private final BayesPm bayesPm;
        private Node node;
        private StringTextField[] categoryFields;
        private final LinkedList focusTraveralOrder = new LinkedList();

        public CategoryEditor(BayesPm bayesPm, Node node) {
            if (bayesPm == null) {
                throw new NullPointerException();
            }

            this.setLayout(new BorderLayout());

            if (node == null) {
//                return;
                throw new NullPointerException();
            }

            this.bayesPm = bayesPm;
            this.node = node;

            this.setNumCategories(this.numCategories());
        }

        public void setNumCategories(int numCategories) {
            this.removeAll();
            JComponent categoryFieldsPanel
                    = this.createCategoryFieldsPanel(numCategories);
            this.add(categoryFieldsPanel, BorderLayout.CENTER);
            this.revalidate();
            this.repaint();
            this.firePropertyChange("modelChanged", null, null);
        }

        public BayesPm getBayesPm() {
            return bayesPm;
        }

        private JComponent createCategoryFieldsPanel(int numCategories) {
            if (numCategories != bayesPm.getNumCategories(this.getNode())) {
                bayesPm.setNumCategories(this.getNode(), numCategories);
            }

            Box panel = Box.createVerticalBox();

            this.createCategoryFields();

            for (int i = 0; i < bayesPm.getNumCategories(this.getNode()); i++) {
                Box row = Box.createHorizontalBox();
                row.add(Box.createRigidArea(new Dimension(10, 0)));
                row.add(new JLabel((i + 1) + "."));
                row.add(Box.createRigidArea(new Dimension(4, 0)));
                row.add(categoryFields[i]);

                row.add(Box.createHorizontalGlue());
                panel.add(row);
            }

            this.setLayout(new BorderLayout());
            this.add(panel, BorderLayout.CENTER);

            this.setFocusTraversalPolicy(new FocusTraversalPolicy() {
                @Override
                public Component getComponentAfter(Container focusCycleRoot,
                                                   Component aComponent) {
                    int index = focusTraveralOrder.indexOf(aComponent);
                    int size = focusTraveralOrder.size();

                    if (index != -1) {
                        return (Component) focusTraveralOrder.get(
                                (index + 1) % size);
                    } else {
                        return this.getFirstComponent(focusCycleRoot);
                    }
                }

                @Override
                public Component getComponentBefore(Container focusCycleRoot,
                                                    Component aComponent) {
                    int index = focusTraveralOrder.indexOf(aComponent);
                    int size = focusTraveralOrder.size();

                    if (index != -1) {
                        return (Component) focusTraveralOrder.get(
                                (index - 1) % size);
                    } else {
                        return this.getFirstComponent(focusCycleRoot);
                    }
                }

                @Override
                public Component getFirstComponent(Container focusCycleRoot) {
                    return (Component) focusTraveralOrder.getFirst();
                }

                @Override
                public Component getLastComponent(Container focusCycleRoot) {
                    return (Component) focusTraveralOrder.getLast();
                }

                @Override
                public Component getDefaultComponent(Container focusCycleRoot) {
                    return this.getFirstComponent(focusCycleRoot);
                }
            });

            this.setFocusCycleRoot(true);

            return panel;
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            for (StringTextField field : categoryFields) {
                field.setEnabled(enabled);
            }
        }

        private void createCategoryFields() {
            categoryFields = new StringTextField[this.numCategories()];

            for (int i = 0; i < this.numCategories(); i++) {
                categoryFields[i] = new StringTextField(this.category(i), 10);
                StringTextField _field = categoryFields[i];

                categoryFields[i].setFilter((String value, String oldValue) -> {
                    if (labels.get(_field) != null) {
                        int index = labels.get(_field);

                        if (value == null) {
                            value = this.category(index);
                        }

                        for (int j = 0; j < this.numCategories(); j++) {
                            if (j != index && this.category(j).equals(value)) {
                                value = this.category(index);
                            }
                        }

                        this.setCategory(index, value);
                    }

                    return value;
                });

                labels.put(categoryFields[i], i);
                focusTraveralOrder.add(categoryFields[i]);
            }
        }

        private int numCategories() {
            return bayesPm.getNumCategories(this.getNode());
        }

        private String category(int index) {
            return bayesPm.getCategory(this.getNode(), index);
        }

        private void setCategory(int index, String value) {
            DiscreteVariable variable
                    = (DiscreteVariable) bayesPm.getVariable(this.getNode());
            List<String> categories = new ArrayList<>(variable.getCategories());
            categories.set(index, value);
            bayesPm.setCategories(node, categories);

            this.firePropertyChange("modelChanged", null, null);
        }

        public void setNode(Node node) {
            for (int i = 0; i < this.numCategories(); i++) {
                categoryFields[i].setValue(categoryFields[i].getText());
            }

            this.node = node;
            this.setNumCategories(bayesPm.getNumCategories(node));
        }

        public void setCategories(List categories) {
            if (categories == null) {
                throw new NullPointerException();
            }

            if (categories.size() < 2) {
                throw new IllegalArgumentException(
                        "Number of categories must be" + " >= 2: "
                                + categories.size());
            }

            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i) == null) {
                    throw new NullPointerException();
                }
            }

            this.removeAll();
            JComponent categoryFieldsPanel
                    = this.createCategoryFieldsPanel(categories.size());

            for (int i = 0; i < categories.size(); i++) {
                categoryFields[i].setValue((String) categories.get(i));
            }

            this.add(categoryFieldsPanel, BorderLayout.CENTER);
            this.revalidate();
            this.repaint();
        }

        public Node getNode() {
            return node;
        }
    }

    /**
     * The actionPerformed method is still abstract.
     */
    abstract static class IndexedAction extends AbstractAction {

        private static final long serialVersionUID = -8261331986030513841L;

        private final int index;

        public IndexedAction(String name, int index) {
            super(name);
            this.index = index;
        }

        public int getIndex() {
            return index;
        }

    }

}
