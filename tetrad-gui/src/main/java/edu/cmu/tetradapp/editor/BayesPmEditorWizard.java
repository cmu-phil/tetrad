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
import java.awt.event.InputEvent;
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

        workbench().setAllowDoubleClickActions(false);

        // Construct components.
        createVariableChooser(getBayesPm(), workbench());

        JButton nextButton = new JButton("Next");
        nextButton.setMnemonic('N');

        int numCategories = numCategories();

        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Edit categories for: "));
        b2.add(this.variableChooser);
        b2.add(nextButton);
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(10));

        if (numCategories != 0) {
            this.spinnerModel = new SpinnerNumberModel(numCategories, 2, 1000, 1);
            this.numCategoriesSpinner = new JSpinner(this.spinnerModel) {

                private static final long serialVersionUID = -7932603602816371347L;

                @Override
                public Dimension getMaximumSize() {
                    return getPreferredSize();
                }

            };
            this.numCategoriesSpinner.setFont(new Font("Serif", Font.PLAIN, 12));
            this.numCategoriesSpinner.addChangeListener((e) -> {
                JSpinner spinner = (JSpinner) e.getSource();
                SpinnerNumberModel model
                        = (SpinnerNumberModel) spinner.getModel();
                setNumCategories(model.getNumber().intValue());
            });

            Box b3 = Box.createHorizontalBox();
            b3.add(new JLabel("Number of categories:  "));
            b3.add(this.numCategoriesSpinner);
            b3.add(Box.createHorizontalGlue());
            b1.add(b3);

            b1.add(Box.createVerticalStrut(10));
        }

        this.categoryEditor = new CategoryEditor(bayesPm, getNode());

        Box b4 = Box.createHorizontalBox();
        b4.add(new JLabel("Category names: "));
        b4.add(Box.createHorizontalGlue());
        b1.add(b4);

        b1.add(Box.createVerticalStrut(10));

        Box b5 = Box.createHorizontalBox();
        b5.add(this.categoryEditor);
        b1.add(b5);

        Box b6 = Box.createHorizontalBox();
        b6.add(Box.createRigidArea(new Dimension(400, 0)));
        b1.add(b6);
        b1.add(Box.createVerticalGlue());

        JMenuBar menuBar = createMenuBar();

        b1.setBorder(new EmptyBorder(10, 10, 0, 10));

        setLayout(new BorderLayout());
        add(b1, BorderLayout.CENTER);
        add(menuBar, BorderLayout.NORTH);

        workbench.addPropertyChangeListener((evt) -> {
            if (evt.getPropertyName().equals("selectedNodes")) {
                List selection = (List) (evt.getNewValue());
                if (selection.size() == 1) {
                    Node node = (Node) (selection.get(0));
                    this.variableChooser.setSelectedItem(node);
                }
            }
        });

        this.variableChooser.addActionListener((e) -> {
            Node n = (Node) (this.variableChooser.getSelectedItem());
            workbench().scrollWorkbenchToNode(n);
            setNode(n);
        });

        nextButton.addActionListener((e) -> {
            int current = this.variableChooser.getSelectedIndex();
            int max = this.variableChooser.getItemCount();

            ++current;

            if (current == max) {
                JOptionPane.showMessageDialog(this,
                        "There are no more variables.");
            }

            int set = (current < max) ? current : 0;

            this.variableChooser.setSelectedIndex(set);
        });

        enableByNodeType();
    }

    private void enableByNodeType() {
        if (!isEditingMeasuredVariablesAllowed() && this.categoryEditor.getNode().getNodeType() == NodeType.MEASURED) {
            setEnabled(false);
        } else
            setEnabled(isEditingLatentVariablesAllowed() || this.categoryEditor.getNode().getNodeType() != NodeType.LATENT);
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        this.numCategoriesSpinner.setEnabled(enabled);
        this.categoryEditor.setEnabled(enabled);
    }

    private void setNumCategories(int numCategories) {
        this.categoryEditor.setNumCategories(numCategories);
        firePropertyChange("modelChanged", null, null);
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu presetMenu = new JMenu("Presets");
        this.presetMenu = presetMenu;
        menuBar.add(presetMenu);

        for (int i = 0; i < this.presetStrings.length; i++) {
            StringBuilder buf = new StringBuilder();

            for (int j = 0; j < this.presetStrings[i].length; j++) {
                buf.append(this.presetStrings[i][j]);

                if (j < this.presetStrings[i].length - 1) {
                    buf.append("-");
                }
            }

            Action action = new IndexedAction(buf.toString(), i) {

                private static final long serialVersionUID = 5052478563546335636L;

                @Override
                public void actionPerformed(ActionEvent e) {
                    setCategories(Arrays.asList(BayesPmEditorWizard.this.presetStrings[getIndex()]));
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

                int numCategories = numCategories();

                for (int i = 0; i < numCategories; i++) {
                    categories.add(ret + (i + 1));
                }

                setCategories(categories);
            }
        };

        presetMenu.add(sequence);

        JMenu transfer = new JMenu("Transfer");
        JMenuItem copy = new JMenuItem("Copy categories");
        JMenuItem paste = new JMenuItem("Paste categories");

        copy.addActionListener((e) -> {
            copyCategories();

            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "<html>"
                            + "The categories for this node have been copied; to transfer "
                            + "<br>these categories, choose another node and paste. You may"
                            + "<br>paste multiple times." + "</html>");
        });

        paste.addActionListener((e) -> pasteCategories());

        copy.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
        paste.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK));

        transfer.add(copy);
        transfer.add(paste);

        menuBar.add(transfer);

        return menuBar;
    }

    private void copyCategories() {
        Node node = (Node) this.variableChooser.getSelectedItem();
        DiscreteVariable variable
                = (DiscreteVariable) this.bayesPm.getVariable(node);
        this.copiedCategories = variable.getCategories();
    }

    private void pasteCategories() {
        if (this.copiedCategories != null) {
            setCategories(this.copiedCategories);
        }
    }

    private void setCategories(List categories) {
        this.categoryEditor.setCategories(categories);
        this.spinnerModel.setValue(categories.size());
        firePropertyChange("modelChanged", null, null);
    }

    private void createVariableChooser(BayesPm bayesPm, GraphWorkbench workbench) {
        this.variableChooser = new JComboBox<>();
        this.variableChooser.setBackground(Color.white);

        Graph graphModel = bayesPm.getDag();

        List<Node> nodes = graphModel.getNodes().stream().sorted().collect(Collectors.toList());
        nodes.forEach(this.variableChooser::addItem);

        if (this.variableChooser.getItemCount() > 0) {
            this.variableChooser.setSelectedIndex(0);
        }

        workbench.scrollWorkbenchToNode((Node) this.variableChooser.getSelectedItem());
    }

    private void setNode(Node node) {
        this.categoryEditor.setNode(node);
        int numCategories = this.bayesPm.getNumCategories(node);
        this.spinnerModel.setValue(numCategories);
        firePropertyChange("modelChanged", null, null);
        enableByNodeType();
    }

    private GraphWorkbench workbench() {
        return this.workbench;
    }

    private int numCategories() {
        return getBayesPm().getNumCategories(getNode());
    }

    private BayesPm getBayesPm() {
        return this.bayesPm;
    }

    private Node getNode() {
        Node selectedItem = (Node) this.variableChooser.getSelectedItem();

        if (selectedItem == null) {
            throw new NullPointerException();
        }

        return selectedItem;
    }

    private boolean isEditingMeasuredVariablesAllowed() {
        return this.editingMeasuredVariablesAllowed;
    }

    public void setEditingMeasuredVariablesAllowed(boolean editingMeasuredVariablesAllowed) {
        this.editingMeasuredVariablesAllowed = editingMeasuredVariablesAllowed;
        setNode(this.categoryEditor.getNode());

        this.presetMenu.setEnabled(editingMeasuredVariablesAllowed);
    }

    private boolean isEditingLatentVariablesAllowed() {
        return this.editingLatentVariablesAllowed;
    }

    public void setEditingLatentVariablesAllowed(boolean editingLatentVariablesAllowed) {
        this.editingLatentVariablesAllowed = editingLatentVariablesAllowed;
        setNode(this.categoryEditor.getNode());

        if (!editingLatentVariablesAllowed) {
            this.presetMenu.setEnabled(false);
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

            setLayout(new BorderLayout());

            if (node == null) {
//                return;
                throw new NullPointerException();
            }

            this.bayesPm = bayesPm;
            this.node = node;

            setNumCategories(numCategories());
        }

        public void setNumCategories(int numCategories) {
            removeAll();
            JComponent categoryFieldsPanel
                    = createCategoryFieldsPanel(numCategories);
            add(categoryFieldsPanel, BorderLayout.CENTER);
            revalidate();
            repaint();
            firePropertyChange("modelChanged", null, null);
        }

        public BayesPm getBayesPm() {
            return this.bayesPm;
        }

        private JComponent createCategoryFieldsPanel(int numCategories) {
            if (numCategories != this.bayesPm.getNumCategories(getNode())) {
                this.bayesPm.setNumCategories(getNode(), numCategories);
            }

            Box panel = Box.createVerticalBox();

            createCategoryFields();

            for (int i = 0; i < this.bayesPm.getNumCategories(getNode()); i++) {
                Box row = Box.createHorizontalBox();
                row.add(Box.createRigidArea(new Dimension(10, 0)));
                row.add(new JLabel((i + 1) + "."));
                row.add(Box.createRigidArea(new Dimension(4, 0)));
                row.add(this.categoryFields[i]);

                row.add(Box.createHorizontalGlue());
                panel.add(row);
            }

            setLayout(new BorderLayout());
            add(panel, BorderLayout.CENTER);

            setFocusTraversalPolicy(new FocusTraversalPolicy() {
                @Override
                public Component getComponentAfter(Container focusCycleRoot,
                                                   Component aComponent) {
                    int index = CategoryEditor.this.focusTraveralOrder.indexOf(aComponent);
                    int size = CategoryEditor.this.focusTraveralOrder.size();

                    if (index != -1) {
                        return (Component) CategoryEditor.this.focusTraveralOrder.get(
                                (index + 1) % size);
                    } else {
                        return getFirstComponent(focusCycleRoot);
                    }
                }

                @Override
                public Component getComponentBefore(Container focusCycleRoot,
                                                    Component aComponent) {
                    int index = CategoryEditor.this.focusTraveralOrder.indexOf(aComponent);
                    int size = CategoryEditor.this.focusTraveralOrder.size();

                    if (index != -1) {
                        return (Component) CategoryEditor.this.focusTraveralOrder.get(
                                (index - 1) % size);
                    } else {
                        return getFirstComponent(focusCycleRoot);
                    }
                }

                @Override
                public Component getFirstComponent(Container focusCycleRoot) {
                    return (Component) CategoryEditor.this.focusTraveralOrder.getFirst();
                }

                @Override
                public Component getLastComponent(Container focusCycleRoot) {
                    return (Component) CategoryEditor.this.focusTraveralOrder.getLast();
                }

                @Override
                public Component getDefaultComponent(Container focusCycleRoot) {
                    return getFirstComponent(focusCycleRoot);
                }
            });

            setFocusCycleRoot(true);

            return panel;
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            for (StringTextField field : this.categoryFields) {
                field.setEnabled(enabled);
            }
        }

        private void createCategoryFields() {
            this.categoryFields = new StringTextField[numCategories()];

            for (int i = 0; i < numCategories(); i++) {
                this.categoryFields[i] = new StringTextField(category(i), 10);
                StringTextField _field = this.categoryFields[i];

                this.categoryFields[i].setFilter((String value, String oldValue) -> {
                    if (BayesPmEditorWizard.this.labels.get(_field) != null) {
                        int index = BayesPmEditorWizard.this.labels.get(_field);

                        if (value == null) {
                            value = category(index);
                        }

                        for (int j = 0; j < numCategories(); j++) {
                            if (j != index && category(j).equals(value)) {
                                value = category(index);
                            }
                        }

                        setCategory(index, value);
                    }

                    return value;
                });

                BayesPmEditorWizard.this.labels.put(this.categoryFields[i], i);
                this.focusTraveralOrder.add(this.categoryFields[i]);
            }
        }

        private int numCategories() {
            return this.bayesPm.getNumCategories(getNode());
        }

        private String category(int index) {
            return this.bayesPm.getCategory(getNode(), index);
        }

        private void setCategory(int index, String value) {
            DiscreteVariable variable
                    = (DiscreteVariable) this.bayesPm.getVariable(getNode());
            List<String> categories = new ArrayList<>(variable.getCategories());
            categories.set(index, value);
            this.bayesPm.setCategories(this.node, categories);

            firePropertyChange("modelChanged", null, null);
        }

        public void setNode(Node node) {
            for (int i = 0; i < numCategories(); i++) {
                this.categoryFields[i].setValue(this.categoryFields[i].getText());
            }

            this.node = node;
            setNumCategories(this.bayesPm.getNumCategories(node));
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

            for (Object category : categories) {
                if (category == null) {
                    throw new NullPointerException();
                }
            }

            removeAll();
            JComponent categoryFieldsPanel
                    = createCategoryFieldsPanel(categories.size());

            for (int i = 0; i < categories.size(); i++) {
                this.categoryFields[i].setValue((String) categories.get(i));
            }

            add(categoryFieldsPanel, BorderLayout.CENTER);
            revalidate();
            repaint();
        }

        public Node getNode() {
            return this.node;
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
            return this.index;
        }

    }

}
