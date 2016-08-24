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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DagWrapper;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GraphWrapper;
import edu.cmu.tetradapp.model.SemGraphWrapper;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.prefs.Preferences;


/**
 * Edits parameters for PC, FCI, CCD, and GA.
 *
 * @author Shane Harwood
 * @author Joseph Ramsey
 */
public final class FciSearchParamEditor extends JPanel implements ParameterEditor {

    /**
     * The parameter object being edited.
     */
    private Parameters params;

    /**
     * A text field for editing the alpha value.
     */
    private DoubleTextField alphaField;

    /**
     * The parent models of this search object; should contain a DataModel.
     */
    private Object[] parentModels;

    //=============================CONSTRUCTORS===========================//

    /**
     * Opens up an editor to let the user view the given PcRunner. Note that
     * this constructor must not change form; it is being used by reflection.
     */
    public FciSearchParamEditor() {
    }

    public void setParams(Parameters params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = params;
    }

    public void setParentModels(Object[] parentModels) {
        if (parentModels == null) {
            throw new NullPointerException();
        }

        this.parentModels = parentModels;
    }

    public void setup() {
        /*
      The variable names from the object being searched over (usually data).
     */
        List varNames = (List<String>) params.get("varNames", null);

        DataModel dataModel1 = null;
        Graph graph = null;

        for (Object parentModel1 : parentModels) {
            if (parentModel1 instanceof DataWrapper) {
                DataWrapper dataWrapper = (DataWrapper) parentModel1;
                dataModel1 = dataWrapper.getSelectedDataModel();
            }

            if (parentModel1 instanceof GraphWrapper) {
                GraphWrapper graphWrapper = (GraphWrapper) parentModel1;
                graph = graphWrapper.getGraph();
            }

            if (parentModel1 instanceof DagWrapper) {
                DagWrapper dagWrapper = (DagWrapper) parentModel1;
                graph = dagWrapper.getDag();
            }

            if (parentModel1 instanceof SemGraphWrapper) {
                SemGraphWrapper semGraphWrapper = (SemGraphWrapper) parentModel1;
                graph = semGraphWrapper.getGraph();
            }
        }

        if (dataModel1 != null) {
            varNames = new ArrayList(dataModel1.getVariableNames());
        }
        else if (graph != null) {
            Iterator<Node> it = graph.getNodes().iterator();
            varNames = new ArrayList();

            Node temp;

            while (it.hasNext()) {
                temp = it.next();

                if (temp.getNodeType() == NodeType.MEASURED) {
                    varNames.add(temp.getName());
                }
            }
        }
        else {
            throw new NullPointerException(
                    "Null model (no graph or data model " +
                            "passed to the search).");
        }

        params.set("varNames", varNames);

        IntTextField depthField =
                new IntTextField(params.getInt("depth", -1), 4);
        depthField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    params.set("depth", value);
                    return value;
                }
                catch (Exception e) {
                    return oldValue;
                }
            }
        });

        double alpha = params.getDouble("alpha", 0.001);

        if (!Double.isNaN(alpha)) {
            alphaField =
                    new DoubleTextField(alpha, 4, NumberFormatUtil.getInstance().getNumberFormat());
            alphaField.setFilter(new DoubleTextField.Filter() {
                public double filter(double value, double oldValue) {
                    try {
                        params.set("alpha", 0.001);
                        Preferences.userRoot().putDouble("alpha", value);
                        return value;
                    }
                    catch (Exception e) {
                        return oldValue;
                    }
                }
            });
        }

        setBorder(new MatteBorder(10, 10, 10, 10, super.getBackground()));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Knowledge:"));
        b1.add(Box.createGlue());
        add(b1);
        add(Box.createVerticalStrut(10));

        if (!Double.isNaN(alpha)) {
            Box b2 = Box.createHorizontalBox();
            b2.add(new JLabel("Alpha Value:"));
            b2.add(Box.createGlue());
            b2.add(alphaField);
            add(b2);
            add(Box.createVerticalStrut(10));
        }

        Box b3 = Box.createHorizontalBox();
        b3.add(new JLabel("Search Depth:"));
        b3.add(Box.createGlue());
        b3.add(depthField);
        add(b3);
        add(Box.createVerticalStrut(10));
    }

    public boolean mustBeShown() {
        return false;
    }
    private Parameters getParams() {
        return params;
    }
}


