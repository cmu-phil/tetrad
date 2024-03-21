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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetradapp.model.GeneralizedSemEstimatorWrapper;
import edu.cmu.tetradapp.session.DelegatesEditing;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.workbench.LayoutMenu;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.io.Serial;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * The GeneralizedSemEstimatorEditor class represents an editor for the GeneralizedSemEstimatorWrapper.
 * It provides a graphical user interface for editing variables, graph, and generating reports.
 */
public final class GeneralizedSemEstimatorEditor extends JPanel implements DelegatesEditing, LayoutEditable {

    @Serial
    private static final long serialVersionUID = 5161532456725190959L;

    /**
     * A reference to the error terms menu item so it can be reset.
     */
    private final JMenuItem errorTerms;
    /**
     * A common map of nodes to launched editors so that they can all be closed when this editor is closed.
     */
    private final Map<Object, EditorWindow> launchedEditors = new HashMap<>();

    /**
     * The wrapper for the estimator.
     */
    private final GeneralizedSemEstimatorWrapper wrapper;

    /**
     * The graphical editor for the SemIm.
     */
    private GeneralizedSemImGraphicalEditor graphicalEditor;

    //========================CONSTRUCTORS===========================//

    /**
     * Constructs a GeneralizedSemEstimatorEditor with the specified wrapper.
     *
     * @param wrapper the GeneralizedSemEstimatorWrapper to be used
     */
    public GeneralizedSemEstimatorEditor(GeneralizedSemEstimatorWrapper wrapper) {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(800, 600));

        this.wrapper = wrapper;

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("Variables", new JScrollPane(listEditor()));
        tabbedPane.add("Graph", new JScrollPane(graphicalEditor()));
        tabbedPane.add("Report", new JScrollPane(estimationReport()));

        add(tabbedPane, BorderLayout.CENTER);

        Box b = Box.createHorizontalBox();
        b.add(Box.createHorizontalGlue());
        JButton execute = new JButton("Execute");

        execute.addActionListener(e -> {
            wrapper.execute();
            tabbedPane.removeAll();
            tabbedPane.add("Variables", listEditor());
            tabbedPane.add("Graph", graphicalEditor());
            tabbedPane.add("Report", estimationReport());
        });

        b.add(execute);
        add(b, BorderLayout.SOUTH);

        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        menuBar.add(file);
        file.add(new SaveComponentImage(this.graphicalEditor.getWorkbench(),
                "Save Graph Image..."));

        SemGraph graph = (SemGraph) this.graphicalEditor.getWorkbench().getGraph();
        boolean shown = wrapper.isShowErrors();
        graph.setShowErrorTerms(shown);

        this.errorTerms = new JMenuItem();

        if (shown) {
            this.errorTerms.setText("Hide Error Terms");
        } else {
            this.errorTerms.setText("Show Error Terms");
        }

        this.errorTerms.addActionListener(e -> {
            JMenuItem menuItem = (JMenuItem) e.getSource();

            if ("Hide Error Terms".equals(menuItem.getText())) {
                menuItem.setText("Show Error Terms");
                SemGraph graph1 = (SemGraph) GeneralizedSemEstimatorEditor.this.graphicalEditor.getWorkbench().getGraph();
                graph1.setShowErrorTerms(false);
                wrapper.setShowErrors(false);
                graphicalEditor().refreshLabels();
            } else if ("Show Error Terms".equals(menuItem.getText())) {
                menuItem.setText("Hide Error Terms");
                SemGraph graph1 = (SemGraph) GeneralizedSemEstimatorEditor.this.graphicalEditor.getWorkbench().getGraph();
                graph1.setShowErrorTerms(true);
                wrapper.setShowErrors(true);
                graphicalEditor().refreshLabels();
            }
        });

        JMenuItem lengthCutoff = new JMenuItem("Formula Cutoff");

