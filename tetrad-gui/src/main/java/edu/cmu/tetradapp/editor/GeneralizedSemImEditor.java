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
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetradapp.model.GeneralizedSemEstimatorWrapper;
import edu.cmu.tetradapp.model.GeneralizedSemImWrapper;
import edu.cmu.tetradapp.session.DelegatesEditing;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.workbench.LayoutMenu;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * A JPanel class that represents an editor for GeneralizedSemIm objects. It implements the DelegatesEditing and
 * LayoutEditable interfaces.
 */
public final class GeneralizedSemImEditor extends JPanel implements DelegatesEditing,
        LayoutEditable {

    /**
     * The SemPm being edited.
     */
    private final GeneralizedSemIm semIm;
    /**
     * A common map of nodes to launched editors so that they can all be closed when this editor is closed.
     */
    private final Map<Object, EditorWindow> launchedEditors = new HashMap<>();
    /**
     * The graphical editor for the SemIm.
     */
    private final GeneralizedSemImGraphicalEditor graphicalEditor;
    /**
     * The graphical editor for the SemIm.
     */
    private final GeneralizedSemImListEditor listEditor;
    /**
     * A reference to the error terms menu item so it can be reset.
     */
    private JMenuItem errorTerms;

    /**
     * Constructs a GeneralizedSemImEditor with the specified GeneralizedSemEstimatorWrapper.
     *
     * @param wrapper the GeneralizedSemEstimatorWrapper to initialize the editor with
     * @throws NullPointerException if the Generalized SEM IM is null
     */
    public GeneralizedSemImEditor(GeneralizedSemEstimatorWrapper wrapper) {
        GeneralizedSemIm semIm = wrapper.getSemIm();

        if (semIm == null) {
            throw new NullPointerException("Generalized SEM IM must not be null.");
        }

        this.semIm = semIm;
        setLayout(new BorderLayout());

        this.graphicalEditor = new GeneralizedSemImGraphicalEditor(getSemIm(), this.launchedEditors);
        this.graphicalEditor.enableEditing(false);

        this.listEditor = new GeneralizedSemImListEditor(getSemIm(), this.launchedEditors);

        initializeTabbedPane();
        initializeErrorTermsMenuBar(wrapper);
        initializeAncetorListener();
    }

    /**
     * Constructs a GeneralizedSemImEditor with the specified GeneralizedSemImWrapper.
     *
     * @param wrapper the GeneralizedSemImWrapper to initialize the editor with
     * @throws IllegalArgumentException if the wrapper contains more than one Generalized SEM IM
     * @throws NullPointerException     if the Generalized SEM IM is null
     */
    public GeneralizedSemImEditor(GeneralizedSemImWrapper wrapper) {
        if (wrapper.getSemIms() == null || wrapper.getSemIms().size() > 1) {
            throw new IllegalArgumentException("I'm sorry; this editor can only edit a single generalized SEM IM.");
        }
        GeneralizedSemIm semIm = wrapper.getSemIms().get(0);

        if (semIm == null) {
            throw new NullPointerException("Generalized SEM IM must not be null.");
        }

        this.semIm = semIm;

        this.graphicalEditor = new GeneralizedSemImGraphicalEditor(getSemIm(), this.launchedEditors);
        this.graphicalEditor.enableEditing(false);

        this.listEditor = new GeneralizedSemImListEditor(getSemIm(), this.launchedEditors);

        this.setLayout(new BorderLayout());
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("Variables", this.listEditor());
        tabbedPane.add("Graph", this.graphicalEditor());
        this.add(tabbedPane, BorderLayout.CENTER);
        JMenuBar menuBar = initializeMenuBar(graphicalEditor);
        SemGraph graph = (SemGraph) graphicalEditor.getWorkbench().getGraph();
        boolean shown = wrapper.isShowErrors();
        graph.setShowErrorTerms(shown);
        initializeErrorTermsMenu(wrapper, shown);
        initializeToolsMenu(this.errorTerms, menuBar);
        initializeMenuBar(menuBar);
        initializeAncetorListener();
    }

    /**
     * When the dialog closes, we want to close all generalized expression editors. We do this by detecting when the
     * ancestor of this editor has been removed.
     */
    private void initializeAncetorListener() {
        this.addAncestorListener(new AncestorListener() {
            public void ancestorAdded(AncestorEvent ancestorEvent) {
            }

            public void ancestorRemoved(AncestorEvent ancestorEvent) {
                System.out.println("Ancestor removed: " + ancestorEvent.getAncestor());

                for (Object o : launchedEditors.keySet()) {
                    EditorWindow window = launchedEditors.get(o);
                    window.closeDialog();
                }
            }

            public void ancestorMoved(AncestorEvent ancestorEvent) {
            }
        });
    }

    private void initializeTabbedPane() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("Variables", listEditor());
        tabbedPane.add("Graph", graphicalEditor());
        add(tabbedPane, BorderLayout.CENTER);
    }

    private void initializeErrorTermsMenuBar(GeneralizedSemEstimatorWrapper wrapper) {
        JMenuBar menuBar = initializeMenuBar(this.graphicalEditor);
        SemGraph graph = (SemGraph) this.graphicalEditor.getWorkbench().getGraph();
        boolean shown = wrapper.isShowErrors();
        graph.setShowErrorTerms(shown);
        initializeErrorTermsMenu(wrapper, shown);
        initializeToolsMenu(this.errorTerms, menuBar);
        menuBar.add(new LayoutMenu(this));
        this.add(menuBar, BorderLayout.NORTH);
    }

    /**
     * Initializes the Tools menu in the menu bar.
     *
     * @param errorTerms the error terms menu item
     * @param menuBar    the menu bar to add the Tools menu to
     */
    private void initializeToolsMenu(JMenuItem errorTerms, JMenuBar menuBar) {
        JMenuItem lengthCutoff = initializeLengthCutoffMenu(GeneralizedSemImEditor.this.graphicalEditor);
        JMenu tools = new JMenu("Tools");
        tools.add(errorTerms);
        tools.add(lengthCutoff);
        menuBar.add(tools);
    }

    /**
     * Initializes the length cutoff menu item for the graphical editor.
     *
     * @param graphicalEditor the GeneralizedSemImGraphicalEditor object
     * @return the initialized JMenuItem
     */
    @NotNull
    private JMenuItem initializeLengthCutoffMenu(GeneralizedSemImGraphicalEditor graphicalEditor) {
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

            Box b = Box.createVerticalBox();

            Box b1 = Box.createHorizontalBox();
            b1.add(new JLabel("Formulas longer than "));
            b1.add(lengthField);
            b1.add(new JLabel(" will be replaced in the graph by \"--long formula--\"."));
            b.add(b1);

            b.setBorder(new EmptyBorder(5, 5, 5, 5));

            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());
            panel.add(b, BorderLayout.CENTER);

            EditorWindow editorWindow = new EditorWindow(panel, "Apply Templates", "OK",
                    false, GeneralizedSemImEditor.this);

            editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
                public void internalFrameClosing(InternalFrameEvent event) {
                    graphicalEditor.refreshLabels();
                }
            });

            DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
            editorWindow.pack();
            editorWindow.setVisible(true);
        });
        return lengthCutoff;
    }

    /**
     * Initializes the error terms menu item.
     *
     * @param wrapper The GeneralizedSemEstimatorWrapper object.
     * @param shown   A boolean indicating whether the error terms are shown or hidden.
     */
    private void initializeErrorTermsMenu(GeneralizedSemEstimatorWrapper wrapper, boolean shown) {
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
                SemGraph graph1 = (SemGraph) GeneralizedSemImEditor.this.graphicalEditor.getWorkbench().getGraph();
                graph1.setShowErrorTerms(false);
                wrapper.setShowErrors(false);
                graphicalEditor().refreshLabels();
            } else if ("Show Error Terms".equals(menuItem.getText())) {
                menuItem.setText("Hide Error Terms");
                SemGraph graph1 = (SemGraph) GeneralizedSemImEditor.this.graphicalEditor.getWorkbench().getGraph();
                graph1.setShowErrorTerms(true);
                wrapper.setShowErrors(true);
                graphicalEditor().refreshLabels();
            }
        });
    }

    /**
     * Initializes and returns the menu bar for the graphical editor.
     *
     * @param graphicalEditor the graphical editor object to initialize the menu bar with
     * @return the initialized menu bar
     */
    @NotNull
    private JMenuBar initializeMenuBar(GeneralizedSemImGraphicalEditor graphicalEditor) {
        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        menuBar.add(file);
        file.add(new SaveComponentImage(graphicalEditor.getWorkbench(),
                "Save Graph Image..."));
        return menuBar;
    }

    private void initializeMenuBar(JMenuBar menuBar) {
        menuBar.add(new LayoutMenu(this));
        add(menuBar, BorderLayout.NORTH);
    }

    /**
     * Initializes the error terms menu item.
     *
     * @param wrapper the GeneralizedSemImWrapper object
     * @param shown   a boolean indicating whether the error terms are shown or hidden
     */
    private void initializeErrorTermsMenu(GeneralizedSemImWrapper wrapper, boolean shown) {
        errorTerms = new JMenuItem();

        if (shown) {
            errorTerms.setText("Hide Error Terms");
        } else {
            errorTerms.setText("Show Error Terms");
        }

        errorTerms.addActionListener(e -> {
            JMenuItem menuItem = (JMenuItem) e.getSource();

            if ("Hide Error Terms".equals(menuItem.getText())) {
                menuItem.setText("Show Error Terms");
                SemGraph graph1 = (SemGraph) graphicalEditor.getWorkbench().getGraph();
                graph1.setShowErrorTerms(false);
                wrapper.setShowErrors(false);
                GeneralizedSemImEditor.this.graphicalEditor().refreshLabels();
            } else if ("Show Error Terms".equals(menuItem.getText())) {
                menuItem.setText("Hide Error Terms");
                SemGraph graph1 = (SemGraph) graphicalEditor.getWorkbench().getGraph();
                graph1.setShowErrorTerms(true);
                wrapper.setShowErrors(true);
                GeneralizedSemImEditor.this.graphicalEditor().refreshLabels();
            }
        });
    }

    /**
     * Returns the editing delegate component.
     *
     * @return the editing delegate component
     */
    public JComponent getEditDelegate() {
        return graphicalEditor();
    }

    /**
     * Retrieves the graph from the graphical editor in the GeneralizedSemImEditor.
     *
     * @return the graph
     */
    public Graph getGraph() {
        return graphicalEditor().getWorkbench().getGraph();
    }

    /**
     * Retrieves the model edges to display from the workbench of the graphical editor associated with the current
     * instance.
     *
     * @return the model edges to display as a map of edges and their display settings
     */
    @Override
    public Map<Edge, Object> getModelEdgesToDisplay() {
        return graphicalEditor().getWorkbench().getModelEdgesToDisplay();
    }

    /**
     * Retrieves the model nodes to display from the graphical editor's workbench.
     *
     * @return the model nodes to display as a map of nodes and their display settings
     */
    public Map<Node, Object> getModelNodesToDisplay() {
        return graphicalEditor().getWorkbench().getModelNodesToDisplay();
    }

    /**
     * Retrieves the Knowledge object from the graphical editor's workbench.
     *
     * @return the Knowledge object
     */
    public Knowledge getKnowledge() {
        return graphicalEditor().getWorkbench().getKnowledge();
    }

    /**
     * Returns the source graph associated with the graphical editor in the GeneralizedSemImEditor.
     *
     * @return the source graph
     */
    public Graph getSourceGraph() {
        return graphicalEditor().getWorkbench().getSourceGraph();
    }

    /**
     * Sets the layout of the graph in the graphical editor based on the given graph.
     *
     * @param graph a {@link Graph} object
     */
    public void layoutByGraph(Graph graph) {
        SemGraph _graph = (SemGraph) graphicalEditor().getWorkbench().getGraph();
        _graph.setShowErrorTerms(false);
        graphicalEditor().getWorkbench().layoutByGraph(graph);
        _graph.resetErrorPositions();
        this.errorTerms.setText("Show Error Terms");
    }

    /**
     * Sets the layout of the graph in the graphical editor based on the knowledge. It sets the 'showErrorTerms'
     * property of the graph to false. It then calls the 'layoutByKnowledge' method of the workbench in the graphical
     * editor. After that, it resets the error positions of the graph. Finally, it sets the text of the errorTerms field
     * to "Show Error Terms".
     */
    public void layoutByKnowledge() {
        SemGraph _graph = (SemGraph) graphicalEditor().getWorkbench().getGraph();
        _graph.setShowErrorTerms(false);
        graphicalEditor().getWorkbench().layoutByKnowledge();
        _graph.resetErrorPositions();
        this.errorTerms.setText("Show Error Terms");
    }

    /**
     * Returns the graphical editor associated with the current instance.
     *
     * @return the graphical editor
     */
    private GeneralizedSemImGraphicalEditor graphicalEditor() {
        return this.graphicalEditor;
    }

    /**
     * Returns the list editor for editing the parameters of the SemIm using a graph workbench.
     *
     * @return the list editor for editing the parameters of the SemIm
     */
    private GeneralizedSemImListEditor listEditor() {
        return this.listEditor;
    }

    /**
     * Returns the GeneralizedSemIm object associated with the current instance.
     *
     * @return the GeneralizedSemIm object
     */
    private GeneralizedSemIm getSemIm() {
        return this.semIm;
    }
}
