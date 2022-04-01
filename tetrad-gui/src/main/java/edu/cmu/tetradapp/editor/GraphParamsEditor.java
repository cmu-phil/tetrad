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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.util.Parameters;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * Edits the parameters for generating random graphs.
 *
 * @author Joseph Ramsey
 */
public class GraphParamsEditor extends JPanel implements ParameterEditor {
    private Parameters params = new Parameters();

    /**
     * Constructs a dialog to edit the given workbench randomization
     * parameters.
     */
    public GraphParamsEditor() {
    }

    public void setParams(Parameters params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = params;
        setup();
    }

    public void setParentModels(Object[] parentModels) {
        // Do nothing.
    }

    public void setup() {
        boolean cyclicAllowed = this.params.getBoolean("cyclicAllowed", false);
        RandomGraphEditor randomDagEditor = new RandomGraphEditor(cyclicAllowed, this.params);
        RandomMimParamsEditor randomMimEditor = new RandomMimParamsEditor(this.params);
        RandomDagScaleFreeEditor randomScaleFreeEditor = new RandomDagScaleFreeEditor();

        // construct the workbench.
        setLayout(new BorderLayout());

        Box b1 = Box.createVerticalBox();
        Box b2 = Box.createVerticalBox();

        b2.setBorder(new TitledBorder(""));
        b1.add(b2);
        b1.add(Box.createVerticalStrut(5));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("DAG", randomDagEditor);
        tabs.add("MIM", randomMimEditor);
        tabs.add("Scale Free", randomScaleFreeEditor);

        String type = this.params.getString("randomGraphType", "Uniform");

        switch (type) {
            case "Uniform":
                tabs.setSelectedIndex(0);
                break;
            case "Mim":
                tabs.setSelectedIndex(1);
                break;
            case "ScaleFree":
                tabs.setSelectedIndex(2);
                break;
            default:
                throw new IllegalStateException("Unrecognized graph type: " + type);
        }

        tabs.addChangeListener(changeEvent -> {
            JTabbedPane pane = (JTabbedPane) changeEvent.getSource();

            if (pane.getSelectedIndex() == 0) {
                GraphParamsEditor.this.params.set("randomGraphType", "Uniform");
            } else if (pane.getSelectedIndex() == 1) {
                GraphParamsEditor.this.params.set("randomGraphType", "Mim");
            } else if (pane.getSelectedIndex() == 2) {
                GraphParamsEditor.this.params.set("randomGraphType", "ScaleFree");
            }
        });

        Box b6 = Box.createHorizontalBox();
        b6.add(tabs);
        b6.add(Box.createHorizontalGlue());

        // RandomGraphEditor adds a titled border.
//        b6.setBorder(new TitledBorder(""));
        b1.add(b6);

        b1.add(Box.createHorizontalGlue());
        add(b1, BorderLayout.CENTER);
    }

    public boolean mustBeShown() {
        return false;
    }
}





