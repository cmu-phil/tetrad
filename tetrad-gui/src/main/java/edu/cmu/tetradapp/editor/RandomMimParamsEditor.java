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
 * <p>
 * The class extends JPanel and organizes the UI using a vertical box layout with labeled input fields for user-friendly
 * interaction. Filters are applied to ensure valid inputs are accepted and internally updated.
 * <p>
 * This class is intended for use within an application where users need to manage and customize graph structures and
 * their associated properties for analysis or simulation.
 * <p>
 * Key features: - Input and validation for structural edge counts. - Input and real-time validation for latent group
 * specifications. - Input and validation for counts of impure edges of various types. - Organized and labeled layout
 * for user clarity.
 */
class RandomMimParamsEditor extends JPanel {

    @Serial
    private static final long serialVersionUID = -1478898170626611725L;

    // ---- Parameter keys (centralized) -------------------------------------
    private static final String K_NUM_STRUCTURAL_EDGES = "mimNumStructuralEdges";
    private static final String K_LATENT_GROUP_SPECS = "mimLatentGroupSpecs";
    private static final String K_LATENT_MEASURED_IMPURE_PARENTS = "mimLatentMeasuredImpureParents";
    private static final String K_MEASURED_MEASURED_IMPURE_PARENTS = "mimMeasuredMeasuredImpureParents";
    private static final String K_MEASURED_MEASURED_IMPURE_ASSOC = "mimMeasuredMeasuredImpureAssociations";

    // ---- Defaults (kept close to keys) ------------------------------------
    private static final int D_NUM_STRUCTURAL_EDGES = 3;
    private static final String D_LATENT_GROUP_SPECS = "5:5(1)";
    private static final int D_ZERO = 0;

    /**
     * Constructs a {@code RandomMimParamsEditor} instance. This editor provides a graphical user interface for editing
     * parameters related to random Mixed Independence Model (MIM) generation. Various fields and controls are
     * initialized to specify and constrain the parameter values.
     *
     * @param parameters the parameters object that initializes and stores the settings for the random MIM generation.
     *                   It provides default values and serves as the backing store for parameter updates made through
     *                   the editor.
     */
    public RandomMimParamsEditor(Parameters parameters) {
        setLayout(new BorderLayout());

        // Structural edges (clamped to simple DAG max given current node count)
        IntTextField numStructuralEdges = new IntTextField(
                parameters.getInt(K_NUM_STRUCTURAL_EDGES, D_NUM_STRUCTURAL_EDGES), 4
        );
        numStructuralEdges.setFilter((value, oldValue) -> {
            try {
                List<DataGraphUtilsFlexMim.LatentGroupSpec> specs = DataGraphUtilsFlexMim.parseLatentGroupSpecs(parameters.getString(K_LATENT_GROUP_SPECS, D_LATENT_GROUP_SPECS));

                int sumGroups = 0;

                for (DataGraphUtilsFlexMim.LatentGroupSpec spec : specs) {
                    sumGroups += spec.countGroups();
                }

                int n = Math.max(0, sumGroups);
                int maxEdges = n <= 1 ? 0 : (n * (n - 1)) / 2;
                int clamped = Math.min(Math.max(0, value), maxEdges);
                parameters.set(K_NUM_STRUCTURAL_EDGES, clamped);
                return clamped;
            } catch (Exception ex) {
                TetradLogger.getInstance().log(ex.toString());
                return oldValue;
            }
        });

        // Latent group specs (validated by parser)
        StringTextField latentGroupSpecs = new StringTextField(
                parameters.getString(K_LATENT_GROUP_SPECS, D_LATENT_GROUP_SPECS), 16
        );
        latentGroupSpecs.setFilter((value, oldValue) -> {
            try {
                // Validate before saving
                DataGraphUtilsFlexMim.parseLatentGroupSpecs(value);
                parameters.set(K_LATENT_GROUP_SPECS, value);
                return value;
            } catch (Exception ex) {
                TetradLogger.getInstance().log(ex.toString());
                return oldValue;
            }
        });

        // Impure edge counts (all must be >= 0)
        IntTextField numLatentMeasuredImpureParents = new IntTextField(
                parameters.getInt(K_LATENT_MEASURED_IMPURE_PARENTS, D_ZERO), 4
        );
        numLatentMeasuredImpureParents.setFilter((value, oldValue) ->
                nonNegativeFilter(parameters, K_LATENT_MEASURED_IMPURE_PARENTS, value, oldValue));

        IntTextField numMeasuredMeasuredImpureParents = new IntTextField(
                parameters.getInt(K_MEASURED_MEASURED_IMPURE_PARENTS, D_ZERO), 4
        );
        numMeasuredMeasuredImpureParents.setFilter((value, oldValue) ->
                nonNegativeFilter(parameters, K_MEASURED_MEASURED_IMPURE_PARENTS, value, oldValue));

        IntTextField numMeasuredMeasuredImpureAssociations = new IntTextField(
                parameters.getInt(K_MEASURED_MEASURED_IMPURE_ASSOC, D_ZERO), 4
        );
        numMeasuredMeasuredImpureAssociations.setFilter((value, oldValue) ->
                nonNegativeFilter(parameters, K_MEASURED_MEASURED_IMPURE_ASSOC, value, oldValue));

        // ---- Layout (keeps the currently visible rows only) ----------------
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