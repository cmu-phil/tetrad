package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.io.VerticalTabularDiscreteDataReader;
import edu.cmu.tetrad.search.*;

import java.io.File;
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

            //==== tried with BigDataSetUtility ==============
            Set<String> eVars = new HashSet<String>();
            //eVars.add("MULT");
            DataSet regularDataSet = BigDataSetUtility.readInDiscreteData(new File(readfilename), delimiter, eVars);
            // ======done with BigDataSetUtility=============

            Path dataFile = Paths.get(readfilename);

            edu.cmu.tetrad.io.DataReader dataReader = new VerticalTabularDiscreteDataReader(dataFile, delimiter);
            DataSet dataSet = dataReader.readInData();
            System.out.println("cols: " + dataSet.getNumColumns() + " rows: " + dataSet.getNumRows());

            //testing the read file
            //DataWriter.writeRectangularData(dataSet, new FileWriter("dataOut2.txt"), '\t');

            //apply Hsim to data, with whatever parameters

            //========first make the Dag for Hsim==========

            //ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(dataSet);
            double penaltyDiscount = 2.0;
            SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
            score.setPenaltyDiscount(penaltyDiscount);
            Fgs fgs = new Fgs(score);
            fgs.setVerbose(false);
            fgs.setNumPatternsToStore(0);
//            fgs.setAlpha(penaltyDiscount);
            //fgs.setOut(out);
            //fgs.setFaithfulnessAssumed(true);
            //fgs.setMaxIndegree(1);
            //fgs.setCycleBound(5);

            Graph estGraph = fgs.search();
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
            Set<Node> simnodes = new HashSet<Node>();

            for( int i = 0; i < resimNodeNames.length; i++) {
                Node thisNode = estDAG.getNode(resimNodeNames[i]);
                simnodes.add(thisNode);
            }


            //===========Apply the hybrid resimulation===============
            Hsim hsim = new Hsim(estDAG,simnodes,regularDataSet);
            DataSet newDataSet = hsim.hybridsimulate();

            //write output to a new file
            DataWriter.writeRectangularData(newDataSet, new FileWriter(filenameOut), delimiter);

        //=======Run FGS on the output data, and compare it to the original learned graph
        Path dataFileOut = Paths.get(filenameOut);
        edu.cmu.tetrad.io.DataReader dataReaderOut = new VerticalTabularDiscreteDataReader(dataFileOut, delimiter);

            DataSet dataSetOut = dataReaderOut.readInData();

            SemBicScore _score = new SemBicScore(new CovarianceMatrix(dataSetOut));
            _score.setPenaltyDiscount(2.0);
            Fgs fgsOut = new Fgs(_score);
            fgsOut.setVerbose(false);
            fgsOut.setNumPatternsToStore(0);
//            fgsOut.setAlpha(2.0);
            //fgsOut.setOut(out);
            //fgsOut.setFaithfulnessAssumed(true);
            // fgsOut.setMaxIndegree(1);
            // fgsOut.setCycleBound(5);

            Graph estGraphOut = fgsOut.search();
            System.out.println(estGraphOut);

        SearchGraphUtils.graphComparison(estGraphOut, estGraph, System.out);
        }
        catch(Exception IOException){
            IOException.printStackTrace();
        }
    }
}

