package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.pitt.dbmi.data.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDataFileReader;
import edu.pitt.dbmi.data.reader.tabular.TabularDataReader;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Erich on 7/6/2016.
 */
public class GdistanceTest {

    public static void main(String... args) {
        //first generate a couple random graphs
        int numVars = 16;
        int numEdges = 16;
        List<Node> vars = new ArrayList<>();
        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }
        Graph testdag1 = GraphUtils.randomGraphRandomForwardEdges(vars, 0, numEdges, 30, 15, 15, false, true);
        Graph testdag2 = GraphUtils.randomGraphRandomForwardEdges(vars, 0, numEdges, 30, 15, 15, false, true);

        //System.out.println(testdag1);
        //load the location map
        String workingDirectory = System.getProperty("user.dir");
        System.out.println(workingDirectory);
        Path mapPath = Paths.get("locationMap.txt");
        System.out.println(mapPath);
        TabularDataReader dataReaderMap = new ContinuousTabularDataFileReader(mapPath.toFile(), Delimiter.COMMA);
        try {
            DataSet locationMap = (DataSet) DataConvertUtils.toDataModel(dataReaderMap.readInData());
            // System.out.println(locationMap);
            //then compare their distance
            double xdist = 2.4;
            double ydist = 2.4;
            double zdist = 2;
            Gdistance gdist = new Gdistance(locationMap, xdist, ydist, zdist);
            List<Double> output = gdist.distances(testdag1, testdag2);

            System.out.println(output);

            PrintWriter writer = new PrintWriter("Gdistances.txt", "UTF-8");
            writer.println(output);
            writer.close();
        } catch (Exception IOException) {
            IOException.printStackTrace();
        }
    }
}
