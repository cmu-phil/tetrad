///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

import edu.cmu.tetrad.sem.NNEstimatorParams;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.NNEstimatorModel;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.LongTextField;

import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;

/**
 * Parameter editor for the NN Estimator session node. Exposes the seed, the
 * per-node network training settings, the MMD² feature count, the
 * edge-strength Monte Carlo settings, and the cross-validation fold
 * assignment. Values are written straight into the {@link Parameters} the
 * node passes to {@link NNEstimatorModel}, which reads them on every
 * resimulate.
 *
 * <p>The descriptions are kept here rather than in the manual's parameter
 * definitions so this editor works before the manual entries exist.
 *
 * @author josephramsey
 * @see NNEstimatorModel
 */
public final class NNEstimatorParamsEditor extends JPanel implements ParameterEditor {

    private Parameters params;

    /** Constructs the editor; the GUI is built in {@link #setup()}. */
    public NNEstimatorParamsEditor() {
        super(new BorderLayout());
    }

    @Override
    public void setParams(Parameters params) {
        this.params = params;
    }

    @Override
    public void setParentModels(Object[] parentModels) {
        // Parents (data, graph) are not needed to edit these parameters.
    }

    @Override
    public void setup() {
        removeAll();
        NNEstimatorParams d = new NNEstimatorParams();

        JPanel grid = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;
        int row = 0;

        row = header(grid, c, row, "Reproducibility");
        LongTextField seed = new LongTextField(
                params.getLong(NNEstimatorModel.SEED, NNEstimatorModel.DEFAULT_SEED), 10);
        seed.setFilter((v, old) -> { params.set(NNEstimatorModel.SEED, v); return v; });
        row = add(grid, c, row, "Seed", seed,
                "Used for training, simulation, fold assignment, and edge strength. "
                + "Two NN Estimator boxes on the same data and seed give the same numbers.");
        JCheckBox randomize = new JCheckBox("Randomize the seed on every resimulate",
                params.getBoolean(NNEstimatorModel.RANDOMIZE_SEED, false));
        randomize.addActionListener(e ->
                params.set(NNEstimatorModel.RANDOMIZE_SEED, randomize.isSelected()));
        row = add(grid, c, row, "", randomize,
                "Tick this to see run-to-run variation. Results are then not reproducible.");

        row = header(grid, c, row, "Per-node networks");
        row = add(grid, c, row, "Hidden units",
                intField(NNEstimatorModel.HIDDEN, d.hidden, 1, 4096),
                "Size of the single hidden layer in each node's network.");
        row = add(grid, c, row, "Epochs",
                intField(NNEstimatorModel.EPOCHS, d.epochs, 1, 100_000),
                "Passes over the training rows per node.");
        row = add(grid, c, row, "Learning rate",
                doubleField(NNEstimatorModel.LEARNING_RATE, d.lr, 1e-6, 10.0),
                "SGD step size.");
        row = add(grid, c, row, "Weight decay",
                doubleField(NNEstimatorModel.WEIGHT_DECAY, d.l2, 0.0, 10.0),
                "L2 penalty on network weights.");

        row = header(grid, c, row, "Adequacy and edge strength");
        row = add(grid, c, row, "MMD² features",
                intField(NNEstimatorModel.MMD_FEATURES, d.mmdFeatures, 16, 65_536),
                "Random Fourier features used to approximate MMD². More is slower and less noisy.");
        row = add(grid, c, row, "Draws per configuration",
                intField(NNEstimatorModel.EDGE_DRAWS_PER_CONFIG, d.edgeDrawsPerConfig, 2, 100_000),
                "Child draws per observed parent configuration, per condition.");
        row = add(grid, c, row, "Repeats",
                intField(NNEstimatorModel.EDGE_REPEATS, d.edgeRepeats, 1, 1000),
                "Independent repeats of each edge computation; the SD across them is shown as ±.");
        row = add(grid, c, row, "Null refits",
                intField(NNEstimatorModel.EDGE_NULL_REFITS, d.edgeNullRefits, 0, 1000),
                "Same-parent refits used to measure training noise per child. 0 skips the null.");

        row = header(grid, c, row, "Cross-validation");
        JCheckBox shuffle = new JCheckBox("Shuffle rows before cutting folds",
                params.getBoolean(NNEstimatorModel.SHUFFLE_FOLDS, d.shuffleFolds));
        shuffle.addActionListener(e ->
                params.set(NNEstimatorModel.SHUFFLE_FOLDS, shuffle.isSelected()));
        add(grid, c, row, "", shuffle,
                "Off: folds are contiguous blocks in file order, right for serially dependent rows. "
                + "On: rows are permuted first, right for rows sorted by some variable.");

        JLabel note = new JLabel("<html><i>These are read each time you press Resimulate in the editor. "
                + "The Explanation tab in the editor describes what each one affects.</i></html>");
        note.setBorder(BorderFactory.createEmptyBorder(8, 6, 2, 6));

        add(grid, BorderLayout.CENTER);
        add(note, BorderLayout.SOUTH);
        revalidate();
        repaint();
    }

    @Override
    public boolean mustBeShown() {
        return false;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private IntTextField intField(String key, int def, int lo, int hi) {
        IntTextField f = new IntTextField(params.getInt(key, def), 8);
        f.setFilter((v, old) -> {
            if (v < lo || v > hi) return old;
            params.set(key, v);
            return v;
        });
        return f;
    }

    private DoubleTextField doubleField(String key, double def, double lo, double hi) {
        DoubleTextField f = new DoubleTextField(params.getDouble(key, def), 8,
                new DecimalFormat("0.######"), new DecimalFormat("0.0#E0"), 0.001);
        f.setFilter((v, old) -> {
            if (v < lo || v > hi) return old;
            params.set(key, v);
            return v;
        });
        return f;
    }

    private static int header(JPanel grid, GridBagConstraints c, int row, String text) {
        JLabel h = new JLabel(text);
        h.setFont(h.getFont().deriveFont(Font.BOLD));
        c.gridx = 0; c.gridy = row; c.gridwidth = 3;
        c.insets = new Insets(row == 0 ? 3 : 10, 6, 3, 6);
        grid.add(h, c);
        c.gridwidth = 1;
        c.insets = new Insets(3, 6, 3, 6);
        return row + 1;
    }

    private static int add(JPanel grid, GridBagConstraints c, int row,
                           String label, JComponent field, String description) {
        c.gridy = row;
        c.gridx = 0; grid.add(new JLabel(label), c);
        c.gridx = 1; grid.add(field, c);
        JLabel desc = new JLabel("<html><body style='width: 340px'>" + description + "</body></html>");
        desc.setFont(desc.getFont().deriveFont(Font.PLAIN, 11f));
        desc.setForeground(Color.DARK_GRAY);
        c.gridx = 2; grid.add(desc, c);
        return row + 1;
    }
}
