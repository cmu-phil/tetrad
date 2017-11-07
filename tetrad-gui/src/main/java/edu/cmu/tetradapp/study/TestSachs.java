package edu.cmu.tetradapp.study;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.multi.Fask;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Cpc;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Pc;
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.workbench.PngWriter;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.util.StatUtils.min;
import static java.lang.Math.log;

public class TestSachs {

    @Test
    public void test1() {
        Statistic ap = new AdjacencyPrecision();
        Statistic ar = new AdjacencyRecall();
        Statistic ahp = new ArrowheadPrecisionIgnore2c();
        Statistic ahr = new ArrowheadRecall();

        List<String> paths = new ArrayList<>();

        paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/1. cd3cd28.txt");
        paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/2. cd3cd28icam2.txt");
        paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/3. cd3cd28+aktinhib.txt");
        paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/4. cd3cd28+g0076.txt");
        paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/5. cd3cd28+psitect.txt");
        paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/6. cd3cd28+u0126.txt");
        paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/7. cd3cd28+ly.txt");
        paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/8. pma.txt");
        paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/9. b2camp.txt");

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

        double[] penalties = {.1}; // only do one at a time or the computer will freeze up!
        boolean[] loggedArr = {true, false};

        for (double penalty : penalties) {
            for (boolean logged : loggedArr) {

                parameters.set("penaltyDiscount", penalty);
                parameters.set("twoCycleAlpha", 1e-6);
                parameters.set("presumePositiveCoefficients", true);

                File commonDir = new File("/Users/user/Downloads/sachsgraphs/test/tetrad2/penalty." + penalty + "/" + (logged ? "logged" : "raw"));
                commonDir.mkdirs();

                for (Algorithm alg : algorithms) {
                    File algDir = new File(commonDir, alg.getDescription() + (logged ? ".logged" : ".raw"));
                    algDir.mkdirs();

                    File txtDir = new File(algDir, "txt");
                    txtDir.mkdirs();

                    File pngDir = new File(algDir, "png");
                    pngDir.mkdirs();

                    Graph combinedGraph = new EdgeListGraph(groundTruth.getNodes());


                    for (int f = 0; f < files.size(); f++) {

                        File file = new File(paths.get(f));

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


                        String name = "data." + (f + 1);

                        if (new File(algDir, name + ".png").exists()) {
                            continue;
                        }

                        GraphUtils.circleLayout(estGraph, 200, 200, 175);

                        GraphUtils.saveGraph(estGraph, new File(txtDir, name + ".txt"), false);

                        PngWriter.writePng(estGraph, new File(pngDir, name + ".png"));
                    }

                    String name = "combined" + (logged ? ".logged" : ".raw");
                    GraphUtils.saveGraph(combinedGraph, new File(txtDir, name + ".txt"), false);

                    GraphUtils.circleLayout(combinedGraph, 200, 200, 175);
                    PngWriter.writePng(combinedGraph, new File(pngDir, name + ".png"));

                    System.out.println("AP = " + ap.getValue(groundTruth, combinedGraph));
                    System.out.println("AR = " + ar.getValue(groundTruth, combinedGraph));
                    System.out.println("AHP = " + ahp.getValue(groundTruth, combinedGraph));
                    System.out.println("AHR = " + ahr.getValue(groundTruth, combinedGraph));
                }
            }
        }
    }

