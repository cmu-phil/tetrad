package edu.cmu.tetrad.cli.search;

import edu.cmu.tetrad.cli.validation.DataValidation;
import edu.cmu.tetrad.cli.validation.TabularContinuousData;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.io.DataReader;
import edu.cmu.tetrad.io.TabularContinuousDataReader;
import edu.cmu.tetrad.search.Fgs;
import edu.cmu.tetrad.search.SemBicScore;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Author : Jeremy Espino MD
 * Created  2/12/16 9:44 AM
 */
public class FgsApiExample {

    public static void main(String[] args) throws Exception {

        // set path to Retention data
        Path dataFile = Paths.get("tetrad-cli/test/data", "Retention.txt");

        Character delimiter = '\t';

        // perform data validation
        // note: assuming data has unique variable names and does not contain zero covariance pairs
        DataValidation dataValidation = new TabularContinuousData(dataFile, delimiter);
        if (!dataValidation.validate(System.err, true)) {
            System.exit(-128);
        }

        DataReader dataReader = new TabularContinuousDataReader(dataFile, delimiter);
        DataSet dataSet = dataReader.readInData();

        SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
        score.setPenaltyDiscount(4.0);

        Fgs fgs = new Fgs(score);
        fgs.setOut(System.out);
        fgs.setDepth(-1);
        fgs.setIgnoreLinearDependent(false);
        fgs.setNumPatternsToStore(0);  // always set to zero
        fgs.setHeuristicSpeedup(true);
        fgs.setVerbose(true);

        Graph graph = fgs.search();
        System.out.println();
        System.out.println(graph.toString().trim());
        System.out.flush();

    }
}
