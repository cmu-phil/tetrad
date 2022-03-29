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
    private final DiscreteVariable variable;

    public DiscreteDiscretizationEditor(final DiscreteVariable variable) {
        if (variable == null) {
            throw new NullPointerException();
        }

        final int numCategories = variable.getNumCategories();
        this.variable = variable;

        //String name = variable.getNode();

        final Box b1 = Box.createVerticalBox();

//        Box b2 = Box.createHorizontalBox();
//        b2.add(Box.createRigidArea(new Dimension(5, 0)));
//        b2.add(new JLabel("Variable: " + name + " (Discrete)"));
//        b2.add(Box.createHorizontalGlue());
//        b1.add(b2);

        b1.add(Box.createVerticalStrut(10));

        final Box b6 = Box.createHorizontalBox();
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

    public void setDiscretizationSpec(final DiscretizationSpec spec) {
        this.remapEditor.setDiscretizationSpec((DiscreteDiscretizationSpec) spec);
    }

    //===========================PRIVATE METHODS=========================//

    private RemapEditor createRemapEditor() {
        final List<String> categories = DiscreteDiscretizationEditor.defaultCategories(this.variable);
        final int[] remap = DiscreteDiscretizationEditor.defaultRemap(categories);
        final DiscreteDiscretizationSpec discretizationSpec =
                new DiscreteDiscretizationSpec(remap, categories);
        this.remapEditor = new RemapEditor(this.variable, discretizationSpec);
        return this.remapEditor;
    }

    private static int[] defaultRemap(final List<String> categories) {
        final int[] remap = new int[categories.size()];
        for (int i = 0; i < remap.length; i++) {
            remap[i] = i;
        }
        return remap;
    }

    private static List<String> defaultCategories(final DiscreteVariable variable) {
        final List<String> categories = new LinkedList<>();
        for (int i = 0; i < variable.getNumCategories(); i++) {
            categories.add(variable.getCategory(i));
        }
        return categories;
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
        private StringTextField[] categoryFields;
        private StringTextField[] rangeFields;
        private final Map<Object, Integer> labels = new HashMap<>();

        private final LinkedList<StringTextField> focusTraveralOrder =
                new LinkedList<>();
        private final DiscreteVariable oldVariable;

        public RemapEditor(final DiscreteVariable variable,
                           final DiscreteDiscretizationSpec spec) {
            this.variable = variable;

            if (variable == null) {
                throw new NullPointerException();
            }

            if (spec == null) {
                throw new NullPointerException();
            }

            this.oldVariable = variable;

            final int[] remap = spec.getRemap();
            final List<String> categories = spec.getCategories();

            for (int i = 0; i < this.oldVariable.getNumCategories(); i++) {
                this.newCategories.add(categories.get(remap[i]));
            }

            final Box panel = Box.createVerticalBox();

            createCategoryFields();
            createRangeFields();

            for (int i = 0; i < categories.size(); i++) {
                final Box row = Box.createHorizontalBox();
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
                public Component getComponentAfter(final Container focusCycleRoot,
                                                   final Component aComponent) {
                    final int index = RemapEditor.this.focusTraveralOrder.indexOf(aComponent);
                    final int size = RemapEditor.this.focusTraveralOrder.size();

                    if (index != -1) {
                        return RemapEditor.this.focusTraveralOrder.get(
                                (index + 1) % size);
                    } else {
                        return getFirstComponent(focusCycleRoot);
                    }
                }

                public Component getComponentBefore(final Container focusCycleRoot,
                                                    final Component aComponent) {
                    final int index = RemapEditor.this.focusTraveralOrder.indexOf(aComponent);
                    final int size = RemapEditor.this.focusTraveralOrder.size();

                    if (index != -1) {
                        return RemapEditor.this.focusTraveralOrder.get((index - 1) % size);
                    } else {
                        return getFirstComponent(focusCycleRoot);
                    }
                }

                public Component getFirstComponent(final Container focusCycleRoot) {
                    return RemapEditor.this.focusTraveralOrder.getFirst();
                }

                public Component getLastComponent(final Container focusCycleRoot) {
                    return RemapEditor.this.focusTraveralOrder.getLast();
                }

                public Component getDefaultComponent(final Container focusCycleRoot) {
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
                final StringTextField _field = this.rangeFields[i];

                this.rangeFields[i].setFilter(new StringTextField.Filter() {
                    public String filter(String value, final String oldValue) {
                        if (RemapEditor.this.labels.get(_field) != null) {
                            final int index = RemapEditor.this.labels.get(_field);

                            if (value == null) {
                                value = RemapEditor.this.oldVariable.getCategory(index);
                            }

                            RemapEditor.this.newCategories.set(index, value);
                        }

                        return value;
                    }
                });

                this.labels.put(this.rangeFields[i], i);
                this.focusTraveralOrder.add(this.rangeFields[i]);
            }
        }

        public DiscreteVariable getVariable() {
            return this.variable;
        }

        public DiscreteDiscretizationSpec getDiscretizationSpec() {
            final List<String> categoryList = new LinkedList<>();

            for (final String newCategory : this.newCategories) {
                if (!categoryList.contains(newCategory)) {
                    categoryList.add(newCategory);
                }
            }

            final int[] remap = new int[this.oldVariable.getNumCategories()];

            for (int i = 0; i < remap.length; i++) {
                final String value = this.newCategories.get(i);
                remap[i] = categoryList.indexOf(value);
            }

            return new DiscreteDiscretizationSpec(remap, categoryList);
        }

        public void setDiscretizationSpec(final DiscreteDiscretizationSpec spec) {
            final int[] remap = spec.getRemap();
            final List categories = spec.getCategories();

            for (int i = 0; i < this.oldVariable.getNumCategories(); i++) {
                this.rangeFields[i].setValue((String) categories.get(remap[i]));
            }
        }
    }
}





