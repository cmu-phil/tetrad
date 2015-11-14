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

package edu.cmu.tetrad.search.kernel;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

/**
 * Gaussian kernel for a given bandwidth. Default bandwidth is set using the median distance heuristic.
 *
 * @author Robert Tillman
 */
public final class KernelGaussian implements Kernel {

    /**
     * The bandwidth
     */
    private double sigma;

    /**
     * Creates a new Gaussian kernel with the given bandwidth
     *
     * @param sigma the bandwidth
     */
    public KernelGaussian(double sigma) {
        if (sigma <= 0)
            throw new IllegalArgumentException("Sigma must be > 0");
        this.sigma = sigma;
    }

    /**
     * Creates a new Gaussian kernel using the median distance between points to set the bandwidth
     *
     * @param dataset dataet containing variable used to set bandwidth
     * @param node    variable used to set bandwidth
     */
    public KernelGaussian(DataSet dataset, Node node) {
        setMedianBandwidth(dataset, node);
    }

    /**
     * @return the bandwidth
     */
    public double getBandwidth() {
        return this.sigma;
    }

    /**
     * Evaluates the kernel at two given points
     *
     * @param i first point
     * @param j second point
     * @return
     */
    public double eval(double i, double j) {
        double evalKernel = Math.exp(-.5 * (Math.pow((i - j), 2) / Math.pow(sigma, 2)));
        return evalKernel;
    }

    /**
     * Default setting of bandwidth based on median distance heuristic
     *
     * @param dataset
     * @param node
     * @return
     */
    public void setDefaultBw(DataSet dataset, Node node) {
        setMedianBandwidth(dataset, node);
    }


    /**
     * Sets the bandwidth of the kernel to median distance between two points in the given vector
     *
     * @param dataset dataet containing variable used to set bandwidth
     * @param node    variable used to set bandwidth
     */
    public void setMedianBandwidth(DataSet dataset, Node node) {
        int col = dataset.getColumn(node);
        int m = dataset.getNumRows();

        double[] diff = new double[(int) Math.pow(m, 2) - m];
        int c = 0;
        for (int i = 0; i < (m - 1); i++) {
            for (int j = (i + 1); j < m; j++) {
                diff[c] = Math.abs(dataset.getDouble(i, col) - dataset.getDouble(j, col));
                c++;
            }
        }

        this.sigma = find(diff, 0, (m - 1));
    }

    // private method for finding median distance

    private double find(double[] a, int from, int to) {
        int low = from;
        int high = to;
        int median = (low + high) / 2;
        do {
            if (high <= low) {
                return a[median];
            }
            if (high == low + 1) {
                if (a[low] > a[high]) {
                    swap(a, low, high);
                }
                return a[median];
            }
            int middle = (low + high) / 2;
            if (a[middle] > a[high]) {
                swap(a, middle, high);
            }
            if (a[low] > a[high]) {
                swap(a, low, high);
            }
            if (a[middle] > a[low]) {
                swap(a, middle, low);
            }
            swap(a, middle, low + 1);
            int ll = low + 1;
            int hh = high;
            do {
                do {
                    ll++;
                }
                while (a[low] > a[ll]);
                do {
                    hh--;
                }
                while (a[hh] > a[low]);
                if (hh < ll) {
                    break;
                }
                swap(a, ll, hh);
            }
            while (true);
            swap(a, low, hh);
            if (hh <= median) {
                low = ll;
            }
            if (hh >= median) {
                high = hh - 1;
            }
        }
        while (true);
    }

    private void swap(double[] a, int i1, int i2) {
        double temp = a[i1];
        a[i1] = a[i2];
        a[i2] = temp;
    }


}



