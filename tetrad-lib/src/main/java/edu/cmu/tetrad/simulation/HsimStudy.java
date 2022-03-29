package edu.cmu.tetrad.simulation;

/**
 * Created by Erich on 3/28/2016.
 */
public class HsimStudy {

    public static void main(final String[] args) {
        //***!!!!===!!!=== Parameters for the User to fill in! !!!===!!!===***
        final String readfilename = "DataHsimTest2.txt";//"GeMSlim.csv";
        final String filenameOut = "dataOut2.txt";//"dataOutGeM.txt";
        final char delimiter = '\t';//',';
        final String[] resimNodeNames = {"X1", "X2", "X3", "X4"};

        final boolean verbose = true;//set verbose to false to suppress System.out reports

        HsimRun.run(readfilename, filenameOut, delimiter, resimNodeNames, verbose);

    }
}

