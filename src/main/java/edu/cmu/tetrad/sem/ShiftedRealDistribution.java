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

/**
 * Represents a real distribution that is shifted along the independent axis.
 * Created by jdramsey on 8/28/15.
 */
public class ShiftedRealDistribution implements RealDistribution {
    private final RealDistribution dist;
    private final double shift;

    public ShiftedRealDistribution(RealDistribution dist, double shift) {
        this.dist = dist;
        this.shift = shift;
    }

    @Override
    public double probability(double v) {
        return dist.probability(v - shift);
    }

    @Override
    public double density(double v) {
        return dist.density(v - shift);
    }

    @Override
    public double cumulativeProbability(double v) {
        return dist.cumulativeProbability(v - shift);
    }

    @Override
    @Deprecated
    public double cumulativeProbability(double v, double v1) throws NumberIsTooLargeException {
        throw new UnsupportedOperationException();
    }

    @Override
    public double inverseCumulativeProbability(double v) throws OutOfRangeException {
        return dist.inverseCumulativeProbability(v - shift);
    }

    @Override
    public double getNumericalMean() {
        return dist.getNumericalMean();
    }

    @Override
    public double getNumericalVariance() {
        return dist.getNumericalVariance();
    }

    @Override
    public double getSupportLowerBound() {
        return dist.getSupportLowerBound();
    }

    @Override
    public double getSupportUpperBound() {
        return dist.getSupportUpperBound();
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
        return dist.isSupportConnected();
    }

    @Override
    public void reseedRandomGenerator(long l) {
        reseedRandomGenerator(l);
    }

    @Override
    public double sample() {
        return dist.sample();
    }

    @Override
    public double[] sample(int i) {
        return dist.sample(i);
    }
}

