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

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.ImputeMissingDataWrapper;
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import java.awt.*;

/**
 * Edits the parameters of the imputation data manipulation: which imputer, how many completed datasets, the seed,
 * and the donor and sweep counts for the chained-equations method.
 *
 * <p>The panel states the two things a user has to know to read the output correctly: that the box emits several
 * datasets rather than one, and that validity rests on MAR. Both are properties of the method rather than of the
 * settings, so they belong in the panel and not only in the manual.</p>
 *
 * @author josephramsey
 * @see ImputeMissingDataWrapper
 */
public class ImputeMissingDataParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameters being edited.
     */
    private Parameters params;

    /**
     * Sets up the panel.
     */
    public void setup() {
        buildGui();
    }

    /**
     * {@inheritDoc}
     */
    public void setParams(Parameters params) {
        this.params = params;
    }

    /**
     * Sets the parent models. Not used here.
     *
     * @param parentModels The parent models.
     */
    public void setParentModels(Object[] parentModels) {
    }

    /**
     * Whether the panel must be shown before the model is created.
     *
     * @return True.
     */
    public boolean mustBeShown() {
        return true;
    }

    private void buildGui() {
        setLayout(new BorderLayout());

        JComboBox<ImputeMissingDataWrapper.Method> methodCombo =
                new JComboBox<>(ImputeMissingDataWrapper.Method.values());
        methodCombo.setSelectedItem(currentMethod());
        methodCombo.setToolTipText("<html>AUTO: the EM multivariate normal method for continuous data, chained"
                                   + " equations otherwise.<br>MVN_EM: EM under a saturated normal, then draws from"
                                   + " each row's conditional normal. Continuous data only.<br>MICE: chained"
                                   + " equations with predictive mean matching. Continuous, discrete or mixed;"
                                   + " imputed values are copied from observed donors,<br>so discrete imputations"
                                   + " are always valid categories.</html>");
        methodCombo.addActionListener(e -> {
            Object selected = methodCombo.getSelectedItem();
            if (selected != null) this.params.set("imputationMethod", selected.toString());
        });

        IntTextField mField = new IntTextField(this.params.getInt("numImputations", 5), 4);
        mField.setToolTipText("How many completed datasets to produce. At least 2; a single completed dataset"
                              + " would present imputed values as measured ones.");
        mField.setFilter((value, oldValue) -> {
            if (value < 2) return oldValue;
            this.params.set("numImputations", value);
            return value;
        });

        IntTextField seedField = new IntTextField((int) this.params.getLong("imputationSeed", 0L), 8);
        seedField.setToolTipText("Random seed, for reproducible imputations.");
        seedField.setFilter((value, oldValue) -> {
            this.params.set("imputationSeed", (long) value);
            return value;
        });

        IntTextField donorField = new IntTextField(this.params.getInt("miceNumDonors", 5), 4);
        donorField.setToolTipText("MICE only: how many near donors a missing cell draws its value from.");
        donorField.setFilter((value, oldValue) -> {
            if (value < 1) return oldValue;
            this.params.set("miceNumDonors", value);
            return value;
        });

        IntTextField sweepField = new IntTextField(this.params.getInt("miceNumSweeps", 5), 4);
        sweepField.setToolTipText("MICE only: how many passes over the variables before a dataset is kept.");
        sweepField.setFilter((value, oldValue) -> {
            if (value < 1) return oldValue;
            this.params.set("miceNumSweeps", value);
            return value;
        });

        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("<html>"
                          + "Fills in missing values by multiple imputation, emitting several completed datasets"
                          + "<br>rather than one. Values are drawn from their conditional distributions, not set to"
                          + "<br>conditional means, so variances and correlations are not artificially tightened."
                          + "<br><br>Valid under MAR. Under MNAR the result is biased, not merely noisy, and no"
                          + "<br>setting here repairs that. Both methods are improper in Rubin's sense, so"
                          + "<br>between-imputation variability is understated even across the m datasets."
                          + "</html>"));

        b1.add(b2);
        b1.add(Box.createVerticalStrut(8));
        b1.add(labeled("Method:", methodCombo));
        b1.add(labeled("Number of imputations:", mField));
        b1.add(labeled("Seed:", seedField));
        b1.add(Box.createVerticalStrut(6));
        b1.add(labeled("MICE donors:", donorField));
        b1.add(labeled("MICE sweeps:", sweepField));
        b1.add(Box.createHorizontalGlue());

        add(b1, BorderLayout.CENTER);
    }

    private ImputeMissingDataWrapper.Method currentMethod() {
        try {
            return ImputeMissingDataWrapper.Method.valueOf(
                    this.params.getString("imputationMethod", ImputeMissingDataWrapper.Method.AUTO.name()));
        } catch (IllegalArgumentException e) {
            return ImputeMissingDataWrapper.Method.AUTO;
        }
    }

    private static Box labeled(String label, JComponent field) {
        Box box = Box.createHorizontalBox();
        box.add(new JLabel("<html><i>" + label + "  </i></html>"));
        box.add(Box.createHorizontalGlue());
        box.add(field);
        return box;
    }
}
