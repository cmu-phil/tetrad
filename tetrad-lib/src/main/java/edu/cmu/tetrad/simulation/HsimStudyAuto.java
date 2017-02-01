package edu.cmu.tetrad.simulation;

/**
 * Created by Erich on 3/28/2016.
 */
public class HsimStudyAuto {

    public static void main(String[] args) {
        //***!!!!===!!!=== Parameters for the User to fill in! !!!===!!!===***
        String readfilename = "YeastNoDupe2Slim.csv";
        String filenameOut = "dataOutGeM.txt";
        char delimiter = ','; //'\t';
        int resimSize = 2;//number of variables to be resimmed
        HsimAutoRun study = new HsimAutoRun(readfilename,delimiter);
        study.setVerbose(false);//set this to true if you want HsimAutoRun to report information
        study.setWrite(true);//set this to true if you want HsimAutoRun to write the hsim data to a file
        study.setFilenameOut(filenameOut);
        study.setDelimiter(delimiter);
        study.run(resimSize);
    }
}

