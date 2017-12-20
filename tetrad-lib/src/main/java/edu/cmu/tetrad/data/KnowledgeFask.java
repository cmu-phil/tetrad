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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.OrderedPair;
import edu.cmu.tetrad.search.Fask;
import edu.cmu.tetrad.search.SemBicScore;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradSerializable;

import java.util.*;
import java.util.regex.Matcher;

import static java.lang.Math.signum;
import static java.lang.Math.sqrt;

/**
 * Stores information about required and forbidden edges and common causes for
 * use in algorithm. This information can be set edge by edge or else globally
 * via temporal tiers. When setting temporal tiers, all edges from later tiers
 * to earlier tiers are forbidden.
 * <p>
 * For this class, all variable names are referenced by name only. This is
 * because the same Knowledge object is intended to plug into different graphs
 * with MyNodes that possibly have the same names. Thus, if the Knowledge object
 * forbids the edge X --> Y, then it forbids any edge which connects a MyNode
 * named "X" to a MyNode named "Y", even if the underlying MyNodes themselves
 * named "X" and "Y", respectively, are not the same.
 * <p>
 * In place of variable names, wildcard expressions containing the wildcard '*'
 * may be substituted. These will be matched to as many myNodes as possible. The
 * '*' wildcard matches any string of consecutive characters up until the
 * following character is encountered. Thus, "X*a" will match "X123a" and
 * "X45a".
 *
 * @author Joseph Ramsey
 */
public final class KnowledgeFask implements TetradSerializable, IKnowledge {

    static final long serialVersionUID = 23L;
    private final DataSet dataSet;
    private final double[][] colData;
    private final Graph faskGraph;
    private double alpha = 1e-6;
    private double penaltyDiscount = 2;
    private boolean presumePositiveCoefficients = true;
    private IKnowledge givenKnowledge = new Knowledge2();

    private Set<MyNode> myNodes = new HashSet<>();

//    private List<OrderedPair<Set<MyNode>>> forbiddenRulesSpecs;
//    private List<OrderedPair<Set<MyNode>>> requiredRulesSpecs;
//    private List<Set<MyNode>> tierSpecs;

    // Legacy.
//    private final List<KnowledgeGroup> knowledgeGroups = new ArrayList<>();
//    private Map<KnowledgeGroup, OrderedPair<Set<MyNode>>> knowledgeGroupRules;

//    private boolean defaultToKnowledgeLayout = false;

    private Map<String, MyNode> namesToVars = new HashMap<>();

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public boolean isPresumePositiveCoefficients() {
        return presumePositiveCoefficients;
    }

    public void setPresumePositiveCoefficients(boolean presumePositiveCoefficients) {
        this.presumePositiveCoefficients = presumePositiveCoefficients;
    }

    // Wraps a variable name so that it has object identity. For speed.
    public static class MyNode implements Comparable, TetradSerializable {

        static final long serialVersionUID = 23L;
        private final String name;

        public MyNode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public String toString() {
            return getName();
        }

        public static MyNode serializableInstance() {
            return new MyNode("X");
        }

        @Override
        public int compareTo(Object o) {
            if (o == null) {
                throw new NullPointerException();
            }
            if (!(o instanceof MyNode)) {
                throw new IllegalArgumentException();
            }
            MyNode node = (MyNode) o;
            return getName().compareTo(node.getName());
        }
    }

