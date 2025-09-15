package edu.cmu.tetradapp.app;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * This listener constructs a menu on the fly consisting of all titles of the internal frames currently on the desktop.
 * When the user selects one of these titles, the corresponding internal frame is moved to the front.
 *
 * @author josephramsey
 * @author Chirayu Kong Wongchokprasitti chw20@pitt.edu
 */
final class WindowMenuListener implements MenuListener, ActionListener {

    /**
     * The window menu that is constructed on the fly.
     */
    private final JMenu windowMenu;

    /**
     * A map from menu items to the internal frames they represent, used to determine which session editor to navigate
     * to.
     */
    private final Hashtable<JMenuItem, JInternalFrame> itemsToFrames;

    private final TetradDesktop desktop;

    /**
     * Constructs the window menu listener.  Requires to be told which object the window menu is and which object the
     * desktop pane is.
     *
     * @param windowMenu a {@link javax.swing.JMenu} object
     * @param desktop    a {@link edu.cmu.tetradapp.app.TetradDesktop} object
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
        this.itemsToFrames = new Hashtable<>();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Required for the MenuListener interface; unused.
     */
    public void menuCanceled(MenuEvent e) {
    }

    /**
     * {@inheritDoc}
     * <p>
     * Required for the MenuListener interface; unused.
     */
    public void menuDeselected(MenuEvent e) {
    }

    /**
     * {@inheritDoc}
     * <p>
     * Reacts when the window menu is selected by constructing a menu on the fly consisting of an alphabetized list of
     * sessoin editors. The user can navigate to any session editor by selecting its name from the list.
     */
    public void menuSelected(MenuEvent e) {

        this.windowMenu.removeAll();
        this.itemsToFrames.clear();

        JInternalFrame[] layer0Frames = this.desktop.getDesktopPane().getAllFramesInLayer(0);
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

        for (String title1 : titles) {
            JMenuItem item = new JMenuItem(title1);
            this.windowMenu.add(item);
            item.addActionListener(this);
            this.itemsToFrames.put(item, titlesToFrames.get(title1));
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Reacts to selections of menu items in the window menu by moving their corresponding internal frames to the
     * front.
     */
    public void actionPerformed(ActionEvent e) {
        System.out.println(e.getActionCommand());
        Object item = e.getSource();
        JInternalFrame frame = this.itemsToFrames.get(item);
        frame.moveToFront();
        if (frame.getContentPane().getComponents().length > 0) {
            this.desktop.setMainTitle(frame.getContentPane().getComponent(0).getName());
        }
    }

    /**
     * ???
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "Some WindowMenuListener.";
    }
}





