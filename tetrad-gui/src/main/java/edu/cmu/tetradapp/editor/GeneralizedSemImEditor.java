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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.session.DelegatesEditing;
import edu.cmu.tetradapp.model.GeneralizedSemEstimatorWrapper;
import edu.cmu.tetradapp.model.GeneralizedSemImWrapper;
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Edits a SEM PM model.
 *
 * @author Donald Crimbchin
 * @author Joseph Ramsey
 */
public final class GeneralizedSemImEditor extends JPanel implements DelegatesEditing,
        LayoutEditable {

    /**
     * The SemPm being edited.
     */
    private final GeneralizedSemIm semIm;

    /**
     * The graphical editor for the SemIm.
     */
    private GeneralizedSemImGraphicalEditor graphicalEditor;

    /**
     * The graphical editor for the SemIm.
     */
    private GeneralizedSemImListEditor listEditor;

    /**
     * A reference to the error terms menu item so it can be reset.
     */
    private final JMenuItem errorTerms;

    /**
     * Edits the initial distributions of the parameters.
     */
    private GeneralizedSemImParamsEditor paramsEditor;

    /**
     * A common map of nodes to launched editors so that they can all be closed
     * when this editor is closed.
     */
    private final Map<Object, EditorWindow> launchedEditors = new HashMap<>();

    //========================CONSTRUCTORS===========================//
    public GeneralizedSemImEditor(final GeneralizedSemEstimatorWrapper wrapper) {
        final GeneralizedSemIm semIm = wrapper.getSemIm();

        if (semIm == null) {
            throw new NullPointerException("Generalized SEM IM must not be null.");
        }

        this.semIm = semIm;
        setLayout(new BorderLayout());

        final JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("Variables", listEditor());
        tabbedPane.add("Graph", graphicalEditor());

        add(tabbedPane, BorderLayout.CENTER);

        final JMenuBar menuBar = new JMenuBar();
        final JMenu file = new JMenu("File");
        menuBar.add(file);
        file.add(new SaveComponentImage(this.graphicalEditor.getWorkbench(),
                "Save Graph Image..."));

        final SemGraph graph = (SemGraph) this.graphicalEditor.getWorkbench().getGraph();
        final boolean shown = wrapper.isShowErrors();
        graph.setShowErrorTerms(shown);

        this.errorTerms = new JMenuItem();

        if (shown) {
            this.errorTerms.setText("Hide Error Terms");
        } else {
            this.errorTerms.setText("Show Error Terms");
        }

        this.errorTerms.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final JMenuItem menuItem = (JMenuItem) e.getSource();

                if ("Hide Error Terms".equals(menuItem.getText())) {
                    menuItem.setText("Show Error Terms");
                    final SemGraph graph = (SemGraph) GeneralizedSemImEditor.this.graphicalEditor.getWorkbench().getGraph();
                    graph.setShowErrorTerms(false);
                    wrapper.setShowErrors(false);
                    graphicalEditor().refreshLabels();
                } else if ("Show Error Terms".equals(menuItem.getText())) {
                    menuItem.setText("Hide Error Terms");
                    final SemGraph graph = (SemGraph) GeneralizedSemImEditor.this.graphicalEditor.getWorkbench().getGraph();
                    graph.setShowErrorTerms(true);
                    wrapper.setShowErrors(true);
                    graphicalEditor().refreshLabels();
                }
            }
        });

        final JMenuItem lengthCutoff = new JMenuItem("Formula Cutoff");

        lengthCutoff.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent event) {
                final int length = Preferences.userRoot().getInt("maxExpressionLength", 25);

                final IntTextField lengthField = new IntTextField(length, 4);
                lengthField.setFilter(new IntTextField.Filter() {
                    public int filter(int value, int oldValue) {
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
                        = new EditorWindow(panel, "Apply Templates", "OK", false, GeneralizedSemImEditor.this);

                editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
                    public void internalFrameClosing(InternalFrameEvent event) {
                        graphicalEditor.refreshLabels();
                    }
                });

                DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
                editorWindow.pack();
                editorWindow.setVisible(true);
            }
        });

        JMenu tools = new JMenu("Tools");
        tools.add(errorTerms);
        tools.add(lengthCutoff);
        menuBar.add(tools);

        menuBar.add(new LayoutMenu(this));

        this.add(menuBar, BorderLayout.NORTH);

        // When the dialog closes, we want to close all generalized expression editors. We do this by
        // detecting when the ancestor of this editor has been removed.
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

    public GeneralizedSemImEditor(GeneralizedSemImWrapper wrapper) {
        if (wrapper.getSemIms() == null || wrapper.getSemIms().size() > 1) {
            throw new IllegalArgumentException("I'm sorry; this editor can only edit a single generalized SEM IM.");
        }
        GeneralizedSemIm semIm = wrapper.getSemIms().get(0);

        if (semIm == null) {
            throw new NullPointerException("Generalized SEM IM must not be null.");
        }

        this.semIm = semIm;
        this.setLayout(new BorderLayout());

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("Variables", this.listEditor());
        tabbedPane.add("Graph", this.graphicalEditor());

        this.add(tabbedPane, BorderLayout.CENTER);

        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        menuBar.add(file);
        file.add(new SaveComponentImage(graphicalEditor.getWorkbench(),
                "Save Graph Image..."));

        SemGraph graph = (SemGraph) graphicalEditor.getWorkbench().getGraph();
        boolean shown = wrapper.isShowErrors();
        graph.setShowErrorTerms(shown);

        errorTerms = new JMenuItem();

        if (shown) {
            errorTerms.setText("Hide Error Terms");
        } else {
            errorTerms.setText("Show Error Terms");
        }

        errorTerms.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JMenuItem menuItem = (JMenuItem) e.getSource();

                if ("Hide Error Terms".equals(menuItem.getText())) {
                    menuItem.setText("Show Error Terms");
                    SemGraph graph = (SemGraph) graphicalEditor.getWorkbench().getGraph();
                    graph.setShowErrorTerms(false);
                    wrapper.setShowErrors(false);
                    GeneralizedSemImEditor.this.graphicalEditor().refreshLabels();
                } else if ("Show Error Terms".equals(menuItem.getText())) {
                    menuItem.setText("Hide Error Terms");
                    SemGraph graph = (SemGraph) graphicalEditor.getWorkbench().getGraph();
                    graph.setShowErrorTerms(true);
                    wrapper.setShowErrors(true);
                    GeneralizedSemImEditor.this.graphicalEditor().refreshLabels();
                }
            }
        });

        JMenuItem lengthCutoff = new JMenuItem("Formula Cutoff");

        lengthCutoff.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                int length = Preferences.userRoot().getInt("maxExpressionLength", 25);

                IntTextField lengthField = new IntTextField(length, 4);
                lengthField.setFilter(new IntTextField.Filter() {
                    public int filter(final int value, final int oldValue) {
                        try {
                            if (value > 0) {
                                Preferences.userRoot().putInt("maxExpressionLength", value);
                                return value;
                            } else {
                                return 0;
                            }
                        } catch (final Exception e) {
                            return oldValue;
                        }
                    }
                });

                final Box b = Box.createVerticalBox();

                final Box b1 = Box.createHorizontalBox();
                b1.add(new JLabel("Formulas longer than "));
                b1.add(lengthField);
                b1.add(new JLabel(" will be replaced in the graph by \"--long formula--\"."));
                b.add(b1);

                b.setBorder(new EmptyBorder(5, 5, 5, 5));

                final JPanel panel = new JPanel();
                panel.setLayout(new BorderLayout());
                panel.add(b, BorderLayout.CENTER);

                final EditorWindow editorWindow
                        = new EditorWindow(panel, "Apply Templates", "OK", false, GeneralizedSemImEditor.this);

                editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
                    public void internalFrameClosing(final InternalFrameEvent event) {
                        GeneralizedSemImEditor.this.graphicalEditor.refreshLabels();
                    }
                });

                DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
                editorWindow.pack();
                editorWindow.setVisible(true);
            }
        });

        final JMenu tools = new JMenu("Tools");
        tools.add(this.errorTerms);
        tools.add(lengthCutoff);
        menuBar.add(tools);

        menuBar.add(new LayoutMenu(this));

        add(menuBar, BorderLayout.NORTH);

        // When the dialog closes, we want to close all generalized expression editors. We do this by
        // detecting when the ancestor of this editor has been removed.
        addAncestorListener(new AncestorListener() {
            public void ancestorAdded(final AncestorEvent ancestorEvent) {
            }

            public void ancestorRemoved(final AncestorEvent ancestorEvent) {
                System.out.println("Ancestor removed: " + ancestorEvent.getAncestor());

                for (final Object o : GeneralizedSemImEditor.this.launchedEditors.keySet()) {
                    final EditorWindow window = GeneralizedSemImEditor.this.launchedEditors.get(o);
                    window.closeDialog();
                }
            }

            public void ancestorMoved(final AncestorEvent ancestorEvent) {
            }
        });
    }

    private SemGraph getSemGraph() {
        return getSemPm().getGraph();
    }

    public JComponent getEditDelegate() {
        return graphicalEditor();
    }

    public Graph getGraph() {
        return graphicalEditor().getWorkbench().getGraph();
    }

    @Override
    public Map getModelEdgesToDisplay() {
        return graphicalEditor().getWorkbench().getModelEdgesToDisplay();
    }

    public Map getModelNodesToDisplay() {
        return graphicalEditor().getWorkbench().getModelNodesToDisplay();
    }

    public IKnowledge getKnowledge() {
        return graphicalEditor().getWorkbench().getKnowledge();
    }

    public Graph getSourceGraph() {
        return graphicalEditor().getWorkbench().getSourceGraph();
    }

    public void layoutByGraph(final Graph graph) {
        final SemGraph _graph = (SemGraph) graphicalEditor().getWorkbench().getGraph();
        _graph.setShowErrorTerms(false);
        graphicalEditor().getWorkbench().layoutByGraph(graph);
        _graph.resetErrorPositions();
//        graphicalEditor().getWorkbench().setGraph(_graph);
        this.errorTerms.setText("Show Error Terms");
    }

    public void layoutByKnowledge() {
        final SemGraph _graph = (SemGraph) graphicalEditor().getWorkbench().getGraph();
        _graph.setShowErrorTerms(false);
        graphicalEditor().getWorkbench().layoutByKnowledge();
        _graph.resetErrorPositions();
//        graphicalEditor().getWorkbench().setGraph(_graph);
        this.errorTerms.setText("Show Error Terms");
    }

    //========================PRIVATE METHODS===========================//
    private GeneralizedSemPm getSemPm() {
        return this.semIm.getSemPm();
    }

    private GeneralizedSemImGraphicalEditor graphicalEditor() {
        if (this.graphicalEditor == null) {
            this.graphicalEditor = new GeneralizedSemImGraphicalEditor(getSemIm(), this.launchedEditors);
            this.graphicalEditor.enableEditing(false);
        }
        return this.graphicalEditor;
    }

    private GeneralizedSemImListEditor listEditor() {
        if (this.listEditor == null) {
            this.listEditor = new GeneralizedSemImListEditor(getSemIm(), this.launchedEditors);
        }
        return this.listEditor;
    }

    private GeneralizedSemImParamsEditor parametersEditor() {
        if (this.paramsEditor == null) {
            this.paramsEditor = new GeneralizedSemImParamsEditor(getSemIm(), this.launchedEditors);
        }
        return this.paramsEditor;
    }

    private GeneralizedSemIm getSemIm() {
        return this.semIm;
    }

}
