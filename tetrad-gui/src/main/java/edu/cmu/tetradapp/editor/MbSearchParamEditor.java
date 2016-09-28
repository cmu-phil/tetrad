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
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GraphSource;
import edu.cmu.tetradapp.model.IndependenceFactsModel;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Edits parameters for Markov blanket search algorithm.
 *
 * @author Ricardo Silva (GES version)
 * @author Frank Wimberly adapted for PCX.
 */
public final class MbSearchParamEditor extends JPanel implements ParameterEditor {

    /**
     * The parameter object being edited.
     */
    private Parameters params;

    /**
     * The name of the target variable or node in the PCX search.
     */
    private String targetName;

    private Object[] parentModels;

    /**
     * Opens up an editor to let the user view the given PcRunner.
     */
    public MbSearchParamEditor() {
    }

    public void setParams(Parameters params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = params;
    }

    public void setParentModels(Object[] parentModels) {
        if (parentModels == null) {
            throw new NullPointerException();
        }

        this.parentModels = parentModels;
    }

    public void setup() {
        /*
      The variable names from the object being searched over (usually data).
     */
        List<String> varNames = (List<String>) params().get("varNames", null);

        if (varNames == null) {
            varNames = getVarsFromData(parentModels);

            if (varNames == null) {
                varNames = getVarsFromGraph(parentModels);
            }

            if (varNames == null) {
                throw new IllegalStateException(
                        "Variables are not accessible.");
            }

            params().set("varNames", varNames);
        }

        setBorder(new MatteBorder(10, 10, 10, 10, super.getBackground()));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        // Set up components.
        String[] variableNames = varNames.toArray(new String[varNames.size()]);
        Arrays.sort(variableNames);
        JComboBox varsBox = new JComboBox(variableNames);
        varsBox.setMaximumSize(new Dimension(80, 24));
        varsBox.setPreferredSize(new Dimension(80, 24));

        String targetName = params.getString("targetName", null);
        if (!Arrays.asList(variableNames).contains(targetName)) {
            params.set("targetName", variableNames[0]);
            targetName = params.getString("targetName", null);
        }

        if (targetName == null) {
            targetName = (String) varsBox.getSelectedItem();
        } else {
            varsBox.setSelectedItem(targetName);
        }

        setTargetName(targetName);
        params().set("targetName", targetName());

        varsBox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                JComboBox box = (JComboBox) e.getSource();
                setTargetName((String) box.getSelectedItem());
                params().set("targetName", targetName());
            }
        });

        DoubleTextField alphaField = new DoubleTextField(params.getDouble("alpha", 0.001), 8,
                new DecimalFormat("0.0########"));
        alphaField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    params().set("alpha", 0.001);
                    Preferences.userRoot().putDouble("alpha",
                            params().getDouble("alpha", 0.001));
                    return value;
                }
                catch (Exception e) {
                    return oldValue;
                }
            }
        });

        IntTextField pcDepthField = new IntTextField(params().getInt("depth", -1), 4);
        pcDepthField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    params().set("depth", value);
                    Preferences.userRoot().putInt("pcDepth",
                            params().getInt("depth", -1));
                    return value;
                }
                catch (Exception e) {
                    return oldValue;
                }
            }
        });

        JButton knowledgeButton = new JButton("Edit");
        knowledgeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                openKnowledgeEditor();
            }
        });

        JCheckBox preventCycles = new JCheckBox();
        preventCycles.setSelected(params.getBoolean("aggressivelyPreventCycles", false));
        preventCycles.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox box = (JCheckBox) e.getSource();
                Parameters p = params;
                p.set("aggressivelyPreventCycles", box.isSelected());
            }
        });

        // Do Layout.
        Box hBox = Box.createHorizontalBox();
        hBox.add(new JLabel("Aggressively Prevent Cycles:"));
        hBox.add(Box.createHorizontalGlue());
        hBox.add(preventCycles);
        add(hBox);
        add(Box.createVerticalStrut(5));


        Box b0 = Box.createHorizontalBox();
        b0.add(new JLabel("Target:"));
        b0.add(Box.createRigidArea(new Dimension(10, 0)));
        b0.add(Box.createHorizontalGlue());
        b0.add(varsBox);
        add(b0);

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Alpha:"));
        b1.add(Box.createRigidArea(new Dimension(10, 0)));
        b1.add(Box.createHorizontalGlue());
        b1.add(alphaField);
        add(b1);

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Depth:"));
        b2.add(Box.createRigidArea(new Dimension(10, 0)));
        b2.add(Box.createHorizontalGlue());
        b2.add(pcDepthField);
        add(b2);

//        Box b3 = Box.createHorizontalBox();
//        b3.add(new JLabel("Knowledge:"));
//        b3.add(Box.createRigidArea(new Dimension(10, 0)));
//        b3.add(Box.createHorizontalGlue());
//        b3.add(knowledgeButton);
//
//
//        add(b3);
    }

    public boolean mustBeShown() {
        return false;
    }

    private List<String> getVarsFromData(Object[] parentModels) {
        DataModel dataModel = null;

        for (Object parentModel : parentModels) {
            if (parentModel instanceof DataWrapper) {
                DataWrapper dataWrapper = (DataWrapper) parentModel;
                dataModel = dataWrapper.getSelectedDataModel();
            }
            else if (parentModel instanceof IndependenceFactsModel) {
                dataModel = ((IndependenceFactsModel) parentModel).getFacts();
            }
        }

        if (dataModel == null) {
            return null;
        } else {
            return new ArrayList<>(dataModel.getVariableNames());
        }
    }

    private List<String> getVarsFromGraph(Object[] parentModels) {
        GraphSource graphSource = null;

        for (Object parentModel : parentModels) {
            if (parentModel instanceof GraphSource) {
                graphSource = (GraphSource) parentModel;
            }
        }

        if (graphSource == null) {
            return null;
        } else {
            return graphSource.getGraph().getNodeNames();
        }
    }

    /**
     * Opens the knowledge editor.
     */
    private void openKnowledgeEditor() {
        if (params() == null) {
            throw new NullPointerException("Parameter object must not be " +
                    "null if you want to launch a OldKnowledgeEditor.");
        }

        if (params().get("knowledge", new Knowledge2()) == null) {
            throw new NullPointerException(
                    "Knowledge in params object must " + "not be null.");
        }
    }

    private Parameters params() {
        return this.params;
    }

    private String targetName() {
        return targetName;
    }

    private void setTargetName(String targetName) {
        this.targetName = targetName;
    }
}