    @Test
    public void test2() {
        File txt = new File("/Users/user/Downloads/sachsgraphs/generalized.scoring.metrics.with.GES.search/raw.larger.kernel.width/txt");
        File png = new File("/Users/user/Downloads/sachsgraphs/generalized.scoring.metrics.with.GES.search/raw.larger.kernel.width/png");

        String[] _vars = {"raf", "mek", "plc", "pip2", "pip3", "erk", "akt", "pka", "pkc", "p38", "jnk"};
        List<Node> vars = new ArrayList<>();
        for (String name : _vars) {
            vars.add(new ContinuousVariable(name));
        }

        File[] files = txt.listFiles();

        Graph combined = new EdgeListGraph(vars);

        try {
            for (File file : files) {
                if (file.getName().startsWith(".")) continue;

                DataReader reader = new DataReader();
                reader.setVariablesSupplied(false);
                reader.setDelimiter(DelimiterType.COMMA);
                DataSet data = reader.parseTabular(file);

                Graph graph = new EdgeListGraph(vars);

                for (int j = 0; j < 11; j++) {
                    for (int i = 0; i < 11; i++) {
                        if (data.getDouble(i, j) == 1) {
                            Edge edge = Edges.directedEdge(vars.get(i), vars.get(j));

                            if (!graph.containsEdge(edge)) {
                                graph.addEdge(edge);
                            }

                            if (!combined.containsEdge(edge)) {
                                combined.addEdge(edge);
                            }
                        } else if (data.getDouble(i, j) == -1) {
                            Edge edge = Edges.undirectedEdge(vars.get(i), vars.get(j));

                            if (!graph.containsEdge(edge)) {
                                graph.addEdge(edge);
                            }

                            if (!combined.containsEdge(edge)) {
                                combined.addEdge(edge);
                            }
                        }
                    }
                }

                GraphUtils.circleLayout(graph, 200, 200, 175);
                PngWriter.writePng(graph, new File(png, file.getName() + ".png"));
            }

            GraphUtils.circleLayout(combined, 200, 200, 175);
            PngWriter.writePng(combined, new File(png, "combined.png"));
            GraphUtils.saveGraph(combined, new File(txt, "combined.txt"), false);

            GraphUtils.circleLayout(combined, 200, 200, 175);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test3() {

        // Concatenate the "main result" data.
        File dir = new File("/Users/user/Downloads/sachs/data/Data Files/main.result/");

        File[] filesArr = dir.listFiles();
        List<File> files = new ArrayList<>();

        for (File file : filesArr) {
            if (!file.getName().startsWith(".")) {
                files.add(file);
            }
        }

        List<DataSet> dataSets = new ArrayList<>();

        List<String> paths = new ArrayList<>();

        paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/1. cd3cd28.txt");
        paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/2. cd3cd28icam2.txt");
        paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/3. cd3cd28+aktinhib.txt");
        paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/4. cd3cd28+g0076.txt");
        paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/5. cd3cd28+psitect.txt");
        paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/6. cd3cd28+u0126.txt");
        paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/7. cd3cd28+ly.txt");
        paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/8. pma.txt");
        paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/9. b2camp.txt");

        for (int f = 0; f < paths.size(); f++) {

            File file = new File(paths.get(f));

            System.out.println(file.getName());

            DataSet data = null;
            try {
                DataReader reader = new DataReader();
                reader.setVariablesSupplied(true);
                reader.setDelimiter(DelimiterType.WHITESPACE);
                data = reader.parseTabular(file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            for (int i = 0; i < data.getNumRows(); i++) {
                for (int j = 0; j < data.getNumColumns(); j++) {
                    data.setDouble(i, j, log(0.0001 + data.getDouble(i, j)));
                }
            }

            dataSets.add(DataUtils.center(data));
        }

        DataSet concat = DataUtils.concatenate(dataSets);

//        double[][] concatdoubles = concat.getDoubleData().transpose().toArray();
//
//        double[] min = new double[concatdoubles.length];
//
//        for (int j = 0; j < min.length; j++) min[j] = min(concatdoubles[j]);
//
//        DataSet logged = concat.copy();
//
//        for (int i = 0; i < concat.getNumRows(); i++) {
//            for (int j = 0; j < concat.getNumColumns(); j++) {
//                logged.setDouble(i, j, log(0.0001 - min[j] + concatdoubles[j][i]));
//            }
//        }

//        try {
////            DataWriter.writeRectangularData(concat,
////                    new FileWriter("/Users/user/Downloads/sachsgraphs/test/concat.main.result.centered.txt"),
////                    '\t');
////            DataWriter.writeRectangularData(logged,
////                    new FileWriter("/Users/user/Downloads/sachsgraphs/test/concat.main.result.centered.logged.txt"),
////                    '\t');
//
////            DataWriter.writeRectangularData(concat,
////                    new FileWriter("/Users/user/Downloads/sachsgraphs/test/concat.main.result.logged.centered.txt"),
////                    '\t');
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    @Test
    public void test4() {
        File centeredLoggedFile = new File("/Users/user/Downloads/sachsgraphs/data/combined.centered.data/concat.main.result.logged.centered.txt");
        File centeredFile = new File("/Users/user/Downloads/sachsgraphs/data/combined.centered.data/concat.main.result.centered.txt");

        List<Algorithm> algorithms = new ArrayList<>();

        algorithms.add(new Fask(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
        algorithms.add(new Pc(new edu.cmu.tetrad.algcomparison.independence.SemBicTest()));
        algorithms.add(new Cpc(new edu.cmu.tetrad.algcomparison.independence.SemBicTest()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges(new SemBicScore()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Fci(new SemBicTest()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci(new SemBicTest(), new SemBicScore()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.FaskGfci(new SemBicTest()));

        Parameters parameters = new Parameters();

        double[] penalties = {2, 1, .5, .1}; // only do one at a time or the computer will freeze up!
        boolean[] loggedArr = {true, false};

        for (double penalty : penalties) {
            for (boolean logged : loggedArr) {

                parameters.set("penaltyDiscount", penalty);
                parameters.set("twoCycleAlpha", 1e-6);
                parameters.set("presumePositiveCoefficients", true);

                File commonDir = new File("/Users/user/Downloads/sachsgraphs/test/tetrad2/centeredcombined/penalty." + penalty + "/" + (logged ? "logged" : "raw"));
                commonDir.mkdirs();

                for (Algorithm alg : algorithms) {
                    File algDir = new File(commonDir, alg.getDescription() + (logged ? ".logged" : ".raw"));
                    algDir.mkdirs();

                    File txtDir = new File(algDir, "txt");
                    txtDir.mkdirs();

                    File pngDir = new File(algDir, "png");
                    pngDir.mkdirs();

                    DataSet data1 = null;
                    try {
                        DataReader reader = new DataReader();
                        reader.setVariablesSupplied(true);
                        reader.setDelimiter(DelimiterType.TAB);

                        if (logged) {
                            data1 = reader.parseTabular(centeredLoggedFile);
                        } else {
                            data1 = reader.parseTabular(centeredFile);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    Graph combinedGraph = alg.search(data1, parameters);

                    System.out.println(combinedGraph.getNodes());

                    for (Node node : combinedGraph.getNodes()) {
                        node.setName(node.getName().toLowerCase());
                        if ("praf".equals(node.getName())) node.setName("raf");
                        if ("pmek".equals(node.getName())) node.setName("mek");
                        if ("plcg".equals(node.getName())) node.setName("plc");
                        if ("p44/42".equals(node.getName())) node.setName("erk");
                        if ("pakts473".equals(node.getName())) node.setName("akt");
                        if ("pjnk".equals(node.getName())) node.setName("jnk");
                    }

                    String name = "combined";

                    if (new File(algDir, name + ".png").exists()) {
                        continue;
                    }

                    GraphUtils.circleLayout(combinedGraph, 200, 200, 175);

                    GraphUtils.saveGraph(combinedGraph, new File(txtDir, name + ".txt"), false);

                    PngWriter.writePng(combinedGraph, new File(pngDir, name + ".png"));

                    GraphUtils.circleLayout(combinedGraph, 200, 200, 175);
                    PngWriter.writePng(combinedGraph, new File(pngDir, name + ".png"));
                }
            }
        }
    }
}
