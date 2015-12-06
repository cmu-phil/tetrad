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

package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.TetradSerializableExcluded;

/**
 * An ordered pair of objects. This does not serialize well, unfortunately.
 *
 * @author Tyler Gibson
 */
public class OrderedPair<E> implements TetradSerializable, TetradSerializableExcluded {
    static final long serialVersionUID = 23L;

    /**
     * The "First" node.
     */
    private E first;


    /**
     * The "second" node.
     */
    private E second;


    public OrderedPair(E first, E second) {
        if (first == null) {
            throw new NullPointerException("First node must not be null.");
        }
        if (second == null) {
            throw new NullPointerException("Second node must not be null.");
        }
        this.first = first;
        this.second = second;
    }

    //============================== Public methods =============================//

    public E getFirst() {
        return this.first;
    }

    public E getSecond() {
        return this.second;
    }

    public int hashCode() {
        return 13 * this.first.hashCode() + 67 * this.second.hashCode();
    }

    public boolean equals(Object o) {
        if (!(o instanceof OrderedPair)) throw new IllegalArgumentException();
        OrderedPair<E> that = (OrderedPair<E>) o;
        return this.first.equals(that.first) && this.second.equals(that.second);
    }

    public String toString() {
        return "<" + this.first + ", " + this.second + ">";
    }

}



