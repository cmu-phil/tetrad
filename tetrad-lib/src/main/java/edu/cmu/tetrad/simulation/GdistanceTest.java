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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDatasetFileReader;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Erich on 7/6/2016.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GdistanceTest {

    /**
     * Private constructor to prevent instantiation.
     */
    private GdistanceTest() {
    }

    /**
     * The main method generates random graphs, loads a location map, calculates the distance between two graphs, and
     * saves the output to a file.
     *
     * @param args the command line arguments
     */
    public static void main(String... args) {
        //first generate a couple random graphs
        final int numVars = 16;
        final int numEdges = 16;
        List<Node> vars = new ArrayList<>();
        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }
        Graph testdag1 = RandomGraph.randomGraphRandomForwardEdges(vars, 0, numEdges, 30, 15, 15, false, true, -1);
        Graph testdag2 = RandomGraph.randomGraphRandomForwardEdges(vars, 0, numEdges, 30, 15, 15, false, true, -1);

        //System.out.println(testdag1);
        //load the location map
        String workingDirectory = System.getProperty("user.dir");
        System.out.println(workingDirectory);
        Path mapPath = Paths.get("locationMap.txt");
        System.out.println(mapPath);
        ContinuousTabularDatasetFileReader dataReaderMap = new ContinuousTabularDatasetFileReader(mapPath, Delimiter.COMMA);
        try {
            DataSet locationMap = (DataSet) DataConvertUtils.toDataModel(dataReaderMap.readInData());
            // System.out.println(locationMap);
            //then compare their distance
            final double xdist = 2.4;
            final double ydist = 2.4;
            final double zdist = 2;
            Gdistance gdist = new Gdistance(locationMap, xdist, ydist, zdist);
            List<Double> output = gdist.distances(testdag1, testdag2);

            System.out.println(output);

            PrintWriter writer = new PrintWriter("Gdistances.txt", StandardCharsets.UTF_8);
            writer.println(output);
            writer.close();
        } catch (Exception IOException) {
            IOException.printStackTrace();
        }
    }
}

