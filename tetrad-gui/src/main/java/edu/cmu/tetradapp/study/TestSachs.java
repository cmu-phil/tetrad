package edu.cmu.tetradapp.study;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.multi.Fask;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Cpc;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Pc;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.SingleGraphAlg;
import edu.cmu.tetrad.algcomparison.independence.BDeuTest;
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.SemBicScoreImages;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetradapp.workbench.PngWriter;
import edu.pitt.dbmi.data.Dataset;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static edu.cmu.tetrad.util.StatUtils.mean;
import static edu.cmu.tetrad.util.StatUtils.sd;
import static java.lang.Math.abs;
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

        File graphFile = new File("/Users/user/Downloads/sachsgraphs/groundtruth/ground.truth2.txt");
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
//        algorithms.add(new Pc(new edu.cmu.tetrad.algcomparison.independence.SemBicTest()));
//        algorithms.add(new Cpc(new edu.cmu.tetrad.algcomparison.independence.SemBicTest()));
//        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges(new SemBicScore()));
//        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Fci(new SemBicTest()));
//        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci(new SemBicTest(), new SemBicScore()));
//        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.FaskGfci(new SemBicTest()));

//        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.multi.ImagesSemBic());


        Parameters parameters = new Parameters();

        double[] penalties = {.5}; // only do one at a time or the computer will freeze up!
        boolean[] loggedArr = {true, false};

        for (double penalty : penalties) {
            for (boolean logged : loggedArr) {

                parameters.set("penaltyDiscount", penalty);
                parameters.set("twoCycleAlpha", 1e-3);
                parameters.set("faskDelta", -0.2);
                parameters.set("numRuns", 1);
                parameters.set("randomSelectionSize", 9);

                File commonDir = new File("/Users/user/Downloads/sachsgraphs/test/tetrad3/penalty." + penalty + "/" + (logged ? "logged" : "raw"));
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


                        DataSet data = null;

                        try {
                            DataReader reader = new DataReader();
                            reader.setVariablesSupplied(true);
                            reader.setDelimiter(DelimiterType.TAB);
                            data = reader.parseTabular(file);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        Graph estGraph = alg.search(data, parameters);

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

                        int[][] pos = {
                                {345, 120},
                                {360, 210},
                                {45, 180},
                                {60, 380},
                                {120, 300},
                                {345, 285},
                                {285, 360},
                                {105, 105},
                                {240, 30},
                                {200, 270},
                                {120, 210}
                        };

                        for (int i = 0; i < 11; i++) {
                            Node node = estGraph.getNodes().get(i);
                            node.setCenter(pos[i][0], pos[i][1]);
                        }

//                        GraphUtils.circleLayout(estGraph, 200, 200, 175);

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
        File centeredLoggedFile = new File("/Users/user/Downloads/sachsgraphs/data/combined.data2/nonparametric.concatenated.txt");
//        File centeredFile = new File("/Users/user/Downloads/sachsgraphs/data/combined.centered.data/concat.main.result.centered.txt");

        List<Algorithm> algorithms = new ArrayList<>();

        algorithms.add(new Fask(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
        algorithms.add(new Pc(new edu.cmu.tetrad.algcomparison.independence.SemBicTest()));
        algorithms.add(new Cpc(new edu.cmu.tetrad.algcomparison.independence.SemBicTest()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges(new SemBicScore()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Fci(new SemBicTest()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci(new SemBicTest(), new SemBicScore()));

        Parameters parameters = new Parameters();

        double[] penalties = {2, 1, .5, .1}; // only do one at a time or the computer will freeze up!
        boolean[] loggedArr = {true, false};

        for (double penalty : penalties) {

            parameters.set("penaltyDiscount", penalty);
            parameters.set("twoCycleAlpha", 1e-6);
            parameters.set("presumePositiveCoefficients", true);

            File commonDir = new File("/Users/user/Downloads/sachsgraphs/test/tetrad2/standardizedcombined/penalty." + penalty + "/" + "raw");
            commonDir.mkdirs();

            for (Algorithm alg : algorithms) {
                File algDir = new File(commonDir, alg.getDescription() + (".raw"));
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

//                        if (logged) {
                    data1 = reader.parseTabular(centeredLoggedFile);
//                        } else {
//                            data1 = reader.parseTabular(centeredFile);
//                        }
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

//                GraphUtils.saveGraph(combinedGraph, new File(txtDir, name + ".txt"), false);

                PngWriter.writePng(combinedGraph, new File(pngDir, name + ".png"));

                GraphUtils.circleLayout(combinedGraph, 200, 200, 175);
                PngWriter.writePng(combinedGraph, new File(pngDir, name + ".png"));
            }
        }
    }

    @Test
    public void test5() {
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

//        algorithms.add(new Fask(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
//        algorithms.add(new Pc(new edu.cmu.tetrad.algcomparison.independence.SemBicTest()));
//        algorithms.add(new Cpc(new edu.cmu.tetrad.algcomparison.independence.SemBicTest()));
//        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges(new SemBicScore()));
//        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Fci(new SemBicTest()));
//        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci(new SemBicTest(), new SemBicScore()));
//        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.FaskGfci(new SemBicTest()));

        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.multi.ImagesSemBic());


        Parameters parameters = new Parameters();

        double[] penalties = {2, 1, .5, .1}; // only do one at a time or the computer will freeze up!
        boolean[] loggedArr = {true, false};

        for (double penalty : penalties) {
            for (boolean logged : loggedArr) {

                parameters.set("penaltyDiscount", penalty);
                parameters.set("twoCycleAlpha", 1e-6);
                parameters.set("presumePositiveCoefficients", true);
                parameters.set("numRuns", 1);
                parameters.set("randomSelectionSize", 9);

                File commonDir = new File("/Users/user/Downloads/sachsgraphs/test/tetrad2/penalty." + penalty + "/" + (logged ? "logged" : "raw"));
                commonDir.mkdirs();

                for (Algorithm alg : algorithms) {
                    File algDir = new File(commonDir, alg.getDescription() + (logged ? ".logged" : ".raw"));
                    algDir.mkdirs();

                    File txtDir = new File(algDir, "txt");
                    txtDir.mkdirs();

                    File pngDir = new File(algDir, "png");
                    pngDir.mkdirs();

                    List<DataModel> dataSets = new ArrayList<>();

                    for (int f = 0; f < files.size(); f++) {

                        File file = new File(paths.get(f));

                        System.out.println(file.getName());

                        DataSet data1 = null;
                        try {
                            DataReader reader = new DataReader();
                            reader.setVariablesSupplied(true);
                            reader.setDelimiter(DelimiterType.WHITESPACE);
                            data1 = reader.parseTabular(file);

                            for (Node node : data1.getVariables()) {
                                node.setName(node.getName().toLowerCase());
                                if ("praf".equals(node.getName())) node.setName("raf");
                                if ("pmek".equals(node.getName())) node.setName("mek");
                                if ("plcg".equals(node.getName())) node.setName("plc");
                                if ("p44/42".equals(node.getName())) node.setName("erk");
                                if ("pakts473".equals(node.getName())) node.setName("akt");
                                if ("pjnk".equals(node.getName())) node.setName("jnk");
                            }

                            if (logged) {
                                for (int i = 0; i < data1.getNumRows(); i++) {
                                    for (int j = 0; j < data1.getNumColumns(); j++) {
                                        data1.setDouble(i, j, log(0.0001 + data1.getDouble(i, j)));
                                    }
                                }
                            }

                            dataSets.add(data1);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    SemBicScoreImages imagesScore = new SemBicScoreImages(dataSets);
                    imagesScore.setPenaltyDiscount(penalty);

                    edu.cmu.tetrad.search.Fges fges = new edu.cmu.tetrad.search.Fges(imagesScore);

                    Graph combinedGraph = fges.search();

                    System.out.println(combinedGraph.getNodes());

//                    for (Node node : combinedGraph.getNodes()) {
//                        node.setName(node.getName().toLowerCase());
//                        if ("praf".equals(node.getName())) node.setName("raf");
//                        if ("pmek".equals(node.getName())) node.setName("mek");
//                        if ("plcg".equals(node.getName())) node.setName("plc");
//                        if ("p44/42".equals(node.getName())) node.setName("erk");
//                        if ("pakts473".equals(node.getName())) node.setName("akt");
//                        if ("pjnk".equals(node.getName())) node.setName("jnk");
//                    }

                    combinedGraph = GraphUtils.replaceNodes(combinedGraph, groundTruth.getNodes());

                    System.out.println(groundTruth);
                    System.out.println(combinedGraph);

                    System.out.println("AP = " + ap.getValue(groundTruth, combinedGraph));
                    System.out.println("AR = " + ar.getValue(groundTruth, combinedGraph));
                    System.out.println("AHP = " + ahp.getValue(groundTruth, combinedGraph));
                    System.out.println("AHR = " + ahr.getValue(groundTruth, combinedGraph));

                    for (Edge edge : combinedGraph.getEdges()) {
                        if (!combinedGraph.containsEdge(edge)) combinedGraph.addEdge(edge);
                    }

                    String name = "combined";

                    if (new File(algDir, name + ".png").exists()) {
                        continue;
                    }

                    GraphUtils.circleLayout(combinedGraph, 200, 200, 175);

                    GraphUtils.saveGraph(combinedGraph, new File(txtDir, name + ".txt"), false);

                    PngWriter.writePng(combinedGraph, new File(pngDir, name + ".png"));
                }
            }
        }
    }

    @Test
    public void test6() {
        File centeredLoggedFile = new File("/Users/user/Downloads/sachsgraphs/data/combined.centered.data/concat.main.result.logged.centered.txt");
        File centeredFile = new File("/Users/user/Downloads/sachsgraphs/data/combined.centered.data/concat.main.result.centered.txt");

        List<Algorithm> algorithms = new ArrayList<>();

//        algorithms.add(new Fask(new edu.cmu.tetrad.algcomparison.score.BdeuScore()));
        algorithms.add(new Pc(new edu.cmu.tetrad.algcomparison.independence.BDeuTest()));
        algorithms.add(new Cpc(new edu.cmu.tetrad.algcomparison.independence.BDeuTest()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges(new BdeuScore()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Fci(new BDeuTest()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci(new BDeuTest(), new BdeuScore()));
//        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.FaskGfci(new BDeuTest()));

        Parameters parameters = new Parameters();

        boolean[] loggedArr = {true, false};

        for (boolean logged : loggedArr) {

            parameters.set("twoCycleAlpha", 1e-6);
            parameters.set("presumePositiveCoefficients", true);

            File commonDir = new File("/Users/user/Downloads/sachsgraphs/test/tetrad2/centeredcombined/discrete/" + (logged ? "logged" : "raw"));
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
                        data1 = DataUtils.discretize(data1, 3, false);
                    } else {
                        data1 = reader.parseTabular(centeredFile);
                        data1 = DataUtils.discretize(data1, 3, false);
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

    @Test
    public void test7() {
        Parameters parameters = new Parameters();
        parameters.set("penaltyDiscount", .5, 1, 2);
        parameters.set("twoCycleAlpha", 1e-6);
        parameters.set("alpha", 1e-4);
        parameters.set("presumePositiveCoefficients", true);
//        parameters.set("doNonparanormalTransform", true);


        List<String> filenames = new ArrayList<>();

//        filenames.add("raw.concatenated.txt");
        filenames.add("raw.concatenated.log.plus0.0001.txt");
//        filenames.add("raw.concatenated.log.plus10.txt");
//        filenames.add("raw.concatenated.log.plus100.txt");
//        filenames.add("raw.concatenated.log.plus300.txt");
//        filenames.add("raw.concatenated.nonparanormal.txt");
//        filenames.add("raw.log.plus0.0001.concatenated.txt");
//        filenames.add("raw.log.plus10.concatenated.txt");
//        filenames.add("raw.log.plus100.concatenated.txt");
//        filenames.add("raw.raw.plus300.concatenated.txt");
//        filenames.add("raw.centered.concatenated.txt");
//        filenames.add("raw.standardized.concatenated.txt");
//        filenames.add("raw.nonparanormal.concatenated.txt");


        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("penaltyDiscount"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecisionIgnore2c());
        statistics.add(new ArrowheadRecall());
        statistics.add(new AncestorPrecision());
        statistics.add(new AncestorRecall());
        statistics.add(new TwoCycleFalsePositive());
        statistics.add(new TwoCycleFalseNegative());
//        statistics.add(new MathewsCorrAdj());
        statistics.add(new MathewsCorrArrow());
//        statistics.add(new F1Adj());
//        statistics.add(new F1Arrow());
//        statistics.add(new SHD());
//        statistics.add(new ElapsedTime());

//        statistics.setWeight("AP", 1.0);
//        statistics.setWeight("AR", 0.5);

        File file = new File("/Users/user/Downloads/sachsgraphs/groundtruth/sachsgraph.txt");
        Graph sachsGraph = GraphUtils.loadGraphTxt(file);

        Algorithms algorithms = new Algorithms();

        algorithms.add(new Fask(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
        algorithms.add(new Pc(new edu.cmu.tetrad.algcomparison.independence.SemBicTest()));
        algorithms.add(new Cpc(new edu.cmu.tetrad.algcomparison.independence.SemBicTest()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges(new SemBicScore()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Fci(new SemBicTest()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci(new SemBicTest(), new SemBicScore()));
        algorithms.add(new SingleGraphAlg(sachsGraph));

        Simulations simulations = new Simulations();

        simulations.add(new LoadDataAndGraphsSachs(filenames.get(0)));

        edu.cmu.tetrad.algcomparison.Comparison comparison = new edu.cmu.tetrad.algcomparison.Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setParallelized(true);
        comparison.setSaveGraphs(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

//        comparison.setReplacePartialOrientedWithDirected(true);

        comparison.compareFromSimulations("comparisonSachs", simulations, algorithms, statistics, parameters);
    }

    public class LoadDataAndGraphsSachs implements Simulation {
        static final long serialVersionUID = 23L;
        private final String dataFileName;
        private List<Graph> graphs = new ArrayList<>();
        private List<DataSet> dataSets = new ArrayList<>();
        private List<String> usedParameters = new ArrayList<>();
        private String description = "";

        public LoadDataAndGraphsSachs(String dataFileName) {
            this.dataFileName = dataFileName;
        }

        @Override
        public void createData(Parameters parameters) {
            this.dataSets = new ArrayList<>();

            String datadir = "/Users/user/Downloads/sachsgraphs/sachs.data/prepared.concatenations";

            List<String> paths = new ArrayList<>();

//            paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/1. cd3cd28.txt");
//            paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/2. cd3cd28icam2.txt");
//            paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/3. cd3cd28+aktinhib.txt");
//            paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/4. cd3cd28+g0076.txt");
//            paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/5. cd3cd28+psitect.txt");
//            paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/6. cd3cd28+u0126.txt");
//            paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/7. cd3cd28+ly.txt");
//            paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/8. pma.txt");
//            paths.add("/Users/user/Downloads/sachs/data/Data Files/txt/9. b2camp.txt");

            File dir = new File("/Users/user/Downloads/sachs/data/Data Files/main.result/");

//            File graphFile = new File("/Users/user/Downloads/sachsgraphs/groundtruth/biologist.view.txt");
            File graphFile = new File("/Users/user/Downloads/sachsgraphs/groundtruth/ground.truth.txt");
//            File graphFile = new File("/Users/user/Downloads/sachsgraphs/groundtruth/ground.truth.txt");
//            File graphFile = new File("/Users/user/Downloads/sachsgraphs/groundtruth/som.figure.3.txt");
//            File graphFile = new File("/Users/user/Downloads/sachsgraphs/groundtruth/modified.truth.txt");
//        File file2 = new File("/Users/user/Downloads/sachs/graphs/biologist.view.txt");
            Graph groundTruth = GraphUtils.loadGraphTxt(graphFile);


            for (Node node : groundTruth.getNodes()) {
                node.setName(node.getName().toLowerCase());
                if ("plcg".equals(node.getName())) node.setName("plc");
            }

            groundTruth = GraphUtils.replaceNodes(groundTruth, groundTruth.getNodes());

//            File[] filesArr = dir.listFiles();
//            List<File> files = new ArrayList<>();
//
//            for (File file : filesArr) {
//                if (!file.getName().startsWith(".")) {
//                    files.add(file);
//                }
//            }


//            List<DataSet> individualDataSets = new ArrayList<>();
//
//            for (int f = 0; f < files.size(); f++) {
//                File file = new File(paths.get(f));
//
//                System.out.println(file.getName());
//
//                DataSet data1 = null;
//
//
//                try {
//                    DataReader reader = new DataReader();
//                    reader.setVariablesSupplied(true);
//                    reader.setDelimiter(DelimiterType.WHITESPACE);
//                    data1 = reader.parseTabular(file);
//
//                    for (Node node : data1.getVariables()) {
//                        node.setName(node.getName().toLowerCase());
//                        if ("praf".equals(node.getName())) node.setName("raf");
//                        if ("pmek".equals(node.getName())) node.setName("mek");
//                        if ("plcg".equals(node.getName())) node.setName("plc");
//                        if ("p44/42".equals(node.getName())) node.setName("erk");
//                        if ("pakts473".equals(node.getName())) node.setName("akt");
//                        if ("pjnk".equals(node.getName())) node.setName("jnk");
//                    }
//
////                    data1 = DataUtils.center(data1);
//
////                    logData(data1, 300);
//
//                    data1 = DataUtils.getNonparanormalTransformed(data1);
//
////                    data1 = DataUtils.standardizeData(data1);
//
////                    data1 = removeExtremalValues(data1);
//
//                    individualDataSets.add(data1);
//
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }

//            DataSet concatenate = DataUtils.concatenate(individualDataSets);

//            logData(concatenate, 300);

//            concatenate = DataUtils.getNonparanormalTransformed(concatenate);

            DataSet concatenate;

            try {
                DataReader reader = new DataReader();
                reader.setVariablesSupplied(true);
                reader.setDelimiter(DelimiterType.WHITESPACE);
                File file = new File(
                        "/Users/user/Downloads/sachsgraphs/sachs.data/prepared.concatenations",
                        dataFileName);
                System.out.println(file.getAbsolutePath());

                concatenate = reader.parseTabular(file);


                for (Node node : concatenate.getVariables()) {
                    node.setName(node.getName().toLowerCase());
                    if ("praf".equals(node.getName())) node.setName("raf");
                    if ("pmek".equals(node.getName())) node.setName("mek");
                    if ("plcg".equals(node.getName())) node.setName("plc");
                    if ("p44/42".equals(node.getName())) node.setName("erk");
                    if ("pakts473".equals(node.getName())) node.setName("akt");
                    if ("pjnk".equals(node.getName())) node.setName("jnk");
                }

                dataSets.add(concatenate);
                graphs.add(groundTruth);
            } catch (IOException e) {
                e.printStackTrace();
            }


//            try {
//                String datadir = "/Users/user/Downloads/sachsgraphs/sachs.data/prepared.concatenations.disable";
//                File _datadir = new File(datadir);
////                _datadir.mkdirs();
//                String filename = "raw.nonparanormal.concatenated";
//                PrintStream out = new PrintStream(new File(_datadir, filename));
//                out.println(concatenate);
//                out.close();
//
//            } catch (FileNotFoundException e) {
//                e.printStackTrace();
//            }

        }


        private DataSet removeExtremalValues(DataSet data1) {
            double[][] data2 = data1.getDoubleData().transpose().toArray();
            int[] keep = new int[data2[0].length];
            Arrays.fill(keep, 1);

            for (int j = 0; j < data2.length; j++) {
                double[] col = data2[j];
                double mean = mean(col);
                double std = sd(col);

                for (int i = 0; i < col.length; i++) {
                    if (col[i] < mean - 3 * std || col[i] > mean + 3 * std) {
                        keep[i] = 0;
                    }
                }
            }

            int sum = 0;
            for (int i = 0; i < keep.length; i++) sum++;

            int[] rows = new int[sum];
            int t = 0;
            for (int i = 0; i < keep.length; i++) {
                rows[t++] = i;
            }

            data1 = data1.subsetRows(rows);
            return data1;
        }

        @Override
        public Graph getTrueGraph(int index) {
            return graphs.get(index);
        }

        @Override
        public DataModel getDataModel(int index) {
            return dataSets.get(index);
        }

        @Override
        public DataModel getDataModelWithLatents(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getDescription() {
            return "Load data sets and graphs from a directory" + (!("".equals(description)) ? ": " + description : "");

//        try {
//            File file = new File(path, "parameters.txt");
//            BufferedReader r = new BufferedReader(new FileReader(file));
//
//            StringBuilder b = new StringBuilder();
//            b.append("Load data sets and graphs from a directory.").append("\n\n");
//            String line;
//
//            while ((line = r.readLine()) != null) {
//                if (line.trim().isEmpty()) continue;
//                b.append(line).append("\n");
//            }
//
//            return b.toString();
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
        }

        @Override
        public List<String> getParameters() {
            return usedParameters;
        }

        @Override
        public int getNumDataModels() {
            return dataSets.size();
        }

        @Override
        public DataType getDataType() {
            boolean continuous = false;
            boolean discrete = false;
            boolean mixed = false;

            for (DataSet dataSet : dataSets) {
                if (dataSet.isContinuous()) continuous = true;
                if (dataSet.isDiscrete()) discrete = true;
                if (dataSet.isMixed()) mixed = true;
            }

            if (mixed) return DataType.Mixed;
            else if (continuous && discrete) return DataType.Mixed;
            else if (continuous) return DataType.Continuous;
            else if (discrete) return DataType.Discrete;

            return DataType.Mixed;
        }

        public String getDataFileName() {
            return dataFileName;
        }
    }

    @Test
    public void test8() {
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

        String[] manipVars = {"cd3", "cd28", "icam2", "aktinhib", "g0076", "psitect", "u0126", "ly", "pma", "b2camp"};

        String[][] manip = {
                {"cd3", "cd28"},
                {"cd3", "cd28", "icam2"},
                {"cd3", "cd28", "aktinhib"},
                {"cd3", "cd28", "g0076"},
                {"cd3", "cd28", "psitect"},
                {"cd3", "cd28", "u0126"},
                {"cd3", "cd28", "ly"},
                {"pma"},
                {"cd3", "cd28", "b2camp"}
        };

        List<DataSet> allData = new ArrayList<>();

        for (int f = 0; f < paths.size(); f++) {

            File file = new File(paths.get(f));

            System.out.println(file.getName());

            DataSet data1;

            try {
                DataReader reader = new DataReader();
                reader.setVariablesSupplied(true);
                reader.setDelimiter(DelimiterType.WHITESPACE);
                data1 = reader.parseTabular(file);

                for (Node node : data1.getVariables()) {
                    node.setName(node.getName().toLowerCase());
                    if ("praf".equals(node.getName())) node.setName("raf");
                    if ("pmek".equals(node.getName())) node.setName("mek");
                    if ("plcg".equals(node.getName())) node.setName("plc");
                    if ("p44/42".equals(node.getName())) node.setName("erk");
                    if ("pakts473".equals(node.getName())) node.setName("akt");
                    if ("pjnk".equals(node.getName())) node.setName("jnk");
                }

//                data1 = DataUtils.getNonparanormalTransformed(data1);
                data1 = DataUtils.logData(data1, 10);

                List<Node> allVars = new ArrayList<>();

                allVars.addAll(data1.getVariables());

                for (String var : manipVars) {
                    allVars.add(new DiscreteVariable(var, 2));
                }

                DataSet data2 = new BoxDataSet(new DoubleDataBox(data1.getNumRows(), allVars.size()), allVars);

                for (int i = 0; i < data1.getNumRows(); i++) {
                    for (int j = 0; j < data1.getNumColumns(); j++) {
                        data2.setDouble(i, j, data1.getDouble(i, j));
                    }

                    for (String s : manipVars) {
                        int col = data2.getColumn(data2.getVariable(s));
                        data2.setInt(i, col, 0);
                    }

                    for (String s : manip[f]) {
                        int col = data2.getColumn(data2.getVariable(s));
                        data2.setInt(i, col, 1);
                    }
                }

                allData.add(data2);
            } catch (IOException e) {
                e.printStackTrace();
            }


        }

        DataSet concatenate = DataUtils.concatenate(allData);

        System.out.println(concatenate);

        try {
            String name = "data.with.discrete.latents.individually.log0";

            String datadir = "/Users/user/Downloads/sachsgraphs/sachs.data/" + name;
            File _datadir = new File(datadir);
            _datadir.mkdirs();
            PrintStream out = new PrintStream(new File(_datadir, name));
            out.println(concatenate);
            out.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test9() {
        try {
            String dir = "/Users/user/Documents/MATLAB";

            List<Node> nodes = new ArrayList<>();

            String[] nodeNames = {"raf", "mek", "plc", "pip2", "pip3", "erk", "akt", "pka", "pkc", "p38", "jnk"};

            int[][] pos = {
                    {345, 120},
                    {360, 210},
                    {45, 180},
                    {60, 380},
                    {120, 300},
                    {345, 285},
                    {285, 360},
                    {105, 105},
                    {240, 30},
                    {200, 270},
                    {120, 210}
            };

            for (int i = 0; i < 11; i++) {
                ContinuousVariable node = new ContinuousVariable(nodeNames[i]);
                node.setCenter(pos[i][0], pos[i][1]);
                nodes.add(node);
            }

            for (int index = 1; index <= 9; index++) {
                Graph g = new EdgeListGraph(nodes);

                File file = new File(dir, "sachs.new.B" + index + ".txt");

                DataReader reader = new DataReader();
                reader.setVariablesSupplied(false);
                reader.setDelimiter(DelimiterType.COMMA);

                DataSet dataSet = reader.parseTabular(file);

                for (int j = 0; j < 11; j++) {
                    for (int i = 0; i < 11; i++) {
                        if (abs(dataSet.getDouble(i, j)) > 0.00) {
                            g.addDirectedEdge(nodes.get(j), nodes.get(i));
                        }
                    }
                }

//                GraphUtils.circleLayout(g, 200, 200, 175);

                final File dir1 = new File("/Users/user/Downloads/sachsgraphs/twostepindividual3");
                dir1.mkdirs();

                GraphUtils.saveGraph(g, new File(dir1, "A" + index + ".txt"), false);

                PngWriter.writePng(g, new File(dir1, "A" + index + ".png"));

            }
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    @Test
    public void test10() {
        List<String> paths = new ArrayList<>();

        final String dir0 = "/Users/user/Downloads/sachsgraphs/sachs.data";
        final String dir = dir0 + "/main.result";

        paths.add("1. cd3cd28.txt");
        paths.add("2. cd3cd28icam2.txt");
        paths.add("3. cd3cd28+aktinhib.txt");
        paths.add("4. cd3cd28+g0076.txt");
        paths.add("5. cd3cd28+psitect.txt");
        paths.add("6. cd3cd28+u0126.txt");
        paths.add("7. cd3cd28+ly.txt");
        paths.add("8. pma.txt");
        paths.add("9. b2camp.txt");

        try {
            for (String path : paths) {

                DataReader reader = new DataReader();
                reader.setVariablesSupplied(true);
                reader.setDelimiter(DelimiterType.TAB);
                DataSet data = reader.parseTabular(new File(dir, path));

                data = DataUtils.logData(data, 1);

//                data = DataUtils.center(data);

                File dir2 = new File(dir0 + "/logged1");
                dir2.mkdirs();

                final File file = new File(dir2, path);
                System.out.println(file.getAbsolutePath());
                DataWriter.writeRectangularData(data, new FileWriter(file), '\t');
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    @Test
    public void testMakeALittleTable() {
//        final String trueName = "ground.truth.sachs.txt";
        final String trueName = "supplemented.ground.truth.txt";

        System.out.println("Comparing to " + trueName);

        final File dir = new File("/Users/user/Downloads/files.for.fask.sachs.report/txt");
        Graph trueGraph = GraphUtils.loadGraphTxt(new File(dir, trueName));

        List<Statistic> statistics = new ArrayList<>();

        String[] estNames = new String[]{
                "sachs.model.txt",
                "figure7.txt",
                "figure11.txt",
                "glasso.txt",
                "aragam.continuous.txt",
                "aragam.discrete.txt",
                "henao.txt",
                "miller.txt",
                "desgranges.txt",
                "mooij.txt",
                "cgnn.txt",
                "sam.txt"
        };

        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecisionIgnore2c());
        statistics.add(new ArrowheadRecall());

        for (String estName : estNames) {
            Graph estGraph = GraphUtils.loadGraphTxt(new File(dir, estName));
            estGraph = GraphUtils.replaceNodes(estGraph, trueGraph.getNodes());

            double adjFp = statistics.get(0).getValue(trueGraph, estGraph);
            double adjFn = statistics.get(1).getValue(trueGraph, estGraph);
            double ahTp = statistics.get(2).getValue(trueGraph, estGraph);
            double ahFp = statistics.get(3).getValue(trueGraph, estGraph);

            System.out.println(estName + "\t" + adjFp + "\t" + adjFn + "\t" + ahTp + "\t" + ahFp);

        }

    }
}
