package edu.cmu.tetrad.algcomparison.examples;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.FAS;
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularData;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDatasetFileReader;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class RunKemmeren {
    public static void main(final String... args) {

//        Path path = Paths.get("/home/bandrews/Desktop/Kemmeren/data_kemmeren_centered.txt");
        final Path path = Paths.get("/home/bandrews/Desktop/Kemmeren/obs_data.txt");
//        Path path = Paths.get("/home/bandrews/Desktop/Kemmeren/subsampled_data.txt");
//        Path path = Paths.get("/home/bandrews/Desktop/test.txt");

        System.out.println("Loading Data");

        final ContinuousTabularDatasetFileReader reader = new ContinuousTabularDatasetFileReader(path, Delimiter.TAB);

        try {
            final ContinuousTabularData data = ((ContinuousTabularData) reader.readInData());
            final List<Node> variables = new ArrayList<>();
            for (final DataColumn column : data.getDataColumns()) {
                final Node node = new ContinuousVariable(column.getName());
                variables.add(node);
            }

            final BoxDataSet dataSet = new BoxDataSet(new DoubleDataBox(data.getData()), variables);

            System.out.println("Generating Background Knowledge");

            final Knowledge2 knowledge = new Knowledge2(dataSet.getVariableNames());

//            Set<String> meta = new HashSet<>();
//            Set<String> domain = new HashSet<>();
//            for(String node : dataSet.getVariableNames()) {
//                if(node.startsWith("I_")) {
//                    meta.add(node);
//                    knowledge.addToTier(0, node);
//                } else {
//                    domain.add(node);
//                    knowledge.addToTier(1, node);
//                }
//            }
//            knowledge.addKnowledgeGroup(new KnowledgeGroup(2, meta, domain));
//            knowledge.setTierForbiddenWithin(0, true);
//
//            for(String node : meta) {
//                if(domain.contains(node.substring(2))) {
//                    knowledge.setRequired(node, node.substring(2));
//                }
//            }

            System.out.println("Running Search");

            final Parameters parameters = new Parameters();
            parameters.set("penaltyDiscount", 2);
            parameters.set("structurePrior", 1);
            parameters.set("faithfulnessAssumed", true);
            parameters.set("symmetricFirstStep", false);
            parameters.set("maxDegree", -1);
            parameters.set("depth", 1);
            parameters.set("sepsetsReturnEmptyIfNotFixed", true);
            parameters.set("verbose", true);

            final SemBicScore score = new SemBicScore();
            final SemBicTest test = new SemBicTest();

//            Fges search = new Fges(score);
            final FAS search = new FAS(test);
//            Gfci search = new Gfci(test, score);
            search.setKnowledge(knowledge);
            final Graph graph = search.search(dataSet, parameters);

            System.out.println("Writing Output");

            final PrintWriter out = new PrintWriter("/home/bandrews/Desktop/out.txt");
            out.println(graph.toString());
            out.close();

        } catch (final IOException e) {
            e.printStackTrace();
        }


    }
}