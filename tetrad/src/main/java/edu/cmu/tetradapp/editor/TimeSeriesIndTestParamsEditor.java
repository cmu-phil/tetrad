///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.model.LagIndTestParams;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;

/**
 * Edits the properties of a measurement params.
 *
 * @author Joseph Ramsey
 */
class TimeSeriesIndTestParamsEditor extends JComponent {

    /**
     * The parameters object being edited.
     */
    private LagIndTestParams params = null;

    /**
     * Edits the alpha value, in [0, 1].
     */
    private DoubleTextField alphaField;

    /**
     * Edits the number of lags.
     */
    private IntTextField numLagsField;

    /**
     * Constructs a dialog to edit the given gene simulation parameters object.
     */
    public TimeSeriesIndTestParamsEditor(LagIndTestParams simulator) {
        params = simulator;

        alphaField = new DoubleTextField(getLagIndTestParams().getAlpha(), 5,
                NumberFormatUtil.getInstance().getNumberFormat());
        alphaField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    getLagIndTestParams().setAlpha(value);
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        numLagsField = new IntTextField((getLagIndTestParams()).getNumLags(), 3);
        numLagsField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    getLagIndTestParams().setNumLags(value);
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        buildGui();
    }

    /**
     * Constructs the Gui used to edit properties; called from each constructor.
     * Constructs labels and text fields for editing each property and adds
     * appropriate listeners.
     */
    private void buildGui() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Alpha:"));
        b1.add(Box.createHorizontalGlue());
        b1.add(alphaField);
        add(b1);

        //        Box b2 = Box.createHorizontalBox();
        //        b2.add(new JLabel("Window Size:"));
        //        b2.add(Box.createHorizontalGlue());
        //        b2.add(windowSizeField);
        //        add(b2);

        Box b3 = Box.createHorizontalBox();
        b3.add(new JLabel("Num Lags:"));
        b3.add(Box.createHorizontalGlue());
        b3.add(numLagsField);
        add(b3);
        add(Box.createVerticalGlue());

        Box b4 = Box.createHorizontalBox();
        b4.add(new JLabel("Num Times:"));
        b4.add(Box.createHorizontalGlue());
        int numTimePoints = getLagIndTestParams().getNumTimePoints();
        b4.add(new JLabel(Integer.toString(numTimePoints)));
        add(b4);
        add(Box.createVerticalGlue());
    }

    /**
     * @return the getMappings object being edited. (This probably should not be
     * public, but it is needed so that the textfields can edit the model.)
     *
     * @return the stored simulation parameters model.
     */
    private LagIndTestParams getLagIndTestParams() {
        return params;
    }
}