    //================================CONSTRUCTORS========================//
    /**
     * Constructs a blank knowledge object.
     */
    public KnowledgeFask(DataSet dataSet, IKnowledge givenKnowledge) {
        this.givenKnowledge = givenKnowledge;

//        this.forbiddenRulesSpecs = new ArrayList<>();
//        this.requiredRulesSpecs = new ArrayList<>();
//        this.knowledgeGroupRules = new HashMap<>();
//        this.tierSpecs = new ArrayList<>();

        this.namesToVars = new HashMap<>();

        this.dataSet = DataUtils.center(dataSet);
        colData = dataSet.getDoubleData().transpose().toArray();

        for (int j = 0; j < colData.length; j++) {
            double[] x = colData[j];

            double s = signum(StatUtils.skewness(x));

            for (int i = 0; i < x.length; i++) {
                x[i] = s * x[i];
            }

            colData[j] = x;
        }

        Fask fask = new Fask(dataSet, new SemBicScore(new CovarianceMatrixOnTheFly(dataSet)));
        fask.setPenaltyDiscount(getPenaltyDiscount());
        fask.setAlpha(getAlpha());
        fask.setDepth(-1);

        this.faskGraph = fask.search();

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static KnowledgeFask serializableInstance() {
        return new KnowledgeFask(new ColtDataSet(0, new ArrayList<>()), new Knowledge2());
    }

    //===============================PUBLIC METHODS=======================//
    private MyNode getVar(String var1) {
        return namesToVars.get(var1);
    }

    /**
     * Adds the given variable or wildcard pattern to the given tier. The tier
     * is a non-negative integer.
     */
    public final void addToTier(int tier, String spec) {
        givenKnowledge.addToTier(tier, spec);

//        addVariable(spec);
//
//        if (tier < 0) {
//            throw new IllegalArgumentException();
//        }
//
//        if (spec == null) {
//            throw new NullPointerException();
//        }
//
//        spec = checkSpec(spec);
//        final Set<MyNode> vars = getExtent(spec);
//
//        ensureTiers(tier);
//
//        for (MyNode s : vars) {
//            if (checkVarName(s.getName())) {
//                addVariable(s.getName());
//                tierSpecs.get(tier).add(s);
//            }
//        }
    }

    /**
     * Puts a variable into tier i if its name is xxx:ti for some xxx and some
     * i.
     */
    public final void addToTiersByVarNames(List<String> myNodes) {
        givenKnowledge.addToTiersByVarNames(myNodes);

//        if (!this.myNodes.containsAll(myNodes)) {
//            for (String variable : myNodes) {
//                if (!checkVarName(variable)) {
//                    throw new IllegalArgumentException("Bad variable name: " + variable);
//                }
//                addVariable(variable);
//            }
//        }
//
//        for (Object variable : myNodes) {
//            String MyNode = (String) variable;
//            int index = MyNode.lastIndexOf(":t");
//
//            if (index != -1) {
//                String substring = MyNode.substring(index + 2);
//                addToTier(new Integer(substring), MyNode);
//            }
//        }
    }

    /**
     * @return a shallow copy of the list of group rules.
     */
    public List<KnowledgeGroup> getKnowledgeGroups() {
        return givenKnowledge.getKnowledgeGroups();
//        return new ArrayList<>(this.knowledgeGroups);
    }

    /**
     * Removes the knowledge group at the given index.
     */
    public void removeKnowledgeGroup(int index) {
        givenKnowledge.removeKnowledgeGroup(index);

//        OrderedPair<Set<MyNode>> old = knowledgeGroupRules.get(knowledgeGroups.get(index));
//
//        forbiddenRulesSpecs.remove(old);
//        requiredRulesSpecs.remove(old);
//
//        this.knowledgeGroups.remove(index);
    }

    /**
     * Adds a knowledge group. Legacy method, replaced by setForbidden,
     * setRequired with patterns. Needed for the interface.
     */
    public void addKnowledgeGroup(KnowledgeGroup group) {
        givenKnowledge.addKnowledgeGroup(group);

//        this.knowledgeGroups.add(group);
//
//        OrderedPair<Set<MyNode>> o = getGroupRule(group);
//        knowledgeGroupRules.put(group, o);
//
//        if (group.getType() == KnowledgeGroup.FORBIDDEN) {
//            forbiddenRulesSpecs.add(o);
//        } else if (group.getType() == KnowledgeGroup.REQUIRED) {
//            requiredRulesSpecs.add(o);
//        }
    }

    /**
     * Legacy, do not use.
     */
    public void setKnowledgeGroup(int index, KnowledgeGroup group) {
        givenKnowledge.setKnowledgeGroup(index, group);

//        OrderedPair<Set<MyNode>> o = getGroupRule(group);
//        OrderedPair<Set<MyNode>> old = knowledgeGroupRules.get(knowledgeGroups.get(index));
//
//        forbiddenRulesSpecs.remove(old);
//        requiredRulesSpecs.remove(old);
//
//        if (group.getType() == KnowledgeGroup.FORBIDDEN) {
//            forbiddenRulesSpecs.add(o);
//        } else if (group.getType() == KnowledgeGroup.REQUIRED) {
//            requiredRulesSpecs.add(o);
//        }
//
//        knowledgeGroups.set(index, group);
    }

    /**
     * Iterator over the KnowledgeEdge's representing forbidden edges.
     */
    public final Iterator<KnowledgeEdge> forbiddenEdgesIterator() {
        return givenKnowledge.forbiddenEdgesIterator();

//        Set<KnowledgeEdge> edges = new HashSet<>();
//
//        for (OrderedPair<Set<MyNode>> o : forbiddenRulesSpecs) {
//            final Set<MyNode> first = o.getFirst();
//            for (MyNode s1 : first) {
//                final Set<MyNode> second = o.getSecond();
//                for (MyNode s2 : second) {
//                    if (!s1.equals(s2)) {
//                        edges.add(new KnowledgeEdge(s1.getName(), s2.getName()));
//                    }
//                }
//            }
//        }
//
//        return edges.iterator();
    }

    /**
     * Iterator over the knowledge's explicitly forbidden edges.
     */
    public final Iterator<KnowledgeEdge> explicitlyForbiddenEdgesIterator() {
        return givenKnowledge.explicitlyForbiddenEdgesIterator();

//        Set<OrderedPair<Set<MyNode>>> copy = new HashSet<>(forbiddenRulesSpecs);
//        copy.removeAll(forbiddenTierRules());
//
//        for (KnowledgeGroup group : knowledgeGroups) {
//            copy.remove(knowledgeGroupRules.get(group));
//        }
//
//        Set<KnowledgeEdge> edges = new HashSet<>();
//
//        for (OrderedPair<Set<MyNode>> o : copy) {
//            final Set<MyNode> first = o.getFirst();
//            for (MyNode s1 : first) {
//                final Set<MyNode> second = o.getSecond();
//                for (MyNode s2 : second) {
//                    edges.add(new KnowledgeEdge(s1.getName(), s2.getName()));
//                }
//            }
//        }
//
//        return edges.iterator();
    }

    /**
     * @return the list of edges not in any tier.
     */
    public final List<String> getVariablesNotInTiers() {
        return givenKnowledge.getVariablesNotInTiers();

//        List<MyNode> notInTier = new ArrayList<>(myNodes);
//
//        for (Set<MyNode> tier : tierSpecs) {
//            if (tier == null) {
//                tier = new HashSet<>();
//            }
//            notInTier.removeAll(tier);
//        }
//
//        List<String> names = new ArrayList<>();
//        for (MyNode MyNode : notInTier) {
//            names.add(MyNode.getName());
//        }
//
//        return names;
    }

    /**
     * @param tier the index of the desired tier.
     * @return a copy of this tier.
     */
    public final List<String> getTier(int tier) {
        return givenKnowledge.getTier(tier);

//        ensureTiers(tier);
//
//        List<String> names = new ArrayList<>();
//        for (MyNode MyNode : tierSpecs.get(tier)) {
//            names.add(MyNode.getName());
//        }
//
//        Collections.sort(names);
//        return names;
    }

    /**
     * @return the number of temporal tiers.
     */
    public final int getNumTiers() {
        return givenKnowledge.getNumTiers();
//        return tierSpecs.size();
    }

    /**
     * Determines whether the edge var1 --> var2 is forbidden.
     */
    public final boolean isForbidden(String var1, String var2) {
        Node x = faskGraph.getNode(var1);
        Node y = faskGraph.getNode(var2);

        if (faskGraph.getEdges(x, y).size() == 2) return false;

        int c1 = dataSet.getColumn(dataSet.getVariable(var1));
        int c2 = dataSet.getColumn(dataSet.getVariable(var2));
        return givenKnowledge.isForbidden(var1, var2) || leftright(colData[c2], colData[c1]);
    }


    private boolean leftright(double[] x, double[] y) {
        double left = cu(x, y, x) / (sqrt(cu(x, x, x) * cu(y, y, x)));
        double right = cu(x, y, y) / (sqrt(cu(x, x, y) * cu(y, y, y)));
        double lr = left - right;
        if (!isPresumePositiveCoefficients()) lr *= StatUtils.correlation(x, y);
        return lr > 0;
    }

    public static double cu(double[] x, double[] y, double[] condition) {
        double exy = 0.0;

        int n = 0;

        for (int k = 0; k < x.length; k++) {
            if (condition[k] > 0) {
                exy += x[k] * y[k];
                n++;
            }
        }

        return exy / n;
    }


    /**
     * Determines whether the edge var1 --> var2 is required..
     */
    public final boolean isRequired(String var1, String var2) {
        return givenKnowledge.isRequired(var1, var2);

//        for (OrderedPair<Set<MyNode>> rule : requiredRulesSpecs) {
//            if (rule.getFirst().contains(getVar(var1))) {
//                if (rule.getSecond().contains(getVar(var2))) {
//                    if (!var1.equals(var2)) {
//                        return true;
//                    }
//                }
//            }
//        }
//
//        return false;
    }

    /**
     * Legacy.
     */
    public final boolean isRequiredByGroups(String var1, String var2) {
        return isRequiredByGroups(var1, var2);

//        Set<OrderedPair<Set<MyNode>>> s = new HashSet<>();
//
//        for (KnowledgeGroup group : knowledgeGroups) {
//            if (group.getType() == KnowledgeGroup.REQUIRED) {
//                OrderedPair<Set<MyNode>> o = getGroupRule(group);
//                s.add(o);
//            }
//        }
//
//        for (OrderedPair<Set<MyNode>> rule : s) {
//            if (rule.getFirst().contains(getVar(var1))) {
//                if (rule.getSecond().contains(getVar(var2))) {
//                    return true;
//                }
//            }
//        }
//
//        return false;
    }

    /**
     * Legacy.
     */
    public final boolean isForbiddenByGroups(String var1, String var2) {
        return isForbiddenByGroups(var1, var2);

//        Set<OrderedPair<Set<MyNode>>> s = new HashSet<>();
//
//        for (KnowledgeGroup group : knowledgeGroups) {
//            if (group.getType() == KnowledgeGroup.FORBIDDEN) {
//                OrderedPair<Set<MyNode>> o = getGroupRule(group);
//                s.add(o);
//            }
//        }
//
//        for (OrderedPair<Set<MyNode>> rule : s) {
//            if (rule.getFirst().contains(namesToVars.get(var1))) {
//                if (rule.getSecond().contains(namesToVars.get(var2))) {
//                    return true;
//                }
//            }
//        }
//
//        return false;
    }

    /**
     * @return true iff no edge between x and y is required.
     */
    public final boolean noEdgeRequired(String x, String y) {
        return givenKnowledge.noEdgeRequired(x, y);

//        return !(isRequired(x, y) || isRequired(y, x));
    }

    /**
     * Determines whether the edge var1 --> var2 is forbidden by the temporal
     * tiers.
     */
    public final boolean isForbiddenByTiers(String var1, String var2) {
        return givenKnowledge.isForbiddenByTiers(var1, var2);

//        for (OrderedPair<Set<MyNode>> rule : forbiddenTierRules()) {
//            if (rule.getFirst().contains(getVar(var1))) {
//                if (rule.getSecond().contains(getVar(var2))) {
//                    return true;
//                }
//            }
//        }
//
//        return false;
    }

    /**
     * @return true if there is no background knowledge recorded.
     */
    public final boolean isEmpty() {
        return false;

//        return forbiddenRulesSpecs.isEmpty() && requiredRulesSpecs.isEmpty() && tierSpecs.isEmpty();
    }

//    public void saveKnowledge(Writer out)
//            throws IOException {
//        StringBuilder buf = new StringBuilder();
//        buf.append("/knowledge");
//
//        buf.append("\naddtemporal\n");
//
//        for (int i = 0; i < tierSpecs.size(); i++) {
//            String forbiddenWithin = isTierForbiddenWithin(i) ? "*" : "";
//
//            buf.append("\n").append(i).append(forbiddenWithin).append(" ");
//
//            List<String> tier = getTier(i);
////            Collections.sort(tier); // Do not sort!
//
//            for (Object aTier : tier) {
//                String name = (String) aTier;
//                buf.append(name).append(" ");
//            }
//        }
//
//        buf.append("\n");
//
//        buf.append("\nforbiddirect\n\n");
//
//        Set<OrderedPair<Set<MyNode>>> copy = new HashSet<>(forbiddenRulesSpecs);
//        copy.removeAll(forbiddenTierRules());
//
//        for (OrderedPair<Set<MyNode>> o : copy) {
//            Set<MyNode> first = o.getFirst();
//            Set<MyNode> second = o.getSecond();
//
//            for (MyNode s : first) {
//                buf.append(s).append(" ");
//            }
//
//            buf.append("==> ");
//
//            for (MyNode s : second) {
//                buf.append(s).append(" ");
//            }
//
//            buf.append("\n");
//        }
//
//        buf.append("requiredirect\n\n");
//
//        for (OrderedPair<Set<MyNode>> o : requiredRulesSpecs) {
//            Set<MyNode> first = o.getFirst();
//            Set<MyNode> second = o.getSecond();
//
//            for (MyNode s : first) {
//                buf.append(s).append(" ");
//            }
//
//            buf.append("==> ");
//
//            for (MyNode s : second) {
//                buf.append(s).append(" ");
//            }
//
//            buf.append("\n");
//        }
//
//        out.write(buf.toString());
//        out.flush();
//    }

//    private void saveKnowledge(Writer out)
//            throws IOException {
//        StringBuilder buf = new StringBuilder();
//        buf.append("/knowledge");
//
//        buf.append("\naddtemporal\n");
//
//        for (int i = 0; i < tierSpecs.size(); i++) {
//            String forbiddenWithin = isTierForbiddenWithin(i) ? "*" : "";
//
//            buf.append("\n").append(i).append(forbiddenWithin).append(" ");
//
//            List<String> tier = getTier(i);
//
//            for (Object aTier : tier) {
//                String name = (String) aTier;
//                buf.append(name).append(" ");
//            }
//        }
//
//        buf.append("\n");
//
//        buf.append("\nforbiddirect\n\n");
//
//        Set<OrderedPair<Set<MyNode>>> copy = new HashSet<>(forbiddenRulesSpecs);
//        copy.removeAll(forbiddenTierRules());
//
//        for (OrderedPair<Set<MyNode>> o : copy) {
//            Set<MyNode> first = o.getFirst();
//            Set<MyNode> second = o.getSecond();
//
//            for (MyNode s : first) {
//                buf.append(s).append(" ");
//            }
//
//            buf.append("==> ");
//
//            for (MyNode s : second) {
//                buf.append(s).append(" ");
//            }
//
//            buf.append("\n");
//        }
//
//        buf.append("requiredirect\n\n");
//
//        for (OrderedPair<Set<MyNode>> o : requiredRulesSpecs) {
//            Set<MyNode> first = o.getFirst();
//            Set<MyNode> second = o.getSecond();
//
//            for (MyNode s : first) {
//                buf.append(s).append(" ");
//            }
//
//            buf.append("==> ");
//
//            for (MyNode s : second) {
//                buf.append(s).append(" ");
//            }
//
//            buf.append("\n");
//        }
//
//        out.write(buf.toString());
//        out.flush();
//    }
    /**
     * Iterator over the KnowledgeEdge's representing required edges.
     */
    public final Iterator<KnowledgeEdge> requiredEdgesIterator() {
        return givenKnowledge.requiredEdgesIterator();

//        Set<KnowledgeEdge> edges = new HashSet<>();
//
//        for (OrderedPair<Set<MyNode>> o : requiredRulesSpecs) {
//            final Set<MyNode> first = o.getFirst();
//            for (MyNode s1 : first) {
//                final Set<MyNode> second = o.getSecond();
//                for (MyNode s2 : second) {
//                    if (!s1.equals(s2)) {
//                        edges.add(new KnowledgeEdge(s1.getName(), s2.getName()));
//                    }
//                }
//            }
//        }
//
//        return edges.iterator();
    }

    /**
     * Iterator over the KnowledgeEdge's explicitly required edges.
     */
    public final Iterator<KnowledgeEdge> explicitlyRequiredEdgesIterator() {
        return givenKnowledge.explicitlyForbiddenEdgesIterator();


//        return requiredEdgesIterator();
    }

    /**
     * Marks the edge var1 --> var2 as forbid.
     *
     * @param spec1
     * @param spec2
     */
    @Override
    public final void setForbidden(String spec1, String spec2) {
        givenKnowledge.setForbidden(spec1, spec2);

//        addVariable(spec1);
//        addVariable(spec2);
//
//        spec1 = checkSpec(spec1);
//        spec2 = checkSpec(spec2);
//
//        Set<MyNode> f1 = getExtent(spec1);
//        Set<MyNode> f2 = getExtent(spec2);
//
//        OrderedPair<Set<MyNode>> o = new OrderedPair<>(f1, f2);
//
//        if (!forbiddenRulesSpecs.contains(o)) {
//            forbiddenRulesSpecs.add(o);
//        }
    }

    /**
     * Marks the edge var1 --> var2 as not forbid.
     */
    @Override
    public final void removeForbidden(String spec1, String spec2) {
        givenKnowledge.removeForbidden(spec1, spec2);

//        spec1 = checkSpec(spec1);
//        spec2 = checkSpec(spec2);
//
//        Set<MyNode> f1 = getExtent(spec1);
//        Set<MyNode> f2 = getExtent(spec2);
//
//        OrderedPair<Set<MyNode>> o = new OrderedPair<>(f1, f2);
//
//        forbiddenRulesSpecs.remove(o);
    }

    /**
     * Marks the edge var1 --> var2 as required.
     *
     * @param spec1
     * @param spec2
     */
    @Override
    public final void setRequired(String spec1, String spec2) {
        givenKnowledge.setRequired(spec1, spec2);

//        addVariable(spec1);
//        addVariable(spec2);
//
//        spec1 = checkSpec(spec1);
//        spec2 = checkSpec(spec2);
//
//        Set<MyNode> f1 = getExtent(spec1);
//        Set<MyNode> f2 = getExtent(spec2);
//
//        for (MyNode s : f1) {
//            if (checkVarName(s.getName())) {
//                addVariable(s.getName());
//            }
//        }
//
//        for (MyNode s : f2) {
//            if (checkVarName(s.getName())) {
//                addVariable(s.getName());
//            }
//        }
//
//        OrderedPair<Set<MyNode>> o = new OrderedPair<>(f1, f2);
//
//        if (!requiredRulesSpecs.contains(o)) { // Added this line.
//            requiredRulesSpecs.add(o);
//        }  // Add this line.
    }

    /**
     * Marks the edge var1 --> var2 as not required.
     */
    public final void removeRequired(String spec1, String spec2) {
        givenKnowledge.removeRequired(spec1, spec2);

//        spec1 = checkSpec(spec1);
//        spec2 = checkSpec(spec2);
//
//        Set<MyNode> f1 = getExtent(spec1);
//        Set<MyNode> f2 = getExtent(spec2);
//
//        OrderedPair<Set<MyNode>> o = new OrderedPair<>(f1, f2);
//
//        requiredRulesSpecs.remove(o);
    }

    /**
     * Removes the given variable from all tiers.
     */
    public final void removeFromTiers(String spec) {
        givenKnowledge.removeFromTiers(spec);


//        for (Set<MyNode> tier : tierSpecs) {
//            tier.remove(getVar(spec));
//        }
    }

    /**
     * Forbids any variable from being parent of any other variable within the
     * given tier, or cancels this forbidding.
     */
    public final void setTierForbiddenWithin(int tier, boolean forbidden) {
        givenKnowledge.setTierForbiddenWithin(tier, forbidden);

//        ensureTiers(tier);
//        Set<MyNode> _tier = tierSpecs.get(tier);
//        OrderedPair<Set<MyNode>> o = new OrderedPair<>(_tier, _tier);
//
//        if (forbidden) {
//            forbiddenRulesSpecs.add(o);
//        } else {
//            forbiddenRulesSpecs.remove(o);
//        }
    }

    /**
     * Checks whether it is the case that any variable is forbidden by any other
     * variable within a given tier.
     */
    public final boolean isTierForbiddenWithin(int tier) {
        return givenKnowledge.isTierForbiddenWithin(tier);

//        ensureTiers(tier);
//
//        if (tierSpecs.get(tier).isEmpty()) {
//            return false;
//        }
//
//        Set<MyNode> _tier = tierSpecs.get(tier);
//        OrderedPair<Set<MyNode>> o = new OrderedPair<>(_tier, _tier);
//
//        return forbiddenRulesSpecs.contains(o);

    }

//    private void ensureTiers(int tier) {
//        for (int i = tierSpecs.size(); i <= tier; i++) {
//            tierSpecs.add(new LinkedHashSet<MyNode>());
//
//            for (int j = 0; j < i; j++) {
//                forbiddenRulesSpecs.add(new OrderedPair<>(tierSpecs.get(i), tierSpecs.get(j)));
//            }
//        }
//    }

    /**
     * @return the largest indes of a tier in which every variable is forbidden
     * by every other variable, or -1 if there is not such tier.
     */
    public final int getMaxTierForbiddenWithin() {
        return givenKnowledge.getMaxTierForbiddenWithin();

//        for (int tier = tierSpecs.size(); tier >= 0; tier--) {
//            if (isTierForbiddenWithin(tier)) {
//                return tier;
//            }
//        }
//
//        return -1;
    }

    public final void setDefaultToKnowledgeLayout(
            boolean defaultToKnowledgeLayout) {
        givenKnowledge.setDefaultToKnowledgeLayout(defaultToKnowledgeLayout);


//        this.defaultToKnowledgeLayout = defaultToKnowledgeLayout;
    }

    public final boolean isDefaultToKnowledgeLayout() {
        return givenKnowledge.isDefaultToKnowledgeLayout();

//        return defaultToKnowledgeLayout;
    }

    /**
     * Removes explicit knowledge and tier information.
     */
    public final void clear() {
        givenKnowledge.clear();

//        this.forbiddenRulesSpecs = new ArrayList<>();
//        this.requiredRulesSpecs = new ArrayList<>();
//        this.tierSpecs = new ArrayList<>();
    }

    /**
     * Computes a hashcode.
     */
    public final int hashCode() {
        return givenKnowledge.hashCode();

//        int hash = 37;
//        hash += 17 * this.myNodes.hashCode() + 37;
//        hash += 17 * this.forbiddenRulesSpecs.hashCode() + 37;
//        hash += 17 * this.requiredRulesSpecs.hashCode() + 37;
//        hash += 17 * this.tierSpecs.hashCode() + 37;
//        return hash;
    }

    /**
     * Two Knowledge objects are equal just in case their forbidden and required
     * edges are equal, and their tiers are equal.
     */
    public final boolean equals(Object o) {
        return givenKnowledge.equals(o);

//        if (!(o instanceof KnowledgeFask)) {
//            return false;
//        }
//        KnowledgeFask that = (KnowledgeFask) o;
//
//        return this.forbiddenRulesSpecs.equals(that.forbiddenRulesSpecs)
//                && this.requiredRulesSpecs.equals(that.requiredRulesSpecs)
//                && this.tierSpecs.equals(that.tierSpecs);
    }

    /**
     * @return the contents of this Knowledge object in String form.
     */
    public final String toString() {
        return givenKnowledge.toString();

//        try {
//            CharArrayWriter out = new CharArrayWriter();
//            saveKnowledge(out);
//            return out.toString();
//        } catch (IOException e) {
//            throw new IllegalStateException("Could not render knowledge.");
//        }
    }

    /**
     * Does a subclass specific copy. For this class does a deep copy.
     */
    @Override
    public IKnowledge copy() {
        return givenKnowledge.copy();

//        return new KnowledgeFask(this.dataSet, givenKnowledge);
    }

    public boolean isViolatedBy(Graph graph) {
        return givenKnowledge.isViolatedBy(graph);

//        if (graph == null) {
//            throw new NullPointerException("Sorry, a graph hasn't been provided.");
//        }
//
//        for (Edge edge : graph.getEdges()) {
//            if (!edge.isDirected()) {
//                continue;
//            }
//
//            Node from = Edges.getDirectedEdgeTail(edge);
//            Node to = Edges.getDirectedEdgeHead(edge);
//
//            if (isForbidden(from.getName(), to.getName())) {
//                return true;
//            }
//        }
//
//        return false;
    }

    /**
     * Sets the variable in a given tier to the specified list.
     */
    public void setTier(int tier, List<String> vars) {
        givenKnowledge.setTier(tier, vars);


//        ensureTiers(tier);
//        Set<MyNode> _tier = tierSpecs.get(tier);
//        if (_tier != null) {
//            _tier.clear();
//        }
//
//        for (String var : vars) {
//            addToTier(tier, var);
//        }
    }

    /**
     * Adds the given variable name to knowledge. Duplicates are ignored.
     */
    public void addVariable(String varName) {
        givenKnowledge.addVariable(varName);

//        if (!namesToVars.containsKey(varName) && checkVarName(varName)) {
//            MyNode e = new MyNode(varName);
//            myNodes.add(e);
//            namesToVars.put(varName, e);
//        }
    }

    /**
     * Removes the given variable from the list of myNodes and all rules.
     */
    public void removeVariable(String name) {
        givenKnowledge.removeVariable(name);


//        if (!checkVarName(name)) {
//            throw new IllegalArgumentException("Bad variable name: " + name);
//        }
//
//        MyNode MyNode = getVar(name);
//
//        myNodes.remove(MyNode);
//
//        for (OrderedPair<Set<MyNode>> o : forbiddenRulesSpecs) {
//            o.getFirst().remove(MyNode);
//            o.getSecond().remove(MyNode);
//        }
//        for (OrderedPair<Set<MyNode>> o : requiredRulesSpecs) {
//            o.getFirst().remove(MyNode);
//            o.getSecond().remove(MyNode);
//        }
//        for (Set<MyNode> tier : tierSpecs) {
//            tier.remove(MyNode);
//        }
    }

    /**
     * @return a copy of the list of variable, in alphabetical order.
     */
    public List<String> getVariables() {
        return givenKnowledge.getVariables();


//        List<String> names = new ArrayList<>();
//        for (MyNode MyNode : myNodes) {
//            names.add(MyNode.getName());
//        }
//        Collections.sort(names);
//        return new ArrayList<>(names);
    }

    //=====================================PRIVATE METHODS============================//
    private OrderedPair<Set<MyNode>> getGroupRule(KnowledgeGroup group) {
        Set<String> from = group.getFromVariables();
        Set<String> to = group.getToVariables();
        Set<MyNode> fromExtent = new HashSet<>();
        Set<MyNode> toExtent = new HashSet<>();
        for (String s : from) {
            fromExtent.addAll(getExtent(s));
        }
        for (String s : to) {
            toExtent.addAll(getExtent(s));
        }
        return new OrderedPair<>(fromExtent, toExtent);
    }

    private boolean checkVarName(String name) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[A-Za-z0-9:_\\-\\.]+");
        Matcher matcher = pattern.matcher(name);
        return matcher.matches();
    }

