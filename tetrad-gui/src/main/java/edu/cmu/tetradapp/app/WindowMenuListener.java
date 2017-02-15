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

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import edu.cmu.tetradapp.app.hpc.action.HpcJobActivityAction;
import edu.cmu.tetradapp.app.hpc.manager.HpcAccountManager;
import edu.pitt.dbmi.tetrad.db.entity.HpcAccount;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * This listener constructs a menu on the fly consisting of all titles of the
 * internal frames currently on the desktop.  When the user selects one of these
 * titles, the corresponding internal frame is moved to the front.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 * @author Chirayu Kong Wongchokprasitti chw20@pitt.edu
 */
final class WindowMenuListener implements MenuListener, ActionListener {

    /**
     * The window menu that is constructed on the fly.
     */
    private JMenu windowMenu;

    /**
     * The desktop pane that the session editors presented by
     * <code>windowMenu</code> are situated.
     */
    //private final JDesktopPane desktopPane;

    /**
     * A map from menu items to the internal frames they represent, used to
     * determine which session editor to navigate to.
     */
    private Hashtable<JMenuItem, JInternalFrame> itemsToFrames;

    private final TetradDesktop desktop;
    
    /**
     * Constructs the window menu listener.  Requires to be told which object
     * the window menu is and which object the desktop pane is.
     */
    public WindowMenuListener(JMenu windowMenu, TetradDesktop desktop) {

        if (windowMenu == null) {
            throw new NullPointerException("Window menu must not be null.");
        }

        
        
        if (desktop == null) {
            throw new NullPointerException("Desktop pane must not be null.");
        }

        this.windowMenu = windowMenu;
        this.desktop = desktop;
        itemsToFrames = new Hashtable<>();
    }

    /**
     * Required for the MenuListener interface; unused.
     *
     * @param e the menu event.
     */
    public void menuCanceled(MenuEvent e) {
    }

    /**
     * Required for the MenuListener interface; unused.
     */
    public void menuDeselected(MenuEvent e) {
    }

    /**
     * Reacts when the window menu is selected by constructing a menu on the fly
     * consisting of an alphabetized list of sessoin editors. The user can
     * navigate to any session editor by selecting its name from the list.
     *
     * @param e the menu event indicating that the window menu has been
     *          selected.
     */
    public void menuSelected(MenuEvent e) {

        windowMenu.removeAll();
        itemsToFrames.clear();

        JInternalFrame[] layer0Frames = desktop.getDesktopPane().getAllFramesInLayer(0);
        List<String> titles = new ArrayList<>();
        Map<String, JInternalFrame> titlesToFrames = new HashMap<>();

        for (JInternalFrame layer0Frame : layer0Frames) {
            String title = layer0Frame.getTitle();
            title = ((title == null) ||
                    title.equals("")) ? "[untitled]" : title;
            titles.add(title);
            titlesToFrames.put(title, layer0Frame);
        }

        Collections.sort(titles);

        for (Object title1 : titles) {
            String title = (String) title1;
            JMenuItem item = new JMenuItem(title);
            this.windowMenu.add(item);
            item.addActionListener(this);
            this.itemsToFrames.put(item, titlesToFrames.get(title));
        }
        
        // If there are hpc account(s) setup, show the hpc activity
        HpcAccountManager hpcAccountManager = desktop.getHpcAccountManager();
        List<HpcAccount> hpcAccounts = hpcAccountManager.getHpcAccounts();
        if(hpcAccounts != null && !hpcAccounts.isEmpty()){
            this.windowMenu.addSeparator();
            String title = "HPC Job Activity";
            JMenuItem item = new JMenuItem(new HpcJobActivityAction(title));
            this.windowMenu.add(item);
            //item.addActionListener(hpcJobActivityMenuListener);
        }
    }

    /**
     * Reacts to selections of menu items in the window menu by moving their
     * corresponding internal frames to the front.
     *
     * @param e the action event indicating which internal frame should be moved
     *          to the front.
     */
    public void actionPerformed(ActionEvent e) {
        System.out.println(e.getActionCommand());
        Object item = e.getSource();
        JInternalFrame frame = (JInternalFrame) itemsToFrames.get(item);
        frame.moveToFront();
        if(frame.getContentPane().getComponents().length > 0){
            desktop.setMainTitle(frame.getContentPane().getComponent(0).getName());
        }
    }

    /**
     * ???
     */
    public String toString() {
        return "Some WindowMenuListener.";
    }
}





