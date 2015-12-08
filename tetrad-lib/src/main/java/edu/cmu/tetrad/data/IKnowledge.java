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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.TetradSerializable;

import java.util.Iterator;
import java.util.List;

/**
 * Interface for knowledge of forbidden and required edges. Implemented
 * in different ways. See implementations.
 */
public interface IKnowledge extends TetradSerializable {
    long serialVersionUID = 23L;

    void addToTier(int tier, String var);

    void addToTiersByVarNames(List<String> varNames);

    void addKnowledgeGroup(KnowledgeGroup group);

    void addVariable(String varName);

    void clear();

    boolean equals(Object o);

    Iterator<KnowledgeEdge> explicitlyForbiddenEdgesIterator();

    Iterator<KnowledgeEdge> explicitlyRequiredEdgesIterator();

    Iterator<KnowledgeEdge> forbiddenEdgesIterator();

    List<KnowledgeGroup> getKnowledgeGroups();

    List<String> getVariables();

    List<String> getVariablesNotInTiers();

    List<String> getTier(int tier);

    int getNumTiers();

    int hashCode();

    boolean isDefaultToKnowledgeLayout();

    boolean isForbidden(String var1, String var2);

    boolean isForbiddenByGroups(String var1, String var2);

    boolean isForbiddenByTiers(String var1, String var2);

    boolean isRequired(String var1, String var2);

    boolean isRequiredByGroups(String var1, String var2);

    boolean isEmpty();

    boolean isTierForbiddenWithin(int tier);

    boolean isViolatedBy(Graph graph);

    boolean noEdgeRequired(String x, String y);

    void removeFromTiers(String var);

    void removeKnowledgeGroup(int index);

    void removeVariable(String varName);

    Iterator<KnowledgeEdge> requiredEdgesIterator();

    void setForbidden(String var1, String var2);

    void removeForbidden(String spec1, String spec2);

    void setRequired(String var1, String var2);

    void removeRequired(String var1, String var2);

    void setKnowledgeGroup(int index, KnowledgeGroup group);

    void setTier(int tier, List<String> vars);

    void setTierForbiddenWithin(int tier, boolean forbidden);

    int getMaxTierForbiddenWithin();

    void setDefaultToKnowledgeLayout(boolean defaultToKnowledgeLayout);

    String toString();

    IKnowledge copy();
}



