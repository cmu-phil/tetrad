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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Structural invariants for the parameter documentation resource, docs/manual/parameter.definitions.html.
 * <p>
 * ParamDescriptions reads this file with Jsoup and resolves every field by element id. That makes the parse
 * order-independent and, unfortunately, silent about several ways the file can be wrong. In particular,
 * Jsoup's getElementById returns the first match in document order, so if two parameter headings share an id,
 * both passes of the ParamDescriptions loop read the first block's spans and the second block's content is
 * never read at all -- it becomes inert text with no warning logged. Similarly, a block of spans that has lost
 * its heading is skipped entirely, since ParamDescriptions enumerates headings rather than spans.
 * <p>
 * These tests make those failure modes loud. They are pure resource checks: they do not construct a
 * ParamDescriptions instance, so a malformed file fails here with a precise message rather than throwing from
 * static initialization somewhere far away.
 *
 * @author josephramsey
 */
public class ParameterDefinitionsTest {

    /**
     * The resource path used by ParamDescriptions.
     */
    private static final String RESOURCE = "docs/manual/parameter.definitions.html";

    /**
     * The value types ParamDescriptions is able to interpret.
     */
    private static final Set<String> LEGAL_VALUE_TYPES =
            new HashSet<>(Arrays.asList("String", "Integer", "Double", "Long", "Boolean"));

    /**
     * The per-parameter fields ParamDescriptions requires, as span id suffixes.
     */
    private static final List<String> REQUIRED_SUFFIXES =
            Arrays.asList("_short_desc", "_long_desc", "_default_value", "_lower_bound", "_upper_bound", "_value_type");

    /**
     * Matches the id of any span that looks like a parameter field, so orphaned blocks can be detected.
     */
    private static final Pattern FIELD_SPAN_ID = Pattern.compile(
            "^(.*)(_short_desc|_long_desc|_default_value|_lower_bound|_upper_bound|_value_type)$");

    /**
     * Ids sorted the way the file is maintained: case-insensitively, with a case-sensitive tiebreak so the
     * order is total.
     */
    private static final Comparator<String> ID_ORDER =
            Comparator.comparing((String s) -> s.toLowerCase()).thenComparing(Comparator.naturalOrder());

    /**
     * Represents a test class for parameter definitions.
     */
    public ParameterDefinitionsTest() {
    }

    /**
     * Parses the resource exactly as ParamDescriptions does.
     */
    private static Document doc() throws IOException {
        try (InputStream in = ParameterDefinitionsTest.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull("Could not find " + RESOURCE + " on the classpath.", in);
            return Jsoup.parse(in, "UTF-8", "");
        }
    }

    /**
     * The parameter headings, in document order.
     */
    private static Elements headings(Document doc) {
        Elements elements = doc.getElementsByClass("parameter_description");
        assertTrue("No parameter_description elements found in " + RESOURCE + "; the file is empty or malformed.",
                !elements.isEmpty());
        return elements;
    }

    /**
     * No two parameter headings may share an id.
     * <p>
     * A duplicate does not throw; it silently discards the second block, because every field lookup in
     * ParamDescriptions resolves to the first block's spans. Since the file is maintained in alphabetical order
     * by id, duplicates land next to each other and are easy to reconcile once reported.
     */
    @Test
    public void testNoDuplicateParameterIds() throws IOException {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicates = new TreeSet<>();

        for (Element heading : headings(doc())) {
            if (!seen.add(heading.id())) {
                duplicates.add(heading.id());
            }
        }

        assertEquals("Duplicate parameter ids in " + RESOURCE + ". Each duplicate silently shadows the later "
                     + "block, whose text is never read. Offending ids: " + duplicates,
                new TreeSet<String>(), duplicates);
    }

