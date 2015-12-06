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


import java.util.*;

/**
 * Logger configuration.
 *
 * @author Tyler Gibson
 */
public class DefaultTetradLoggerConfig implements TetradLoggerConfig {

    static final long serialVersionUID = 23L;

    /**
     * The events that are supported.
     */
    private List<Event> events;


    /**
     * The event ids that are currently active.
     */
    private Set<String> active = new HashSet<>();


    /**
     * Constructs the config given the events in it.
     *
     * @param events The events that the logger reports.
     */
    public DefaultTetradLoggerConfig(List<Event> events) {
        if (events == null) {
            throw new NullPointerException("The given list of events must not be null");
        }
        this.events = new ArrayList<>(events);
    }


    /**
     * Constructs the config for the given event ids. This will create <code>Event</code>s with
     * no descriptions.
     *
     * @param events The events that the logger reports.
     */
    public DefaultTetradLoggerConfig(String... events) {
        this.events = new ArrayList<>(events.length);
        this.active = new HashSet<>();
        for (String event : events) {
            this.events.add(new DefaultEvent(event, "No Description"));
            this.active.add(event);
        }
    }

    public TetradLoggerConfig copy() {
        DefaultTetradLoggerConfig copy = new DefaultTetradLoggerConfig();
        copy.events = new ArrayList<>(this.events);
        copy.active = new HashSet<>(this.active);
        return copy;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static DefaultTetradLoggerConfig serializableInstance() {
        return new DefaultTetradLoggerConfig();
    }

    //=========================== public methods ================================//

    public boolean isEventActive(String id) {
        return this.active.contains(id);
    }

    public boolean isActive() {
        return !this.active.isEmpty();
    }

    public List<Event> getSupportedEvents() {
        return Collections.unmodifiableList(this.events);
    }

    public void setEventActive(String id, boolean active) {
        if (!contains(id)) {
            throw new IllegalArgumentException("There is no event known under the given id: " + id);
        }
        if (active) {
            this.active.add(id);
        } else {
            this.active.remove(id);
        }
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("\nDefaultTetradLoggerConfig: events as follows:");

        for (Event event : events) {
            buf.append("\n").append(event).append(active.contains(event.getId()) ? " (active)" : "");
        }

        return buf.toString();
    }

    //======================= Private Methods ==================================//

    private boolean contains(String id) {
        for (Event event : this.events) {
            if (id.equals(event.getId())) {
                return true;
            }
        }
        return false;
    }

    //================================= Inner class ==================================//

    public static class DefaultEvent implements TetradLoggerConfig.Event {
        static final long serialVersionUID = 23L;

        private String id;
        private String description;


        public DefaultEvent(String id, String description) {
            if (id == null) {
                throw new NullPointerException("The given id must not be null");
            }
            if (description == null) {
                throw new NullPointerException("The given description must not be null");
            }
            this.id = id;
            this.description = description;
        }


        /**
         * Generates a simple exemplar of this class to test serialization.
         */
        public static DefaultEvent serializableInstance() {
            return new DefaultEvent("", "");
        }

        public String getId() {
            return this.id;
        }

        public String getDescription() {
            return this.description;
        }

        public String toString() {
            return "Event(" + id + ", " + description + ")";
        }
    }

}




