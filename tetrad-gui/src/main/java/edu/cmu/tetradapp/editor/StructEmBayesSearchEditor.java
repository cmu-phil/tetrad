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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesProperties;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.model.StructEmBayesSearchRunner;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.NumberFormat;

/**
 * An editor for Bayes net instantiated models.  Assumes that the workbench and
 * parameterized model have been established (that is, that the nodes have been
 * identified and named and that the number and names of the values for the
 * nodes have been specified) and allows the user to set conditional
 * probabilities of node values given combinations of parent values.
 *
 * @author Aaron Powers
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 * @author Frank Wimberly - adapted for EM Bayes estimator and Strucural EM
 * Bayes estimator
 */
class StructEmBayesSearchEditor extends JPanel {

    /**
     * The wizard that allows the user to modify parameter values for this IM.
     */
    private final StructEMBayesSearchEditorWizard wizard;

    /**
     * Constructs a new instanted model editor from a Bayes IM.
     */
    private StructEmBayesSearchEditor(final BayesIm bayesIm,
                                      final DataSet dataSet) {
        if (bayesIm == null) {
            throw new NullPointerException("Bayes IM must not be null.");
        }

        // Add a menu item to allow the BayesIm to be saved out in
        // causality lab format.
        final JMenuBar menuBar = new JMenuBar();
        setLayout(new BorderLayout());
        add(menuBar, BorderLayout.NORTH);

        final JMenu file = new JMenu("File");
        menuBar.add(file);
//        file.add(new SaveScreenshot(this, true, "Save Screenshot..."));
        setLayout(new BorderLayout());
        add(menuBar, BorderLayout.NORTH);

        // Rest of setup.
        final Graph graph = bayesIm.getBayesPm().getDag();

        final GraphWorkbench workbench = new GraphWorkbench(graph);
        this.wizard = new StructEMBayesSearchEditorWizard(bayesIm, workbench);

        this.wizard.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(final PropertyChangeEvent evt) {
                if ("editorValueChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("modelChanged", null, null);
                }
            }
        });

        final JScrollPane workbenchScroll = new JScrollPane(workbench);
        workbenchScroll.setPreferredSize(new Dimension(400, 400));

        final JScrollPane wizardScroll = new JScrollPane(getWizard());

        final BayesProperties scorer = new BayesProperties(dataSet);

        final StringBuilder buf = new StringBuilder();
        buf.append("\nP-value = ").append(scorer.getLikelihoodRatioP(graph));
        buf.append("\nDf = ").append(scorer.getDof());
        /*
      Formats numbers.
     */
        final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        buf.append("\nChi square = ").append(
                nf.format(scorer.getChisq()));
        buf.append("\nBIC score = ").append(nf.format(scorer.getBic()));

        final JTextArea modelParametersText = new JTextArea();
        modelParametersText.setText(buf.toString());

        final JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("Model", wizardScroll);
        tabbedPane.add("Model Statistics", modelParametersText);

        final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                workbenchScroll, tabbedPane);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(workbenchScroll.getPreferredSize().width);
        add(splitPane, BorderLayout.CENTER);

        setName("Bayes IM Editor");
        getWizard().addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(final PropertyChangeEvent evt) {
                if ("editorClosing".equals(evt.getPropertyName())) {
                    firePropertyChange("editorClosing", null, getName());
                }

                if ("closeFrame".equals(evt.getPropertyName())) {
                    firePropertyChange("closeFrame", null, null);
                    firePropertyChange("editorClosing", true, true);
                }
            }
        });
    }

    /**
     * Constructs a new Bayes IM Editor from a Bayes estimator wrapper.
     */
    public StructEmBayesSearchEditor(
            final StructEmBayesSearchRunner semBayesEstWrapper) {
        //this(seMbayesEstWrapper.getEstimateBayesIm(),
        //eMbayesEstWrapper.getSelectedDataModel());
        this(semBayesEstWrapper.getEstimatedBayesIm(),
                semBayesEstWrapper.getDataSet());
    }

    /**
     * Sets the name of this editor.
     */
    public void setName(final String name) {
        final String oldName = getName();
        super.setName(name);
        this.firePropertyChange("name", oldName, getName());
    }

    /**
     * @return a reference to this editor.
     */
    private StructEMBayesSearchEditorWizard getWizard() {
        return this.wizard;
    }
}





