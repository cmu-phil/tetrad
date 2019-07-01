/**
 * 
 */
package edu.cmu.tetradapp.editor.cg;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FocusTraversalPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.util.StringTextField;
import edu.pitt.dbmi.cg.CgPm;

/**
 * Jun 21, 2019 12:58:50 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class CgCategoryEditor extends JPanel {

    private static final long serialVersionUID = -7488118975131239436L;

    private final Map<Object, Integer> labels = new HashMap<>();

    private CgPm cgPm;
    private Node node;
    private StringTextField[] categoryFields;
    private final LinkedList<StringTextField> focusTraveralOrder = new LinkedList<>();

    public CgCategoryEditor(CgPm cgPm, Node node) {
        if (cgPm == null) {
            throw new NullPointerException();
        }

        setLayout(new BorderLayout());

        if (node == null) {
//            return;
            throw new NullPointerException();
        }

        this.cgPm = cgPm;
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

    public CgPm getCgPm() {
        return cgPm;
    }

    private JComponent createCategoryFieldsPanel(int numCategories) {
        if (numCategories != cgPm.getDiscreteNumCategories(getNode())) {
        	cgPm.setDiscreteNumCategories(getNode(), numCategories);
        }

        Box panel = Box.createVerticalBox();

        createCategoryFields();

        for (int i = 0; i < cgPm.getDiscreteNumCategories(getNode()); i++) {
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
                int index = focusTraveralOrder.indexOf(aComponent);
                int size = focusTraveralOrder.size();

                if (index != -1) {
                    return (Component) focusTraveralOrder.get(
                            (index + 1) % size);
                } else {
                    return getFirstComponent(focusCycleRoot);
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
                    return getFirstComponent(focusCycleRoot);
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
                return getFirstComponent(focusCycleRoot);
            }
        });

        setFocusCycleRoot(true);

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
        this.categoryFields = new StringTextField[numCategories()];

        for (int i = 0; i < numCategories(); i++) {
            this.categoryFields[i] = new StringTextField(category(i), 10);
            final StringTextField _field = this.categoryFields[i];

            this.categoryFields[i].setFilter((String value, String oldValue) -> {
                if (labels.get(_field) != null) {
                    int index = labels.get(_field);

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

            labels.put(this.categoryFields[i], i);
            this.focusTraveralOrder.add(this.categoryFields[i]);
        }
    }

    private int numCategories() {
        return cgPm.getDiscreteNumCategories(getNode());
    }

    private String category(int index) {
        return cgPm.getDiscreteCategory(getNode(), index);
    }

    private void setCategory(int index, String value) {
        DiscreteVariable variable
                = (DiscreteVariable) cgPm.getDiscreteVariable(getNode());
        List<String> categories = new ArrayList<>(variable.getCategories());
        categories.set(index, value);
        cgPm.setDiscreteCategories(node, categories);

        firePropertyChange("modelChanged", null, null);
    }

    public void setNode(Node node) {
        for (int i = 0; i < numCategories(); i++) {
            categoryFields[i].setValue(categoryFields[i].getText());
        }

        this.node = node;
        setNumCategories(cgPm.getDiscreteNumCategories(node));
    }

    public void setCategories(List<String> categories) {
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
        return node;
    }
}
