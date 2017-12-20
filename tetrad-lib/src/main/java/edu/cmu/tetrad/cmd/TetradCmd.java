///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.cmd;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesEstimator;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs several algorithm from Tetrad. Documentation is available
 * in the wiki of the Tetrad project on GitHub. This will be replaced by
 * the package tetrad-cli.
 *
 * @author Joseph Ramsey
 */
public final class TetradCmd {
    private String algorithmName;
    private String dataFileName;
    private String knowledgeFileName;
    private String dataTypeName;
    private String graphXmlFilename;
    private String graphTxtFilename;
    private String initialGraphTxtFilename;
    private int depth = -1;
    private double significance = 0.05;
    private DataSet data;
    private ICovarianceMatrix covarianceMatrix;
    private String outputStreamPath;
    private PrintStream out = System.out;
    private String seed;
    private String numNodes = "5";
    private String numEdges = "5";
    private IKnowledge knowledge = new Knowledge2();
    private boolean whitespace = false;
    private boolean verbose = false;
    private double samplePrior = 1.0;
    private double structurePrior = 1.0;
    private double penaltyDiscount = 1.0;
    private TestType testType = TestType.TETRAD_DELTA;
    private Graph initialGraph;
    private boolean rfciUsed = false;
    private boolean nodsep = false;
    private boolean useCovariance = true;
    private boolean silent = false;
    private boolean useConditionalCorrelation = false;

    public TetradCmd(String[] argv) {
        readArguments(new StringArrayTokenizer(argv));

        setOutputStream();
//        loadDataSelect();
        runAlgorithm();

        if (out != System.out) {
            out.close();
        }
    }

    private void setOutputStream() {
        if (outputStreamPath == null) {
            return;
        }

        File file = new File(outputStreamPath);

        try {
            out = new PrintStream(new FileOutputStream(file));
        } catch (FileNotFoundException e) {
            throw new IllegalStateException(
                    "Could not create a logfile at location " +
                            file.getAbsolutePath()
            );
        }
    }

