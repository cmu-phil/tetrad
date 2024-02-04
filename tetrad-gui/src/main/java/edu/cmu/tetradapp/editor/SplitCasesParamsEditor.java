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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.SplitCasesSpec;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.StringTextField;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Edits parameters for splitting a dataset by cases. One parameter is whether the data should be split in original
 * order or in a shuffled order. The other set of parameters is what the breakpoints should be.
 *
 * @author josephramsey
 */
public class SplitCasesParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The dataset being split.
     */
    private DataSet dataSet;

    /**
     * A field to store the number of desired splits of the data.
     */
    private IntTextField numSplitsField;


    /**
     * The params used to store the values this editor edits.
     */
    private Parameters params;


    /**
     * A panel to hold the split editor in case the user changes her mind about the number of splits (in which case a
     * new split editor needs to be put here).
     */
    private JPanel splitEditorPanel;

    //==============================CONSTRUCTORS========================//

    /**
     * Constructs a JPanel to edit the discretization for a continuous variable.
     */
    public SplitCasesParamsEditor() {

    }

    //================================PUBLIC METHODS=======================//

    private static SplitCasesSpec getDefaultSpec(int sampleSize, int numSplits) {
        int[] breakpoints = defaultBreakpoints(sampleSize, numSplits);
        List<String> splitNames = new LinkedList<>();

        if (numSplits == 1) {
            splitNames.add("same_data");
        } else if (numSplits == 2) {
            splitNames.add("train");
            splitNames.add("test");
        } else {
            for (int i = 0; i < numSplits; i++) {
                splitNames.add("split_" + i);
            }
        }

        return new SplitCasesSpec(sampleSize, breakpoints, splitNames);
    }

    private static int[] defaultBreakpoints(int sampleSize, int numSplits) {
        int interval = sampleSize / numSplits;
        int[] breakpoints = new int[numSplits - 1];
        for (int i = 0; i < breakpoints.length; i++) {
            breakpoints[i] = (i + 1) * interval;
        }
        return breakpoints;
    }

    private void setNumSplits(int numSplits) {
        if (numSplits < 1) {
            throw new IllegalArgumentException("Number of splits must be " +
                    "at least 1.");
        }

        this.params.set("numSplits", numSplits);
        this.splitEditorPanel.removeAll();
        SplitCasesSpec defaultSpec = SplitCasesParamsEditor.getDefaultSpec(this.dataSet.getNumRows(), numSplits);
        SplitEditor splitEditor = new SplitEditor(defaultSpec);
        this.params.set("splitCasesSpec", defaultSpec);
        this.splitEditorPanel.add(splitEditor, BorderLayout.CENTER);
        this.splitEditorPanel.revalidate();
        this.splitEditorPanel.repaint();
        this.numSplitsField.setText(String.valueOf(numSplits));
        Preferences.userRoot().putInt("latestNumCategories", numSplits);
    }

    public void setup() {
        this.numSplitsField = new IntTextField(this.params.getInt("numSplits", 3), 2);
        this.numSplitsField.setFilter((value, oldValue) -> {
            SplitCasesParamsEditor.this.setNumSplits(value);
            return value;
        });

        splitEditorPanel = new JPanel();
        splitEditorPanel.setLayout(new BorderLayout());
        this.setNumSplits(params.getInt("numSplits", 2));

        JRadioButton shuffleButton = new JRadioButton("Shuffled order");
        JRadioButton noShuffleButton = new JRadioButton("Original order");

        shuffleButton.addActionListener(e -> params.set("dataShuffled", true));

        noShuffleButton.addActionListener(e -> params.set("dataShuffled", false));

        ButtonGroup group = new ButtonGroup();
        group.add(shuffleButton);
        group.add(noShuffleButton);
        shuffleButton.setSelected(params.getBoolean("dataShuffled", true));

        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Using data in:"));
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);

        Box b3 = Box.createHorizontalBox();
        b3.add(Box.createHorizontalStrut(10));
        b3.add(shuffleButton);
        b3.add(Box.createHorizontalGlue());
        b1.add(b3);

        Box b4 = Box.createHorizontalBox();
        b4.add(Box.createHorizontalStrut(10));
        b4.add(noShuffleButton);
        b4.add(Box.createHorizontalGlue());
        b1.add(b4);
        b1.add(Box.createVerticalStrut(10));

        Box b5 = Box.createHorizontalBox();
        b5.add(new JLabel("Split data into "));
        b5.add(numSplitsField);
        b5.add(new JLabel(" subsets as follows:"));
        b5.add(Box.createHorizontalGlue());
        b1.add(b5);
        b1.add(Box.createVerticalStrut(10));

        b1.add(splitEditorPanel);
        b1.add(Box.createVerticalGlue());

        this.setLayout(new BorderLayout());
        this.add(b1, BorderLayout.CENTER);
    }

    public void setParams(Parameters params) {
        this.params = params;
    }

    //==============================PRIVATE METHODS=======================//

    public void setParentModels(Object[] parentModels) {
        if (parentModels == null || parentModels.length == 0) {
            throw new IllegalArgumentException("There must be parent model");
        }
        DataWrapper data = null;
        for (Object parent : parentModels) {
            if (parent instanceof DataWrapper) {
                data = (DataWrapper) parent;
            }
        }
        if (data == null) {
            throw new IllegalArgumentException("Should have have a data wrapper as a parent");
        }
        DataModel model = data.getSelectedDataModel();
        if (!(model instanceof DataSet)) {
            throw new IllegalArgumentException("The data must be tabular");
        }
        dataSet = (DataSet) model;
    }

    public boolean mustBeShown() {
        return true;
    }


    //============================STATIC CLASSES=======================//

    static final class SplitEditor extends JComponent {
        private final int[] breakpoints;
        private final List<String> splitNames;
        private final LinkedList<JTextField> focusTraveralOrder =
                new LinkedList<>();
        private final Map<Object, Integer> labels = new HashMap<>();
        private final int sampleSize;
        private StringTextField[] splitNameFields;
        private IntTextField[] leftSplitFields;
        private IntTextField[] rightSplitFields;

        public SplitEditor(SplitCasesSpec spec) {
            sampleSize = spec.getSampleSize();
            breakpoints = spec.getBreakpoints();
            splitNames = spec.getSplitNames();

            Box rangeEditor = Box.createVerticalBox();

            this.createSplitNameFields();
            this.createRangeFields();

            for (int i = 0; i < splitNames.size(); i++) {
                Box row = Box.createHorizontalBox();
                row.add(Box.createRigidArea(new Dimension(10, 0)));

                row.add(new JLabel("Name = "));
                row.add(splitNameFields[i]);
                row.add(new JLabel(" : row "));
                row.add(leftSplitFields[i]);
                row.add(new JLabel(" to row "));
                row.add(rightSplitFields[i]);

                row.add(Box.createHorizontalGlue());
                rangeEditor.add(row);
            }

            this.setLayout(new BorderLayout());
            this.add(rangeEditor, BorderLayout.CENTER);

            this.setFocusTraversalPolicy(new FocusTraversalPolicy() {
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
            });

            this.setFocusCycleRoot(true);
        }

        private void createSplitNameFields() {
            splitNameFields = new StringTextField[this.getNumSplits()];

            for (int i = 0; i < this.getNumSplits(); i++) {
                String split = splitNames.get(i);
                splitNameFields[i] = new StringTextField(split, 6);
                StringTextField _field = splitNameFields[i];

                splitNameFields[i].setFilter((value, oldValue) -> {
                    if (labels.get(_field) != null) {
                        int index = labels.get(_field);

                        if (value == null) {
                            value = splitNames.get(index);
                        }

                        for (int i1 = 0; i1 < splitNames.size(); i1++) {
                            if (i1 != index && splitNames.get(i1).equals(value)) {
                                value = splitNames.get(index);
                                break;
                            }
                        }

                        splitNames.set(index, value);
                    }

                    return value;
                });

                labels.put(splitNameFields[i], i);
                focusTraveralOrder.add(splitNameFields[i]);
            }
        }

        private void createRangeFields() {
            leftSplitFields = new IntTextField[this.getNumSplits()];
            rightSplitFields = new IntTextField[this.getNumSplits()];

            int maxSplit = this.getNumSplits() - 1;

            leftSplitFields[0] = new IntTextField(1, 6);
            leftSplitFields[0].setFilter(
                    (value, oldValue) -> oldValue);

            rightSplitFields[maxSplit] = new IntTextField(sampleSize, 6);
            rightSplitFields[maxSplit].setFilter(
                    (value, oldValue) -> oldValue);

            leftSplitFields[0].setEditable(false);
            rightSplitFields[maxSplit].setEditable(false);

            for (int i = 0; i < this.getNumSplits() - 1; i++) {
                rightSplitFields[i] = new IntTextField(breakpoints[i] - 1, 6);
                rightSplitFields[i].setEditable(false);
                labels.put(rightSplitFields[i], i);

                leftSplitFields[i + 1] = new IntTextField(breakpoints[i], 6);
                labels.put(leftSplitFields[i + 1], i + 1);
                Integer label = labels.get(leftSplitFields[i + 1]);

                leftSplitFields[i + 1].setFilter(
                        (value, oldValue) -> {
                            if (label == null) {
                                return oldValue;
                            }

                            int index = label;

                            if (index - 1 > 0 &&
                                    !(SplitEditor.this.breakpoints[index - 2] < value)) {
                                value = SplitEditor.this.breakpoints[index - 1];
                            }

                            if (index - 1 < SplitEditor.this.breakpoints.length - 1 &&
                                    !(value < SplitEditor.this.breakpoints[index])) {
                                value = SplitEditor.this.breakpoints[index - 1];
                            }

                            SplitEditor.this.breakpoints[index - 1] = value;

                            getRightSplitFields()[index - 1].setValue(
                                    value - 1);
                            return value;
                        });

                this.labels.put(this.leftSplitFields[i + 1], i + 1);
                this.focusTraveralOrder.add(this.leftSplitFields[i + 1]);
            }
        }

        private IntTextField[] getRightSplitFields() {
            return this.rightSplitFields;
        }

        private int getNumSplits() {
            return this.splitNames.size();
        }

        public List<String> getSplitNames() {
            return this.splitNames;
        }

        public int getSampleSize() {
            return this.sampleSize;
        }
    }
}



