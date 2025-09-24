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

package edu.cmu.tetrad.util;

/**
 * This cancels all processes that check the TaskManager.getInstance().isCanceled() method.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TaskManager {
    private static final TaskManager instance = new TaskManager();

    /**
     * Whether the task has been canceled.
     */
    private boolean canceled;

    /**
     * Prevent instantiation.
     */
    private TaskManager() {
        this.canceled = false;
    }

    /**
     * <p>Getter for the field <code>instance</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.TaskManager} object
     */
    public static TaskManager getInstance() {
        return TaskManager.instance;
    }

    /**
     * <p>isCanceled.</p>
     *
     * @return a boolean
     */
    public boolean isCanceled() {
        return this.canceled;
    }

    /**
     * <p>Setter for the field <code>canceled</code>.</p>
     *
     * @param canceled a boolean
     */
    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }
}

