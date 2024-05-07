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

package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.data.DiscreteDiscretizationSpec;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.DiscretizationSpec;
import edu.cmu.tetradapp.util.StringTextField;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Edits discretization parameters for a discrete variable.
 *
 * @author josephramsey
 */
class DiscreteDiscretizationEditor extends JPanel implements DiscretizationEditor {

    /**
     * The variable.
     */
    private final DiscreteVariable variable;

    /**
     * The remap editor.
     */
    private RemapEditor remapEditor;

    /**
     * <p>Constructor for DiscreteDiscretizationEditor.</p>
     *
     * @param variable a {@link edu.cmu.tetrad.data.DiscreteVariable} object
     */
    public DiscreteDiscretizationEditor(DiscreteVariable variable) {
        if (variable == null) {
            throw new NullPointerException();
        }

        this.variable = variable;

        //String name = variable.getNode();

        Box b1 = Box.createVerticalBox();

        b1.add(Box.createVerticalStrut(10));

        Box b6 = Box.createHorizontalBox();
        b6.add(Box.createRigidArea(new Dimension(10, 0)));
        b6.add(new JLabel(
                "Edit new categories that old categories should map to:"));
        b6.add(Box.createHorizontalGlue());
        b1.add(b6);

        b1.add(Box.createVerticalStrut(10));
        b1.add(createRemapEditor());

        b1.add(Box.createVerticalGlue());

        setLayout(new BorderLayout());
        add(b1, BorderLayout.CENTER);
    }

    //===========================PUBLIC METHODS==========================//

    private static int[] defaultRemap(List<String> categories) {
        int[] remap = new int[categories.size()];
        for (int i = 0; i < remap.length; i++) {
            remap[i] = i;
        }
        return remap;
    }

    private static List<String> defaultCategories(DiscreteVariable variable) {
        List<String> categories = new LinkedList<>();
        for (int i = 0; i < variable.getNumCategories(); i++) {
            categories.add(variable.getCategory(i));
        }
        return categories;
    }

    //===========================PRIVATE METHODS=========================//

    /**
     * <p>getDiscretizationSpec.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DiscretizationSpec} object
     */
    public DiscretizationSpec getDiscretizationSpec() {
        return this.remapEditor.getDiscretizationSpec();
    }

    /**
     * <p>setDiscretizationSpec.</p>
     *
     * @param spec a {@link edu.cmu.tetrad.data.DiscretizationSpec} object
     */
    public void setDiscretizationSpec(DiscretizationSpec spec) {
        this.remapEditor.setDiscretizationSpec((DiscreteDiscretizationSpec) spec);
    }

    private RemapEditor createRemapEditor() {
        List<String> categories = DiscreteDiscretizationEditor.defaultCategories(this.variable);
        int[] remap = DiscreteDiscretizationEditor.defaultRemap(categories);
        DiscreteDiscretizationSpec discretizationSpec =
                new DiscreteDiscretizationSpec(remap, categories);
        this.remapEditor = new RemapEditor(this.variable, discretizationSpec);
        return this.remapEditor;
    }

    //================================CLASSES============================//

    static final class BigLabel extends JLabel {
        private static final Font FONT = new Font("Dialog", Font.BOLD, 20);

        public BigLabel() {
            super(" --> ");
            setFont(BigLabel.FONT);
        }
    }

    static final class RemapEditor extends JComponent {
        private final DiscreteVariable variable;
        private final List<String> newCategories = new LinkedList<>();
        private final Map<Object, Integer> labels = new HashMap<>();
        private final LinkedList<StringTextField> focusTraveralOrder =
                new LinkedList<>();
        private final DiscreteVariable oldVariable;
        private StringTextField[] categoryFields;
        private StringTextField[] rangeFields;

