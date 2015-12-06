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

package edu.cmu.tetrad.util;

import java.util.List;

/**
 * Represents the configuration for the logger.  The idea is that each model has its own logger
 * configuration, which is merely the events that the model supports.
 *
 * @author Tyler Gibson
 */
public interface TetradLoggerConfig extends TetradSerializable {


    /**
     * States whether the event associated with the given id is active, that is whether
     * it should be logged or not.
     */
    boolean isEventActive(String id);


    /**
     * States whether the config is active or not. THe config is considered active if there is
     * at least one active event.
     *
     * @return - true iff its active.
     */
    boolean isActive();


    /**
     * @return - events
     */
    List<Event> getSupportedEvents();


    /**
     * Sets whether the event associated with the given id is active or not.
     */
    void setEventActive(String id, boolean active);

    /**
     * @return a copy of this configuration.
     */
    TetradLoggerConfig copy();

    /**
     * @return a string representation.
     */
    String toString();

    /**
     * Represents an event which is just an id and a description.
     */
    interface Event extends TetradSerializable {


        /**
         * @return the Sting ID fo the event.
         */
        String getId();


        /**
         * @return the String description of the event.
         */
        String getDescription();


    }


}



