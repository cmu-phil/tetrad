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

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.DirichletBayesIm;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetradapp.model.DirichletBayesImWrapper;
import edu.cmu.tetradapp.model.DirichletEstimatorWrapper;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;

/**
 * An editor for Bayes net instantiated models. Assumes that the workbench and parameterized model have been established
 * (that is, that the nodes have been identified and named and that the number and names of the values for the nodes
 * have been specified) and allows the user to set conditional probabilities of node values given combinations of parent
 * values.
 *
 * @author Aaron Powers
 * @author josephramsey
 * @version $Id: $Id
 */
public class DirichletBayesImEditor extends JPanel {

    /**
     * The wizard for editing the probabilities.
     */
    private final DirichletBayesImProbsWizard probsWizard;

    /**
     * The wizard for editing the pseudocounts.
     */
    private final DirichletBayesImCountsWizard countsWizard;

    /**
     * Constructs a new instantiated model editor from a Bayes IM.
     */
    private DirichletBayesImEditor(DirichletBayesIm dirichletBayesIm) {
        if (dirichletBayesIm == null) {
            throw new NullPointerException("Bayes IM must not be null.");
        }

        // Add a menu item to allow the BayesIm to be saved out in
        // causality lab format.
        setLayout(new BorderLayout());
        BayesPm bayesPm = dirichletBayesIm.getBayesPm();
        Graph graph = bayesPm.getDag();
        GraphWorkbench workbench = new GraphWorkbench(graph);

        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        menuBar.add(file);
//        file.add(new SaveScreenshot(this, true, "Save Screenshot..."));
        file.add(new SaveComponentImage(workbench, "Save Graph Image..."));
        setLayout(new BorderLayout());
        add(menuBar, BorderLayout.NORTH);

        // Rest of setup.
        this.probsWizard = new DirichletBayesImProbsWizard(dirichletBayesIm, workbench);
        this.probsWizard.enableEditing(false);

        this.countsWizard = new DirichletBayesImCountsWizard(dirichletBayesIm, workbench);

        this.probsWizard.addPropertyChangeListener(evt -> {
            if ("editorValueChanged".equals(evt.getPropertyName())) {
                firePropertyChange("modelChanged", null, null);
            }
        });

        this.countsWizard.addPropertyChangeListener(evt -> {
            if ("editorValueChanged".equals(evt.getPropertyName())) {
                firePropertyChange("modelChanged", null, null);
            }
        });

        JScrollPane workbenchScroll = new JScrollPane(workbench);
        JScrollPane probsScroll = new JScrollPane(getProbsWizard());
        JScrollPane countsScroll = new JScrollPane(getCountsWizard());

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("Probabilities", probsScroll);
        tabbedPane.add("Pseudocounts", countsScroll);

        tabbedPane.addChangeListener(e -> {
            JTabbedPane tabbedPane1 = (JTabbedPane) e.getSource();
            tabbedPane1.getSelectedComponent().requestFocus();
        });

        workbenchScroll.setPreferredSize(new Dimension(400, 400));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                workbenchScroll, tabbedPane);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(workbenchScroll.getPreferredSize().width);

        add(splitPane, BorderLayout.CENTER);

        setName("Dirichlet Bayes IM Editor");
        getProbsWizard().addPropertyChangeListener(
                evt -> {
                    if ("editorClosing".equals(evt.getPropertyName())) {
                        firePropertyChange("editorClosing", null,
                                getName());
                    }

                    if ("closeFrame".equals(evt.getPropertyName())) {
                        firePropertyChange("closeFrame", null, null);
                        firePropertyChange("editorClosing", true, true);
                    }
                });
    }

    /**
     * Constructs a new instanted model editor from a Bayes IM wrapper.
     *
     * @param dirichletBayesImWrapper a {@link edu.cmu.tetradapp.model.DirichletBayesImWrapper} object
     */
    public DirichletBayesImEditor(
            DirichletBayesImWrapper dirichletBayesImWrapper) {
        this(dirichletBayesImWrapper.getDirichletBayesIm());
    }

    /**
     * Constructs a new Bayes IM Editor from a Dirichlet Prior.
     *
     * @param dirichletEstWrapper a {@link edu.cmu.tetradapp.model.DirichletEstimatorWrapper} object
     */
    public DirichletBayesImEditor(
            DirichletEstimatorWrapper dirichletEstWrapper) {
        this(dirichletEstWrapper.getEstimatedBayesIm());
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
    private DirichletBayesImProbsWizard getProbsWizard() {
        return this.probsWizard;
    }

    /**
     * @return a reference to this editor.
     */
    private DirichletBayesImCountsWizard getCountsWizard() {
        return this.countsWizard;
    }
}
