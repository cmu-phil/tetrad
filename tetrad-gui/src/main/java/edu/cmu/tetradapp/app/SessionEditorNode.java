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

package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.session.*;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.editor.EditorWindow;
import edu.cmu.tetradapp.editor.FinalizingParameterEditor;
import edu.cmu.tetradapp.editor.ParameterEditor;
import edu.cmu.tetradapp.model.*;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.DisplayNode;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.Point;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Wraps a SessionNodeWrapper as a DisplayNode for presentation in a
 * SessionWorkbench. Connecting these nodes using SessionEdges results in
 * parents being added to appropriate SessionNodes underlying. Double clicking
 * these nodes launches corresponding model editors.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 * @see SessionEditorWorkbench
 * @see edu.cmu.tetrad.graph.Edge
 * @see edu.cmu.tetrad.session.SessionNode
 * @see edu.cmu.tetrad.session.Session
 */
public final class SessionEditorNode extends DisplayNode {

    /**
     * If an editor has been opened, this is a reference to that editor. Used to
     * close the editor if necessary.
     */
    private EditorWindow spawnedEditor;

    /**
     * Keeps track of whether the last model class for this node should be
     * remembered. (But why??)
     */
    private boolean rememberLastClass = false;

    /**
     * The simulation study (used to edit the repetition values).
     */
    private SimulationStudy simulationStudy;

    /**
     * A reference to the sessionWrapper, the model associated with this node.
     */
    private SessionWrapper sessionWrapper;


    /**
     * The configuration for this editor node.
     */
    private SessionNodeConfig config;

    //===========================CONSTRUCTORS==============================//

