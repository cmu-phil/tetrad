///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Version;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.model.TetradMetadata;
import edu.cmu.tetradapp.session.Session;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.WatchedProcess;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.prefs.Preferences;


/**
 * Represents an action to load a session from a file. Extends AbstractAction class.
 */
final class LoadSessionAction extends AbstractAction {

    /**
     * Constructs a new action to open sessions.
     */
    public LoadSessionAction() {
        super("Open Session...");
    }

    /**
     * Opens a session file and loads it into Tetrad.
     *
     * @param e the event to be processed
     */
    public void actionPerformed(ActionEvent e) {

        // select a file to open using the file chooser
        JFileChooser chooser = new JFileChooser();
        String sessionSaveLocation =
                Preferences.userRoot().get("sessionSaveLocation", "");

        for (javax.swing.filechooser.FileFilter filter : chooser.getChoosableFileFilters()) {
            chooser.removeChoosableFileFilter(filter);
        }

        chooser.setCurrentDirectory(new File(sessionSaveLocation));
        chooser.addChoosableFileFilter(new TetFileFilter());
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int ret1 = chooser.showOpenDialog(JOptionUtils.centeringComp());

        if (!(ret1 == JFileChooser.APPROVE_OPTION)) {
            return;
        }

        File file = chooser.getSelectedFile();

        if (file == null) {
            return;
        }

        Preferences.userRoot().put("sessionSaveLocation", file.getParent());

        Session session = DesktopController.getInstance().getSessionByName(file.getName());

        if (session != null) {
            if (session.isEmpty()) {
                DesktopController.getInstance().closeSessionByName(file.getName());
            } else {
                int ret = JOptionPane.showConfirmDialog(JOptionUtils.centeringComp(),
                        "Replace existing session by that name?.", "Confirm", JOptionPane.YES_NO_OPTION);

                if (ret == JOptionPane.YES_OPTION) {
                    DesktopController.getInstance().closeSessionByName(file.getName());
                } else {
                    return;
                }
            }
        }

        // The watcher thread is causing a race condition with JFileChooser.showOpenDialog somehow. Placing that
        // code outside the thread.
        class MyWatchedProcess extends WatchedProcess {
            public void watch() {
                try {
                    FileInputStream in = new FileInputStream(file);
                    DecompressibleInputStream objIn = new DecompressibleInputStream(in);
                    Object o = objIn.readObject();

                    TetradMetadata metadata = null;
                    SessionWrapper sessionWrapper = null;

                    if (o instanceof TetradMetadata) {
                        metadata = (TetradMetadata) o;

                        try {
                            sessionWrapper = (SessionWrapper) objIn.readObject();
                        } catch (ClassNotFoundException e1) {

                            throw e1;
                        } catch (Exception e2) {
                            e2.printStackTrace();
                            TetradLogger.getInstance().forceLogMessage("Exception: " + e2.getMessage());
                        }
                    } else if (o instanceof SessionWrapper) {
                        sessionWrapper = (SessionWrapper) o;
                    }

                    in.close();

                    if (metadata == null) {
                        throw new NullPointerException("Could not read metadata.");
                    }

                    if (sessionWrapper == null) {
                        Version version = metadata.getVersion();
                        Date date = metadata.getDate();
                        SimpleDateFormat df = new SimpleDateFormat("MMM dd, yyyy");

                        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                                "Could not load this session file into Tetrad " + Version.currentViewableVersion() + "! \n" +
                                "The session was saved by Tetrad " + version + " on " + df.format(date));

                        return;
                    }

                    SessionEditorWorkbench graph =
                            new SessionEditorWorkbench(sessionWrapper);

                    String name = file.getName();
                    sessionWrapper.setName(name);

                    SessionEditor editor = new SessionEditor(name, graph);

                    DesktopController.getInstance().addSessionEditor(editor);
                    DesktopController.getInstance().closeEmptySessions();
                    DesktopController.getInstance().putMetadata(sessionWrapper, metadata);
                } catch (FileNotFoundException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), "That wasn't a TETRAD session file: " + file);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), "An error occurred attempting to load the session.");
                }
            }
        }

        new MyWatchedProcess();
    }

    /**
     * Represents a decompressible input stream for deserializing objects.
     */
    public static class DecompressibleInputStream extends ObjectInputStream {

        public DecompressibleInputStream(InputStream in) throws IOException {
            super(in);
        }

        public Class<?> resolveClass(ObjectStreamClass desc) throws ClassNotFoundException {
            String remappedClassName = mapToCurrentPackageName(desc.getName());
            return Class.forName(remappedClassName);
        }

        private String mapToCurrentPackageName(String originalClassName) {

            // Implement this function to correctly map obsolete class names to their current counterparts
            // The following lines are just examples, should be adapted according to the actual class name changes in your codebase
            if (originalClassName.contains("tetrad.session"))
                return originalClassName.replace("tetrad.session", "tetradapp.session");

            // If no special mapping is needed, return the original class name
            return originalClassName;
        }
    }
}





