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
 * @author Joseph Ramsey
 */
class DiscreteDiscretizationEditor extends JPanel implements DiscretizationEditor {
    private RemapEditor remapEditor;
    private DiscreteVariable variable;

    public DiscreteDiscretizationEditor(DiscreteVariable variable) {
        if (variable == null) {
            throw new NullPointerException();
        }

        int numCategories = variable.getNumCategories();
        this.variable = variable;

        //String name = variable.getName();

        Box b1 = Box.createVerticalBox();

//        Box b2 = Box.createHorizontalBox();
//        b2.add(Box.createRigidArea(new Dimension(5, 0)));
//        b2.add(new JLabel("Variable: " + name + " (Discrete)"));
//        b2.add(Box.createHorizontalGlue());
//        b1.add(b2);

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

    public DiscretizationSpec getDiscretizationSpec() {
        return this.remapEditor.getDiscretizationSpec();
    }

    public void setDiscretizationSpec(DiscretizationSpec spec) {
        remapEditor.setDiscretizationSpec((DiscreteDiscretizationSpec) spec);
    }

    //===========================PRIVATE METHODS=========================//

    private RemapEditor createRemapEditor() {
        List<String> categories = defaultCategories(variable);
        int[] remap = defaultRemap(categories);
        DiscreteDiscretizationSpec discretizationSpec =
                new DiscreteDiscretizationSpec(remap, categories);
        this.remapEditor = new RemapEditor(variable, discretizationSpec);
        return this.remapEditor;
    }

    private static int[] defaultRemap(List<String> categories) {
        int[] remap = new int[categories.size()];
        for (int i = 0; i < remap.length; i++) {
            remap[i] = i;
        }
        return remap;
    }

    private static List<String> defaultCategories(DiscreteVariable variable) {
        List<String> categories = new LinkedList<String>();
        for (int i = 0; i < variable.getNumCategories(); i++) {
            categories.add(variable.getCategory(i));
        }
        return categories;
    }

    //================================CLASSES============================//

    final static class BigLabel extends JLabel {
        private static final Font FONT = new Font("Dialog", Font.BOLD, 20);

        public BigLabel() {
            super(" --> ");
            setFont(FONT);
        }
    }

    final static class RemapEditor extends JComponent {
        private final DiscreteVariable variable;
        private final List<String> newCategories = new LinkedList<String>();
        private StringTextField[] categoryFields;
        private StringTextField[] rangeFields;
        private final Map<Object, Integer> labels = new HashMap<Object, Integer>();

        private final LinkedList<StringTextField> focusTraveralOrder =
                new LinkedList<StringTextField>();
        private DiscreteVariable oldVariable;

        public RemapEditor(DiscreteVariable variable,
                DiscreteDiscretizationSpec spec) {
            this.variable = variable;

            if (variable == null) {
                throw new NullPointerException();
            }

            if (spec == null) {
                throw new NullPointerException();
            }

            oldVariable = variable;

            int[] remap = spec.getRemap();
            List<String> categories = spec.getCategories();

            for (int i = 0; i < oldVariable.getNumCategories(); i++) {
                newCategories.add(categories.get(remap[i]));
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
                    int index = focusTraveralOrder.indexOf(aComponent);
                    int size = focusTraveralOrder.size();

                    if (index != -1) {
                        return focusTraveralOrder.get(
                                (index + 1) % size);
                    }
                    else {
                        return getFirstComponent(focusCycleRoot);
                    }
                }

                public Component getComponentBefore(Container focusCycleRoot,
                        Component aComponent) {
                    int index = focusTraveralOrder.indexOf(aComponent);
                    int size = focusTraveralOrder.size();

                    if (index != -1) {
                        return focusTraveralOrder.get((index - 1) % size);
                    }
                    else {
                        return getFirstComponent(focusCycleRoot);
                    }
                }

                public Component getFirstComponent(Container focusCycleRoot) {
                    return focusTraveralOrder.getFirst();
                }

                public Component getLastComponent(Container focusCycleRoot) {
                    return focusTraveralOrder.getLast();
                }

                public Component getDefaultComponent(Container focusCycleRoot) {
                    return getFirstComponent(focusCycleRoot);
                }
            });

            setFocusCycleRoot(true);
        }

        private void createCategoryFields() {
            this.categoryFields =
                    new StringTextField[oldVariable.getNumCategories()];

            for (int i = 0; i < oldVariable.getNumCategories(); i++) {
                this.categoryFields[i] =
                        new StringTextField(oldVariable.getCategory(i), 6);
                labels.put(this.categoryFields[i], i);
                this.categoryFields[i].setEditable(false);
            }
        }

        private void createRangeFields() {
            this.rangeFields =
                    new StringTextField[oldVariable.getNumCategories()];

            for (int i = 0; i < oldVariable.getNumCategories(); i++) {
                this.rangeFields[i] =
                        new StringTextField(oldVariable.getCategory(i), 6);
                final StringTextField _field = this.rangeFields[i];

                this.rangeFields[i].setFilter(new StringTextField.Filter() {
                    public String filter(String value, String oldValue) {
                        if (labels.get(_field) != null) {
                            int index = labels.get(_field);

                            if (value == null) {
                                value = oldVariable.getCategory(index);
                            }

                            newCategories.set(index, value);
                        }

                        return value;
                    }
                });

                labels.put(this.rangeFields[i], i);
                this.focusTraveralOrder.add(this.rangeFields[i]);
            }
        }

        public DiscreteVariable getVariable() {
            return variable;
        }

        public DiscreteDiscretizationSpec getDiscretizationSpec() {
            List<String> categoryList = new LinkedList<String>();

            for (String newCategory : newCategories) {
                if (!categoryList.contains(newCategory)) {
                    categoryList.add(newCategory);
                }
            }

            int[] remap = new int[oldVariable.getNumCategories()];

            for (int i = 0; i < remap.length; i++) {
                String value = newCategories.get(i);
                remap[i] = categoryList.indexOf(value);
            }

            return new DiscreteDiscretizationSpec(remap, categoryList);
        }

        public void setDiscretizationSpec(DiscreteDiscretizationSpec spec) {
            int[] remap = spec.getRemap();
            List categories = spec.getCategories();

            for (int i = 0; i < oldVariable.getNumCategories(); i++) {
                this.rangeFields[i].setValue((String) categories.get(remap[i]));
            }
        }
    }
}





