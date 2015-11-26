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
import edu.cmu.tetradapp.model.DirichletBayesImParams;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Edits the parameters for simulating data from Bayes nets.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class DirichletBayesImParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameters object being edited.
     */
    private DirichletBayesImParams params = null;

    /**
     * Constructs a dialog to edit the given workbench Bayes simulation
     * getMappings object.
     */
    public DirichletBayesImParamsEditor() {
    }

    public void setParams(Params params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = (DirichletBayesImParams) params;
    }

    public void setParentModels(Object[] parentModels) {
        // does nothing.
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

        JRadioButton manualRetain = new JRadioButton();
        JRadioButton randomRetain = new JRadioButton();

        manualRetain.setText(
                "Manually, retaining previous values where possible.");
        randomRetain.setText(
                "Using a symmetric prior for each row of each conditional" +
                        " probability table.");

        ButtonGroup group = new ButtonGroup();
        group.add(manualRetain);
        group.add(randomRetain);

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

        if (getParams().getInitializationMode() == DirichletBayesImParams
                .MANUAL_RETAIN) {
            manualRetain.setSelected(true);
            symmetricAlphaField.setEnabled(false);
        }
        else if (getParams().getInitializationMode() == DirichletBayesImParams
                .SYMMETRIC_PRIOR) {
            randomRetain.setSelected(true);
            symmetricAlphaField.setEnabled(true);
        }
        else {
            throw new IllegalStateException();
        }

        manualRetain.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getParams().setInitializationMode(
                        DirichletBayesImParams.MANUAL_RETAIN);
                symmetricAlphaField.setEnabled(false);
            }
        });

        randomRetain.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getParams().setInitializationMode(
                        DirichletBayesImParams.SYMMETRIC_PRIOR);
                symmetricAlphaField.setEnabled(true);
            }
        });

        // continue workbench construction.
        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel(
                "Pseudocounts for this Dirichlet Bayes IM should be initialized:"));
        b2.add(Box.createHorizontalGlue());

        Box b3 = Box.createHorizontalBox();
        b3.add(manualRetain);
        b3.add(Box.createHorizontalGlue());

        Box b4 = Box.createHorizontalBox();
        b4.add(randomRetain);
        b4.add(Box.createHorizontalGlue());

        Box b5 = Box.createHorizontalBox();
        b5.add(Box.createRigidArea(new Dimension(30, 0)));
        b5.add(new JLabel("All pseudocounts = "));
        b5.add(symmetricAlphaField);
        b5.add(Box.createHorizontalGlue());

        b1.add(b2);
        b1.add(Box.createVerticalStrut(5));
        b1.add(b3);
        b1.add(b4);
        b1.add(b5);
        b1.add(Box.createHorizontalGlue());
        add(b1, BorderLayout.CENTER);
    }

    /**
     * @return the getMappings object being edited. (This probably should not be
     * public, but it is needed so that the textfields can edit the model.)
     *
     * @return the stored simulation parameters model.
     */
    private synchronized DirichletBayesImParams getParams() {
        return this.params;
    }
}





