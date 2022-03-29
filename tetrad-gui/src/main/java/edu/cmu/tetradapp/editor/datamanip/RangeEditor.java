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

import edu.cmu.tetrad.data.ContinuousDiscretizationSpec;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.DoubleTextField.Filter;
import edu.cmu.tetradapp.util.StringTextField;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * An editor that allows one to edit a range while discretizing continuous data.
 *
 * @author Joseph Ramsey
 * @author Tyler Gibson
 */
@SuppressWarnings({"SuspiciousMethodCalls"})
final class RangeEditor extends JComponent {

    /**
     * The breakpoints to utilize, this may be user defined or calculated by the editor.
     */
    private final double[] breakpoints;

    /**
     * The categories whos ranges are being edited.
     */
    private final List<String> categories;

    /**
     * The text fields that allow one to edit the name of the category.
     */
    private StringTextField[] categoryFields;

    /**
     * The fields that allows a user to edit the lower bound on the range.
     */
    private DoubleTextField[] leftRangeFields;

    /**
     * The fields that allow a user to edit the upper bound on the range.
     */
    private DoubleTextField[] rightRangeFields;


    /**
     * Used to keep track of which compoent has focus.
     */
    private final LinkedList<JTextField> focusTraveralOrder = new LinkedList<>();

    /**
     * Label map.
     */
    private final Map<Object, Integer> labels = new HashMap<>();


    /**
     * States whether the editor is editable.
     */
    private final boolean editableRange;

    /**
     * Contructs the range editor given the variable that is being edited and
     * the continuous discreitization spec to base initial values on.
     */
    public RangeEditor(ContinuousDiscretizationSpec spec) {
        breakpoints = spec.getBreakpoints();
        categories = spec.getCategories();
        editableRange = true;
        this.buildEditor();
    }

    //=============================== Public Methods ===========================//


    /**
     * @return the <code>ContinuousDiscretizationSpec</code> that has been
     * created by the user.
     */
    public ContinuousDiscretizationSpec getDiscretizationSpec() {
        return new ContinuousDiscretizationSpec(breakpoints,
                categories);
    }

    //================================= Private Methods ==========================//


    /**
     * Builds the editor.
     */
    private void buildEditor() {
        Box rangeEditor = Box.createVerticalBox();

        this.createCategoryFields();
        this.createRangeFields();

        for (int i = 0; i < categories.size(); i++) {
            Box row = Box.createHorizontalBox();
            row.add(Box.createRigidArea(new Dimension(10, 0)));

            row.add(new JLabel((i + 1) + ". "));
            row.add(categoryFields[i]);
            row.add(new BigLabel(" = [ "));
            row.add(leftRangeFields[i]);
            row.add(new BigLabel(", "));
            row.add(rightRangeFields[i]);

            if (i < categories.size() - 1) {
                row.add(new BigLabel(" )"));
            } else {
                row.add(new BigLabel(" ]"));
            }

            row.add(Box.createHorizontalGlue());
            rangeEditor.add(row);
        }

        this.setLayout(new BorderLayout());
        this.add(rangeEditor, BorderLayout.CENTER);

        this.setFocusTraversalPolicy(new MyFocusTraversalPolicy());

        this.setFocusCycleRoot(true);
    }

    /**
     * Creates the category fields, these are allowed to be edited even when editable is false.
     */
    private void createCategoryFields() {
        categoryFields = new StringTextField[this.getNumCategories()];

        for (int i = 0; i < this.getNumCategories(); i++) {
            String category = categories.get(i);
            categoryFields[i] = new StringTextField(category, 6);
            StringTextField _field = categoryFields[i];

            categoryFields[i].setFilter(new StringTextField.Filter() {
                public String filter(String value, String oldValue) {
                    if (labels.get(_field) != null) {
                        int index = labels.get(_field);

                        if (value == null) {
                            value = categories.get(index);
                        }

                        for (int i = 0; i < categories.size(); i++) {
                            if (i != index &&
                                    categories.get(i).equals(value)) {
                                value = categories.get(index);
                            }
                        }

                        categories.set(index, value);
                    }

                    return value;
                }
            });

            labels.put(categoryFields[i], i);
            focusTraveralOrder.add(categoryFields[i]);
        }
    }

