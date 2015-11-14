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

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.Preferences;

/**
 * Edits the parameters for generating random graphs.
 *
 * @author Joseph Ramsey
 */
public class RandomGraphEditor extends JPanel {
    private IntTextField numNodesField;
    private IntTextField numLatentsField;
    private IntTextField maxEdgesField;
    private IntTextField maxIndegreeField;
    private IntTextField maxOutdegreeField;
    private IntTextField maxDegreeField;
    private JRadioButton randomForward;
    private JRadioButton chooseUniform;
    private JRadioButton chooseFixed;
    private JComboBox connectedBox;
    private JComboBox addCyclesBox;
    private IntTextField numTwoCyclesField;
    private IntTextField minCycleLengthField;

    // If true, the parameters for adding cycles will be displayed.
    private boolean cyclicAllowed = false;

    /**
     * Constructs a dialog to edit the given workbench randomization
     * parameters.
     * @param cyclicAllowed
     */
    public RandomGraphEditor(boolean cyclicAllowed) {
        this(new EdgeListGraph(), cyclicAllowed);
    }

    /**
     * Constructs a dialog to edit the given workbench randomization
     * parameters.
     * //     * @param preferredNumNodes an integer which, if greater than 1, will revise the number of nodes,
     * //     * number of edges,a nd number of latent nodes. Useful if the interface suggests a number of nodes
     * //     * that overrides the number of nodes set in the preferences.
     */
    public RandomGraphEditor(Graph oldGraph, boolean cyclicAllowed) {
        this.cyclicAllowed = cyclicAllowed;

        int oldNumMeasured = 0;
        int oldNumLatents = 0;

        for (Node node : oldGraph.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                oldNumMeasured++;
            }

            if (node.getNodeType() == NodeType.LATENT) {
                oldNumLatents++;
            }
        }

        int oldNumNodes = oldNumMeasured + oldNumLatents;

        if (oldNumNodes > 1 && oldNumMeasured == getNumMeasuredNodes() &&
                oldNumLatents == getNumLatents()) {
            setNumMeasuredNodes(oldNumMeasured);
            setNumLatents(oldNumLatents);
            setMaxEdges(Math.min(getMaxEdges(), oldNumNodes * (oldNumNodes - 1) / 2));
        }

        numNodesField = new IntTextField(getNumMeasuredNodes(), 4);
        numLatentsField = new IntTextField(getNumLatents(), 4);
        maxEdgesField = new IntTextField(getMaxEdges(), 4);
        maxIndegreeField = new IntTextField(getMaxIndegree(), 4);
        maxOutdegreeField = new IntTextField(getMaxOutdegree(), 4);
        maxDegreeField = new IntTextField(getMaxDegree(), 4);
        randomForward = new JRadioButton("Add random forward edges");
        chooseUniform = new JRadioButton("Draw uniformly from all such DAGs");
        chooseFixed = new JRadioButton("Guarantee maximum number of edges");
        connectedBox = new JComboBox(new String[]{"No", "Yes"});
        addCyclesBox = new JComboBox(new String[]{"No", "Yes"});
        numTwoCyclesField = new IntTextField(getMinNumCycles(), 4);
        minCycleLengthField = new IntTextField(getMinCycleLength(), 4);

        ButtonGroup group = new ButtonGroup();
        group.add(randomForward);
        group.add(chooseUniform);
        group.add(chooseFixed);
        randomForward.setSelected(isRandomForward());
        chooseUniform.setSelected(isUniformlySelected());
        chooseFixed.setSelected(isChooseFixed());

        // set up text and ties them to the parameters object being edited.
        numNodesField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                if (value == numNodesField.getValue()) {
                    return oldValue;
                }

                try {
                    setNumMeasuredNodes(value);
                }
                catch (Exception e) {
                    // Ignore.
                }

