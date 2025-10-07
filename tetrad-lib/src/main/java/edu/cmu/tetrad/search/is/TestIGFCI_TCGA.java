package edu.cmu.tetrad.search.is;

import edu.cmu.tetrad.data.BootstrapSampler;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphSaveLoadUtils;
import edu.cmu.tetrad.search.score.BDeuScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.DelimiterUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularDatasetFileReader;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularDatasetReader;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;


public class TestIGFCI_TCGA {
    public static void main(String[] args) {
        // read and process input arguments
        String data_path = System.getProperty("user.dir");
        boolean threshold = true;
        double alpha = 0, cutoff = 0.5, kappa = 0.5;
        int nbs = 1;

        System.out.println(Arrays.asList(args));
        String data_name = "gsva_dis_25", knowledge_name = "forbid_pairs_nodes2";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-th":
                    threshold = Boolean.parseBoolean(args[i + 1]);
                    break;
                case "-alpha":
                    alpha = Double.parseDouble(args[i + 1]);
                    break;
                case "-cutoff":
                    cutoff = Double.parseDouble(args[i + 1]);
                    break;
                case "-kappa":
                    kappa = Double.parseDouble(args[i + 1]);
                    break;
                case "-data":
                    data_name = args[i + 1];
                    break;
                case "-knowledge":
                    knowledge_name = args[i + 1];
                    break;
                case "-dir":
                    data_path = args[i + 1];
                    break;
                case "-bs":
                    nbs = Integer.parseInt(args[i + 1]);
                    break;
            }
        }

        TestIGFCI_TCGA t = new TestIGFCI_TCGA();
        t.test_sim(alpha, threshold, cutoff, kappa, data_name, knowledge_name, data_path, nbs);
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

    public void test_sim(double alpha, boolean threshold, double cutoff, double kappa, String data_name, String knowledge_name, String data_path, int nbs) { //int numVars, double edgesPerNode, double latent, int numCases, int numTests, int numActualTest, int numSim, String data_path, int time, long seed){

        RandomUtil.getInstance().setSeed(1454147771L + 100 * nbs);

        String pathToData = data_path + "/" + data_name + ".csv";
        String names = "Name";

        // Read in the data
        DataSet trainDataWnames = readData(pathToData);
        DataSet trainDataOrig = trainDataWnames.copy();
        trainDataOrig.removeColumn(trainDataWnames.getColumn(trainDataWnames.getVariable(names)));
        int numBSCases = (int) (0.9 * trainDataOrig.getNumRows());
        DataSet bs = new BootstrapSampler().sample(trainDataOrig, numBSCases);

        String pathToKnowledge = data_path + "/" + knowledge_name + ".csv";
        Knowledge knowledge = new Knowledge();
        DataSet knowledgeData = readData(pathToKnowledge);
        for (int i = 0; i < knowledgeData.getNumRows(); i++) {
            knowledge.setForbidden(knowledgeData.getObject(i, 0).toString(), knowledgeData.getObject(i, 1).toString());
            knowledge.setForbidden(knowledgeData.getObject(i, 1).toString(), knowledgeData.getObject(i, 0).toString());

        }


        // learn the population model using all training data and write the result in the output file
        System.out.println("begin population search");
        IndTestProbabilisticBDeu indTest_pop = new IndTestProbabilisticBDeu(bs, 0.5);
        indTest_pop.setThreshold(threshold);
        indTest_pop.setCutoff(cutoff);
        BDeuScore scoreP = new BDeuScore(bs);
        edu.cmu.tetrad.search.Gfci fci_pop = new edu.cmu.tetrad.search.Gfci(indTest_pop, scoreP);
        fci_pop.setKnowledge(knowledge);
        Graph graphP = null;
        try {
            graphP = fci_pop.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        File dir = new File(data_path + "/outputs_PAGs_BS/" + data_name);
        dir.mkdirs();
        String outputFileName = data_name + "_populationWide_" + nbs + ".txt";
        File filePop = new File(dir, outputFileName);
        GraphSaveLoadUtils.saveGraph(graphP, filePop, false);

        // run leave-one-out cross-validation over the training set to learn an instance-specific PAG for each sample
        for (int i = 0; i < trainDataOrig.getNumRows(); i++) {
            System.out.println("case i: " + i);
            DataSet trainData = trainDataOrig.copy();
            DataSet test = trainData.subsetRows(new int[]{i});
            trainData.removeRows(new int[]{i});

            // learn the instance-specific model using the IGFCi method, training data, and test
            // define the instance-specific BSC test
            IndTestProbabilisticISBDeu indTest_IS = new IndTestProbabilisticISBDeu(bs, test, scoreP.getStructurePrior());
            indTest_IS.setThreshold(threshold);
            indTest_IS.setCutoff(cutoff);

            // define the instance-specific score
            IsBDeuScore2 scoreI = new IsBDeuScore2(bs, test);
            scoreI.setKAddition(kappa);
            scoreI.setKDeletion(kappa);
            scoreI.setKReorientation(kappa);

            Score scoreI2 = new BDeuScore(trainData);

            //run IsGFci and write the result in the output file
            IsGFci Fci_IS = new IsGFci(indTest_IS, scoreI, scoreI2);
            Fci_IS.setKnowledge(knowledge);
            Graph graphI = null;
            try {
                graphI = Fci_IS.search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            String caseName = (String) trainDataWnames.getObject(i, trainDataWnames.getColumn(trainDataWnames.getVariable(names)));
            outputFileName = data_name + "_" + caseName + "_" + nbs + ".txt";
            File fileIs = new File(dir, outputFileName);
            GraphSaveLoadUtils.saveGraph(graphI, fileIs, false);
        }

    }

}