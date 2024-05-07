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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.BayesProperties;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.BayesEstimatorWrapper;
import edu.cmu.tetradapp.model.BayesImWrapper;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.io.Serial;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * An editor for Bayes net instantiated models. Assumes that the workbench and parameterized model have been
 * established. (That is, that the nodes have been identified and named that the number and names of the values for the
 * nodes have been specified). It Also allows the user to set conditional probabilities of node values given
 * combinations of parent values.
 *
 * @author Aaron Powers
 * @author josephramsey
 * @version $Id: $Id
 */
public class BayesEstimatorEditor extends JPanel {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The panel that contains the workbench and the wizard.
     */
    private final JPanel targetPanel;

    /**
     * The wrapper for the Bayes estimator.
     */
    private final BayesEstimatorWrapper wrapper;

    /**
     * The wizard that allows the user to modify parameter values for this IM.
     */
    private BayesEstimatorEditorWizard wizard;

    /**
     * Constructs a new instantiated model editor from a Bayes IM.
     *
     * @param bayesIm a {@link edu.cmu.tetrad.bayes.BayesIm} object
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public BayesEstimatorEditor(BayesIm bayesIm, DataSet dataSet, Parameters parameters) {
        this(new BayesEstimatorWrapper(new DataWrapper(dataSet), new BayesImWrapper(bayesIm), parameters));
    }

    /**
     * Constructs a new Bayes IM Editor from a Bayes estimator wrapper.
     *
     * @param bayesEstWrapper a {@link edu.cmu.tetradapp.model.BayesEstimatorWrapper} object
     */
    public BayesEstimatorEditor(BayesEstimatorWrapper bayesEstWrapper) {
        this.wrapper = bayesEstWrapper;

        setLayout(new BorderLayout());

        this.targetPanel = new JPanel();
        this.targetPanel.setLayout(new BorderLayout());

        resetBayesImEditor();

        add(this.targetPanel, BorderLayout.CENTER);
        validate();

        if (this.wrapper.getNumModels() > 1) {
            JComboBox<Integer> comp = new JComboBox<>();

            for (int i = 0; i < this.wrapper.getNumModels(); i++) {
                comp.addItem(i + 1);
            }

            comp.addActionListener(e -> {
                Object selectedItem = comp.getSelectedItem();

                if (selectedItem instanceof Integer) {
                    BayesEstimatorEditor.this.wrapper.setModelIndex((Integer) selectedItem - 1);
                    resetBayesImEditor();
                    validate();
                }
            });

            comp.setMaximumSize(comp.getPreferredSize());

            Box b = Box.createHorizontalBox();
            b.add(new JLabel("Using model"));
            b.add(comp);
            b.add(new JLabel("from "));
            b.add(new JLabel(this.wrapper.getName()));
            b.add(Box.createHorizontalGlue());

            add(b, BorderLayout.NORTH);
        }
    }

    /**
     * Sets the name of this component.
     *
     * @param name the string that is to be this component's name
     */
    public void setName(String name) {
        String oldName = getName();
        super.setName(name);
        this.firePropertyChange("name", oldName, getName());
    }

    /**
     * @return a reference to this editor.
     */
    private BayesEstimatorEditorWizard getWizard() {
        return this.wizard;
    }

    private void resetBayesImEditor() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        // Rest of setup
        BayesIm bayesIm = this.wrapper.getEstimatedBayesIm();
        BayesPm bayesPm = bayesIm.getBayesPm();
        Graph graph = bayesPm.getDag();

        GraphWorkbench workbench = new GraphWorkbench(graph);
        this.wizard = new BayesEstimatorEditorWizard(bayesIm, workbench);
        this.wizard.enableEditing(false);

        this.wizard.addPropertyChangeListener(evt -> {
            if ("editorValueChanged".equals(evt.getPropertyName())) {
                firePropertyChange("modelChanged", null, null);
            }
        });

        JScrollPane workbenchScroll = new JScrollPane(workbench);
        workbenchScroll.setPreferredSize(new Dimension(400, 400));

        JScrollPane wizardScroll = new JScrollPane(getWizard());

        DataSet dataSet = this.wrapper.getDataSet();
        BayesProperties properties = new BayesProperties(dataSet);
        StringBuilder buf = new StringBuilder();
        BayesProperties.LikelihoodRet ret = properties.getLikelihoodRatioP(graph);
        NumberFormat nf = new DecimalFormat("0.00");
        buf.append("P-value = ").append(nf.format(ret.p));
        buf.append("\nDf = ").append(nf.format(ret.dof));
        buf.append("\nChi square = ").append(nf.format(ret.chiSq));
        buf.append("\nBIC score = ").append(nf.format(ret.bic));

        buf.append("\n\nH0: Given model");
        buf.append("\nH1: Complete model");

        JTextArea modelParametersText = new JTextArea();
        modelParametersText.setText(buf.toString());

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("Model", wizardScroll);
        tabbedPane.add("Model Statistics", modelParametersText);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                workbenchScroll, tabbedPane);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(workbenchScroll.getPreferredSize().width);

        setLayout(new BorderLayout());
        panel.add(splitPane, BorderLayout.CENTER);

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

        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        menuBar.add(file);
        file.add(new SaveComponentImage(workbench, "Save Graph Image..."));
        panel.add(menuBar, BorderLayout.NORTH);

        this.targetPanel.add(panel, BorderLayout.CENTER);
        validate();
    }
}
