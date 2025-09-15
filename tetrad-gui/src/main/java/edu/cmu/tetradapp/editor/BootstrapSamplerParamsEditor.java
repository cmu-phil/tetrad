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
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.prefs.Preferences;

/**
 * Edits the parameters for simulating data from Bayes nets.
 *
 * @author josephramsey
 * @author Frank Wimberly based on similar classes by Joe Ramsey
 * @version $Id: $Id
 */
public class BootstrapSamplerParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameters object being edited.
     */
    private Parameters params;


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
     * <p>setup.</p>
     */
    public void setup() {
        buildGui();
    }

    /**
     * <p>mustBeShown.</p>
     *
     * @return a boolean
     */
    public boolean mustBeShown() {
        return true;
    }

    //================================= Private Methods ===============================//

    /**
     * Constructs the Gui used to edit properties; called from each constructor. Constructs labels and text fields for
     * editing each property and adds appropriate listeners.
     */
    private void buildGui() {
        setLayout(new BorderLayout());
        JLabel label;

        if (Preferences.userRoot().getBoolean("withReplacement", true)) {
            label = new JLabel("<html>" +
                               "The input dataset will be sampled with replacement to create a" +
                               "<br>new dataset with the number of samples entered below." +
                               "</html>");
        } else {
            label = new JLabel("<html>" +
                               "The input dataset will be sampled without replacement to create a " +
                               "<br>new>dataset with the number of samples entered below." +
                               "</html>");
        }

        IntTextField sampleSizeField = new IntTextField(Preferences.userRoot().getInt("bootstrapSampleSize",
                1000), 6);
        BootstrapSamplerParamsEditor.this.params.set("sampleSize",
                Preferences.userRoot().getInt("bootstrapSampleSize", 1000));

        sampleSizeField.setFilter((value, oldValue) -> {
            try {
                BootstrapSamplerParamsEditor.this.params.set("sampleSize", value);
                Preferences.userRoot().putInt("bootstrapSampleSize", value);
                return value;
            } catch (IllegalArgumentException e) {
                return oldValue;
            }
        });

        Checkbox withReplacementCheckbox = new Checkbox("With replacement",
                Preferences.userRoot().getBoolean("withReplacement", true));

        withReplacementCheckbox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                BootstrapSamplerParamsEditor.this.params.set("withReplacement", true);
                Preferences.userRoot().putBoolean("withReplacement", true);
                label.setText("<html>" +
                              "The input dataset will be sampled with replacement to create a " +
                              "<br>new>dataset with the number of samples entered below." +
                              "</html>");
            } else {
                BootstrapSamplerParamsEditor.this.params.set("withReplacement", false);
                Preferences.userRoot().putBoolean("withReplacement", false);
                label.setText("<html>" +
                              "The input dataset will be sampled without replacement to create a" +
                              "<br>new dataset with the number of samples entered below." +
                              "</html>");
            }
        });

        // continue workbench construction.
        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(label);

        Box b7 = Box.createHorizontalBox();
        b7.add(Box.createHorizontalGlue());
        b7.add(new JLabel("<html>" + "<i>Sample size:  </i>" + "</html>"));
        b7.add(sampleSizeField);

        b7.add(Box.createHorizontalStrut(10));
        b7.add(withReplacementCheckbox);

        b1.add(b2);
        b1.add(Box.createVerticalStrut(5));
        b1.add(b7);
        b1.add(Box.createHorizontalGlue());
        add(b1, BorderLayout.CENTER);
    }

}







