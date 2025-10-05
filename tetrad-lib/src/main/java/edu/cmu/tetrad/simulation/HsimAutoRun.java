///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataWriter;
import edu.cmu.tetrad.data.VerticalIntDataBox;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.score.BDeuScore;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.DelimiterUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularDatasetFileReader;

import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Erich on 3/28/2016.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class HsimAutoRun {

    private boolean verbose;
    private DataSet data;
    private boolean write;
    private String filenameOut = "defaultOut";
    private char delimiter = ',';

    //*********Constructors*************//

    /**
     * <p>Constructor for HsimAutoRun.</p>
     *
     * @param indata a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public HsimAutoRun(DataSet indata) {
        //need to turn indata into a VerticalIntDataBox still !!!!!!!!!!!!!!!!!11
        //first check if indata is already the right type
        if (((BoxDataSet) indata).getDataBox() instanceof VerticalIntDataBox) {
            this.data = indata;
        } else {
            VerticalIntDataBox dataVertBox = HsimUtils.makeVertIntBox(indata);
            this.data = new BoxDataSet(dataVertBox, indata.getVariables());
        }
    }

    /**
     * <p>Constructor for HsimAutoRun.</p>
     *
     * @param readfilename a {@link java.lang.String} object
     * @param delim        a char
     */
    public HsimAutoRun(String readfilename, char delim) {
        String workingDirectory = System.getProperty("user.dir");
        System.out.println(workingDirectory);
        Set<String> eVars = new HashSet<>();
        eVars.add("MULT");
        Path dataFile = Paths.get(readfilename);

        VerticalDiscreteTabularDatasetFileReader dataReader = new VerticalDiscreteTabularDatasetFileReader(dataFile, DelimiterUtils.toDelimiter(delim));
        try {
            this.data = (DataSet) DataConvertUtils.toDataModel(dataReader.readInData(eVars));
        } catch (Exception IOException) {
            IOException.printStackTrace();
        }
        //if (verbose) System.out.println("Vertical cols: " + dataSet.getNumColumns() + " rows: " + dataSet.getNumRows());
    }

    //***********Public methods*************//

    /**
     * <p>run.</p>
     *
     * @param resimSize a int
     * @return an array of  objects
     */
    public double[] run(int resimSize) {
        //modify this so that verbose is a private data value, and so that data can be taken from either a dataset or a file.
        //===========read data from file=============
        Set<String> eVars = new HashSet<>();
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
            BDeuScore score = new BDeuScore(this.data);

            //ICovarianceMatrix cov = new CovarianceMatrix(dataSet);
            final double penaltyDiscount = 2.0;
            Fges fges = new Fges(score);
            fges.setVerbose(false);

            Graph estGraph = fges.search();
            //if (verbose) System.out.println(estGraph);

            Graph estCPDAG = new EdgeListGraph(estGraph);
            Graph estGraphDAG = GraphTransforms.dagFromCpdag(estCPDAG, null);
            Dag estDAG = new Dag(estGraphDAG);

            //===========Identify the nodes to be resimulated===========
            //select a random node as the centroid
            List<Node> allNodes = estGraph.getNodes();
            int size = allNodes.size();
            int randIndex = RandomUtil.getInstance().nextInt(size);
            Node centroid = allNodes.get(randIndex);
            if (this.verbose) {
                System.out.println("the centroid is " + centroid);
            }

            List<Node> queue = new ArrayList<>();
            queue.add(centroid);
            List<Node> queueAdd;
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
                        queueAdd.add(allNodes.get(RandomUtil.getInstance().nextInt(size)));
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

            Set<Node> simnodes = new HashSet<>(queue);
            if (this.verbose) {
                System.out.println("the resimmed nodes are " + simnodes);
            }

            //===========Apply the hybrid resimulation===============
            Hsim hsim = new Hsim(estDAG, simnodes, this.data); //regularDataSet
            DataSet newDataSet = hsim.hybridsimulate();

            //write output to a new file
            if (this.write) {
                FileWriter fileWriter = new FileWriter(this.filenameOut);
                DataWriter.writeRectangularData(newDataSet, fileWriter, this.delimiter);
                fileWriter.close();
            }
            //=======Run FGES on the output data, and compare it to the original learned graph

            BDeuScore newscore = new BDeuScore(newDataSet);
            Fges fgesOut = new Fges(newscore);
            fgesOut.setVerbose(false);

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

            output = HsimUtils.errorEval(estEvalGraphOut, estEvalGraph);
            if (this.verbose) {
                System.out.println(output[0] + " " + output[1] + " " + output[2] + " " + output[3] + " " + output[4]);
            }
        } catch (Exception IOException) {
            IOException.printStackTrace();
        }
        return output;
    }

    //******* Methods for setting values to private variables****************//

    /**
     * <p>Setter for the field <code>verbose</code>.</p>
     *
     * @param verbosity a boolean
     */
    public void setVerbose(boolean verbosity) {
        this.verbose = verbosity;
    }

    /**
     * <p>Setter for the field <code>write</code>.</p>
     *
     * @param setwrite a boolean
     */
    public void setWrite(boolean setwrite) {
        this.write = setwrite;
    }

    /**
     * <p>Setter for the field <code>filenameOut</code>.</p>
     *
     * @param filename a {@link java.lang.String} object
     */
    public void setFilenameOut(String filename) {
        this.filenameOut = filename;
    }

    /**
     * <p>Setter for the field <code>delimiter</code>.</p>
     *
     * @param delim a char
     */
    public void setDelimiter(char delim) {
        this.delimiter = delim;
    }
}

