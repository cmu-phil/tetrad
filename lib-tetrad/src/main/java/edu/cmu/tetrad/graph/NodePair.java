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

/**
 * An unordered pair of nodes.
 *
 * @author Tyler Gibson
 */
public class NodePair {


    /**
     * The "First" node.
     */
    private Node first;


    /**
     * The "second" node.
     */
    private Node second;


    public NodePair(Node first, Node second){
        if(first == null){
            throw new NullPointerException("First node must not be null.");
        }
        if(second == null){
            throw new NullPointerException("Second node must not be null.");
        }
        this.first = first;
        this.second = second;
    }

    //============================== Public methods =============================//

    public Node getFirst(){
        return this.first;
    }

    public Node getSecond(){
        return this.second;
    }

    public int hashCode(){
        return this.first.hashCode() + this.second.hashCode();
    }


    public boolean equals(Object o){
        if(o == this){
            return true;
        }
        if(!(o instanceof NodePair)){
            return false;
        }
        NodePair thatPair = (NodePair)o;
        if(this.first.equals(thatPair.first) && this.second.equals(thatPair.second)){
            return true;
        }
        return this.first.equals(thatPair.second) && this.second.equals(thatPair.first);
    }

    public String toString(){
        return "{" + this.first + ", " + this.second + "}";
    }

}



