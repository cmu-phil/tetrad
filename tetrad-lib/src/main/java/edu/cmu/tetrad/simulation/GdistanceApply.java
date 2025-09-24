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

package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphSaveLoadUtils;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDatasetFileReader;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Created by Erich on 7/14/2016.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GdistanceApply {

    /**
     * Private constructor to prevent instantiation.
     */
    private GdistanceApply() {
    }

    /**
     * <p>main.</p>
     *
     * @param args a {@link java.lang.String} object
     */
    public static void main(String... args) {
        final double xdist = 2.4;
        final double ydist = 2.4;
        final double zdist = 2;
        long timestart = System.nanoTime();
        System.out.println("Loading first graph");
        Graph graph1 = GraphSaveLoadUtils.loadGraphTxt(new File("Motion_Corrected_Graphs/singlesub_motion_graph_025_04.txt"));
        long timegraph1 = System.nanoTime();
        //System.out.println(graph1);
        System.out.println("Done loading first graph. Elapsed time: " + (timegraph1 - timestart) / 1000000000 + "s");
        System.out.println("Loading second graph");
        Graph graph2 = GraphSaveLoadUtils.loadGraphTxt(new File("Motion_Corrected_Graphs/singlesub_motion_graph_027_04.txt"));
        long timegraph2 = System.nanoTime();
        System.out.println("Done loading second graph. Elapsed time: " + (timegraph2 - timegraph1) / 1000000000 + "s");

        //+++++++++ these steps are specifically for the motion corrected fMRI graphs ++++++++++++
        graph1.removeNode(graph1.getNode("Motion_1"));
        graph1.removeNode(graph1.getNode("Motion_2"));
        graph1.removeNode(graph1.getNode("Motion_3"));
        graph1.removeNode(graph1.getNode("Motion_4"));
        graph1.removeNode(graph1.getNode("Motion_5"));
        graph1.removeNode(graph1.getNode("Motion_6"));

        graph2.removeNode(graph2.getNode("Motion_1"));
        graph2.removeNode(graph2.getNode("Motion_2"));
        graph2.removeNode(graph2.getNode("Motion_3"));
        graph2.removeNode(graph2.getNode("Motion_4"));
        graph2.removeNode(graph2.getNode("Motion_5"));
        graph2.removeNode(graph2.getNode("Motion_6"));

        //load the location map
        String workingDirectory = System.getProperty("user.dir");
        System.out.println(workingDirectory);
        Path mapPath = Paths.get("coords.txt");
        System.out.println(mapPath);
        ContinuousTabularDatasetFileReader dataReaderMap = new ContinuousTabularDatasetFileReader(mapPath, Delimiter.COMMA);
        try {
            DataSet locationMap = (DataSet) DataConvertUtils.toDataModel(dataReaderMap.readInData());
            long timegraph3 = System.nanoTime();
            System.out.println("Done loading location map. Elapsed time: " + (timegraph3 - timegraph2) / 1000000000 + "s");

            System.out.println("Running Gdistance");

            Gdistance gdist = new Gdistance(locationMap, xdist, ydist, zdist);
            List<Double> distance = gdist.distances(graph1, graph2);
            System.out.println(distance);
            System.out.println("Done running Distance. Elapsed time: " + (System.nanoTime() - timegraph3) / 1000000000 + "s");
            System.out.println("Total elapsed time: " + (System.nanoTime() - timestart) / 1000000000 + "s");

            PrintWriter writer = new PrintWriter("Gdistances.txt", StandardCharsets.UTF_8);
            writer.println(distance);
            writer.close();
        } catch (Exception IOException) {
            IOException.printStackTrace();
        }
    }
}

