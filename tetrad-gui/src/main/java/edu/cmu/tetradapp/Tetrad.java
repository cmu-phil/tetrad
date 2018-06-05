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
package edu.cmu.tetradapp;

import edu.cmu.tetrad.graph.NodeEqualityMode;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Version;
import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.util.SplashScreen;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Locale;
import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 * <p>
 * Launches Tetrad as an application. The intended class path in either case is
 * "edu.cmu.tetradapp.Tetrad", so care should be taken not to move this class
 * out of the "INSTANCE" package. The launch itself is carried out by the method
 * "launchFrame()", which generates a new frame for the application.</p>
 * <p>
 * Note to programmers: <b>Please don't make any changes to this class.</b>
 * If you need another way of launching Tetrad for special purposes, it's easy
 * enough to create a copy of this class with a different name and modify
 * it.</p>
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public final class Tetrad implements PropertyChangeListener {

    /**
     * The launch frame.
     */
    private JFrame frame;

    /**
     * The desktop placed into the launch frame.
     */
    private TetradDesktop desktop;

    /**
     * The main application title.
     */
    private final String mainTitle
            = "Tetrad " + Version.currentViewableVersion()
                    .toString();

    /**
     * Skip latest version checking
     */
    private static boolean skipLatest;

    //==============================CONSTRUCTORS===========================//
    public Tetrad() {
    }

    //==============================PUBLIC METHODS=========================//
    /**
     * Responds to "exitProgram" property change events by disposing of the
     * Tetrad IV frame and exiting if possible.
     *
     * @param e the property change event
     */
    public void propertyChange(final PropertyChangeEvent e) {
        if ("exitProgram".equals(e.getPropertyName())) {
            exitApplication();
        }
    }

    /**
     * <p>
     * Launches Tetrad as an application. One way to launch Tetrad IV as an
     * application is the following:</p>
     * <pre>java -cp jarname.jar INSTANCE.Tetrad</pre>
     * <p>
     * where "jarname.jar" is a jar containing all of the classes of Tetrad IV,
     * properly compiled, along with all of the auxiliary jar contents and all
     * of the images which Tetrad IV uses, all in their proper relative
     * directories.</p>
     *
     * @param argv --skip-latest argument will skip checking for latest version.
     */
    public static void main(final String[] argv) {

        // Avoid updates to swing code that causes comparison-method-violates-its-general-contract warnings
        System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");

        setLookAndFeel();

        // This is needed to get numbers to be parsed and rendered uniformly, especially in the interface.
        Locale.setDefault(Locale.US);

        // Check if we should skip checking for latest version
        skipLatest = argv.length > 0 && argv[0] != null && argv[0].compareToIgnoreCase("--skip-latest") == 0;
        SplashScreen.show("Loading Tetrad...", 1000, skipLatest);
        EventQueue.invokeLater(() -> {
            new Tetrad().launchFrame();
        });
    }

    //===============================PRIVATE METHODS=======================//
    private static void setLookAndFeel() {
        try {
            String os = System.getProperties().getProperty("os.name");
            if (os.equals("Windows XP")) {
                // The only system look and feel that seems to work well is the
                // one for Windows XP. When running on Mac the mac look and
                // feel is forced. The new look (synth or whatever its called)
                // and feel for linux on 1.5 looks
                // pretty bad so it shouldn't be used.
                // By default linux will use the metal look and feel.
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Launches the frame. (This is left as a separate method in case we ever
     * want to launch it as an applet.)
     */
    private void launchFrame() {
        System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
        NodeEqualityMode.setEqualityMode(NodeEqualityMode.Type.OBJECT);

        // Set up the desktop.
        this.desktop = new TetradDesktop();
        getDesktop().addPropertyChangeListener(this);
        JOptionUtils.setCenteringComp(getDesktop());
        DesktopController.setReference(getDesktop());

        // Set up the frame. Note the order in which the next few steps
        // happen. First, the frame is given a preferred size, so that if
        // someone unmaximizes it it doesn't shrivel up to the top left
        // corner. Next, the content pane is set. Next, it is packed. Finally,
        // it is maximized. For some reason, most of the details of this
        // order are important. jdramsey 12/14/02
        this.frame = new JFrame(this.mainTitle) {

            private static final long serialVersionUID = -9077349253115802418L;

            @Override
            public Dimension getPreferredSize() {
                Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
                double minLength = Math.min(size.getWidth(), size.getHeight());
                double height = minLength * 0.8;
                double width = height * (4.0 / 3);

                return new Dimension((int) width, (int) height);
//                return Toolkit.getDefaultToolkit().getScreenSize();

//                Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
//                return new Dimension(size.width - 100, size.height - 100);
            }

//            public Dimension getMinimumSize() {
//                return Toolkit.getDefaultToolkit().getScreenSize();
////                return new Dimension(400, 400);
//            }
//////
//            public Dimension getMaximumSize() {
//                return Toolkit.getDefaultToolkit().getScreenSize();
//            }
        };

        // Fixing a bug caused by switch to Oracle Java (at least for Mac), although I must say the following
        // code is what should have worked to begin with. Bug was that sessions would appear only in the lower
        // left hand corner of the screen.
        this.frame.setPreferredSize(Toolkit.getDefaultToolkit().getScreenSize());
//        this.frame.setMinimumSize(Toolkit.getDefaultToolkit().getScreenSize());
//        this.frame.setMaximumSize(Toolkit.getDefaultToolkit().getScreenSize());

        getFrame().setContentPane(getDesktop());
        getFrame().pack();
        getFrame().setLocationRelativeTo(null);

        // This doesn't let the user resize the main window.
//        getFrame().setExtendedState(Frame.MAXIMIZED_BOTH);
        Image image = ImageUtils.getImage(this, "tyler16.png");
        getFrame().setIconImage(image);

        // Add an initial session editor to the desktop. Must be done
        // from here, not in the constructor of TetradDesktop.
        getDesktop().newSessionEditor();
        getFrame().setVisible(true);
        getFrame().setDefaultCloseOperation(
                WindowConstants.DO_NOTHING_ON_CLOSE);

        getFrame().addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                exitApplication();
            }
        });

        SplashScreen.hide();

//        try {
//            Preferences.userRoot().clear();
//        } catch (BackingStoreException e) {
//            e.printStackTrace();
//        }
    }

    /**
     * Exits the application gracefully.
     */
    private void exitApplication() {
        boolean succeeded = getDesktop().closeAllSessions();

        if (!succeeded) {
            return;
        }

        getFrame().setVisible(false);
        getFrame().dispose();
        TetradLogger.getInstance().removeNextOutputStream();

        try {
            System.exit(0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JFrame getFrame() {
        return frame;
    }

    private TetradDesktop getDesktop() {
        return desktop;
    }

}
