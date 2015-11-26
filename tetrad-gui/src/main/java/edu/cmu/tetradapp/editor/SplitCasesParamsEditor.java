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
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.datamanip.SplitCasesParams;
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
    private SplitCasesParams params;


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

    public void setNumSplits(int numSplits) {
        if (numSplits < 1) {
            throw new IllegalArgumentException("Number of splits must be " +
                    "at least 1.");
        }

        this.params.setNumSplits(numSplits);
        splitEditorPanel.removeAll();
        SplitCasesSpec defaultSpec = getDefaultSpec(this.dataSet.getNumRows(), numSplits);
        SplitEditor splitEditor = new SplitEditor(defaultSpec);
        this.params.setSpec(defaultSpec);
        splitEditorPanel.add(splitEditor, BorderLayout.CENTER);
        splitEditorPanel.revalidate();
        splitEditorPanel.repaint();
        numSplitsField.setText(String.valueOf(numSplits));
        Preferences.userRoot().putInt("latestNumCategories", numSplits);
    }

    public void setup() {
        SplitCasesSpec spec = this.params.getSpec();
        if(spec != null){
           spec = getDefaultSpec(this.dataSet.getNumRows(), this.params.getNumSplits());
        }
        numSplitsField = new IntTextField(this.params.getNumSplits(), 2);
        numSplitsField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                setNumSplits(value);
                return value;
            }
        });

        splitEditorPanel = new JPanel();
        splitEditorPanel.setLayout(new BorderLayout());
        setNumSplits(this.params.getNumSplits());

        JRadioButton shuffleButton = new JRadioButton("Shuffled order");
        JRadioButton noShuffleButton = new JRadioButton("Original order");

        shuffleButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                params.setDataShuffled(true);
            }
        });

        noShuffleButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                params.setDataShuffled(false);
            }
        });

        ButtonGroup group = new ButtonGroup();
        group.add(shuffleButton);
        group.add(noShuffleButton);
        shuffleButton.setSelected(this.params.isDataShuffled());

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

        setLayout(new BorderLayout());
        add(b1, BorderLayout.CENTER);
    }



    public void setParams(Params params) {
        this.params = (SplitCasesParams) params;
    }


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
        this.dataSet = (DataSet) model;
    }


    public boolean mustBeShown() {
        return true;
    }

    //==============================PRIVATE METHODS=======================//


    private static SplitCasesSpec getDefaultSpec(int sampleSize, int numSplits) {
        int[] breakpoints = defaultBreakpoints(sampleSize, numSplits);
        List<String> splitNames = new LinkedList<String>();

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


    //============================STATIC CLASSES=======================//

    final static class SplitEditor extends JComponent {
        private final int[] breakpoints;
        private final List<String> splitNames;
        private StringTextField[] splitNameFields;
        private IntTextField[] leftSplitFields;
        private IntTextField[] rightSplitFields;

        private LinkedList<JTextField> focusTraveralOrder =
                new LinkedList<JTextField>();
        private Map<Object, Integer> labels = new HashMap<Object, Integer>();
        private int sampleSize;

        public SplitEditor(SplitCasesSpec spec) {
            this.sampleSize = spec.getSampleSize();
            this.breakpoints = spec.getBreakpoints();
            this.splitNames = spec.getSplitNames();

            Box rangeEditor = Box.createVerticalBox();

            createSplitNameFields();
            createRangeFields();

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

            setLayout(new BorderLayout());
            add(rangeEditor, BorderLayout.CENTER);

            setFocusTraversalPolicy(new FocusTraversalPolicy() {
                public Component getComponentAfter(Container focusCycleRoot,
                                                   Component aComponent) {
                    int index = focusTraveralOrder.indexOf(aComponent);
                    int size = focusTraveralOrder.size();

                    if (index != -1) {
                        return focusTraveralOrder.get((index + 1) % size);
                    } else {
                        return getFirstComponent(focusCycleRoot);
                    }
                }

                public Component getComponentBefore(Container focusCycleRoot,
                                                    Component aComponent) {
                    int index = focusTraveralOrder.indexOf(aComponent);
                    int size = focusTraveralOrder.size();

                    if (index != -1) {
                        return focusTraveralOrder.get((index - 1) % size);
                    } else {
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

        private void createSplitNameFields() {
            splitNameFields = new StringTextField[getNumSplits()];

            for (int i = 0; i < getNumSplits(); i++) {
                String split = splitNames.get(i);
                splitNameFields[i] = new StringTextField(split, 6);
                final StringTextField _field = splitNameFields[i];

                splitNameFields[i].setFilter(new StringTextField.Filter() {
                    public String filter(String value, String oldValue) {
                        if (labels.get(_field) != null) {
                            int index = labels.get(_field);

                            if (value == null) {
                                value = splitNames.get(index);
                            }

                            for (int i = 0; i < splitNames.size(); i++) {
                                if (i != index && splitNames.get(i).equals(value)) {
                                    value = splitNames.get(index);
                                }
                            }

                            splitNames.set(index, value);
                        }

                        return value;
                    }
                });

                labels.put(this.splitNameFields[i], i);
                focusTraveralOrder.add(this.splitNameFields[i]);
            }
        }

        private void createRangeFields() {
            leftSplitFields = new IntTextField[getNumSplits()];
            rightSplitFields = new IntTextField[getNumSplits()];

            int maxSplit = getNumSplits() - 1;

            leftSplitFields[0] = new IntTextField(1, 6);
            leftSplitFields[0].setFilter(
                    new IntTextField.Filter() {
                        public int filter(int value, int oldValue) {
                            return oldValue;
                        }
                    });

            rightSplitFields[maxSplit] = new IntTextField(sampleSize, 6);
            rightSplitFields[maxSplit].setFilter(
                    new IntTextField.Filter() {
                        public int filter(int value, int oldValue) {
                            return oldValue;
                        }
                    });

            leftSplitFields[0].setEditable(false);
            rightSplitFields[maxSplit].setEditable(false);

            for (int i = 0; i < getNumSplits() - 1; i++) {
                rightSplitFields[i] = new IntTextField(breakpoints[i] - 1, 6);
                rightSplitFields[i].setEditable(false);
                labels.put(rightSplitFields[i], i);

                leftSplitFields[i + 1] = new IntTextField(breakpoints[i], 6);
                labels.put(leftSplitFields[i + 1], i + 1);
                final Object label = labels.get(leftSplitFields[i + 1]);

                leftSplitFields[i + 1].setFilter(
                        new IntTextField.Filter() {
                            public int filter(int value, int oldValue) {
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

                                getRightSplitFields()[index - 1].setValue(
                                        value - 1);
                                return value;
                            }
                        });

                labels.put(this.leftSplitFields[i + 1], i + 1);
                focusTraveralOrder.add(this.leftSplitFields[i + 1]);
            }
        }

        private IntTextField[] getRightSplitFields() {
            return rightSplitFields;
        }

        private int getNumSplits() {
            return splitNames.size();
        }

        public int[] getBreakpoints() {
            return breakpoints;
        }

        public List<String> getSplitNames() {
            return splitNames;
        }

        public int getSampleSize() {
            return sampleSize;
        }
    }
}



