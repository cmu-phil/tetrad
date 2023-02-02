package edu.cmu.tetrad.calibration;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.LargeScaleSimulation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


//MP: Each BootstrapWorker object will run the RFCI method on one Bootstrap and append the results to the results list


public class DataForCalibration_RFCI {
    PrintWriter outProb;
    private PrintWriter outGraph;
    private PrintWriter outPag;
    public int depth = 5;


    public static void main(String[] args) throws IOException {


        String algorithm = "";
        double alpha = 0.05, numLatentConfounders = 0.1;
        int numVars = 0, numCases = 0, edgesPerNode = 0, numBootstrapSamples = 0, seedIndex = -1;
        String data_path = System.getProperty("user.dir");

        System.out.println(Arrays.asList(args));

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-v":
                    numVars = Integer.parseInt(args[i + 1]);
                    break;

                case "-p":
                    edgesPerNode = Integer.parseInt(args[i + 1]);
                    break;

                case "-c":
                    numCases = Integer.parseInt(args[i + 1]);
                    break;

                case "-l":
                    numLatentConfounders = Double.parseDouble(args[i + 1]);
                    break;

                case "-b":
                    numBootstrapSamples = Integer.parseInt(args[i + 1]);
                    break;

                case "-s":
                    seedIndex = Integer.parseInt(args[i + 1]);
                    break;

                case "-a":
                    algorithm = args[i + 1];
                    break;

                case "-alpha":
                    alpha = Double.parseDouble(args[i + 1]);
                    break;
                case "-dir":
                    data_path = args[i + 1];
                    break;
            }
        }
        //RandomUtil.getInstance().setSeed(seed[seedIndex]);

        int numEdges = numVars * edgesPerNode;
        DataForCalibration_RFCI DFC = new DataForCalibration_RFCI();

        boolean probFileExists = DFC.checkProbFileExists("RandomGraph", numVars, numEdges, numCases, numBootstrapSamples, algorithm, seedIndex, numLatentConfounders, alpha, data_path);
        if (probFileExists) {
            String dirname = data_path +  "/CalibrationConstraintBased/"+ algorithm + "/" + "RandomGraph" +"-Vars" + numVars +  "-Edges" + numEdges +  "-Cases" + numCases +  "-BS" + numBootstrapSamples + "-H" + numLatentConfounders + "-a" + alpha ;
            String probFileName = "probs_v" + numVars +  "_e" + numEdges +  "_c" + numCases +  "_b" + numBootstrapSamples + "_" + seedIndex + ".txt";
            System.out.println("Warning: The program stopped because the Prob File already exists in the following path: \n"+ dirname+ "/" + probFileName);
            return;
        }
        String ConfigString = String.valueOf(Math.random());
        System.out.println(ConfigString + ": Started!");


        int LV = (int) Math.floor(numLatentConfounders * numVars);
        System.out.println("LV: " + LV);
        Graph dag = DFC.makeDAG(numVars, edgesPerNode, LV);

        System.out.println("Graph simulation done");

////        final DagToPag dagToPag = new DagToPag(dag);
//
//
//        // MP: What is it doing? Complete is used to be RFCI, False will result in running FCI
//        dagToPag.setCompleteRuleSetUsed(true);
//
//        Graph truePag = dagToPag.convert();

        Graph truePag = SearchGraphUtils.dagToPag(dag);

        System.out.println("true PAG construction Done!");

        truePag = GraphUtils.replaceNodes(truePag, dag.getNodes());


        // data simulation
        LargeScaleSimulation simulator = new LargeScaleSimulation(dag);
        DataSet data = simulator.simulateDataReducedForm(numCases);

        // To remove the columns related to latent variables from dataset
        data = DataUtils.restrictToMeasured(data);
        System.out.println("Data simulation done");

        System.out.println("Covariance matrix done");

        Graph estPag;

        long time1 = System.currentTimeMillis();

