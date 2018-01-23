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

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.app.hpc.action.HpcAccountSettingAction;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.SessionEditorIndirectRef;

import javax.help.*;
import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.net.URL;

/**
 * The main menubar for Tetrad.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 * @author Chirayu Kong Wongchokprasitti chw20@pitt.edu
 */
final class TetradMenuBar extends JMenuBar {

    /**
     * 
     */
    private static final long serialVersionUID = -2734606481426217430L;
    
    /**
     * A reference to the tetrad desktop.
     */
    private final TetradDesktop desktop;


    /**
     * Creates the main menubar for Tetrad.
     */
    public TetradMenuBar(TetradDesktop desktop) {
        this.desktop = desktop;
        setBorder(new EtchedBorder());

        // create the menus and add them to the menubar
        JMenu fileMenu = new JMenu("File");
        JMenu editMenu = new JMenu("Edit");
        JMenu loggingMenu = new JMenu("Logging");
        JMenu templateMenu = new JMenu("Pipelines");
        JMenu windowMenu = new JMenu("Window");
        JMenu helpMenu = new JMenu("About");

        add(fileMenu);
        add(editMenu);
        add(loggingMenu);
        add(templateMenu);
        add(windowMenu);
        add(helpMenu);
        
        buildFileMenu(fileMenu);
        buildEditMenu(editMenu);
        buildLoggingMenu(loggingMenu);
        buildTemplateMenu(templateMenu);
        buildWindowMenu(windowMenu);
        buildHelpMenu(helpMenu);
    }

    //================================ Private Method ===============================//

