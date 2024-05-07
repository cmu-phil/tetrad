///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.ParameterEditor;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.DecimalFormat;

/**
 * <p>BoxCoxParamsEditor class.</p>
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class BoxCoxParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The params.
     */
    private Parameters params;


    /**
     * Empty constructor that does nothing, call <code>setup()</code> to build panel.
     */
    public BoxCoxParamsEditor() {
        super(new BorderLayout());
    }


    /**
     * {@inheritDoc}
     * <p>
     * Sets the parameters.
     */
    public void setParams(Parameters params) {
        this.params = params;
    }

    /**
     * Does nothing
     *
     * @param parentModels an array of {@link java.lang.Object} objects
     */
    public void setParentModels(Object[] parentModels) {

    }

    /**
     * Builds the panel.
     */
    public void setup() {
        DoubleTextField lambda = new DoubleTextField(this.params.getDouble("lambda", 0), 8, new DecimalFormat("0.0"));

        lambda.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                if (value >= 0) {
                    BoxCoxParamsEditor.this.params.set("lambda", value);
                    return value;
                } else {
                    return oldValue;
                }
            }
        });


        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Lambda: "));
        b1.add(Box.createHorizontalGlue());
        b1.add(Box.createHorizontalStrut(15));
        b1.add(lambda);
        b1.setBorder(new EmptyBorder(10, 10, 10, 10));
        add(b1, BorderLayout.CENTER);
    }

    /**
     * <p>mustBeShown.</p>
     *
     * @return true.
     */
    public boolean mustBeShown() {
        return true;
    }
}



