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
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.model.GraphSource;
import edu.cmu.tetradapp.model.Simulation;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.List;

/**
 * Edits the parameters for generating random graphs.
 *
 * @author Joseph Ramsey
 */
public class EdgewiseComparisonParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameters object being edited.
     */
    private Parameters params = null;

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

    public void setParams(Parameters params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = params;
    }

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

        if (graphSources.size() == 1 && graphSources.get(0) instanceof GeneralAlgorithmRunner) {
            model1 = (GeneralAlgorithmRunner) graphSources.get(0);
            model2 = ((GeneralAlgorithmRunner) model1).getDataWrapper();
        } else if (graphSources.size() == 2) {
            model1 = (SessionModel) graphSources.get(0);
            model2 = (SessionModel) graphSources.get(1);
        } else {
            throw new IllegalArgumentException("Expecting 2 graph source.");
        }

        setLayout(new BorderLayout());

        // Reset?
        JRadioButton resetOnExecute = new JRadioButton("Reset");
        JRadioButton dontResetOnExecute = new JRadioButton("Appended to");
        ButtonGroup group1 = new ButtonGroup();
        group1.add(resetOnExecute);
        group1.add(dontResetOnExecute);

        resetOnExecute.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getParams().set("resetTableOnExecute", true);
            }
        });

        dontResetOnExecute.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getParams().set("resetTableOnExecute", false);
            }
        });

        if (getParams().getBoolean("resetTableOnExecute", false)) {
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

        latents.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getParams().set("keepLatents", true);
            }
        });

        if (getParams().getBoolean("keepLatents", false)) {
            latents.setSelected(true);
        } else {
            noLatents.setSelected(true);
        }

        // True graph?
        JRadioButton graph1 = new JRadioButton(model1.getName());
        JRadioButton graph2 = new JRadioButton(model2.getName());

        graph1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getParams().set("referenceGraphName", model1.getName());
                getParams().set("targetGraphName", model2.getName());
            }
        });

        graph2.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getParams().set("referenceGraphName", model2.getName());
                getParams().set("targetGraphName", model1.getName());
            }
        });

        ButtonGroup group = new ButtonGroup();
        group.add(graph1);
        group.add(graph2);

        boolean alreadySet = false;

        if (model1 instanceof GeneralAlgorithmRunner) {
            graph1.setSelected(true);
        }

        if (model2 instanceof GeneralAlgorithmRunner) {
            graph2.setSelected(true);
            alreadySet = true;
        }

        if (model2 instanceof Simulation) {
            graph2.setSelected(true);
            alreadySet = true;
        }

        if (!alreadySet) {
            graph1.setSelected(true);
        }

        if (graph1.isSelected()) {
            getParams().set("referenceGraphName", model1.getName());
            getParams().set("targetGraphName", model2.getName());
        } else if (graph2.isSelected()) {
            getParams().set("referenceGraphName", model2.getName());
            getParams().set("targetGraphName", model1.getName());
        }

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





