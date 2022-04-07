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

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * Edits the parameters for generating random graphs.
 *
 * @author Joseph Ramsey
 */
class RandomGraphEditor extends JPanel {
    private final Parameters parameters;
    private final IntTextField numNodesField;
    private final IntTextField numLatentsField;
    private final IntTextField maxEdgesField;
    private final IntTextField maxIndegreeField;
    private final IntTextField maxOutdegreeField;
    private final IntTextField maxDegreeField;
    private final JRadioButton chooseUniform;
    private final JRadioButton chooseFixed;
    private final JComboBox connectedBox;
    private final IntTextField numTwoCyclesField;
    private final IntTextField minCycleLengthField;

    /**
     * Constructs a dialog to edit the given workbench randomization
     * parameters.
     */
    public RandomGraphEditor(boolean cyclicAllowed, Parameters parameters) {
        this(new EdgeListGraph(), cyclicAllowed, parameters);
    }

    /**
     * Constructs a dialog to edit the given workbench randomization
     * parameters.
     * //     * @param preferredNumNodes an integer which, if greater than 1, will revise the number of nodes,
     * //     * number of edges,a nd number of latent nodes. Useful if the interface suggests a number of nodes
     * //     * that overrides the number of nodes set in the preferences.
     */
    public RandomGraphEditor(Graph oldGraph, boolean cyclicAllowed, Parameters parameters) {
        if (parameters == null) {
            throw new NullPointerException();
        }

        this.parameters = parameters;

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

        this.numNodesField = new IntTextField(getNumMeasuredNodes(), 4);
        this.numLatentsField = new IntTextField(getNumLatents(), 4);
        this.maxEdgesField = new IntTextField(getMaxEdges(), 4);
        this.maxIndegreeField = new IntTextField(getMaxIndegree(), 4);
        this.maxOutdegreeField = new IntTextField(getMaxOutdegree(), 4);
        this.maxDegreeField = new IntTextField(getMaxDegree(), 4);
        JRadioButton randomForward = new JRadioButton("Add random forward edges");
        this.chooseUniform = new JRadioButton("Draw uniformly from all such DAGs");
        this.chooseFixed = new JRadioButton("Guarantee maximum number of edges");
        this.connectedBox = new JComboBox<>(new String[]{"No", "Yes"});
        JComboBox addCyclesBox = new JComboBox<>(new String[]{"No", "Yes"});
        this.numTwoCyclesField = new IntTextField(getMinNumCycles(), 4);
        this.minCycleLengthField = new IntTextField(getMinCycleLength(), 4);

        ButtonGroup group = new ButtonGroup();
        group.add(randomForward);
        group.add(this.chooseUniform);
        group.add(this.chooseFixed);
        randomForward.setSelected(isRandomForward());
        this.chooseUniform.setSelected(isUniformlySelected());
        this.chooseFixed.setSelected(isChooseFixed());

        // set up text and ties them to the parameters object being edited.
        this.numNodesField.setFilter((value, oldValue) -> {
            if (value == numNodesField.getValue()) {
                return oldValue;
            }

            try {
                RandomGraphEditor.this.setNumMeasuredNodes(value);
            } catch (Exception e) {
                // Ignore.
            }

            maxEdgesField.setValue(RandomGraphEditor.this.getMaxEdges());
            return value;
        });

        numLatentsField.setFilter((value, oldValue) -> {
            if (value == numLatentsField.getValue()) {
                return oldValue;
            }

            try {
                RandomGraphEditor.this.setNumLatents(value);
            } catch (Exception e) {
                // Ignore.
            }

            maxEdgesField.setValue(RandomGraphEditor.this.getMaxEdges());
            return value;
        });

        maxEdgesField.setFilter((value, oldValue) -> {
            if (value == maxEdgesField.getValue()) {
                return oldValue;
            }

            try {
                RandomGraphEditor.this.setMaxEdges(value);
            } catch (Exception e) {
                // Ignore.
            }

            return value;
        });

        maxIndegreeField.setFilter((value, oldValue) -> {
            if (value == maxIndegreeField.getValue()) {
                return oldValue;
            }

            try {
                RandomGraphEditor.this.setMaxIndegree(value);
            } catch (Exception e) {
                // Ignore.
            }

            maxOutdegreeField.setValue(RandomGraphEditor.this.getMaxOutdegree());
            return value;
        });

        maxOutdegreeField.setFilter((value, oldValue) -> {
            if (value == maxOutdegreeField.getValue()) {
                return oldValue;
            }

            try {
                RandomGraphEditor.this.setMaxOutdegree(value);
            } catch (Exception e) {
                // Ignore.
            }

            maxIndegreeField.setValue(RandomGraphEditor.this.getMaxIndegree());
            maxDegreeField.setValue(RandomGraphEditor.this.getMaxDegree());
            return value;
        });

        maxDegreeField.setFilter((value, oldValue) -> {
            if (value == maxDegreeField.getValue()) {
                return oldValue;
            }

            try {
                RandomGraphEditor.this.setMaxDegree(value);
            } catch (Exception e) {
                // Ignore.
            }

            maxIndegreeField.setValue(RandomGraphEditor.this.getMaxIndegree());
            maxOutdegreeField.setValue(RandomGraphEditor.this.getMaxOutdegree());
            return value;
        });

        if (this.isConnected()) {
            connectedBox.setSelectedItem("Yes");
        } else {
            connectedBox.setSelectedItem("No");
        }

        if (this.isUniformlySelected() || this.isChooseFixed()) {
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

        minCycleLengthField.setEnabled(this.isAddCycles());

        connectedBox.setMaximumSize(connectedBox.getPreferredSize());
        connectedBox.addActionListener(e -> {
            JComboBox box = (JComboBox) e.getSource();
            if ("Yes".equals(box.getSelectedItem())) {
                RandomGraphEditor.this.setConnected(true);
            } else if ("No".equals(box.getSelectedItem())) {
                RandomGraphEditor.this.setConnected(false);
            } else {
                throw new IllegalArgumentException();
            }

            maxIndegreeField.setValue(RandomGraphEditor.this.getMaxIndegree());
            maxOutdegreeField.setValue(RandomGraphEditor.this.getMaxOutdegree());
            maxDegreeField.setValue(RandomGraphEditor.this.getMaxDegree());
            maxEdgesField.setValue(RandomGraphEditor.this.getMaxEdges());
        });

        randomForward.addActionListener(e -> {
            JRadioButton button = (JRadioButton) e.getSource();
            button.setSelected(true);
            RandomGraphEditor.this.setRandomForward(true);
            RandomGraphEditor.this.setUniformlySelected(false);
            RandomGraphEditor.this.setChooseFixed(false);

            maxIndegreeField.setEnabled(true);
            maxOutdegreeField.setEnabled(true);
            maxDegreeField.setEnabled(true);
            connectedBox.setEnabled(true);
        });

        chooseUniform.addActionListener(e -> {
            JRadioButton button = (JRadioButton) e.getSource();
            button.setSelected(true);
            RandomGraphEditor.this.setRandomForward(false);
            RandomGraphEditor.this.setUniformlySelected(true);
            RandomGraphEditor.this.setChooseFixed(false);

            maxIndegreeField.setEnabled(true);
            maxOutdegreeField.setEnabled(true);
            maxDegreeField.setEnabled(true);
            connectedBox.setEnabled(true);
        });

        chooseFixed.addActionListener(e -> {
            JRadioButton button = (JRadioButton) e.getSource();
            button.setSelected(true);
            RandomGraphEditor.this.setRandomForward(false);
            RandomGraphEditor.this.setUniformlySelected(false);
            RandomGraphEditor.this.setChooseFixed(true);

            maxIndegreeField.setEnabled(false);
            maxOutdegreeField.setEnabled(false);
            maxDegreeField.setEnabled(false);
            connectedBox.setEnabled(false);
        });

        if (this.isAddCycles()) {
            addCyclesBox.setSelectedItem("Yes");
        } else {
            addCyclesBox.setSelectedItem("No");
        }

        addCyclesBox.setMaximumSize(addCyclesBox.getPreferredSize());
        addCyclesBox.addActionListener(e -> {
            JComboBox box = (JComboBox) e.getSource();
            if ("Yes".equals(box.getSelectedItem())) {
                RandomGraphEditor.this.setAddCycles(true);
//                    numTwoCyclesField.setEnabled(true);
                minCycleLengthField.setEnabled(true);
            } else if ("No".equals(box.getSelectedItem())) {
                RandomGraphEditor.this.setAddCycles(false);
//                    numTwoCyclesField.setEnabled(false);
                minCycleLengthField.setEnabled(false);
            } else {
                throw new IllegalArgumentException();
            }
        });

        numTwoCyclesField.setFilter((value, oldValue) -> {
            if (value == numTwoCyclesField.getValue()) {
                return oldValue;
            }

            try {
                RandomGraphEditor.this.setMinNumCycles(value);
            } catch (Exception e) {
                // Ignore.
            }

            return value;
        });

        minCycleLengthField.setFilter((value, oldValue) -> {
            if (value == RandomGraphEditor.this.minCycleLengthField.getValue()) {
                return oldValue;
            }

            try {
                setMinCycleLength(value);
            } catch (Exception e) {
                // Ignore.
            }

            return value;
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
        b10.add(this.numNodesField);
        b1.add(b10);

        Box b11 = Box.createHorizontalBox();
        b11.add(new JLabel("Max # latent confounders:"));
        b11.add(Box.createHorizontalStrut(25));
        b11.add(Box.createHorizontalGlue());
        b11.add(this.numLatentsField);
        b1.add(b11);
        b1.add(Box.createVerticalStrut(5));

        Box b12 = Box.createHorizontalBox();
        b12.add(new JLabel("Maximum number of edges:"));
        b12.add(Box.createHorizontalGlue());
        b12.add(this.maxEdgesField);
        b1.add(b12);
        b1.add(Box.createVerticalStrut(5));

        Box b14 = Box.createHorizontalBox();
        b14.add(new JLabel("Maximum indegree:"));
        b14.add(Box.createHorizontalGlue());
        b14.add(this.maxIndegreeField);
        b1.add(b14);

        Box b15 = Box.createHorizontalBox();
        b15.add(new JLabel("Maximum outdegree:"));
        b15.add(Box.createHorizontalGlue());
        b15.add(this.maxOutdegreeField);
        b1.add(b15);

        Box b13 = Box.createHorizontalBox();
        b13.add(new JLabel("Maximum degree:"));
        b13.add(Box.createHorizontalGlue());
        b13.add(this.maxDegreeField);
        b1.add(b13);
        b1.add(Box.createVerticalStrut(5));

        Box b16 = Box.createHorizontalBox();
        b16.add(new JLabel("Connected:"));
        b16.add(Box.createHorizontalGlue());
        b16.add(this.connectedBox);
        b1.add(b16);
        b1.add(Box.createVerticalStrut(5));

        Box b17a = Box.createHorizontalBox();
        b17a.add(randomForward);
        b17a.add(Box.createHorizontalGlue());
        b1.add(b17a);

        Box b17 = Box.createHorizontalBox();
        b17.add(this.chooseUniform);
        b17.add(Box.createHorizontalGlue());
        b1.add(b17);

        Box b18 = Box.createHorizontalBox();
        b18.add(this.chooseFixed);
        b18.add(Box.createHorizontalGlue());
        b1.add(b18);

        Box d = Box.createVerticalBox();
        b1.setBorder(new TitledBorder(""));
        d.add(b1);

        if (cyclicAllowed) {
            Box c1 = Box.createVerticalBox();

            Box c2 = Box.createHorizontalBox();
            c2.add(new JLabel("Create a cyclic graph?"));
            c2.add(Box.createHorizontalGlue());
            c2.add(addCyclesBox);
            c1.add(c2);
            c1.add(Box.createVerticalStrut(5));

            Box c3 = Box.createHorizontalBox();
            c3.add(new JLabel("Number of two cycles to add:"));
            c3.add(Box.createHorizontalGlue());
            c3.add(this.numTwoCyclesField);
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
            this.numNodesField.setEnabled(enabled);
            this.numLatentsField.setEnabled(enabled);
            this.maxEdgesField.setEnabled(enabled);
            this.maxIndegreeField.setEnabled(false);
            this.maxOutdegreeField.setEnabled(false);
            this.maxDegreeField.setEnabled(false);
            this.connectedBox.setEnabled(false);
            this.chooseUniform.setEnabled(enabled);
            this.chooseFixed.setEnabled(enabled);
        } else {
            this.numNodesField.setEnabled(enabled);
            this.numLatentsField.setEnabled(enabled);
            this.maxEdgesField.setEnabled(enabled);
            this.maxIndegreeField.setEnabled(enabled);
            this.maxOutdegreeField.setEnabled(enabled);
            this.maxDegreeField.setEnabled(enabled);
            this.connectedBox.setEnabled(enabled);
            this.chooseUniform.setEnabled(enabled);
            this.chooseFixed.setEnabled(enabled);
        }
    }

    public boolean isRandomForward() {
        return this.parameters.getBoolean("graphRandomFoward", true);
    }

    public boolean isUniformlySelected() {
        return this.parameters.getBoolean("graphUniformlySelected", true);
    }

    public boolean isChooseFixed() {
        return this.parameters.getBoolean("graphChooseFixed", true);
    }

    private void setRandomForward(boolean randomFoward) {
        this.parameters.set("graphRandomFoward", randomFoward);
    }

    private void setUniformlySelected(boolean uniformlySelected) {
        this.parameters.set("graphUniformlySelected", uniformlySelected);
    }

    private void setChooseFixed(boolean chooseFixed) {
        this.parameters.set("graphChooseFixed", chooseFixed);
    }

    public int getNumNodes() {
        return getNumMeasuredNodes() + getNumLatents();
    }

    private int getNumMeasuredNodes() {
        return this.parameters.getInt("newGraphNumMeasuredNodes", 10);
    }

    private void setNumMeasuredNodes(int numMeasuredNodes) {
        if (numMeasuredNodes + getNumLatents() < 2) {
            throw new IllegalArgumentException("Number of nodes Must be greater than or equal to 2.");
        }

        this.parameters.set("newGraphNumMeasuredNodes", numMeasuredNodes);

        if (isConnected()) {
            setMaxEdges(Math.max(getMaxEdges(), numMeasuredNodes + getNumLatents()));
        }
    }

    public int getNumLatents() {
        return this.parameters.getInt("newGraphNumLatents", 0);
    }

    private void setNumLatents(int numLatentNodes) {
        if (numLatentNodes < 0) {
            throw new IllegalArgumentException(
                    "Max # latent confounders must be" + " >= 0: " +
                            numLatentNodes);
        }

        this.parameters.set("newGraphNumLatents", numLatentNodes);
    }

    public int getMaxEdges() {
        return this.parameters.getInt("newGraphNumEdges", 10);
    }


    private void setMaxEdges(int numEdges) {
        if (/*!isConnected() &&*/ numEdges < 0) {
            throw new IllegalArgumentException(
                    "Number of edges Must be greater than or equal to 0: " + numEdges);
        }

        int maxNumEdges = getNumNodes() * (getNumNodes() - 1) / 2;

        if (numEdges > maxNumEdges) {
            numEdges = maxNumEdges;
        }

        this.parameters.set("newGraphNumEdges", numEdges);
    }

    public int getMaxDegree() {
        return this.parameters.getInt("randomGraphMaxDegree", 6);
    }

    private void setMaxDegree(int maxDegree) {
        if (!isConnected() && maxDegree < 1) {
            this.parameters.set("randomGraphMaxDegree", 1);
            return;
        }

        if (isConnected() && maxDegree < 3) {
            this.parameters.set("randomGraphMaxDegree", 3);
            return;
        }

        this.parameters.set("randomGraphMaxDegree", maxDegree);
    }

    public int getMaxIndegree() {
        return this.parameters.getInt("randomGraphMaxIndegree", 3);
    }

    private void setMaxIndegree(int maxIndegree) {
        if (!isConnected() && maxIndegree < 1) {
            this.parameters.set("randomGraphMaxIndegree", 1);
            return;
        }

        if (isConnected() && maxIndegree < 2) {
            this.parameters.set("randomGraphMaxIndegree", 2);
            return;
        }

        this.parameters.set("randomGraphMaxIndegree", maxIndegree);
    }

    public int getMaxOutdegree() {
        return this.parameters.getInt("randomGraphMaxOutdegree", 3);
    }

    private void setMaxOutdegree(int maxOutDegree) {
        if (!isConnected() && maxOutDegree < 1) {
            this.parameters.set("randomGraphMaxOutdegree", 1);
            return;
        }

        if (isConnected() && maxOutDegree < 2) {
            this.parameters.set("randomGraphMaxOutdegree", 2);
            return;
        }

        this.parameters.set("randomGraphMaxOutdegree", maxOutDegree);
    }

    private void setConnected(boolean connected) {
        this.parameters.set("randomGraphConnected", connected);

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
        return this.parameters.getBoolean("randomGraphConnected", false);
    }

    private void setAddCycles(boolean addCycles) {
        this.parameters.set("randomGraphAddCycles", addCycles);
    }

    public boolean isAddCycles() {
        return this.parameters.getBoolean("randomGraphAddCycles", false);
    }

    public int getMinNumCycles() {
        int minNumCycles = this.parameters.getInt("randomGraphMinNumCycles", 0);
        System.out.println("get min num cycles = " + minNumCycles);

        return minNumCycles;
    }

    private void setMinNumCycles(int minNumCycles) {

        System.out.println("set min num cycles = " + minNumCycles);

        if (minNumCycles < 0) {
            this.parameters.set("randomGraphMinNumCycles", 0);
            return;
        }

        this.parameters.set("randomGraphMinNumCycles", minNumCycles);
    }

    public int getMinCycleLength() {
        return this.parameters.getInt("randomGraphMinCycleLength", 2);
    }

    private void setMinCycleLength(int minCycleLength) {
        if (minCycleLength < 2) {
            this.parameters.set("randomGraphMinCycleLength", 2);
            return;
        }

        this.parameters.set("randomGraphMinCycleLength", minCycleLength);
    }
}





