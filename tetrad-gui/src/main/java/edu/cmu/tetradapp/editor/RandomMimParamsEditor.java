package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.DataGraphUtilsFlexMim;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.StringTextField;

import javax.swing.*;
import java.awt.*;
import java.io.Serial;
import java.util.List;

/**
 * RandomMimParamsEditor is a user interface component that allows for the configuration and editing of parameters used
 * to construct random MIM (Multiple Indicator Models) graphs. It provides input fields for setting structural edges,
 * latent group specifications, and impure edge counts, which are validated and saved back into the provided parameters
 * object.
 */
class RandomMimParamsEditor extends JPanel {

    @Serial
    private static final long serialVersionUID = -1478898170626611725L;

    // ---- Parameter keys ----------------------------------------------------
    private static final String K_NUM_STRUCTURAL_EDGES = "mimNumStructuralEdges";
    private static final String K_LATENT_GROUP_SPECS = "mimLatentGroupSpecs";
    private static final String K_LATENT_MEASURED_IMPURE_PARENTS = "mimLatentMeasuredImpureParents";
    private static final String K_MEASURED_MEASURED_IMPURE_PARENTS = "mimMeasuredMeasuredImpureParents";
    private static final String K_MEASURED_MEASURED_IMPURE_ASSOC = "mimMeasuredMeasuredImpureAssociations";

    // ---- Defaults ----------------------------------------------------------
    private static final int D_NUM_STRUCTURAL_EDGES = 3;
    private static final String D_LATENT_GROUP_SPECS = "5:5(1)";
    private static final int D_ZERO = 0;

    /**
     * Constructs a new RandomMimParamsEditor with the provided parameter configuration. This editor sets up the user
     * interface for configuring random graph parameters, such as the number of structural edges, latent group
     * specifications, and various types of impure edges.
     *
     * @param parameters the Parameters object containing the configuration values for initializing and updating the
     *                   editor. It provides access to default and stored values for various settings and is used to
     *                   reflect and validate changes made in the user interface.
     */
    public RandomMimParamsEditor(Parameters parameters) {
        setLayout(new BorderLayout());

        // Structural edges (clamped to simple DAG max given current #latent groups from specs)
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

        // Latent group specs (validated by parser, and re-clamps structural edges if needed)
        final StringTextField latentGroupSpecs = new StringTextField(
                parameters.getString(K_LATENT_GROUP_SPECS, D_LATENT_GROUP_SPECS), 20
        );
        latentGroupSpecs.setFilter((value, oldValue) -> {
            try {
                String cleaned = normalizeSpecs(value);
                DataGraphUtilsFlexMim.parseLatentGroupSpecs(cleaned); // validate
                parameters.set(K_LATENT_GROUP_SPECS, cleaned);

                // After specs change, recompute max edges and clamp the current structural edge count.
                int maxEdges = computeMaxEdgesFromSpecs(parameters);
                int current = parameters.getInt(K_NUM_STRUCTURAL_EDGES, D_NUM_STRUCTURAL_EDGES);
                int clamped = clampToRange(current, 0, maxEdges);
                if (clamped != current) {
                    parameters.set(K_NUM_STRUCTURAL_EDGES, clamped);
                    // reflect visually
                    numStructuralEdges.setValue(clamped);
                }
                return cleaned;
            } catch (Exception ex) {
                TetradLogger.getInstance().log(ex.toString());
                return oldValue;
            }
        });

        // Impure edge counts (all must be >= 0)
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
            // If specs are invalid at init, leave as-is but log; user can fix the field.
            TetradLogger.getInstance().log(ex.toString());
        }

        // ---- Layout ---------------------------------------------------------
        Box root = Box.createVerticalBox();
        root.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        root.add(row("List of count:children:(rank), comma separated; e.g. 5:6(1),2:8(2):", latentGroupSpecs));
        root.add(Box.createVerticalStrut(10));
        root.add(row("Number of structural edges:", numStructuralEdges));
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
        String raw = p.getString(K_LATENT_GROUP_SPECS, D_LATENT_GROUP_SPECS);
        String cleaned = normalizeSpecs(raw);
        List<DataGraphUtilsFlexMim.LatentGroupSpec> specs =
                DataGraphUtilsFlexMim.parseLatentGroupSpecs(cleaned);

        int groups = 0;
        for (DataGraphUtilsFlexMim.LatentGroupSpec s : specs) {
            groups += s.countGroups();
        }
        if (groups <= 1) return 0;
        return (groups * (groups - 1)) / 2;
    }

    private static String normalizeSpecs(String s) {
        // Trim and collapse internal runs of whitespace; tolerate stray spaces around commas/colons/parens.
        String trimmed = (s == null) ? "" : s.trim();
        // Optionally remove spaces entirely (robust to "5 : 6 (1), 2 : 8 (2)")
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