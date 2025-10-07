package edu.cmu.tetrad.search.is;

import edu.cmu.tetrad.data.BootstrapSampler;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphSaveLoadUtils;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.score.BDeuScore;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.DelimiterUtils;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularDatasetFileReader;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularDatasetReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestISFGS_TCGA {
    public static void main(String[] args) {

        String pathToFolder = System.getProperty("user.dir");
        String dataName = "gsva_dis_25";
        String pathToData = pathToFolder + "/" + dataName + ".csv";
        String names = "Name";
        int numBootstraps = 1;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-data":
                    dataName = args[i + 1];
                    break;
                case "-dir":
                    pathToFolder = args[i + 1];
                    break;
                case "-bs":
                    numBootstraps = Integer.parseInt(args[i + 1]);
                    break;
            }
        }


        // Read in the data
        DataSet trainDataOrig = readData(pathToData);
        System.out.println(trainDataOrig.getNumRows() + ", " + trainDataOrig.getNumColumns());
        DataSet trainDataWOnames = trainDataOrig.copy();
        trainDataWOnames.removeColumn(trainDataOrig.getColumn(trainDataOrig.getVariable(names)));

        // Create the knowledge
//		IKnowledge knowledge = createKnowledge(trainDataOrig, target);

        // learn the population model using all training data
        double samplePrior = 1.0;
        double structurePrior = 1.0;
        Graph graphP = BNlearn_pop(trainDataWOnames, samplePrior, structurePrior);
        System.out.println("Pop graph:" + graphP.getEdges());


        for (int p = 5; p <= 5; p++) {

            double k_add = p / 10.0; //Math.pow(10, -1.0*p);

            System.out.println("kappa = " + k_add);
            File dir = new File(pathToFolder + "/outputs/" + dataName + "/kappa_" + k_add + "/");
            dir.mkdirs();
            String outputFileName = dataName + "_population_wide.txt";
            File filePop = new File(dir, outputFileName);
            GraphSaveLoadUtils.saveGraph(graphP, filePop, false);

            outputFileName = dataName + "_kappa_" + k_add + ".csv";
            File fileML = new File(dir, outputFileName);
            PrintStream out = null;
            try {
                out = new PrintStream(new FileOutputStream(fileML));
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            out.println("case, m-likelihood pop, m-likelihood IS");

            //LOOCV loop
            for (int i = 0; i < trainDataWOnames.getNumRows(); i++) {
                DataSet trainData = trainDataWOnames.copy();

                DataSet test = trainDataWOnames.subsetRows(new int[]{i});
                trainData.removeRows(new int[]{i});

                // Generate the bootstrap samples
                for (int bootstrap = 0; bootstrap < numBootstraps; bootstrap++) {
                    DataSet bs = new BootstrapSampler().sample(trainData, trainData.getNumRows());

                    BDeuScore _score = new BDeuScore(bs);

                    IsBDeuScore2 scoreI = new IsBDeuScore2(bs, test);
                    scoreI.setSamplePrior(samplePrior);
                    scoreI.setKAddition(0.5);
                    scoreI.setKDeletion(0.5);
                    scoreI.setKReorientation(0.5);
                    IsFges fgesI = new IsFges(scoreI, _score);
                    fgesI.setPopulationGraph(graphP);
                    double scoreP = fgesI.scoreDag(graphP);

                    String caseName = (String) trainDataOrig.getObject(i, trainDataOrig.getColumn(trainDataOrig.getVariable(names)));
                    outputFileName = dataName + "_" + caseName + "_kappa_" + k_add + "_" + bootstrap + ".txt";
                    File fileIs = new File(dir, outputFileName);

                    // learn the IS graph
                    Graph graphI = learnBNIS(bs, test, k_add, graphP, samplePrior, out, caseName, scoreP);

                    GraphSaveLoadUtils.saveGraph(graphI, fileIs, false);
                }
            }
            out.close();
        }

    }

    private static Graph learnBNIS(DataSet trainData, DataSet test, double kappa, Graph graphP, double samplePrior, PrintStream out, String caseName, double scoreP) {
        // learn the instance-specific model
        BDeuScore _score = new BDeuScore(trainData);

        IsBDeuScore2 scoreI = new IsBDeuScore2(trainData, test);
        scoreI.setSamplePrior(samplePrior);
        scoreI.setKAddition(kappa);
        scoreI.setKDeletion(kappa);
        scoreI.setKReorientation(kappa);
        IsFges fgesI = new IsFges(scoreI, _score);
        fgesI.setPopulationGraph(graphP);
        fgesI.setInitialGraph(graphP);
        Graph graphI = null;
        try {
            graphI = fgesI.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        graphI = GraphUtils.replaceNodes(graphI, trainData.getVariables());
        double scoreGI = fgesI.scoreDag(graphI);
        out.println(caseName + "," + scoreP + "," + scoreGI);

        return graphI;
    }

    private static Graph BNlearn_pop(DataSet trainDataOrig, double samplePrior, double structurePrior) {
        BDeuScore scoreP = new BDeuScore(trainDataOrig);
        scoreP.setPriorEquivalentSampleSize(samplePrior);
        scoreP.setStructurePrior(structurePrior);
        Fges fgesP = new Fges(scoreP);
        fgesP.setSymmetricFirstStep(true);
        Graph graphP = null;
        try {
            graphP = fgesP.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        graphP = GraphUtils.replaceNodes(graphP, trainDataOrig.getVariables());
        return graphP;
    }

    private static DataSet readData(String pathToData) {
        Path trainDataFile = Paths.get(pathToData);
        char delimiter = ',';
        VerticalDiscreteTabularDatasetReader trainDataReader = new VerticalDiscreteTabularDatasetFileReader(trainDataFile, DelimiterUtils.toDelimiter(delimiter));
        DataSet trainDataOrig = null;
        try {
            trainDataOrig = (DataSet) DataConvertUtils.toDataModel(trainDataReader.readInData());
        } catch (Exception IOException) {
            IOException.printStackTrace();
        }
        return trainDataOrig;
    }

}