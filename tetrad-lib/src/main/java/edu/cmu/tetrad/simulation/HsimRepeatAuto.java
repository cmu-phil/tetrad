package edu.cmu.tetrad.simulation;

/**
 * Created by Erich on 3/28/2016.
 */
public class HsimRepeatAuto {

    public static void main(String[] args) {
        //***!!!!===!!!=== Parameters for the User to fill in! !!!===!!!===***
        String readfilename = "YeastNoDupe2Slim.csv";
        String filenameOut = "dataOutYeast.txt";
        char delimiter = ',';//'\t';

        int resimSize = 2;
        int repeat = 100;

        HsimRepeatAutoRun study = new HsimRepeatAutoRun(readfilename,delimiter);
        study.setVerbose(false);
        study.setWrite(true);
        study.setFilenameOut(filenameOut);
        study.setDelimiter(delimiter);
        study.run(resimSize,repeat);
    }
}

