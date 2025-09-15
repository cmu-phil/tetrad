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
final class LaunchFlowchartAction extends AbstractAction {

    /**
     * Creates a new close session action for the given desktop.
     */
    public LaunchFlowchartAction() {
        super("Launch Algorithm Flowchart");
    }

    /**
     * This method handles the action event triggered by a user interaction.
     *
     * @param e the event to be processed
     */
    public void actionPerformed(ActionEvent e) {
        Desktop d = Desktop.getDesktop();
        try {
            d.browse(new URI("https://htmlpreview.github.io/?https:///github.com/cmu-phil/" +
                             "tetrad/blob/development/tetrad-lib/src/main/resources/docs/manual/flowchart.html"));
        } catch (IOException | URISyntaxException e2) {
            e2.printStackTrace();
        }
    }
}



