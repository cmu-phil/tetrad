package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.editor.SaveComponentImage;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.session.Session;
import edu.cmu.tetradapp.util.SessionEditorIndirectRef;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays a toolbar and workbench for editing Session's.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see SessionEditorWorkbench
 * @see SessionEditorToolbar
 */
public final class SessionEditor extends JComponent
        implements SessionEditorIndirectRef {

    /**
     * The toolbar displayed on the left-hand side of the session editor.
     */
    private SessionEditorToolbar toolbar;

    /**
     * The workbench displayed on the right-hand side of the session editor.
     */
    private SessionEditorWorkbench workbench;

    /**
     * Constucts a session editor with the given name.
     *
     * @param name a {@link java.lang.String} object
     */
    public SessionEditor(String name) {
        this(name, null);
    }

    /**
     * Constructs a new session editor.  A session editor consists of a session workbench and a session toolbar.
     *
     * @param name      The name of the session.  This is used for saving out
     * @param workbench a {@link edu.cmu.tetradapp.app.SessionEditorWorkbench} object
     */
    public SessionEditor(String name, SessionEditorWorkbench workbench) {
        setName(name);

        if (workbench == null) {
            Session session = new Session(name);
            SessionWrapper wrapper = new SessionWrapper(session);
            workbench = new SessionEditorWorkbench(wrapper);
        }

        workbench.setName(name);

        setWorkbench(workbench);
        setToolbar(new SessionEditorToolbar(workbench));
        JScrollPane workbenchScroll = new JScrollPane(workbench);

        setLayout(new BorderLayout());
        add(workbenchScroll, BorderLayout.CENTER);
        add(getToolbar(), BorderLayout.WEST);

        workbench.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent e) {
                String propertyName = e.getPropertyName();

                if ("name".equals(propertyName)) {
                    firePropertyChange("name", e.getOldValue(),
                            e.getNewValue());
                }
            }
        });
    }

    /**
     * <p>getSessionWorkbench.</p>
     *
     * @return the session workbench.
     */
    public SessionEditorWorkbench getSessionWorkbench() {
        return getWorkbench();
    }

    /**
     * <p>getSelectedModelComponents.</p>
     *
     * @return a list of all the SessionNodeWrappers (TetradNodes) and SessionNodeEdges that are model components for
     * the respective SessionNodes and SessionEdges selected in the workbench. Note that the workbench, not the
     * SessionEditorNodes themselves, keeps track of the selection.
     */
    public List getSelectedModelComponents() {
        List selectedComponents = getWorkbench().getSelectedComponents();
        List selectedModelComponents = new ArrayList();

        for (Object comp : selectedComponents) {
            if (comp instanceof SessionEditorNode editorNode) {
                Node modelNode = editorNode.getModelNode();
                selectedModelComponents.add(modelNode);
            } else if (comp instanceof SessionEditorEdge editorEdge) {
                Edge modelEdge = getWorkbench().getModelEdge(editorEdge);
                selectedModelComponents.add(modelEdge);
            }
        }

        return selectedModelComponents;
    }

    /**
     * Pastes a consistent list of model nodes into the workbench. Note that the responsivity of the toolbar to events
     * needs to be turned off during this operation.
     *
     * @param sessionElements the list of model nodes.
     * @param point           the upper left corner of the first node.
     */
    public void pasteSubsession(List sessionElements, Point point) {
        getToolbar().setRespondingToEvents(false);
        getWorkbench().pasteSubsession(sessionElements, point);
        getToolbar().setRespondingToEvents(true);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the name of the session editor.
     */
    public void setName(String name) {
        if (name == null) {
            throw new NullPointerException("Name must not be null.");
        }

        super.setName(name);
    }

    private SessionEditorToolbar getToolbar() {
        return this.toolbar;
    }

    private void setToolbar(SessionEditorToolbar toolbar) {
        this.toolbar = toolbar;
    }

    private SessionEditorWorkbench getWorkbench() {
        return this.workbench;
    }

    private void setWorkbench(SessionEditorWorkbench workbench) {
        this.workbench = workbench;
    }

    /**
     * <p>saveSessionImage.</p>
     */
    public void saveSessionImage() {
        Action action = new SaveComponentImage(this.workbench, "Save Session Image...");
        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "Save"));
    }

    /**
     * <p>firePropertyChange.</p>
     *
     * @param s    a {@link java.lang.String} object
     * @param o    a {@link java.lang.Object} object
     * @param name a {@link java.lang.String} object
     */
    public void firePropertyChange(String s, Object o, String name) {
        super.firePropertyChange(s, o, name);
    }
}





