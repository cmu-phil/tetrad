/*
 * Copyright (C) 2017 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetradapp.ui.tool;

import edu.cmu.tetrad.session.Session;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Version;
import edu.cmu.tetradapp.app.DecompressibleInputStream;
import edu.cmu.tetradapp.app.SessionEditor;
import edu.cmu.tetradapp.app.SessionEditorWorkbench;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.model.TetradMetadata;
import edu.cmu.tetradapp.util.DesktopController;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.TransferHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Dec 6, 2017 1:02:46 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class SessionFileTransferHandler extends TransferHandler {

    private static final long serialVersionUID = -6674597813640455425L;

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionFileTransferHandler.class);

    @Override
    public boolean canImport(TransferSupport support) {
        for (DataFlavor flavor : support.getDataFlavors()) {
            if (flavor.isFlavorJavaFileListType()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean importData(TransferSupport support) {
        try {
            List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
            for (File file : files) {
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
                            return false;
                        }
                    }
                }

                try (InputStream in = Files.newInputStream(file.toPath())) {
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
                            sessionWrapper = null;
                        }
                    } else if (o instanceof SessionWrapper) {
                        metadata = null;
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
                                "The session was saved by Tetrad " + version + " on " +  df.format(date));

                        return false;
                    }

                    SessionEditorWorkbench graph
                            = new SessionEditorWorkbench(sessionWrapper);

                    String name = file.getName();
                    sessionWrapper.setName(name);

                    SessionEditor editor = new SessionEditor(name, graph);

                    DesktopController.getInstance().addSessionEditor(editor);
                    DesktopController.getInstance().closeEmptySessions();
                    DesktopController.getInstance().putMetadata(sessionWrapper, metadata);
                } catch (FileNotFoundException exception) {
                    LOGGER.error("", exception);
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), "That wasn't a TETRAD session file: " + file);
                } catch (Exception exception) {
                    LOGGER.error("", exception);
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), "An error occurred attempting to load the session.");
                }
            }
        } catch (UnsupportedFlavorException | IOException exception) {
            LOGGER.error("", exception);
        }

        return super.importData(support);
    }

}
