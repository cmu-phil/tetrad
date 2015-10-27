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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;

import java.text.NumberFormat;
import java.util.Iterator;
import java.util.List;

/**
 * Contains utilities for logging search steps.
 *
 * @author Joseph Ramsey
 */
public class SearchLogUtils {
    private static NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    public static String endpointOrientedMsg(Endpoint e, Node x, Node y) {
        char endptChar = '*';

        if (e == Endpoint.TAIL) {
            endptChar = '-';
        } else if (e == Endpoint.ARROW) {
            endptChar = '>';
        } else if (e == Endpoint.CIRCLE) {
            endptChar = 'o';
        }

        String msg = "Orienting endpoint: " + x.getName() + " *-" + endptChar +
                " " + y.getName();
        return msg;
    }

    public static String edgeOrientedMsg(String reason, Edge edge) {
        return "Orienting edge (" + reason + "): " + edge;
    }

    public static String colliderOrientedMsg(String note, Node x, Node y, Node z) {
        return "Orienting collider (" + note + "): " + x.getName() + " *-> " +
                y.getName() + " <-* " + z.getName();
    }

    public static String colliderOrientedMsg(Node x, Node y, Node z) {
        return "Orienting collider: " + x.getName() + " *-> " +
                y.getName() + " <-* " + z.getName();
    }

    public static String colliderOrientedMsg(Node x, Node y, Node z, List sepset) {
        return "Orienting collider: " + x.getName() + " *-> " +
                y.getName() + " <-* " + z.getName() + "\t(Sepset = " + sepset +
                ")";
    }

    public static String independenceFactMsg(Node x, Node y, List<Node> condSet, double pValue) {
        StringBuilder sb = new StringBuilder();

        sb.append("Independence accepted: ");
        sb.append(independenceFact(x, y, condSet));

        if (!Double.isNaN(pValue)) {
            sb.append("\tp = ").append(nf.format(pValue));
        }

        return sb.toString();
    }

    public static String dependenceFactMsg(Node x, Node y, List<Node> condSet, double pValue) {
        StringBuilder sb = new StringBuilder();

        sb.append("Dependent: ");
        sb.append(independenceFact(x, y, condSet));

        if (!Double.isNaN(pValue)) {
            sb.append("\tp = ").append(nf.format(pValue));
        }

        return sb.toString();
    }


    public static String independenceFact(Node x, Node y, List<Node> condSet) {
        StringBuilder sb = new StringBuilder();

        sb.append(x.getName());
        sb.append(" _||_ ");
        sb.append(y.getName());

        Iterator it = condSet.iterator();

        if (it.hasNext()) {
            sb.append(" | ");
            sb.append(it.next());
        }

        while (it.hasNext()) {
            sb.append(", ");
            sb.append(it.next());
        }

        return sb.toString();
    }

}





