package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.SessionEditorIndirectRef;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Closes the frontmost session of the given desktop.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class CloseSessionAction extends AbstractAction {

    /**
     * Creates a new close session action for the given desktop.
     */
    public CloseSessionAction() {
        super("Close Session");
    }

    /**
     * Performs an action when an event occurs.
     *
     * @param e the event to be processed
     */
    public void actionPerformed(ActionEvent e) {

        // Get the frontmost SessionWrapper.
        SessionEditorIndirectRef sessionEditorRef =
                DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        SessionEditorWorkbench graph = sessionEditor.getSessionWorkbench();
        SessionWrapper sessionWrapper = graph.getSessionWrapper();

        if (sessionWrapper.isSessionChanged()) {
            String name = sessionWrapper.getName();

            // check to make sure user wants to evaporate this window...
            String msg =
                    "Do you want to save the changes you made to " + name + "?";
            int response = JOptionPane.showConfirmDialog(
                    JOptionUtils.centeringComp(), msg, "Fair Warning",
                    JOptionPane.YES_NO_CANCEL_OPTION);

            if (response == JOptionPane.YES_OPTION) {
                SaveSessionAction saveSessionAction = new SaveSessionAction();
                saveSessionAction.actionPerformed(e);
            } else if (response == JOptionPane.CANCEL_OPTION) {
                return;
            }
        }

        DesktopController.getInstance().closeFrontmostSession();

        if (DesktopController.getInstance().getFrontmostSessionEditor() == null) {
            new NewSessionAction().actionPerformed(null);
        }
    }

    /**
     * Prints out a string representation of this object.
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "Close session action.";
    }
}





