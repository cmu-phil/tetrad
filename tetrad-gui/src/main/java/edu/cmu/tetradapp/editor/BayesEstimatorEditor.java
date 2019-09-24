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
import edu.cmu.tetrad.bayes.BayesProperties;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.model.BayesEstimatorWrapper;
import edu.cmu.tetradapp.model.BayesImWrapper;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.NumberFormat;
import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;

/**
 * An editor for Bayes net instantiated models. Assumes that the workbench and
 * parameterized model have been established (that is, that the nodes have been
 * identified and named and that the number and names of the values for the
 * nodes have been specified) and allows the user to set conditional
 * probabilities of node values given combinations of parent values.
 *
 * @author Aaron Powers
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class BayesEstimatorEditor extends JPanel {

    private static final long serialVersionUID = 1L;

    private JPanel targetPanel;
    /**
     * The wizard that allows the user to modify parameter values for this IM.
     */
    private BayesEstimatorEditorWizard wizard;
    private BayesEstimatorWrapper wrapper;

    /**
     * Constructs a new instantiated model editor from a Bayes IM.
     */
    public BayesEstimatorEditor(BayesIm bayesIm, DataSet dataSet) {
        this(new BayesEstimatorWrapper(new DataWrapper(dataSet), new BayesImWrapper(bayesIm)));
    }

    /**
     * Constructs a new Bayes IM Editor from a Bayes estimator wrapper.
     */
    public BayesEstimatorEditor(BayesEstimatorWrapper bayesEstWrapper) {
        this.wrapper = bayesEstWrapper;

        setLayout(new BorderLayout());

        targetPanel = new JPanel();
        targetPanel.setLayout(new BorderLayout());

        resetBayesImEditor();

        add(targetPanel, BorderLayout.CENTER);
        validate();

        if (wrapper.getNumModels() > 1) {
            final JComboBox<Integer> comp = new JComboBox<>();

            for (int i = 0; i < wrapper.getNumModels(); i++) {
                comp.addItem(i + 1);
            }

            comp.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    wrapper.setModelIndex(((Integer) comp.getSelectedItem()).intValue() - 1);
                    resetBayesImEditor();
                    validate();
                }
            });

            comp.setMaximumSize(comp.getPreferredSize());

            Box b = Box.createHorizontalBox();
            b.add(new JLabel("Using model"));
            b.add(comp);
            b.add(new JLabel("from "));
            b.add(new JLabel(wrapper.getName()));
            b.add(Box.createHorizontalGlue());

            add(b, BorderLayout.NORTH);
        }
    }

    /**
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
    private BayesEstimatorEditorWizard getWizard() {
        return wizard;
    }

    private void resetBayesImEditor() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        // Rest of setup
        BayesIm bayesIm = wrapper.getEstimatedBayesIm();
        BayesPm bayesPm = bayesIm.getBayesPm();
        Graph graph = bayesPm.getDag();

        GraphWorkbench workbench = new GraphWorkbench(graph);
        wizard = new BayesEstimatorEditorWizard(bayesIm, workbench);
        wizard.enableEditing(false);

        wizard.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("editorValueChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("modelChanged", null, null);
                }
            }
        });

        JScrollPane workbenchScroll = new JScrollPane(workbench);
        workbenchScroll.setPreferredSize(new Dimension(400, 400));

        JScrollPane wizardScroll = new JScrollPane(getWizard());

        BayesProperties properties = new BayesProperties(wrapper.getDataSet());

        StringBuilder buf = new StringBuilder();
        buf.append("\nP-value = ").append(properties.getLikelihoodRatioP(graph));
//        buf.append("\nP-value = ").append(properties.getVuongP());
        buf.append("\nDf = ").append(properties.getDof());
        /*
      Formats numbers.
         */
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        buf.append("\nChi square = ")
                .append(nf.format(properties.getChisq()));
        buf.append("\nBIC score = ").append(nf.format(properties.getBic()));
        buf.append("\n\nH0: Complete graph.");

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
        getWizard().addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("editorClosing".equals(evt.getPropertyName())) {
                    firePropertyChange("editorClosing", null, getName());
                }

                if ("closeFrame".equals(evt.getPropertyName())) {
                    firePropertyChange("closeFrame", null, null);
                    firePropertyChange("editorClosing", true, true);
                }
            }
        });

        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        menuBar.add(file);
//        file.add(new SaveScreenshot(this, true, "Save Screenshot..."));
        file.add(new SaveComponentImage(workbench, "Save Graph Image..."));
        panel.add(menuBar, BorderLayout.NORTH);

        targetPanel.add(panel, BorderLayout.CENTER);
        validate();
    }
}
