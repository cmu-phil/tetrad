package edu.cmu.tetrad.simulation;

/**
 * Created by Erich on 3/28/2016.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class HsimRepeatAuto {

    /**
     * Private constructor to prevent instantiation.
     */
    private HsimRepeatAuto() {

    }

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        //***!!!!===!!!=== Parameters for the User to fill in! !!!===!!!===***
        final String readfilename = "YeastNoDupe2Slim.csv";
        final String filenameOut = "dataOutYeast.txt";
        final char delimiter = ',';//'\t';

        final int resimSize = 2;
        final int repeat = 100;

        HsimRepeatAutoRun study = new HsimRepeatAutoRun(readfilename, delimiter);
        study.setVerbose(false);
        study.setWrite(true);
        study.setFilenameOut(filenameOut);
        study.setDelimiter(delimiter);
        study.run(resimSize, repeat);
    }
}

