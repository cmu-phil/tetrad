package edu.cmu.tetrad.algcomparison.examples;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.BOSS;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges;
import edu.cmu.tetrad.algcomparison.algorithm.pairwise.FaskPW;
import edu.cmu.tetrad.algcomparison.algorithm.pairwise.RSkew;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.EbicScore;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DoubleDataBox;
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

public class Gango {
    public static void main(String[] args) {
        try {
            String adj = args[0].toLowerCase();
            String bic = args[1].toLowerCase();
            double param = Double.parseDouble(args[2]);
            String ori = args[3].toLowerCase();
            Path path = Paths.get(args[4]);
            String sep = args[5].toLowerCase();
            PrintWriter printer = new PrintWriter(args[6]);

            Parameters parameters = new Parameters();
            parameters.set("verbose", true);
            parameters.set("bossAlg", 1);

            ScoreWrapper score = new SemBicScore();
            parameters.set("penaltyDiscount", param);
            if (bic.equals("ebic")) {
                score = new EbicScore();
                parameters.set("ebicGamma", param);
            }

            Algorithm search = new Fges(score);
            if (adj.equals("boss")) {
                FisherZ test = new FisherZ();
                search = new BOSS(test, score);
            }

            Algorithm orient = new RSkew(search);
            if (ori.equals("fask") || ori.equals("faskpw")) {
                orient = new FaskPW(search);
            }

            Delimiter delimiter = Delimiter.WHITESPACE;
            if (sep.equals("comma") || sep.equals(",")) {
                delimiter = Delimiter.COMMA;
            } else if (sep.equals("tab") || sep.equals("\\t")) {
                delimiter = Delimiter.TAB;
            } else if (sep.equals("semicolon") || sep.equals(";")) {
            delimiter = Delimiter.SEMICOLON;
            }

            System.out.println("Loading Dataset");
            ContinuousTabularDatasetFileReader reader = new ContinuousTabularDatasetFileReader(path, delimiter);
            ContinuousTabularData data = ((ContinuousTabularData) reader.readInData());
            List<Node> variables = new ArrayList<>();
            for (DataColumn column : data.getDataColumns()) {
                Node node = new ContinuousVariable(column.getName());
                variables.add(node);
            }
            BoxDataSet dataset = new BoxDataSet(new DoubleDataBox(data.getData()), variables);

            System.out.println("Running Search");
            Graph g = orient.search(dataset, parameters);

            printer.println(g.toString());
            printer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}