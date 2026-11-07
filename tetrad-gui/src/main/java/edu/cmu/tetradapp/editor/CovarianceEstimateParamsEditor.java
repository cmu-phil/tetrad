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
import edu.cmu.tetradapp.model.CovarianceEstimateDataWrapper;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;

/**
 * Parameter editor for {@link CovarianceEstimateDataWrapper}: the estimator (EM, pairwise, censored EM), the
 * ceiling for the censored estimator, and the effective-sample-size mode.
 *
 * @author josephramsey
 */
public class CovarianceEstimateParamsEditor extends JPanel implements ParameterEditor {

    private Parameters params;

    /**
     * Constructs the editor; the GUI is built in {@link #setup()}.
     */
    public CovarianceEstimateParamsEditor() {
    }

    /**
     * {@inheritDoc}
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
     * {@inheritDoc}
     */
    public void setParentModels(Object[] parentModels) {
    }

    /**
     * {@inheritDoc}
     */
    public boolean mustBeShown() {
        return true;
    }

    private void buildGui() {
        setLayout(new BorderLayout());

        JComboBox<CovarianceEstimateDataWrapper.Method> methodCombo =
                new JComboBox<>(CovarianceEstimateDataWrapper.Method.values());
        methodCombo.setSelectedItem(CovarianceEstimateDataWrapper.method(this.params));
        methodCombo.setToolTipText("<html>EM: one covariance matrix from every row, missing values at random."
                                   + "<br>PAIRWISE: each entry from the rows complete on its own pair; need not"
                                   + " be positive semidefinite.<br>CENSORED: EM with every cell at the ceiling"
                                   + " treated as \"at least the ceiling\"; other missing values at random.</html>");

        DoubleTextField ceilingField = new DoubleTextField(
                this.params.getDouble(CovarianceEstimateDataWrapper.CEILING, 100.0), 8,
                NumberFormat.getNumberInstance());
        ceilingField.setToolTipText("CENSORED only: the common ceiling, applied to every column (100 for grades)."
                                    + " Columns that never reach it are unaffected.");
        ceilingField.setFilter((value, oldValue) -> {
            this.params.set(CovarianceEstimateDataWrapper.CEILING, value);
            return value;
        });
        ceilingField.setEnabled(methodCombo.getSelectedItem() == CovarianceEstimateDataWrapper.Method.CENSORED);

        methodCombo.addActionListener(e -> {
            Object selected = methodCombo.getSelectedItem();
            if (selected == null) return;
            this.params.set(CovarianceEstimateDataWrapper.METHOD, selected.toString());
            ceilingField.setEnabled(selected == CovarianceEstimateDataWrapper.Method.CENSORED);
        });

        String[] essModes = {"meanPairwise", "minPairwise", "fullN"};
        JComboBox<String> essCombo = new JComboBox<>(essModes);
        essCombo.setSelectedItem(this.params.getString(CovarianceEstimateDataWrapper.ESS_MODE, "meanPairwise"));
        essCombo.setToolTipText("<html>The sample size the matrix carries. meanPairwise: the mean over pairs of"
                                + " the rows complete on both (the usual choice with missing values)."
                                + "<br>minPairwise: the smallest such count (conservative). fullN: the row"
                                + " count, which overstates the evidence when values are missing.</html>");
        essCombo.addActionListener(e -> {
            Object selected = essCombo.getSelectedItem();
            if (selected != null) this.params.set(CovarianceEstimateDataWrapper.ESS_MODE, selected.toString());
        });

        Box b1 = Box.createVerticalBox();
        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("<html>"
                          + "Replaces the data set by one covariance matrix estimated from all of its rows,"
                          + "<br>incomplete ones included, so that an Estimator box or a score-based search"
                          + "<br>downstream runs on it directly."
                          + "<br><br>EM is consistent when values are missing at random. Censored EM also treats"
                          + "<br>values at a ceiling as censored rather than exact, which repairs the attenuation"
                          + "<br>a point mass at 100 puts into every correlation; it assumes the uncensored"
                          + "<br>values are normal. Neither repairs values that are missing because of their own"
                          + "<br>size."
                          + "</html>"));
        b1.add(b2);
        b1.add(Box.createVerticalStrut(8));
        b1.add(labeled("Estimator:", methodCombo));
        b1.add(labeled("Ceiling (censored only):", ceilingField));
        b1.add(labeled("Sample size to carry:", essCombo));
        b1.add(Box.createHorizontalGlue());

        add(b1, BorderLayout.CENTER);
    }

    private static Box labeled(String label, JComponent field) {
        Box box = Box.createHorizontalBox();
        box.add(new JLabel("<html><i>" + label + "  </i></html>"));
        box.add(Box.createHorizontalGlue());
        box.add(field);
        return box;
    }
}
