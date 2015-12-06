///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.session.Session;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.editor.EditorWindow;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.model.TetradMetadata;
import edu.cmu.tetradapp.util.*;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Constructs a desktop for the Tetrad application.
 *
 * @author Don Crimbchin (djc2@andrew.cmu.edu)
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 * @author Raul Salinas wsalinas@andrew.cmu.edu
 */
public final class TetradDesktop extends JPanel
        implements DesktopControllable, PropertyChangeListener {

    /**
     * Margin for desktop when unmaximized.
     */
    private static final int MARGIN = 10;

    /**
     * The desktop pane in which all of the session editors are located.
     */
    private final JDesktopPane desktopPane;

    /**
     * Stores a list of keys for components added to the workbench.
     */
    private final List sessionNodeKeys;

    /**
     * A map from components in the desktop to the frames they're embedded in.
     */
    private final Map<SessionEditor, JInternalFrame> framesMap = new HashMap<SessionEditor, JInternalFrame>();

    /**
     * A map from SessionWrapper to TetradMetadata, storing metadata for
     * sessions that have been loaded in.
     */
    private final Map<SessionWrapper, TetradMetadata> metadataMap = new HashMap<SessionWrapper, TetradMetadata>();


    /**
     * The log display, is null when not being displayed.
     */
    private TetradLogArea logArea;


    /**
     * Constructs a new desktop.
     */
    public TetradDesktop() {
        setBackground(new Color(204, 204, 204));
        sessionNodeKeys = new ArrayList();
        // Create the desktop pane.
        this.desktopPane = new JDesktopPane();

        // Do Layout.
        setLayout(new BorderLayout());
        desktopPane.setDesktopManager(new DefaultDesktopManager());
        desktopPane.setBorder(new BevelBorder(BevelBorder.LOWERED));
        desktopPane.addPropertyChangeListener(this);

        this.setupDesktop();
        Preferences.userRoot().putBoolean("displayLogging", false);
//        setDisplayLogging(Preferences.userRoot().getBoolean("displayLogging", false));
        TetradLogger.getInstance().addTetradLoggerListener(new LoggerListener());

//        Bug in Swing for 1.7.
//        System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
    }

    //===========================PUBLIC METHODS============================//

    public void newSessionEditor() {
        String newName = getNewSessionName();
        SessionEditor editor = new SessionEditor(newName);
        addSessionEditor(editor);
    }

    /**
     * Adds a component to the middle layer of the desktop--that is, the layer
     * for session node editors. Note: The comp is a SessionEditor
     */
    public void addSessionEditor(SessionEditorIndirectRef editorRef) {
        SessionEditor editor = (SessionEditor) editorRef;

        JInternalFrame frame = new TetradInternalFrame(null);

        frame.getContentPane().add(editor);
        framesMap.put(editor, frame);
        editor.addPropertyChangeListener(this);

        // Set the "small" size of the frame so that it has sensible
        // bounds when the users unmazimizes it.
        Dimension fullSize = desktopPane.getSize();
        int smallSize =
                Math.min(fullSize.width - MARGIN, fullSize.height - MARGIN);
        Dimension size = new Dimension(smallSize, smallSize);
        setGoodBounds(frame, desktopPane, size);
        desktopPane.add(frame);

        // Set the frame to be maximized. This step must come after the frame
        // is added to the desktop. -Raul. 6/21/01
        try {
            frame.setMaximum(true);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Problem setting frame to max: " + frame);
        }

        desktopPane.setLayer(frame, JLayeredPane.DEFAULT_LAYER);
        frame.moveToFront();
        frame.setTitle(editor.getName());
        frame.setVisible(true);

        setMainTitle(editor.getName());
    }

    /**
     * Adds the given componet to the given layer.
     */
    public void addEditorWindow(EditorWindowIndirectRef windowRef, int layer) {
        final EditorWindow window = (EditorWindow) windowRef;

        Dimension desktopSize = desktopPane.getSize();
        Dimension preferredSize = window.getPreferredSize();

//        int x = desktopSize.width / 2 - preferredSize.width / 2;
//        int y = desktopSize.height / 2 - preferredSize.height / 2;

        Component source = window.getCenteringComp();

        Point convertedPoint = SwingUtilities.convertPoint(source.getParent(), source.getLocation(), this);

        int x = convertedPoint.x + source.getWidth() / 2 - preferredSize.width / 2;
        int y = convertedPoint.y - 25 + source.getHeight() / 2 - preferredSize.height / 2;

        int topMargin = 35;
        int bottomMargin = 35;
        int leftMargin = 150;
        int rightMargin = 25;

        if (x < leftMargin) x = leftMargin;
        if (y < topMargin) y = topMargin;

        int height = Math.min(preferredSize.height, getHeight() - topMargin - bottomMargin);
        int width = Math.min(preferredSize.width, getWidth() - leftMargin - rightMargin);

        if (x + width > getWidth() - rightMargin) {
            x = getWidth() - width - rightMargin;
        }

        if (y + height > getHeight() - bottomMargin) {
            y = getHeight() - height - bottomMargin;
        }

        window.setLocation(x, y);
        window.setPreferredSize(new Dimension(width, height));

        // This line sometimes hangs, so I'm putting it in a watched process
        // so it can be stopped by the user. Not ideal.
//        Window owner = (Window) getTopLevelAncestor();
//
//        new WatchedProcess(owner) {
//            public void watch() {
        getDesktopPane().add(window);
        window.setLayer(layer);
        window.moveToFront();

        window.setVisible(true);
    }

    public void closeFrontmostSession() {
        for (JInternalFrame frame : desktopPane.getAllFrames()) {
            if (frame instanceof EditorWindow) {
                ((EditorWindow) frame).closeDialog();
            }
        }

        JInternalFrame[] frames = desktopPane.getAllFramesInLayer(0);

        if (frames.length > 0) {
            frames[0].dispose();
            Map<SessionEditor, JInternalFrame> framesMap = this.framesMap;
            for (Iterator<SessionEditor> i = framesMap.keySet().iterator(); i.hasNext(); ) {
                SessionEditor sessionEditor = i.next();
                JInternalFrame frame = framesMap.get(sessionEditor);
                if (frame == frames[0]) {
                    i.remove();
                    break;
                }
            }
        }
    }

    public void closeSessionByName(String name) {
        for (JInternalFrame frame : desktopPane.getAllFrames()) {
            if (frame instanceof EditorWindow) {
                ((EditorWindow) frame).closeDialog();
            }
        }

        JInternalFrame[] frames = desktopPane.getAllFramesInLayer(0);

        if (frames.length > 0) {
            Map<SessionEditor, JInternalFrame> framesMap = this.framesMap;
            for (Iterator<SessionEditor> i = framesMap.keySet().iterator(); i.hasNext(); ) {
                SessionEditor sessionEditor = i.next();
                if (sessionEditor.getName().equals(name)) {
                    JInternalFrame frame = framesMap.get(sessionEditor);
                    i.remove();
                }
            }
        }
    }

    public void closeEmptySessions() {
        JInternalFrame[] frames = desktopPane.getAllFramesInLayer(0);

        for (JInternalFrame frame : frames) {
            Object o = frame.getContentPane().getComponents()[0];

            if (o instanceof SessionEditor) {
                SessionEditor sessionEditor = (SessionEditor) o;
                SessionEditorWorkbench workbench =
                        sessionEditor.getSessionWorkbench();
                Graph graph = workbench.getGraph();
                if (graph.getNumNodes() == 0) {
                    frame.dispose();
                }
            }
        }
    }

    public boolean existsSessionByName(String name) {
        JInternalFrame[] allFrames = desktopPane.getAllFramesInLayer(0);

        for (JInternalFrame allFrame : allFrames) {
            Object o = allFrame.getContentPane().getComponents()[0];

            if (o instanceof SessionEditor) {
                SessionEditor editor = (SessionEditor) o;

                String editorName = editor.getName();
                if (editorName.equals(name)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public Session getSessionByName(String name) {
        JInternalFrame[] allFrames = desktopPane.getAllFramesInLayer(0);

        for (JInternalFrame allFrame : allFrames) {
            Object o = allFrame.getContentPane().getComponents()[0];

            if (o instanceof SessionEditor) {
                SessionEditor editor = (SessionEditor) o;

                String editorName = editor.getName();
                if (editorName.equals(name)) {
                    return editor.getSessionWorkbench().getSessionWrapper().getSession();
                }
            }
        }

        return null;
    }

    public SessionEditor getFrontmostSessionEditor() {
        JInternalFrame[] allFrames = desktopPane.getAllFramesInLayer(0);

        if (allFrames.length == 0) {
            return null;
        }

        JInternalFrame frontmostFrame = allFrames[0];
        Object o = frontmostFrame.getContentPane().getComponents()[0];

        boolean isSessionEditor = o instanceof SessionEditor;
        return isSessionEditor ? (SessionEditor) o : null;
    }

    /**
     * Reacts to property change events 'editorClosing', 'closeFrame', and
     * 'name'.
     *
     * @param e the property change event.
     */
    public void propertyChange(PropertyChangeEvent e) {

        //Handles the removal of editor frames from desktop
        String name = e.getPropertyName();

        if ("editorClosing".equals(name)) {

            //find NewValue in String array, and remove
            for (int n = 0; n < sessionNodeKeys.size(); n++) {
                if (e.getNewValue().equals((sessionNodeKeys.get(n)))) {
                    sessionNodeKeys.remove(n);
                }
            }
        } else if ("closeFrame".equals(e.getPropertyName())) {
            if (getFramesMap().containsKey(e.getSource())) {
                Object frameObject = getFramesMap().get(e.getSource());
                JInternalFrame frame = (JInternalFrame) frameObject;
                frame.setVisible(false);
                frame.dispose();
            }
        } else if ("name".equals(e.getPropertyName())) {
            if (getFramesMap().containsKey(e.getSource())) {
                Object frameObject = getFramesMap().get(e.getSource());
                JInternalFrame frame = (JInternalFrame) frameObject;
                String _name = (String) (e.getNewValue());
                frame.setTitle(_name);
                setMainTitle(_name);
            }
        }
    }

    private void setMainTitle(String name) {
        JFrame jFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
        jFrame.setTitle(name + " - " + "Tetrad " +
                Version.currentViewableVersion().toString());
    }

    /**
     * Fires an event to close the program.
     */
    public void exitProgram() {
        firePropertyChange("exitProgram", null, null);
    }

    public final JDesktopPane getDesktopPane() {
        return this.desktopPane;
    }

    /**
     * Queries the user as to whether they would like to save their sessions.
     *
     * @return true if the transaction was ended successfully, false if not
     * (that is, canceled).
     */
    public boolean closeAllSessions() {
        while (existsSession()) {
            SessionEditor sessionEditor = getFrontmostSessionEditor();
            SessionEditorWorkbench workbench =
                    sessionEditor.getSessionWorkbench();
            SessionWrapper wrapper = workbench.getSessionWrapper();

            if (!wrapper.isSessionChanged()) {
                closeFrontmostSession();
                continue;
            }

            String name = sessionEditor.getName();

            int ret = JOptionPane.showConfirmDialog(
                    JOptionUtils.centeringComp(),
                    "Would you like to save the changes you made to " + name +
                            "?", "Advise needed...",
                    JOptionPane.YES_NO_CANCEL_OPTION);

            if (ret == JOptionPane.NO_OPTION) {
                closeFrontmostSession();
                continue;
            } else if (ret == JOptionPane.CANCEL_OPTION) {
                return false;
            }

            SaveSessionAsAction action = new SaveSessionAsAction();
            action.actionPerformed(new ActionEvent(this,
                    ActionEvent.ACTION_PERFORMED, "Dummy close action"));

            if (!action.isSaved()) {
                int ret2 = JOptionPane.showConfirmDialog(
                        JOptionUtils.centeringComp(),
                        "This session was not saved. Close session and continue anyway?",
                        "Advise needed...", JOptionPane.OK_CANCEL_OPTION);

                if (ret2 == JOptionPane.CANCEL_OPTION) {
                    return false;
                }
            }

            closeFrontmostSession();
        }

        return true;
    }

    public void putMetadata(SessionWrapperIndirectRef sessionWrapperRef,
                            TetradMetadataIndirectRef metadataRef) {
        SessionWrapper sessionWrapper = (SessionWrapper) sessionWrapperRef;
        TetradMetadata metadata = (TetradMetadata) metadataRef;

        this.metadataMap.put(sessionWrapper, metadata);
    }


    public TetradMetadataIndirectRef getTetradMetadata(
            SessionWrapperIndirectRef sessionWrapperRef) {
        SessionWrapper sessionWrapper = (SessionWrapper) sessionWrapperRef;

        return this.metadataMap.get(sessionWrapper);
    }


    /**
     * Sets whether the display log output should be displayed or not. If true
     * then a text area roughly 20% of the screen size will appear on the bottom
     * and will display any log output, otherwise just the standard tetrad
     * workbend is shown.
     */
    public void setDisplayLogging(boolean displayLogging) {
        if (displayLogging) {
            try {
                TetradLogger.getInstance().setNextOutputStream();
            } catch (IllegalStateException e2) {
//                    TetradLogger.getInstance().removeNextOutputStream();
                e2.printStackTrace();
                return;
            }

            this.logArea = new TetradLogArea(this);
        } else {
            if (this.logArea != null) {
                TetradLogger.getInstance().removeOutputStream(this.logArea.getOutputStream());
            }
            this.logArea = null;
        }
        setupDesktop();
        revalidate();
        repaint();

        Preferences.userRoot().putBoolean("displayLogging", displayLogging);
    }


    /**
     * States whether the desktop is currently displaying log output.
     *
     * @return - true iff the desktop is display log output.
     */
    public boolean isDisplayLogging() {
        return this.logArea != null;
    }

    //===========================PRIVATE METHODS==========================//


    /**
     * @return a reasonable divider location for the log output.
     */
    private int getDivider() {
        int height;
        if (desktopPane.getSize().height == 0) {
            Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
            height = size.height;
        } else {
            height = desktopPane.getSize().height;
        }
        return (int) (height * .80);
    }


    /**
     * Sets up the desktop components.
     */
    private void setupDesktop() {
        removeAll();
        if (this.logArea != null) {
            Border border = new CompoundBorder(new EmptyBorder(0, 2, 0, 2), new BevelBorder(BevelBorder.LOWERED));
            logArea.setBorder(border);
            JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, desktopPane, logArea);
            splitPane.setDividerSize(5);
            splitPane.setDividerLocation(getDivider());
            add(splitPane, BorderLayout.CENTER);
        } else {
            add(desktopPane, BorderLayout.CENTER);
        }
        JMenuBar menuBar = new TetradMenuBar(this);
        add(menuBar, BorderLayout.NORTH);
    }


    /**
     * @return true iff there exist a session in the desktop.
     */
    private boolean existsSession() {
        JInternalFrame[] allFrames = desktopPane.getAllFramesInLayer(0);

        for (JInternalFrame allFrame : allFrames) {
            Object o = allFrame.getContentPane().getComponents()[0];

            if (o instanceof SessionEditor) {
                return true;
            }
        }

        return false;
    }

    /**
     * Randomly picks the location of a new window, such that it fits completely
     * on the screen.
     *
     * @param desktopPane the desktop pane that the frame is being added to.
     * @param frame       the JInternalFrame which is being added.
     * @param desiredSize the desired dimensions of the frame.
     */
    private static void setGoodBounds(JInternalFrame frame, JDesktopPane desktopPane,
                                      Dimension desiredSize) {
        RandomUtil randomUtil = RandomUtil.getInstance();
        Dimension desktopSize = desktopPane.getSize();

        Dimension d = new Dimension(desiredSize);
        int tx = desktopSize.width - d.width;
        int ty = desktopSize.height - d.height;

        if (tx < 0) {
            tx = 0;
            d.width = desktopSize.width;
        } else {
            tx = (int) (randomUtil.nextDouble() * tx);
        }

        if (ty < 0) {
            ty = 0;
            d.height = desktopSize.height;
        } else {
            ty = (int) (randomUtil.nextDouble() * ty);
        }

        frame.setBounds(tx, ty, d.width, d.height);
    }

    /**
     * @return the next available session name in the series untitled1.tet,
     * untitled2.tet, etc.
     */
    private String getNewSessionName() {
        String base = "untitled";
        String suffix = ".tet";
        int i = 0;    // Sequence 1, 2, 3, ...

        loop:
        while (true) {
            i++;

            String name = base + i + suffix;

            for (Object _o : framesMap.keySet()) {
                if (_o instanceof SessionEditor) {
                    SessionEditor sessionEditor = (SessionEditor) _o;
                    SessionEditorWorkbench workbench =
                            sessionEditor.getSessionWorkbench();
                    SessionWrapper sessionWrapper =
                            workbench.getSessionWrapper();

                    if (sessionWrapper.getName().equals(name)) {
                        continue loop;
                    }
                }
            }

            return name;
        }
    }

    private Map<SessionEditor, JInternalFrame> getFramesMap() {
        return framesMap;
    }

    /**
     * States whether the log display should be automatically displayed, if
     * there is no value in the user's prefs then it will display a prompt
     * asking the user whether they would like to disable automatic popups.
     */
    private boolean allowAutomaticLogPopup() {
        Boolean allowed = TetradLogger.getInstance().isAutomaticLogDisplayEnabled();
        // ask the user whether they way the feature etc.
        if (allowed == null) {
            String message = "<html>Whenever Tetrad's logging features are active any generated log <br>" +
                    "output will be automatically display in Tetrad's log display. Would you like Tetrad<br>" +
                    "to continue to automatically open the log display window whenever there is logging output?</html>";
            int option = JOptionPane.showConfirmDialog(this, message, "Automatic Logging", JOptionPane.YES_NO_OPTION);
            if (option == JOptionPane.NO_OPTION) {
                JOptionPane.showMessageDialog(this, "This feature can be enabled later by going to Logging>Setup Logging.");
            }
            TetradLogger.getInstance().setAutomaticLogDisplayEnabled(option == JOptionPane.YES_OPTION);
            // return true, so that opens this time, in the future the user's pref will be used.
            return true;
        }

        return allowed;
    }

    //================================ Inner class =======================================//

    /**
     * Listener for the logger that will open the display log if not already
     * open.
     */
    private class LoggerListener implements TetradLoggerListener {

        public void configurationActived(TetradLoggerEvent evt) {
            TetradLoggerConfig config = evt.getTetradLoggerConfig();
            // if logging is actually turned on, then open display.
            if (TetradLogger.getInstance().isLogging() && config.isActive()
                    && TetradLogger.getInstance().isDisplayLogEnabled()) {
                // if the log display isn't already up, open it.
                if (!isDisplayLogging() && allowAutomaticLogPopup()) {
                    setDisplayLogging(true);
                }
            }
        }

        public void configurationDeactived(TetradLoggerEvent evt) {
            // do nothing.
        }


    }

    /**
     * A listener that prevents the display from moving too high in the
     * desktop.
     */
    private static class PositionListener extends ComponentAdapter {

        public void componentMoved(ComponentEvent evt) {
            Component component = evt.getComponent();
            Point point = component.getLocation();
            if (point.y < 0) {
                component.setBounds(point.x, 0, component.getWidth(), component.getHeight());
            }
        }

    }


}






