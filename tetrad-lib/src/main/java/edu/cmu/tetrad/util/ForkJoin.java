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
 * A singleton class for managing a ForkJoinPool. All uses of ForkJoinPool in Tetrad should go through this class. New
 * pools can be created with the newPool method, and the stored pool can be retrieved with the getPool method. Every
 * algorithm run should begin by calling newPool with the number of threads to use, which returns the created pool.
 * Importantly, when Thread.currentThread().interrupt() is called, ForJoin.getInstance()l.getPool().shutdownNow() should
 * be called on the stored pool whereever the ForkJoin class is accessible. (In some modules it may not be.)  The effect
 * of calling this is that the pool will be shut down and all running threads in the pool will be interrupted. It is
 * important for this that ForkJoinPool.commonPool() NOT be used, calling shutdown() or shutdownNow() on it will have no
 * effect. (See the documentation for those methods in ForkJoinPool.) These methods are idempotent, so it is safe to
 * call them even if the pool has already been shut down and threads already interrupted.
 * <p>
 * It is important that all time-consuming methods in the Tetrad codebase check Thread.currentThread().isInterrupted(),
 * call ForkJoin.getInstance().getPool().shutdownNow() if it returns true, and return immediately or break out of the
 * loop. This will allow the user to interrupt the algorithm and have it shut down gracefully. It will also all the stop
 * button in the GUI to work correctly.
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
        int parallelism = Runtime.getRuntime().availableProcessors();
        pool = new ForkJoinPool(parallelism);
    }

    /**
     * Returns the instance of ForkJoinUtils.
     *
     * @return the instance of ForkJoinUtils.
     */
    public static ForkJoin getInstance() {
        return instance;
    }
}

