///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.study.gene.tetrad.gene.util;

import edu.cmu.tetrad.study.gene.tetrad.gene.history.BasicLagGraph;
import edu.cmu.tetrad.study.gene.tetrad.gene.history.LagGraph;
import edu.cmu.tetrad.study.gene.tetrad.gene.history.LaggedFactor;

import java.io.*;
import java.util.StringTokenizer;

/**
 * The purpose of this little converter is to convert "effect" files into "cause" (BasicLagGraph) files. It takes files
 * of the following form:
 * <pre>
 * cause effect1 effect2 effect3 ...
 * cause effect1 effect2 effect3 ...
 * </pre>
 * and converts them into LagGraphs.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class HandleyConvert {

    /**
     * Private constructor to prevent instantiation.
     */
    private HandleyConvert() {

    }

    /**
     * Converts the graph file from the moves line.
     *
     * @param args The expected argument is the filename of the graph file that has been saved out using the toString()
     *             method of the edu.cmu.genehistory.kernel.UpdateGraph class.
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Assumes that the graph is given by the buffered reader and prints the conversion of the grap to the printstream.
     *
     * @param in  the buffered reader containing the graph in the form of the main javadoc for this class.
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Assumes that the line consists of a cause followed by a number of effects of that cause, in whitespace delimited
     * format; adds the appropriate genes and lagged factors to the lagGraph.
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

}






