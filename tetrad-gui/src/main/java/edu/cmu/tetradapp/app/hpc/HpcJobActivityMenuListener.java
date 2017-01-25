package edu.cmu.tetradapp.app.hpc;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JDesktopPane;
import javax.swing.JLayeredPane;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import edu.cmu.tetradapp.app.TetradDesktop;

/**
 * 
 * Jan 24, 2017 5:35:10 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class HpcJobActivityMenuListener implements MenuListener, ActionListener {

    private HpcJobActivityFrame hpcJobActivityFrame = null;

    private final TetradDesktop desktop;

    public HpcJobActivityMenuListener(final TetradDesktop desktop) {
	this.desktop = desktop;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
	if (hpcJobActivityFrame == null) {
	    hpcJobActivityFrame = new HpcJobActivityFrame(desktop);

	    JDesktopPane desktopPane = desktop.getDesktopPane();

	    // Set the "small" size of the frame so that it has sensible
	    // bounds when the users unmazimizes it.
	    Dimension fullSize = desktopPane.getSize();
	    int smallSize = Math.min(fullSize.width, fullSize.height);
	    Dimension size = new Dimension(smallSize, smallSize);
	    TetradDesktop.setGoodBounds(hpcJobActivityFrame, desktopPane, size);
	    desktopPane.add(hpcJobActivityFrame);

	    // Set the frame to be maximized. This step must come after the
	    // frame is added to the desktop. -Raul. 6/21/01
	    try {
		hpcJobActivityFrame.setMaximum(true);
	    } catch (Exception exp) {
		throw new RuntimeException("Problem setting frame to max: "
			+ hpcJobActivityFrame);
	    }

	    desktopPane.setLayer(hpcJobActivityFrame,
		    JLayeredPane.DEFAULT_LAYER);

	    hpcJobActivityFrame.setVisible(true);

	    desktop.setMainTitle(hpcJobActivityFrame.getName());

	}
	hpcJobActivityFrame.moveToFront();
    }

    @Override
    public void menuSelected(MenuEvent e) {

    }

    @Override
    public void menuDeselected(MenuEvent e) {

    }

    @Override
    public void menuCanceled(MenuEvent e) {

    }

}