    /**
     * Wraps the given SessionNodeWrapper as a SessionEditorNode.
     */
    public SessionEditorNode(SessionNodeWrapper modelNode, SimulationStudy simulationStudy) {
        setModelNode(modelNode);
        TetradApplicationConfig appConfig = TetradApplicationConfig.getInstance();
        this.config = appConfig.getSessionNodeConfig(modelNode.getButtonType());
        if (this.config == null) {
            throw new NullPointerException("There is no configuration for node of type: " + modelNode.getButtonType());
        }
        if (simulationStudy == null) {
            throw new NullPointerException(
                    "Simulation study must not be null.");
        }
        SessionDisplayComp displayComp = this.config.getSessionDisplayCompInstance();
        String tooltip = this.config.getTooltipText();

        this.simulationStudy = simulationStudy;
        displayComp.setName(modelNode.getSessionName());

        if (displayComp instanceof NoteDisplayComp) {
            setDisplayComp(displayComp);
            setLayout(new BorderLayout());
            add((JComponent) getSessionDisplayComp(), BorderLayout.CENTER);
            setSelected(false);
            setToolTipText(tooltip);
            this.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        ToolTipManager toolTipManager =
                                ToolTipManager.sharedInstance();
                        toolTipManager.setInitialDelay(750);
                        getNotePopup().show(SessionEditorNode.this, e.getX(), e.getY());
                    }

                    e.consume();
                }
            });
        } else {
            setDisplayComp(displayComp);
            setLayout(new BorderLayout());
            add((JComponent) getSessionDisplayComp(), BorderLayout.CENTER);
            setSelected(false);
            createParamObjects(this);
            setToolTipText(tooltip);
            addListeners(this, modelNode);
        }
    }

    //===========================PUBLIC METHODS============================//

    public final void adjustToModel() {
        String acronym = getAcronym();

        // Set the color.
        if ("No model".equals(acronym)) {
            getSessionDisplayComp().setHasModel(false);
        } else {
            getSessionDisplayComp().setHasModel(true);
        }

        // Set the text for the model acronym.
        getSessionDisplayComp().setAcronym(acronym);

        // Make sure the node is deselected.
        setSelected(false);

        Dimension size = getSize();
        Point location = getLocation();

        int centerX = (int) location.getX() + size.width / 2;
        int centerY = (int) location.getY() + size.height / 2;

        int newX = centerX - getPreferredSize().width / 2;
        int newY = centerY - getPreferredSize().height / 2;

        setLocation(newX, newY);
        setSize(getPreferredSize());
        repaint();
    }

    /**
     * @return the acronym for the contained model class.
     */
    private String getAcronym() {
        SessionNodeWrapper modelNode = (SessionNodeWrapper) getModelNode();
        SessionNode sessionNode = modelNode.getSessionNode();
        Object model = sessionNode.getModel();
        if (model == null) {
            return "No model";
        } else {
            Class<? extends Object> modelClass = model.getClass();
            SessionNodeModelConfig modelConfig = this.config.getModelConfig(modelClass);

            if (modelConfig == null) {
                System.out.println("Tried to load model config for " + modelClass);
                return modelClass.getSimpleName();
            }

            return modelConfig.getAcronym();
        }
    }


    public void doDoubleClickAction() {
        doDoubleClickAction(null);
    }

    /**
     * Launches the editor associates with this node.
     *
     * @param sessionWrapper Needed to allow the option of deleting edges
     */
    public void doDoubleClickAction(Graph sessionWrapper) {
        this.sessionWrapper = (SessionWrapper) sessionWrapper;
        Window owner = (Window) getTopLevelAncestor();

        new WatchedProcess(owner) {
            public void watch() {
                TetradLogger.getInstance().setTetradLoggerConfig(getSessionNode().getLoggerConfig());
                launchEditorVisit();
            }
        };
    }

    private void launchEditorVisit() {
        try {

            // If there is already an editor open, don't launch another one.
            if (spawnedEditor() != null) {
                return;
            }

            boolean created = createModel(false);

            if (!created) {
                return;
            }

            boolean cloned = getSessionNode().useClonedModel();

            SessionModel model = getSessionNode().getModel();
            Class<? extends Object> modelClass = model.getClass();
            SessionNodeModelConfig modelConfig = this.config.getModelConfig(modelClass);
            JPanel editor;
            if (model instanceof SessionAppModule) {
                editor = ((SessionAppModule) model).newEditor();
            } else {
                Object[] arguments = new Object[]{model};
                editor = modelConfig.getEditorInstance(arguments);
                addEditorListener(editor);
            }

            ModificationRegistery.registerEditor(getSessionNode(), editor);

            String descrip = modelConfig.getName();
            editor.setName(getName() + " (" + descrip + ")");

            EditorWindow editorWindow =
                    new EditorWindow(editor, editor.getName(), "Save", cloned, this);

            editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
                public void internalFrameClosing(InternalFrameEvent e) {
                    if (getChildren().iterator().hasNext()) {
                        finishedEditingDialog();
                    }

                    ModificationRegistery.unregisterSessionNode(
                            getSessionNode());
                    setSpawnedEditor(null);

                    EditorWindow window = (EditorWindow) e.getSource();
                    if (window.isCanceled()) {
                        getSessionNode().restoreOriginalModel();
                    }

                    getSessionNode().forgetSavedModel();
                }
            });

            DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
            editorWindow.pack();
            editorWindow.setVisible(true);
            spawnedEditor = editorWindow;

            if (sessionWrapper != null) {
                sessionWrapper.setSessionChanged(true);
            }
        } catch (CouldNotCreateModelException e) {
            SessionUtils.showPermissibleParentsDialog(e.getModelClass(),
                    SessionEditorNode.this, true, true);
            e.printStackTrace();

        } catch (ClassCastException e) {
            // Annoying Layout error that gives no information.
            e.printStackTrace();
        } catch (Exception e) {
            Throwable cause = e;

            while (cause.getCause() != null) {
                cause = cause.getCause();
            }

            Component centeringComp = SessionEditorNode.this;
            String s = cause.getMessage();

            if (!"".equals(s)) {
                JOptionPane.showMessageDialog(centeringComp, s,
                        null, JOptionPane.WARNING_MESSAGE);
            }

            e.printStackTrace();
        }
    }

    /**
     * Sets the selection status of the node.
     *
     * @param selected the selection status of the node (true or false).
     */
    public void setSelected(boolean selected) {
//        setBorder(null);
        super.setSelected(selected);
        getSessionDisplayComp().setSelected(selected);
//        repaint();
    }

    //===========================PRIVATE METHODS===========================//


    private static void addListeners(final SessionEditorNode sessionEditorNode,
                                     SessionNodeWrapper modelNode) {
        // Add a mouse listener for popups.
        sessionEditorNode.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    ToolTipManager toolTipManager =
                            ToolTipManager.sharedInstance();
                    toolTipManager.setInitialDelay(750);
                    sessionEditorNode.getPopup().show(sessionEditorNode, e.getX(), e.getY());
                }

                e.consume();
            }
        });

        sessionEditorNode.addComponentListener(new ComponentAdapter() {
            public void componentMoved(ComponentEvent e) {
                sessionEditorNode.getSimulationStudy().getSession().setSessionChanged(true);
            }
        });

        SessionNode sessionNode = modelNode.getSessionNode();

        sessionNode.addSessionListener(new SessionAdapter() {
            public void modelCreated(SessionEvent sessionEvent) {
                sessionEditorNode.adjustToModel();

                // This code is here to allow multiple editor windows to be
                // opened at the same time--if a new model is created,
                // the getModel editor window is simply closed.  jdramsey
                // 5/18/02
                if (sessionEditorNode.spawnedEditor() != null) {
                    EditorWindow editorWindow = sessionEditorNode.spawnedEditor();
                    editorWindow.closeDialog();
                }
            }

            public void modelDestroyed(SessionEvent sessionEvent) {
                sessionEditorNode.adjustToModel();

                // This code is here to allow multiple editor windows to be
                // opened at the same time--if the getModel model is destroyed,
                // the getModel editor window is closed. jdramsey 5/18/02
                if (sessionEditorNode.spawnedEditor() != null) {
                    EditorWindow editorWindow = sessionEditorNode.spawnedEditor();
                    editorWindow.closeDialog();
                }
            }

            public void modelUnclear(SessionEvent sessionEvent) {
                try {
                    boolean created = sessionEditorNode.createModel(false);

                    if (!created) {
                        return;
                    }

                    sessionEditorNode.adjustToModel();
                } catch (Exception e) {
                    String message = e.getMessage();

                    if (message == null || message.length() == 0) {
                        message = "Could not make a model for this box.";
                    }

                    JOptionPane.showMessageDialog(sessionEditorNode, message);
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Adds a property change listener that listends for "changeNodeLabel" events.
     */
    private void addEditorListener(JPanel editor) {
        editor.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("changeNodeLabel".equals(evt.getPropertyName())) {
                    getDisplayComp().setName((String) evt.getNewValue());
                    SessionNodeWrapper wrapper =
                            (SessionNodeWrapper) getModelNode();
                    wrapper.setSessionName((String) evt.getNewValue());
                    adjustToModel();
                }
            }
        });
    }


    /**
     * Creates a popup for a note node.
     *
     * @return - popup
     */
    private JPopupMenu getNotePopup() {
        JPopupMenu popup = new JPopupMenu();

        JMenuItem renameBox = new JMenuItem("Rename note");
        renameBox.setToolTipText("<html>Renames this note.</html>");
        renameBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Component centeringComp = SessionEditorNode.this;
                String name =
                        JOptionPane.showInputDialog(centeringComp, "New name:");

                if (!NamingProtocol.isLegalName(name)) {
                    JOptionPane.showMessageDialog(centeringComp,
                            NamingProtocol.getProtocolDescription());
                    return;
                }

                SessionNodeWrapper wrapper =
                        (SessionNodeWrapper) getModelNode();
                wrapper.setSessionName(name);
                getSessionDisplayComp().setName(name);
                adjustToModel();
            }
        });

        JMenuItem cloneBox = new JMenuItem("Clone Note");
        cloneBox.setToolTipText("<html>" +
                "Makes a copy of this session note and its contents. To clone<br>" +
                "a whole subgraph, or to paste into a different sessions, select<br>" +
                "the subgraph and use the Copy/Paste gadgets in the Edit menu." +
                "</html>");
        cloneBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                firePropertyChange("cloneMe", null, SessionEditorNode.this);
            }
        });

        JMenuItem deleteBox = new JMenuItem("Delete Note");
        deleteBox.setToolTipText(
                "<html>Deletes this note from the workbench</html>");

        deleteBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (getSessionNode().getModel() == null) {
                    Component centeringComp = SessionEditorNode.this;
                    int ret = JOptionPane.showConfirmDialog(centeringComp,
                            "Really delete note?");

                    if (ret != JOptionPane.YES_OPTION) {
                        return;
                    }
                } else {
                    Component centeringComp = SessionEditorNode.this;
                    int ret = JOptionPane.showConfirmDialog(centeringComp,
                            "<html>" +
                                    "Really delete note? Any information it contains will<br>" +
                                    "be destroyed." + "</html>");

                    if (ret != JOptionPane.YES_OPTION) {
                        return;
                    }
                }

                firePropertyChange("deleteNode", null, null);
            }
        });

        JMenuItem help = new JMenuItem("Help");
        deleteBox.setToolTipText("<html>Shows help for this box.</html>");

        help.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                SessionNodeWrapper sessionNodeWrapper =
                        (SessionNodeWrapper) getModelNode();
                SessionNode sessionNode = sessionNodeWrapper.getSessionNode();
                showInfoBoxForModel(sessionNode, sessionNode.getModelClasses());
            }
        });

        popup.add(renameBox);
        popup.add(cloneBox);
        popup.add(deleteBox);
        popup.addSeparator();
        popup.add(help);

        return popup;
    }


    /**
     * Creates the popup for the node.
     */
    private JPopupMenu getPopup() {
        JPopupMenu popup = new JPopupMenu();

        JMenuItem createModel = new JMenuItem("Create Model");
        createModel.setToolTipText("<html>Creates a new model for this node" +
                "<br>of the type selected.</html>");

        createModel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    if (getSessionNode().getModel() == null) {
                        createModel(false);
                    } else {
                        Component centeringComp = SessionEditorNode.this;
                        JOptionPane.showMessageDialog(centeringComp,
                                "Please destroy the model model first.");
                    }
                } catch (Exception e1) {
                    Component centeringComp = SessionEditorNode.this;
                    JOptionPane.showMessageDialog(centeringComp,
                            "Could not create a model for this box.");
                    e1.printStackTrace();
                }
            }
        });

        JMenuItem editModel = new JMenuItem("Edit Model");
        editModel.setToolTipText("<html>Edits the model in this node.</html>");

        editModel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    if (getSessionNode().getModel() == null) {
                        Component centeringComp = SessionEditorNode.this;
                        JOptionPane.showMessageDialog(centeringComp,
                                "No model has been created yet.");
                    } else {
                        doDoubleClickAction();
                    }
                } catch (Exception e1) {
                    Component centeringComp = SessionEditorNode.this;
                    JOptionPane.showMessageDialog(centeringComp,
                            "Double click failed. See console for exception.");
                    e1.printStackTrace();
                }
            }
        });

        JMenuItem destroyModel = new JMenuItem("Destroy Model");
        destroyModel.setToolTipText("<html>Destroys the model for this node, " +
                "<br>if it has one, destroying any " +
                "<br>downstream models as well.</html>");

        destroyModel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Component centeringComp = SessionEditorNode.this;

                if (getSessionNode().getModel() == null) {
                    JOptionPane.showMessageDialog(centeringComp,
                            "This box does not contain a model.");
                    return;
                }

                int ret = JOptionPane.showConfirmDialog(centeringComp,
                        "Really destroy model in box? This will destroy models " +
                                "downstream as well.", "Confirm",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                if (ret != JOptionPane.YES_OPTION) {
                    return;
                }

                destroyModel();
            }
        });

        JMenuItem propagateDownstream =
                new JMenuItem("Propagate changes downstream");
        propagateDownstream.setToolTipText("<html>" +
                "Fills in this box and downstream boxes with models," +
                "<br>overwriting any models that already exist.</html>");

        propagateDownstream.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Component centeringComp = SessionEditorNode.this;

                if (getSessionNode().getModel() != null && !getSessionNode().getChildren().isEmpty()) {
                    int ret = JOptionPane.showConfirmDialog(centeringComp,
                            "You will be rewriting all downstream models. Is that OK?",
                            "Confirm",
                            JOptionPane.OK_CANCEL_OPTION,
                            JOptionPane.WARNING_MESSAGE);

                    if (ret != JOptionPane.YES_OPTION) {
                        return;
                    }

                    ret = JOptionPane.showConfirmDialog(centeringComp,
                            "Please confirm once more.",
                            "Confirm",
                            JOptionPane.OK_CANCEL_OPTION,
                            JOptionPane.WARNING_MESSAGE);

                    if (ret != JOptionPane.YES_OPTION) {
                        return;
                    }
                }

                try {
                    createDescendantModels(true);
                } catch (RuntimeException e1) {
                    JOptionPane.showMessageDialog(centeringComp,
                            "Could not complete the creation of descendant models.");
                    e1.printStackTrace();
                }
            }
        });

        JMenuItem renameBox = new JMenuItem("Rename Box");
        renameBox.setToolTipText("<html>Renames this session box.</html>");
        renameBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Component centeringComp = SessionEditorNode.this;
                String name =
                        JOptionPane.showInputDialog(centeringComp, "New name:");

                if (!NamingProtocol.isLegalName(name)) {
                    JOptionPane.showMessageDialog(centeringComp,
                            NamingProtocol.getProtocolDescription());
                    return;
                }

                SessionNodeWrapper wrapper =
                        (SessionNodeWrapper) getModelNode();
                wrapper.setSessionName(name);
                getSessionDisplayComp().setName(name);
                adjustToModel();
            }
        });

        JMenuItem cloneBox = new JMenuItem("Clone Box");
        cloneBox.setToolTipText("<html>" +
                "Makes a copy of this session box and its contents. To clone<br>" +
                "a whole subgraph, or to paste into a different sessions, select<br>" +
                "the subgraph and use the Copy/Paste gadgets in the Edit menu." +
                "</html>");
        cloneBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                firePropertyChange("cloneMe", null, SessionEditorNode.this);
            }
        });

        JMenuItem deleteBox = new JMenuItem("Delete Box");
        deleteBox.setToolTipText(
                "<html>Deletes this box from the workbench</html>");

        deleteBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (getSessionNode().getModel() == null) {
                    Component centeringComp = SessionEditorNode.this;
                    int ret = JOptionPane.showConfirmDialog(centeringComp,
                            "Really delete box?");

                    if (ret != JOptionPane.YES_OPTION) {
                        return;
                    }
                } else {
                    Component centeringComp = SessionEditorNode.this;
                    int ret = JOptionPane.showConfirmDialog(centeringComp,
                            "<html>" +
                                    "Really delete box? Any information it contains will<br>" +
                                    "be destroyed." + "</html>");

                    if (ret != JOptionPane.YES_OPTION) {
                        return;
                    }
                }

                firePropertyChange("deleteNode", null, null);
            }
        });

        JMenuItem help = new JMenuItem("Help");
        deleteBox.setToolTipText("<html>Shows help for this box.</html>");

        help.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                SessionNodeWrapper sessionNodeWrapper =
                        (SessionNodeWrapper) getModelNode();
                SessionNode sessionNode = sessionNodeWrapper.getSessionNode();
                showInfoBoxForModel(sessionNode, sessionNode.getModelClasses());
            }
        });

        JMenuItem setRepetition =
                new JMenuItem("Set Repeat...");
        setRepetition.setToolTipText(
                "<html>Sets the number of times this node " +
                        "<br>will be repeated when executing," +
                        "<br>at each depth first traversal of the" +
                        "<br>node. Useful for simulation studies.</html>");

        setRepetition.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                editRepetition();
            }
        });

        JMenuItem editSimulationParameters =
                new JMenuItem("Edit Parameters...");
        editSimulationParameters.setToolTipText("<html>");

        editSimulationParameters.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                SessionModel model = getSessionNode().getModel();
                Class modelClass;

                if (model == null) {
                    modelClass = determineTheModelClass(getSessionNode());
                } else {
                    modelClass = model.getClass();
                }

                if (!getSessionNode().existsParameterizedConstructor(
                        modelClass)) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "There is no parameterization for this model.");
                    return;
                }

                Params param = getSessionNode().getParam(modelClass);
                Object[] arguments =
                        getSessionNode().getModelConstructorArguments(
                                modelClass);

                if (param != null) {
                    try {
                        editParameters(modelClass, param, arguments);
                        int ret = JOptionPane.showConfirmDialog(JOptionUtils.centeringComp(),
                                "Create a new model with these parameters and erase\nall downstream models?",
                                "Double check...", JOptionPane.YES_NO_OPTION);
                        if (ret == JOptionPane.YES_OPTION) {
                            getSessionNode().destroyModel();
                            getSessionNode().createModel(modelClass, true);
                            doDoubleClickAction(getSessionWrapper());
                        }
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });

        JMenuItem simulate = new JMenuItem(new RunSimulationAction(this));
        simulate.setToolTipText("<html>Runs a simulation study, visiting " +
                "<br>nodes downstream recursively in depth first" +
                "<br>order (with repetitions as noted), writing" +
                "<br>output to a log file.</html>");


        popup.add(createModel);
        popup.add(editModel);
        popup.add(destroyModel);

        popup.addSeparator();

        popup.add(renameBox);
        popup.add(cloneBox);
        popup.add(deleteBox);

        popup.addSeparator();
        popup.add(editSimulationParameters);
        addEditLoggerSettings(popup);
        popup.add(propagateDownstream);

        popup.addSeparator();

        popup.add(setRepetition);
        popup.add(simulate);

        popup.addSeparator();
        popup.add(help);

        return popup;
    }


    /**
     * Adds the "Edit logger" option if applicable.
     */
    private void addEditLoggerSettings(JPopupMenu menu) {
        SessionNodeWrapper modelNode = (SessionNodeWrapper) getModelNode();
        SessionNode sessionNode = modelNode.getSessionNode();
        SessionModel model = sessionNode.getModel();
        final TetradLoggerConfig config = sessionNode.getLoggerConfig();
        if (config != null) {
            JMenuItem item = new JMenuItem("Edit Logger Settings ...");
            item.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    showLogConfig(config);
                }
            });
            menu.add(item);
        }
    }


    /**
     * Shows a dialog that allows the user to change the settings for the box's model logger.
     */
    private void showLogConfig(final TetradLoggerConfig config) {
        List<TetradLoggerConfig.Event> events = config.getSupportedEvents();
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(3, events.size() / 3));
        for (TetradLoggerConfig.Event event : events) {
            final String id = event.getId();
            JCheckBox checkBox = new JCheckBox(event.getDescription());
            checkBox.setHorizontalTextPosition(AbstractButton.RIGHT);
            checkBox.setSelected(config.isEventActive(id));
            checkBox.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    JCheckBox box = (JCheckBox) e.getSource();
                    config.setEventActive(id, box.isSelected());
                }
            });
            panel.add(checkBox);
        }
        panel.setBorder(new TitledBorder("Select Events to Log"));
        // how show the dialog
        JOptionPane.showMessageDialog(this, panel, "Edit Events to Log", JOptionPane.PLAIN_MESSAGE);
    }


    private void executeSessionNode(final SessionNode sessionNode,
                                    final boolean overwrite) {
        Window owner = (Window) getTopLevelAncestor();

        new WatchedProcess(owner) {
            public void watch() {
                Class c = SessionEditorWorkbench.class;
                Container container = SwingUtilities.getAncestorOfClass(c,
                        SessionEditorNode.this);
                SessionEditorWorkbench workbench =
                        (SessionEditorWorkbench) container;

                System.out.println("Executing" + sessionNode);

                workbench.getSimulationStudy().execute(sessionNode, overwrite);
            }
        };
    }

    private void createDescendantModels(final boolean overwrite) {
        Window owner = (Window) getTopLevelAncestor();

        new WatchedProcess(owner) {
            public void watch() {
                Class clazz = SessionEditorWorkbench.class;
                Container container = SwingUtilities.getAncestorOfClass(clazz,
                        SessionEditorNode.this);
                SessionEditorWorkbench workbench =
                        (SessionEditorWorkbench) container;

                if (workbench != null) {
                    workbench.getSimulationStudy().createDescendantModels(
                            getSessionNode(), overwrite);
                }

//                if (getSessionNode().getModel() != null) {
//                    JOptionPane.showMessageDialog(SessionEditorNode.this,
//                            "Downstream models overwritten.");
//                }
            }
        };
    }

    /**
     * After editing a session node, either run changes or break edges.
     */
    private void finishedEditingDialog() {
        if (!ModificationRegistery.modelHasChanged(getSessionNode())) {
            return;
        }

        // If there isn't any model downstream, no point to showing the next
        // dialog.
        for (SessionNode child : getChildren()) {
            if (child.getModel() != null) {
                continue;
            }

            return;
        }

        Object[] options = {"Execute", "Break Edges"};
        Component centeringComp = SessionEditorNode.this;
        int selection = JOptionPane.showOptionDialog(centeringComp,
                "Changing this node will affect its children.\n" +
                        "Click on \"Execute\" to percolate changes down.\n" +
                        "Click on \"Break Edges\" to leave the children the same.",
                "Warning", JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[0]);

        if (selection == 0) {
            for (SessionNode child : getChildren()) {
                boolean overwrite = true;
                executeSessionNode(child, overwrite);
            }
        } else if (selection == 1) {
            for (Edge edge : sessionWrapper.getEdges(getModelNode())) {

                // only break edges to children.
                if (edge.getNode2() == getModelNode()) {
                    SessionNodeWrapper otherWrapper =
                            (SessionNodeWrapper) edge.getNode1();
                    SessionNode other = otherWrapper.getSessionNode();
                    if (getChildren().contains(other)) {
                        sessionWrapper.removeEdge(edge);
                    }
                } else {
                    SessionNodeWrapper otherWrapper =
                            (SessionNodeWrapper) edge.getNode2();
                    SessionNode other = otherWrapper.getSessionNode();
                    if (getChildren().contains(other)) {
                        sessionWrapper.removeEdge(edge);
                    }
                }
            }
        }
    }

    private void editRepetition() {
        SessionNodeWrapper wrapper = (SessionNodeWrapper) getModelNode();
        RepetitionEditor repetitionEditor = new RepetitionEditor(this, wrapper);
        Component centeringComp = SessionEditorNode.this;

        JOptionPane.showOptionDialog(centeringComp, repetitionEditor,
                "Repetition Editor", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE, null,
                new String[]{"OK", "Cancel"}, "OK");
    }

    /**
     * Creates a model in the wrapped SessionNode, given the SessionNode's
     * parent models.
     *
     * @throws IllegalStateException if the model cannot be created. The reason
     *                               why the model cannot be created is in the
     *                               message of the exception.
     */
    private boolean createModel(boolean simulation) throws Exception {
        if (getSessionNode().getModel() != null) {
            return true;
        }

        SessionNode sessionNode = getSessionNode();
        Class modelClass = determineTheModelClass(sessionNode);

        if (modelClass == null) {
            return false;
        }

        // Must determine whether that model has a parameterizing object
        // associated with it and if so edit that. (This has to be done
        // before creating the model since it will be an argument to the
        // constructor of the model.)
        if (sessionNode.existsParameterizedConstructor(modelClass)) {
            Params params = sessionNode.getParam(modelClass);
            Object[] arguments = sessionNode.getModelConstructorArguments(
                    modelClass);

            if (params != null) {
                boolean edited = editParameters(modelClass, params, arguments);

                if (!edited) {
                    return false;
                }
            }
        }

        // Finally, create the model. Don't worry, if anything goes wrong
        // in the process, an exception will be thrown with an
        // appropriate message.
        sessionNode.createModel(modelClass, simulation);
        return true;
    }

    /**
     * @return the model class, or null if no model class was determined.
     */
    private Class determineTheModelClass(SessionNode sessionNode) {

        // The config file lists which model classes the node can be
        // associated with, based on the node's type.
        loadModelClassesFromConfig(sessionNode);

        // Must first ascertain the class to create.
        Class[] modelClasses = rememberLastClass ? new Class[]{
                sessionNode.getLastModelClass()} :
                sessionNode.getConsistentModelClasses();

        // If you can't even put a model into the object, throw an
        // exception.
        if ((modelClasses == null) || (modelClasses.length == 0)) {
//            JOptionPane.showMessageDialog(this, missingParentsMessage());
            return null;
//            throw new RuntimeException(missingParentsMessage());
        }

        // Choose a single model class either by asking the user or by just
        // returning the single model in the list.
        return modelClasses.length > 1 ? getModelClassFromUser(modelClasses,
                true) : modelClasses[0];
    }

    /**
     * @return the selected model class, or null if no model class was
     * selected.
     */
    private Class getModelClassFromUser(Class[] modelClasses,
                                        boolean cancelable) {

        // Count the number of model classes that can be listed for the user;
        // if there's only one, don't ask the user for input.
        List<Class> reducedList = new LinkedList<Class>();

        for (Class modelClass : modelClasses) {
            if (!(UnlistedSessionModel.class.isAssignableFrom(modelClass))) {
                reducedList.add(modelClass);
            }
        }

        if (reducedList.size() == 0) {
            throw new RuntimeException("There is no model to choose.");
        }


        SessionNodeWrapper sessionNodeWrapper = (SessionNodeWrapper) getModelNode();
        SessionNode sessionNode = sessionNodeWrapper.getSessionNode();
        ModelChooser chooser = this.config.getModelChooserInstance(sessionNode);
        Component centeringComp = SessionEditorNode.this;

        if (cancelable) {
            int selection = JOptionPane.showOptionDialog(centeringComp,
                    chooser, chooser.getTitle(), JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE, null,
                    new String[]{"OK", "Cancel"}, "OK");

            if (selection == 0) {
                //this.rememberLastClass = modelTypeChooser.getRemembered();
                return chooser.getSelectedModel();
            } else {
                return null;
            }
        } else {
            JOptionPane.showOptionDialog(centeringComp, chooser, chooser.getTitle(),
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, new String[]{"OK"}, "OK");

            //this.rememberLastClass = modelTypeChooser.getRemembered();
            return chooser.getSelectedModel();
        }
    }

    private Class showInfoBoxForModel(SessionNode sessionNode, Class[] modelClasses) {

        // Count the number of model classes that can be listed for the user;
        // if there's only one, don't ask the user for input.
        List<Class> reducedList = new LinkedList<Class>();
        String buttonType;

        for (Class modelClass : modelClasses) {
            if (!(UnlistedSessionModel.class.isAssignableFrom(modelClass))) {
                reducedList.add(modelClass);
            }
        }

        if (reducedList.size() == 0) {
            throw new RuntimeException("There is no model to choose.");
        }

        // Show a model type choose to the user. This has an "Info" button in it.
        SessionNodeWrapper nodeWrapper = (SessionNodeWrapper) getModelNode();
        buttonType = nodeWrapper.getButtonType();

        ModelChooser chooser = this.config.getModelChooserInstance(sessionNode);
        Component centeringComp = SessionEditorNode.this;

        JOptionPane.showMessageDialog(centeringComp, chooser,
                "Choose Model for Help...", JOptionPane.QUESTION_MESSAGE);

        return chooser.getSelectedModel();
    }

    private String missingParentsMessage() {
        Set<SessionNode> parents = getParents();

        for (Object parent3 : parents) {
            SessionNode parent = (SessionNode) parent3;

            if (parent.getModel() == null) {
                return "Please fill in all the parent boxes first.";
            }
        }

        if (parents.size() == 2) {
            Iterator<SessionNode> i = parents.iterator();
            Object parent1 = (i.next()).getModel();
            Object parent2 = (i.next()).getModel();

            if ((parent1 instanceof SemPmWrapper &&
                    parent2 instanceof BayesDataWrapper) || (
                    parent2 instanceof SemPmWrapper &&
                            parent1 instanceof BayesDataWrapper) || (
                    parent2 instanceof SemPmWrapper &&
                            parent1 instanceof DirichletBayesDataWrapper)) {
                return "Sem PM incompatible with discrete data.";
            }

            if ((parent1 instanceof BayesPmWrapper &&
                    parent2 instanceof SemDataWrapper) || (
                    parent2 instanceof BayesPmWrapper &&
                            parent1 instanceof SemDataWrapper)) {
                return "Bayes PM incompatible with continuous data.";
            }

        }

        return "There are no consistent models for that set of parents.";
    }

    private Set<SessionNode> getParents() {
        SessionNodeWrapper _sessionNodeWrapper =
                (SessionNodeWrapper) getModelNode();
        SessionNode _sessionNode = _sessionNodeWrapper.getSessionNode();
        return _sessionNode.getParents();
    }

    public Set<SessionNode> getChildren() {
        SessionNodeWrapper _sessionNodeWrapper =
                (SessionNodeWrapper) getModelNode();
        SessionNode _sessionNode = _sessionNodeWrapper.getSessionNode();
        return _sessionNode.getChildren();
    }

    /**
     * Update the model classes of the given node to the latest available model
     * classes, of such exists. Otherwise, leave the model classes of the node
     * unchanged.
     */
    private static void loadModelClassesFromConfig(SessionNode sessionNode) {
        String nodeName = sessionNode.getBoxType();

        if (nodeName != null) {
            String baseName = extractBase(nodeName);
            Class[] newModelClasses = modelClasses(baseName);

            if (newModelClasses != null) {
                sessionNode.setModelClasses(newModelClasses);
            } else {
                throw new RuntimeException("Model classes for this session " +
                        "node were not set in the configuration.");
            }
        }
    }

    /**
     * Destroys the model for the wrapped SessionNode.
     */
    private void destroyModel() {
        getSessionNode().destroyModel();
        getSessionNode().forgetOldModel();
    }


    /**
     * Tries to edit the parameters, returns true if successfully otherwise false is returned
     */
    private boolean editParameters(final Class modelClass, Params params,
                                   Object[] parentModels)
            throws Exception {
        if (parentModels == null) {
            throw new NullPointerException("Parent models array is null.");
        }

        if (params == null) {
            throw new NullPointerException("Params cannot be null.");
        }


        SessionNodeModelConfig modelConfig = this.config.getModelConfig(modelClass);
        final ParameterEditor paramEditor = modelConfig.getParameterEditorInstance();
        if (paramEditor == null) {
            // if no editor, then consider the params "edited".
            return true;
        } else {
            paramEditor.setParams(params);
            paramEditor.setParentModels(parentModels);
        }
        // If a finalizing editor and a dialog then let it handle things on itself onw.
        if (paramEditor instanceof FinalizingParameterEditor && paramEditor instanceof JDialog) {
            FinalizingParameterEditor e = (FinalizingParameterEditor) paramEditor;
            e.setup();
            return e.finalizeEdit();
        }
        // wrap editor and deal with respose.
        paramEditor.setup();
        JComponent editor = (JComponent) paramEditor;
        SessionNodeWrapper nodeWrapper = (SessionNodeWrapper) getModelNode();
        String buttonType = nodeWrapper.getButtonType();
        editor.setName(buttonType + " Structure Editor");
        Component centeringComp = SessionEditorNode.this;

        int ret = JOptionPane.showOptionDialog(centeringComp, editor,
                editor.getName(), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, null,
                null, null);

        if (ret == JOptionPane.OK_OPTION) {
            // if finalizing editor, then deal with specially.
            if (paramEditor instanceof FinalizingParameterEditor) {
                return ((FinalizingParameterEditor) paramEditor).finalizeEdit();
            }
            return true;
        }

        // This doens't block properly.
//        final EditorWindow window = new EditorWindow((JPanel) paramEditor,
//                "All Paths", "Close", true, centeringComp);
//        DesktopController.getInstance().addEditorWindow(window);
//        window.setVisible(true);
//
//        window.addInternalFrameListener(new InternalFrameAdapter() {
//            public void internalFrameClosing(InternalFrameEvent internalFrameEvent) {
//                try {
//                    EditorWindow window = (EditorWindow) internalFrameEvent.getSource();
//
//                    if (window.isCanceled()) {
//                        return;
//                    }
//
//                    boolean successful = true;
//
//                    if (paramEditor instanceof FinalizingParameterEditor) {
//                        successful = ((FinalizingParameterEditor) paramEditor).finalizeEdit();
//                    }
//
//                    if (successful) {
//                        getSessionNode().destroyModel();
//                        getSessionNode().createModel(modelClass, true);
//                        doDoubleClickAction(getSessionWrapper());
//                    }
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//
//            }
//        });

        return false;
    }

    public SessionNode getSessionNode() {
        SessionNodeWrapper wrapper = (SessionNodeWrapper) super.getModelNode();
        return wrapper.getSessionNode();
    }

