package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Version;
import edu.cmu.tetradapp.util.LicenseUtils;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Closes the frontmost session of the given desktop.
 *
 * @author josephramsey
 */
final class AboutTetradAction extends AbstractAction {

    /**
     * Creates a new close session action for the given desktop.
     */
    public AboutTetradAction() {
        super("About Tetrad " + Version.currentViewableVersion());
    }

    /**
     * Performs the action when an event is triggered.
     *
     * @param e the event to be processed
     */
    public void actionPerformed(ActionEvent e) {
        Box b1 = Box.createVerticalBox();
        Version currentVersion = Version.currentViewableVersion();

        String copyright = LicenseUtils.copyright();
        copyright = copyright.replaceAll("\n", "<br>");

        JLabel label = new JLabel();
        label.setText("<html>" + "<b>Tetrad " + currentVersion + "</b>" +
                      "<br>" +
                      "<br>Laboratory for Symbolic and Educational Computing" +
                      "<br>Department of Philosophy" +
                      "<br>Carnegie Mellon University" + "<br>" +
                      "<br>Project Direction: Clark Glymour, Richard Scheines, Peter Spirtes" +
                      "<br>Lead Developer: Joseph Ramsey" +
                      "<br>" + copyright + "</html>"

        );
        label.setBackground(Color.LIGHT_GRAY);
        label.setFont(new Font("Dialog", Font.PLAIN, 12));
        label.setBorder(new CompoundBorder(new LineBorder(Color.DARK_GRAY),
                new EmptyBorder(10, 10, 10, 10)));

        b1.add(label);

        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), b1,
                "About Tetrad...", JOptionPane.PLAIN_MESSAGE);
    }
}