    private void buildFileMenu(final JMenu fileMenu) {
        //=======================FILE MENU=========================//

        // These have to be wrapped in JMenuItems to get the keyboard
        // accelerators to work correctly.
        JMenuItem newSession = new JMenuItem(new NewSessionAction());
        JMenuItem loadSession = new JMenuItem(new LoadSessionAction());
        JMenuItem closeSession = new JMenuItem(new CloseSessionAction());
        JMenuItem saveSession = new JMenuItem(new SaveSessionAction());

        fileMenu.add(newSession);
        fileMenu.add(loadSession);
        fileMenu.add(closeSession);
//        fileMenu.addSeparator();
//        fileMenu.add(templateMenu);
//
        
//      //=======================EXAMPLES MENU=========================//
//      // Build a LoadTemplateAction for each file name in
//      // this.exampleFiles.
//      String[] templateNames = ConstructTemplateAction.getTemplateNames();
//      for (String templateName : templateNames) {
//          if ("--separator--".equals(templateName)) {
//              fileMenu.addSeparator();
//          } else {
//              ConstructTemplateAction action =
//                      new ConstructTemplateAction(templateName);
//              fileMenu.add(action);
//          }
//      }

      fileMenu.addSeparator();
      fileMenu.add(saveSession);
      fileMenu.add(new SaveSessionAsAction());
      fileMenu.addSeparator();
      fileMenu.add(new SessionVersionAction());
      fileMenu.addSeparator();
//      fileMenu.add(new SaveScreenshot(desktop, true, "Save Screenshot..."));

      final JMenuItem menuItem = new JMenuItem("Save Session Workspace Image...");
      menuItem.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
              SessionEditorIndirectRef editorRef =
                      DesktopController.getInstance().getFrontmostSessionEditor();
              SessionEditor editor = (SessionEditor) editorRef;
              editor.saveSessionImage();
          }
      });

      fileMenu.add(menuItem);
      fileMenu.addSeparator();

      JMenu settingsMenu = new JMenu("Settings");
      JMenuItem loggingSettingMenuItem = new JMenuItem(new SetupLoggingAction());
      JMenuItem hpcAccountSettingMenuItem = new JMenuItem(new HpcAccountSettingAction());
      settingsMenu.add(loggingSettingMenuItem);
      settingsMenu.add(hpcAccountSettingMenuItem);
      fileMenu.add(settingsMenu);
      fileMenu.addSeparator();

      JMenuItem exit = new JMenuItem(new ExitAction());
      fileMenu.add(exit);
      exit.setAccelerator(
              KeyStroke.getKeyStroke(KeyEvent.VK_Q, ActionEvent.CTRL_MASK));

      newSession.setAccelerator(
              KeyStroke.getKeyStroke(KeyEvent.VK_N, ActionEvent.CTRL_MASK));
      loadSession.setAccelerator(
              KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK));
      saveSession.setAccelerator(
              KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK));
      closeSession.setAccelerator(
              KeyStroke.getKeyStroke(KeyEvent.VK_W, ActionEvent.CTRL_MASK));

    }

    private void buildEditMenu(final JMenu editMenu){
        //=======================EDIT MENU=========================//
        JMenuItem cut = new JMenuItem(new CutSubsessionAction());
        JMenuItem copy = new JMenuItem(new CopySubsessionAction());
        JMenuItem paste = new JMenuItem(new PasteSubsessionAction());
        JMenuItem numberFormat = new JMenuItem(new NumberFormatAction());

        cut.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_X, ActionEvent.CTRL_MASK));
        copy.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
        paste.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK));

        editMenu.add(cut);
        editMenu.add(copy);
        editMenu.add(paste);
        editMenu.addSeparator();
        editMenu.add(numberFormat);
    }
    
    
    /**
     * Builds the logging menu
     */
    private void buildLoggingMenu(JMenu loggingMenu) {
        //================================= Logging Menu ==========================//

        // build the logging menu on the fly.
        loggingMenu.addMenuListener(new LoggingMenuListener());        
    }

    private void buildTemplateMenu(final JMenu templateMenu){
//      //=======================EXAMPLES MENU=========================//
//      // Build a LoadTemplateAction for each file name in
//      // this.exampleFiles.
      String[] templateNames = ConstructTemplateAction.getTemplateNames();
      for (String templateName : templateNames) {
          if ("--separator--".equals(templateName)) {
              templateMenu.addSeparator();
          } else {
              ConstructTemplateAction action =
                      new ConstructTemplateAction(templateName);
              templateMenu.add(action);
          }
      }

    }
    
    private void buildWindowMenu(final JMenu windowMenu){
        //=======================WINDOW MENU=========================//
        // These items are created on the fly based on whatever session
        // editors are available.
        WindowMenuListener windowMenuListener =
                new WindowMenuListener(windowMenu, desktop);
        windowMenu.addMenuListener(windowMenuListener);
    }    
    
    private void buildHelpMenu(final JMenu helpMenu){
        //=======================HELP MENU=========================//
        // A reference to the help item is stored at class level so that
        // it can be "clicked" from other classes.

//        String helpHS = "/resources/javahelp/TetradHelp.hs";
//        HelpSet hs;
//
//        try {
//            URL url = this.getClass().getResource(helpHS);
//            hs = new HelpSet(null, url);
//        } catch (Exception ee) {
//            System.out.println("HelpSet " + ee.getMessage());
//            System.out.println("HelpSet " + helpHS + " not found");
//            return;
//        }
//
//        final HelpBroker hb = hs.createHelpBroker();
//
//        JMenuItem help = new JMenuItem("Tetrad Help");
//        help.addActionListener(new CSH.DisplayHelpFromSource(hb));
////        helpMenu.add(help);
//
//        help.addActionListener(new CSH.DisplayHelpFromSource(hb));

        helpMenu.add(new AboutTetradAction());
        helpMenu.add(new WarrantyAction());
        helpMenu.add(new LicenseAction());

    }
    
    
    //========================= Inner Classes ==========================================//

    private class LoggingMenuListener implements MenuListener {

        public void menuSelected(MenuEvent e) {
            final JMenu loggingMenu = (JMenu) e.getSource();
            
            loggingMenu.removeAll();
            // check box to turn logging on/off
//            JMenuItem loggingState = new JMenuItem();
//            loggingState.setText(TetradLogger.getInstance().isLogging() ? "Turn Logging Off" : "Turn Logging On");
            //check box to set whether logging should be displayed or not
            JMenuItem displayLogging = new JMenuItem();
            displayLogging.setText(desktop.isDisplayLogging() ? "Stop Logging" : "Start Logging");

            loggingMenu.add(displayLogging);
//            loggingMenu.add(new SetupLoggingAction());
//            loggingMenu.add(loggingState);


            displayLogging.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    JMenuItem item = (JMenuItem) e.getSource();
                    String text = item.getText();
                    boolean logging = text.contains("Start");
                    desktop.setDisplayLogging(logging);
                    TetradLogger.getInstance().setLogging(true);
                    item.setText(logging ? "Start Logging" : "Stop Logging");
                }
            });


//            loggingState.addActionListener(new ActionListener() {
//                public void actionPerformed(ActionEvent e) {
//                    JMenuItem item = (JMenuItem) e.getSource();
//                    String text = item.getText();
//                    TetradLogger.getInstance().setLogging(text.contains("On"));
//                    item.setText(TetradLogger.getInstance().isLogging() ? "Turn Logging Off" : "Turn Logging On");
//                }
//            });

        }

        public void menuDeselected(MenuEvent e) {
        }

        public void menuCanceled(MenuEvent e) {
        }
    }
}