        lengthCutoff.addActionListener(event -> {
            int length = Preferences.userRoot().getInt("maxExpressionLength", 25);

            IntTextField lengthField = new IntTextField(length, 4);
            lengthField.setFilter((value, oldValue) -> {
                try {
                    if (value > 0) {
                        Preferences.userRoot().putInt("maxExpressionLength", value);
                        return value;
                    } else {
                        return 0;
                    }
                } catch (Exception e) {
                    return oldValue;
                }
            });

            Box b12 = Box.createVerticalBox();

            Box b1 = Box.createHorizontalBox();
            b1.add(new JLabel("Formulas longer than "));
            b1.add(lengthField);
            b1.add(new JLabel(" will be replaced in the graph by \"--long formula--\"."));
            b12.add(b1);

            b12.setBorder(new EmptyBorder(5, 5, 5, 5));

            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());
            panel.add(b12, BorderLayout.CENTER);

            EditorWindow editorWindow
                    = new EditorWindow(panel, "Apply Templates", "OK", false, GeneralizedSemEstimatorEditor.this);

            editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
                public void internalFrameClosing(InternalFrameEvent event) {
                    GeneralizedSemEstimatorEditor.this.graphicalEditor.refreshLabels();
                }
            });

            DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
            editorWindow.pack();
            editorWindow.setVisible(true);
        });

        JMenu tools = new JMenu("Tools");
        tools.add(this.errorTerms);
        tools.add(lengthCutoff);
        menuBar.add(tools);

        menuBar.add(new LayoutMenu(this));

        add(menuBar, BorderLayout.NORTH);

        // When the dialog closes, we want to close all generalized expression editors. We do this by
        // detecting when the ancestor of this editor has been removed.
        addAncestorListener(new AncestorListener() {
            public void ancestorAdded(AncestorEvent ancestorEvent) {
            }

            public void ancestorRemoved(AncestorEvent ancestorEvent) {
                for (Object o : GeneralizedSemEstimatorEditor.this.launchedEditors.keySet()) {
                    EditorWindow window = GeneralizedSemEstimatorEditor.this.launchedEditors.get(o);
                    window.closeDialog();
                }
            }

            public void ancestorMoved(AncestorEvent ancestorEvent) {
            }
        });
    }

    /**
     * Returns the editing delegate component.
     *
     * @return The editing delegate component as a JComponent.
     */
    public JComponent getEditDelegate() {
        return graphicalEditor();
    }

    /**
     * Retrieves the graph from the graphical editor's workbench.
     *
     * @return The graph from the workbench.
     */
    public Graph getGraph() {
        return graphicalEditor().getWorkbench().getGraph();
    }

    /**
     * Retrieves the model edges to display from the graphical editor's workbench.
     *
     * @return The model edges to display as a Map, where the keys are Edges and the values are their associated objects.
     */
    @Override
    public Map getModelEdgesToDisplay() {
        return graphicalEditor().getWorkbench().getModelEdgesToDisplay();
    }

    /**
     * Retrieves the model nodes to display from the graphical editor's workbench.
     *
     * @return The model nodes to display.
     */
    public Map getModelNodesToDisplay() {
        return graphicalEditor().getWorkbench().getModelNodesToDisplay();
    }

    /**
     * Retrieves the knowledge from the graphical editor's workbench.
     *
     * @return The knowledge from the workbench.
     */
    public Knowledge getKnowledge() {
        return graphicalEditor().getWorkbench().getKnowledge();
    }

    /**
     * Retrieves the source graph from the graphical editor's workbench.
     *
     * @return The source graph.
     */
    public Graph getSourceGraph() {
        return graphicalEditor().getWorkbench().getSourceGraph();
    }

    /**
     * Layouts the graph based on the provided graph object.
     *
     * @param graph The graph to use for layout.
     */
    public void layoutByGraph(Graph graph) {
        SemGraph _graph = (SemGraph) graphicalEditor().getWorkbench().getGraph();
        _graph.setShowErrorTerms(false);
        graphicalEditor().getWorkbench().layoutByGraph(graph);
        _graph.resetErrorPositions();
//        graphicalEditor().getWorkbench().setGraph(_graph);
        this.errorTerms.setText("Show Error Terms");
    }

    /**
     * Layout the graph based on knowledge.
     */
    public void layoutByKnowledge() {
        SemGraph _graph = (SemGraph) graphicalEditor().getWorkbench().getGraph();
        _graph.setShowErrorTerms(false);
        graphicalEditor().getWorkbench().layoutByKnowledge();
        _graph.resetErrorPositions();
//        graphicalEditor().getWorkbench().setGraph(_graph);
        this.errorTerms.setText("Show Error Terms");
    }

    //========================PRIVATE METHODS===========================//
    private GeneralizedSemImGraphicalEditor graphicalEditor() {
        this.graphicalEditor = new GeneralizedSemImGraphicalEditor(getEstIm(), this.launchedEditors);
        this.graphicalEditor.enableEditing(false);

        return this.graphicalEditor;
    }

    private GeneralizedSemImListEditor listEditor() {
        /*
      The graphical editor for the SemIm.
         */
        return new GeneralizedSemImListEditor(getEstIm(), this.launchedEditors);
    }

    private JPanel estimationReport() {
        JPanel p = new JPanel();
        p.setLayout(new BorderLayout());

        /*

         */
        JTextArea report = new JTextArea(this.wrapper.getReport());
        p.add(report, BorderLayout.CENTER);

        return p;
    }

    private GeneralizedSemIm getEstIm() {
        return this.wrapper.getSemIm();
    }
}
