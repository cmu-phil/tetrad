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

import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import java.awt.*;

/**
 * Edits the parameters of the Add Missingness Indicators box.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class AddMissingnessIndicatorsParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameters object being edited.
     */
    private Parameters params;

    /**
     * Constructs a new editor.
     */
    public AddMissingnessIndicatorsParamsEditor() {
    }

    //========================= Public Methods =======================================//

    /**
     * Builds the GUI.
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
     * @param parentModels an array of {@link java.lang.Object} objects
     */
    public void setParentModels(Object[] parentModels) {
    }

    /**
     * Returns true; this editor always shows.
     *
     * @return a boolean
     */
    public boolean mustBeShown() {
        return true;
    }

    //======================== Private Methods ====================================//

    private void buildGui() {
        setLayout(new BorderLayout());

        DoubleTextField minRateField = new DoubleTextField(
                this.params.getDouble("missingnessIndicatorMinRate", 0.02), 6,
                NumberFormatUtil.getInstance().getNumberFormat());
        minRateField.setToolTipText("<html>Variables missing on a smaller fraction of cases than this get no"
                                    + " indicator.<br>A handful of missing cases gives an indicator that is nearly"
                                    + " constant and carries<br>almost no information, at the cost of a column.</html>");
        minRateField.setFilter((value, oldValue) -> {
            if (value < 0.0 || value > 1.0) return oldValue;
            if (value > this.params.getDouble("missingnessIndicatorMaxRate", 0.98)) return oldValue;
            this.params.set("missingnessIndicatorMinRate", value);
            return value;
        });

        DoubleTextField maxRateField = new DoubleTextField(
                this.params.getDouble("missingnessIndicatorMaxRate", 0.98), 6,
                NumberFormatUtil.getInstance().getNumberFormat());
        maxRateField.setToolTipText("<html>Variables missing on a larger fraction of cases than this get no"
                                    + " indicator, for the same reason<br>read the other way. A variable that is"
                                    + " entirely missing is always skipped.</html>");
        maxRateField.setFilter((value, oldValue) -> {
            if (value < 0.0 || value > 1.0) return oldValue;
            if (value < this.params.getDouble("missingnessIndicatorMinRate", 0.02)) return oldValue;
            this.params.set("missingnessIndicatorMaxRate", value);
            return value;
        });

        JCheckBox discreteBox = new JCheckBox("Discrete indicators",
                this.params.getBoolean("missingnessIndicatorDiscrete", true));
        discreteBox.setToolTipText("<html>Checked: binary discrete variables, which needs a score that accepts"
                                   + " mixed data,<br>such as degenerate Gaussian or conditional Gaussian."
                                   + "<br>Unchecked: continuous 0/1 columns, so a continuous score can be used."
                                   + " That is a<br>linear-probability approximation to a binary variable and should"
                                   + " be read as one.</html>");
        discreteBox.addActionListener(e ->
                this.params.set("missingnessIndicatorDiscrete", discreteBox.isSelected()));

        JCheckBox knowledgeBox = new JCheckBox("Attach tier knowledge",
                this.params.getBoolean("missingnessIndicatorKnowledge", true));
        knowledgeBox.setToolTipText("<html>Puts the substantive variables in tier 0 and the indicators in tier 1,"
                                    + " which forbids an<br>indicator to be a cause of a substantive variable."
                                    + " Whether a value was recorded does not<br>change what the value would have"
                                    + " been, so this direction is not in question.</html>");

        JCheckBox selfMaskBox = new JCheckBox("Forbid self-masking edges",
                this.params.getBoolean("missingnessIndicatorForbidSelfMasking", true));
        selfMaskBox.setToolTipText("<html>Forbids each variable to cause its own indicator. That edge is not merely"
                                   + " hard to detect;<br>it cannot be scored at all, because the variable is"
                                   + " observed on exactly the cases where<br>its indicator says \"observed\", so the"
                                   + " indicator is constant on every usable case.</html>");
        selfMaskBox.setEnabled(knowledgeBox.isSelected());
        selfMaskBox.addActionListener(e ->
                this.params.set("missingnessIndicatorForbidSelfMasking", selfMaskBox.isSelected()));

        knowledgeBox.addActionListener(e -> {
            this.params.set("missingnessIndicatorKnowledge", knowledgeBox.isSelected());
            selfMaskBox.setEnabled(knowledgeBox.isSelected());
        });

        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("<html>"
                          + "Appends, for each variable with missing values, an indicator recording whether that"
                          + "<br>variable was missing in each case. The original columns are untouched, so they"
                          + "<br>still carry their missing values and are still handled by whatever missing-data"
                          + "<br>policy the score or test is set to use."
                          + "<br><br>The indicator is 1, or category \"missing\", when the value is absent."
                          + "<br><br>This makes the missingness mechanism part of the graph: edges into an indicator"
                          + "<br>say what the mechanism depends on, and edges among indicators say how the losses"
                          + "<br>are coupled. It does not recover self-masking, the dependence of missingness on the"
                          + "<br>deleted value itself, which no method can recover from the observed cases alone."
                          + "</html>"));
        b2.add(Box.createHorizontalGlue());

        Box b3 = Box.createHorizontalBox();
        b3.add(Box.createHorizontalGlue());
        b3.add(new JLabel("<html><i>Minimum missingness rate:  </i></html>"));
        b3.add(minRateField);

        Box b4 = Box.createHorizontalBox();
        b4.add(Box.createHorizontalGlue());
        b4.add(new JLabel("<html><i>Maximum missingness rate:  </i></html>"));
        b4.add(maxRateField);

        Box b5 = Box.createHorizontalBox();
        b5.add(discreteBox);
        b5.add(Box.createHorizontalGlue());

        Box b6 = Box.createHorizontalBox();
        b6.add(knowledgeBox);
        b6.add(Box.createHorizontalGlue());

        Box b7 = Box.createHorizontalBox();
        b7.add(Box.createHorizontalStrut(20));
        b7.add(selfMaskBox);
        b7.add(Box.createHorizontalGlue());

        b1.add(b2);
        b1.add(Box.createVerticalStrut(8));
        b1.add(b3);
        b1.add(b4);
        b1.add(Box.createVerticalStrut(8));
        b1.add(b5);
        b1.add(b6);
        b1.add(b7);
        b1.add(Box.createVerticalGlue());

        add(b1, BorderLayout.CENTER);
    }
}
