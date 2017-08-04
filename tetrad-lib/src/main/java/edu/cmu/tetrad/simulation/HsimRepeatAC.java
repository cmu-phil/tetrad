package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.DelimiterUtils;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDataFileReader;
import edu.pitt.dbmi.data.reader.tabular.TabularDataReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Erich on 4/29/2016.
 */
public class HsimRepeatAC {

    private boolean verbose = false; //set this to true if you want HsimAutoRun to report information to System.out
    private DataSet data;
    private boolean write = false;
    private String filenameOut = "defaultOut";
    private char delimiter = ',';

    //*********Constructors*************//
    public HsimRepeatAC(DataSet indata) {
        data = indata;
        //may need to make this part more complicated if CovarianceMatrix method is finicky
    }

    public HsimRepeatAC(String readfilename, char delim) {
        String workingDirectory = System.getProperty("user.dir");
        System.out.println(workingDirectory);
        Set<String> eVars = new HashSet<String>();
        eVars.add("MULT");
        Path dataFile = Paths.get(readfilename);

        TabularDataReader dataReader = new ContinuousTabularDataFileReader(dataFile.toFile(), DelimiterUtils.toDelimiter(delim));
        try {
            data = (DataSet) DataConvertUtils.toDataModel(dataReader.readInData(eVars));
        } catch (Exception IOException) {
            IOException.printStackTrace();
        }
    }

    //***************PUBLIC METHODS********************//
    public double[] run(int resimSize, int repeat) {
        //parameter: set of positive integers, which are resimSize values.
        List<Integer> schedule = new ArrayList<Integer>();

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
        //evalIncrement = new double[5];
        //Integer count = 0;
        Integer count0 = 0;
        Integer count1 = 0;
        Integer count2 = 0;
        Integer count3 = 0;
        Integer count4 = 0;

        for (Integer i : schedule) {
            //count++;
            HsimAutoC study = new HsimAutoC(data);
            //this is done differently if write is true. in that case, HsimAutoC will be used differently
            if (write) {
                study.setWrite(true);
                study.setFilenameOut(filenameOut);
                study.setDelimiter(delimiter);
            }
            //pass verbose on to the lower level as well
            if (verbose) {
                study.setVerbose(true);
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

        if (verbose) {
            System.out.println("Average eval scores: " + evalTotal[0] + " " + evalTotal[1]
                    + " " + evalTotal[2] + " " + evalTotal[3] + " " + evalTotal[4]);
        }
        return evalTotal;
    }

    //*************************Methods for setting private variables***********//
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
