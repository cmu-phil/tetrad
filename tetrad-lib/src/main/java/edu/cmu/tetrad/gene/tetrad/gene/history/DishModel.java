///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.gene.tetrad.gene.history;

import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.dist.Distribution;
import edu.cmu.tetrad.util.dist.Normal;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Models the manner in which gene models are initialized differentially
 * depending on the dishes that the cells are in. The basic idea of the model is
 * that cells are grown in different dishes, and as a result may be initialized
 * differently. (They may also grow differently, although that is not being
 * modelled currently.) Causes of differentiation may be nutrition, temperature,
 * etc.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class DishModel implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The number of the getModel dish.
     *
     * @serial
     */
    private int dishNumber = 0;

    /**
     * The standard deviation of the normal distribution from which dish bump
     * values are drawn, in percent. The distribution has a mean of 100%.
     *
     * @serial
     */
    private double dishBumpStDev = 10.0;

    /**
     * An array of dish bumps for each dish.
     *
     * @serial
     */
    private double[] dishBumps;

    //===============================CONSTRUCTORS========================//

    public DishModel(int numDishes, double dishBumpStDev) {

        if (numDishes < 1) {
            throw new IllegalArgumentException(
                    "There must be at least one dish");
        }

        if (dishBumpStDev < 0.0) {
            throw new IllegalArgumentException(
                    "The stdev must be non-negative");
        }

        // numDishes is not stored at the class level, because the
        // length of dishBumps[] already contains that information.
        this.dishBumps = new double[numDishes];
        this.setDishBumpStDev(dishBumpStDev);

        Distribution distribution =
                new Normal(100.0, dishBumpStDev);

        for (int i = 0; i < numDishes; i++) {
            this.dishBumps[i] = distribution.nextRandom();
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static DishModel serializableInstance() {
        return new DishModel(1, 1.0);
    }

    //===============================PUBLIC METHODS======================//

    /**
     * Returns the number of the getModel dish.
     */
    public int getDishNumber() {
        return this.dishNumber;
    }

    /**
     * Sets the number of the getModel dish.
     */
    public void setDishNumber(int dishNumber) {

        if ((dishNumber >= 0) && (dishNumber < dishBumps.length)) {
            this.dishNumber = dishNumber;
        } else {
            throw new IllegalArgumentException(
                    "Invalid dish number: " + dishNumber);
        }
    }

    /**
     * Bumps the given expression value in the manner prescribed for the
     * getModel dish.
     */
    public double bumpInitialization(double expressionLevel) {

        //System.out.println(this.dishBumps[this.dishNumber]);
        return expressionLevel * this.dishBumps[this.dishNumber] / 100.0;
    }

    public double getDishBumpStDev() {
        return dishBumpStDev;
    }

    public void setDishBumpStDev(double dishBumpStDev) {
        this.dishBumpStDev = dishBumpStDev;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (dishBumps == null) {
            throw new NullPointerException();
        }

        if (dishBumpStDev < 0.0) {
            throw new IllegalStateException();
        }

        if (dishNumber < 0 || dishNumber >= dishBumps.length) {
            throw new IllegalStateException();
        }
    }

}





