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

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import java.awt.*;

/**
 * Edits the parameters for simulating data from Bayes nets.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public final class BayesPmParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameters object being edited.
     */
    private Parameters params;

    /**
     * Lets the user edit the number of nodes.
     */
    private IntTextField lowerBoundField;

    /**
     * Lets the user edit the number of nodes.
     */
    private IntTextField upperBoundField;

    /**
     * Constructs a dialog to edit the given workbench Bayes simulation
     * getMappings object.
     */
    public BayesPmParamsEditor() {
    }

    public void setParams(final Parameters params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = params;
    }

    public void setParentModels(final Object[] parentModels) {
        // Do nothing.
    }

    public void setup() {
        this.lowerBoundField = new IntTextField(getParams().getInt("lowerBoundNumVals", 2), 4);
        this.lowerBoundField.setFilter((value, oldValue) -> {
            try {
                getParams().set("lowerBoundNumVals", value);
                return value;
            } catch (final Exception e) {
                return oldValue;
            }
        });

        this.upperBoundField = new IntTextField(getParams().getInt("upperBoundNumVals", 4), 4);
        this.upperBoundField.setFilter((value, oldValue) -> {
            try {
                getParams().set("upperBoundNumVals", value);
                return value;
            } catch (final Exception e) {
                return oldValue;
            }
        });

        // construct the workbench.
        setLayout(new BorderLayout());

        final JRadioButton trinary =
                new JRadioButton("<html>" + "3-valued:</html>");

        final JRadioButton range =
                new JRadioButton("<html>" + "Range:</html>");
        final ButtonGroup group = new ButtonGroup();
        group.add(trinary);
        group.add(range);

        // continue workbench construction.
        final Box b1 = Box.createVerticalBox();

        final Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Categories for variables should be:"));
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(5));

        final Box b3 = Box.createHorizontalBox();
        b3.add(trinary);
        b3.add(Box.createHorizontalGlue());
        b1.add(b3);

        final Box b4 = Box.createHorizontalBox();
        b4.add(Box.createHorizontalStrut(25));
        b4.add(new JLabel("<html>" +
                "All variables will initially have 3 categories, '0', '1' and '2', " +
                "<br>which can then be changed variable by variable in the editor." +
                "</html>"));
        b4.add(Box.createHorizontalGlue());
        b1.add(b4);
        b1.add(Box.createVerticalStrut(10));

        final Box b5 = Box.createHorizontalBox();
        b5.add(range);
        b5.add(Box.createHorizontalGlue());
        b1.add(b5);

        final Box b6 = Box.createHorizontalBox();
        b6.add(Box.createHorizontalStrut(25));
        b6.add(new JLabel("<html>" +
                "Each variable will be automatically be assigned a number of categories" +
                "<br>in a range." + "</html>"));
        b6.add(Box.createHorizontalGlue());
        b1.add(b6);
        b1.add(Box.createVerticalStrut(10));

        final Box b7 = Box.createHorizontalBox();
        b7.add(Box.createHorizontalStrut(25));
        b7.add(new JLabel("Least number of categories for each variable:  "));
        b7.add(Box.createHorizontalGlue());
        b7.add(this.lowerBoundField);
        b1.add(b7);

        final Box b8 = Box.createHorizontalBox();
        b8.add(Box.createHorizontalStrut(25));
        b8.add(new JLabel(
                "Greatest number of categories for each variable:  "));
        b8.add(Box.createHorizontalGlue());
        b8.add(this.upperBoundField);
        b1.add(b8);

        b1.add(Box.createHorizontalGlue());
        add(b1, BorderLayout.CENTER);

        if (getParams().getString("bayesPmInitializationMode", "trinary").equals("trinary")) {
            trinary.setSelected(true);
            this.lowerBoundField.setEnabled(false);
            this.upperBoundField.setEnabled(false);
        } else {
            range.setSelected(true);
            this.lowerBoundField.setEnabled(true);
            this.upperBoundField.setEnabled(true);
        }

        trinary.addActionListener(e -> {
            getParams().set("bayesPmInitializationMode", "trinary");
            this.lowerBoundField.setEnabled(false);
            this.upperBoundField.setEnabled(false);
        });

        range.addActionListener(e -> {
            getParams().set("bayesPmInitializationMode", "range");
            this.lowerBoundField.setEnabled(true);
            this.upperBoundField.setEnabled(true);
        });
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
    private synchronized Parameters getParams() {
        return this.params;
    }
}





