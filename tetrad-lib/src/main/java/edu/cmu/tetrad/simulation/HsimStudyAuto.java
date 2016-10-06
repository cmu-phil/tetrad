package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.BigDataSetUtility;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataWriter;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.io.VerticalTabularDiscreteDataReader;
import edu.cmu.tetrad.search.Fgs;
import edu.cmu.tetrad.search.PatternToDag;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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

