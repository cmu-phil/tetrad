package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataWriter;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.SemBicScore;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.DelimiterUtils;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDatasetFileReader;

import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by Erich on 3/28/2016.
 */
public class HsimAutoC {

    private boolean verbose;
    private DataSet data;
    private boolean write;
    private String filenameOut = "defaultOut";
    private char delimiter = ',';

    //*********Constructors*************//
    //contructor using a previously existing DataSet object
    public HsimAutoC(final DataSet indata) {
        //first check if indata is already the right type
        this.data = indata;
        //may need to make this part more complicated if CovarianceMatrix method is finicky
    }

    //constructor that loads data from a file named readfilename, with delimiter delim
    public HsimAutoC(final String readfilename, final char delim) {
        final String workingDirectory = System.getProperty("user.dir");
        System.out.println(workingDirectory);
        final Set<String> eVars = new HashSet<String>();
        eVars.add("MULT");
        final Path dataFile = Paths.get(readfilename);

        final ContinuousTabularDatasetFileReader dataReader = new ContinuousTabularDatasetFileReader(dataFile, DelimiterUtils.toDelimiter(delim));
        try {
            this.data = (DataSet) DataConvertUtils.toDataModel(dataReader.readInData());
        } catch (final Exception IOException) {
            IOException.printStackTrace();
        }
    }

    //***********Public methods*************//
    public double[] run(final int resimSize) {

        double[] output;
        output = new double[5];
        //========first make the Dag for Hsim==========
        final ICovarianceMatrix cov = new CovarianceMatrix(this.data);
        final SemBicScore score = new SemBicScore(cov);

        final double penaltyDiscount = 2.0;
        final Fges fges = new Fges(score);
        fges.setVerbose(false);
        fges.setPenaltyDiscount(penaltyDiscount);

        final Graph estGraph = fges.search();
        //if (verbose) System.out.println(estGraph);

        final Graph estGraphDAG = SearchGraphUtils.dagFromCPDAG(estGraph);
        final Dag estDAG = new Dag(estGraphDAG);
        //Dag estDAG = new Dag(estGraph);

        //===========Identify the nodes to be resimulated===========
        //for this class, I'm going to choose variables for resimulation randomly, rather than building cliques
        //select a random node
        final List<Node> remainingNodes = estGraph.getNodes();
        int randIndex = new Random().nextInt(remainingNodes.size());
        Node randomnode = remainingNodes.get(randIndex);
        if (this.verbose) {
            System.out.println("the first node is " + randomnode);
        }
        final List<Node> queue = new ArrayList<>();
        queue.add(randomnode);
        //while queue has size less than the resim size, grow it
        //if (verbose) System.out.println(queue);
        while (queue.size() < resimSize) {
            //choose another node randomly
            remainingNodes.remove(randIndex);
            randIndex = new Random().nextInt(remainingNodes.size());
            randomnode = remainingNodes.get(randIndex);
            //add that node to the resim set
            queue.add(randomnode);
        }

        final Set<Node> simnodes = new HashSet<Node>(queue);
        if (this.verbose) {
            System.out.println("the resimmed nodes are " + simnodes);
        }

        //===========Apply the hybrid resimulation===============
        final HsimContinuous hsimC = new HsimContinuous(estDAG, simnodes, this.data); //regularDataSet
        final DataSet newDataSet = hsimC.hybridsimulate();

        //write output to a new file
        if (this.write) {
            try {
                final FileWriter fileWriter = new FileWriter(this.filenameOut);
                DataWriter.writeRectangularData(newDataSet, fileWriter, this.delimiter);
                fileWriter.close();
            } catch (final Exception IOException) {
                IOException.printStackTrace();
            }
        }

        //=======Run FGS on the output data, and compare it to the original learned graph
        //Path dataFileOut = Paths.get(filenameOut);
        //edu.cmu.tetrad.io.DataReader dataReaderOut = new VerticalTabularDiscreteDataReader(dataFileOut, delimiter);
        final ICovarianceMatrix newcov = new CovarianceMatrix(this.data);
        final SemBicScore newscore = new SemBicScore(newcov);
        final Fges fgesOut = new Fges(newscore);
        fgesOut.setVerbose(false);
        fgesOut.setPenaltyDiscount(2.0);

        Graph estGraphOut = fgesOut.search();
        //if (verbose) System.out.println(" bugchecking: fgs estGraphOut: " + estGraphOut);

        //doing the replaceNodes trick to fix some bugs
        estGraphOut = GraphUtils.replaceNodes(estGraphOut, estDAG.getNodes());
        //restrict the comparison to the simnodes and edges to their parents
        final Set<Node> allParents = HsimUtils.getAllParents(estGraphOut, simnodes);
        final Set<Node> addParents = HsimUtils.getAllParents(estDAG, simnodes);
        allParents.addAll(addParents);
        Graph estEvalGraphOut = HsimUtils.evalEdges(estGraphOut, simnodes, allParents);
        final Graph estEvalGraph = HsimUtils.evalEdges(estDAG, simnodes, allParents);

        //SearchGraphUtils.graphComparison(estGraph, estGraphOut, System.out);
        estEvalGraphOut = GraphUtils.replaceNodes(estEvalGraphOut, estEvalGraph.getNodes());
        //if (verbose) System.out.println(estEvalGraph);
        //if (verbose) System.out.println(estEvalGraphOut);

        //SearchGraphUtils.graphComparison(estEvalGraphOut, estEvalGraph, System.out);
        output = HsimUtils.errorEval(estEvalGraphOut, estEvalGraph);
        if (this.verbose) {
            System.out.println(output[0] + " " + output[1] + " " + output[2] + " " + output[3] + " " + output[4]);
        }
        return output;
    }

    //******* Methods for setting values to private variables****************//
    public void setVerbose(final boolean verbosity) {
        this.verbose = verbosity;
    }

    public void setWrite(final boolean setwrite) {
        this.write = setwrite;
    }

    public void setFilenameOut(final String filename) {
        this.filenameOut = filename;
    }

    public void setDelimiter(final char delim) {
        this.delimiter = delim;
    }
}
