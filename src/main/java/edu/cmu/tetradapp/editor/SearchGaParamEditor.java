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
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GaParams;
import edu.cmu.tetradapp.model.SearchParams;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Edits parameters for GA.
 *
 * @author Shane Harwood
 * @author Joseph Ramsey
 */
public final class SearchGaParamEditor extends JPanel implements ParameterEditor {

    /**
     * The parameter object being edited.
     */
    private GaParams params;
    private Object[] parentModels;

    public SearchGaParamEditor() {
    }

    public void setParams(Params params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = (GaParams) params;
    }

    public void setParentModels(Object[] parentModels) {
        if (parentModels == null) {
            throw new NullPointerException();
        }

        this.parentModels = parentModels;
    }

    public void setup() {
        DoubleTextField alphaField = new DoubleTextField(params.getAlpha(), 4,
                NumberFormatUtil.getInstance().getNumberFormat());
        alphaField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                if (value < 0.0 || value > 1.0) {
                    return oldValue;
                }

                params.setAlpha(value);
                return value;
            }
        });

        DoubleTextField biasField = new DoubleTextField(params.getBias(), 4,
                NumberFormatUtil.getInstance().getNumberFormat());
        biasField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                if (value < 0.0 || value > 1.0) {
                    return oldValue;
                }

                params.setBias(value);
                return value;
            }
        });

        DoubleTextField timeField = new DoubleTextField(params.getTimeLimit(),
                4, NumberFormatUtil.getInstance().getNumberFormat());
        timeField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                if (value < 0.0 || value > 1.0) {
                    return oldValue;
                }

                params.setTimeLimit(value);
                return value;
            }
        });

        DataModel dataModel = null;
        Graph graph = null;

        for (Object parentModel : this.parentModels) {
            if (parentModel instanceof DataWrapper) {
                DataWrapper dataWrapper = (DataWrapper) parentModel;
                dataModel = dataWrapper.getSelectedDataModel();
            }

            if (parentModel instanceof Graph) {
                graph = (Graph) parentModel;
            }
        }

        List varNames;

        if (dataModel != null) {
            varNames = new ArrayList(dataModel.getVariableNames());
            System.out.println("ar names = " + varNames);
        }
        else if (graph != null) {
            Iterator it = graph.getNodes().iterator();
            varNames = new ArrayList();

            Node temp;

            while (it.hasNext()) {
                temp = (Node) it.next();

                if (temp.getNodeType() == NodeType.MEASURED) {
                    varNames.add(temp.getName());
                }
            }
            System.out.println("ar names = " + varNames);
        }
        else {
            System.out.println("Null Data Model");
        }

        biasField.setValue(1.0);

        timeField.setValue(0.0);

        Color backgroundColor = super.getBackground();

        setBackground(backgroundColor);
        setBorder(new MatteBorder(10, 10, 10, 10, backgroundColor));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JPanel[] rows = new JPanel[9];

        for (int i = 0; i < 9; i++) {
            rows[i] = new JPanel();
        }

        for (int i = 0; i < 3; i++) {
            rows[i].setLayout(new FlowLayout(FlowLayout.LEFT));
            rows[i].setMaximumSize(new Dimension(1000, 20));
        }

        // Second Row
        String msg0 = "Alpha Value:";
        rows[1].setLayout(new FlowLayout(FlowLayout.LEFT));
        rows[1].setMaximumSize(new Dimension(1000, 20));
        rows[1].add(new JLabel(msg0));
        rows[1].add(alphaField);
        add(rows[1]);

        //  -- Third Row
        JButton knowledgeButton =
                new JButton("View Forbiden/Required Edgelist");
        rows[2] = new JPanel();

        rows[2].setBackground(backgroundColor);
        rows[2].setLayout(new FlowLayout(FlowLayout.LEFT));
        rows[2].add(knowledgeButton);
        add(rows[2]);

        //  -- Fourth Row
        String msg1 = "Scoring Bias (1.0 default): ";

        rows[3] = new JPanel();
        rows[3].add(new JLabel(msg1));
        rows[3].add(biasField);
        rows[3].setBackground(backgroundColor);
        rows[3].setLayout(new FlowLayout(FlowLayout.LEFT));
        add(rows[3]);

        //  -- Fourth Row
        String msg2 = "Time Limit(minutes): ";

        rows[4] = new JPanel();
        rows[4].add(new JLabel(msg2));
        rows[4].add(timeField);
        rows[4].setBackground(backgroundColor);
        rows[4].setLayout(new FlowLayout(FlowLayout.LEFT));
        add(rows[4]);

        knowledgeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
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

        IKnowledge knowledge = getParams().getKnowledge();
        List varNames = getParams().getVarNames();
        Graph sourceGraph = getParams().getSourceGraph();

        KnowledgeEditor knowledgeEditor =
                new KnowledgeEditor(knowledge, varNames, sourceGraph);
        EditorWindow window = new EditorWindow(knowledgeEditor,
                knowledgeEditor.getName(), "Save", false, SearchGaParamEditor.this);
        DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
        window.setVisible(true);
    }

    private SearchParams getParams() {
        return params;
    }
}