    /**
     * Every parameter heading must carry a non-empty id.
     */
    @Test
    public void testEveryHeadingHasAnId() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Element heading : headings(doc())) {
            if (heading.id().trim().isEmpty()) {
                offenders.add(heading.text().trim());
            }
        }

        assertEquals("Parameter headings with no id in " + RESOURCE + "; ParamDescriptions would register these "
                     + "under the empty string. Heading text: " + offenders,
                new ArrayList<String>(), offenders);
    }

    /**
     * A heading's visible label must equal its own id.
     * <p>
     * The label is cosmetic as far as ParamDescriptions is concerned, which is precisely why it drifts. A block
     * whose label disagrees with its id reads as documentation for a parameter that is not the one being
     * documented.
     */
    @Test
    public void testHeadingLabelMatchesId() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Element heading : headings(doc())) {
            String label = heading.text().trim();

            if (!heading.id().equals(label)) {
                offenders.add("id=" + heading.id() + " label=" + label);
            }
        }

        assertEquals("Parameter headings whose visible label disagrees with their id in " + RESOURCE
                     + ". The id is what ParamDescriptions registers, so the label is misleading here: " + offenders,
                new ArrayList<String>(), offenders);
    }

    /**
     * Every parameter must supply all six fields ParamDescriptions reads, and the value type must be one it can
     * interpret.
     * <p>
     * A missing span throws a NullPointerException out of ParamDescriptions' static initialization, which
     * surfaces as an opaque startup failure. An unrecognized value type is quieter still: the parameter is
     * pushed onto the unsupported-type list and silently dropped from the map.
     */
    @Test
    public void testEveryParameterHasCompleteFields() throws IOException {
        Document doc = doc();
        List<String> offenders = new ArrayList<>();

        for (Element heading : headings(doc)) {
            String id = heading.id();

            for (String suffix : REQUIRED_SUFFIXES) {
                if (doc.getElementById(id + suffix) == null) {
                    offenders.add(id + suffix + " (missing)");
                }
            }

            Element valueType = doc.getElementById(id + "_value_type");

            if (valueType != null && !LEGAL_VALUE_TYPES.contains(valueType.text().trim())) {
                offenders.add(id + "_value_type = '" + valueType.text().trim() + "' (not one of " + LEGAL_VALUE_TYPES + ")");
            }
        }

        assertEquals("Incomplete or uninterpretable parameter entries in " + RESOURCE + ": " + offenders,
                new ArrayList<String>(), offenders);
    }

    /**
     * No block of parameter field spans may exist without a heading to introduce it.
     * <p>
     * ParamDescriptions enumerates headings, not spans, so an orphaned block is invisible: the parameter is
     * never registered and the documentation silently has no effect. This is the shape of defect that left a
     * breakTies block stranded in the file for some time.
     */
    @Test
    public void testNoOrphanedFieldSpans() throws IOException {
        Document doc = doc();

        Set<String> documented = new HashSet<>();
        for (Element heading : headings(doc)) {
            documented.add(heading.id());
        }

        Set<String> orphaned = new TreeSet<>();
        for (Element span : doc.select("span[id]")) {
            Matcher matcher = FIELD_SPAN_ID.matcher(span.id());

            if (matcher.matches() && !documented.contains(matcher.group(1))) {
                orphaned.add(matcher.group(1));
            }
        }

        assertEquals("Parameter field spans with no corresponding heading in " + RESOURCE + ". These are never "
                     + "read by ParamDescriptions; either give them a heading or delete them. Prefixes: " + orphaned,
                new TreeSet<String>(), orphaned);
    }

    /**
     * The file is maintained in alphabetical order by parameter id.
     * <p>
     * Order has no effect on ParamDescriptions, which stores results in a TreeMap. It is enforced so the file
     * stays navigable by hand and so duplicate ids become adjacent rather than hiding thousands of lines apart.
     */
    @Test
    public void testParametersAreAlphabetizedById() throws IOException {
        List<String> actual = new ArrayList<>();
        for (Element heading : headings(doc())) {
            actual.add(heading.id());
        }

        List<String> expected = new ArrayList<>(actual);
        expected.sort(ID_ORDER);

        String firstOutOfOrder = null;
        for (int i = 1; i < actual.size(); i++) {
            if (ID_ORDER.compare(actual.get(i - 1), actual.get(i)) > 0) {
                firstOutOfOrder = actual.get(i - 1) + " precedes " + actual.get(i);
                break;
            }
        }

        assertEquals("Parameter entries in " + RESOURCE + " are not in alphabetical order by id. First break: "
                     + firstOutOfOrder + ".", expected, actual);
    }
}
