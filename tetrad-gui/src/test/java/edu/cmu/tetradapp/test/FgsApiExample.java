package edu.cmu.tetradapp.test;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.AlgorithmFactory;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.algcomparison.independence.ConditionalGaussianLRT;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.score.ConditionalGaussianBicScore;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.BDeScore;
import edu.cmu.tetrad.search.BicScore;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.metadata.Metadata;
import edu.pitt.dbmi.data.reader.tabular.TabularColumnFileReader;
import edu.pitt.dbmi.data.reader.tabular.TabularColumnReader;
import edu.pitt.dbmi.data.reader.tabular.TabularDataFileReader;
import edu.pitt.dbmi.data.reader.tabular.TabularDataReader;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Author : Jeremy Espino MD
 * Created  10/15/19 6:47 PM
 */
public class FgsApiExample {

    public static void main(String[] args) throws Exception {

        // Read in the data
        // set path to data
        Path dataFile = Paths.get("./tetrad-lib/src/test/resources/","iq_brain_size.tetrad.txt");

        // data file settings
        Delimiter delimiter = Delimiter.WHITESPACE;
        int numberOfCategories = 2;
        boolean hasHeader = true;
        boolean isDiscrete = false;

        // tabular data is our input (can also use covariance)
        TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, delimiter);

        // create the column  object - describes the data in each column
        DataColumn[] dataColumns;

        // optionally skip columns you don't want (e.g., the row id)
        // dataColumns = columnReader.readInDataColumns(new int[]{0},isDiscrete);
        // or just read in all columns and set all columns to continuous
        dataColumns = columnReader.readInDataColumns(isDiscrete);

        // setup data reader
        TabularDataReader dataReader = new TabularDataFileReader(dataFile, delimiter);

        // if this is a mixed dataset determine which columns are discrete based on number of unique values in column i.e, updates dataColumns[]
        dataReader.determineDiscreteDataColumns(dataColumns, numberOfCategories, hasHeader);

        // actually read in the data
        Data data = dataReader.read(dataColumns, hasHeader, null)  ;
        DataModel dataModel = DataConvertUtils.toDataModel(data);



        // Select search algorithm
        // instantiate instance of the FGES using our algorithm factory
        // which allows you to easily swap out ind. tests and scores

        // for continuous data can use sem bic score
        //Algorithm fges = AlgorithmFactory.create(Fges.class, null, SemBicScore.class);

        // for discrete  data can use sem bic score
        //Algorithm fges = AlgorithmFactory.create(Fges.class, null, BdeuScore.class);

        // for mixed data can use Conditional Gaussian
        Algorithm fges = AlgorithmFactory.create(Fges.class, null , ConditionalGaussianBicScore.class);




        // Set algorithm parameters
        // we've standardized parameters using the manual i.e., the id names in the manual are the names to use, also in Params class
        Parameters parameters = new Parameters();

        // fges parameters
        parameters.set(Params.DEPTH, 100);
        parameters.set(Params.PENALTY_DISCOUNT, 1.0);
        parameters.set(Params.FAITHFULNESS_ASSUMED, true);
        parameters.set(Params.VERBOSE, true);
        parameters.set(Params.SYMMETRIC_FIRST_STEP, false);

        // parameters for conditional gaussian
        parameters.set(Params.DISCRETIZE, false);

        // perform the search
        Graph graph = fges.search(dataModel, parameters);

        // output the graph
        System.out.println();
        System.out.println(graph.toString().trim());
        System.out.flush();

    }

}