                maxEdgesField.setValue(getMaxEdges());
                return value;
            }
        });

        numLatentsField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                if (value == numLatentsField.getValue()) {
                    return oldValue;
                }

                try {
                    setNumLatents(value);
                }
                catch (Exception e) {
                    // Ignore.
                }

                maxEdgesField.setValue(getMaxEdges());
                return value;
            }
        });

        maxEdgesField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                if (value == maxEdgesField.getValue()) {
                    return oldValue;
                }

                try {
                    setMaxEdges(value);
                }
                catch (Exception e) {
                    // Ignore.
                }

                return value;
            }
        });

        maxIndegreeField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                if (value == maxIndegreeField.getValue()) {
                    return oldValue;
                }

                try {
                    setMaxIndegree(value);
                }
                catch (Exception e) {
                    // Ignore.
                }

                maxOutdegreeField.setValue(getMaxOutdegree());
                return value;
            }
        });

        maxOutdegreeField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                if (value == maxOutdegreeField.getValue()) {
                    return oldValue;
                }

                try {
                    setMaxOutdegree(value);
                }
                catch (Exception e) {
                    // Ignore.
                }

                maxIndegreeField.setValue(getMaxIndegree());
                maxDegreeField.setValue(getMaxDegree());
                return value;
            }
        });

        maxDegreeField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                if (value == maxDegreeField.getValue()) {
                    return oldValue;
                }

                try {
                    setMaxDegree(value);
                }
                catch (Exception e) {
                    // Ignore.
                }

                maxIndegreeField.setValue(getMaxIndegree());
                maxOutdegreeField.setValue(getMaxOutdegree());
                return value;
            }
        });

        if (isConnected()) {
            connectedBox.setSelectedItem("Yes");
        } else {
            connectedBox.setSelectedItem("No");
        }

        if (isUniformlySelected() || isChooseFixed()) {
            maxIndegreeField.setEnabled(true);
            maxOutdegreeField.setEnabled(true);
            maxDegreeField.setEnabled(true);
            connectedBox.setEnabled(true);
        } else {
            maxIndegreeField.setEnabled(false);
            maxOutdegreeField.setEnabled(false);
            maxDegreeField.setEnabled(false);
            connectedBox.setEnabled(false);
        }

        if (isAddCycles()) {
//            numTwoCyclesField.setEnabled(true);
            minCycleLengthField.setEnabled(true);
        } else {
//            numTwoCyclesField.setEnabled(false);
            minCycleLengthField.setEnabled(false);
        }

        connectedBox.setMaximumSize(connectedBox.getPreferredSize());
        connectedBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JComboBox box = (JComboBox) e.getSource();
                if ("Yes".equals(box.getSelectedItem())) {
                    setConnected(true);
                } else if ("No".equals(box.getSelectedItem())) {
                    setConnected(false);
                } else {
                    throw new IllegalArgumentException();
                }

                maxIndegreeField.setValue(getMaxIndegree());
                maxOutdegreeField.setValue(getMaxOutdegree());
                maxDegreeField.setValue(getMaxDegree());
                maxEdgesField.setValue(getMaxEdges());
            }
        });

        randomForward.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JRadioButton button = (JRadioButton) e.getSource();
                button.setSelected(true);
                setRandomForward(true);
                setUniformlySelected(false);
                setChooseFixed(false);

                maxIndegreeField.setEnabled(true);
                maxOutdegreeField.setEnabled(true);
                maxDegreeField.setEnabled(true);
                connectedBox.setEnabled(true);
            }
        });

        chooseUniform.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JRadioButton button = (JRadioButton) e.getSource();
                button.setSelected(true);
                setRandomForward(false);
                setUniformlySelected(true);
                setChooseFixed(false);

                maxIndegreeField.setEnabled(true);
                maxOutdegreeField.setEnabled(true);
                maxDegreeField.setEnabled(true);
                connectedBox.setEnabled(true);
            }
        });

        chooseFixed.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JRadioButton button = (JRadioButton) e.getSource();
                button.setSelected(true);
                setRandomForward(false);
                setUniformlySelected(false);
                setChooseFixed(true);

                maxIndegreeField.setEnabled(false);
                maxOutdegreeField.setEnabled(false);
                maxDegreeField.setEnabled(false);
                connectedBox.setEnabled(false);
            }
        });

        if (isAddCycles()) {
            addCyclesBox.setSelectedItem("Yes");
        } else {
            addCyclesBox.setSelectedItem("No");
        }

        addCyclesBox.setMaximumSize(addCyclesBox.getPreferredSize());
        addCyclesBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JComboBox box = (JComboBox) e.getSource();
                if ("Yes".equals(box.getSelectedItem())) {
                    setAddCycles(true);
//                    numTwoCyclesField.setEnabled(true);
                    minCycleLengthField.setEnabled(true);
                } else if ("No".equals(box.getSelectedItem())) {
                    setAddCycles(false);
//                    numTwoCyclesField.setEnabled(false);
                    minCycleLengthField.setEnabled(false);
                } else {
                    throw new IllegalArgumentException();
                }
            }
        });

        numTwoCyclesField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                if (value == numTwoCyclesField.getValue()) {
                    return oldValue;
                }

                try {
                    setMinNumCycles(value);
                } catch (Exception e) {
                    // Ignore.
                }

                return value;
            }
        });

        minCycleLengthField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                if (value == minCycleLengthField.getValue()) {
                    return oldValue;
                }

                try {
                    setMinCycleLength(value);
                }
                catch (Exception e) {
                    // Ignore.
                }

                return value;
            }
        });

        // construct the workbench.
        setLayout(new BorderLayout());

        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Parameters for Random DAG:"));
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(5));

        Box b10 = Box.createHorizontalBox();
        b10.add(new JLabel("Number of measured nodes:"));
        b10.add(Box.createRigidArea(new Dimension(10, 0)));
        b10.add(Box.createHorizontalGlue());
        b10.add(numNodesField);
        b1.add(b10);

        Box b11 = Box.createHorizontalBox();
        b11.add(new JLabel("Max # latent confounders:"));
        b11.add(Box.createHorizontalStrut(25));
        b11.add(Box.createHorizontalGlue());
        b11.add(numLatentsField);
        b1.add(b11);
        b1.add(Box.createVerticalStrut(5));

        Box b12 = Box.createHorizontalBox();
        b12.add(new JLabel("Maximum number of edges:"));
        b12.add(Box.createHorizontalGlue());
        b12.add(maxEdgesField);
        b1.add(b12);
        b1.add(Box.createVerticalStrut(5));

        Box b14 = Box.createHorizontalBox();
        b14.add(new JLabel("Maximum indegree:"));
        b14.add(Box.createHorizontalGlue());
        b14.add(maxIndegreeField);
        b1.add(b14);

        Box b15 = Box.createHorizontalBox();
        b15.add(new JLabel("Maximum outdegree:"));
        b15.add(Box.createHorizontalGlue());
        b15.add(maxOutdegreeField);
        b1.add(b15);

        Box b13 = Box.createHorizontalBox();
        b13.add(new JLabel("Maximum degree:"));
        b13.add(Box.createHorizontalGlue());
        b13.add(maxDegreeField);
        b1.add(b13);
        b1.add(Box.createVerticalStrut(5));

        Box b16 = Box.createHorizontalBox();
        b16.add(new JLabel("Connected:"));
        b16.add(Box.createHorizontalGlue());
        b16.add(connectedBox);
        b1.add(b16);
        b1.add(Box.createVerticalStrut(5));

        Box b17a = Box.createHorizontalBox();
        b17a.add(randomForward);
        b17a.add(Box.createHorizontalGlue());
        b1.add(b17a);

        Box b17 = Box.createHorizontalBox();
        b17.add(chooseUniform);
        b17.add(Box.createHorizontalGlue());
        b1.add(b17);

        Box b18 = Box.createHorizontalBox();
        b18.add(chooseFixed);
        b18.add(Box.createHorizontalGlue());
        b1.add(b18);

        Box d = Box.createVerticalBox();
        b1.setBorder(new TitledBorder(""));
        d.add(b1);

        if (this.cyclicAllowed) {
            Box c1 = Box.createVerticalBox();

            Box c2 = Box.createHorizontalBox();
            c2.add(new JLabel("Create a cyclic graph?"));
            c2.add(Box.createHorizontalGlue());
            c2.add(addCyclesBox);
            c1.add(c2);
            c1.add(Box.createVerticalStrut(5));

//            Box c3 = Box.createHorizontalBox();
//            c3.add(new JLabel("Target number of cycles (may vary)"));
//            c3.add(Box.createHorizontalGlue());
//            c3.add(numTwoCyclesField);
//            c1.add(c3);
//            c1.add(Box.createVerticalStrut(5));
//
//            Box c4 = Box.createHorizontalBox();
//            c4.add(new JLabel("Minimum cycle length"));
//            c4.add(Box.createHorizontalGlue());
//            c4.add(minCycleLengthField);
//            c1.add(c4);

            Box c3 = Box.createHorizontalBox();
            c3.add(new JLabel("Number of two cycles to add:"));
            c3.add(Box.createHorizontalGlue());
            c3.add(numTwoCyclesField);
            c1.add(c3);
            c1.add(Box.createVerticalStrut(5));

            c1.setBorder(new TitledBorder(""));

            d.add(Box.createVerticalStrut(5));
            d.add(c1);
        }

        add(d, BorderLayout.CENTER);
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        if (isChooseFixed() && enabled) {
            numNodesField.setEnabled(enabled);
            numLatentsField.setEnabled(enabled);
            maxEdgesField.setEnabled(enabled);
            maxIndegreeField.setEnabled(false);
            maxOutdegreeField.setEnabled(false);
            maxDegreeField.setEnabled(false);
            connectedBox.setEnabled(false);
            chooseUniform.setEnabled(enabled);
            chooseFixed.setEnabled(enabled);
        } else {
            numNodesField.setEnabled(enabled);
            numLatentsField.setEnabled(enabled);
            maxEdgesField.setEnabled(enabled);
            maxIndegreeField.setEnabled(enabled);
            maxOutdegreeField.setEnabled(enabled);
            maxDegreeField.setEnabled(enabled);
            connectedBox.setEnabled(enabled);
            chooseUniform.setEnabled(enabled);
            chooseFixed.setEnabled(enabled);
        }
    }

    public boolean isRandomForward() {
        return Preferences.userRoot().getBoolean("graphRandomFoward", true);
    }

    public boolean isUniformlySelected() {
        return Preferences.userRoot().getBoolean("graphUniformlySelected", true);
    }

    public boolean isChooseFixed() {
        return Preferences.userRoot().getBoolean("graphChooseFixed", true);
    }

    public void setRandomForward(boolean randomFoward) {
        Preferences.userRoot().putBoolean("graphRandomFoward", randomFoward);
    }

    public void setUniformlySelected(boolean uniformlySelected) {
        Preferences.userRoot().putBoolean("graphUniformlySelected", uniformlySelected);
    }

    public void setChooseFixed(boolean chooseFixed) {
        Preferences.userRoot().putBoolean("graphChooseFixed", chooseFixed);
    }

    public int getNumNodes() {
        return getNumMeasuredNodes() + getNumLatents();
    }

    public int getNumMeasuredNodes() {
        return Preferences.userRoot().getInt("newGraphNumMeasuredNodes", 5);
    }

    private void setNumMeasuredNodes(int numMeasuredNodes) {
        if (numMeasuredNodes + getNumLatents() < 2) {
            throw new IllegalArgumentException("Number of nodes Must be greater than or equal to 2.");
        }

//        if (numNodes < getNumLatents()) {
//            throw new IllegalArgumentException(
//                    "Number of nodes Must be greater than or equal to number " + "of latent nodes.");
//        }

        Preferences.userRoot().putInt("newGraphNumMeasuredNodes", numMeasuredNodes);

        if (isConnected()) {
            setMaxEdges(Math.max(getMaxEdges(), numMeasuredNodes + getNumLatents()));
        }
    }

    public int getNumLatents() {
        return Preferences.userRoot().getInt("newGraphNumLatents", 0);
    }

    private void setNumLatents(int numLatentNodes) {
        if (numLatentNodes < 0) {
            throw new IllegalArgumentException(
                    "Max # latent confounders must be" + " >= 0: " +
                            numLatentNodes);
        }

//        if (numLatentNodes > getNumNodes()) {
//            throw new IllegalArgumentException(
//                    "Number of latent nodes must be " + "<= number of nodes.");
//        }

        Preferences.userRoot().putInt("newGraphNumLatents", numLatentNodes);
    }

    public int getMaxEdges() {
        return Preferences.userRoot().getInt("newGraphNumEdges", 3);
    }


    private void setMaxEdges(int numEdges) {
        if (isConnected() && numEdges < getNumNodes()) {
            throw new IllegalArgumentException("When assuming connectedness, " +
                    "the number of edges must be at least the number of nodes.");
        }

        if (!isConnected() && numEdges < 0) {
            throw new IllegalArgumentException(
                    "Number of edges Must be greater than or equal to 0: " + numEdges);
        }

        int maxNumEdges = getNumNodes() * (getNumNodes() - 1) / 2;

        if (numEdges > maxNumEdges) {
            numEdges = maxNumEdges;
        }

        Preferences.    userRoot().putInt("newGraphNumEdges", numEdges);
    }

    public int getMaxDegree() {
        return Preferences.userRoot().getInt("randomGraphMaxDegree", 6);
    }

    private void setMaxDegree(int maxDegree) {
        if (!isConnected() && maxDegree < 1) {
            Preferences.userRoot().putInt("randomGraphMaxDegree", 1);
            return;
        }

        if (isConnected() && maxDegree < 3) {
            Preferences.userRoot().putInt("randomGraphMaxDegree", 3);
            return;
        }

        Preferences.userRoot().putInt("randomGraphMaxDegree", maxDegree);
    }

    public int getMaxIndegree() {
        return Preferences.userRoot().getInt("randomGraphMaxIndegree", 3);
    }

    private void setMaxIndegree(int maxIndegree) {
        if (!isConnected() && maxIndegree < 1) {
            Preferences.userRoot().putInt("randomGraphMaxIndegree", 1);
            return;
        }

        if (isConnected() && maxIndegree < 2) {
            Preferences.userRoot().putInt("randomGraphMaxIndegree", 2);
            return;
        }

        Preferences.userRoot().putInt("randomGraphMaxIndegree", maxIndegree);
    }

    public int getMaxOutdegree() {
        return Preferences.userRoot().getInt("randomGraphMaxOutdegree", 3);
    }

    private void setMaxOutdegree(int maxOutDegree) {
        if (!isConnected() && maxOutDegree < 1) {
            Preferences.userRoot().putInt("randomGraphMaxOutdegree", 1);
            return;
        }

        if (isConnected() && maxOutDegree < 2) {
            Preferences.userRoot().putInt("randomGraphMaxOutdegree", 2);
            return;
        }

        Preferences.userRoot().putInt("randomGraphMaxOutdegree", maxOutDegree);
    }

    private void setConnected(boolean connected) {
        Preferences.userRoot().putBoolean("randomGraphConnected", connected);

        if (connected) {
            if (getMaxIndegree() < 2) {
                setMaxIndegree(2);
            }

            if (getMaxOutdegree() < 2) {
                setMaxOutdegree(2);
            }

            if (getMaxDegree() < 3) {
                setMaxDegree(3);
            }

            if (getMaxEdges() < getNumNodes()) {
                setMaxEdges(getNumNodes());
            }
        }
    }

    public boolean isConnected() {
        return Preferences.userRoot().getBoolean("randomGraphConnected", false);
    }

    private void setAddCycles(boolean addCycles) {
        Preferences.userRoot().putBoolean("randomGraphAddCycles", addCycles);
    }

    public boolean isAddCycles() {
        return Preferences.userRoot().getBoolean("randomGraphAddCycles", false);
    }

    public int getMinNumCycles() {
        int minNumCycles = Preferences.userRoot().getInt("randomGraphMinNumCycles", 0);
        System.out.println("get min num cycles = " + minNumCycles);

        return minNumCycles;
    }

    private void setMinNumCycles(int minNumCycles) {

        System.out.println("set min num cycles = " + minNumCycles);

        if (minNumCycles < 0) {
            Preferences.userRoot().putInt("randomGraphMinNumCycles", 0);
            return;
        }

        Preferences.userRoot().putInt("randomGraphMinNumCycles", minNumCycles);
    }

    public int getMinCycleLength() {
        return Preferences.userRoot().getInt("randomGraphMinCycleLength", 2);
    }

    private void setMinCycleLength(int minCycleLength) {
        if (minCycleLength < 2) {
            Preferences.userRoot().putInt("randomGraphMinCycleLength", 2);
            return;
        }

        Preferences.userRoot().putInt("randomGraphMinCycleLength", minCycleLength);
    }
}





