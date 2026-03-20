/// ////////////////////////////////////////////////////////////////////////////
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

import edu.cmu.tetrad.graph.RandomMim;
import edu.cmu.tetrad.graph.RandomMimic;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.StringTextField;

import javax.swing.*;
import java.awt.*;
import java.io.Serial;
import java.util.List;

/**
 * RandomMimicParamsEditor is a user interface component that allows for the configuration and
 * editing of parameters used to construct random MIMIC (Multiple Indicators Multiple Causes)
 * graphs. It provides input fields for setting structural edges, MIMIC group specifications,
 * shared parents, and impure edge counts, which are validated and saved back into the provided
 * parameters object.
 */
class RandomMimicParamsEditor extends JPanel {

    @Serial
    private static final long serialVersionUID = 3827491650234817293L;

    // ---- Parameter keys ----------------------------------------------------
    private static final String K_NUM_STRUCTURAL_EDGES = "mimicNumStructuralEdges";
    private static final String K_MIMIC_GROUP_SPECS = "mimicGroupSpecs";
    private static final String K_NUM_SHARED_PARENTS = "mimicNumSharedParents";
    private static final String K_LATENT_MEASURED_IMPURE_PARENTS = "mimLatentMeasuredImpureParents";
    private static final String K_MEASURED_MEASURED_IMPURE_PARENTS = "mimMeasuredMeasuredImpureParents";
    private static final String K_MEASURED_MEASURED_IMPURE_ASSOC = "mimMeasuredMeasuredImpureAssociations";

    // ---- Defaults ----------------------------------------------------------
    private static final int D_NUM_STRUCTURAL_EDGES = 5;
    private static final String D_MIMIC_GROUP_SPECS = "5:5:3";
    private static final int D_ZERO = 0;

