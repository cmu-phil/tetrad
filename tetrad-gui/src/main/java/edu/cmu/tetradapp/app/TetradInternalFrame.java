package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.util.ImageUtils;

import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Extends JInternalFrame to ask the user if she wants to close the window.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TetradInternalFrame extends JInternalFrame {

    private static final long serialVersionUID = 907395289049591825L;

    /**
     * Constructs a new frame which will throw up a warning dialog if someone tries to close it.
     *
     * @param title the title of the frame.
     */
    public TetradInternalFrame(String title) {
        super(title, false, true, false, false);
        Image image = ImageUtils.getImage(this, "tyler16.png");
        setFrameIcon(new ImageIcon(image));

        this.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addInternalFrameListener(new InternalFrameAdapter() {

            /**
             * Throws up a warning dialog and then closes the frame if the user
             * says to.  Otherwise ignores the attempt.
             */
            public void internalFrameClosing(InternalFrameEvent e) {
                ActionEvent e2 = new ActionEvent(e.getSource(),
                        ActionEvent.ACTION_PERFORMED, "FrameClosing");

                CloseSessionAction closeSessionAction =
                        new CloseSessionAction();
                closeSessionAction.actionPerformed(e2);
            }
        });
    }
}





