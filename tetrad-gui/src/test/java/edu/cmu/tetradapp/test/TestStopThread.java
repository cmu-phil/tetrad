///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

package edu.cmu.tetradapp.test;

import edu.cmu.tetradapp.util.WatchedProcess2;

import javax.swing.*;

/**
 * Implements basic tests of the choice generator. The choice generator should visit every choice in a choose b exactly
 * once, and then return null.
 *
 * @author josephramsey
 */
public class TestStopThread {

    public static void main(String[] args) {
        class MyWatchedProcess extends WatchedProcess2 {
            @Override
            public void watch() throws InterruptedException {
                Thread.sleep(1000);
            }
        };

        SwingUtilities.invokeLater(MyWatchedProcess::new);
    }

//        public static class WatchedProcess3 {
//        private final JFrame frame;
//        private final JButton startButton;
//        private Thread longRunningThread;
//
//        public WatchedProcess3() {
//            frame = new JFrame("Thread Stop Example");
//            startButton = new JButton("Start");
//
//            startButton.addActionListener(e -> startLongRunningThread());
//
//            JPanel panel = new JPanel();
//            panel.add(startButton);
//
//            frame.getContentPane().add(panel);
//            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//            frame.setSize(300, 100);
//            frame.setVisible(true);
//        }
//
//        private void startLongRunningThread() {
//            startButton.setEnabled(false);  // Disable the start button
//
//            longRunningThread = new Thread(() -> {
//                // Long running process logic...
//                for (int i = 1; i <= 10; i++) {
//                    if (Thread.interrupted()) {
//                        // Thread was interrupted, so exit the loop and terminate
//                        System.out.println("Thread was interrupted. Stopping...");
//                        return;
//                    }
//
//                    System.out.println("Processing iteration " + i);
//                    try {
//                        Thread.sleep(1000);
//                    } catch (InterruptedException e) {
//                        // Thread was interrupted while sleeping, so exit the loop and terminate
//                        System.out.println("Thread was interrupted while sleeping. Stopping...");
//                        return;
//                    }
//                }
//
//                // Process completed successfully
//                System.out.println("Process completed successfully.");
//            });
//
//            longRunningThread.start();
//
//            showStopDialog();
//        }
//
//        private void stopLongRunningThread() {
//            if (longRunningThread != null && longRunningThread.isAlive()) {
//                longRunningThread.interrupt();
//            }
//
//            startButton.setEnabled(true);   // Enable the start button
//        }
//
//        private void showStopDialog() {
//            JDialog dialog = new JDialog(frame, "Stop Process", Dialog.ModalityType.APPLICATION_MODAL);
//            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
//            dialog.setSize(200, 100);
//            dialog.setResizable(false);
//
//            JButton stopButton = new JButton("Stop");
//            stopButton.addActionListener(new ActionListener() {
//                @Override
//                public void actionPerformed(ActionEvent e) {
//                    stopLongRunningThread();
//                    dialog.dispose();
//                }
//            });
//
//            JPanel panel = new JPanel();
//            panel.add(stopButton);
//            dialog.getContentPane().add(panel);
//
//            dialog.setLocationRelativeTo(frame);
//            dialog.setVisible(true);
//        }
//    }

    WatchedProcess2 process = new WatchedProcess2() {
        @Override
        public void watch() throws InterruptedException {
            Thread.sleep(1000);
        }
    };
}





