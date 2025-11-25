package edu.cmu.tetradapp.app;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Launches the online Tetrad manual in the system browser.
 *
 * @author josephramsey
 */
final class LaunchManualAction extends AbstractAction {

    // TODO: update this to the final RTD URL you decide to use (stable vs latest, etc.).
    private static final String MANUAL_URL =
            "https://tetrad-manual.readthedocs.io/en/latest/";

    public LaunchManualAction() {
        super("Launch Manual");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!Desktop.isDesktopSupported()) {
            JOptionPane.showMessageDialog(
                    null,
                    "Desktop browsing is not supported on this platform.\n" +
                    "You can open the manual manually at:\n" + MANUAL_URL,
                    "Launch Manual",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        Desktop desktop = Desktop.getDesktop();
        try {
            desktop.browse(new URI(MANUAL_URL));
        } catch (IOException | URISyntaxException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                    null,
                    "Unable to open the manual in your browser.\n" +
                    "You can still access it at:\n" + MANUAL_URL,
                    "Launch Manual",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }
}