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

/**
 * Edits the parameters used to initialize a Hybrid CG parametric model from a graph.
 * The user chooses how variable types are assigned when the PM is first constructed:
 * types taken from the graph's variables (the default, and the previous behavior),
 * all continuous, all discrete, or a fixed-proportion mixture of the two. For any
 * variables made discrete, a number of categories is drawn uniformly at random in a
 * user-specified range (default 2 to 4).
 *
 * <p>When a data set is attached to the PM box, variable types are taken from the
 * data and these settings are ignored.</p>
 *
 * <p>In the mixture mode, the percentage is applied as a fixed proportion: with p
 * percent discrete and n variables, exactly round(p / 100 * n) variables are chosen
 * discrete by a seeded shuffle, rather than flipping a coin per variable.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class HybridCgPmParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameters object being edited.
     */
    private Parameters params;

    /**
     * Lets the user edit the percentage of variables to make discrete in the mixture mode.
     */
    private IntTextField percentDiscreteField;

    /**
     * Lets the user edit the least number of categories for discrete variables.
     */
    private IntTextField minCategoriesField;

    /**
     * Lets the user edit the greatest number of categories for discrete variables.
     */
    private IntTextField maxCategoriesField;

    /**
     * Constructs the editor.
     */
    public HybridCgPmParamsEditor() {
    }

    /**
     * <p>setParentModels.</p>
     *
     * @param parentModels an array of {@link java.lang.Object} objects
     */
    public void setParentModels(Object[] parentModels) {
        // Do nothing.
    }

    /**
     * <p>setup.</p>
     */
    public void setup() {
        this.percentDiscreteField = new IntTextField(getParams().getInt("hybridcg.percentDiscrete", 50), 4);
        this.percentDiscreteField.setFilter((value, oldValue) -> {
            if (value < 0 || value > 100) return oldValue;
            getParams().set("hybridcg.percentDiscrete", value);
            return value;
        });

        this.minCategoriesField = new IntTextField(getParams().getInt("hybridcg.minCategories", 2), 4);
        this.minCategoriesField.setFilter((value, oldValue) -> {
            if (value < 2) return oldValue;
            getParams().set("hybridcg.minCategories", value);
            return value;
        });

        this.maxCategoriesField = new IntTextField(getParams().getInt("hybridcg.maxCategories", 4), 4);
        this.maxCategoriesField.setFilter((value, oldValue) -> {
            if (value < 2) return oldValue;
            getParams().set("hybridcg.maxCategories", value);
            return value;
        });

        setLayout(new BorderLayout());

        JRadioButton fromGraph = new JRadioButton("<html>Use types from the graph's variables:</html>");
        JRadioButton allContinuous = new JRadioButton("<html>All continuous:</html>");
        JRadioButton allDiscrete = new JRadioButton("<html>All discrete:</html>");
        JRadioButton mixed = new JRadioButton("<html>Mixture of continuous and discrete:</html>");

        ButtonGroup group = new ButtonGroup();
        group.add(fromGraph);
        group.add(allContinuous);
        group.add(allDiscrete);
        group.add(mixed);

        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Variable types for the Hybrid CG PM should be:"));
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(5));

        Box b3 = Box.createHorizontalBox();
        b3.add(fromGraph);
        b3.add(Box.createHorizontalGlue());
        b1.add(b3);

        Box b4 = Box.createHorizontalBox();
        b4.add(Box.createHorizontalStrut(25));
        b4.add(new JLabel("<html>" +
                          "Discrete variables in the graph stay discrete and keep their categories;" +
                          "<br>all other variables are made continuous. If a data set is attached," +
                          "<br>types are taken from the data instead." +
                          "</html>"));
        b4.add(Box.createHorizontalGlue());
        b1.add(b4);
        b1.add(Box.createVerticalStrut(10));

        Box b5 = Box.createHorizontalBox();
        b5.add(allContinuous);
        b5.add(Box.createHorizontalGlue());
        b1.add(b5);
        b1.add(Box.createVerticalStrut(10));

        Box b6 = Box.createHorizontalBox();
        b6.add(allDiscrete);
        b6.add(Box.createHorizontalGlue());
        b1.add(b6);
        b1.add(Box.createVerticalStrut(10));

        Box b7 = Box.createHorizontalBox();
        b7.add(mixed);
        b7.add(Box.createHorizontalGlue());
        b1.add(b7);

        Box b8 = Box.createHorizontalBox();
        b8.add(Box.createHorizontalStrut(25));
        b8.add(new JLabel("Percent of variables to make discrete:  "));
        b8.add(Box.createHorizontalGlue());
        b8.add(this.percentDiscreteField);
        b1.add(b8);
        b1.add(Box.createVerticalStrut(10));

        Box b9 = Box.createHorizontalBox();
        b9.add(new JLabel("<html>Discrete variables not already given categories in the graph will be" +
                          "<br>assigned a number of categories at random in this range:</html>"));
        b9.add(Box.createHorizontalGlue());
        b1.add(b9);
        b1.add(Box.createVerticalStrut(5));

        Box b10 = Box.createHorizontalBox();
        b10.add(Box.createHorizontalStrut(25));
        b10.add(new JLabel("Least number of categories for each discrete variable:  "));
        b10.add(Box.createHorizontalGlue());
        b10.add(this.minCategoriesField);
        b1.add(b10);

        Box b11 = Box.createHorizontalBox();
        b11.add(Box.createHorizontalStrut(25));
        b11.add(new JLabel("Greatest number of categories for each discrete variable:  "));
        b11.add(Box.createHorizontalGlue());
        b11.add(this.maxCategoriesField);
        b1.add(b11);

        b1.add(Box.createHorizontalGlue());
        add(b1, BorderLayout.CENTER);

        String mode = getParams().getString("hybridcg.pmTypeMode", "fromGraph");

        switch (mode) {
            case "allContinuous" -> allContinuous.setSelected(true);
            case "allDiscrete" -> allDiscrete.setSelected(true);
            case "mixed" -> mixed.setSelected(true);
            default -> fromGraph.setSelected(true);
        }

        enableFields(mode);

        fromGraph.addActionListener(e -> setMode("fromGraph"));
        allContinuous.addActionListener(e -> setMode("allContinuous"));
        allDiscrete.addActionListener(e -> setMode("allDiscrete"));
        mixed.addActionListener(e -> setMode("mixed"));
    }

    private void setMode(String mode) {
        getParams().set("hybridcg.pmTypeMode", mode);
        enableFields(mode);
    }

    private void enableFields(String mode) {
        boolean anyDiscrete = mode.equals("allDiscrete") || mode.equals("mixed");
        this.percentDiscreteField.setEnabled(mode.equals("mixed"));
        this.minCategoriesField.setEnabled(anyDiscrete);
        this.maxCategoriesField.setEnabled(anyDiscrete);
    }

    /**
     * <p>mustBeShown.</p>
     *
     * @return a boolean
     */
    public boolean mustBeShown() {
        return false;
    }

    /**
     * Returns the parameters object being edited.
     *
     * @return the stored parameters model.
     */
    private synchronized Parameters getParams() {
        return this.params;
    }

    /**
     * {@inheritDoc}
     */
    public void setParams(Parameters params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = params;
    }
}
