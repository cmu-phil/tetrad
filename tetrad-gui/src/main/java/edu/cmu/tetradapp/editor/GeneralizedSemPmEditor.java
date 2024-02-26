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
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetradapp.model.GeneralizedSemPmWrapper;
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
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.prefs.Preferences;


/**
 * The GeneralizedSemPmEditor class provides a graphical user interface for editing and manipulating a Generalized SEM
 * PM model. It extends the JPanel class and implements the DelegatesEditing and LayoutEditable interfaces.
 *
 * @see JPanel
 * @see DelegatesEditing
 * @see LayoutEditable
 */
public final class GeneralizedSemPmEditor extends JPanel implements DelegatesEditing,
        LayoutEditable {

    /**
     * The SemPm being edited.
     */
    private final GeneralizedSemPm semPm;
    /**
     * A common map of nodes to launched editors so that they can all be closed when this editor is closed.
     */
    private final Map<Object, EditorWindow> launchedEditors = new HashMap<>();
    /**
     * A reference to the error terms menu item, so it can be reset.
     */
    private JMenuItem errorTerms;
    /**
     * The graphical editor for the SemPm.
     */
    private GeneralizedSemPmGraphicalEditor graphicalEditor;
    /**
     * The graphical editor for the SemPm.
     */
    private GeneralizedSemPmListEditor listEditor;
    /**
     * The editor for initial value distributions.
     */
    private GeneralizedSemPmParamsEditor parameterEditor;

    /**
     * Constructor for GeneralizedSemPmEditor class.
     *
     * @param wrapper The wrapper object containing the GeneralizedSemPm instance.
     * @throws NullPointerException if the provided Generalized SEM PM is null.
     */
    public GeneralizedSemPmEditor(GeneralizedSemPmWrapper wrapper) {
        GeneralizedSemPm semPm = wrapper.getSemPm();
        this.semPm = Objects.requireNonNull(semPm, "Generalized SEM PM must not be null.");
        setLayout(new BorderLayout());
        initializeEditors();
        addTabbedPane();
        initializeMenuBar(wrapper);
        ancestorListenerInitializer();
    }

    /**
     * Initializes the editors for the GeneralizedSemPmEditor class.
     */
    private void initializeEditors() {
        graphicalEditorInitializer();
        parameterEditorInitializer();
        listEditorInitializer();
    }

    /**
     * Initializes the menu bar for the application.
     *
     * @param wrapper The GeneralizedSemPmWrapper object.
     */
    private void initializeMenuBar(GeneralizedSemPmWrapper wrapper) {
        JMenuBar menuBar = new JMenuBar();
        fileMenuInitializer(menuBar);
        boolean shown = semGraphInitializer(wrapper);
        errorTermsMenuItemsInitializer(shown);
        errorTermsInitializer(wrapper);
        JMenuItem templateMenu = templateMenuInitializer();
        JMenuItem lengthCutoff = lengthMenuInitializer();
        paramsMenuInitializer(templateMenu, lengthCutoff, menuBar);
        menuBarInitializer(menuBar);
    }

    /**
     * By default, hide the error terms.
     */
    private boolean semGraphInitializer(GeneralizedSemPmWrapper wrapper) {
        SemGraph graph = (SemGraph) this.graphicalEditor.getWorkbench().getGraph();
        boolean shown = wrapper.isShowErrors();
        graph.setShowErrorTerms(shown);
        return shown;
    }

    /**
     * Initializes the "File" menu in the given menu bar and adds a "Save Graph Image..." option.
     *
     * @param menuBar The menu bar to add the "File" menu to.
     */
    private void fileMenuInitializer(JMenuBar menuBar) {
        JMenu file = new JMenu("File");
        menuBar.add(file);

        file.add(new SaveComponentImage(this.graphicalEditor.getWorkbench(),
                "Save Graph Image..."));
    }

    /**
     * Initializes the error terms menu items based on the value of the shown parameter.
     *
     * @param shown The boolean value indicating if the error terms should be shown or hidden.
     */
    private void errorTermsMenuItemsInitializer(boolean shown) {
        this.errorTerms = new JMenuItem();

        if (shown) {
            this.errorTerms.setText("Hide Error Terms");
        } else {
            this.errorTerms.setText("Show Error Terms");
        }
    }

    /**
     * Initializes the menu bar for the application.
     *
     * @param menuBar the menu bar to be initialized
     */
    private void menuBarInitializer(JMenuBar menuBar) {
        menuBar.add(new LayoutMenu(this));
        add(menuBar, BorderLayout.NORTH);
    }

    /**
     * Initializes and returns the length menu item. The length menu item represents the option for setting the formula
     * cutoff length.
     *
     * @return The length menu item.
     */
    @NotNull
    private JMenuItem lengthMenuInitializer() {
        JMenuItem lengthCutoff = new JMenuItem("Formula Cutoff");

        lengthCutoff.addActionListener(event -> lengthCutoffAction());
        return lengthCutoff;
    }

    /**
     * Initializes the "Params" menu in the given menu bar and adds the templateMenu, lengthCutoff, and errorTerms menu
     * items.
     *
     * @param templateMenu The JMenuItem representing the template menu item.
     * @param lengthCutoff The JMenuItem representing the length cutoff menu item.
     * @param menuBar      The JMenuBar to add the "Params" menu to.
     */
    private void paramsMenuInitializer(JMenuItem templateMenu, JMenuItem lengthCutoff, JMenuBar menuBar) {
        JMenu params = new JMenu("Tools");
        params.add(this.errorTerms);
        params.add(templateMenu);
        params.add(lengthCutoff);
        menuBar.add(params);
    }

    /**
     * When the dialog closes, we want to close all generalized expression editors. We do this by detecting when the
     * ancestor of this editor has been removed.
     */
    private void ancestorListenerInitializer() {
        addAncestorListener(new AncestorListener() {
            public void ancestorAdded(AncestorEvent ancestorEvent) {
            }

            public void ancestorRemoved(AncestorEvent ancestorEvent) {
                for (Object o : GeneralizedSemPmEditor.this.launchedEditors.keySet()) {
                    EditorWindow window = GeneralizedSemPmEditor.this.launchedEditors.get(o);
                    window.closeDialog();
                }
            }

            public void ancestorMoved(AncestorEvent ancestorEvent) {
            }
        });
    }

    /**
     * Initializes and returns the template menu item.
     *
     * @return The template menu item.
     */
    @NotNull
    private JMenuItem templateMenuInitializer() {
        JMenuItem templateMenu = new JMenuItem("Apply Templates...");

        templateMenu.addActionListener(actionEvent -> {
            GeneralizedTemplateEditor editor = new GeneralizedTemplateEditor(getSemPm());

            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());
            panel.add(editor, BorderLayout.CENTER);

            EditorWindow editorWindow
                    = new EditorWindow(panel, "Apply Templates", "OK", false, GeneralizedSemPmEditor.this);

            DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
            editorWindow.pack();
            editorWindow.setVisible(true);

            editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
                public void internalFrameClosing(InternalFrameEvent internalFrameEvent) {
                    if (!editorWindow.isCanceled()) {
                        editorWindowCanceledAction(editor);
                    }
                }

                private void editorWindowCanceledAction(GeneralizedTemplateEditor editor) {
                    GeneralizedSemPm _semPm = editor.getSemPm();
                    GeneralizedSemPm semPm1 = GeneralizedSemPmEditor.this.semPm;

                    for (Node node : _semPm.getNodes()) {
                        try {
                            semPm1.setNodeExpression(node, _semPm.getNodeExpressionString(node));
                        } catch (ParseException e) {
                            JOptionPane.showMessageDialog(GeneralizedSemPmEditor.this,
                                    "Could not set the expression for " + node + " to "
                                            + _semPm.getNodeExpressionString(node));
                        }
                    }

                    for (String startsWith : _semPm.startsWithPrefixes()) {
                        try {
                            semPm1.setStartsWithParametersTemplate(startsWith, _semPm.getStartsWithParameterTemplate(startsWith));
                        } catch (ParseException e) {
                            JOptionPane.showMessageDialog(GeneralizedSemPmEditor.this,
                                    "Could not set the expression for " + startsWith + " to "
                                            + _semPm.getParameterExpressionString(_semPm.getStartsWithParameterTemplate(startsWith)));
                        }
                    }

                    for (String parameter : _semPm.getParameters()) {
                        try {
                            boolean found = false;

                            for (String startsWith : _semPm.startsWithPrefixes()) {
                                if (parameter.startsWith(startsWith)) {
                                    semPm1.setParameterExpression(parameter, _semPm.getStartsWithParameterTemplate(startsWith));
                                    found = true;
                                    break;
                                }
                            }

                            if (!found) {
                                semPm1.setParameterExpression(parameter, _semPm.getParameterExpressionString(parameter));
                            }
                        } catch (ParseException e) {
                            JOptionPane.showMessageDialog(GeneralizedSemPmEditor.this,
                                    "Could not set the expression for " + parameter + " to "
                                            + _semPm.getParameterExpressionString(parameter));
                        }
                    }

                    try {
                        semPm1.setVariablesTemplate(_semPm.getVariablesTemplate());
                        semPm1.setErrorsTemplate(_semPm.getErrorsTemplate());
                        semPm1.setParametersTemplate(_semPm.getParametersTemplate());
                    } catch (ParseException e) {
                        JOptionPane.showMessageDialog(GeneralizedSemPmEditor.this,
                                "Problem copying template.");
                    }

                    GeneralizedSemPmEditor.this.graphicalEditor.refreshLabels();
                    GeneralizedSemPmEditor.this.listEditor.refreshLabels();
                    GeneralizedSemPmEditor.this.parameterEditor.refreshLabels();

                    firePropertyChange("modelChanged", null, null);
                }
            });

        });
        return templateMenu;
    }

    /**
     * Initializes the error terms menu item and handles its action events.
     *
     * @param wrapper The GeneralizedSemPmWrapper object.
     */
    private void errorTermsInitializer(GeneralizedSemPmWrapper wrapper) {
        this.errorTerms.addActionListener(e -> {
            JMenuItem menuItem = (JMenuItem) e.getSource();

            if ("Hide Error Terms".equals(menuItem.getText())) {
                menuItem.setText("Show Error Terms");
                SemGraph graph1 = (SemGraph) GeneralizedSemPmEditor.this.graphicalEditor.getWorkbench().getGraph();
                graph1.setShowErrorTerms(false);
                wrapper.setShowErrors(false);
                graphicalEditor().refreshLabels();
            } else if ("Show Error Terms".equals(menuItem.getText())) {
                menuItem.setText("Hide Error Terms");
                SemGraph graph1 = (SemGraph) GeneralizedSemPmEditor.this.graphicalEditor.getWorkbench().getGraph();
                graph1.setShowErrorTerms(true);
                wrapper.setShowErrors(true);
                graphicalEditor().refreshLabels();
            }
        });
    }

    /**
     * Adds a JTabbedPane to the panel and sets up its content and event listeners.
     */
    private void addTabbedPane() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("Variables", listEditor());
        tabbedPane.add("Parameters", initialValuesEditor());
        tabbedPane.add("Graph", graphicalEditor());

        tabbedPane.addChangeListener(changeEvent -> {
            GeneralizedSemPmEditor.this.graphicalEditor.refreshLabels();
            GeneralizedSemPmEditor.this.listEditor.refreshLabels();
            GeneralizedSemPmEditor.this.parameterEditor.refreshLabels();
        });

        add(tabbedPane, BorderLayout.CENTER);
    }

    /**
     * Initializes the parameter editor for the SemPm using a graph workbench.
     */
    private void parameterEditorInitializer() {
        this.parameterEditor = new GeneralizedSemPmParamsEditor(getSemPm(), this.launchedEditors);
    }

    /**
     * Initializes the graphical editor for the GeneralizedSemPmEditor class. The graphical editor is responsible for
     * displaying and manipulating the graph. It creates a new GeneralizedSemPmGraphicalEditor instance and enables
     * editing. It also adds a property change listener to fire the "modelChanged" event when the model is changed.
     *
     * @see GeneralizedSemPmGraphicalEditor
     */
    private void graphicalEditorInitializer() {
        this.graphicalEditor = new GeneralizedSemPmGraphicalEditor(getSemPm(), this.launchedEditors);
        this.graphicalEditor.enableEditing(false);

        this.graphicalEditor.addPropertyChangeListener(event -> {
            if ("modelChanged".equals(event.getPropertyName())) {
                firePropertyChange("modelChanged", null, null);
            }
        });
    }

    /**
     * Initializes the list editor for the GeneralizedSemPmEditor class. The list editor is responsible for displaying
     * and manipulating a list of values related to the SemPm. It creates a new GeneralizedSemPmListEditor instance and
     * adds a property change listener to fire the "modelChanged" event when the model is changed.
     */
    private void listEditorInitializer() {
        this.listEditor = new GeneralizedSemPmListEditor(getSemPm(), initialValuesEditor(), this.launchedEditors);

        this.listEditor.addPropertyChangeListener(event -> {
            if ("modelChanged".equals(event.getPropertyName())) {
                firePropertyChange("modelChanged", null, null);
            }
        });
    }

    /**
     * Performs the action when the length cutoff is changed. The method retrieves the current length cutoff value from
     * preferences, creates a text field to display and edit the length cutoff, sets a filter on the text field to
     * validate the input, creates and configures a panel to display the length cutoff options, creates an editor window
     * with the panel as its content, adds a listener to refresh the labels in the graphical editor when the editor
     * window is closed, adds the editor window to the desktop controller, packs and displays the editor window.
     */
    private void lengthCutoffAction() {
        int length = Preferences.userRoot().getInt("maxExpressionLength", 25);

        IntTextField lengthField = new IntTextField(length, 4);
        lengthField.setFilter((value, oldValue) -> {
            if (value > 0) {
                Preferences.userRoot().putInt("maxExpressionLength", value);
                return value;
            } else {
                return 0;
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

        EditorWindow editorWindow
                = new EditorWindow(panel, "Apply Templates", "OK", false, GeneralizedSemPmEditor.this);

        editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
            public void internalFrameClosing(InternalFrameEvent event) {
                GeneralizedSemPmEditor.this.graphicalEditor.refreshLabels();
            }
        });

        DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
        editorWindow.pack();
        editorWindow.setVisible(true);
    }

    /**
     * Returns the editing delegate component for the getEditDelegate method.
     *
     * @return the editing delegate component
     */
    public JComponent getEditDelegate() {
        return graphicalEditor();
    }

    /**
     * Retrieves the graph from the graphical editor's workbench.
     *
     * @return The graph object.
     */
    public Graph getGraph() {
        return graphicalEditor().getWorkbench().getGraph();
    }

    /**
     * Retrieves the model edges to display from the graphical editor's workbench.
     *
     * @return A map of Edge objects to Object values representing the model edges to display.
     */
    @Override
    public Map<Edge, Object> getModelEdgesToDisplay() {
        return graphicalEditor().getWorkbench().getModelEdgesToDisplay();
    }

    /**
     * Retrieves the model nodes to display from the graphical editor's workbench.
     *
     * @return The model nodes to display as a Map, where each Node is mapped to an Object.
     */
    public Map<Node, Object> getModelNodesToDisplay() {
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
     * @return The source graph of the graphical editor's workbench.
     */
    public Graph getSourceGraph() {
        return graphicalEditor().getWorkbench().getSourceGraph();
    }

    /**
     * Layouts the graph based on the provided graph object.
     *
     * @param graph a Graph object representing the layout configuration
     */
    public void layoutByGraph(Graph graph) {
        SemGraph _graph = (SemGraph) graphicalEditor().getWorkbench().getGraph();
        _graph.setShowErrorTerms(false);
        graphicalEditor().getWorkbench().layoutByGraph(graph);
        _graph.resetErrorPositions();
        this.errorTerms.setText("Show Error Terms");
    }

    /**
     * Layout the graph by knowledge.
     */
    public void layoutByKnowledge() {
        SemGraph _graph = (SemGraph) graphicalEditor().getWorkbench().getGraph();
        _graph.setShowErrorTerms(false);
        graphicalEditor().getWorkbench().layoutByKnowledge();
        _graph.resetErrorPositions();
        this.errorTerms.setText("Show Error Terms");
    }

    /**
     * Retrieves the GeneralizedSemPm object associated with this class.
     *
     * @return The GeneralizedSemPm object.
     */
    private GeneralizedSemPm getSemPm() {
        return this.semPm;
    }

    /**
     * Returns the graphical editor for the GeneralizedSemPmEditor class.
     *
     * @return The graphical editor.
     */
    private GeneralizedSemPmGraphicalEditor graphicalEditor() {
        return this.graphicalEditor;
    }

    /**
     * Retrieves the list editor for the GeneralizedSemPmEditor class. The list editor is responsible for displaying and
     * manipulating a list of values related to the SemPm. It returns the existing listEditor object.
     *
     * @return The list editor for the GeneralizedSemPmEditor class.
     */
    private GeneralizedSemPmListEditor listEditor() {
        return this.listEditor;
    }

    /**
     * Returns the initial values editor for the GeneralizedSemPmEditor class.
     *
     * @return The initial values' editor.
     */
    private GeneralizedSemPmParamsEditor initialValuesEditor() {
        return this.parameterEditor;
    }
}
