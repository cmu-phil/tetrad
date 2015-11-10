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
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetradapp.knowledge_editor.KnowledgeEditor;
import edu.cmu.tetradapp.model.*;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
public final class PcSearchParamEditor extends JPanel implements ParameterEditor {

    /**
     * The parameter object being edited.
     */
    private PcSearchParams params;

    /**
     * A text field for editing the alpha getValue.
     */
    private DoubleTextField alphaField;

    /**
     * The parent models of this search object; should contain a DataModel.
     */
    private Object[] parentModels;

    /**
     * The variable names from the object being searched over (usually data).
     */
    private List<String> varNames;

    /**
     * Opens up an editor to let the user view the given PcRunner.
     */
    public PcSearchParamEditor() {
    }

    public void setParams(Params params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = (PcSearchParams) params;
    }

    public void setParentModels(Object[] parentModels) {
        if (parentModels == null) {
            throw new NullPointerException();
        }

        this.parentModels = parentModels;
    }

    public void setup() {
        this.varNames = params.getVarNames();

        for (Object parentModel : parentModels) {
            if (parentModel instanceof DataWrapper) {
                DataWrapper wrapper = (DataWrapper) parentModel;
                DataModel dataModel = wrapper.getSelectedDataModel();
                new IndTestChooser().adjustIndTestParams(dataModel, params);
                break;
            }
            else if (parentModel instanceof GraphWrapper) {
                GraphWrapper wrapper = (GraphWrapper) parentModel;
                new IndTestChooser().adjustIndTestParams(wrapper.getGraph(),
                        params);
                break;
            }
            else if (parentModel instanceof DagWrapper) {
                DagWrapper wrapper = (DagWrapper) parentModel;
                new IndTestChooser().adjustIndTestParams(wrapper.getGraph(),
                        params);
                break;
            }
            else if (parentModel instanceof SemGraphWrapper) {
                SemGraphWrapper wrapper = (SemGraphWrapper) parentModel;
                new IndTestChooser().adjustIndTestParams(wrapper.getGraph(),
                        params);
                break;
            }
        }

        DataModel dataModel1 = null;
        Graph graph = null;

        for (Object parentModel1 : this.parentModels) {
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
            varNames = new ArrayList<String>(dataModel1.getVariableNames());
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

        this.params.setVarNames(varNames);
        JButton knowledgeButton = new JButton("Edit");

        IntTextField depthField =
                new IntTextField(this.params.getIndTestParams().getDepth(), 4);
        depthField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    PcSearchParamEditor.this.params.getIndTestParams().setDepth(value);
                    Preferences.userRoot().putInt("depth", value);
                    return value;
                }
                catch (Exception e) {
                    return oldValue;
                }
            }
        });

        double alpha = this.params.getIndTestParams().getAlpha();

        if (!Double.isNaN(alpha)) {
            alphaField = new DoubleTextField(alpha, 4, NumberFormatUtil.getInstance().getNumberFormat());
            alphaField.setFilter(new DoubleTextField.Filter() {
                public double filter(double value, double oldValue) {
                    try {
                        PcSearchParamEditor.this.params.getIndTestParams().setAlpha(value);
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
        b1.add(knowledgeButton);
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

        knowledgeButton.addActionListener(new ActionListener() {
            public final void actionPerformed(ActionEvent e) {
                openKnowledgeEditor();
            }
        });
    }

    public boolean mustBeShown() {
        return false;
    }

    /**
     * Must pass knowledge from getMappings. If null, creates new Knowledge2
     * object.
     */
    private void openKnowledgeEditor() {
        if (this.getParams() == null) {
            throw new NullPointerException("Parameter object must not be " +
                    "null if you want to launch a OldKnowledgeEditor.");
        }

        IKnowledge knowledge = this.getParams().getKnowledge();

        KnowledgeEditor knowledgeEditor = new KnowledgeEditor(knowledge,
                varNames, params.getSourceGraph());
        Dialog owner = (Dialog) this.getTopLevelAncestor();

        EditorWindow window = new EditorWindow(knowledgeEditor,
                knowledgeEditor.getName(), "Save", false, this);
        DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
        window.setVisible(true);
    }

    private PcSearchParams getParams() {
        return params;
    }
}





