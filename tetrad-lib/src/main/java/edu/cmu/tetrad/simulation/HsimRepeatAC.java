package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.DelimiterUtils;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDatasetFileReader;

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
public class HsimRepeatAC {

    private boolean verbose; //set this to true if you want HsimAutoRun to report information to System.out
    private DataSet data;
    private boolean write;
    private String filenameOut = "defaultOut";
    private char delimiter = ',';

    //*********Constructors*************//

    /**
     * <p>Constructor for HsimRepeatAC.</p>
     *
     * @param indata a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public HsimRepeatAC(DataSet indata) {
        this.data = indata;
        //may need to make this part more complicated if CovarianceMatrix method is finicky
    }

    /**
     * <p>Constructor for HsimRepeatAC.</p>
     *
     * @param readfilename a {@link java.lang.String} object
     * @param delim        a char
     */
    public HsimRepeatAC(String readfilename, char delim) {
        String workingDirectory = System.getProperty("user.dir");
        System.out.println(workingDirectory);
        Set<String> eVars = new HashSet<>();
        eVars.add("MULT");
        Path dataFile = Paths.get(readfilename);

        ContinuousTabularDatasetFileReader dataReader = new ContinuousTabularDatasetFileReader(dataFile, DelimiterUtils.toDelimiter(delim));
        try {
            this.data = (DataSet) DataConvertUtils.toDataModel(dataReader.readInData(eVars));
        } catch (Exception IOException) {
            IOException.printStackTrace();
        }
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
        Integer count0 = 0;
        Integer count1 = 0;
        Integer count2 = 0;
        Integer count3 = 0;
        Integer count4 = 0;

        for (Integer i : schedule) {
            //count++;
            HsimAutoC study = new HsimAutoC(this.data);
            //this is done differently if write is true. in that case, HsimAutoC will be used differently
            if (this.write) {
                study.setWrite(true);
                study.setFilenameOut(this.filenameOut);
                study.setDelimiter(this.delimiter);
            }
            //pass verbose on to the lower level as well
            if (this.verbose) {
                study.setVerbose(verbose);
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
        evalTotal[0] = evalTotal[0] / (double) (count0);
        evalTotal[1] = evalTotal[1] / (double) (count1);
        evalTotal[2] = evalTotal[2] / (double) (count2);
        evalTotal[3] = evalTotal[3] / (double) (count3);
        evalTotal[4] = evalTotal[4] / (double) (count4);

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
