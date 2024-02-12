///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

package edu.cmu.tetrad.util;

import java.util.concurrent.ForkJoinPool;

/**
 * A singleton class for managing a ForkJoinPool.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ForkJoin {

    /**
     * The singleton instance of ForkJoinUtils.
     */
    private static final ForkJoin instance = new ForkJoin();

    /**
     * The ForkJoinPool.
     */
    private ForkJoinPool pool = new ForkJoinPool();

    /**
     * Private constructor.
     */
    private ForkJoin() {
        pool = newPool(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Returns the instance of ForkJoinUtils.
     */
    public static ForkJoin getInstance() {
        return instance;
    }

    /**
     * Returns a ForkJoinPool with the given parallelism. If parallelism is 1, returns the common pool. A call to this
     * method will shut down the current pool and create a new one.
     *
     * @param parallelism the number of threads to use.
     * @return a new ForkJoinPool with the given parallelism.
     */
    public ForkJoinPool newPool(int parallelism) {
        pool.shutdownNow();
        ForkJoinPool pool = new ForkJoinPool(parallelism);
        this.pool = pool;
        return pool;
    }

    /**
     * Returns the stored ForkJoinPool.
     *
     * @return the stored ForkJoinPool.
     */
    public ForkJoinPool getPool() {
        return pool;
    }
}

