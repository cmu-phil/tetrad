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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.SplitCasesSpec;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.StringTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Edits parameters for splitting a dataset by cases. One parameter is
 * whether the data should be split in original order or in a shuffled order.
 * The other set of parameters is what the breakpoints should be.
 *
 * @author Joseph Ramsey
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
     * A panel to hold the split editor in case the user changes her mind
     * about the number of splits (in which case a new split editor needs
     * to be put here).
     */
    private JPanel splitEditorPanel;

    //==============================CONSTRUCTORS========================//

    /**
     * Constructs a JPanel to edit the discretization for a continuous
     * variable.
     */
    public SplitCasesParamsEditor() {

    }

    //================================PUBLIC METHODS=======================//

    private void setNumSplits(final int numSplits) {
        if (numSplits < 1) {
            throw new IllegalArgumentException("Number of splits must be " +
                    "at least 1.");
        }

        this.params.set("numSplits", numSplits);
        this.splitEditorPanel.removeAll();
        final SplitCasesSpec defaultSpec = getDefaultSpec(this.dataSet.getNumRows(), numSplits);
        final SplitEditor splitEditor = new SplitEditor(defaultSpec);
        this.params.set("splitCasesSpec", defaultSpec);
        this.splitEditorPanel.add(splitEditor, BorderLayout.CENTER);
        this.splitEditorPanel.revalidate();
        this.splitEditorPanel.repaint();
        this.numSplitsField.setText(String.valueOf(numSplits));
        Preferences.userRoot().putInt("latestNumCategories", numSplits);
    }

    public void setup() {
        SplitCasesSpec spec = (SplitCasesSpec) this.params.get("splitCasesSpec", null);
        if (spec != null) {
            spec = getDefaultSpec(this.dataSet.getNumRows(), this.params.getInt("numSplits", 3));
        }
        this.numSplitsField = new IntTextField(this.params.getInt("numSplits", 3), 2);
        this.numSplitsField.setFilter(new IntTextField.Filter() {
            public int filter(final int value, final int oldValue) {
                setNumSplits(value);
                return value;
            }
        });

        this.splitEditorPanel = new JPanel();
        this.splitEditorPanel.setLayout(new BorderLayout());
        setNumSplits(this.params.getInt("numSplits", 3));

        final JRadioButton shuffleButton = new JRadioButton("Shuffled order");
        final JRadioButton noShuffleButton = new JRadioButton("Original order");

        shuffleButton.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                SplitCasesParamsEditor.this.params.set("dataShuffled", true);
            }
        });

        noShuffleButton.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                SplitCasesParamsEditor.this.params.set("dataShuffled", false);
            }
        });

        final ButtonGroup group = new ButtonGroup();
        group.add(shuffleButton);
        group.add(noShuffleButton);
        shuffleButton.setSelected(this.params.getBoolean("dataShuffled", true));

        final Box b1 = Box.createVerticalBox();

        final Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Using data in:"));
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);

        final Box b3 = Box.createHorizontalBox();
        b3.add(Box.createHorizontalStrut(10));
        b3.add(shuffleButton);
        b3.add(Box.createHorizontalGlue());
        b1.add(b3);

        final Box b4 = Box.createHorizontalBox();
        b4.add(Box.createHorizontalStrut(10));
        b4.add(noShuffleButton);
        b4.add(Box.createHorizontalGlue());
        b1.add(b4);
        b1.add(Box.createVerticalStrut(10));

        final Box b5 = Box.createHorizontalBox();
        b5.add(new JLabel("Split data into "));
        b5.add(this.numSplitsField);
        b5.add(new JLabel(" subsets as follows:"));
        b5.add(Box.createHorizontalGlue());
        b1.add(b5);
        b1.add(Box.createVerticalStrut(10));

        b1.add(this.splitEditorPanel);
        b1.add(Box.createVerticalGlue());

        setLayout(new BorderLayout());
        add(b1, BorderLayout.CENTER);
    }


    public void setParams(final Parameters params) {
        this.params = params;
    }


    public void setParentModels(final Object[] parentModels) {
        if (parentModels == null || parentModels.length == 0) {
            throw new IllegalArgumentException("There must be parent model");
        }
        DataWrapper data = null;
        for (final Object parent : parentModels) {
            if (parent instanceof DataWrapper) {
                data = (DataWrapper) parent;
            }
        }
        if (data == null) {
            throw new IllegalArgumentException("Should have have a data wrapper as a parent");
        }
        final DataModel model = data.getSelectedDataModel();
        if (!(model instanceof DataSet)) {
            throw new IllegalArgumentException("The data must be tabular");
        }
        this.dataSet = (DataSet) model;
    }


    public boolean mustBeShown() {
        return true;
    }

    //==============================PRIVATE METHODS=======================//


    private static SplitCasesSpec getDefaultSpec(final int sampleSize, final int numSplits) {
        final int[] breakpoints = defaultBreakpoints(sampleSize, numSplits);
        final List<String> splitNames = new LinkedList<>();

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

    private static int[] defaultBreakpoints(final int sampleSize, final int numSplits) {
        final int interval = sampleSize / numSplits;
        final int[] breakpoints = new int[numSplits - 1];
        for (int i = 0; i < breakpoints.length; i++) {
            breakpoints[i] = (i + 1) * interval;
        }
        return breakpoints;
    }


    //============================STATIC CLASSES=======================//

    final static class SplitEditor extends JComponent {
        private final int[] breakpoints;
        private final List<String> splitNames;
        private StringTextField[] splitNameFields;
        private IntTextField[] leftSplitFields;
        private IntTextField[] rightSplitFields;

        private final LinkedList<JTextField> focusTraveralOrder =
                new LinkedList<>();
        private final Map<Object, Integer> labels = new HashMap<>();
        private final int sampleSize;

        public SplitEditor(final SplitCasesSpec spec) {
            this.sampleSize = spec.getSampleSize();
            this.breakpoints = spec.getBreakpoints();
            this.splitNames = spec.getSplitNames();

            final Box rangeEditor = Box.createVerticalBox();

            createSplitNameFields();
            createRangeFields();

            for (int i = 0; i < this.splitNames.size(); i++) {
                final Box row = Box.createHorizontalBox();
                row.add(Box.createRigidArea(new Dimension(10, 0)));

                row.add(new JLabel("Name = "));
                row.add(this.splitNameFields[i]);
                row.add(new JLabel(" : row "));
                row.add(this.leftSplitFields[i]);
                row.add(new JLabel(" to row "));
                row.add(this.rightSplitFields[i]);

                row.add(Box.createHorizontalGlue());
                rangeEditor.add(row);
            }

            setLayout(new BorderLayout());
            add(rangeEditor, BorderLayout.CENTER);

            setFocusTraversalPolicy(new FocusTraversalPolicy() {
                public Component getComponentAfter(final Container focusCycleRoot,
                                                   final Component aComponent) {
                    final int index = SplitEditor.this.focusTraveralOrder.indexOf(aComponent);
                    final int size = SplitEditor.this.focusTraveralOrder.size();

                    if (index != -1) {
                        return SplitEditor.this.focusTraveralOrder.get((index + 1) % size);
                    } else {
                        return getFirstComponent(focusCycleRoot);
                    }
                }

                public Component getComponentBefore(final Container focusCycleRoot,
                                                    final Component aComponent) {
                    final int index = SplitEditor.this.focusTraveralOrder.indexOf(aComponent);
                    final int size = SplitEditor.this.focusTraveralOrder.size();

                    if (index != -1) {
                        return SplitEditor.this.focusTraveralOrder.get((index - 1) % size);
                    } else {
                        return getFirstComponent(focusCycleRoot);
                    }
                }

                public Component getFirstComponent(final Container focusCycleRoot) {
                    return SplitEditor.this.focusTraveralOrder.getFirst();
                }

                public Component getLastComponent(final Container focusCycleRoot) {
                    return SplitEditor.this.focusTraveralOrder.getLast();
                }

                public Component getDefaultComponent(final Container focusCycleRoot) {
                    return getFirstComponent(focusCycleRoot);
                }
            });

            setFocusCycleRoot(true);
        }

        private void createSplitNameFields() {
            this.splitNameFields = new StringTextField[getNumSplits()];

            for (int i = 0; i < getNumSplits(); i++) {
                final String split = this.splitNames.get(i);
                this.splitNameFields[i] = new StringTextField(split, 6);
                final StringTextField _field = this.splitNameFields[i];

                this.splitNameFields[i].setFilter(new StringTextField.Filter() {
                    public String filter(String value, final String oldValue) {
                        if (SplitEditor.this.labels.get(_field) != null) {
                            final int index = SplitEditor.this.labels.get(_field);

                            if (value == null) {
                                value = SplitEditor.this.splitNames.get(index);
                            }

                            for (int i = 0; i < SplitEditor.this.splitNames.size(); i++) {
                                if (i != index && SplitEditor.this.splitNames.get(i).equals(value)) {
                                    value = SplitEditor.this.splitNames.get(index);
                                }
                            }

                            SplitEditor.this.splitNames.set(index, value);
                        }

                        return value;
                    }
                });

                this.labels.put(this.splitNameFields[i], i);
                this.focusTraveralOrder.add(this.splitNameFields[i]);
            }
        }

        private void createRangeFields() {
            this.leftSplitFields = new IntTextField[getNumSplits()];
            this.rightSplitFields = new IntTextField[getNumSplits()];

            final int maxSplit = getNumSplits() - 1;

            this.leftSplitFields[0] = new IntTextField(1, 6);
            this.leftSplitFields[0].setFilter(
                    new IntTextField.Filter() {
                        public int filter(final int value, final int oldValue) {
                            return oldValue;
                        }
                    });

            this.rightSplitFields[maxSplit] = new IntTextField(this.sampleSize, 6);
            this.rightSplitFields[maxSplit].setFilter(
                    new IntTextField.Filter() {
                        public int filter(final int value, final int oldValue) {
                            return oldValue;
                        }
                    });

            this.leftSplitFields[0].setEditable(false);
            this.rightSplitFields[maxSplit].setEditable(false);

            for (int i = 0; i < getNumSplits() - 1; i++) {
                this.rightSplitFields[i] = new IntTextField(this.breakpoints[i] - 1, 6);
                this.rightSplitFields[i].setEditable(false);
                this.labels.put(this.rightSplitFields[i], i);

                this.leftSplitFields[i + 1] = new IntTextField(this.breakpoints[i], 6);
                this.labels.put(this.leftSplitFields[i + 1], i + 1);
                final Object label = this.labels.get(this.leftSplitFields[i + 1]);

                this.leftSplitFields[i + 1].setFilter(
                        new IntTextField.Filter() {
                            public int filter(int value, final int oldValue) {
                                if (label == null) {
                                    return oldValue;
                                }

                                final int index = (Integer) label;

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
                            }
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

        public int[] getBreakpoints() {
            return this.breakpoints;
        }

        public List<String> getSplitNames() {
            return this.splitNames;
        }

        public int getSampleSize() {
            return this.sampleSize;
        }
    }
}