        public RemapEditor(DiscreteVariable variable,
                           DiscreteDiscretizationSpec spec) {
            this.variable = variable;

            if (variable == null) {
                throw new NullPointerException();
            }

            if (spec == null) {
                throw new NullPointerException();
            }

            this.oldVariable = variable;

            int[] remap = spec.getRemap();
            List<String> categories = spec.getCategories();

            for (int i = 0; i < this.oldVariable.getNumCategories(); i++) {
                this.newCategories.add(categories.get(remap[i]));
            }

            Box panel = Box.createVerticalBox();

            createCategoryFields();
            createRangeFields();

            for (int i = 0; i < categories.size(); i++) {
                Box row = Box.createHorizontalBox();
                row.add(Box.createRigidArea(new Dimension(10, 0)));

                row.add(new JLabel((i + 1) + ". "));
                row.add(this.categoryFields[i]);
                row.add(new BigLabel());
                row.add(this.rangeFields[i]);

                row.add(Box.createHorizontalGlue());
                panel.add(row);
            }

            setLayout(new BorderLayout());
            add(panel, BorderLayout.CENTER);

            setFocusTraversalPolicy(new FocusTraversalPolicy() {
                public Component getComponentAfter(Container focusCycleRoot,
                                                   Component aComponent) {
                    int index = RemapEditor.this.focusTraveralOrder.indexOf(aComponent);
                    int size = RemapEditor.this.focusTraveralOrder.size();

                    if (index != -1) {
                        return RemapEditor.this.focusTraveralOrder.get(
                                (index + 1) % size);
                    } else {
                        return getFirstComponent(focusCycleRoot);
                    }
                }

                public Component getComponentBefore(Container focusCycleRoot,
                                                    Component aComponent) {
                    int index = RemapEditor.this.focusTraveralOrder.indexOf(aComponent);
                    int size = RemapEditor.this.focusTraveralOrder.size();

                    if (index != -1) {
                        return RemapEditor.this.focusTraveralOrder.get((index - 1) % size);
                    } else {
                        return getFirstComponent(focusCycleRoot);
                    }
                }

                public Component getFirstComponent(Container focusCycleRoot) {
                    return RemapEditor.this.focusTraveralOrder.getFirst();
                }

                public Component getLastComponent(Container focusCycleRoot) {
                    return RemapEditor.this.focusTraveralOrder.getLast();
                }

                public Component getDefaultComponent(Container focusCycleRoot) {
                    return getFirstComponent(focusCycleRoot);
                }
            });

            setFocusCycleRoot(true);
        }

        private void createCategoryFields() {
            this.categoryFields =
                    new StringTextField[this.oldVariable.getNumCategories()];

            for (int i = 0; i < this.oldVariable.getNumCategories(); i++) {
                this.categoryFields[i] =
                        new StringTextField(this.oldVariable.getCategory(i), 6);
                this.labels.put(this.categoryFields[i], i);
                this.categoryFields[i].setEditable(false);
            }
        }

        private void createRangeFields() {
            this.rangeFields =
                    new StringTextField[this.oldVariable.getNumCategories()];

            for (int i = 0; i < this.oldVariable.getNumCategories(); i++) {
                this.rangeFields[i] =
                        new StringTextField(this.oldVariable.getCategory(i), 6);
                StringTextField _field = this.rangeFields[i];

                this.rangeFields[i].setFilter((value, oldValue) -> {
                    if (RemapEditor.this.labels.get(_field) != null) {
                        int index = RemapEditor.this.labels.get(_field);

                        if (value == null) {
                            value = RemapEditor.this.oldVariable.getCategory(index);
                        }

                        RemapEditor.this.newCategories.set(index, value);
                    }

                    return value;
                });

                this.labels.put(this.rangeFields[i], i);
                this.focusTraveralOrder.add(this.rangeFields[i]);
            }
        }

        public DiscreteVariable getVariable() {
            return this.variable;
        }

        public DiscreteDiscretizationSpec getDiscretizationSpec() {
            List<String> categoryList = new LinkedList<>();

            for (String newCategory : this.newCategories) {
                if (!categoryList.contains(newCategory)) {
                    categoryList.add(newCategory);
                }
            }

            int[] remap = new int[this.oldVariable.getNumCategories()];

            for (int i = 0; i < remap.length; i++) {
                String value = this.newCategories.get(i);
                remap[i] = categoryList.indexOf(value);
            }

            return new DiscreteDiscretizationSpec(remap, categoryList);
        }

        public void setDiscretizationSpec(DiscreteDiscretizationSpec spec) {
            int[] remap = spec.getRemap();
            List<String> categories = spec.getCategories();

            for (int i = 0; i < this.oldVariable.getNumCategories(); i++) {
                this.rangeFields[i].setValue(categories.get(remap[i]));
            }
        }
    }
}





