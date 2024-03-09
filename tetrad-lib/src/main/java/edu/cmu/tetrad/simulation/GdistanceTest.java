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
     * The main method generates random graphs, loads a location map, calculates the distance between two graphs,
     * and saves the output to a file.
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
        Graph testdag1 = RandomGraph.randomGraphRandomForwardEdges(vars, 0, numEdges, 30, 15, 15, false, true);
        Graph testdag2 = RandomGraph.randomGraphRandomForwardEdges(vars, 0, numEdges, 30, 15, 15, false, true);

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
