///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.util.dist;

import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.text.NumberFormat;

/**
 * A normal distribution that allows its parameters to be set and allows random sampling. The parameters are 0 = mean, 1
 * = standard deviation.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TruncatedNormal implements Distribution {
    private static final long serialVersionUID = 23L;

    /**
     * The mean of the distribution.
     *
     * @serial
     */
    private double mean;

    /**
     * The standard devision of the distribution.
     *
     * @serial
     */
    private double sd;

    /**
     * The standard devision of the distribution.
     *
     * @serial
     */
    private double low;

    /**
     * The standard devision of the distribution.
     *
     * @serial
     */
    private double high;

    //=========================CONSTRUCTORS===========================//

    private TruncatedNormal() {
        setParameter(0, 0);
        setParameter(1, 1);
        setParameter(2, Double.NEGATIVE_INFINITY);
        setParameter(3, Double.POSITIVE_INFINITY);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return The exemplar.
     */
    public static TruncatedNormal serializableInstance() {
        return new TruncatedNormal();
    }

    //=========================PUBLIC METHODS=========================//

    /**
     * <p>getName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return "TruncNormal";
    }

    /**
     * {@inheritDoc}
     */
    public void setParameter(int index, double value) {
        if (index == 0) {
            this.mean = value;
        } else if (index == 1 && value >= 0) {
            this.sd = value;
        } else if (index == 2) {
            this.low = value;
        } else if (index == 3) {
            this.high = value;
        } else {
            throw new IllegalArgumentException("Illegal value for parameter " +
                                               index + ": " + value);
        }
    }

    /**
     * {@inheritDoc}
     */
    public double getParameter(int index) {
        if (index == 0) {
            return this.mean;
        } else if (index == 1) {
            return this.sd;
        } else {
            throw new IllegalArgumentException("Illegal index: " + index);
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getParameterName(int index) {
        if (index == 0) {
            return "Mean";
        } else if (index == 1) {
            return "Standard Deviation";
        } else if (index == 2) {
            return "Low";
        } else if (index == 3) {
            return "High";
        } else {
            throw new IllegalArgumentException("Not a parameter index: " + index);
        }
    }

    /**
     * <p>getNumParameters.</p>
     *
     * @return the number of parameters = 2.
     */
    public int getNumParameters() {
        return 4;
    }

    /**
     * <p>nextRandom.</p>
     *
     * @return the next random sample from the distribution.
     */
    public double nextRandom() {
        return RandomUtil.getInstance().nextTruncatedNormal(this.mean, this.sd, this.low, this.high);
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        return "TruncNormal(" + nf.format(this.mean) + ", " + nf.format(this.sd) + ", " + nf.format(this.low) + ", " + nf.format(this.high) + ")";
    }

    //========================PRIVATE METHODS===========================//

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }
}






