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
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetradapp.model.DirichletEstimatorParams;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import java.awt.*;

/**
 * Edits the parameters for simulating data from Bayes nets.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class DirichletEstimatorParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameters object being edited.
     */
    private DirichletEstimatorParams params = null;

    /**
     * Constructs a dialog to edit the given workbench Bayes simulation
     * getMappings object.
     */
    public DirichletEstimatorParamsEditor() {
    }

    public void setParams(Params params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = (DirichletEstimatorParams) params;
    }

    public void setParentModels(Object[] parentModels) {
        // Does nothing.
    }

    public boolean mustBeShown() {
        return false;
    }

    /**
     * Constructs the Gui used to edit properties; called from each constructor.
     * Constructs labels and text fields for editing each property and adds
     * appropriate listeners.
     */
    public void setup() {
        setLayout(new BorderLayout());

        final DoubleTextField symmetricAlphaField = new DoubleTextField(
                params.getSymmetricAlpha(), 5, NumberFormatUtil.getInstance().getNumberFormat());
        symmetricAlphaField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    params.setSymmetricAlpha(value);
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        // continue workbench construction.
        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("<html>" +
                "If you make a Dirichlet estimator using a Bayes PM and a " +
                "<br>discrete data set as parents, a Dirichlet Bayes IM will" +
                "<br>be created behind the scenes for you using the number you" +
                "<br>provide below as pseudocount for every cell. This Dirichlet" +
                "<br>Bayes IM will be used as the prior for the estimation. If" +
                "<br>you would like to have more control over how this prior is" +
                "<br>created, please remove the PM-->Estimator edge, add a new" +
                "<br>IM box, connect it as PM-->IM-->Estimator, and create the" +
                "<br>prior you want as a Dirichlet Bayes IM in the IM box." +
                "</html>"));

        Box b7 = Box.createHorizontalBox();
        b7.add(Box.createHorizontalGlue());
        b7.add(new JLabel("<html>" +
                "<i>Estimate using a prior with all pseudocounts =</i>" +
                "</html>"));
        b7.add(symmetricAlphaField);

        b1.add(b2);
        b1.add(Box.createVerticalStrut(5));
        b1.add(b7);
        b1.add(Box.createHorizontalGlue());
        add(b1, BorderLayout.CENTER);
    }

    /**
     * @return the getMappings object being edited. (This probably should not be
     * public, but it is needed so that the textfields can edit the model.)
     *
     * @return the stored simulation parameters model.
     */
    protected synchronized DirichletEstimatorParams getParams() {
        return this.params;
    }
}





