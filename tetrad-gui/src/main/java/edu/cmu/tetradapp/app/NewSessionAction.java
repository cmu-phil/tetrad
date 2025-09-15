package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.util.DesktopController;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Opens a session from a file.
 *
 * @author josephramsey
 */
final class NewSessionAction extends AbstractAction {

    /**
     * Creates a new session action for the given desktop.
     */
    public NewSessionAction() {
        super("New Session");
    }

    /**
     * Performs an action when an event occurs.
     *
     * @param e the event to be processed
     */
    public void actionPerformed(ActionEvent e) {
        DesktopController.getInstance().newSessionEditor();
    }
}





