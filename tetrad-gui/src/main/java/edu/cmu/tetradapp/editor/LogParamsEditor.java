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
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import java.awt.*;

/**
 * Edits the parameters for simulating data from Bayes nets.
 *
 * @author josephramsey
 * @author Frank Wimberly based on similar classes by Joe Ramsey
 * @version $Id: $Id
 */
public class LogParamsEditor extends JPanel implements ParameterEditor {

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

        DoubleTextField aField = new DoubleTextField(this.params.getDouble("a", 10.0), 6, NumberFormatUtil.getInstance().getNumberFormat());
        aField.setFilter((value, oldValue) -> {
            try {
                LogParamsEditor.this.params.set("a", value);
                return value;
            } catch (IllegalArgumentException e) {
                return oldValue;
            }
        });

        IntTextField baseField = new IntTextField(this.params.getInt("base", 0), 4);
        baseField.setFilter((value, oldValue) -> {
            try {
                LogParamsEditor.this.params.set("base", value);
                return value;
            } catch (IllegalArgumentException e) {
                return oldValue;
            }
        });

        // continue workbench construction.
        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("<html>" +
                          "The input dataset will be logarithmically transformed by applying f(x) = ln(a + x) to each data point x." +
                          "<br> Can also 'unlog' the data i.e., apply g(x) = exp(x) - a, or override the base"));


        Box b9 = Box.createHorizontalBox();
        b9.add(Box.createHorizontalGlue());
        b9.add(new JLabel("<html> base (use 0 for natural log and base <i>e</i>): </html>"));
        b9.add(baseField);

        Box b7 = Box.createHorizontalBox();
        b7.add(Box.createHorizontalGlue());
        b7.add(new JLabel("<html><i>a =  </i></html>"));
        b7.add(aField);


        JCheckBox unlog = new JCheckBox();
        unlog.setSelected(this.params.getBoolean("unlog", false));
        unlog.addActionListener(e -> {
            JCheckBox box = (JCheckBox) e.getSource();
            LogParamsEditor.this.params.set("unlog", box.isSelected());
        });

        Box b8 = Box.createHorizontalBox();
        b8.add(Box.createHorizontalGlue());
        b8.add(new JLabel("<html>Unlog: </html>"));
        b8.add(unlog);


        b1.add(b2);
        b1.add(Box.createVerticalStrut(5));
        b1.add(b7);
        b1.add(Box.createHorizontalGlue());
        b1.add(b8);
        b1.add(Box.createHorizontalGlue());
        b1.add(b9);
        b1.add(Box.createHorizontalGlue());
        add(b1, BorderLayout.CENTER);
    }

}

