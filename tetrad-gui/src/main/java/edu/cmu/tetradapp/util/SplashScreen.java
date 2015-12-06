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

package edu.cmu.tetradapp.util;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * A class that displays a splashScreen with a progress bar. Everything is
 * static so there can only be one Splash Screen. The usage is show,
 * (increment)* and hide.
 *
 * @author Juan Casares
 */
public class SplashScreen {
    private static int MAX;
    private static int COUNTER;
    private static SplashWindow WINDOW;

    public static void show(Frame parent, String title, int max) {
        hide();
        SplashScreen.COUNTER = 0;
        SplashScreen.MAX = max;
        WINDOW = new SplashWindow(parent, null, title);
    }

    public static void hide() {
        if (WINDOW == null) {
            return;
        }
        // show a complete bar for a short while
        WINDOW.bar.setValue(MAX);
        WINDOW.bar.repaint();

        try {
            Thread.sleep(2000);
        }
        catch (InterruptedException e) {
            // Ignore.
        }

        WINDOW.setVisible(false);
        WINDOW.dispose();
        WINDOW = null;
    }

    public static void increment() {
        increment(1);
    }

    public static void increment(int by) {
        COUNTER += by;
        if (COUNTER > MAX) {
            COUNTER = MAX;
        }
        if (WINDOW != null) {
            WINDOW.bar.setValue(COUNTER);
        }
    }

    private static class SplashWindow extends Window {
        Image splashIm;
        JProgressBar bar;

        SplashWindow(Frame parent, Image image, String title) {
            super(parent);
            this.splashIm = image;
            //setSize(200, 100);

            JPanel panel = new JPanel();
            panel.setBackground(Color.white);
            panel.setBorder(BorderFactory.createLineBorder(Color.black));
            panel.setLayout(new BorderLayout());
            add(panel, BorderLayout.CENTER);

            Box b = Box.createVerticalBox();
            panel.add(b, BorderLayout.CENTER);

            Box b1 = Box.createHorizontalBox();
            JLabel label = new JLabel(title, JLabel.CENTER);
            label.setFont(label.getFont().deriveFont((float) 16));
            b1.add(Box.createHorizontalGlue());
            b1.add(label);
            b1.add(Box.createHorizontalGlue());
            b.add(b1);

            JTextArea textArea = new JTextArea(LicenseUtils.copyright());
            textArea.setBorder(new EmptyBorder(5, 5, 5, 5));
            b.add(textArea);

            bar = new JProgressBar(0, MAX);
            bar.setBackground(Color.white);
            bar.setBorderPainted(false);
            b.add(bar);

            /* Center the WINDOW */
            pack();

            Dimension screenDim = Toolkit.getDefaultToolkit().getScreenSize();
            Rectangle bounds = getBounds();
            setLocation((screenDim.width - bounds.width) / 2,
                    (screenDim.height - bounds.height) / 2);


            setVisible(true);
            repaint();
        }

        // must move to panel
        public void paint(Graphics g) {
            super.paint(g);
            if (splashIm != null) {
                g.drawImage(splashIm, 0, 0, this);
            }
        }
    }
}






