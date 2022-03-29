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
import edu.cmu.tetrad.util.Parameters;
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
    private Parameters params;

    /**
     * Constructs a dialog to edit the given workbench Bayes simulation
     * getMappings object.
     */
    public DirichletBayesImParamsEditor() {
    }

    public void setParams(final Parameters params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = params;
    }

    public void setParentModels(final Object[] parentModels) {
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

        final JRadioButton manual = new JRadioButton();
        final JRadioButton randomRetain = new JRadioButton();

        manual.setText("Manually");
        randomRetain.setText("Using a symmetric prior for each row of each conditional" +
                " probability table.");

        final ButtonGroup group = new ButtonGroup();
        group.add(manual);
        group.add(randomRetain);

        final DoubleTextField symmetricAlphaField = new DoubleTextField(
                this.params.getDouble("symmetricAlpha", 1.0), 5, NumberFormatUtil.getInstance().getNumberFormat());
        symmetricAlphaField.setFilter(new DoubleTextField.Filter() {
            public double filter(final double value, final double oldValue) {
                try {
                    DirichletBayesImParamsEditor.this.params.set("symmetricAlpha", value);
                    return value;
                } catch (final IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        if (getParams().getString("initializationMode", "symmetricPrior").equals("manual")) {
            manual.setSelected(true);
            symmetricAlphaField.setEnabled(false);
        } else if (getParams().getString("initializationMode", "symmetricPrior").equals("symmetricPrior")) {
            randomRetain.setSelected(true);
            symmetricAlphaField.setEnabled(true);
        } else {
            throw new IllegalStateException();
        }

        manual.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                getParams().set("initializationMode", "manual");
                symmetricAlphaField.setEnabled(false);
            }
        });

        randomRetain.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                getParams().set("initializationMode", "symmetricPrior");
                symmetricAlphaField.setEnabled(true);
            }
        });

        // continue workbench construction.
        final Box b1 = Box.createVerticalBox();

        final Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel(
                "Pseudocounts for this Dirichlet Bayes IM should be initialized:"));
        b2.add(Box.createHorizontalGlue());

        final Box b3 = Box.createHorizontalBox();
        b3.add(manual);
        b3.add(Box.createHorizontalGlue());

        final Box b4 = Box.createHorizontalBox();
        b4.add(randomRetain);
        b4.add(Box.createHorizontalGlue());

        final Box b5 = Box.createHorizontalBox();
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
     */
    private synchronized Parameters getParams() {
        return this.params;
    }
}