//    private String getDescription(Class modelClass) {
//        SessionNodeModelConfig config = this.config.getModelConfig(modelClass);
//        return config.getName();
//    }

    private void setSpawnedEditor(EditorWindow editorWindow) {
        this.spawnedEditor = editorWindow;
    }

    /**
     * @return the spawned editor, if there is one.
     */
    private EditorWindow spawnedEditor() {
        return this.spawnedEditor;
    }

    /**
     * Sets up default parameter objects for the node.
     */
    private void createParamObjects(SessionEditorNode sessionEditorNode) {
        SessionNode sessionNode = sessionEditorNode.getSessionNode();
        Class[] modelClasses = sessionNode.getModelClasses();
        for (Class clazz : modelClasses) {
            // A parameter class might exist if this session was read
            // in from a file.
            if (sessionNode.getParam(clazz) == null) {
                SessionNodeModelConfig modelConfig = this.config.getModelConfig(clazz);
                if (modelConfig == null) {
                    continue;
//                    throw new NullPointerException("No configuration found for model: " + clazz);
                }
                Params param = modelConfig.getParametersInstance();
                if (param != null) {
                    sessionNode.putParam(clazz, param);
                }
            }
        }
    }

    /**
     * @return the substring of <code>name</code> up to but not including a
     * contiguous string of digits at the end. For example, given "Graph123"
     */
    private static String extractBase(String name) {
        if (name == null) {
            throw new NullPointerException("Name must not be null.");
        }

        StringBuilder buffer = new StringBuilder(name);

        for (int i = buffer.length() - 1; i >= 0; i--) {
            if (!Character.isDigit(buffer.charAt(i))) {
                return buffer.substring(0, i + 1);
            }
        }

        return "Node";
    }

    /**
     * @return the model classes associated with the given button type.
     *
     * @throws NullPointerException if no classes are stored for the given
     *                              type.
     */
    private static Class[] modelClasses(String boxType) {
        TetradApplicationConfig config = TetradApplicationConfig.getInstance();
        SessionNodeConfig nodeConfig = config.getSessionNodeConfig(boxType);
        if (nodeConfig == null) {
            throw new NullPointerException("THere is no configuration for " + boxType);
        }

        return nodeConfig.getModels();
    }

    private SimulationStudy getSimulationStudy() {
        return simulationStudy;
    }


    private SessionWrapper getSessionWrapper() {
        return sessionWrapper;
    }

    public SessionDisplayComp getSessionDisplayComp() {
        return (SessionDisplayComp) getDisplayComp();
    }

    /**
     * Allows the user to edit the number of times a given node is executed in a
     * simulation study.
     */
    private static final class RepetitionEditor extends JComponent {
        private final SessionEditorNode editorNode;
        private final SessionNodeWrapper wrapper;

        public RepetitionEditor(SessionEditorNode editorNode,
                                SessionNodeWrapper wrapper) {
            this.editorNode = editorNode;
            this.wrapper = wrapper;

            IntTextField repetitionField = new IntTextField(getRepetition(), 6);
            repetitionField.setFilter(new IntTextField.Filter() {
                public int filter(int value, int oldValue) {
                    try {
                        setRepetition(value);
                        return value;
                    } catch (Exception e) {
                        return oldValue;
                    }
                }
            });

            setLayout(new BorderLayout());

            Box b0 = Box.createVerticalBox();

            Box b1 = Box.createHorizontalBox();
            b1.add(new JLabel("Set the number of times this node should be"));
            b1.add(Box.createHorizontalGlue());
            b0.add(b1);

            Box b2 = Box.createHorizontalBox();
            b2.add(new JLabel("repeated each time it is encountered in a depth first"));
            b2.add(Box.createHorizontalGlue());
            b0.add(b2);

            Box b3 = Box.createHorizontalBox();
            b3.add(new JLabel("traversal of the nodes:"));
            b3.add(Box.createHorizontalGlue());
            b3.add(repetitionField);
            b0.add(b3);

            add(b0, BorderLayout.CENTER);
        }

        private void setRepetition(int value) {
            simulationStudy().setRepetition(getSessionNode(), value);
        }

        private int getRepetition() {
            return simulationStudy().getRepetition(getSessionNode());
        }

        public SessionNodeWrapper getWrapper() {
            return this.wrapper;
        }

        public SessionEditorNode getEditorNode() {
            return editorNode;
        }

        public SessionNode getSessionNode() {
            return wrapper.getSessionNode();
        }

        private SimulationStudy simulationStudy() {
            return editorNode.getSimulationStudy();
        }
    }
}




