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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.*;
import org.junit.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests the additional tier structures of Knowledge, the direct (un-mirrored) enforcement of
 * knowledge groups, and the knowledge text format's comma-delimited variant for variable names
 * containing spaces.
 *
 * @author josephramsey
 */
public final class TestKnowledgeTierStructures {

    /**
     * Tier structures are independent orderings: later cannot cause earlier within a structure,
     * optionally nothing causes anything within a tier, and structures place no constraints on one
     * another.
     */
    @Test
    public void testStructureSemantics() {
        Knowledge k = new Knowledge(List.of("a", "b", "c", "d"));

        KnowledgeTierStructure s1 = new KnowledgeTierStructure("S1");
        s1.addToTier(0, "a");
        s1.addToTier(1, "b");

        KnowledgeTierStructure s2 = new KnowledgeTierStructure("S2");
        s2.addToTier(0, "c");
        s2.addToTier(1, "d");
        s2.setTierForbiddenWithin(0, true);

        k.addTierStructure(s1);
        k.addTierStructure(s2);

        assertTrue(k.isForbidden("b", "a"));
        assertTrue(k.isForbidden("d", "c"));
        assertFalse(k.isForbidden("a", "b"));
        assertFalse(k.isForbidden("b", "c"));
        assertFalse(k.isForbidden("c", "b"));
        assertFalse(k.isForbidden("a", "d"));
        assertFalse(k.isEmpty());

        // A variable may be in several structures; an edge is forbidden if any structure forbids
        // it.
        KnowledgeTierStructure s3 = new KnowledgeTierStructure("S3");
        s3.addToTier(0, "d");
        s3.addToTier(1, "a");
        k.addTierStructure(s3);

        assertTrue(k.isForbidden("a", "d"));
        assertTrue(k.isForbidden("d", "c"));

        // Within a structure, a variable is in one tier only.
        s3.addToTier(0, "a");
        assertEquals(0, s3.isInWhichTier("a"));
        assertFalse(k.isForbidden("a", "d"));
    }

    /**
     * Knowledge groups are enforced directly, as groups; in particular, removing a group no longer
     * deletes an identical explicitly set rule, and the required and forbidden edge lists include
     * the groups' edges.
     */
    @Test
    public void testGroupsEnforcedDirectly() {
        Knowledge k = new Knowledge(List.of("p", "q"));
        k.setForbidden("p", "q");
        k.addKnowledgeGroup(new KnowledgeGroup(KnowledgeGroup.FORBIDDEN, Set.of("p"), Set.of("q")));

        assertTrue(k.isForbidden("p", "q"));
        assertTrue(k.isForbiddenByGroups("p", "q"));

        k.removeKnowledgeGroup(0);

        assertFalse(k.isForbiddenByGroups("p", "q"));
        assertTrue("The explicit rule must survive removing the coinciding group",
                k.isForbidden("p", "q"));

        Knowledge k2 = new Knowledge(List.of("p", "q"));
        k2.addKnowledgeGroup(new KnowledgeGroup(KnowledgeGroup.REQUIRED, Set.of("p"), Set.of("q")));

        assertTrue(k2.isRequired("p", "q"));
        assertEquals(1, k2.getListOfRequiredEdges().size());
        assertFalse(k2.isEmpty());

        k2.removeKnowledgeGroup(0);

        assertFalse(k2.isRequired("p", "q"));
        assertTrue(k2.getListOfRequiredEdges().isEmpty());
    }

    /**
     * The whitespace-delimited text format is unchanged for names without spaces, and the '-'
     * (can cause only next tier) flag, which the loader formerly rejected, round-trips.
     */
    @Test
    public void testWhitespaceRoundTrip() throws Exception {
        Knowledge k = new Knowledge(List.of("A", "B", "C", "D"));
        k.addToTier(0, "A");
        k.addToTier(1, "B");
        k.addToTier(2, "C");
        k.addToTier(3, "D");
        k.setTierForbiddenWithin(0, true);
        k.setOnlyCanCauseNextTier(0, true);
        k.setForbidden("D", "A");
        k.setRequired("A", "D");

        StringWriter w = new StringWriter();
        DataWriter.saveKnowledge(k, w);
        String text = w.toString();

        assertTrue(text.startsWith("/knowledge\n"));
        assertFalse(text.contains("comma"));

        Knowledge k2 = SimpleDataLoader.loadKnowledge(new StringReader(text),
                DelimiterType.WHITESPACE, "//");

        assertTrue(k2.isTierForbiddenWithin(0));
        assertTrue(k2.isOnlyCanCauseNextTier(0));
        assertTrue(k2.isForbidden("A", "C"));
        assertTrue(k2.isForbidden("D", "A"));
        assertTrue(k2.isRequired("A", "D"));
    }

