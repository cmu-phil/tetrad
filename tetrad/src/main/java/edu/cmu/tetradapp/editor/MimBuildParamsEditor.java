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
import edu.cmu.tetrad.search.MimBuild;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetradapp.knowledge_editor.KnowledgeEditor;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GraphWrapper;
import edu.cmu.tetradapp.model.MimParams;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class is the parameter editor for MIMBuild parameters.
 *
 * @author Ricardo Silva
 */
public class MimBuildParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameter wrapper being viewed.
     */
    private MimParams params;
    private Object[] parentModels;

    /**
     * Opens up an editor to let the user view the given FciRunner.
     */
    public MimBuildParamsEditor() {
    }


    public void setParams(Params params) {
        this.params = (MimParams) params;
    }

    public void setParentModels(Object[] parentModels) {
        this.parentModels = parentModels;
    }

    public void setup() {
        List varNames;

        DoubleTextField alphaField = new DoubleTextField(getParams().getAlpha(),
                4, NumberFormatUtil.getInstance().getNumberFormat());
        alphaField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    getParams().setAlpha(value);
                    return value;
                }
                catch (Exception e) {
                    return oldValue;
                }
            }
        });

        final String[] descriptions = MimBuild.getAlgorithmDescriptions();
        JComboBox algorithmSelector = new JComboBox(descriptions);
        algorithmSelector.setSelectedIndex(params.getAlgorithmType());

        algorithmSelector.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JComboBox combo = (JComboBox) e.getSource();
                int index = combo.getSelectedIndex();
                getParams().setAlgorithmType(index);
            }
        });

        DataModel dataModel = null;
        Graph graph = null;

        for (int i = 0; i < parentModels.length; i++) {
            if (parentModels[i] instanceof DataWrapper) {
                DataWrapper dataWrapper = (DataWrapper) parentModels[i];
                dataModel = dataWrapper.getSelectedDataModel();
            }
            if (parentModels[i] instanceof GraphWrapper) {
                GraphWrapper graphWrapper = (GraphWrapper) parentModels[i];
                graph = graphWrapper.getGraph();
            }
        }

        if (dataModel != null) {
            varNames = new ArrayList(dataModel.getVariableNames());
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
        }
        else {
            System.out.println("Null Data Model");
        }

        Box b = Box.createVerticalBox();

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Alpha:"));
        b1.add(Box.createHorizontalGlue());
        b1.add(alphaField);
        b.add(b1);

        JButton editClusters = new JButton("Edit");

        editClusters.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                openClusterEditor();
            }
        });

        JButton editKnowledge = new JButton("Edit");

        editKnowledge.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                openKnowledgeEditor();
            }
        });

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Cluster Assignments:"));
        b2.add(Box.createHorizontalGlue());
        b2.add(editClusters);
        b.add(b2);

        Box b3 = Box.createHorizontalBox();
        b3.add(new JLabel("Knowledge"));
        b3.add(Box.createHorizontalGlue());
        b3.add(editKnowledge);
        b.add(b3);

        Box b4 = Box.createHorizontalBox();
        b4.add(new JLabel("Algorithm:"));
        b4.add(Box.createHorizontalGlue());
        b4.add(algorithmSelector);
        b.add(b4);

        setLayout(new BorderLayout());
        add(b, BorderLayout.CENTER);
    }

    public boolean mustBeShown() {
        return false;
    }

    private void openClusterEditor() {
        ClusterEditor clusterEditor = new ClusterEditor(
                getParams().getClusters(), getParams().getVarNames());

        EditorWindow window = new EditorWindow(clusterEditor,
                clusterEditor.getName(), "Save", false, this);
        DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
        window.setVisible(true);
    }

    private void openKnowledgeEditor() {
        KnowledgeEditor knowledgeEditor = new KnowledgeEditor(
                getParams().getKnowledge(), getParams().getVarNames(),
                getParams().getSourceGraph());

        EditorWindow window = new EditorWindow(knowledgeEditor,
                knowledgeEditor.getName(), "Save", false, this);
        DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
        window.setVisible(true);

    }

    private MimParams getParams() {
        return params;
    }
}





