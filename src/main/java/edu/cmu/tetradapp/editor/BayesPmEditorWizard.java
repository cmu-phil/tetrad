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
import edu.cmu.tetradapp.util.SortingComboBox;
import edu.cmu.tetradapp.util.StringTextField;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

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
    private BayesPm bayesPm;

    /**
     * Lets the user select the variable they want to edit.
     */
    private JComboBox variableChooser;

    /**
     * True iff the editing of measured variables is allowed.
     */
    private boolean editingMeasuredVariablesAllowed = false;

    /**
     * True iff the editing of latent variables is allowed.
     */
    private boolean editingLatentVariablesAllowed = false;

    /**
     * Lets the user see graphically which variable is being edited and click to
     * another variable.
     */
    private GraphWorkbench workbench;

    /**
     * A reference to the category editor.
     */
    private CategoryEditor categoryEditor;

    /**
     * A reference to the spinner model.
     */
    private SpinnerNumberModel spinnerModel;

    /**
     * The preset strings that will be used.
     */
    private String[][] presetStrings = new String[][]{{"Low", "High"},
            {"Low", "Medium", "High"}, {"On", "Off"}, {"Yes", "No"}};

    /**
     * ?
     */
    private List copiedCategories;

    /**
     * ?
     */
    private Map<Object, Integer> labels = new HashMap<Object, Integer>();

    /**
     * ?
     */
    private JSpinner numCategoriesSpinner;

    /**
     *
     */
    private JMenu presetMenu;

    /**
     * This is the wizard for the PMEditor class.  Its function is to allow the
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
        b2.add(variableChooser);
        b2.add(nextButton);
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(10));

        if (numCategories != 0) {
            spinnerModel = new SpinnerNumberModel(numCategories, 2, 1000, 1);
            numCategoriesSpinner = new JSpinner(spinnerModel) {
                public Dimension getMaximumSize() {
                    return getPreferredSize();
                }
            };
            numCategoriesSpinner.setFont(new Font("Serif", Font.PLAIN, 12));
            numCategoriesSpinner.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                    JSpinner spinner = (JSpinner) e.getSource();
                    SpinnerNumberModel model =
                            (SpinnerNumberModel) spinner.getModel();
                    setNumCategories(model.getNumber().intValue());
                }
            });

            Box b3 = Box.createHorizontalBox();
            b3.add(new JLabel("Number of categories:  "));
            b3.add(numCategoriesSpinner);
            b3.add(Box.createHorizontalGlue());
            b1.add(b3);

            b1.add(Box.createVerticalStrut(10));
        }

        categoryEditor = new CategoryEditor(bayesPm, getNode());

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

        JMenuBar menuBar = createMenuBar();

        b1.setBorder(new EmptyBorder(10, 10, 0, 10));

        setLayout(new BorderLayout());
        add(b1, BorderLayout.CENTER);
        add(menuBar, BorderLayout.NORTH);

        workbench.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals("selectedNodes")) {
                    List selection = (List) (evt.getNewValue());
                    if (selection.size() == 1) {
                        Node node = (Node) (selection.get(0));
                        variableChooser.setSelectedItem(node);
                    }
                }
            }
        });

        variableChooser.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Node node = (Node) (variableChooser.getSelectedItem());
                workbench().scrollWorkbenchToNode(node);
                setNode(node);
            }
        });

        nextButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int current = variableChooser.getSelectedIndex();
                int max = variableChooser.getItemCount();

                ++current;

                if (current == max) {
                    JOptionPane.showMessageDialog(BayesPmEditorWizard.this,
                            "There are no more variables.");
                }

                int set = (current < max) ? current : 0;

                variableChooser.setSelectedIndex(set);
            }
        });

        enableByNodeType();
    }

    private void enableByNodeType() {
        if (!isEditingMeasuredVariablesAllowed() && categoryEditor.getNode().getNodeType() == NodeType.MEASURED) {
            setEnabled(false);
        }
        else if (!isEditingLatentVariablesAllowed() && categoryEditor.getNode().getNodeType() == NodeType.LATENT) {
            setEnabled(false);
        }
        else {
            setEnabled(true);
        }
    }


    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        numCategoriesSpinner.setEnabled(enabled);
        categoryEditor.setEnabled(enabled);
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

        for (int i = 0; i < presetStrings.length; i++) {
            StringBuilder buf = new StringBuilder();

            for (int j = 0; j < presetStrings[i].length; j++) {
                buf.append(presetStrings[i][j]);

                if (j < presetStrings[i].length - 1) {
                    buf.append("-");
                }
            }

            Action action = new IndexedAction(buf.toString(), i) {
                public void actionPerformed(ActionEvent e) {
                    setCategories(Arrays.asList(presetStrings[getIndex()]));
                }
            };

            presetMenu.add(action);
        }

        presetMenu.addSeparator();

        Action sequence = new AbstractAction("x1, x2, x3, ...") {
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

        copy.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                copyCategories();

                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "<html>" +
                                "The categories for this node have been copied; to transfer " +
                                "<br>these categories, choose another node and paste. You may" +
                                "<br>paste multiple times." + "</html>");
            }
        });

        paste.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                pasteCategories();
            }
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
        DiscreteVariable variable =
                (DiscreteVariable) bayesPm.getVariable(node);
        this.copiedCategories = variable.getCategories();
    }

    private void pasteCategories() {
        if (this.copiedCategories != null) {
            setCategories(this.copiedCategories);
        }
    }

    private void setCategories(List categories) {
        categoryEditor.setCategories(categories);
        spinnerModel.setValue(categories.size());
        firePropertyChange("modelChanged", null, null);
    }

    private void createVariableChooser(BayesPm bayesPm,
            GraphWorkbench workbench) {
        variableChooser = new SortingComboBox() {
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };

        variableChooser.setBackground(Color.white);

        Graph graphModel = bayesPm.getDag();

        for (Iterator it = graphModel.getNodes().iterator(); it.hasNext();) {
            variableChooser.addItem(it.next());
        }

        if (graphModel.getNodes().size() > 0) {
            variableChooser.setSelectedIndex(0);
        }

        workbench.scrollWorkbenchToNode(
                (Node) (variableChooser.getSelectedItem()));
    }

    private void setNode(Node node) {
        categoryEditor.setNode(node);
        int numCategories = bayesPm.getNumCategories(node);
        spinnerModel.setValue(numCategories);
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
        return bayesPm;
    }

    private Node getNode() {
        Node selectedItem = (Node) variableChooser.getSelectedItem();

        if (selectedItem == null) {
            throw new NullPointerException();
        }

        return selectedItem;
    }

    public boolean isEditingMeasuredVariablesAllowed() {
        return editingMeasuredVariablesAllowed;
    }

    public void setEditingMeasuredVariablesAllowed(boolean editingMeasuredVariablesAllowed) {
        this.editingMeasuredVariablesAllowed = editingMeasuredVariablesAllowed;
        setNode(categoryEditor.getNode());

        if (!editingMeasuredVariablesAllowed) {
            presetMenu.setEnabled(false);
        } else {
            presetMenu.setEnabled(true);
        }
    }

    public boolean isEditingLatentVariablesAllowed() {
        return editingLatentVariablesAllowed;
    }

    public void setEditingLatentVariablesAllowed(boolean editingLatentVariablesAllowed) {
        this.editingLatentVariablesAllowed = editingLatentVariablesAllowed;
        setNode(categoryEditor.getNode());

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
        private BayesPm bayesPm;
        private Node node;
        private StringTextField[] categoryFields;
        private LinkedList focusTraveralOrder = new LinkedList();


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
            JComponent categoryFieldsPanel =
                    createCategoryFieldsPanel(numCategories);
            add(categoryFieldsPanel, BorderLayout.CENTER);
            revalidate();
            repaint();
            firePropertyChange("modelChanged", null, null);
        }

        public BayesPm getBayesPm() {
            return bayesPm;
        }

        private JComponent createCategoryFieldsPanel(int numCategories) {
            if (numCategories != bayesPm.getNumCategories(getNode())) {
                bayesPm.setNumCategories(getNode(), numCategories);
            }

            Box panel = Box.createVerticalBox();

            createCategoryFields();

            for (int i = 0; i < bayesPm.getNumCategories(getNode()); i++) {
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
                public Component getComponentAfter(Container focusCycleRoot,
                                                   Component aComponent) {
                    int index = focusTraveralOrder.indexOf(aComponent);
                    int size = focusTraveralOrder.size();

                    if (index != -1) {
                        return (Component) focusTraveralOrder.get(
                                (index + 1) % size);
                    } else {
                        return getFirstComponent(focusCycleRoot);
                    }
                }

                public Component getComponentBefore(Container focusCycleRoot,
                                                    Component aComponent) {
                    int index = focusTraveralOrder.indexOf(aComponent);
                    int size = focusTraveralOrder.size();

                    if (index != -1) {
                        return (Component) focusTraveralOrder.get(
                                (index - 1) % size);
                    } else {
                        return getFirstComponent(focusCycleRoot);
                    }
                }

                public Component getFirstComponent(Container focusCycleRoot) {
                    return (Component) focusTraveralOrder.getFirst();
                }

                public Component getLastComponent(Container focusCycleRoot) {
                    return (Component) focusTraveralOrder.getLast();
                }

                public Component getDefaultComponent(Container focusCycleRoot) {
                    return getFirstComponent(focusCycleRoot);
                }
            });

            setFocusCycleRoot(true);

            return panel;
        }

        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            for (StringTextField field : categoryFields) {
                field.setEnabled(enabled);
            }
        }

        private void createCategoryFields() {
            this.categoryFields = new StringTextField[numCategories()];

            for (int i = 0; i < numCategories(); i++) {
                this.categoryFields[i] = new StringTextField(category(i), 10);
                final StringTextField _field = this.categoryFields[i];

                this.categoryFields[i].setFilter(new StringTextField.Filter() {
                    public String filter(String value, String oldValue) {
                        if (labels.get(_field) != null) {
                            int index = labels.get(_field);

                            if (value == null) {
                                value = category(index);
                            }

                            for (int i = 0; i < numCategories(); i++) {
                                if (i != index && category(i).equals(value)) {
                                    value = category(index);
                                }
                            }

                            setCategory(index, value);
                        }

                        return value;
                    }
                });

                labels.put(this.categoryFields[i], i);
                this.focusTraveralOrder.add(this.categoryFields[i]);
            }
        }

        private int numCategories() {
            return bayesPm.getNumCategories(getNode());
        }

        private String category(int index) {
            return bayesPm.getCategory(getNode(), index);
        }

        private void setCategory(int index, String value) {
            DiscreteVariable variable =
                    (DiscreteVariable) bayesPm.getVariable(getNode());
            List<String> categories = new ArrayList<>(variable.getCategories());
            categories.set(index, value);
            bayesPm.setCategories(node, categories);

            firePropertyChange("modelChanged", null, null);
        }

        public void setNode(Node node) {
            for (int i = 0; i < numCategories(); i++) {
                categoryFields[i].setValue(categoryFields[i].getText());
            }

            this.node = node;
            setNumCategories(bayesPm.getNumCategories(node));
        }

        public void setCategories(List categories) {
            if (categories == null) {
                throw new NullPointerException();
            }

            if (categories.size() < 2) {
                throw new IllegalArgumentException(
                        "Number of categories must be" + " >= 2: " +
                                categories.size());
            }

            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i) == null) {
                    throw new NullPointerException();
                }
            }

            removeAll();
            JComponent categoryFieldsPanel =
                    createCategoryFieldsPanel(categories.size());

            for (int i = 0; i < categories.size(); i++) {
                this.categoryFields[i].setValue((String) categories.get(i));
            }

            add(categoryFieldsPanel, BorderLayout.CENTER);
            revalidate();
            repaint();
        }

        public Node getNode() {
            return node;
        }
    }

    /**
     * The actionPerformed method is still abstract.
     */
    abstract static class IndexedAction extends AbstractAction {
        private int index;

        public IndexedAction(String name, int index) {
            super(name);
            this.index = index;
        }

        public int getIndex() {
            return index;
        }
    }
}






