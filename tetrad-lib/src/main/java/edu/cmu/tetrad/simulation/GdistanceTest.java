package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.io.TabularContinuousDataReader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Erich on 7/6/2016.
 */
public class GdistanceTest {
    public static void main (String... args) {
        //first generate a couple random graphs
        int numVars = 5;
        int numEdges = 4;
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
        edu.cmu.tetrad.io.DataReader dataReaderMap = new TabularContinuousDataReader(mapPath, ',');
        try{
            DataSet locationMap = dataReaderMap.readInData();
            //then compare their distance
            List<Double> output = Gdistance.distances(testdag1, testdag2, locationMap);

            System.out.println(output);
        }
        catch(Exception IOException){
            IOException.printStackTrace();
        }
    }
}
