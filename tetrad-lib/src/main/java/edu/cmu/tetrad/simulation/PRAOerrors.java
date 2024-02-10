package edu.cmu.tetrad.simulation;

import java.lang.reflect.Array;
import java.util.List;

/**
 * Created by Erich on 8/21/2016. This class is for storing precision and recall values for both adjacencies and
 * orientations
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class PRAOerrors {
    private double AdjRecall, AdjPrecision, OrientPrecision, OrientRecall; //these store the actual errors
    private String errorsName; //this is a name identifying where the errors come from

    //****************CONSTRUCTORS*******************//
    //this constructor makes a PRAOerrors object from an array of error values.
    //make sure the array is storing its values in the correct order, if you use this

    /**
     * <p>Constructor for PRAOerrors.</p>
     *
     * @param input   an array of {@link double} objects
     * @param thename a {@link java.lang.String} object
     */
    public PRAOerrors(double[] input, String thename) {
        if (Array.getLength(input) == 5) {
            this.AdjRecall = input[1];
            this.AdjPrecision = input[2];
            this.OrientRecall = input[3];
            this.OrientPrecision = input[4];
            this.errorsName = thename;
        }
        if (Array.getLength(input) == 4) {
            this.AdjRecall = input[0];
            this.AdjPrecision = input[1];
            this.OrientRecall = input[2];
            this.OrientPrecision = input[3];
            this.errorsName = thename;
        }
        if (Array.getLength(input) != 4 && Array.getLength(input) != 5) {
            throw new IllegalArgumentException("Input array not of length 4 or 5");
        }
    }

    //method for constructing a mean PRAO from a list of PRAO objects

    /**
     * <p>Constructor for PRAOerrors.</p>
     *
     * @param input   a {@link java.util.List} object
     * @param thename a {@link java.lang.String} object
     */
    public PRAOerrors(List<PRAOerrors> input, String thename) {
        double totalAR = 0;
        double totalAP = 0;
        double totalOR = 0;
        double totalOP = 0;
        int countAR = 0;
        int countAP = 0;
        int countOR = 0;
        int countOP = 0;
        //iterate through members of the list, summing and counting all non-NaN values
        for (PRAOerrors errors : input) {
            if (!Double.isNaN(errors.getAdjRecall())) {
                totalAR += errors.getAdjRecall();
                countAR++;
            }
            if (!Double.isNaN(errors.getAdjPrecision())) {
                totalAP += errors.getAdjPrecision();
                countAP++;
            }
            if (!Double.isNaN(errors.getOrientRecall())) {
                totalOR += errors.getOrientRecall();
                countOR++;
            }
            if (!Double.isNaN(errors.getOrientPrecision())) {
                totalOP += errors.getOrientPrecision();
                countOP++;
            }
        }
        this.AdjRecall = totalAR / countAR;
        this.AdjPrecision = totalAP / countAP;
        this.OrientRecall = totalOR / countOR;
        this.OrientPrecision = totalOP / countOP;
        this.errorsName = thename;
    }

    //****************Public Methods******************8//

    /**
     * <p>getName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.errorsName;
    }

    /**
     * <p>getAdjRecall.</p>
     *
     * @return a double
     */
    public double getAdjRecall() {
        return this.AdjRecall;
    }

    /**
     * <p>getAdjPrecision.</p>
     *
     * @return a double
     */
    public double getAdjPrecision() {
        return this.AdjPrecision;
    }

    /**
     * <p>getOrientRecall.</p>
     *
     * @return a double
     */
    public double getOrientRecall() {
        return this.OrientRecall;
    }

    /**
     * <p>getOrientPrecision.</p>
     *
     * @return a double
     */
    public double getOrientPrecision() {
        return this.OrientPrecision;
    }

    /**
     * <p>valuesToString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String valuesToString() {
        return "AR: " + this.AdjRecall + " AP: " + this.AdjPrecision + " OR: " + this.OrientRecall + " OP: " + this.OrientPrecision;
    }

    //returns a string summarizing all the information

    /**
     * <p>allToString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String allToString() {
        String nl = System.lineSeparator();
        return this.errorsName + nl + "AR: " + this.AdjRecall + " AP: " + this.AdjPrecision + " OR: " + this.OrientRecall + " OP: " + this.OrientPrecision;
    }

    //returns an array of the error values

    /**
     * <p>toArray.</p>
     *
     * @return an array of {@link double} objects
     */
    public double[] toArray() {
        double[] output = new double[4];
        output[0] = this.AdjRecall;
        output[1] = this.AdjPrecision;
        output[2] = this.OrientRecall;
        output[3] = this.OrientPrecision;
        return output;
    }
}
