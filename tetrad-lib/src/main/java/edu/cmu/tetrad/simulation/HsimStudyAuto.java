package edu.cmu.tetrad.simulation;

/**
 * Created by Erich on 3/28/2016.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class HsimStudyAuto {

    /**
     * Private constructor to prevent instantiation.
     */
    private HsimStudyAuto() {
    }

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        //***!!!!===!!!=== Parameters for the User to fill in! !!!===!!!===***
        final String readfilename = "YeastNoDupe2Slim.csv";
        final String filenameOut = "dataOutGeM.txt";
        final char delimiter = ','; //'\t';
        final int resimSize = 2;//number of variables to be resimmed
        HsimAutoRun study = new HsimAutoRun(readfilename, delimiter);
        study.setVerbose(false);//set this to true if you want HsimAutoRun to report information
        study.setWrite(true);//set this to true if you want HsimAutoRun to write the hsim data to a file
        study.setFilenameOut(filenameOut);
        study.setDelimiter(delimiter);
        study.run(resimSize);
    }
}

