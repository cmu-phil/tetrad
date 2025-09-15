package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.util.LicenseUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Closes the frontmost session of the given desktop.
 *
 * @author josephramsey
 */
final class WarrantyAction extends AbstractAction {

    /**
     * Creates a new close session action for the given desktop.
     */
    public WarrantyAction() {
        super("Warranty");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Closes the frontmost session of this action's desktop.
     */
    public void actionPerformed(ActionEvent e) {
        String license = LicenseUtils.license();

        int index = license.indexOf("SUCH DAMAGES");

        JTextArea textArea = new JTextArea(license);
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(textArea);
        scroll.setPreferredSize(new Dimension(600, 400));
        textArea.setCaretPosition(index);

        Box b = Box.createVerticalBox();
        b.add(scroll);

        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), b,
                "Warranty", JOptionPane.PLAIN_MESSAGE);
    }
}



