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

package edu.cmu.tetrad.gene.tetrad.gene.util;

import edu.cmu.tetrad.gene.tetrad.gene.history.BasicLagGraph;
import edu.cmu.tetrad.gene.tetrad.gene.history.LagGraph;
import edu.cmu.tetrad.gene.tetrad.gene.history.LaggedFactor;

import java.io.*;
import java.util.StringTokenizer;

/**
 * The purpose of this little converter is to convert "effect" files into
 * "cause" (BasicLagGraph) files. It takes files of the following form:
 * <pre>
 * cause effect1 effect2 effect3 ...
 * cause effect1 effect2 effect3 ...
 * </pre>
 * and converts them into LagGraphs.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class HandleyConvert {

    /**
     * Assumes that the graph is given by the buffered reader and prints the
     * conversion of the grap to the printstream.
     *
     * @param in  the buffered reader containing the graph in the form of the
     *            main javadoc for this class.
     * @param out the printstream to write the converted graph to.
     */
    private void convert(BufferedReader in, PrintStream out) {
        try {
            LagGraph lagGraph = new BasicLagGraph();
            String line = null;

            while ((line = in.readLine()) != null) {
                addEdges(line, lagGraph);
            }
            out.print(lagGraph);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Assumes that the line consists of a cause followed by a number of effects
     * of that cause, in whitespace delimited format; adds the appropriate genes
     * and lagged factors to the lagGraph.
     *
     * @param line the input line described above.
     */
    private void addEdges(String line, LagGraph lagGraph) {
        StringTokenizer st = new StringTokenizer(line);
        String cause = st.nextToken();

        while (st.hasMoreTokens()) {
            String effect = st.nextToken();
            if (effect == "") {
                continue;
            }

            System.out.println(cause + " --> " + effect);

            lagGraph.addFactor(effect);
            lagGraph.addEdge(effect, new LaggedFactor(cause, 1));
        }
    }

    /**
     * Converts the graph file from the moves line.
     *
     * @param args The expected argument is the filename of the graph file that
     *             has been saved out using the toString() method of the
     *             edu.cmu.genehistory.kernel.UpdateGraph class.
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expecting exactly one " +
                    "argument, the filename " + "of the file to translate.");
        }

        try {
            File inFile = new File(args[0]);
            File outFile = new File(inFile.getName() + ".out");
            BufferedReader in = new BufferedReader(new FileReader(inFile));
            PrintStream out = new PrintStream(new FileOutputStream(outFile));

            new HandleyConvert().convert(in, out);
            out.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

}