    /**
     * Creates the range fields, if the editor is not editable then all these fields should
     * be not editable.
     */
    private void createRangeFields() {
        leftRangeFields = new DoubleTextField[this.getNumCategories()];
        rightRangeFields = new DoubleTextField[this.getNumCategories()];

        int maxCategory = this.getNumCategories() - 1;

        leftRangeFields[0] = new DoubleTextField(
                Double.NEGATIVE_INFINITY, 6, NumberFormatUtil.getInstance().getNumberFormat());
        leftRangeFields[0].setFilter(
                new Filter() {
                    public double filter(double value, double oldValue) {
                        return oldValue;
                    }
                });

        rightRangeFields[maxCategory] = new DoubleTextField(
                Double.POSITIVE_INFINITY, 6, NumberFormatUtil.getInstance().getNumberFormat());
        rightRangeFields[maxCategory].setFilter(
                new Filter() {
                    public double filter(double value, double oldValue) {
                        return oldValue;
                    }
                });

        leftRangeFields[0].setEditable(false);
        rightRangeFields[maxCategory].setEditable(false);
        leftRangeFields[0].setHorizontalAlignment(JTextField.CENTER);
        rightRangeFields[maxCategory].setHorizontalAlignment(
                JTextField.CENTER);

        for (int i = 0; i < this.getNumCategories() - 1; i++) {
            rightRangeFields[i] = new DoubleTextField(breakpoints[i], 6, NumberFormatUtil.getInstance().getNumberFormat());
            rightRangeFields[i].setEditable(false);
            labels.put(rightRangeFields[i], i);

            leftRangeFields[i + 1] = new DoubleTextField(breakpoints[i], 6, NumberFormatUtil.getInstance().getNumberFormat());
            leftRangeFields[i + 1].setEditable(editableRange);
            labels.put(leftRangeFields[i + 1], i + 1);

            Object label = labels.get(leftRangeFields[i + 1]);
            leftRangeFields[i + 1].setFilter(
                    new Filter() {
                        public double filter(double value,
                                             double oldValue) {
                            if (label == null) {
                                return oldValue;
                            }

                            int index = (Integer) label;

                            if (index - 1 > 0 &&
                                    !(breakpoints[index - 2] < value)) {
                                value = breakpoints[index - 1];
                            }

                            if (index - 1 < breakpoints.length - 1 &&
                                    !(value < breakpoints[index])) {
                                value = breakpoints[index - 1];
                            }

                            breakpoints[index - 1] = value;

                            RangeEditor.this.getRightRangeFields()[index - 1].setValue(
                                    value);
                            return value;
                        }
                    });


            labels.put(leftRangeFields[i + 1], i + 1);
            focusTraveralOrder.add(leftRangeFields[i + 1]);
        }
    }

    private DoubleTextField[] getRightRangeFields() {
        return rightRangeFields;
    }

    private int getNumCategories() {
        return categories.size();
    }

    //========================== Inner Class ====================================//

    private static final class BigLabel extends JLabel {
        private static final Font FONT = new Font("Dialog", Font.BOLD, 20);

        public BigLabel(String text) {
            super(text);
            this.setFont(FONT);
        }
    }

    private class MyFocusTraversalPolicy extends FocusTraversalPolicy {
        public Component getComponentAfter(Container focusCycleRoot,
                                           Component aComponent) {
            int index = focusTraveralOrder.indexOf(aComponent);
            int size = focusTraveralOrder.size();

            if (index != -1) {
                return focusTraveralOrder.get((index + 1) % size);
            } else {
                return this.getFirstComponent(focusCycleRoot);
            }
        }

        public Component getComponentBefore(Container focusCycleRoot,
                                            Component aComponent) {
            int index = focusTraveralOrder.indexOf(aComponent);
            int size = focusTraveralOrder.size();

            if (index != -1) {
                return focusTraveralOrder.get((index - 1) % size);
            } else {
                return this.getFirstComponent(focusCycleRoot);
            }
        }

        public Component getFirstComponent(Container focusCycleRoot) {
            return focusTraveralOrder.getFirst();
        }

        public Component getLastComponent(Container focusCycleRoot) {
            return focusTraveralOrder.getLast();
        }

        public Component getDefaultComponent(Container focusCycleRoot) {
            return this.getFirstComponent(focusCycleRoot);
        }
    }
}




