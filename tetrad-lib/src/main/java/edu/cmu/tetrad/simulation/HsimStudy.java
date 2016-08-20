package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fgs;
import edu.cmu.tetrad.search.PatternToDag;

import edu.cmu.tetrad.io.*;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Erich on 3/28/2016.
 */
public class HsimStudy {

    public static void main(String[] args) {
        //***!!!!===!!!=== Parameters for the User to fill in! !!!===!!!===***
        String readfilename = "DataHsimTest2.txt";//"GeMSlim.csv";
        String filenameOut = "dataOut2.txt";//"dataOutGeM.txt";
        char delimiter = '\t';//',';
        String[] resimNodeNames = {"X1","X2","X3","X4"};

        boolean verbose = true;//set verbose to false to suppress System.out reports

        HsimRun.run(readfilename,filenameOut,delimiter,resimNodeNames,verbose);

    }
}