    private String checkSpec(String spec) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[A-Za-z0-9:-_,\\-\\.*]+");
        Matcher matcher = pattern.matcher(spec);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(spec + ": Pattern names can consist of alphabetic "
                    + "characters plus :, _, -, and .. A wildcard '*' may be included to match a "
                    + "string of such characters.");
        }

        spec = spec.replace(".", "\\.");

        return spec;
    }

    private Set<MyNode> getExtent(String spec) {
        Set<String> split = split(spec);
        Set<MyNode> matches = new HashSet<>();

        for (String _spec : split) {
            _spec = _spec.replace("*", ".*");

            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(_spec);

            for (MyNode var : myNodes) {
                Matcher matcher = pattern.matcher(var.getName());
                if (matcher.matches()) {
                    matches.add(var);
                }
            }
        }

        return matches;
    }

    private Set<String> split(String spec) {
        String[] tokens = spec.split(",");

        Set<String> _tokens = new HashSet<>();

        for (String _token : tokens) {
            if (!_token.trim().equals("")) {
                _tokens.add(_token);
            }
        }

        return _tokens;
    }

//    private Set<OrderedPair<Set<MyNode>>> forbiddenTierRules() {
//        Set<OrderedPair<Set<MyNode>>> rules = new HashSet<>();
//
//        for (int i = 0; i < tierSpecs.size(); i++) {
//            if (isTierForbiddenWithin(i)) {
//                rules.add(new OrderedPair<>(tierSpecs.get(i), tierSpecs.get(i)));
//            }
//        }
//
//        for (int i = 0; i < tierSpecs.size(); i++) {
//            for (int j = i + 1; j < tierSpecs.size(); j++) {
//                rules.add(new OrderedPair<>(tierSpecs.get(j), tierSpecs.get(i)));
//            }
//        }
//
//        return rules;
//    }

    /**
     * Returns the index of the tier of node if it's in a tier, otherwise -1.
     */
    //@Override
    public int isInWhichTier(Node node) {
        return givenKnowledge.isInWhichTier(node);

//        for (int i = 0; i < tierSpecs.size(); i++) {
//            Set<MyNode> tier = tierSpecs.get(i);
//
//            for (MyNode myNode : tier) {
//                if (myNode.getName().equals(node.getName())) {
//                    return i;
//                }
//            }
//        }
//
//        return -1;
    } // added by DMalinsky for tsFCI on 4/20/16

}