    private void readArguments(StringArrayTokenizer tokenizer) {
        while (tokenizer.hasToken()) {
            String token = tokenizer.nextToken();

            if ("-data".equalsIgnoreCase(token)) {
                String argument = tokenizer.nextToken();

                if (argument.startsWith("-")) {
                    throw new IllegalArgumentException(
                            "'-data' tag must be followed " +
                                    "by an argument indicating the path to the data " +
                                    "file."
                    );
                }

                dataFileName = argument;
                useCovariance = false;
            } else if ("-covariance".equalsIgnoreCase(token)) {
                String argument = tokenizer.nextToken();

                if (argument.startsWith("-")) {
                    throw new IllegalArgumentException(
                            "'-data' tag must be followed " +
                                    "by an argument indicating the path to the data " +
                                    "file."
                    );
                }

                dataFileName = argument;
                useCovariance = true;
                dataTypeName = "continuous";
            } else if ("-datatype".equalsIgnoreCase(token)) {
                String argument = tokenizer.nextToken();

                if (argument.startsWith("-")) {
                    throw new IllegalArgumentException(
                            "'-datatype' tag must be followed " +
                                    "by either 'discrete' or 'continuous'."
                    );
                }

                dataTypeName = argument;
            } else if ("-algorithm".equalsIgnoreCase(token)) {
                String argument = tokenizer.nextToken();

                if (argument.startsWith("-")) {
                    throw new IllegalArgumentException(
                            "'-algorithm' tag must be followed " +
                                    "by an algorithm name."
                    );
                }

                algorithmName = argument;
            } else if ("-depth".equalsIgnoreCase(token)) {
                try {
                    String argument = tokenizer.nextToken();

                    if (argument == null) {
                        throw new NumberFormatException();
                    }

                    this.depth = Integer.parseInt(argument);

                    if (this.depth < -1) {
                        throw new IllegalArgumentException(
                                "'depth' must be followed " +
                                        "by an integer >= -1 (-1 means unlimited)."
                        );
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "'depth' must be followed " +
                                    "by an integer >= -1 (-1 means unlimited)."
                    );
                }
            } else if ("-significance".equalsIgnoreCase(token)) {
                try {
                    String argument = tokenizer.nextToken();

                    if (argument.startsWith("-")) {
                        throw new NumberFormatException();
                    }

                    this.significance = Double.parseDouble(argument);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "'-significance' must be " +
                                    "followed by a number in the range [0.0, 1.0]."
                    );
                }
            } else if ("-verbose".equalsIgnoreCase(token)) {
                this.verbose = true;
            } else if ("-outfile".equalsIgnoreCase(token)) {
                String argument = tokenizer.nextToken();

                if (argument.startsWith("-")) {
                    throw new IllegalArgumentException(
                            "'-outfile' tag must be " +
                                    "followed  by an argument indicating the path to the " +
                                    "data file."
                    );
                }

                outputStreamPath = argument;
            } else if ("-seed".equalsIgnoreCase(token)) {
                String argument = tokenizer.nextToken();

                if (argument.startsWith("-")) {
                    throw new IllegalArgumentException(
                            "-seed must be followed by an integer (long) value."
                    );
                }

                seed = argument;
            } else if ("-numNodes".equals(token)) {
                String argument = tokenizer.nextToken();

                if (argument.startsWith("-")) {
                    throw new IllegalArgumentException(
                            "-numNodes must be followed by an integer >= 3.");
                }

                numNodes = argument;
            } else if ("-numEdges".equals(token)) {
                String argument = tokenizer.nextToken();

                if (argument.startsWith("-")) {
                    throw new IllegalArgumentException(
                            "-numEdges must be followed by an integer >= 0.");
                }

                numEdges = argument;
            } else if ("-knowledge".equals(token)) {
                String argument = tokenizer.nextToken();

                if (argument.startsWith("-")) {
                    throw new IllegalArgumentException(
                            "'-knowledge' tag must be followed " +
                                    "by an argument indicating the path to the knowledge " +
                                    "file."
                    );
                }

                knowledgeFileName = argument;
            } else if ("-testtype".equals(token)) {
                String argument = tokenizer.nextToken();

                if (argument.startsWith("-")) {
                    throw new IllegalArgumentException(
                            "'-testType' tag must be followed by 'delta' or 'wishart'");
                }

                switch (argument) {
                    case "delta":
                        testType = TestType.TETRAD_DELTA;
                        break;
                    case "wishart":
                        testType = TestType.TETRAD_WISHART;
                        break;
                    default:
                        throw new IllegalArgumentException("Expecting 'delta' or 'wishart'.");
                }
            } else if ("-graphxml".equals(token)) {
                String argument = tokenizer.nextToken();

                if (argument.startsWith("-")) {
                    throw new IllegalArgumentException(
                            "'-graphxml' tag must be followed " +
                                    "by an argument indicating the path to the file where the graph xml output " +
                                    "is to be written."
                    );
                }

                graphXmlFilename = argument;
            } else if ("-graphtxt".equals(token)) {
                String argument = tokenizer.nextToken();

                if (argument.startsWith("-")) {
                    throw new IllegalArgumentException(
                            "'-graphtxt' tag must be followed " +
                                    "by an argument indicating the path to the file where the graph txt output " +
                                    "is to be written."
                    );
                }

                graphTxtFilename = argument;
            } else if ("-initialgraphtxt".equals(token)) {
                String argument = tokenizer.nextToken();

                if (argument.startsWith("-")) {
                    throw new IllegalArgumentException(
                            "'-initialgraphtxt' tag must be followed " +
                                    "by an argument indicating the path to the file where the graph txt output " +
                                    "is to be written."
                    );
                }

                initialGraphTxtFilename = argument;
            } else if ("-whitespace".equals(token)) {
                whitespace = true;
            } else if ("-sampleprior".equals(token)) {
                try {
                    String argument = tokenizer.nextToken();

                    if (argument.startsWith("-")) {
                        throw new IllegalArgumentException(
                                "'-sampleprior' tag must be followed " +
                                        "by an argument indicating the BDEU structure prior."
                        );
                    }

                    samplePrior = Double.parseDouble(argument);

                    if (samplePrior < 0) {
                        throw new IllegalArgumentException("Sample prior must be >= 0.");
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Not a number.");
                }
            } else if ("-structureprior".equals(token)) {
                try {
                    String argument = tokenizer.nextToken();

                    if (argument.startsWith("-")) {
                        throw new IllegalArgumentException(
                                "'-structureprior' tag must be followed " +
                                        "by an argument indicating the BDEU sample prior."
                        );
                    }

                    structurePrior = Double.parseDouble(argument);

                    if (structurePrior < 0) {
                        throw new IllegalArgumentException("Structure prior must be >= 0.");
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Not a number.");
                }
            } else if ("-penaltydiscount".equals(token)) {
                try {
                    String argument = tokenizer.nextToken();

                    if (argument.startsWith("-")) {
                        throw new IllegalArgumentException(
                                "'-penaltydiscount' tag must be followed " +
                                        "by an argument indicating penalty discount."
                        );
                    }

                    penaltyDiscount = Double.parseDouble(argument);

                    if (penaltyDiscount <= 0) {
                        throw new IllegalArgumentException("Penalty discount must be > 0.");
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Not a number.");
                }
            } else if ("-rfci".equalsIgnoreCase(token)) {
                this.rfciUsed = true;
            } else if ("-nodsep".equalsIgnoreCase(token)) {
                this.nodsep = true;
            } else if ("-silent".equalsIgnoreCase(token)) {
                this.silent = true;
            } else if ("-condcorr".equalsIgnoreCase(token)) {
                this.useConditionalCorrelation = true;
            } else {
                throw new IllegalArgumentException(
                        "Unexpected argument: " + token);
            }

        }
    }

    private void loadData() {
        if (dataFileName == null) {
            throw new IllegalStateException("No data file was specified.");
        }

        if (dataTypeName == null) {
            throw new IllegalStateException(
                    "No data type (continuous/discrete) " + "was specified.");
        }

        outPrint("Loading data from " + dataFileName + ".");

//        if ("continuous".equalsIgnoreCase(dataTypeName)) {
//            outPrint("Data type = continuous.");
//        } else if ("discrete".equalsIgnoreCase(dataTypeName)) {
//            outPrint("Data type = discrete.");
//        } else {
//            throw new IllegalStateException(
//                    "Data type was expected to be either " +
//                            "'continuous' or 'discrete'."
//            );
//        }

        File file = new File(dataFileName);

        try {
            try {
//                    List<Node> knownVariables = null;
//                    RectangularDataSet data = DataLoaders.loadDiscreteData(file,
//                            DelimiterType.WHITESPACE_OR_COMMA, "//",
//                            knownVariables);

                DataReader reader = new DataReader();
                reader.setMaxIntegralDiscrete(Integer.MAX_VALUE);

                if (whitespace) {
                    reader.setDelimiter(DelimiterType.WHITESPACE);
                } else {
                    reader.setDelimiter(DelimiterType.TAB);
                }

                if (useCovariance) {
                    ICovarianceMatrix cov = reader.parseCovariance(file);
                    this.covarianceMatrix = cov;
                } else {
                    DataSet data = reader.parseTabular(file);
                    outPrint("# variables = " + data.getNumColumns() +
                            ", # cases = " + data.getNumRows());
                    this.data = data;
                }

//                systemPrint(data);

                if (initialGraphTxtFilename != null) {
                    initialGraph = GraphUtils.loadGraphTxt(new File(initialGraphTxtFilename));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Could not load file at " + file.getAbsolutePath());
        }
    }

    private void loadKnowledge() {
        if (knowledgeFileName == null) {
            throw new IllegalStateException("No data file was specified.");
        }

        try {
            File knowledgeFile = new File(knowledgeFileName);

            CharArrayWriter writer = new CharArrayWriter();

            FileReader fr = new FileReader(knowledgeFile);
            int i;

            while ((i = fr.read()) != -1) {
                writer.append((char) i);
            }

            DataReader reader = new DataReader();
            char[] chars = writer.toCharArray();

            String x = new String(chars);
            systemPrint(x);

            this.knowledge = reader.parseKnowledge(chars);
        } catch (Exception e) {
            throw new RuntimeException("Couldn't read knowledge.");
        }
    }

    private void systemPrint(String x) {
        if (!silent) {
            System.out.println(x);
        }
    }

    private void outPrint(String x) {
        if (!silent) {
            out.println(x);
        }
    }

    private void runAlgorithm() {

        if (dataFileName != null) {
            loadData();
        }

        if (knowledgeFileName != null) {
            loadKnowledge();
        }

        if ("pc".equalsIgnoreCase(algorithmName)) {
            runPc();
        } else if ("fask".equalsIgnoreCase(algorithmName)) {
            runFask();
        } else if ("pc.stable".equalsIgnoreCase(algorithmName)) {
            runPcStable();
        } else if ("cpc".equalsIgnoreCase(algorithmName)) {
            runCpc();
        } else if ("fci".equalsIgnoreCase(algorithmName)) {
            runFci();
        } else if ("cfci".equalsIgnoreCase(algorithmName)) {
            runCfci();
        } else if ("ccd".equalsIgnoreCase(algorithmName)) {
            runCcd();
        } else if ("fges".equalsIgnoreCase(algorithmName)) {
            runFges();
        } else if ("bayes_est".equalsIgnoreCase(algorithmName)) {
            runBayesEst();
        } else if ("fofc".equalsIgnoreCase(algorithmName)) {
            runFofc();
        } else if ("randomDag".equalsIgnoreCase(algorithmName)) {
            printRandomDag();
        } else {
            TetradLogger.getInstance().reset();
            TetradLogger.getInstance().removeOutputStream(System.out);
            throw new IllegalStateException("No algorithm was specified.");
        }

//        TetradLogger.getInstance().setForceLog(false);
        TetradLogger.getInstance().removeOutputStream(System.out);

    }

    private void printRandomDag() {
        if (seed != null) {
            long _seed;

            try {
                _seed = Long.parseLong(seed);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Seed must be an integer (actually, long) value.");
            }

            RandomUtil.getInstance().setSeed(_seed);
        }

        int _numNodes;

        try {
            _numNodes = Integer.parseInt(numNodes);
        } catch (NumberFormatException e) {
            throw new RuntimeException("numNodes must be an integer.");
        }

        int _numEdges;

        try {
            _numEdges = Integer.parseInt(numEdges);
        } catch (NumberFormatException e) {
            throw new RuntimeException("numEdges must be an integer.");
        }

        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < _numNodes; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Dag dag;

        do {
            dag = new Dag(GraphUtils.randomGraph(nodes, 0, _numEdges, 30,
                    15, 15, false));
        } while (dag.getNumEdges() < _numEdges);

        String xml = GraphUtils.graphToXml(dag);
        systemPrint(xml);
    }

    private void runPc() {
        if (this.data == null && this.covarianceMatrix == null) {
            throw new IllegalStateException("Data did not load correctly.");
        }

        if (verbose) {
            systemPrint("PC");
            systemPrint(getKnowledge().toString());
            systemPrint(getVariables().toString());

            TetradLogger.getInstance().addOutputStream(System.out);

            TetradLogger.getInstance().setEventsToLog("info", "independencies", "knowledgeOrientations",
                    "impliedOrientations", "graph");
//            TetradLogger.getInstance().setForceLog(true);

            TetradLogger.getInstance().log("info", "Testing it.");
        }

        Pc pc = new Pc(getIndependenceTest());
        pc.setDepth(getDepth());
        pc.setKnowledge(getKnowledge());
        pc.setVerbose(verbose);

        // Convert back to Graph..
        Graph resultGraph = pc.search();

        // PrintUtil outputStreamPath problem and graphs.
        outPrint("\nResult graph:");
        outPrint(resultGraph.toString());

        writeGraph(resultGraph);
    }

    private void runFask() {
        if (this.data == null && this.covarianceMatrix == null) {
            throw new IllegalStateException("Data did not load correctly.");
        }

        if (verbose) {
            systemPrint("FASK");
            systemPrint(getKnowledge().toString());
            systemPrint(getVariables().toString());

            TetradLogger.getInstance().addOutputStream(System.out);

            TetradLogger.getInstance().setEventsToLog("info", "independencies", "knowledgeOrientations",
                    "impliedOrientations", "graph");
//            TetradLogger.getInstance().setForceLog(true);

            TetradLogger.getInstance().log("info", "Testing it.");
        }

        SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(data, false));
        score.setPenaltyDiscount(penaltyDiscount);
        Fask pc = new Fask(data, score);
        pc.setAlpha(significance);
        pc.setPenaltyDiscount(penaltyDiscount);
        pc.setDepth(depth);
        pc.setKnowledge(getKnowledge());

        // Convert back to Graph..
        Graph resultGraph = pc.search();

        // PrintUtil outputStreamPath problem and graphs.
        outPrint("\nResult graph:");
        outPrint(resultGraph.toString());

        writeGraph(resultGraph);
    }

    private void runPcStable() {
        if (this.data == null && this.covarianceMatrix == null) {
            throw new IllegalStateException("Data did not load correctly.");
        }

        if (verbose) {
            systemPrint("PC-Stable");
            systemPrint(getKnowledge().toString());
            systemPrint(getVariables().toString());

            TetradLogger.getInstance().addOutputStream(System.out);

            TetradLogger.getInstance().setEventsToLog("info", "independencies", "knowledgeOrientations",
                    "impliedOrientations", "graph");
//            TetradLogger.getInstance().setForceLog(true);

            TetradLogger.getInstance().log("info", "Testing it.");
        }

        PcStable pc = new PcStable(getIndependenceTest());
        pc.setDepth(getDepth());
        pc.setKnowledge(getKnowledge());
        pc.setVerbose(verbose);

        // Convert back to Graph..
        Graph resultGraph = pc.search();

        // PrintUtil outputStreamPath problem and graphs.
        outPrint("\nResult graph:");
        outPrint(resultGraph.toString());

        writeGraph(resultGraph);
    }

    private void runFges() {
        if (this.data == null && this.covarianceMatrix == null) {
            throw new IllegalStateException("Data did not load correctly.");
        }

        if (verbose) {
            systemPrint("FGES");
            systemPrint(getKnowledge().toString());
            systemPrint(getVariables().toString());

            TetradLogger.getInstance().addOutputStream(System.out);

            TetradLogger.getInstance().setEventsToLog("info", "independencies", "knowledgeOrientations",
                    "impliedOrientations", "graph");
//            TetradLogger.getInstance().setForceLog(true);

            TetradLogger.getInstance().log("info", "Testing it.");
        }

        Fges fges;

        if (useCovariance) {
            SemBicScore fgesScore = new SemBicScore(covarianceMatrix);
            fgesScore.setPenaltyDiscount(penaltyDiscount);
            fges = new Fges(fgesScore);

        } else {
            if (data.isDiscrete()) {
                BDeuScore score = new BDeuScore(data);
                score.setSamplePrior(samplePrior);
                score.setStructurePrior(structurePrior);

                fges = new Fges(score);
            } else if (data.isContinuous()) {
                SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(data));
                score.setPenaltyDiscount(penaltyDiscount);
                fges = new Fges(score);
            } else {
                throw new IllegalArgumentException();
            }
        }

        if (initialGraph != null) {
            fges.setInitialGraph(initialGraph);
        }

        fges.setKnowledge(getKnowledge());

        // Convert back to Graph..
        Graph resultGraph = fges.search();

        // PrintUtil outputStreamPath problem and graphs.
        outPrint("\nResult graph:");
        outPrint(resultGraph.toString());

        writeGraph(resultGraph);
    }

    private void runCpc() {
        if (this.data == null && this.covarianceMatrix == null) {
            throw new IllegalStateException("Data did not load correctly.");
        }

        if (verbose) {
            systemPrint("CPC");
            systemPrint(getKnowledge().toString());
            systemPrint(getVariables().toString());

            TetradLogger.getInstance().addOutputStream(System.out);

            TetradLogger.getInstance().setEventsToLog("info", "independencies", "knowledgeOrientations",
                    "impliedOrientations", "graph");
//            TetradLogger.getInstance().setForceLog(true);

            TetradLogger.getInstance().log("info", "Testing it.");
        }

        Cpc pc = new Cpc(getIndependenceTest());
        pc.setDepth(getDepth());
        pc.setKnowledge(getKnowledge());
        pc.setVerbose(verbose);

        // Convert back to Graph..
        Graph resultGraph = pc.search();

        // PrintUtil outputStreamPath problem and graphs.
        outPrint("\nResult graph:");
        outPrint(resultGraph.toString());

        writeGraph(resultGraph);
    }

    private void runFci() {
        if (this.data == null && this.covarianceMatrix == null) {
            throw new IllegalStateException("Data did not load correctly.");
        }

        if (verbose) {
            systemPrint("FCI");
            systemPrint(getKnowledge().toString());
            systemPrint(getVariables().toString());

            TetradLogger.getInstance().addOutputStream(System.out);

            TetradLogger.getInstance().setEventsToLog("info", "independencies", "knowledgeOrientations",
                    "impliedOrientations", "graph");
//            TetradLogger.getInstance().setForceLog(true);

            TetradLogger.getInstance().log("info", "Testing it.");
        }

        if (rfciUsed) {
            Rfci fci = new Rfci(getIndependenceTest());
            fci.setDepth(getDepth());
            fci.setKnowledge(getKnowledge());
            fci.setVerbose(verbose);

            // Convert back to Graph..
            Graph resultGraph = fci.search();

            // PrintUtil outputStreamPath problem and graphs.
            outPrint("\nResult graph:");
            outPrint(resultGraph.toString());

            writeGraph(resultGraph);
        } else {
            Fci fci = new Fci(getIndependenceTest());
            fci.setDepth(getDepth());
            fci.setKnowledge(getKnowledge());
            fci.setPossibleDsepSearchDone(!nodsep);
            fci.setVerbose(verbose);

            // Convert back to Graph..
            Graph resultGraph = fci.search();

            // PrintUtil outputStreamPath problem and graphs.
            outPrint("\nResult graph:");
            outPrint(resultGraph.toString());

            writeGraph(resultGraph);

        }
    }

    private void runCfci() {
        if (this.data == null && this.covarianceMatrix == null) {
            throw new IllegalStateException("Data did not load correctly.");
        }

        if (verbose) {
            systemPrint("CFCI");
            systemPrint(getKnowledge().toString());
            systemPrint(getVariables().toString());

            TetradLogger.getInstance().addOutputStream(System.out);

            TetradLogger.getInstance().setEventsToLog("info", "independencies", "colliderOrientations",
                    "impliedOrientations", "graph");
//            TetradLogger.getInstance().setForceLog(true);

            TetradLogger.getInstance().log("info", "Testing it.");
        }

        Cfci fci = new Cfci(getIndependenceTest());
        fci.setDepth(getDepth());
        fci.setKnowledge(getKnowledge());
        fci.setDepth(depth);
        fci.setVerbose(verbose);

        // Convert back to Graph..
        Graph resultGraph = fci.search();

        // PrintUtil outputStreamPath problem and graphs.
        outPrint("\nResult graph:");
        outPrint(resultGraph.toString());

        writeGraph(resultGraph);
    }

    private void runCcd() {
        if (this.data == null && this.covarianceMatrix == null) {
            throw new IllegalStateException("Data did not load correctly.");
        }

        if (verbose) {
            systemPrint("CCD");
            systemPrint(getKnowledge().toString());
            systemPrint(getVariables().toString());

            TetradLogger.getInstance().addOutputStream(System.out);

            TetradLogger.getInstance().setEventsToLog("info", "independencies", "knowledgeOrientations",
                    "impliedOrientations", "graph");
//            TetradLogger.getInstance().setForceLog(true);

            TetradLogger.getInstance().log("info", "Testing it.");
        }

        Ccd ccd = new Ccd(getIndependenceTest());
        ccd.setDepth(getDepth());

        // Convert back to Graph..
        Graph resultGraph = ccd.search();

        // PrintUtil outputStreamPath problem and graphs.
        outPrint("\nResult graph:");
        outPrint(resultGraph.toString());

        writeGraph(resultGraph);
    }

    private void runBayesEst() {
        if (this.data == null && this.covarianceMatrix != null) {
            throw new IllegalStateException("Continuous tabular data required.");
        }

        if (this.data == null) {
            throw new IllegalStateException("Data did not load correctly.");
        }

        if (!this.data.isDiscrete()) {
            outPrint("Please supply discrete data.");
        }

        IndependenceTest independence = new IndTestChiSquare(data, significance);

        Cpc cpc = new Cpc(independence);
        cpc.setVerbose(verbose);
        Graph pattern = cpc.search();

        outPrint("Found this pattern: " + pattern);

        Dag dag = new Dag(SearchGraphUtils.dagFromPattern(pattern));

        outPrint("Chose this DAG: " + dag);

        BayesPm pm = new BayesPm(dag);

        MlBayesEstimator est = new MlBayesEstimator();
        BayesIm im = est.estimate(pm, data);

        outPrint("Estimated IM: " + im);

    }

    private void runFofc() {
        FindOneFactorClusters fofc;

        if (this.data != null) {
            fofc = new FindOneFactorClusters(this.data,
                    this.testType, FindOneFactorClusters.Algorithm.GAP, significance);
            if (!this.data.isContinuous()) {
                outPrint("Please supply continuous data.");
            }
        } else if (this.covarianceMatrix != null) {
            fofc = new FindOneFactorClusters(this.covarianceMatrix,
                    this.testType, FindOneFactorClusters.Algorithm.GAP, significance);
        } else {
            throw new IllegalStateException("Data did not load correctly.");
        }

        fofc.search();
        List<List<Node>> clusters = fofc.getClusters();

        systemPrint("Clusters:");

        for (int i = 0; i < clusters.size(); i++) {
            systemPrint((i + 1) + ": " + clusters.get(i));
        }
    }

    private void writeGraph(Graph resultGraph) {
        if (graphXmlFilename != null) {
            try {
                String xml = GraphUtils.graphToXml(resultGraph);

                File file = new File(graphXmlFilename);

                PrintWriter out = new PrintWriter(file);

                out.print(xml);
                out.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        if (graphTxtFilename != null) {
            try {
                File file = new File(graphTxtFilename);

                PrintWriter out = new PrintWriter(file);

                out.print(resultGraph);
                out.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private IndependenceTest getIndependenceTest() {
        IndependenceTest independence;

        if (useCovariance) {
            independence = new IndTestFisherZ(covarianceMatrix, significance);
        } else {
            if (this.data.isDiscrete()) {
                independence = new IndTestChiSquare(data, significance);
            } else if (this.data.isContinuous()) {
                if (useConditionalCorrelation) {

                    independence = new IndTestConditionalCorrelation(data, significance);
                    System.err.println("Using Conditional Correlation");

                } else {

                    independence = new IndTestFisherZ(data, significance);
                }


            } else {
                throw new IllegalStateException(
                        "Data must be either continuous or " + "discrete.");
            }
        }
        return independence;
    }

//    private Level convertToLevel(String level) {
//        if ("severe".equalsIgnoreCase(level)) {
//            return Level.SEVERE;
//        } else if ("warning".equalsIgnoreCase(level)) {
//            return Level.WARNING;
//        } else if ("info".equalsIgnoreCase(level)) {
//            return Level.INFO;
//        } else if ("config".equalsIgnoreCase(level)) {
//            return Level.CONFIG;
//        } else if ("fine".equalsIgnoreCase(level)) {
//            return Level.FINE;
//        } else if ("finer".equalsIgnoreCase(level)) {
//            return Level.FINER;
//        } else if ("finest".equalsIgnoreCase(level)) {
//            return Level.FINEST;
//        }
//
//        throw new IllegalArgumentException("Level must be one of 'Severe', " +
//                "'Warning', 'Info', 'Config', 'Fine', 'Finer', 'Finest'.");
//    }

    public static void main(final String[] argv) {
        new TetradCmd(argv);
    }

    private int getDepth() {
        return depth;
    }

    private IKnowledge getKnowledge() {
        return knowledge;
    }

    private List<Node> getVariables() {
        if (data != null) {
            return data.getVariables();
        } else if (covarianceMatrix != null) {
            return covarianceMatrix.getVariables();
        }

        throw new IllegalArgumentException("Data nor covariance specified.");
    }

    /**
     * Allows an array of strings to be treated as a tokenizer.
     */
    private static class StringArrayTokenizer {
        String[] tokens;
        int i = -1;

        public StringArrayTokenizer(String[] tokens) {
            this.tokens = tokens;
        }

        public boolean hasToken() {
            return i < tokens.length - 1;
        }

        public String nextToken() {
            return tokens[++i];
        }
    }
}





