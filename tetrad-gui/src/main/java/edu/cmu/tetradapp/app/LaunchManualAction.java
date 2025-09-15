package edu.cmu.tetradapp.app;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Closes the frontmost session of the given desktop.
 *
 * @author josephramsey
 */
final class LaunchManualAction extends AbstractAction {

    /**
     * Creates a new close session action for the given desktop.
     */
    public LaunchManualAction() {
        super("Launch Manual");
    }

    /**
     * This method handles the action performed when a specific event is triggered.
     *
     * @param e the event to be processed
     */
    public void actionPerformed(ActionEvent e) {
        Desktop d = Desktop.getDesktop();
        try {
            d.browse(new URI("https://htmlpreview.github.io/?https:///github.com/cmu-phil/tetrad/blob/development/" +
                             "tetrad-lib/src/main/resources/docs/manual/index.html"));
        } catch (IOException | URISyntaxException e2) {
            e2.printStackTrace();
        }
    }
}



