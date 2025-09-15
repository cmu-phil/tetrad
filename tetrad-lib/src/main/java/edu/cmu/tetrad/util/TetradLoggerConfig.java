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

import java.util.List;

/**
 * Represents the configuration for the logger.  The idea is that each model has its own logger configuration, which is
 * merely the events that the model supports.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public interface TetradLoggerConfig extends TetradSerializable {

    /**
     * States whether the event associated with the given id is active, that is whether it should be logged or not.
     *
     * @param id a {@link java.lang.String} object
     * @return a boolean
     */
    boolean isEventActive(String id);


    /**
     * States whether the config is active or not. THe config is considered active if there is at least one active
     * event.
     *
     * @return - true iff its active.
     */
    boolean active();


    /**
     * Retrieves the list of supported events.
     *
     * @return the list of supported events
     */
    List<Event> getSupportedEvents();


    /**
     * Sets whether the event associated with the given id is active or not.
     *
     * @param id     a {@link java.lang.String} object
     * @param active a boolean
     */
    void setEventActive(String id, boolean active);

    /**
     * Creates a copy of the TetradLoggerConfig object.
     *
     * @return a copy of the TetradLoggerConfig object
     */
    TetradLoggerConfig copy();

    /**
     * Returns a string representation of the object.
     *
     * @return the string representation of the object
     */
    String toString();

    /**
     * Represents an event which is just an id and a description.
     */
    interface Event extends TetradSerializable {


        /**
         * Returns the ID of the event.
         *
         * @return the ID of the event
         */
        String getId();


        /**
         * Returns the description of the event.
         *
         * @return the description of the event
         */
        String getDescription();
    }
}




