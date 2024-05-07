package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.VerticalIntDataBox;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.DelimiterUtils;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularDatasetFileReader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Erich on 4/29/2016.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class HsimRepeatAutoRun {

    private boolean verbose; //set this to true if you want HsimAutoRun to report information to System.out
    private DataSet data;
    private boolean write;
    private String filenameOut = "defaultOut";
    private char delimiter = ',';

    //*********Constructors*************//

    /**
     * <p>Constructor for HsimRepeatAutoRun.</p>
     *
     * @param indata a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public HsimRepeatAutoRun(DataSet indata) {
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
     * <p>Constructor for HsimRepeatAutoRun.</p>
     *
     * @param readfilename a {@link java.lang.String} object
     * @param delim        a char
     */
    public HsimRepeatAutoRun(String readfilename, char delim) {
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

    //***************PUBLIC METHODS********************//

    /**
     * <p>run.</p>
     *
     * @param resimSize a int
     * @param repeat    a int
     * @return an array of {@link double} objects
     */
    public double[] run(int resimSize, int repeat) {
        //parameter: set of positive integers, which are resimSize values.
        List<Integer> schedule = new ArrayList<>();

        for (int i = 0; i < repeat; i++) {
            schedule.add(resimSize);
        }

        //Arrays.asList(5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5);
        double[] evalTotal;
        evalTotal = new double[5];
        evalTotal[0] = 0;
        evalTotal[1] = 0;
        evalTotal[2] = 0;
        evalTotal[3] = 0;
        evalTotal[4] = 0;

        double[] evalIncrement;
        Integer count0 = 1;
        Integer count1 = 1;
        Integer count2 = 1;
        Integer count3 = 1;
        Integer count4 = 1;

        for (Integer i : schedule) {
            //count++;
            HsimAutoRun study = new HsimAutoRun(this.data);
            //this is done differently if write is true. in that case, HsimAutoRun will be used differently
            if (this.write) {
                study.setWrite(true);
                study.setFilenameOut(this.filenameOut);
                study.setDelimiter(this.delimiter);
            }
            //pass verbose on to the lower level as well
            if (this.verbose) {
                study.setVerbose(false);
            }

            //run the edu.cmu.tetrad.study! yay!
            evalIncrement = study.run(i);
            //need to use if clauses to track each count separately.
            if (!Double.isNaN(evalIncrement[0])) {
                evalTotal[0] = evalTotal[0] + evalIncrement[0];
                count0++;
            }
            if (!Double.isNaN(evalIncrement[1])) {
                evalTotal[1] = evalTotal[1] + evalIncrement[1];
                count1++;
            }
            if (!Double.isNaN(evalIncrement[2])) {
                evalTotal[2] = evalTotal[2] + evalIncrement[2];
                count2++;
            }
            if (!Double.isNaN(evalIncrement[3])) {
                evalTotal[3] = evalTotal[3] + evalIncrement[3];
                count3++;
            }
            if (!Double.isNaN(evalIncrement[4])) {
                evalTotal[4] = evalTotal[4] + evalIncrement[4];
                count4++;
            }
        }
        evalTotal[0] = evalTotal[0] / (double) (count0 - 1);
        evalTotal[1] = evalTotal[1] / (double) (count1 - 1);
        evalTotal[2] = evalTotal[2] / (double) (count2 - 1);
        evalTotal[3] = evalTotal[3] / (double) (count3 - 1);
        evalTotal[4] = evalTotal[4] / (double) (count4 - 1);

        if (this.verbose) {
            System.out.println("Average eval scores: " + evalTotal[0] + " " + evalTotal[1]
                               + " " + evalTotal[2] + " " + evalTotal[3] + " " + evalTotal[4]);
        }
        return evalTotal;
    }

    //*************************Methods for setting private variables***********//

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
