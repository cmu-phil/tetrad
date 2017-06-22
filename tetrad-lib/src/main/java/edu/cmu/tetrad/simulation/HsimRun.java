package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.DelimiterUtils;
import edu.pitt.dbmi.data.reader.tabular.TabularDataReader;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularDataReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Erich on 3/28/2016.
 */
public class HsimRun {

    public static void run(String readfilename, String filenameOut, char delimiter, String[] resimNodeNames, boolean verbose) {

        //===========read data from file=============
        String workingDirectory = System.getProperty("user.dir");
        System.out.println(workingDirectory);
        try {
            Path dataFile = Paths.get(readfilename);

            TabularDataReader dataReader = new VerticalDiscreteTabularDataReader(dataFile.toFile(), DelimiterUtils.toDelimiter(delimiter));
            DataSet dataSet = (DataSet) DataConvertUtils.toDataModel(dataReader.readInData());
            System.out.println("cols: " + dataSet.getNumColumns() + " rows: " + dataSet.getNumRows());

            //testing the read file
            //DataWriter.writeRectangularData(dataSet, new FileWriter("dataOut2.txt"), '\t');
            //apply Hsim to data, with whatever parameters
            //========first make the Dag for Hsim==========
            //ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(dataSet);
            double penaltyDiscount = 2.0;
            SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
            score.setPenaltyDiscount(penaltyDiscount);
            Fges fges = new Fges(score);
            fges.setVerbose(false);
            fges.setNumPatternsToStore(0);
//            fges.setCorrErrorsAlpha(penaltyDiscount);
            //fges.setOut(out);
            //fges.setFaithfulnessAssumed(true);
            //fges.setMaxIndegree(1);
            //fges.setCycleBound(5);

            Graph estGraph = fges.search();
            System.out.println(estGraph);

            Graph estPattern = new EdgeListGraphSingleConnections(estGraph);
            PatternToDag patternToDag = new PatternToDag(estPattern);
            Graph estGraphDAG = patternToDag.patternToDagMeek();
            Dag estDAG = new Dag(estGraphDAG);

            //===========Identify the nodes to be resimulated===========
            //estDAG.getNodes()
            //need to populate simnodes with the nodes to be resimulated
            //for now, I choose a center Node and add its neighbors
            /* ===Commented out, but saved for future use=====
            Node centerNode = estDAG.getNode("X3");
            Set<Node> simnodes = new HashSet<Node>();
            simnodes.add(centerNode);
            simnodes.addAll(estDAG.getAdjacentNodes(centerNode));
             */
            //===test code, for user input specifying specific set of resim nodes====
            //user needs to specify a list or array or something of node names
            //use for loop through that collection, get each node from the names, add to the set
            Set<Node> simnodes = new HashSet<>();

            for (int i = 0; i < resimNodeNames.length; i++) {
                Node thisNode = estDAG.getNode(resimNodeNames[i]);
                simnodes.add(thisNode);
            }

            //===========Apply the hybrid resimulation===============
            Hsim hsim = new Hsim(estDAG, simnodes, dataSet);
            DataSet newDataSet = hsim.hybridsimulate();

            //write output to a new file
            DataWriter.writeRectangularData(newDataSet, new FileWriter(filenameOut), delimiter);

            //=======Run FGES on the output data, and compare it to the original learned graph
            Path dataFileOut = Paths.get(filenameOut);
            TabularDataReader dataReaderOut = new VerticalDiscreteTabularDataReader(dataFileOut.toFile(), DelimiterUtils.toDelimiter(delimiter));

            DataSet dataSetOut = (DataSet) DataConvertUtils.toDataModel(dataReaderOut.readInData());

            SemBicScore _score = new SemBicScore(new CovarianceMatrix(dataSetOut));
            _score.setPenaltyDiscount(2.0);
            Fges fgesOut = new Fges(_score);
            fgesOut.setVerbose(false);
            fgesOut.setNumPatternsToStore(0);
//            fgesOut.setCorrErrorsAlpha(2.0);
            //fgesOut.setOut(out);
            //fgesOut.setFaithfulnessAssumed(true);
            // fgesOut.setMaxIndegree(1);
            // fgesOut.setCycleBound(5);

            Graph estGraphOut = fgesOut.search();
            System.out.println(estGraphOut);

            SearchGraphUtils.graphComparison(estGraphOut, estGraph, System.out);
        } catch (Exception IOException) {
            IOException.printStackTrace();
        }
    }
}
