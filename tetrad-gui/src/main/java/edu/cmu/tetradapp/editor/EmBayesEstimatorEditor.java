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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.EmBayesProperties;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.model.EmBayesEstimatorWrapper;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;

/**
 * An editor for Bayes net instantiated models. Assumes that the workbench and parameterized model have been established
 * (that is, that the nodes have been identified and named and that the number and names of the values for the nodes
 * have been specified) and allows the user to set conditional probabilities of node values given combinations of parent
 * values.
 *
 * @author Aaron Powers
 * @author josephramsey
 * @author Frank Wimberly - adapted for EM Bayes estimator and Strucural EM Bayes estimator
 * @version $Id: $Id
 */
public class EmBayesEstimatorEditor extends JPanel {

    private static final long serialVersionUID = -5645975222086813463L;

    /**
     * The wizard that allows the user to modify parameter values for this IM.
     */
    private EMBayesEstimatorEditorWizard wizard;

    /**
     * The workbench showing now; replaced whenever the display is rebuilt.
     */
    private GraphWorkbench workbench;

    /**
     * Constructs a new instanted model editor from a Bayes IM.
     */
    private EmBayesEstimatorEditor(BayesIm bayesIm,
                                   DataSet dataSet) {
        build(bayesIm, dataSet);
    }

    /**
     * Lays out the editor for the given estimate, replacing whatever it showed before.
     */
    private void build(BayesIm bayesIm, DataSet dataSet) {
        removeAll();

        if (bayesIm == null) {
            throw new NullPointerException("Bayes IM must not be null.");
        }

        Graph graph = bayesIm.getBayesPm().getDag();
        GraphWorkbench workbench = new GraphWorkbench(graph);
        this.workbench = workbench;
        this.wizard = new EMBayesEstimatorEditorWizard(bayesIm, workbench);
        this.wizard.enableEditing(false);

        // Add a menu item to allow the BayesIm to be saved out in
        // causality lab format.
        JMenuBar menuBar = new JMenuBar();
        setLayout(new BorderLayout());
        add(menuBar, BorderLayout.NORTH);

        JMenu file = new JMenu("File");
        menuBar.add(file);
//        file.add(new SaveScreenshot(this, true, "Save Screenshot..."));
        file.add(new SaveComponentImage(workbench, "Save Graph Image..."));
        setLayout(new BorderLayout());
        add(menuBar, BorderLayout.NORTH);

        // Rest of setup.
        this.wizard.addPropertyChangeListener(evt -> {
            if ("editorValueChanged".equals(evt.getPropertyName())) {
                firePropertyChange("modelChanged", null, null);
            }
        });

        JScrollPane workbenchScroll = new JScrollPane(workbench);
        workbenchScroll.setPreferredSize(new Dimension(400, 400));

        JScrollPane wizardScroll = new JScrollPane(getWizard());

        EmBayesProperties scorer = new EmBayesProperties(dataSet, graph);
        scorer.setGraph(graph);

        StringBuilder buf = new StringBuilder();
        buf.append("\nP-value = ").append(scorer.getLikelihoodRatioP());
        buf.append("\nDf = ").append(scorer.getPValueDf());
        /*
      Formats numbers.
         */
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        buf.append("\nChi square = ").append(
                nf.format(scorer.getPValueChisq()));
        buf.append("\nBIC score = ").append(nf.format(scorer.getBic()));
        buf.append("\n\nH0: Completely disconnected graph.");

        JTextArea modelParametersText = new JTextArea();
        modelParametersText.setText(buf.toString());

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("Model", wizardScroll);
        tabbedPane.add("Model Statistics", modelParametersText);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                workbenchScroll, tabbedPane);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(workbenchScroll.getPreferredSize().width);
        add(splitPane, BorderLayout.CENTER);

        setName("Bayes IM Editor");
        getWizard().addPropertyChangeListener(evt -> {
            if ("editorClosing".equals(evt.getPropertyName())) {
                firePropertyChange("editorClosing", null, getName());
            }

            if ("closeFrame".equals(evt.getPropertyName())) {
                firePropertyChange("closeFrame", null, null);
                firePropertyChange("editorClosing", true, true);
            }
        });
    }

    /**
     * Constructs a new Bayes IM Editor from a Bayes estimator wrapper.
     *
     * @param emBayesEstWrapper a {@link edu.cmu.tetradapp.model.EmBayesEstimatorWrapper} object
     */
    public EmBayesEstimatorEditor(EmBayesEstimatorWrapper emBayesEstWrapper) {
        this(emBayesEstWrapper.getEstimateBayesIm(),
                //eMbayesEstWrapper.getSelectedDataModel());
                emBayesEstWrapper.getDataSet());

        // The parent data box holds several data sets: say which one the estimate is from, and let another be
        // chosen, which re-estimates on it.
        // The bar along the bottom: the data chooser, if there is a choice, and the plot matrix button.
        Box south = Box.createHorizontalBox();
        south.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        Box[] dataChooser = new Box[1];

        dataChooser[0] = DataSetChooser.create(emBayesEstWrapper.getDataSets(), emBayesEstWrapper.getDataIndex(),
                index -> new WatchedProcess() {
                    @Override
                    public void watch() {
                        try {
                            emBayesEstWrapper.setDataIndex(index);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                                    EmBayesEstimatorEditor.this,
                                    "Could not estimate on that data set; the estimate shown is still from data set "
                                    + (emBayesEstWrapper.getDataIndex() + 1) + ".\n" + ex.getMessage()));
                            return;
                        }

                        SwingUtilities.invokeLater(() -> {
                            build(emBayesEstWrapper.getEstimateBayesIm(), emBayesEstWrapper.getDataSet());
                            add(south, BorderLayout.SOUTH);
                            revalidate();
                            repaint();
                            firePropertyChange("modelChanged", null, null);
                        });
                    }
                });

        if (dataChooser[0] != null) south.add(dataChooser[0]);
        south.add(Box.createHorizontalGlue());

        // Plots are of the data as given, missing values and all; the latent variables EM adds have no column.
        if (!emBayesEstWrapper.getDataSets().isEmpty()) {
            south.add(PlotMatrixForSelected.button(() -> this.workbench,
                    () -> emBayesEstWrapper.getDataSets().get(emBayesEstWrapper.getDataIndex()),
                    emBayesEstWrapper::getDataSets));
        }

        add(south, BorderLayout.SOUTH);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the name of this editor.
     */
    public void setName(String name) {
        String oldName = getName();
        super.setName(name);
        this.firePropertyChange("name", oldName, getName());
    }

    /**
     * @return a reference to this editor.
     */
    private EMBayesEstimatorEditorWizard getWizard() {
        return this.wizard;
    }
}

