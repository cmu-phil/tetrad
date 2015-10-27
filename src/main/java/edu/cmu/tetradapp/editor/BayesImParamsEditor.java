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

import edu.cmu.tetrad.util.Params;
import edu.cmu.tetradapp.model.BayesImParams;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Edits the parameters for simulating data from Bayes nets.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class BayesImParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameters object being edited.
     */
    private BayesImParams params = null;

    /**
     * Constructs a dialog to edit the given workbench Bayes simulation
     * getMappings object.
     */
    public BayesImParamsEditor() {
    }

    public void setParams(Params params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = (BayesImParams) params;
    }

    public void setParentModels(Object[] parentModels) {
        // Do nothing.
    }

    /**
     * Constructs the Gui used to edit properties; called from each constructor.
     * Constructs labels and text fields for editing each property and adds
     * appropriate listeners.
     */
    public void setup() {
        setLayout(new BorderLayout());

        JRadioButton manually = new JRadioButton();
        final JRadioButton randomly = new JRadioButton();
        final JCheckBox randomEveryTime = new JCheckBox();

        manually.setText("Manually.");
        randomly.setText("Randomly.");
        randomEveryTime.setText("<html>" +
                "Pick new random values every time this " +
                "<br>Bayes IM is re-initialized." + "</html>");
        randomEveryTime.setVerticalTextPosition(SwingConstants.TOP);

        ButtonGroup group = new ButtonGroup();
        group.add(manually);
        group.add(randomly);

        if (getParams().getInitializationMode() == BayesImParams.MANUAL_RETAIN)
        {
            manually.setSelected(true);
            randomEveryTime.setEnabled(false);
            randomEveryTime.setSelected(false);
        }
        else
        if (getParams().getInitializationMode() == BayesImParams.RANDOM_RETAIN)
        {
            randomly.setSelected(true);
            randomEveryTime.setEnabled(true);
            randomEveryTime.setSelected(false);
        }
        else if (getParams().getInitializationMode() == BayesImParams
                .RANDOM_OVERWRITE) {
            randomly.setSelected(true);
            randomEveryTime.setEnabled(true);
            randomEveryTime.setSelected(true);
        }
        else {
            throw new IllegalStateException();
        }

        manually.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getParams().setInitializationMode(BayesImParams.MANUAL_RETAIN);
                randomEveryTime.setEnabled(false);
            }
        });

        randomly.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getParams().setInitializationMode(BayesImParams.RANDOM_RETAIN);
                randomEveryTime.setEnabled(true);
                randomEveryTime.setSelected(false);
            }
        });

        randomEveryTime.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!(randomly.isSelected())) {
                    throw new IllegalStateException("Should only get here if " +
                            "initializing randomly.");
                }

                JCheckBox checkBox = (JCheckBox) e.getSource();

                if (checkBox.isSelected()) {
                    getParams().setInitializationMode(
                            BayesImParams.RANDOM_OVERWRITE);
                }
                else {
                    getParams().setInitializationMode(
                            BayesImParams.RANDOM_RETAIN);
                }
            }
        });

        // continue workbench construction.
        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel(
                "Probability values for this Bayes IM should be filled in: "));
        b2.add(Box.createHorizontalGlue());

        Box b3 = Box.createHorizontalBox();
        b3.add(manually);
        b3.add(Box.createHorizontalGlue());

        Box b4 = Box.createHorizontalBox();
        b4.add(randomly);
        b4.add(Box.createHorizontalGlue());

        Box b5 = Box.createHorizontalBox();
        b5.add(Box.createHorizontalStrut(20));
        b5.add(randomEveryTime);
        b5.add(Box.createHorizontalGlue());

        b1.add(b2);
        b1.add(Box.createVerticalStrut(5));
        b1.add(b3);
        b1.add(b4);
        b1.add(b5);
        b1.add(Box.createHorizontalGlue());
        add(b1, BorderLayout.CENTER);
        setBorder(new EmptyBorder(5, 5, 5, 5));        
    }

    public boolean mustBeShown() {
        return false;
    }

    /**
     * Returns the getMappings object being edited. (This probably should not be
     * public, but it is needed so that the textfields can edit the model.)
     *
     * @return the stored simulation parameters model.
     */
    private synchronized BayesImParams getParams() {
        return this.params;
    }
}





