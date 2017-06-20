package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.BDeuScore;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.PatternToDag;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.DelimiterUtils;
import edu.pitt.dbmi.data.reader.tabular.TabularDataReader;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularDataReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by Erich on 3/28/2016.
 */
public class HsimAutoRun {

    private boolean verbose = false;
    private DataSet data;
    private boolean write = false;
    private String filenameOut = "defaultOut";
    private char delimiter = ',';

    //*********Constructors*************//
    public HsimAutoRun(DataSet indata) {
        //need to turn indata into a VerticalIntDataBox still !!!!!!!!!!!!!!!!!11
        //first check if indata is already the right type
        if (((BoxDataSet) indata).getDataBox() instanceof VerticalIntDataBox) {
            data = indata;
        } else {
            VerticalIntDataBox dataVertBox = HsimUtils.makeVertIntBox(indata);
            data = new BoxDataSet(dataVertBox, indata.getVariables());
        }
    }

    public HsimAutoRun(String readfilename, char delim) {
        String workingDirectory = System.getProperty("user.dir");
        System.out.println(workingDirectory);
        Set<String> eVars = new HashSet<String>();
        eVars.add("MULT");
        Path dataFile = Paths.get(readfilename);

        TabularDataReader dataReader = new VerticalDiscreteTabularDataReader(dataFile.toFile(), DelimiterUtils.toDelimiter(delim));
        try {
            data = (DataSet) DataConvertUtils.toDataModel(dataReader.readInData(eVars));
        } catch (Exception IOException) {
            IOException.printStackTrace();
        }
        //if (verbose) System.out.println("Vertical cols: " + dataSet.getNumColumns() + " rows: " + dataSet.getNumRows());
    }

