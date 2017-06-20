package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.pitt.dbmi.data.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDataFileReader;
import edu.pitt.dbmi.data.reader.tabular.TabularDataReader;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Created by Erich on 7/14/2016.
 */
public class GdistanceApply {

    public static void main(String... args) {
        double xdist = 2.4;
        double ydist = 2.4;
        double zdist = 2;
        long timestart = System.nanoTime();
        System.out.println("Loading first graph");
        Graph graph1 = GraphUtils.loadGraphTxt(new File("Motion_Corrected_Graphs/singlesub_motion_graph_025_04.txt"));
        long timegraph1 = System.nanoTime();
        //System.out.println(graph1);
        System.out.println("Done loading first graph. Elapsed time: " + (timegraph1 - timestart) / 1000000000 + "s");
        System.out.println("Loading second graph");
        Graph graph2 = GraphUtils.loadGraphTxt(new File("Motion_Corrected_Graphs/singlesub_motion_graph_027_04.txt"));
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
        TabularDataReader dataReaderMap = new ContinuousTabularDataFileReader(mapPath.toFile(), Delimiter.COMMA);
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

            PrintWriter writer = new PrintWriter("Gdistances.txt", "UTF-8");
            writer.println(distance);
            writer.close();
        } catch (Exception IOException) {
            IOException.printStackTrace();
        }
    }
}
