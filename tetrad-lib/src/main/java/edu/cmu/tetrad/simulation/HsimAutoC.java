package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataWriter;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.DelimiterUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDatasetFileReader;

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
public class HsimAutoC {

    private boolean verbose;
    private DataSet data;
    private boolean write;
    private String filenameOut = "defaultOut";
    private char delimiter = ',';

    //*********Constructors*************//
    //contructor using a previously existing DataSet object
    /**
     * <p>Constructor for HsimAutoC.</p>
     *
     * @param indata a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public HsimAutoC(DataSet indata) {
        //first check if indata is already the right type
        this.data = indata;
        //may need to make this part more complicated if CovarianceMatrix method is finicky
    }

    //constructor that loads data from a file named readfilename, with delimiter delim
    /**
     * <p>Constructor for HsimAutoC.</p>
     *
     * @param readfilename a {@link java.lang.String} object
     * @param delim a char
     */
    public HsimAutoC(String readfilename, char delim) {
        String workingDirectory = System.getProperty("user.dir");
        System.out.println(workingDirectory);
        Set<String> eVars = new HashSet<>();
        eVars.add("MULT");
        Path dataFile = Paths.get(readfilename);

        ContinuousTabularDatasetFileReader dataReader = new ContinuousTabularDatasetFileReader(dataFile, DelimiterUtils.toDelimiter(delim));
        try {
            this.data = (DataSet) DataConvertUtils.toDataModel(dataReader.readInData());
        } catch (Exception IOException) {
            IOException.printStackTrace();
        }
    }

    //***********Public methods*************//
    /**
     * <p>run.</p>
     *
     * @param resimSize a int
     * @return an array of {@link double} objects
     */
    public double[] run(int resimSize) {

        double[] output;
        output = new double[5];
        //========first make the Dag for Hsim==========
        ICovarianceMatrix cov = new CovarianceMatrix(this.data);
        SemBicScore score = new SemBicScore(cov);
        score.setPenaltyDiscount(2.0);

        Fges fges = new Fges(score);
        fges.setVerbose(false);

        Graph estGraph = fges.search();
        //if (verbose) System.out.println(estGraph);

        Graph estGraphDAG = GraphTransforms.dagFromCpdag(estGraph, null);
        Dag estDAG = new Dag(estGraphDAG);
        //Dag estDAG = new Dag(estGraph);

        //===========Identify the nodes to be resimulated===========
        //for this class, I'm going to choose variables for resimulation randomly, rather than building cliques
        //select a random node
        List<Node> remainingNodes = estGraph.getNodes();
        int randIndex = RandomUtil.getInstance().nextInt(remainingNodes.size());
        Node randomnode = remainingNodes.get(randIndex);
        if (this.verbose) {
            System.out.println("the first node is " + randomnode);
        }
        List<Node> queue = new ArrayList<>();
        queue.add(randomnode);
        //while queue has size less than the resim size, grow it
        //if (verbose) System.out.println(queue);
        while (queue.size() < resimSize) {
            //choose another node randomly
            remainingNodes.remove(randIndex);
            randIndex = RandomUtil.getInstance().nextInt(remainingNodes.size());
            randomnode = remainingNodes.get(randIndex);
            //add that node to the resim set
            queue.add(randomnode);
        }

        Set<Node> simnodes = new HashSet<>(queue);
        if (this.verbose) {
            System.out.println("the resimmed nodes are " + simnodes);
        }

        //===========Apply the hybrid resimulation===============
        HsimContinuous hsimC = new HsimContinuous(estDAG, simnodes, this.data); //regularDataSet
        DataSet newDataSet = hsimC.hybridsimulate();

        //write output to a new file
        if (this.write) {
            try {
                FileWriter fileWriter = new FileWriter(this.filenameOut);
                DataWriter.writeRectangularData(newDataSet, fileWriter, this.delimiter);
                fileWriter.close();
            } catch (Exception IOException) {
                IOException.printStackTrace();
            }
        }

        //=======Run FGS on the output data, and compare it to the original learned graph
        ICovarianceMatrix newcov = new CovarianceMatrix(this.data);
        SemBicScore newscore = new SemBicScore(newcov);
        newscore.setPenaltyDiscount(2.0);
        Fges fgesOut = new Fges(newscore);
        fgesOut.setVerbose(false);

        Graph estGraphOut = fgesOut.search();
        //if (verbose) System.out.println(" bugchecking: fgs estGraphOut: " + estGraphOut);

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
