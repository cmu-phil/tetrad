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

import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.GraphSource;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Edits the parameters for generating random graphs.
 *
 * @author Joseph Ramsey
 */
public class EdgewiseComparisonParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameters object being edited.
     */
    private Parameters params;

    /**
     * The first graph source.
     */
    private SessionModel model1;

    /**
     * The second graph source.
     */
    private SessionModel model2;

    /**
     * The parent models. These should be graph sources.
     */
    private Object[] parentModels;

    public void setParentModels(Object[] parentModels) {
        this.parentModels = parentModels;
    }

    public boolean mustBeShown() {
        return false;
    }

    public void setup() {
        List<GraphSource> graphSources = new LinkedList<>();

        for (Object parentModel : parentModels) {
            if (parentModel instanceof GraphSource) {
                graphSources.add((GraphSource) parentModel);
            }
        }

        if (graphSources.size() != 2) {
            throw new IllegalArgumentException("Expecting two graph sources as input.");
        }

        String name1 = graphSources.get(0).getName();
        String name2 = graphSources.get(1).getName();
        String storedName = Preferences.userRoot().get("__referenceSessionModel", "");

        System.out.println("In body, name1 = " + name1 + " name2 = " + name2 + ", storedName = " + storedName);

        if (name1.startsWith("Simulation")) {
            model1 = (SessionModel) graphSources.get(0);
            model2 = (SessionModel) graphSources.get(1);
        } else if (name2.startsWith("Simulation")) {
            model1 = (SessionModel) graphSources.get(1);
            model2 = (SessionModel) graphSources.get(0);
        } else if (storedName.equals(name1)) {
            model1 = (SessionModel) graphSources.get(0);
            model2 = (SessionModel) graphSources.get(1);
        } else if (storedName.equals(name2)) {
            model1 = (SessionModel) graphSources.get(1);
            model2 = (SessionModel) graphSources.get(0);
        } else {
            model1 = (SessionModel) graphSources.get(0);
            model2 = (SessionModel) graphSources.get(1);
        }

        System.out.println("Decision: reference = " + model1.getName() + ", target = " + model2.getName());

//        Preferences.userRoot().put("__referenceSessionModel", model1.getName());
        params.set("referenceGraphName", model1.getName());
        params.getString("targetGraphName", model2.getName());

        this.setLayout(new BorderLayout());

        // Reset?
        JRadioButton resetOnExecute = new JRadioButton("Reset");
        JRadioButton dontResetOnExecute = new JRadioButton("Appended to");
        ButtonGroup group1 = new ButtonGroup();
        group1.add(resetOnExecute);
        group1.add(dontResetOnExecute);

        resetOnExecute.addActionListener(e -> this.getParams().set("resetTableOnExecute", true));
        dontResetOnExecute.addActionListener(e -> this.getParams().set("resetTableOnExecute", false));

        if (this.getParams().getBoolean("resetTableOnExecute", false)) {
            resetOnExecute.setSelected(true);
        } else {
            dontResetOnExecute.setSelected(true);
        }

        // Latents?
        JRadioButton latents = new JRadioButton("Yes");
        JRadioButton noLatents = new JRadioButton("No");
        ButtonGroup group2 = new ButtonGroup();
        group2.add(latents);
        group2.add(noLatents);

        latents.addActionListener(e -> this.getParams().set("keepLatents", true));

        if (this.getParams().getBoolean("keepLatents", false)) {
            latents.setSelected(true);
        } else {
            noLatents.setSelected(true);
        }

        // True graph?
        JRadioButton graph1 = new JRadioButton(model1.getName());
        JRadioButton graph2 = new JRadioButton(model2.getName());

        graph1.addActionListener(e -> {
            System.out.println("Graph1 button reference = " + model1.getName() + ", target = " + model2.getName());
            Preferences.userRoot().put("__referenceSessionModel", model1.getName());
            params.set("referenceGraphName", model1.getName());
            params.getString("targetGraphName", model1.getName());
        });

        graph2.addActionListener(e -> {
            System.out.println("Graph2 button reference = " + model2.getName() + ", target = " + model1.getName());

            Preferences.userRoot().put("__referenceSessionModel", model2.getName());
            params.set("referenceGraphName", model2.getName());
            params.getString("targetGraphName", model1.getName());
        });

        ButtonGroup group = new ButtonGroup();
        group.add(graph1);
        group.add(graph2);
        graph1.setSelected(true);

        Box b1 = Box.createVerticalBox();

        Box b8 = Box.createHorizontalBox();
        b8.add(new JLabel("Which of the two input graphs is the true graph?"));
        b8.add(Box.createHorizontalGlue());
        b1.add(b8);

        Box b9 = Box.createHorizontalBox();
        b9.add(graph1);
        b9.add(Box.createHorizontalGlue());
        b1.add(b9);

        Box b10 = Box.createHorizontalBox();
        b10.add(graph2);
        b10.add(Box.createHorizontalGlue());
        b1.add(b10);

        b1.add(Box.createHorizontalGlue());
        this.add(b1, BorderLayout.CENTER);
    }

    /**
     * @return the getMappings object being edited. (This probably should not be
     * public, but it is needed so that the textfields can edit the model.)
     */
    private synchronized Parameters getParams() {
        return params;
    }

    public void setParams(Parameters params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = params;
    }
}





