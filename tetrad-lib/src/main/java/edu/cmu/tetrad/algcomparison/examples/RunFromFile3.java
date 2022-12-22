package edu.cmu.tetrad.algcomparison.examples;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges;
import edu.cmu.tetrad.algcomparison.algorithm.pairwise.FaskPW;
import edu.cmu.tetrad.algcomparison.algorithm.pairwise.RSkew;
import edu.cmu.tetrad.algcomparison.score.ZhangShenBoundScore;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Lofs2;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularData;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDatasetFileReader;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class RunFromFile3 {
    public static void main(String... args) {

        try {
            Path dataPath = Paths.get("/Users/bandrews/Downloads/sub-9996_ses-54719_task-revlearnPA_run-01_bold_Atlas_MSMAll_2_d40_WRN_hp2000_clean.GLASSER19v.ptseries.csv");
            ContinuousTabularDatasetFileReader reader = new ContinuousTabularDatasetFileReader(dataPath, Delimiter.COMMA);

            System.out.println("Loading Dataset");
            ContinuousTabularData data = ((ContinuousTabularData) reader.readInData());
            List<Node> variables = new ArrayList<>();

            for (DataColumn column : data.getDataColumns()) {
                Node node = new ContinuousVariable(column.getName());
                variables.add(node);
            }

            BoxDataSet dataset = new BoxDataSet(new DoubleDataBox(data.getData()), variables);
            List<DataSet> datasets = new ArrayList<>();
            datasets.add(DataUtils.getContinuousDataSet(dataset));

            System.out.println("Running Searches");
            Parameters parameters = new Parameters();
            parameters.set("verbose", false);

            PrintWriter out;

            String path = "/Users/bandrews/Desktop";
            File folder = new File(path, "bridges_bic_fmri");
            File[] files = folder.listFiles();
            path += "/rskew_fmri/rskew";

            for(File file : files) {
                String current = file.getName();
                if (current.endsWith(".txt")) {
                    Graph adj = GraphUtils.loadGraphTxt(file);
                    Lofs2 lofs = new Lofs2(adj, datasets);
                    lofs.setRule(Lofs2.Rule.RSkew);
                    Graph ori = lofs.orient();

                    out = new PrintWriter(path + current.substring(7));
                    out.println(ori.toString());
                    out.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}