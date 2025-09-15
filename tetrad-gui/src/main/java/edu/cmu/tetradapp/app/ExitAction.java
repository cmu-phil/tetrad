package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.util.DesktopController;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Exits the Tetrad application.
 *
 * @author josephramsey
 */
final class ExitAction extends AbstractAction {

    /**
     * Creates an exit action.
     */
    public ExitAction() {
        super("Exit");
    }

    /**
     * This method is called when an action event occurs. It invokes the exitProgram() method in the DesktopController
     * class to exit the Tetrad application.
     *
     * @param e the event to be processed
     */
    public void actionPerformed(ActionEvent e) {
        DesktopController.getInstance().exitProgram();
    }
}





