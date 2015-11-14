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
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.EmBayesEstimator;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetradapp.model.BayesEstimatorWrapper;
import edu.cmu.tetradapp.model.BayesImWrapper;
import edu.cmu.tetradapp.model.DirichletEstimatorWrapper;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * An editor for Bayes net instantiated models.  Assumes that the workbench and
 * parameterized model have been established (that is, that the nodes have been
 * identified and named and that the number and names of the values for the
 * nodes have been specified) and allows the user to set conditional
 * probabilities of node values given combinations of parent values.
 *
 * @author Aaron Powers
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class BayesImEditor extends JPanel {

    /**
     * The wizard that allows the user to modify parameter values for this IM.
     */
    private BayesImEditorWizard wizard;
    private BayesImWrapper wrapper;

    public BayesImEditor(BayesIm im) {
        if (im == null) {
            throw new NullPointerException();
        }

        init(im);
    }

    /**
     * Constructs a new instanted model editor from a Bayes IM.
     */
    public BayesImEditor(BayesImWrapper wrapper, BayesIm bayesIm) {
        if (wrapper == null) {
            throw new NullPointerException();
        }

        this.wrapper = wrapper;
        init(bayesIm);
    }

    private void init(BayesIm bayesIm) {
        if (bayesIm == null) {
            throw new NullPointerException("Bayes IM must not be null.");
        }

        BayesPm bayesPm = bayesIm.getBayesPm();
        Graph graph = bayesPm.getDag();
        GraphWorkbench workbench = new GraphWorkbench(graph);

        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        menuBar.add(file);
        file.add(new SaveBayesImXmlAction(this));
        file.add(new LoadBayesImXmlAction(wrapper, this));
        file.add(new LoadBayesImXsdlXmlAction(wrapper, this));
        file.add(new SaveScreenshot(this, true, "Save Screenshot..."));
        file.add(new SaveComponentImage(workbench, "Save Graph Image..."));
        setLayout(new BorderLayout());
        add(menuBar, BorderLayout.NORTH);

        wizard = new BayesImEditorWizard(bayesIm, workbench);

        wizard.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("editorValueChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("modelChanged", null, null);
                }
            }
        });

        JScrollPane workbenchScroll = new JScrollPane(workbench);
        JScrollPane wizardScroll = new JScrollPane(getWizard());

        workbenchScroll.setPreferredSize(new Dimension(450, 450));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                workbenchScroll, wizardScroll);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(workbenchScroll.getPreferredSize().width);
        add(splitPane, BorderLayout.CENTER);

        setName("Bayes IM Editor");
        getWizard().addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("editorClosing".equals(evt.getPropertyName())) {
                    firePropertyChange("editorClosing", null, getName());
                }

                if ("closeFrame".equals(evt.getPropertyName())) {
                    firePropertyChange("closeFrame", null, null);
                    firePropertyChange("editorClosing", true, true);
                }

                if ("modelChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("modelChanged", evt.getOldValue(),
                            evt.getNewValue());
                }
            }
        });
    }

    /**
     * Constructs a new instanted model editor from a Bayes IM wrapper.
     */
    public BayesImEditor(BayesImWrapper bayesImWrapper) {
        this(bayesImWrapper, bayesImWrapper.getBayesIm());
    }

    /**
     * Constructs a new Bayes IM Editor from a Bayes estimator wrapper.
     */
    public BayesImEditor(BayesEstimatorWrapper bayesEstWrapper) {
        this(null, bayesEstWrapper.getEstimatedBayesIm());
    }

    public BayesImEditor(EmBayesEstimator wrapper) {
        this(null, wrapper.getEstimatedIm());
    }

    /**
     * Constructs a new Bayes IM Editor from a Dirichlet Prior.
     */
    public BayesImEditor(DirichletEstimatorWrapper dirichletEstWrapper) {
        this(null, dirichletEstWrapper.getEstimatedBayesIm());
    }

    /**
     * Sets the name of this editor.
     */
    public void setName(String name) {
        String oldName = getName();
        super.setName(name);
        firePropertyChange("name", oldName, getName());
    }

    /**
     * @return a reference to this editor.
     */
    public BayesImEditorWizard getWizard() {
        return wizard;
    }

    public void getBayesIm(BayesIm bayesIm) {
        this.wrapper.setBayesIm(bayesIm);
        removeAll();
        init(this.wrapper.getBayesIm());
        revalidate();
        repaint();
        firePropertyChange("modelChanged", null, null);
    }
}





