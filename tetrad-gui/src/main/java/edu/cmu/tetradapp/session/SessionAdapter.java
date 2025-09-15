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

package edu.cmu.tetradapp.session;


/**
 * Basic implementation of SessionListener with empty methods.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SessionAdapter implements SessionListener {

    /**
     * {@inheritDoc}
     * <p>
     * Indicates that a node has been added.
     */
    public void nodeAdded(SessionEvent event) {
    }

    /**
     * {@inheritDoc}
     * <p>
     * Indicates that a node has been removed.
     */
    public void nodeRemoved(SessionEvent event) {
    }

    /**
     * {@inheritDoc}
     * <p>
     * Indicates that a parent has been added to a node. Note that this implies a child is added to the parent.
     */
    public void parentAdded(SessionEvent event) {
    }

    /**
     * {@inheritDoc}
     * <p>
     * Indicates that a parent has been removed from a node. Note that this implies a child is removed from the parent.
     */
    public void parentRemoved(SessionEvent event) {
    }

    /**
     * {@inheritDoc}
     * <p>
     * Indicates that a model has been created for a node.
     */
    public void modelCreated(SessionEvent event) {
    }

    /**
     * {@inheritDoc}
     * <p>
     * Indicates that a model has been destroyed for a node.
     */
    public void modelDestroyed(SessionEvent event) {
    }

    /**
     * {@inheritDoc}
     * <p>
     * Indicates that the createModel method has been called but there is more than one model consistent with the
     * parents, so a choice has to be made.
     */
    public void modelUnclear(SessionEvent event) {
    }

    /**
     * {@inheritDoc}
     * <p>
     * Indicates that a new execution of a simulation edu.cmu.tetrad.study has begun. (Some parameter objects need to be
     * reset for every execution.
     */
    public void executionStarted(SessionEvent event) {
    }

    /**
     * {@inheritDoc}
     * <p>
     * Indicates that the repetition of some node has changed.
     */
    public void repetitionChanged(SessionEvent event) {
    }

    /**
     * {@inheritDoc}
     * <p>
     * Indicates that the model is contemplating adding an edge (but hasn't yet).
     */
    public void addingEdge(SessionEvent event) {
    }
}






