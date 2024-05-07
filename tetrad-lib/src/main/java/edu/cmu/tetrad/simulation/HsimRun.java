package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataWriter;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.DelimiterUtils;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularDatasetFileReader;

import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Erich on 3/28/2016.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class HsimRun {

    /**
     * Private constructor to prevent instantiation.
     */
    private HsimRun() {

    }

    /**
     * <p>run.</p>
     *
     * @param readfilename   a {@link java.lang.String} object
     * @param filenameOut    a {@link java.lang.String} object
     * @param delimiter      a char
     * @param resimNodeNames an array of {@link java.lang.String} objects
     * @param verbose        a boolean
     */
    public static void run(String readfilename, String filenameOut, char delimiter, String[] resimNodeNames, boolean verbose) {

        //===========read data from file=============
        String workingDirectory = System.getProperty("user.dir");
        System.out.println(workingDirectory);
        try {
            Path dataFile = Paths.get(readfilename);

            VerticalDiscreteTabularDatasetFileReader dataReader = new VerticalDiscreteTabularDatasetFileReader(dataFile, DelimiterUtils.toDelimiter(delimiter));
            DataSet dataSet = (DataSet) DataConvertUtils.toDataModel(dataReader.readInData());
            System.out.println("cols: " + dataSet.getNumColumns() + " rows: " + dataSet.getNumRows());

            //testing the read file
            //DataWriter.writeRectangularData(dataSet, new FileWriter("dataOut2.txt"), '\t');
            //apply Hsim to data, with whatever parameters
            //========first make the Dag for Hsim==========
            //ICovarianceMatrix cov = new CovarianceMatrix(dataSet);
            final double penaltyDiscount = 2.0;
            SemBicScore score = new SemBicScore(new CovarianceMatrix(dataSet));
            score.setPenaltyDiscount(penaltyDiscount);
            Fges fges = new Fges(score);
            fges.setVerbose(false);

            Graph estGraph = fges.search();
            System.out.println(estGraph);

            Graph estCPDAG = new EdgeListGraph(estGraph);
            Graph estGraphDAG = GraphTransforms.dagFromCpdag(estCPDAG, null);
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

            for (String resimNodeName : resimNodeNames) {
                Node thisNode = estDAG.getNode(resimNodeName);
                simnodes.add(thisNode);
            }

            //===========Apply the hybrid resimulation===============
            Hsim hsim = new Hsim(estDAG, simnodes, dataSet);
            DataSet newDataSet = hsim.hybridsimulate();

            //write output to a new file
            DataWriter.writeRectangularData(newDataSet, new FileWriter(filenameOut), delimiter);

            //=======Run FGES on the output data, and compare it to the original learned graph
            Path dataFileOut = Paths.get(filenameOut);
            VerticalDiscreteTabularDatasetFileReader dataReaderOut = new VerticalDiscreteTabularDatasetFileReader(dataFileOut, DelimiterUtils.toDelimiter(delimiter));

            DataSet dataSetOut = (DataSet) DataConvertUtils.toDataModel(dataReaderOut.readInData());

            SemBicScore _score = new SemBicScore(new CovarianceMatrix(dataSetOut));
            _score.setPenaltyDiscount(2.0);
            Fges fgesOut = new Fges(_score);
            fgesOut.setVerbose(false);

            Graph estGraphOut = fgesOut.search();
            System.out.println(estGraphOut);

            GraphSearchUtils.graphComparison(estGraph, estGraphOut, System.out);
        } catch (Exception IOException) {
            IOException.printStackTrace();
        }
    }
}
