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
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DagWrapper;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GraphWrapper;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.StringTextField;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Edits parameters for Markov blanket search algorithm.
 *
 * @author Frank Wimberly
 */
final class LogisticRegressionParamsPanel extends JPanel
        implements ActionListener, ListSelectionListener {

    /**
     * The parameter object being edited.
     */
    private final Parameters params;

    /**
     * The name of the target variable or node in the regression.
     */
    private String targetName;

    /**
     * The variable names from the object being searched over (usually data).
     */
    private List<String> varNames;

    private transient DataModel dataModel;

    private List<String> regressorNames;

    private final JTextField responseVar;

    private JList availableVarsList;
    private JList predictorVarListbox;

    private final ListSelectionModel varsSelModel;
    private final ListSelectionModel predsSelModel;

    private final ArrowButton responseButton;

    final private static String INCLUDE_RESPONSE = "includeResponse";
    final private static String INCLUDE_PREDICTOR = "includePredictor";
    final private static String EXCLUDE_PREDICTOR = "excludePredictor";

    /**
     * Opens up an editor to let the user view the given RegressionRunner.
     */
    public LogisticRegressionParamsPanel(final Parameters params,
                                         final Object[] parentModels) {
        for (final Object parentModel : parentModels) {
            if (parentModel instanceof DataWrapper) {
                final DataWrapper dataWrapper = (DataWrapper) parentModel;
                this.dataModel = dataWrapper.getSelectedDataModel();
            } else if (parentModel instanceof DataModel) {
                this.dataModel = (DataModel) parentModel;
            }
        }

        if (this.dataModel == null) {
            new JOptionPane("Null DataModel");
        }

        if (params == null) {
            throw new NullPointerException(
                    "Parameters must not be null.");
        }

        this.params = params;

        final Object varNames = params.get("varNames", null);

        if (varNames != null) {
            this.varNames = (List<String>) varNames;
        }

        if (this.varNames == null) {
            this.varNames = getVarsFromData(parentModels);

            if (this.varNames == null) {
                this.varNames = getVarsFromGraph(parentModels);
            }

            params.set("varNames", this.varNames);

            if (this.varNames == null) {
                throw new IllegalStateException(
                        "Variables are not accessible.");
            }

            params().set("varNames", this.varNames);
        }

        final JLabel instructions =
                new JLabel("Select response and predictor variables:");
        final JLabel varsLabel = new JLabel("Variables");
        final JLabel responseLabel = new JLabel("Response");
        final JLabel predictorLabel = new JLabel("Predictor(s)");

        final JScrollPane varListbox = (JScrollPane) createVarListbox();
        final JScrollPane predictorListbox =
                (JScrollPane) createPredictorVarListbox();

        this.responseButton = new ArrowButton(this, INCLUDE_RESPONSE);
        final ArrowButton predictorInButton =
                new ArrowButton(this, INCLUDE_PREDICTOR);
        final ArrowButton predictorOutButton =
                new ArrowButton(this, EXCLUDE_PREDICTOR, false);

        this.responseVar = new StringTextField("", 10);
        this.responseVar.setEditable(false);
        this.responseVar.setBackground(Color.white);
        this.responseVar.setPreferredSize(new Dimension(150, 30));
        this.responseVar.setFont(new Font("SanSerif", Font.BOLD, 12));

        //TEST
        this.responseVar.setText(params.getString("targetName", null));
        if (!this.responseVar.getText().equals("") &&
                this.responseButton.getText().equals(">")) {
            this.responseButton.toggleInclude();
        }

        final DefaultListModel predsModel =
                (DefaultListModel) this.predictorVarListbox.getModel();
        final List<String> paramNames = (List<String>) params.get("regressorNames", null);
        for (final String paramName : paramNames) {
            predsModel.addElement(paramName);
        }

        //Construct availableVarsList of variable names not response nor in predictors.
        final List<String> varListNames = new ArrayList<>(this.varNames);
        final String targetName = params.getString("targetName", null);
        if (varListNames.contains(targetName)) {
            varListNames.remove(targetName);
        }

        final List<String> regNames = (List<String>) params.get("regressorNames", null);
        for (final String regName : regNames) {
            if (varListNames.contains(regName)) {
                varListNames.remove(regName);
            }
        }

        final DefaultListModel varsModel =
                (DefaultListModel) this.availableVarsList.getModel();
        varsModel.clear();
        for (final String varListName : varListNames) {
            varsModel.addElement(varListName);
        }

        this.varsSelModel = this.availableVarsList.getSelectionModel();
        this.varsSelModel.setSelectionMode(
                ListSelectionModel.SINGLE_INTERVAL_SELECTION);

        this.predsSelModel = this.predictorVarListbox.getSelectionModel();
        this.predsSelModel.setSelectionMode(
                ListSelectionModel.SINGLE_INTERVAL_SELECTION);


        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        final Box b = Box.createVerticalBox();

        final Box b0 = Box.createHorizontalBox();
        b0.add(instructions);

        final Box b1 = Box.createHorizontalBox();

        final Box b2 = Box.createVerticalBox();
        final Box b3 = Box.createVerticalBox();
        final Box b4 = Box.createVerticalBox();
        final Box b5 = Box.createVerticalBox();

        final DoubleTextField alphaField = new DoubleTextField(params.getDouble("alpha", 0.001), 4,
                NumberFormatUtil.getInstance().getNumberFormat());
        alphaField.setFilter(new DoubleTextField.Filter() {
            public double filter(final double value, final double oldValue) {
                try {
                    params().set("alpha", 0.001);
                    Preferences.userRoot().putDouble("alpha",
                            params().getDouble("alpha", 0.001));
                    return value;
                } catch (final Exception e) {
                    return oldValue;
                }
            }
        });

        final JLabel alphaLabel = new JLabel("Alpha:");

        b2.add(varsLabel);
        b2.add(varListbox);
        //b2.add(availableVarsList);
        b2.add(alphaLabel);
        b2.add(alphaField);

        b3.add(this.responseButton);
        final Component strut3 = Box.createVerticalStrut(90);
        b3.add(strut3);
        b3.add(predictorInButton);
        b3.add(predictorOutButton);

        responseLabel.setPreferredSize(new Dimension(80, 30));
        b4.add(responseLabel);
        final Component strut42 = Box.createVerticalStrut(120);
        b4.add(strut42);
        b4.add(predictorLabel);

        b5.add(this.responseVar);
        final Component strut5 = Box.createVerticalStrut(10);
        b5.add(strut5);
        b5.add(predictorListbox);

        b1.add(b2);
        b1.add(b3);
        b1.add(b4);
        b1.add(b5);

        b.add(b0);
        b.add(b1);

        add(b);
    }

    private JComponent createVarListbox() {
        this.availableVarsList = new JList(new DefaultListModel());
        final DefaultListModel varsModel =
                (DefaultListModel) this.availableVarsList.getModel();

        for (final String varName : this.varNames) {
            varsModel.addElement(varName);
        }

        this.availableVarsList.setVisibleRowCount(5);
        this.availableVarsList.setFixedCellWidth(100);
        this.availableVarsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.availableVarsList.setSelectedIndex(0);

        return new JScrollPane(this.availableVarsList);
    }

    private JComponent createPredictorVarListbox() {
        this.predictorVarListbox = new JList(new DefaultListModel());
        this.predictorVarListbox.setVisibleRowCount(4);
        this.predictorVarListbox.setFixedCellWidth(100);
        this.predictorVarListbox.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION);
        this.predictorVarListbox.setSelectedIndex(0);

        return new JScrollPane(this.predictorVarListbox);
    }


    private List<String> getVarsFromData(final Object[] parentModels) {
        DataModel dataModel = null;

        for (final Object parentModel : parentModels) {
            if (parentModel instanceof DataWrapper) {
                final DataWrapper dataWrapper = (DataWrapper) parentModel;
                dataModel = dataWrapper.getSelectedDataModel();
            }
        }

        if (dataModel == null) {
            return null;
        } else {
            return new ArrayList<>(dataModel.getVariableNames());
        }
    }

    private List<String> getVarsFromGraph(final Object[] parentModels) {
        Object graphWrapper = null;

        for (final Object parentModel : parentModels) {
            if (parentModel instanceof GraphWrapper) {
                graphWrapper = parentModel;
            } else if (parentModel instanceof DagWrapper) {
                graphWrapper = parentModel;
            }
        }

        if (graphWrapper == null) {
            return null;
        } else {
            Graph graph = null;

            if (graphWrapper instanceof GraphWrapper) {
                graph = ((GraphWrapper) graphWrapper).getGraph();
            } else if (graphWrapper instanceof DagWrapper) {
                graph = ((DagWrapper) graphWrapper).getDag();
            }

            final List<Node> nodes = graph.getNodes();
            final List<String> nodeNames = new LinkedList<>();

            for (final Node node : nodes) {
                nodeNames.add(node.getName());
            }

            return nodeNames;
        }
    }

    private void setRegressorNames(final List<String> names) {
        this.regressorNames = names;
    }

    private Parameters params() {
        return this.params;
    }

    private String targetName() {
        return this.targetName;
    }

    private void setTargetName(final String targetName) {
        this.targetName = targetName;
    }

    public void valueChanged(final ListSelectionEvent e) {

    }

    public void actionPerformed(final ActionEvent e) {
        final String varName;
        int varSelectionIndex, predictorVarSelectionIndex;

        final DefaultListModel varsModel =
                (DefaultListModel) this.availableVarsList.getModel();
        final DefaultListModel predsModel =
                (DefaultListModel) this.predictorVarListbox.getModel();

        final int startIndexVars = this.varsSelModel.getMinSelectionIndex();
        final int endIndexVars = this.varsSelModel.getMaxSelectionIndex();

        final int startIndexPreds = this.predsSelModel.getMinSelectionIndex();
        final int endIndexPreds = this.predsSelModel.getMaxSelectionIndex();

        final String[] varSelectedNames =
                new String[endIndexVars - startIndexVars + 1];
        final String[] predSelectedNames =
                new String[endIndexPreds - startIndexPreds + 1];

        if (varsModel.size() != 0) {
            varName = (String) this.availableVarsList.getSelectedValue();
        } else {
            varName = "";
        }

        /* include/exclude response variable */
        if (e.getActionCommand().equals(INCLUDE_RESPONSE)) {
            if ((this.availableVarsList.isSelectionEmpty()) &&
                    (this.responseButton.getIsIncluded())) {
                return;
            }
            //if(varsSelModel.getMaxSelectionIndex() == -1 && responseButton.getIsIncluded()) return;


            if (this.responseButton.getIsIncluded()) {

                //Make sure response variable is dichotomous
                final DataSet cds = (DataSet) this.dataModel;
                final int nrows = cds.getNumRows();
                final Node variable = cds.getVariable(varName);

                final int varIndex = cds.getVariables().indexOf(variable);

                // make sure the variable is binary.
                if (variable instanceof DiscreteVariable) {
                    for (int i = 0; i < nrows; i++) {
                        final int value = cds.getInt(i, varIndex);
                        if (value != 0 && value != 1) {
                            JOptionPane.showMessageDialog(this, "The given target was discrete but not binary");
                            return;
                        }
                    }
                } else {
                    for (int i = 0; i < nrows; i++) {
                        final double value = cds.getDouble(i, varIndex);
                        if (value != 0.0d && value != 1.0d) {
                            JOptionPane.showMessageDialog(this, "Target must be a dictotomous variable");
                            return;
                        }
                    }
                }

                this.responseVar.setText(varName);
                varsModel.removeElement(varName);

            } else {
                varsModel.addElement(this.responseVar.getText());

                this.responseVar.setText("");
                this.responseButton.toggleInclude();  //Test
                return;                          //Test
            }
            this.responseButton.toggleInclude();

            final String newTargetName = this.responseVar.getText();
            setTargetName(newTargetName);
            params().set("targetName", targetName());
        }

        // include predictor variable.
        else if (e.getActionCommand().equals(INCLUDE_PREDICTOR)) {
            if (this.availableVarsList.isSelectionEmpty()) {
                return;
            }

            final int intervalSize = endIndexVars - startIndexVars + 1;
            for (int i = 0; i < intervalSize; i++) {
                varSelectedNames[i] =
                        (String) this.availableVarsList.getSelectedValuesList().get(i);
            }

            for (int i = 0; i < intervalSize; i++) {
                varsModel.removeElement(varSelectedNames[i]);
                predsModel.addElement(varSelectedNames[i]);
            }
        }
        // exclude predictor variable.
        else if (e.getActionCommand().equals(EXCLUDE_PREDICTOR)) {
            if (this.predictorVarListbox.isSelectionEmpty()) {
                return;
            }

            final int intervalSize = endIndexPreds - startIndexPreds + 1;
            for (int i = 0; i < intervalSize; i++) {
                predSelectedNames[i] =
                        (String) this.predictorVarListbox.getSelectedValuesList().get(i);
            }

            for (int i = 0; i < intervalSize; i++) {
                predsModel.removeElement(predSelectedNames[i]);
                varsModel.addElement(predSelectedNames[i]);
            }

        } else {
            return;
        }

        // updates expt variables and predictor listbox.
        varSelectionIndex = this.availableVarsList.getSelectedIndex();
        predictorVarSelectionIndex = this.predictorVarListbox.getSelectedIndex();

        if (varSelectionIndex > 0) {
            varSelectionIndex--;
        }
        if (varSelectionIndex != -1) {
            this.availableVarsList.setSelectedIndex(varSelectionIndex);
        }

        if (predictorVarSelectionIndex > 0) {
            predictorVarSelectionIndex--;
        }
        if (predictorVarSelectionIndex != -1) {
            this.predictorVarListbox.setSelectedIndex(predictorVarSelectionIndex);
        }

        final int numPredictors = predsModel.size();
        final Object[] predictors = new Object[numPredictors];

        final List<String> regNames = new ArrayList<>();

        for (int i = 0; i < numPredictors; i++) {
            predictors[i] = predsModel.getElementAt(i);
            regNames.add((String) predsModel.getElementAt(i));
        }

        setRegressorNames(regNames);

        params().set("regressorNames", this.regressorNames);
    }


    /**
     * Private Inner Class to manipulate the arrow buttons for
     * including/excluding variables for x/y-axis
     */
    public class ArrowButton extends JButton {
        private boolean isInclude;

        public ArrowButton(final LogisticRegressionParamsPanel listener,
                           final String command) {
            this(listener, command, true);
        }

        public ArrowButton(final LogisticRegressionParamsPanel listener,
                           final String command, final boolean isInclude) {
            this.isInclude = isInclude;
            addActionListener(listener);
            setActionCommand(command);

            if (isInclude) {
                setText(">");
            } else {
                setText("<");
            }
        }

        public void toggleInclude() {
            if (this.isInclude) {
                setText("<");
                this.isInclude = false;
            } else {
                setText(">");
                this.isInclude = true;
            }
        }

        public boolean getIsIncluded() {
            return this.isInclude;
        }
    }
}





