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

package edu.cmu.tetrad.sem;

import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.exception.OutOfRangeException;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Only the cumulativeProbability, density, setShift methods are implemented.
 */
public class EmpiricalCdf implements RealDistribution {
    private List<Double> data;
    private Map<Double, Double> map = new HashMap<Double, Double>();

    public EmpiricalCdf(List<Double> data) {
        if (data == null) throw new NullPointerException();
        this.data = data;
        Collections.sort(data);
    }

    public double cumulativeProbability(double x) {
        int count = 0;

        for (double y : data) {
            if (y <= x) {
                count++;
            } else {
                break;
            }
        }

        return count / (double) data.size();
    }

    @Override
    public double probability(double v) {
        return 0;
    }

    @Override
    public double density(double v) {
        double d1 = v - 0.05;
        double d2 = v + 0.05;
        double n2 = cumulativeProbability(d1);
        double n1 = cumulativeProbability(d2);
        return (n1 - n2) / (d2 - d1);
    }

    @Override
    @Deprecated
    public double cumulativeProbability(double v, double v1) throws NumberIsTooLargeException {
        throw new UnsupportedOperationException();
    }

    @Override
    public double inverseCumulativeProbability(double v) throws OutOfRangeException {
        return 0;
    }

    @Override
    public double getNumericalMean() {
        return 0;
    }

    @Override
    public double getNumericalVariance() {
        return 0;
    }

    @Override
    public double getSupportLowerBound() {
        return 0;
    }

    @Override
    public double getSupportUpperBound() {
        return 0;
    }

    @Override
    @Deprecated
    public boolean isSupportLowerBoundInclusive() {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public boolean isSupportUpperBoundInclusive() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSupportConnected() {
        return false;
    }

    @Override
    public void reseedRandomGenerator(long l) {

    }

    @Override
    public double sample() {
        return 0;
    }

    @Override
    public double[] sample(int i) {
        return new double[0];
    }
}

