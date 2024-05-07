package edu.cmu.tetrad.simulation;

import java.lang.reflect.Array;
import java.util.List;

/**
 * Created by Erich on 8/11/2016.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GdistanceUtils {
    //*************** Just a class of tertiary static methods used in some Gdistance projects*****//

    //this method returns a list of percents of how many members of input
    //fall into the various buckets determined by the array of thresholds (ordered from least to greatest)

    /**
     * Private constructor to prevent instantiation.
     */
    private GdistanceUtils() {
    }

    /**
     * Calculates the histogram of a given list of input values based on thresholds. The histogram is an array of
     * percents indicating how many values fall into each threshold bucket.
     *
     * @param input      the list of input values
     * @param thresholds the array of thresholds that define the buckets
     * @return the histogram as an array of percents for each bucket
     * @throws IllegalArgumentException if any value in the input list is less than or equal to the last threshold
     */
    public static double[] histogram(List<Double> input, double[] thresholds) {
        double[] output;
        output = new double[Array.getLength(thresholds) + 1];
        //will use length of input list when calcing percents
        double total = input.size();

        //init output to be all 0s
        for (int i = 0; i < Array.getLength(output); i++) {
            output[i] = 0;
        }

        //go through input, iterate whichever box it falls into
        for (Double i : input) {
            boolean nobinfound = true;
            for (int j = 0; j < Array.getLength(thresholds); j++) {
                if (i <= thresholds[j]) {
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
        for (int i = 0; i < Array.getLength(output); i++) {
            output[i] = output[i] / total;
        }

        return output;
    }
}