    //***********Public methods*************//
    public double[] run(int resimSize) {
        //modify this so that verbose is a private data value, and so that data can be taken from either a dataset or a file.
        //===========read data from file=============
        Set<String> eVars = new HashSet<String>();
        eVars.add("MULT");

        double[] output;
        output = new double[5];
        try {

            //==== try with BigDataSetUtility ==============
            //DataSet regularDataSet = BigDataSetUtility.readInDiscreteData(new File(readfilename), delimiter, eVars);
            // ======done with BigDataSetUtility=============
            //if (verbose) System.out.println("Regular cols: " + regularDataSet.getNumColumns() + " rows: " + regularDataSet.getNumRows());
            //testing the read file
            //DataWriter.writeRectangularData(dataSet, new FileWriter("dataOut2.txt"), '\t');
            //apply Hsim to data, with whatever parameters
            //========first make the Dag for Hsim==========
            BDeuScore score = new BDeuScore(data);

            //ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(dataSet);
            double penaltyDiscount = 2.0;
            Fges fges = new Fges(score);
            fges.setVerbose(false);
            fges.setNumPatternsToStore(0);
            fges.setPenaltyDiscount(penaltyDiscount);

            Graph estGraph = fges.search();
            //if (verbose) System.out.println(estGraph);

            Graph estPattern = new EdgeListGraphSingleConnections(estGraph);
            PatternToDag patternToDag = new PatternToDag(estPattern);
            Graph estGraphDAG = patternToDag.patternToDagMeek();
            Dag estDAG = new Dag(estGraphDAG);

            //===========Identify the nodes to be resimulated===========
            //select a random node as the centroid
            List<Node> allNodes = estGraph.getNodes();
            int size = allNodes.size();
            int randIndex = new Random().nextInt(size);
            Node centroid = allNodes.get(randIndex);
            if (verbose) {
                System.out.println("the centroid is " + centroid);
            }

            List<Node> queue = new ArrayList<>();
            queue.add(centroid);
            List<Node> queueAdd = new ArrayList<Node>();
            //while queue has size less than the resim size, grow it
            //if (verbose) System.out.println(queue);
            while (queue.size() < resimSize) {
                //if (verbose) System.out.println(queue.size() + " vs " + resimSize);
                //find nodes adjacent to nodes in current queue, add them to a queue without duplicating nodes
                int qsize = queue.size();
                for (int i = 0; i < qsize; i++) {
                    //find set of adjacent nodes
                    queueAdd = estGraph.getAdjacentNodes(queue.get(i));
                    //remove nodes that are already in queue
                    queueAdd.removeAll(queue);

                    ////**** If queueAdd is empty at this stage, randomly select a node to add
                    while (queueAdd.size() < 1) {
                        queueAdd.add(allNodes.get(new Random().nextInt(size)));
                    }

                    //add remaining nodes to queue
                    queue.addAll(queueAdd);
                    //if (verbose) System.out.println(queue);

                    //break early when queue outgrows resimsize
                    if (queue.size() >= resimSize) {
                        break;
                    }
                }
            }

            //if queue is too big, remove nodes from the end until it is small enough.
            while (queue.size() > resimSize) {
                queue.remove(queue.size() - 1);
                //if (verbose) System.out.println(queue);
            }

            Set<Node> simnodes = new HashSet<Node>(queue);
            if (verbose) {
                System.out.println("the resimmed nodes are " + simnodes);
            }

            //===========Apply the hybrid resimulation===============
            Hsim hsim = new Hsim(estDAG, simnodes, data); //regularDataSet
            DataSet newDataSet = hsim.hybridsimulate();

            //write output to a new file
            if (write) {
                FileWriter fileWriter = new FileWriter(filenameOut);
                DataWriter.writeRectangularData(newDataSet, fileWriter, delimiter);
                fileWriter.close();
            }
            //=======Run FGES on the output data, and compare it to the original learned graph
            //Path dataFileOut = Paths.get(filenameOut);
            //edu.cmu.tetrad.io.DataReader dataReaderOut = new VerticalTabularDiscreteDataReader(dataFileOut, delimiter);

            //DataSet dataSetOut = dataReaderOut.readInData(eVars);
            BDeuScore newscore = new BDeuScore(newDataSet);
            Fges fgesOut = new Fges(newscore);
            fgesOut.setVerbose(false);
            fgesOut.setNumPatternsToStore(0);
            fgesOut.setPenaltyDiscount(2.0);
            //fgesOut.setOut(out);
            //fgesOut.setFaithfulnessAssumed(true);
            // fgesOut.setMaxIndegree(1);
            // fgesOut.setCycleBound(5);

            Graph estGraphOut = fgesOut.search();
            //if (verbose) System.out.println(" bugchecking: fges estGraphOut: " + estGraphOut);

            //doing the replaceNodes trick to fix some bugs
            estGraphOut = GraphUtils.replaceNodes(estGraphOut, estDAG.getNodes());
            //restrict the comparison to the simnodes and edges to their parents
            Set<Node> allParents = HsimUtils.getAllParents(estGraphOut, simnodes);
            Set<Node> addParents = HsimUtils.getAllParents(estDAG, simnodes);
            allParents.addAll(addParents);
            Graph estEvalGraphOut = HsimUtils.evalEdges(estGraphOut, simnodes, allParents);
            Graph estEvalGraph = HsimUtils.evalEdges(estDAG, simnodes, allParents);

            //SearchGraphUtils.graphComparison(estGraph, estGraphOut, System.out);
            estEvalGraphOut = GraphUtils.replaceNodes(estEvalGraphOut, estEvalGraph.getNodes());
            //if (verbose) System.out.println(estEvalGraph);
            //if (verbose) System.out.println(estEvalGraphOut);

            //SearchGraphUtils.graphComparison(estEvalGraphOut, estEvalGraph, System.out);
            output = HsimUtils.errorEval(estEvalGraphOut, estEvalGraph);
            if (verbose) {
                System.out.println(output[0] + " " + output[1] + " " + output[2] + " " + output[3] + " " + output[4]);
            }
        } catch (Exception IOException) {
            IOException.printStackTrace();
        }
        return output;
    }

    //******* Methods for setting values to private variables****************//
    public void setVerbose(boolean verbosity) {
        verbose = verbosity;
    }

    public void setWrite(boolean setwrite) {
        write = setwrite;
    }

    public void setFilenameOut(String filename) {
        filenameOut = filename;
    }

    public void setDelimiter(char delim) {
        delimiter = delim;
    }
}