    /**
     * Names containing spaces switch the render to the comma-delimited variant, under the header
     * "/knowledge comma", and tiers, tier structures, groups, and explicit edges all round-trip
     * through it. A name containing a comma is quoted.
     */
    @Test
    public void testCommaRoundTrip() throws Exception {
        Knowledge k = new Knowledge(List.of("Exam 1", "Exam 2", "Unit 1 Done", "Unit 2 Done",
                "Other", "Last, First"));
        k.addToTier(0, "Exam 1");
        k.addToTier(1, "Exam 2");
        k.setForbidden("Other", "Exam 1");
        k.setRequired("Exam 1", "Other");
        k.addToTier(1, "Last, First");

        KnowledgeTierStructure units = new KnowledgeTierStructure("Unit Progress");
        units.addToTier(0, "Unit 1 Done");
        units.addToTier(1, "Unit 2 Done");
        units.setTierForbiddenWithin(0, true);
        k.addTierStructure(units);

        k.addKnowledgeGroup(new KnowledgeGroup(KnowledgeGroup.FORBIDDEN,
                Set.of("Unit 1 Done", "Unit 2 Done"), Set.of("Exam 1", "Exam 2")));

        StringWriter w = new StringWriter();
        DataWriter.saveKnowledge(k, w);
        String text = w.toString();

        assertTrue(text.startsWith("/knowledge comma"));
        assertTrue(text.contains("\"Last, First\""));

        Knowledge k2 = SimpleDataLoader.loadKnowledge(new StringReader(text),
                DelimiterType.WHITESPACE, "//");

        assertTrue(k2.getVariables().contains("Exam 1"));
        assertTrue(k2.getVariables().contains("Last, First"));
        assertTrue(k2.isForbidden("Exam 2", "Exam 1"));
        assertTrue(k2.isForbidden("Last, First", "Exam 1"));
        assertTrue(k2.isForbidden("Unit 2 Done", "Unit 1 Done"));
        assertTrue(k2.isForbiddenByGroups("Unit 1 Done", "Exam 1"));
        assertTrue(k2.isRequired("Exam 1", "Other"));
        assertEquals(1, k2.getNumTierStructures());
        assertEquals("Unit Progress", k2.getTierStructures().get(0).getName());
        assertTrue(k2.getTierStructures().get(0).isTierForbiddenWithin(0));

        // The render of the parse parses to the same knowledge.
        StringWriter w2 = new StringWriter();
        DataWriter.saveKnowledge(k2, w2);
        Knowledge k3 = SimpleDataLoader.loadKnowledge(new StringReader(w2.toString()),
                DelimiterType.WHITESPACE, "//");

        assertEquals(k2, k3);
    }

    /**
     * Legacy comma-delimited knowledge sections (as in comma-delimited covariance files) parse by
     * the caller's delimiter, as before, without the header flag.
     */
    @Test
    public void testLegacyCommaDelimiterArgument() throws Exception {
        String text = "/knowledge\naddtemporal\n0,x1,x2\n1,x3\nforbiddirect\nrequiredirect\n";
        Knowledge k = SimpleDataLoader.loadKnowledge(new StringReader(text),
                DelimiterType.COMMA, "//");

        assertTrue(k.isForbidden("x3", "x1"));
        assertTrue(k.isForbidden("x3", "x2"));
    }

    /**
     * Copying carries the groups and the tier structures.
     */
    @Test
    public void testCopy() {
        Knowledge k = new Knowledge(List.of("a", "b", "c"));
        KnowledgeTierStructure s = new KnowledgeTierStructure("S");
        s.addToTier(0, "a");
        s.addToTier(1, "b");
        k.addTierStructure(s);
        k.addKnowledgeGroup(new KnowledgeGroup(KnowledgeGroup.FORBIDDEN, Set.of("c"), Set.of("a")));

        Knowledge copy = k.copy();

        assertEquals(k, copy);
        assertTrue(copy.isForbidden("b", "a"));
        assertTrue(copy.isForbiddenByGroups("c", "a"));
    }
}
