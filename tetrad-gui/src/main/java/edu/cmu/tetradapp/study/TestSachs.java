package edu.cmu.tetradapp.study;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.multi.Fask;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Cpc;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Pc;
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DelimiterType;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.workbench.PngWriter;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.log;

public class TestSachs {

    @Test
    public void test1() {
        Statistic ap = new AdjacencyPrecision();
        Statistic ar = new AdjacencyRecall();
        Statistic ahp = new ArrowheadPrecisionIgnore2c();
        Statistic ahr = new ArrowheadRecall();

        File dir = new File("/Users/user/Downloads/sachs/data/Data Files/main.result/");

        File graphFile = new File("/Users/user/Downloads/sachs/graphs/ground.truth.txt");
//        File file2 = new File("/Users/user/Downloads/sachs/graphs/biologist.view.txt");
        Graph groundTruth = GraphUtils.loadGraphTxt(graphFile);

        for (Node node : groundTruth.getNodes()) {
            node.setName(node.getName().toLowerCase());
            if ("plcg".equals(node.getName())) node.setName("plc");
        }

        groundTruth = GraphUtils.replaceNodes(groundTruth, groundTruth.getNodes());

        File[] filesArr = dir.listFiles();
        List<File> files = new ArrayList<>();

        for (File file : filesArr) {
            if (!file.getName().startsWith(".")) {
                files.add(file);
            }
        }

        List<Algorithm> algorithms = new ArrayList<>();

        algorithms.add(new Fask(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
        algorithms.add(new Pc(new edu.cmu.tetrad.algcomparison.independence.SemBicTest()));
        algorithms.add(new Cpc(new edu.cmu.tetrad.algcomparison.independence.SemBicTest()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges(new SemBicScore()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Fci(new SemBicTest()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci(new SemBicTest(), new SemBicScore()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.FaskGfci(new SemBicTest()));

        Parameters parameters = new Parameters();

        double[] penalties = {2.0, 1.0, 0.5, 0.1};
        boolean[] loggedArr = {true, false};

        for (double penalty : penalties) {
            for (boolean logged : loggedArr) {

                parameters.set("penaltyDiscount", 0.1);
                parameters.set("twoCycleAlpha", 1e-6);
                parameters.set("presumePositiveCoefficients", true);

                File commonDir = new File("/Users/user/Downloads/sachs/graphs/penalty." + penalty + "/" + (logged ? "logged" : "raw"));
                commonDir.mkdirs();

                for (Algorithm alg : algorithms) {
                    Graph combinedGraph = new EdgeListGraph(groundTruth.getNodes());

                    for (int f = 0; f < files.size(); f++) {

                        File file = files.get(f);

                        System.out.println(file.getName());

                        DataSet data1 = null;
                        try {
                            DataReader reader = new DataReader();
                            reader.setVariablesSupplied(true);
                            reader.setDelimiter(DelimiterType.WHITESPACE);
                            data1 = reader.parseTabular(file);

                            if (logged) {
                                for (int i = 0; i < data1.getNumRows(); i++) {
                                    for (int j = 0; j < data1.getNumColumns(); j++) {
                                        data1.setDouble(i, j, log(0.0001 + data1.getDouble(i, j)));
                                    }
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        Graph estGraph = alg.search(data1, parameters);

                        System.out.println(estGraph.getNodes());

                        for (Node node : estGraph.getNodes()) {
                            node.setName(node.getName().toLowerCase());
                            if ("praf".equals(node.getName())) node.setName("raf");
                            if ("pmek".equals(node.getName())) node.setName("mek");
                            if ("plcg".equals(node.getName())) node.setName("plc");
                            if ("p44/42".equals(node.getName())) node.setName("erk");
                            if ("pakts473".equals(node.getName())) node.setName("akt");
                            if ("pjnk".equals(node.getName())) node.setName("jnk");
                        }

                        estGraph = GraphUtils.replaceNodes(estGraph, groundTruth.getNodes());

                        System.out.println(groundTruth);
                        System.out.println(estGraph);

                        System.out.println("AP = " + ap.getValue(groundTruth, estGraph));
                        System.out.println("AR = " + ar.getValue(groundTruth, estGraph));
                        System.out.println("AHP = " + ahp.getValue(groundTruth, estGraph));
                        System.out.println("AHR = " + ahr.getValue(groundTruth, estGraph));

                        for (Edge edge : estGraph.getEdges()) {
                            if (!combinedGraph.containsEdge(edge)) combinedGraph.addEdge(edge);
                        }


                        File outDir = new File(commonDir, "txt/individualgraphs");
                        outDir.mkdirs();

                        File pngDir = new File(commonDir, "png/individualgraphs");
                        pngDir.mkdirs();

                        String name = "file." + (f + 1) + "." + alg.getDescription() + (logged ? ".logged" : ".raw");

                        if (new File(outDir, name + ".png").exists()) {
                            continue;
                        }

                        GraphUtils.circleLayout(estGraph, 200, 200, 175);

                        GraphUtils.saveGraph(estGraph, new File(outDir, name + ".txt"), false);

                        PngWriter.writePng(estGraph, new File(pngDir, name + ".png"));
                    }

                    File outDir = new File(commonDir, "txt/combinedgraphs");
                    outDir.mkdirs();

                    File pngDir = new File(commonDir, "png/combinedgraphs");
                    pngDir.mkdirs();

                    String name ="combined." + alg.getDescription() + (logged ? ".logged" : ".raw");
                    System.out.println("DING DING DING! COMBINED GRAPH IS...\n" + combinedGraph);

                    if (new File(outDir, name + ".png").exists()) {
                        continue;
                    }

                    GraphUtils.circleLayout(combinedGraph, 200, 200, 175);

                    GraphUtils.saveGraph(combinedGraph, new File(outDir, name + ".txt"), false);

                    PngWriter.writePng(combinedGraph, new File(pngDir, name + ".png"));

                    System.out.println("AP = " + ap.getValue(groundTruth, combinedGraph));
                    System.out.println("AR = " + ar.getValue(groundTruth, combinedGraph));
                    System.out.println("AHP = " + ahp.getValue(groundTruth, combinedGraph));
                    System.out.println("AHR = " + ahr.getValue(groundTruth, combinedGraph));
                }
            }
        }
    }
}