    /**
     * Constructs a new RandomMimicParamsEditor with the provided parameter configuration.
     *
     * @param parameters the Parameters object containing the configuration values for
     *                   initializing and updating the editor.
     */
    public RandomMimicParamsEditor(Parameters parameters) {
        setLayout(new BorderLayout());

        // Structural edges (clamped to simple DAG max given current #groups from specs)
        final IntTextField numStructuralEdges = new IntTextField(
                parameters.getInt(K_NUM_STRUCTURAL_EDGES, D_NUM_STRUCTURAL_EDGES), 4
        );
        numStructuralEdges.setFilter((value, oldValue) -> {
            try {
                int maxEdges = computeMaxEdgesFromSpecs(parameters);
                int clamped = clampToRange(value, 0, maxEdges);
                parameters.set(K_NUM_STRUCTURAL_EDGES, clamped);
                return clamped;
            } catch (Exception ex) {
                TetradLogger.getInstance().log(ex.toString());
                return oldValue;
            }
        });

        // MIMIC group specs (validated by parser; re-clamps structural edges if needed)
        final StringTextField mimicGroupSpecs = new StringTextField(
                parameters.getString(K_MIMIC_GROUP_SPECS, D_MIMIC_GROUP_SPECS), 20
        );
        mimicGroupSpecs.setFilter((value, oldValue) -> {
            try {
                String cleaned = normalizeSpecs(value);
                RandomMimic.parseMimicGroupSpecs(cleaned); // validate
                parameters.set(K_MIMIC_GROUP_SPECS, cleaned);

                // After specs change, recompute max edges and clamp structural edge count.
                int maxEdges = computeMaxEdgesFromSpecs(parameters);
                int current = parameters.getInt(K_NUM_STRUCTURAL_EDGES, D_NUM_STRUCTURAL_EDGES);
                int clamped = clampToRange(current, 0, maxEdges);
                if (clamped != current) {
                    parameters.set(K_NUM_STRUCTURAL_EDGES, clamped);
                    numStructuralEdges.setValue(clamped);
                }
                return cleaned;
            } catch (Exception ex) {
                TetradLogger.getInstance().log(ex.toString());
                return oldValue;
            }
        });

        // Shared parents (>= 0)
        final IntTextField numSharedParents = new IntTextField(
                parameters.getInt(K_NUM_SHARED_PARENTS, D_ZERO), 4
        );
        numSharedParents.setFilter((value, oldValue) ->
                nonNegativeFilter(parameters, K_NUM_SHARED_PARENTS, value, oldValue));

        // Impure edge counts (all >= 0)
        final IntTextField numLatentMeasuredImpureParents = new IntTextField(
                parameters.getInt(K_LATENT_MEASURED_IMPURE_PARENTS, D_ZERO), 4
        );
        numLatentMeasuredImpureParents.setFilter((value, oldValue) ->
                nonNegativeFilter(parameters, K_LATENT_MEASURED_IMPURE_PARENTS, value, oldValue));

        final IntTextField numMeasuredMeasuredImpureParents = new IntTextField(
                parameters.getInt(K_MEASURED_MEASURED_IMPURE_PARENTS, D_ZERO), 4
        );
        numMeasuredMeasuredImpureParents.setFilter((value, oldValue) ->
                nonNegativeFilter(parameters, K_MEASURED_MEASURED_IMPURE_PARENTS, value, oldValue));

        final IntTextField numMeasuredMeasuredImpureAssociations = new IntTextField(
                parameters.getInt(K_MEASURED_MEASURED_IMPURE_ASSOC, D_ZERO), 4
        );
        numMeasuredMeasuredImpureAssociations.setFilter((value, oldValue) ->
                nonNegativeFilter(parameters, K_MEASURED_MEASURED_IMPURE_ASSOC, value, oldValue));

        // Ensure initial clamp using current specs
        try {
            int maxEdges = computeMaxEdgesFromSpecs(parameters);
            int current = parameters.getInt(K_NUM_STRUCTURAL_EDGES, D_NUM_STRUCTURAL_EDGES);
            int clamped = clampToRange(current, 0, maxEdges);
            if (clamped != current) {
                parameters.set(K_NUM_STRUCTURAL_EDGES, clamped);
                numStructuralEdges.setValue(clamped);
            }
        } catch (Exception ex) {
            TetradLogger.getInstance().log(ex.toString());
        }

        // ---- Layout --------------------------------------------------------
        Box root = Box.createVerticalBox();
        root.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        root.add(row("List of count:children:parents, comma separated; e.g. 5:6:3,2:8:4", mimicGroupSpecs));
        root.add(Box.createVerticalStrut(10));
        root.add(row("Number of structural edges:", numStructuralEdges));
        root.add(Box.createVerticalStrut(10));
        root.add(row("Number of shared parents:", numSharedParents));
        root.add(Box.createVerticalStrut(10));
        root.add(sectionLabel("Add impure edges:"));
        root.add(row("Latent \u2192 Measured", numLatentMeasuredImpureParents));
        root.add(row("Measured \u2192 Measured", numMeasuredMeasuredImpureParents));
        root.add(row("Measured \u2194 Measured", numMeasuredMeasuredImpureAssociations));

        root.add(Box.createVerticalGlue());
        add(root, BorderLayout.CENTER);
    }

    // ---- Helpers -----------------------------------------------------------

    private static int computeMaxEdgesFromSpecs(Parameters p) {
        String raw = p.getString(K_MIMIC_GROUP_SPECS, D_MIMIC_GROUP_SPECS);
        String cleaned = normalizeSpecs(raw);
        List<RandomMimic.MimicGroupSpec> specs = RandomMimic.parseMimicGroupSpecs(cleaned);

        int groups = 0;
        for (RandomMimic.MimicGroupSpec s : specs) {
            groups += s.countGroups();
        }
        if (groups <= 1) return 0;
        return (groups * (groups - 1)) / 2;
    }

    private static String normalizeSpecs(String s) {
        String trimmed = (s == null) ? "" : s.trim();
        return trimmed.replaceAll("\\s+", "");
    }

    private static int clampToRange(int val, int lo, int hi) {
        if (val < lo) return lo;
        if (val > hi) return hi;
        return val;
    }

    private static int nonNegativeFilter(Parameters p, String key, int value, int oldValue) {
        try {
            if (value < 0) throw new IllegalArgumentException("Value must be \u2265 0.");
            p.set(key, value);
            return value;
        } catch (Exception ex) {
            TetradLogger.getInstance().log(ex.toString());
            return oldValue;
        }
    }

    private static Box row(String label, JComponent field) {
        Box row = Box.createHorizontalBox();
        JLabel l = new JLabel(label);
        l.setLabelFor(field);
        row.add(l);
        row.add(Box.createHorizontalGlue());
        row.add(field);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private static Box sectionLabel(String text) {
        Box box = Box.createHorizontalBox();
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        box.add(l);
        box.add(Box.createHorizontalGlue());
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(Box.createVerticalStrut(4));
        return box;
    }
}