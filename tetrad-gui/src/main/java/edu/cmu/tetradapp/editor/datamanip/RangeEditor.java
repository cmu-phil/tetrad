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

import edu.cmu.tetrad.data.ContinuousDiscretizationSpec;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.util.DoubleTextField;
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
 * @author josephramsey
 * @author Tyler Gibson
 */
@SuppressWarnings("SuspiciousMethodCalls")
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
     * Contructs the range editor given the variable that is being edited and the continuous discreitization spec to
     * base initial values on.
     *
     * @param spec a {@link edu.cmu.tetrad.data.ContinuousDiscretizationSpec} object
     */
    public RangeEditor(ContinuousDiscretizationSpec spec) {
        this.breakpoints = spec.getBreakpoints();
        this.categories = spec.getCategories();
        this.editableRange = true;
        buildEditor();
    }

    //=============================== Public Methods ===========================//


    /**
     * <p>getDiscretizationSpec.</p>
     *
     * @return the <code>ContinuousDiscretizationSpec</code> that has been created by the user.
     */
    public ContinuousDiscretizationSpec getDiscretizationSpec() {
        return new ContinuousDiscretizationSpec(this.breakpoints,
                this.categories);
    }

    //================================= Private Methods ==========================//


    /**
     * Builds the editor.
     */
    private void buildEditor() {
        Box rangeEditor = Box.createVerticalBox();

        createCategoryFields();
        createRangeFields();

        for (int i = 0; i < this.categories.size(); i++) {
            Box row = Box.createHorizontalBox();
            row.add(Box.createRigidArea(new Dimension(10, 0)));

            row.add(new JLabel((i + 1) + ". "));
            row.add(this.categoryFields[i]);
            row.add(new BigLabel(" = [ "));
            row.add(this.leftRangeFields[i]);
            row.add(new BigLabel(", "));
            row.add(this.rightRangeFields[i]);

            if (i < this.categories.size() - 1) {
                row.add(new BigLabel(" )"));
            } else {
                row.add(new BigLabel(" ]"));
            }

            row.add(Box.createHorizontalGlue());
            rangeEditor.add(row);
        }

        setLayout(new BorderLayout());
        add(rangeEditor, BorderLayout.CENTER);

        setFocusTraversalPolicy(new MyFocusTraversalPolicy());

        setFocusCycleRoot(true);
    }

    /**
     * Creates the category fields, these are allowed to be edited even when editable is false.
     */
    private void createCategoryFields() {
        this.categoryFields = new StringTextField[getNumCategories()];

        for (int i = 0; i < getNumCategories(); i++) {
            String category = this.categories.get(i);
            this.categoryFields[i] = new StringTextField(category, 6);
            StringTextField _field = this.categoryFields[i];

            this.categoryFields[i].setFilter((value, oldValue) -> {
                if (RangeEditor.this.labels.get(_field) != null) {
                    int index = RangeEditor.this.labels.get(_field);

                    if (value == null) {
                        value = RangeEditor.this.categories.get(index);
                    }

                    for (int i1 = 0; i1 < RangeEditor.this.categories.size(); i1++) {
                        if (i1 != index &&
                            RangeEditor.this.categories.get(i1).equals(value)) {
                            value = RangeEditor.this.categories.get(index);
                            break;
                        }
                    }

                    RangeEditor.this.categories.set(index, value);
                }

                return value;
            });

            this.labels.put(this.categoryFields[i], i);
            this.focusTraveralOrder.add(this.categoryFields[i]);
        }
    }

    /**
     * Creates the range fields, if the editor is not editable then all these fields should be not editable.
     */
    private void createRangeFields() {
        this.leftRangeFields = new DoubleTextField[getNumCategories()];
        this.rightRangeFields = new DoubleTextField[getNumCategories()];

        int maxCategory = getNumCategories() - 1;

        this.leftRangeFields[0] = new DoubleTextField(
                Double.NEGATIVE_INFINITY, 6, NumberFormatUtil.getInstance().getNumberFormat());
        this.leftRangeFields[0].setFilter(
                (value, oldValue) -> oldValue);

        rightRangeFields[maxCategory] = new DoubleTextField(
                Double.POSITIVE_INFINITY, 6, NumberFormatUtil.getInstance().getNumberFormat());
        rightRangeFields[maxCategory].setFilter(
                (value, oldValue) -> oldValue);

        leftRangeFields[0].setEditable(false);
        rightRangeFields[maxCategory].setEditable(false);
        leftRangeFields[0].setHorizontalAlignment(SwingConstants.CENTER);
        rightRangeFields[maxCategory].setHorizontalAlignment(
                SwingConstants.CENTER);

        for (int i = 0; i < this.getNumCategories() - 1; i++) {
            rightRangeFields[i] = new DoubleTextField(breakpoints[i], 6, NumberFormatUtil.getInstance().getNumberFormat());
            rightRangeFields[i].setEditable(false);
            labels.put(rightRangeFields[i], i);

            leftRangeFields[i + 1] = new DoubleTextField(breakpoints[i], 6, NumberFormatUtil.getInstance().getNumberFormat());
            leftRangeFields[i + 1].setEditable(editableRange);
            labels.put(leftRangeFields[i + 1], i + 1);

            Integer label = labels.get(leftRangeFields[i + 1]);
            leftRangeFields[i + 1].setFilter(
                    (value, oldValue) -> {
                        if (label == null) {
                            return oldValue;
                        }

                        int index = label;

                        if (index - 1 > 0 &&
                            !(RangeEditor.this.breakpoints[index - 2] < value)) {
                            value = RangeEditor.this.breakpoints[index - 1];
                        }

                        if (index - 1 < RangeEditor.this.breakpoints.length - 1 &&
                            !(value < RangeEditor.this.breakpoints[index])) {
                            value = RangeEditor.this.breakpoints[index - 1];
                        }

                        RangeEditor.this.breakpoints[index - 1] = value;

                        getRightRangeFields()[index - 1].setValue(
                                value);
                        return value;
                    });


            this.labels.put(this.leftRangeFields[i + 1], i + 1);
            this.focusTraveralOrder.add(this.leftRangeFields[i + 1]);
        }
    }

    private DoubleTextField[] getRightRangeFields() {
        return this.rightRangeFields;
    }

    private int getNumCategories() {
        return this.categories.size();
    }

    //========================== Inner Class ====================================//

    private static final class BigLabel extends JLabel {
        private static final Font FONT = new Font("Dialog", Font.BOLD, 20);

        public BigLabel(String text) {
            super(text);
            setFont(BigLabel.FONT);
        }
    }

    private class MyFocusTraversalPolicy extends FocusTraversalPolicy {
        public Component getComponentAfter(Container focusCycleRoot,
                                           Component aComponent) {
            int index = RangeEditor.this.focusTraveralOrder.indexOf(aComponent);
            int size = RangeEditor.this.focusTraveralOrder.size();

            if (index != -1) {
                return RangeEditor.this.focusTraveralOrder.get((index + 1) % size);
            } else {
                return getFirstComponent(focusCycleRoot);
            }
        }

        public Component getComponentBefore(Container focusCycleRoot,
                                            Component aComponent) {
            int index = RangeEditor.this.focusTraveralOrder.indexOf(aComponent);
            int size = RangeEditor.this.focusTraveralOrder.size();

            if (index != -1) {
                return RangeEditor.this.focusTraveralOrder.get((index - 1) % size);
            } else {
                return getFirstComponent(focusCycleRoot);
            }
        }

        public Component getFirstComponent(Container focusCycleRoot) {
            return RangeEditor.this.focusTraveralOrder.getFirst();
        }

        public Component getLastComponent(Container focusCycleRoot) {
            return RangeEditor.this.focusTraveralOrder.getLast();
        }

        public Component getDefaultComponent(Container focusCycleRoot) {
            return getFirstComponent(focusCycleRoot);
        }
    }
}




