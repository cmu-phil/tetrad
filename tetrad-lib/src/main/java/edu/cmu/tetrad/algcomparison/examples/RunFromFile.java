package edu.cmu.tetrad.algcomparison.examples;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.BOSS;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.FAS;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.rGES;
import edu.cmu.tetrad.algcomparison.algorithm.pairwise.FaskPW;
import edu.cmu.tetrad.algcomparison.algorithm.pairwise.RSkew;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.algcomparison.score.*;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Bridges;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.DiscreteDataColumn;
import edu.pitt.dbmi.data.reader.tabular.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RunFromFile {
    public static void main(String... args) {

        try {
            Path path = Paths.get("/Users/bandrews/Desktop/gango/combined.csv");
//            ContinuousTabularDatasetFileReader reader = new ContinuousTabularDatasetFileReader(path, Delimiter.COMMA);
            MixedTabularDatasetFileReader reader = new MixedTabularDatasetFileReader(path, Delimiter.COMMA, 2);

            System.out.println("Loading Dataset");
//            ContinuousTabularData data = (ContinuousTabularData) reader.readInData();
            MixedTabularData data = (MixedTabularData) reader.readInData();
            List<Node> variables = new ArrayList<>();
            Knowledge knowledge = new Knowledge();

            for(DiscreteDataColumn col : data.getDataColumns()) {
                DataColumn dataCol = col.getDataColumn();
                if (dataCol.isDiscrete()) {
                    variables.add(new DiscreteVariable(dataCol.getName()));
                } else {
                    variables.add(new ContinuousVariable(dataCol.getName()));
                }
            }

            MixedDataBox dataBox = new MixedDataBox(variables, data.getNumOfRows(), data.getContinuousData(), data.getDiscreteData());
            BoxDataSet dataSet = new BoxDataSet(dataBox, variables);

//            dataSet.isMixed();

            Set<String> meta = new HashSet<>();
            Set<String> domain = new HashSet<>();
            for(String node : dataSet.getVariableNames()) {
                if(node.startsWith("TIME")) {
                    meta.add(node);
                    knowledge.addToTier(0, node);
                } else {
                    domain.add(node);
                    knowledge.addToTier(1, node);
                }
            }
            knowledge.addKnowledgeGroup(new KnowledgeGroup(KnowledgeGroup.FORBIDDEN, domain, meta));
            knowledge.setTierForbiddenWithin(0, true);

            System.out.println("Running Searches");
            Parameters parameters = new Parameters();
            parameters.set("verbose", true);
            parameters.set("penaltyDiscount", 1);
            parameters.set("structurePrior", 0);
            parameters.set("discretize", false);
            parameters.set("ebicGamma", 0.8);
            parameters.set("semBicStructurePrior", 0);
            parameters.set("zSRiskBound", 1e-6);
            parameters.set("bossAlg", 1);

//            SemBicScore score = new SemBicScore();
            ConditionalGaussianBicScore score = new ConditionalGaussianBicScore();
//            EbicScore score = new EbicScore();
//            PoissonPriorScore score = new PoissonPriorScore();
//            ZhangShenBoundScore score = new ZhangShenBoundScore();
//            FisherZ test = new FisherZ();

            System.out.println("fGES");
            Fges fges = new Fges(score);
            fges.setKnowledge(knowledge);
            Graph fges_out = fges.search((DataModel) dataSet, parameters);

//            System.out.println("rGES");
//            rGES rges = new rGES(score);
//            Graph rges_out = rges.search(dataset, parameters);

            System.out.println("Writing Output");
            PrintWriter out;

            out = new PrintWriter("/Users/bandrews/Downloads/testing_out.txt");
            out.println(fges_out.toString());
            out.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}