package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.VerticalIntDataBox;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.DelimiterUtils;
import edu.pitt.dbmi.data.reader.tabular.TabularDataReader;
import edu.pitt.dbmi.data.reader.tabular.VerticalDiscreteTabularDataReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Erich on 4/29/2016.
 */
public class HsimRepeatAutoRun {

    private boolean verbose = false; //set this to true if you want HsimAutoRun to report information to System.out
    private DataSet data;
    private boolean write = false;
    private String filenameOut = "defaultOut";
    private char delimiter = ',';

    //*********Constructors*************//
    public HsimRepeatAutoRun(DataSet indata) {
        //need to turn indata into a VerticalIntDataBox still !!!!!!!!!!!!!!!!!11
        //first check if indata is already the right type
        if (((BoxDataSet) indata).getDataBox() instanceof VerticalIntDataBox) {
            data = indata;
        } else {
            VerticalIntDataBox dataVertBox = HsimUtils.makeVertIntBox(indata);
            data = new BoxDataSet(dataVertBox, indata.getVariables());
        }
    }

    public HsimRepeatAutoRun(String readfilename, char delim) {
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
        Integer count0 = 1;
        Integer count1 = 1;
        Integer count2 = 1;
        Integer count3 = 1;
        Integer count4 = 1;

        for (Integer i : schedule) {
            //count++;
            HsimAutoRun study = new HsimAutoRun(data);
            //this is done differently if write is true. in that case, HsimAutoRun will be used differently
            if (write) {
                study.setWrite(true);
                study.setFilenameOut(filenameOut);
                study.setDelimiter(delimiter);
            }
            //pass verbose on to the lower level as well
            if (verbose) {
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
