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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.util.DesktopController;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.LinkedList;
import java.util.List;

/**
 * Discretizes selected columns in a data set.
 *
 * @author Joseph Ramsey
 */
final class CreateTimeSeriesDataAction extends AbstractAction {

    /**
     * The data editor.                         -
     */
    private DataEditor dataEditor;
    private DataSet dataSet;
    private int numLags = 2;

    /**
     * Creates new action to discretize columns.
     */
    public CreateTimeSeriesDataAction(DataEditor editor) {
        super("Time Series Data");

        if (editor == null) {
            throw new NullPointerException();
        }

        this.dataEditor = editor;
    }

    /**
     * Performs the action of loading a session from a file.
     */
    public void actionPerformed(ActionEvent e) {
        DataModel dataModel = getDataEditor().getSelectedDataModel();

        if (!(dataModel instanceof DataSet)) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "Must be a tabular data set.");
            return;
        }

        this.dataSet = (DataSet) dataModel;

        SpinnerNumberModel spinnerNumberModel =
                new SpinnerNumberModel(getNumLags(), 0, 20, 1);
        JSpinner jSpinner = new JSpinner(spinnerNumberModel);
        jSpinner.setPreferredSize(jSpinner.getPreferredSize());

        spinnerNumberModel.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                SpinnerNumberModel model = (SpinnerNumberModel) e.getSource();
                setNumLags(model.getNumber().intValue());
            }
        });

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        Box b = Box.createVerticalBox();
        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Number of time lags: "));
        b1.add(Box.createHorizontalGlue());
        b1.add(Box.createHorizontalStrut(15));
        b1.add(jSpinner);
        b1.setBorder(new EmptyBorder(10, 10, 10, 10));
        b.add(b1);

        panel.add(b, BorderLayout.CENTER);

        EditorWindow editorWindow = new EditorWindow(panel,
                "Create time series data", "Save", true, dataEditor);
        DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
        editorWindow.setVisible(true);

        editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
            public void internalFrameClosed(InternalFrameEvent e) {
                EditorWindow window = (EditorWindow) e.getSource();

                if (!window.isCanceled()) {
                    if (dataSet.isContinuous()) {
                        createContinuousTimeSeriesData();
                    }
                    else if (dataSet.isDiscrete()) {
                        createDiscreteTimeSeriesData();
                    }
                    else {
                        JOptionPane.showMessageDialog(
                                JOptionUtils.centeringComp(),
                                "Data set must be either continuous or discrete.");
                    }
                }
            }
        });
    }

    private void createContinuousTimeSeriesData() {

        // GIVEN: Continuous data set D, maximum lag m.
        Node[] dataVars = dataSet.getVariables().toArray(new Node[0]);
        int n = dataVars.length;
        int m = getNumLags();

        // LetXi, i = 0,...,n-1, be the variables from the data. Let Xi(t) be
        // the variable Xi at time lag t (before 0), t = 0,...,m.
        Node[][] laggedVars = new Node[m + 1][n];
        IKnowledge knowledge = new Knowledge2();

        for (int s = 0; s < m; s++) {
            for (int j = 0; j < n; j++) {
                String name1 = dataVars[j].getName();
                String name2 = name1 + ":" + (s + 1);
                laggedVars[s][j] = new ContinuousVariable(name2);
                laggedVars[s][j].setCenter(80 * j + 50, 80 * (m - s) + 50);
                knowledge.addToTier(s, laggedVars[s][j].getName());
            }
        }

        // 2. Prepare the data the way you did.
        List<Node> variables = new LinkedList<Node>();

        for (int s = 0; s < m; s++) {
            for (int i = 0; i < n; i++) {
                double[] rawData = new double[dataSet.getNumRows()];

                for (int j = 0; j < dataSet.getNumRows(); j++) {
                    rawData[j] = dataSet.getDouble(j, i);
                }

                int size = dataSet.getNumRows();

                double[] laggedRaw = new double[size - m + 1];
                System.arraycopy(rawData, s, laggedRaw, 0, size - m + 1);
                variables.add((ContinuousVariable) laggedVars[s][i]);
            }
        }

        DataSet _laggedData =
                new ColtDataSet(dataSet.getNumRows() - m + 1, variables);

        for (int s = 0; s < m; s++) {
            for (int i = 0; i < n; i++) {
                double[] rawData = new double[dataSet.getNumRows()];

                for (int j = 0; j < dataSet.getNumRows(); j++) {
                    rawData[j] = dataSet.getDouble(j, i);
                }

                int size = dataSet.getNumRows();

                double[] laggedRaw = new double[size - m + 1];
                System.arraycopy(rawData, s, laggedRaw, 0, size - m + 1);
                int col = _laggedData.getVariables().indexOf(laggedVars[s][i]);

                for (int i1 = 0; i1 < laggedRaw.length; i1++) {
                    _laggedData.setDouble(i1, col, laggedRaw[i1]);
                }
            }
        }

        knowledge.setDefaultToKnowledgeLayout(true);
        _laggedData.setKnowledge(knowledge);
        DataModelList list = new DataModelList();
        list.add(_laggedData);
        getDataEditor().reset(list);
        getDataEditor().selectFirstTab();
    }

    private void createDiscreteTimeSeriesData() {

        // GIVEN: Continuous data set D, maximum lag m.
        Node[] dataVars = dataSet.getVariables().toArray(new Node[0]);
        int n = dataVars.length;
        int m = getNumLags();

        // LetXi, i = 0,...,n-1, be the variables from the data. Let Xi(t) be
        // the variable Xi at time lag t (before 0), t = 0,...,m.
        Node[][] laggedVars = new Node[m + 1][n];
        IKnowledge knowledge = new Knowledge2();

        for (int s = 0; s <= m; s++) {
            for (int j = 0; j < n; j++) {
                String name1 = dataVars[j].getName();
                String name2 = name1 + ":" + (s + 1);
                laggedVars[s][j] =
                        new DiscreteVariable((DiscreteVariable) dataVars[j]);
                laggedVars[s][j].setName(name2);
                laggedVars[s][j].setCenter(80 * j + 50, 80 * (m - s) + 50);
                knowledge.addToTier(s, laggedVars[s][j].getName());
            }
        }

        // 2. Prepare the data the way you did.
        List<Node> variables = new LinkedList<Node>();

        for (int s = 0; s <= m; s++) {
            for (int i = 0; i < n; i++) {
                int[] rawData = new int[dataSet.getNumRows()];

                for (int j = 0; j < dataSet.getNumRows(); j++) {
                    rawData[j] = dataSet.getInt(j, i);
                }

                int size = dataSet.getNumRows();

                int[] laggedRaw = new int[size - m + 1];
                System.arraycopy(rawData, m - s, laggedRaw, 0, size - m + 1);
                variables.add(laggedVars[s][i]);
            }
        }

        DataSet _laggedData =
                new ColtDataSet(dataSet.getNumRows() - m + 1, variables);

        for (int s = 0; s <= m; s++) {
            for (int i = 0; i < n; i++) {
                int[] rawData = new int[dataSet.getNumRows()];

                for (int j = 0; j < dataSet.getNumRows(); j++) {
                    rawData[j] = dataSet.getInt(j, i);
                }

                int size = dataSet.getNumRows();

                int[] laggedRaw = new int[size - m + 1];
                System.arraycopy(rawData, m - s, laggedRaw, 0, size - m + 1);
                int _col = _laggedData.getColumn(laggedVars[s][i]);

                for (int j = 0; j < dataSet.getNumRows(); j++) {
                    _laggedData.setInt(j, _col, laggedRaw[j]);
                }
            }
        }

        knowledge.setDefaultToKnowledgeLayout(true);
        _laggedData.setKnowledge(knowledge);
        DataModelList list = new DataModelList();
        list.add(_laggedData);
        getDataEditor().reset(list);
        getDataEditor().selectFirstTab();

    }

    private void setNumLags(int numLags) {
        if (numLags < 2 || numLags > 20) {
            throw new IllegalArgumentException();
        }

        this.numLags = numLags;
    }

    private DataEditor getDataEditor() {
        return dataEditor;
    }

    private int getNumLags() {
        return numLags;
    }
}





