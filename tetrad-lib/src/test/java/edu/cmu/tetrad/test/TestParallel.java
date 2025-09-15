/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class TestParallel {

    public static void main(String[] args) {
        Thread mainThread = Thread.currentThread();

        // Create a separate thread to interrupt the main thread after 1 second
        Thread interrupter = new Thread(() -> {
            try {
                Thread.sleep(1000); // Wait for 1 second
                System.out.println("Interrupting main thread...");
                mainThread.interrupt(); // Interrupt the main thread
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        interrupter.start(); // Start the interrupter thread
        AtomicInteger count = new AtomicInteger(0);

        try {
            IntStream.range(0, 100000000)
                    .parallel()
                    .forEach(i -> {

                        // Check for main thread interruption
                        if (mainThread.isInterrupted()) {
                            Thread.currentThread().interrupt(); // Propagate to current thread
                            return; // Stop processing
                        }

                        // Check for current thread interruption
                        if (Thread.currentThread().isInterrupted()) {
                            mainThread.interrupt(); // Propagate back to main thread
                            return; // Stop processing
                        }

                        // Perform some long-running task
                        Graph graph = RandomGraph.randomGraph(10, 0, 10, 100, 100, 100, false);
                        SemPm pm = new SemPm(graph);
                        SemIm im = new SemIm(pm);
                        var data = im.simulateData(1000, false);

                        count.incrementAndGet();

                        System.out.println("Processing: " + i);
                    });
        } catch (Exception e) {
            System.out.println("Exception caught: " + e.getMessage());
        } finally {
            System.out.println("Finished processing: count = " + count.get());
        }
    }
}





