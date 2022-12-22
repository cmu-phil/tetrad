package edu.cmu.tetrad.algcomparison.examples;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.BOSS;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.PC;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.rGES;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.PseudoTest;
import edu.cmu.tetrad.algcomparison.score.EbicScore;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.covariance.CovarianceData;
import edu.pitt.dbmi.data.reader.covariance.LowerCovarianceDataFileReader;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class RunFromFile2 {
    public static void main(String... args) {

        try {
            String path = "/Users/bandrews/Desktop/simulations_for_erich";
            File folder = new File(path, "corr");
            File[] files = folder.listFiles();
            path += "/search";

            for(File file : files) {
                String current = file.getName();
                System.out.println(current);

                LowerCovarianceDataFileReader reader = new LowerCovarianceDataFileReader(file.toPath(), Delimiter.COMMA);
                CovarianceData data = reader.readInData();
                DataModel dataset = DataConvertUtils.toDataModel(data);

                Parameters parameters = new Parameters();
                parameters.set("ebicGamma", 0.8);
                parameters.set("bossAlg", 1);
                parameters.set("verbose", false);

                FisherZ fishz = new FisherZ();
                EbicScore ebic = new EbicScore();
                SemBicScore bic = new SemBicScore();

                Graph g;
                PrintWriter out;

                PC pc;
                Fges fges;
                rGES rges;
                BOSS boss;

//                for(double alpha : new double[] {1e-1, 1e-2, 1e-3}) {
//
//                    parameters.set("alpha", alpha);
//
//                    pc = new PC(fishz);
//                    g = pc.search(dataset, parameters);
//                    out = new PrintWriter(path + "/pc_" + alpha + "/" + current);
//                    out.println(g.toString());
//                    out.close();
//                }

                for(int pd : new int[] {1, 2}) {

                    parameters.set("penaltyDiscount", pd);

//                    fges = new Fges(bic);
//                    g = fges.search(dataset, parameters);
//                    out = new PrintWriter(path + "/fges_" + pd + "/" + current);
//                    out.println(g.toString());
//                    out.close();

                    rges = new rGES(bic);
                    g = rges.search(dataset, parameters);
                    out = new PrintWriter(path + "/bridges_" + pd + "/" + current);
                    out.println(g.toString());
                    out.close();

                    boss = new BOSS(fishz, bic);
                    g = boss.search(dataset, parameters);
                    out = new PrintWriter(path + "/boss_" + pd + "/" + current);
                    out.println(g.toString());
                    out.close();
                }

//                fges = new Fges(ebic);
//                g = fges.search(dataset, parameters);
//                out = new PrintWriter(path + "/fges_0.8/" + current);
//                out.println(g.toString());
//                out.close();

                rges = new rGES(ebic);
                g = rges.search(dataset, parameters);
                out = new PrintWriter(path + "/bridges_0.8/" + current);
                out.println(g.toString());
                out.close();

                boss = new BOSS(fishz, ebic);
                g = boss.search(dataset, parameters);
                out = new PrintWriter(path + "/boss_0.8/" + current);
                out.println(g.toString());
                out.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}