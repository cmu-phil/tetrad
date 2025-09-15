package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.util.JOptionUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Closes the frontmost session of the given desktop.
 *
 * @author josephramsey
 */
final class ContributorsAction extends AbstractAction {

    /**
     * Creates a new close session action for the given desktop.
     */
    public ContributorsAction() {
        super("Contributors");
    }

    /**
     * Displays a message dialog showing a list of contributors.
     *
     * @param e the event to be processed
     */
    public void actionPerformed(ActionEvent e) {
        String msg = "This project has been worked on for many years under the generous " +
                     "auspices of the Philosophy Department at Carnegie Mellon University, under " +
                     "the direction of Clark Glymour (Philosophy, CMU), Peter Spirtes (Philosophy, " +
                     "CMU) and Richard Scheines (Philosophy, CMU, now Dean of the College of Humanities " +
                     "and Social Sciences at CMU). The lead developer has been Joseph Ramsey (Philosophy, " +
                     "CMU). Recent work has been done under the direction of Greg Cooper (Bioinformatics, " +
                     "University of Pittsburgh) in conjunction with a grant from NSF establishing the " +
                     "Center for Causal Discovery, with additional advice from Kun Zhang (Philosophy, " +
                     "CMU), The team under the NSF grant consisted of J Espino, Kevin Bui, Zhou Yuan, " +
                     "Kong Wongchakprasitti, and Harry Hochheiser. Additional work has been done by Bryan " +
                     "Andrews, Ruben Sanchez, Fattaneh Jabbari, Ricardo Silva, Dan Malinsky, Erich Kummerfeld, " +
                     "Biwei Huang, Juan Miguel Ogarrio, David Danks, Kevin Kelly, Eric Strobl, Shyam Visweswaran, " +
                     "Shuyan Wang, Madelyn Glymour, Frank Wimberly, Matt Easterday, and Tyler Gibson, and " +
                     "Mike Konrad (Software Engineering Institute, CMU). " +
                     "This material is based, in part, upon work funded and supported by the Department of Defense under " +
                     "Contract No. FA8702-15-D-0002 with Carnegie Mellon University for the operation of the " +
                     "Software Engineering Institute, a federally funded research and development center.";

        JTextArea textArea = new JTextArea(msg);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(textArea);
        scroll.setPreferredSize(new Dimension(400, 250));

        Box b = Box.createVerticalBox();
        b.add(scroll);

        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), b,
                "Contributors", JOptionPane.PLAIN_MESSAGE);
    }
}



