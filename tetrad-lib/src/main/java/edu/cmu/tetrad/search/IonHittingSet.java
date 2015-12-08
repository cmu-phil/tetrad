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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * **************************************************************************
 *
 * @author Trevor Burns
 * <p>
 * Provides a static method which implements a correct version of Reiter's hitting set algorithm as described by
 * Greiner, Smith, and Wilkerson in "A Correction to the Algorithm in Reiter's Theory of Diagnosis" Artificial
 * Intellegence 41 (1989) (see for detailed specification). However, this is not a general implementation, it is
 * tailored for use in the ION search by dealing with GraphChange objects instead of something more general.
 * <p>
 * Varies mainly in that no explicit DAG is constructed, it is only implied by the structure of the calls. This is
 * because this implementation is concerned solely with finding the hitting sets and once a node is processed its
 * information is never again accessed if one stores the necessary path of edge labels in newly constructed nodes.
 * <p>
 * There is one exception to the above claim. If one follows the revised algorithm strictly, in Enhancement Step 3 one
 * does need information from previous levels. The author decided this step was easier to code if one precomputes
 * instead of doing it on the fly, so this exception is inconsiquential.
 */
public class IonHittingSet {

    /**
     * takes a List of HashSets of GraphChanges, and returns a List of GraphChanges.
     */
    public static List<GraphChange> findHittingSet(List<Set<GraphChange>> Forig) {

        LinkedList<HsNode> currentLevel = new LinkedList<HsNode>();
        LinkedList<HsNode> nextLevel = new LinkedList<HsNode>();
        List<GraphChange> hittingSets = new ArrayList<GraphChange>();
        List<Set<GraphChange>> F;

        /* Enhancement Step 3 */
        F = precompute(Forig);

        /* Revised Step 1 */
        currentLevel.addFirst(new HsNode(new GraphChange(), 0));

        /* Revised Step 2: Loop processes all of one level of nodes before
         * starting the next. Only breaks when there are no more nodes in
         * getModel and next levels */
        while (!currentLevel.isEmpty()) {
            HsNode n = currentLevel.removeFirst();

            /* check redundency here in case of new hitting sets since node creation */
            if (nodeRedundant(n, hittingSets)) {
            } // do nothing
            else {
                int nextUCSigma = findNextUCSigma(F, n);

                /* Path intersects with all elements of F, add to hittingSets */
                if (nextUCSigma == -1)
                    hittingSets.add(n.getPath());

                    /* Node is labeled with first non-intersection */
                else {
                    n.updateLabel(nextUCSigma);

                    /* All possible downward arcs examined; either added or ignored */
                    for (GraphChange nextLCSigma : (F.get(nextUCSigma))) {
                        GraphChange newPath = new GraphChange(n.getPath());

                        if (newPath.isConsistent(nextLCSigma)) {
                            newPath.union(nextLCSigma);
                            if (pathNecessary(newPath, nextLevel))
                                nextLevel.add(new HsNode(newPath, n.getLabel()));
                        }
                    }
                }
            }

            /* if there are no nodes left in the getModel level, continue onto the next */
            if (currentLevel.isEmpty()) {
                currentLevel = nextLevel;
                nextLevel = new LinkedList<HsNode>();
            }
        }
        return hittingSets;
    }


    /**
     * Implements one of the listed enhancements, checking if there is a node on the next level which already has the
     * new path. Returns true if a new node should be created with that path and false if a new node is unnecessary.
     */
    private static boolean pathNecessary(GraphChange path, LinkedList<HsNode> nextLevel) {
        boolean necessary = true;

        /* Enhancement Step 1 */
        for (HsNode m : nextLevel) {
            if ((m.getPath()).equals(path)) {
                necessary = false;
                break;
            }
        }

        return necessary;
    }


    /**
     * Implements one of the listed enhancements, checking if the path for the given node n contains a known hitting
     * set. Returns true if the node is redundant and false if the node should be processed.
     */
    private static boolean nodeRedundant(HsNode n, List<GraphChange> hittingSets) {
        boolean redundant = false;

        /* Enhancement Step 2 */
        for (GraphChange hs : hittingSets) {
            if (n.getPath().contains(hs)) {
                redundant = true;
                break;
            }
        }

        return redundant;
    }


    /**
     * Given a node and the List of HashSets returns the index of the first HashSet in F that the node does not
     * intersect or -1 if all are intersected
     */
    private static int findNextUCSigma(List<Set<GraphChange>> F, HsNode n) {
        /* nodes are created with their parent's labels to make this function faster */
        int index = n.getLabel();
        GraphChange path = n.getPath();

        /* loop through elements of F */
        for (; index < F.size(); index++) {
            Set<GraphChange> sigma = F.get(index);
            boolean intersect = false;

            for (GraphChange aSigma : sigma) {
                if (path.contains(aSigma)) {
                    intersect = true;
                    break;
                }
            }
            if (!intersect) return index;
        }
        return -1;
    }


    /**
     * Takes a List of HashSets of GraphChanges, returns the same. Runs through List linearly, checking against all
     * later HashSets in the List (both ways) marking supersets with the addition of an empty GraphChange. Supersets are
     * removed at the end in order to avoid the O(n) removal for each set individually. This effectively implements
     * Enhancement step 3, just not the on-the-fly way specified.
     */
    private static List precompute(List<Set<GraphChange>> F) {
        int size = F.size();
        List<Set<GraphChange>> pruned = new ArrayList<Set<GraphChange>>(size);

        for (int i = 0; i < size; i++) {
            Set<GraphChange> setI = F.get(i);

            for (int j = (i + 1); j < size; j++) {
                Set<GraphChange> setJ = F.get(j);
                /* if one set is already marked, the following check is unnecessary */
                boolean notMarked = !setJ.contains(new GraphChange());

                if (notMarked && setI.containsAll(setJ)) {
                    setI.add(new GraphChange());
                    break;
                } else if (notMarked && setJ.containsAll(setI))
                    setJ.add(new GraphChange());
            }

            if (!setI.contains(new GraphChange()))
                pruned.add(F.get(i));
        }
        return pruned;
    }


    /**
     * Structure for containing the pair of the path (potential hitting set) and label (index of element of F which is
     * first non-intersection). Allows for label updating on the assumption that the node will be created with its
     * parent's label, to be used for a more efficient run of findNextUCSigma
     */
    private static class HsNode {
        private GraphChange path;
        private int label;

        public HsNode(GraphChange path, int label) {
            this.path = path;
            this.label = label;
        }

        public void updateLabel(int label) {
            this.label = label;
        }

        public GraphChange getPath() {
            return path;
        }

        public int getLabel() {
            return label;
        }
    }
}




