package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Version;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.model.TetradMetadata;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.SessionEditorIndirectRef;
import edu.cmu.tetradapp.util.TetradMetadataIndirectRef;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.text.SimpleDateFormat;

/**
 * Saves a session from a file.
 *
 * @author josephramsey
 */
final class SessionVersionAction extends AbstractAction {

    /**
     * <p>Constructor for SessionVersionAction.</p>
     */
    public SessionVersionAction() {
        super("Session Version");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Performs the action of loading a session from a file.
     */
    public void actionPerformed(ActionEvent e) {

        // Get the frontmost SessionWrapper.
        SessionEditorIndirectRef sessionEditorRef =
                DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        SessionEditorWorkbench workbench = sessionEditor.getSessionWorkbench();
        SessionWrapper sessionWrapper = workbench.getSessionWrapper();
        TetradMetadataIndirectRef metadataRef =
                DesktopController.getInstance().getTetradMetadata(
                        sessionWrapper);
        TetradMetadata metadata = (TetradMetadata) metadataRef;

        StringBuilder buf = new StringBuilder();

        if (metadata == null) {
            buf.append(
                    "This session has not yet been saved or loaded. The model\n");
            buf.append("version you are working in is ");
            buf.append(Version.currentViewableVersion());
            buf.append(".");
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    buf.toString());
            return;
        }

        SimpleDateFormat df = new SimpleDateFormat("MMM dd, yyyy");

        buf.append("Version information for \"");
        buf.append(sessionWrapper.getName());
        buf.append("\":\n\n");
        buf.append("Last saved using Tetrad ");
        buf.append(metadata.getVersion());
        buf.append(" (");
        buf.append(df.format(metadata.getDate()));
        buf.append(").\n");
        buf.append("You are running Tetrad ");
        buf.append(Version.currentViewableVersion());
        buf.append(".");
        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                buf.toString());
    }
}