//        if (algorithm.equals("RFCI")) {

        final IndTestFisherZ test = new IndTestFisherZ(data, 0.001);
        final SemBicScore score = new SemBicScore(data);
        score.setPenaltyDiscount(2);

        System.out.println("Starting search with all data");

        BfciFoo fci = new BfciFoo(test, score);
        fci.setVerbose(false);
        fci.setCompleteRuleSetUsed(true);
        fci.setDepth(DFC.depth);
        estPag = fci.search();

        System.out.println("Search done with all data");
//        } else {
//            System.out.println("invalid search algorithm");
//            return;
//        }
        long time2 = System.currentTimeMillis();

        System.out.println("Elapsed (running RFCI on the data): " + (time2 - time1) / 1000 + " sec");


        estPag = GraphUtils.replaceNodes(estPag, truePag.getNodes());

        System.out.println("Generating bootstrap samples from data");
        List<Graph> BNfromBootstrap = new ArrayList<>();


        //MP: Initialize static fields of BootstrapWorker Class
        BootstrapWorker.alpha = alpha;
        BootstrapWorker.DFC = DFC;
        BootstrapWorker.truePag = truePag;
        BootstrapWorker.BootstrapNum = numBootstrapSamples;

        long start, stop;
        start = System.currentTimeMillis();
        for (int i1 = 0; i1 < numBootstrapSamples; i1++) {
            DataSet bootstrapSample = DFC.bootStrapSampling(data, data.getNumRows());
            if (algorithm.equals("RFCI")) {
                BootstrapWorker tmp = new BootstrapWorker(bootstrapSample, BNfromBootstrap);
                BootstrapWorker.addToWaitingList(tmp);
            } else {
                System.out.println("invalid search algorithm");
                return;
            }
        }

        //MP: Running RFCI on the Bootstrap datasets in a multi-thread fashion
        try {
            BootstrapWorker.executeThreads_and_wait();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        stop = System.currentTimeMillis();
        System.out.println("Bootstrap finished in " + (stop - start) + " ms");
        // estimate P_ij
        System.out.println("Probability estimates...");
        EdgeFrequency frequency = new EdgeFrequency(BNfromBootstrap);

        // Writing output files
        boolean set = DFC.setOut("RandomGraph", numVars, numEdges, numCases, numBootstrapSamples, algorithm, seedIndex, numLatentConfounders, alpha, data_path);
        if (!set) {
            return;
        }

        start = System.currentTimeMillis();
        DFC.probDistribution(truePag, estPag, frequency, DFC.outProb, algorithm);
        stop = System.currentTimeMillis();
        System.out.println("probDistribution finished in " + (stop - start) + " ms");
        System.out.println("Writing Probs File: done!");

        DFC.print("Graph: " + dag, DFC.outGraph);
        DFC.print("\n\n", DFC.outGraph);

        DFC.print("Pag:" + truePag, DFC.outPag);
        DFC.print("\n\n", DFC.outPag);


        DFC.outProb.close();
        DFC.outGraph.close();
        DFC.outPag.close();
        System.out.println(ConfigString + ": Done!");

    }


    private void probDistribution(Graph trueBN, Graph gesOut, EdgeFrequency frequency, PrintWriter outP, String algorithm) {

        print("A, B, 0-7, A  B, A --> B, B --> A, A o-> B, B o-> A, A o-o B, A <-> B, A --- B, " + algorithm + " \n", outP);

        Graph complete = new EdgeListGraph(trueBN.getNodes());
        complete.fullyConnect(Endpoint.TAIL);

        for (Edge e : complete.getEdges()) {
            double AnilB;
            double AtoB;
            double BtoA;
            double ACtoB;
            double BCtoA;
            double AccB;
            double AbB;
            double AuB;

            int trueType = 0;
            int estType = 0;

            Node n1 = e.getNode1();
            Node n2 = e.getNode2();

            // compute true edge type for each pair of nodes

            if (trueBN.getEdge(n1, n2) != null) {
                Endpoint p1 = trueBN.getEdge(n1, n2).getEndpoint1();
                Endpoint p2 = trueBN.getEdge(n1, n2).getEndpoint2();

                if (p1 == Endpoint.TAIL && p2 == Endpoint.ARROW) // A -> B
                    trueType = 1;

                else if (p1 == Endpoint.ARROW && p2 == Endpoint.TAIL) // A <- B
                    trueType = 2;

                else if (p1 == Endpoint.CIRCLE && p2 == Endpoint.ARROW) // A o-> B
                    trueType = 3;

                else if (p1 == Endpoint.ARROW && p2 == Endpoint.CIRCLE) // A <-o B
                    trueType = 4;

                else if (p1 == Endpoint.CIRCLE && p2 == Endpoint.CIRCLE) // A o-o B
                    trueType = 5;

                else if (p1 == Endpoint.ARROW && p2 == Endpoint.ARROW) // A <-> B
                    trueType = 6;

                else if (p1 == Endpoint.TAIL && p2 == Endpoint.TAIL) // A -- B
                    trueType = 7;

            }

            // compute probability for each edge type

            Edge e1 = new Edge(n1, n2, Endpoint.NULL, Endpoint.NULL);
            AnilB = frequency.getProbability(e1);

            e1 = new Edge(n1, n2, Endpoint.TAIL, Endpoint.ARROW);
            AtoB = frequency.getProbability(e1);

            e1 = new Edge(n1, n2, Endpoint.ARROW, Endpoint.TAIL);
            BtoA = frequency.getProbability(e1);

            e1 = new Edge(n1, n2, Endpoint.CIRCLE, Endpoint.ARROW);
            ACtoB = frequency.getProbability(e1);

            e1 = new Edge(n1, n2, Endpoint.ARROW, Endpoint.CIRCLE);
            BCtoA = frequency.getProbability(e1);

            e1 = new Edge(n1, n2, Endpoint.CIRCLE, Endpoint.CIRCLE);
            AccB = frequency.getProbability(e1);

            e1 = new Edge(n1, n2, Endpoint.ARROW, Endpoint.ARROW);
            AbB = frequency.getProbability(e1);

            e1 = new Edge(n1, n2, Endpoint.TAIL, Endpoint.TAIL);
            AuB = frequency.getProbability(e1);

            if (gesOut.getEdge(n1, n2) != null) {
                Endpoint p1 = gesOut.getEdge(n1, n2).getEndpoint1();
                Endpoint p2 = gesOut.getEdge(n1, n2).getEndpoint2();

                if (p1 == Endpoint.TAIL && p2 == Endpoint.ARROW) // A -> B
                    estType = 1;

                else if (p1 == Endpoint.ARROW && p2 == Endpoint.TAIL) // A <- B
                    estType = 2;

                else if (p1 == Endpoint.CIRCLE && p2 == Endpoint.ARROW) // A o-> B
                    estType = 3;

                else if (p1 == Endpoint.ARROW && p2 == Endpoint.CIRCLE) // A <-o B
                    estType = 4;

                else if (p1 == Endpoint.CIRCLE && p2 == Endpoint.CIRCLE) // A o-o B
                    estType = 5;

                else if (p1 == Endpoint.ARROW && p2 == Endpoint.ARROW) // A <-> B
                    estType = 6;

                else if (p1 == Endpoint.TAIL && p2 == Endpoint.TAIL) // A -- B
                    estType = 7;
            }
            print(n1 + ", " + n2 + ", " + trueType + ", " + AnilB + ", " +
                    AtoB + ", " + BtoA + ", " +
                    ACtoB + ", " + BCtoA + ", " +
                    AccB + ", " + AbB + ", " + AuB + ", " + estType + "\n", outP);
        }
    }

    public Graph makeDAG(int numVars, double edgesPerNode, int numLatentConfounders) {
        final int numEdges = (int) (numVars * edgesPerNode);

        System.out.println("Making list of vars");

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable(Integer.toString(i)));
            //			vars.add(new DiscreteVariable(Integer.toString(i)));

        }

        System.out.println("Making dag");
        return RandomGraph.randomGraphRandomForwardEdges(vars, numLatentConfounders, numEdges, 30, 15, 15, false, true);//randomGraphRandomForwardEdges(vars, 0,numEdges);
    }

    public DataSet bootStrapSampling(DataSet data, int bootsrapSampleSize) {
        return DataUtils.getBootstrapSample(data, bootsrapSampleSize);
    }

    public Graph learnBNRFCI(DataSet bootstrapSample, int depth, Graph truePag) {
        final IndTestFisherZ test = new IndTestFisherZ(bootstrapSample, 0.001);
        final SemBicScore score = new SemBicScore(bootstrapSample);
        score.setPenaltyDiscount(2);

        System.out.println("Starting search with a bootstrap");

        Rfci fci = new Rfci(test);
        fci.setVerbose(false);
        fci.setCompleteRuleSetUsed(true);
        fci.setDepth(depth);

        Graph estPag = fci.search();
        estPag = GraphUtils.replaceNodes(estPag, truePag.getNodes());

        System.out.println("Search done with a bootstrap");


        return estPag;
    }

    private interface EdgeProbabiity {
        double getProbability(Edge edge);
    }

    private static class EdgeFrequency implements EdgeProbabiity {
        private final List<Graph> PagProbs;

        public EdgeFrequency(List<Graph> PagProb) {
            this.PagProbs = PagProb;
        }

        public double getProbability(Edge e) {
            int count = 0;

            if (!PagProbs.get(0).containsNode(e.getNode1())) throw new IllegalArgumentException();
            if (!PagProbs.get(0).containsNode(e.getNode2())) throw new IllegalArgumentException();

            for (Graph g : PagProbs) {
                if (e.getEndpoint1() == Endpoint.NULL || e.getEndpoint2() == Endpoint.NULL) {
                    if (!g.isAdjacentTo(e.getNode1(), e.getNode2())) count++;
                } else {
                    if (g.containsEdge(e)) count++;
                }
            }

            return count / (double) PagProbs.size();
        }
    }

    public boolean checkProbFileExists(String modelName, int numVars, int numEdges, int numCases, int numBootstrapSamples, String alg, int i, double numLatentConfounders, double alpha, String data_path) {
//		String dirname = System.getProperty("user.dir") +  "/CalibrationConstraintBased/"+ alg + "/" + modelName +"-Vars" + numVars +  "-Edges" + numEdges +  "-Cases" + numCases +  "-BS" + numBootstrapSamples + "-H" + numLatentConfounders + "-a" + alpha ;
        String dirname = data_path + "/CalibrationConstraintBased/" + alg + "/" + modelName + "-Vars" + numVars + "-Edges" + numEdges + "-Cases" + numCases + "-BS" + numBootstrapSamples + "-H" + numLatentConfounders + "-a" + alpha;
        File dir = new File(dirname);
        dir.mkdirs();
        String probFileName = "probs_v" + numVars + "_e" + numEdges + "_c" + numCases + "_b" + numBootstrapSamples + "_" + i + ".txt";
        File probFile = new File(dir, probFileName);
        return probFile.exists();
    }

    public boolean setOut(String modelName, int numVars, int numEdges, int numCases, int numBootstrapSamples, String alg, int i, double numLatentConfounders, double alpha, String data_path) {
        try {
            String dirname = data_path + "/CalibrationConstraintBased/" + alg + "/" + modelName + "-Vars" + numVars + "-Edges" + numEdges + "-Cases" + numCases + "-BS" + numBootstrapSamples + "-H" + numLatentConfounders + "-a" + alpha;
            File dir = new File(dirname);
            dir.mkdirs();
            String probFileName = "probs_v" + numVars + "_e" + numEdges + "_c" + numCases + "_b" + numBootstrapSamples + "_" + i + ".txt";
            String graphFileName = "BN_v" + numVars + "_e" + numEdges + "_c" + numCases + "_b" + numBootstrapSamples + "_" + i + ".txt";
            String PagFileName = "PAG_v" + numVars + "_e" + numEdges + "_c" + numCases + "_b" + numBootstrapSamples + "_" + i + ".txt";

            File probFile = new File(dir, probFileName);
            File graphFile = new File(dir, graphFileName);
            File PagFile = new File(dir, PagFileName);


            if (probFile.exists())
                return false;
            outProb = new PrintWriter(probFile);
            outGraph = new PrintWriter(graphFile);
            outPag = new PrintWriter(PagFile);

            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void print(String s, PrintWriter out) {
        if (out == null) return;
        out.flush();
        out.print(s);
        out.flush();
    }
}