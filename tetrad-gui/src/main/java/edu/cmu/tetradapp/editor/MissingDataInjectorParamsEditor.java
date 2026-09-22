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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * Edits the parameters for simulating data from Bayes nets.
 *
 * @author josephramsey
 * @author Frank Wimberly based on similar classes by Joe Ramsey
 * @version $Id: $Id
 */
public class MissingDataInjectorParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameters object being edited.
     */
    private Parameters params;

    //========================= Public Methods =======================================//

    /**
     * <p>setup.</p>
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
     * <p>setParentModels.</p>
     *
     * @param parentModels an array of {@link java.lang.Object} objects
     */
    public void setParentModels(Object[] parentModels) {

    }

    /**
     * <p>mustBeShown.</p>
     *
     * @return a boolean
     */
    public boolean mustBeShown() {
        return true;
    }

    //======================== Private Methods ====================================//

    /**
     * Constructs the Gui used to edit properties; called from each constructor. Constructs labels and text fields for
     * editing each property and adds appropriate listeners.
     */
    private void buildGui() {
        setLayout(new BorderLayout());

        DoubleTextField probField =
                new DoubleTextField(this.params.getDouble("prob", 0.02), 6, NumberFormatUtil.getInstance().getNumberFormat());
        probField.setToolTipText("Rate for variables the profile does not name; the rate for every variable"
                                 + " when the profile is empty.");
        probField.setFilter((value, oldValue) -> {
            try {
                if (value < 0.0 || value > 1.0) return oldValue;
                MissingDataInjectorParamsEditor.this.params.set("prob", value);
                return value;
            } catch (IllegalArgumentException e) {
                return oldValue;
            }
        });

        DoubleTextField rowField = new DoubleTextField(
                this.params.getDouble("missingnessRowPropensity", 0.0), 6,
                NumberFormatUtil.getInstance().getNumberFormat());
        rowField.setToolTipText("<html>Latent per-case propensity on the logit scale. 0 deletes independently,"
                                + " scattering the losses evenly over cases.<br>Around 1.0 gives roughly twice"
                                + " the spread in per-case missing counts, clustering the losses into fewer"
                                + " cases,<br>which is what real attrition looks like. Marginal rates are held"
                                + " fixed as this changes.</html>");
        rowField.setFilter((value, oldValue) -> {
            try {
                if (value < 0.0) return oldValue;
                MissingDataInjectorParamsEditor.this.params.set("missingnessRowPropensity", value);
                return value;
            } catch (IllegalArgumentException e) {
                return oldValue;
            }
        });

        JTextArea profileArea = new JTextArea(this.params.getString("missingnessRateProfile", ""), 4, 40);
        profileArea.setLineWrap(true);
        profileArea.setWrapStyleWord(true);
        profileArea.setToolTipText("<html>Empty: use the default rate for every variable.<br>"
                                   + "A list of rates, one per variable in dataset order: 0.07 0.13 0.24 ...<br>"
                                   + "Or name=rate assignments, in any order, for just the variables you"
                                   + " want to change: X3=0.45, X7=0.92</html>");
        profileArea.getDocument().addDocumentListener(new DocumentListener() {
            private void sync() {
                MissingDataInjectorParamsEditor.this.params.set("missingnessRateProfile",
                        profileArea.getText());
            }

            public void insertUpdate(DocumentEvent e) { sync(); }

            public void removeUpdate(DocumentEvent e) { sync(); }

            public void changedUpdate(DocumentEvent e) { sync(); }
        });

        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("<html>"
                          + "Deletes values under a per-cell logistic propensity with two terms: a rate for"
                          + "<br>each variable, and a latent per-case propensity shared across variables."
                          + "<br>The per-variable intercepts are calibrated so the rates come out as asked"
                          + "<br>whatever the row propensity is, so level and clustering vary independently."
                          + "<br><br>A row propensity above zero clusters the losses into fewer cases, which"
                          + "<br>is what attrition looks like, but the mechanism stays MCAR: the latent term"
                          + "<br>is independent of the data. Use this to study how methods degrade when"
                          + "<br>complete cases get scarce, not to study MAR or MNAR bias."
                          + "</html>"));

        Box b7 = Box.createHorizontalBox();
        b7.add(Box.createHorizontalGlue());
        b7.add(new JLabel("<html><i>Default probability:  </i></html>"));
        b7.add(probField);

        Box b8 = Box.createHorizontalBox();
        b8.add(Box.createHorizontalGlue());
        b8.add(new JLabel("<html><i>Row propensity:  </i></html>"));
        b8.add(rowField);

        Box b9 = Box.createHorizontalBox();
        b9.add(new JLabel("<html><i>Rate profile:  </i></html>"));
        b9.add(Box.createHorizontalGlue());

        Box b10 = Box.createHorizontalBox();
        b10.add(new JScrollPane(profileArea));

        b1.add(b2);
        b1.add(Box.createVerticalStrut(5));
        b1.add(b7);
        b1.add(b8);
        b1.add(Box.createVerticalStrut(5));
        b1.add(b9);
        b1.add(b10);
        b1.add(Box.createHorizontalGlue());
        add(b1, BorderLayout.CENTER);
    }

}






