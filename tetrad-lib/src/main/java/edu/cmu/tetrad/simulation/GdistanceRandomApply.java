package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.io.TabularContinuousDataReader;

import java.lang.reflect.Array;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Created by Erich on 8/6/2016.
 */
public class GdistanceRandomApply {
    public static void main (String... args) {
        //thresholds are the barriers between histogram buckets.
        double[] thresholds;
        thresholds = new double[5];
        thresholds[0]=0;
        thresholds[1]=2;
        thresholds[2]=4;
        thresholds[3]=6;
        thresholds[4]=8;
        //load the location map
        String workingDirectory = System.getProperty("user.dir");
        System.out.println(workingDirectory);
        Path mapPath = Paths.get("erich_coordinates.txt");
        System.out.println(mapPath);
        edu.cmu.tetrad.io.DataReader dataReaderMap = new TabularContinuousDataReader(mapPath, ',');
        try{
            DataSet locationMap = dataReaderMap.readInData();
            System.out.println("locationMap loaded");

            GdistanceRandom simRandGdistances = new GdistanceRandom(locationMap);
            System.out.println("GdistanceRandom constructed");

            simRandGdistances.setNumEdges1(300);
            simRandGdistances.setNumEdges2(300);
            simRandGdistances.setVerbose(false);

            System.out.println("Edge parameters set, starting simulations");
            List<List<Double>> GdistanceLists = simRandGdistances.randomSimulation(2);
            System.out.println("Simulations done, calculating histograms");
            for (List<Double> gdist : GdistanceLists){
                double[] histogram = GdistanceUtils.histogram(gdist,thresholds);
                //making the string to print out histogram values
                String histString = " ";
                for (int i=0;i< Array.getLength(histogram);i++){
                    histString=histString+" "+histogram[i];
                }
                System.out.println(histString);
            }
        }
        catch(Exception IOException){
            IOException.printStackTrace();
        }
    }
}
