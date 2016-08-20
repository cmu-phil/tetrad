package edu.cmu.tetrad.simulation;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Erich on 8/11/2016.
 */
public class GdistanceUtils {
    //*************** Just a class of tertiary static methods used in some Gdistance projects*****//

    //this method returns a list of percents of how many members of input
    //fall into the various buckets determined by the array of thresholds (ordered from least to greatest)
    public static double[] histogram(List<Double> input, double[] thresholds) {
        double[] output;
        output = new double[Array.getLength(thresholds)+1];
        //will use length of input list when calcing percents
        double total = (double) input.size();

        //init output to be all 0s
        for (int i=0; i<Array.getLength(output); i++){
            output[i]=0;
        }

        //go through input, iterate whichever box it falls into
        for (Double i : input){
            boolean nobinfound = true;
            for (int j=0;j<Array.getLength(thresholds);j++){
                if (i <= thresholds[j]){
                    output[j]++;
                    nobinfound = false;
                    break;
                }
            }
            if (nobinfound) {
                if (i > thresholds[Array.getLength(thresholds) - 1]) {
                    output[Array.getLength(thresholds)]++;
                } else {
                    throw new IllegalArgumentException(
                            "Something weird happened?");
                }
            }
        }

        //turn all the output values into percents
        for (int i=0;i<Array.getLength(output);i++){
            output[i]=output[i]/total;
        }

        return output;
    }
}
