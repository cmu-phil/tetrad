///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.GraphComparisonUtils;
import edu.cmu.tetradapp.model.GraphSource;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Edits the parameters for the graph-graph comparison boxes (Stats List, Misclassifications, Edgewise). Any number
 * of graph sources may be attached; the user picks which one is the reference (true) graph, and, when there are
 * three or more sources, the order in which the remaining (target) graphs appear in the comparison output. The
 * choices are stored in the "referenceGraphName", "targetGraphName", and "targetGraphNames" parameters, which the
 * comparison models read in their constructors.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class EdgewiseComparisonParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameters object being edited.
     */
    private Parameters params;

    /**
     * The parent models. These should be graph sources.
     */
    private Object[] parentModels;

    /**
     * The graph source inputs, sorted by name.
     */
    private final List<GraphSource> graphSources = new ArrayList<>();

    /**
     * The currently selected reference (true) graph source.
     */
    private GraphSource reference;

    /**
     * The target graph sources, in display order.
     */
    private final List<GraphSource> targets = new ArrayList<>();

    /**
     * The list model backing the target-order list.
     */
    private final DefaultListModel<String> targetListModel = new DefaultListModel<>();

    /**
     * <p>Setter for the field <code>parentModels</code>.</p>
     *
     * @param parentModels an array of {@link java.lang.Object} objects
     */
    public void setParentModels(Object[] parentModels) {
        this.parentModels = parentModels;
    }

    /**
     * <p>mustBeShown.</p>
     *
     * @return a boolean
     */
    public boolean mustBeShown() {
        return false;
    }

    /**
     * <p>setup.</p>
     */
    public void setup() {
        this.graphSources.clear();

        for (Object parentModel : this.parentModels) {
            if (parentModel instanceof GraphSource graphSource) {
                this.graphSources.add(graphSource);
            }
        }

        setLayout(new BorderLayout());

        if (this.graphSources.isEmpty()) {
            add(new JLabel("No graph source inputs found."), BorderLayout.CENTER);
            return;
        }

        this.graphSources.sort(java.util.Comparator.comparing(GraphSource::getName));

        // Default reference: the stored parameter if it names an input; else a Simulation box; else the name
        // last chosen in any session (a user preference); else the first input.
        this.reference = null;
        String paramName = this.params.getString("referenceGraphName", null);
        String storedName = Preferences.userRoot().get("__referenceSessionModel", "");

        for (GraphSource source : this.graphSources) {
            if (source.getName().equals(paramName)) {
                this.reference = source;
                break;
            }
        }

        if (this.reference == null) {
            for (GraphSource source : this.graphSources) {
                if (source.getName().startsWith("Simulation")) {
                    this.reference = source;
                    break;
                }
            }
        }

        if (this.reference == null) {
            for (GraphSource source : this.graphSources) {
                if (source.getName().equals(storedName)) {
                    this.reference = source;
                    break;
                }
            }
        }

        if (this.reference == null) {
            this.reference = this.graphSources.getFirst();
        }

        recomputeTargets();

        // Both names must be *set*: Parameters.getString(name, default) stores the default on first access and
        // returns the stored value thereafter, so using it here left targetGraphName frozen at whichever name it
        // was first given, and choosing the other graph as reference then made reference and target identical.
        writeParams();

        Box b1 = Box.createVerticalBox();

        Box b8 = Box.createHorizontalBox();
        b8.add(new JLabel("Which input graph is the true graph?"));
        b8.add(Box.createHorizontalGlue());
        b1.add(b8);

        ButtonGroup group = new ButtonGroup();

        for (GraphSource source : this.graphSources) {
            JRadioButton button = new JRadioButton(source.getName());
            group.add(button);

            if (source == this.reference) {
                button.setSelected(true);
            }

            button.addActionListener(e -> {
                this.reference = source;
                Preferences.userRoot().put("__referenceSessionModel", source.getName());
                recomputeTargets();
                writeParams();
            });

            Box b9 = Box.createHorizontalBox();
            b9.add(button);
            b9.add(Box.createHorizontalGlue());
            b1.add(b9);
        }

        // With three or more inputs, let the user order the target graphs; with two, there is exactly one target.
        if (this.graphSources.size() >= 3) {
            b1.add(Box.createVerticalStrut(10));

            Box b10 = Box.createHorizontalBox();
            b10.add(new JLabel("Order of the other graphs in the comparison:"));
            b10.add(Box.createHorizontalGlue());
            b1.add(b10);

            JList<String> targetList = new JList<>(this.targetListModel);
            targetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            targetList.setVisibleRowCount(Math.min(6, this.graphSources.size() - 1));

            JButton up = new JButton("Up");
            JButton down = new JButton("Down");

            up.addActionListener(e -> moveTarget(targetList, -1));
            down.addActionListener(e -> moveTarget(targetList, 1));

            Box buttons = Box.createVerticalBox();
            buttons.add(up);
            buttons.add(Box.createVerticalStrut(5));
            buttons.add(down);
            buttons.add(Box.createVerticalGlue());

            Box b11 = Box.createHorizontalBox();
            b11.add(new JScrollPane(targetList));
            b11.add(Box.createHorizontalStrut(5));
            b11.add(buttons);
            b1.add(b11);
        }

        b1.add(Box.createHorizontalGlue());
        add(b1, BorderLayout.CENTER);
    }

    /**
     * Recomputes the target list (all inputs but the reference), preserving the previous display order for
     * sources that remain targets, and refreshes the list model.
     */
    private void recomputeTargets() {
        List<GraphSource> previous = new ArrayList<>(this.targets);
        this.targets.clear();

        for (GraphSource source : previous) {
            if (source != this.reference && this.graphSources.contains(source)) {
                this.targets.add(source);
            }
        }

        for (GraphSource source : this.graphSources) {
            if (source != this.reference && !this.targets.contains(source)) {
                this.targets.add(source);
            }
        }

        if (previous.isEmpty() && this.params.getNumValues("targetGraphNames") > 0) {
            // First time through: respect any ordering stored from an earlier edit of this box.
            List<GraphSource> ordered = GraphComparisonUtils.orderTargets(this.graphSources, this.reference,
                    this.params);
            this.targets.clear();
            this.targets.addAll(ordered);
        }

        this.targetListModel.clear();

        for (GraphSource target : this.targets) {
            this.targetListModel.addElement(target.getName());
        }
    }

    /**
     * Moves the selected target up or down in the display order and rewrites the parameters.
     *
     * @param targetList the list component.
     * @param delta      -1 to move up, 1 to move down.
     */
    private void moveTarget(JList<String> targetList, int delta) {
        int index = targetList.getSelectedIndex();
        int newIndex = index + delta;

        if (index < 0 || newIndex < 0 || newIndex >= this.targets.size()) {
            return;
        }

        GraphSource moved = this.targets.remove(index);
        this.targets.add(newIndex, moved);

        String name = this.targetListModel.remove(index);
        this.targetListModel.add(newIndex, name);

        targetList.setSelectedIndex(newIndex);
        writeParams();
    }

    /**
     * Writes the reference name, the first target name, and the full target order to the parameters.
     */
    private void writeParams() {
        this.params.set("referenceGraphName", this.reference.getName());

        if (!this.targets.isEmpty()) {
            this.params.set("targetGraphName", this.targets.getFirst().getName());

            String[] names = new String[this.targets.size()];

            for (int i = 0; i < this.targets.size(); i++) {
                names[i] = this.targets.get(i).getName();
            }

            this.params.set("targetGraphNames", names);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setParams(Parameters params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = params;
    }
}
