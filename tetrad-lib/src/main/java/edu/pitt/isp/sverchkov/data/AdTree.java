///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

package edu.pitt.isp.sverchkov.data;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.Serializable;
import java.util.*;

/**
 * An implementation of a static AD tree based on Moore and Lee 1998 (mostly)
 * <p>
 * Indexing of the variables works backwards: (a_i can't have children a_i ... a_m) ... not that that difference is
 * visible in the API.
 *
 * @param <A> Attribute type
 * @param <V> Value type
 * @author jdramsey
 */
public class AdTree<A, V> extends AdTreeHelper {

    private final Map<A, Integer> attributeLookup;
    private final List<A> attributes;
    private final List<VHelper> values;
    private final CountNode root;

    /**
     * Constructs an AD tree for the given data set.
     *
     * @param data The data set.
     */
    public AdTree(DataTable<A, V> data) {
        super(data.columnCount());

        {
            Map<A, Integer> attrLookup = new HashMap<>();
            List<A> attrs = new ArrayList<>(data.variables());

            int i = 0;
            for (A attribute : attrs)
                attrLookup.put(attribute, i++);

            // Set immutable
            this.attributeLookup = Collections.unmodifiableMap(attrLookup);
            this.attributes = Collections.unmodifiableList(attrs);
        }

        int[][] array = new int[data.rowCount()][this.m];

        {
            List<VHelper> v = new ArrayList<>(this.m);

            for (int i = 0; i < this.m; i++)
                v.add(new VHelper());

            int r = 0;
            for (List<V> row : data) {
                for (int i = 0; i < this.m; i++) {
                    V value = row.get(i);
                    List<V> vlist = v.get(i).list;
                    Map<V, Integer> vmap = v.get(i).map;
                    if (!vlist.contains(value)) {
                        vmap.put(value, vlist.size());
                        vlist.add(value);
                        ++this.airities[i];
                    }
                    array[r][i] = vmap.get(value);
                }
                ++r;
            }

            // Set immutable
            for (ListIterator<VHelper> iter = v.listIterator(); iter.hasNext(); ) {
                VHelper h = iter.next();
                iter.set(new VHelper(
                        Collections.unmodifiableList(h.list),
                        Collections.unmodifiableMap(h.map)));
            }

            this.values = Collections.unmodifiableList(v);
        }

        // Build A-D tree
        this.root = new CountNode(this.m, array);
    }

    /**
     * Returns the number of rows in the data set.
     *
     * @param attribute The attribute to count.
     * @return The number of rows in the data set.
     */
    public List<V> values(A attribute) {
        int index = Objects.requireNonNull(this.attributeLookup.get(attribute),
                "Attribute " + attribute.toString() + " not found.");
        return this.values.get(index).list;
    }

    /**
     * Returns the number of rows in the data set.
     *
     * @param assignment The assignment to count.
     * @return The number of rows in the data set.
     */
    public int count(Map<A, V> assignment) {
        int[] a = new int[this.m];
        for (int i = 0; i < this.m; i++) {
            V value = assignment.get(this.attributes.get(i));
            if (null != value)
                a[i] = this.values.get(i).map.get(value);
            else a[i] = -1;
        }
        return count(a, this.root);
    }

    /**
     * Returns the number of rows in the data set.
     *
     * @param attribute  The attribute to count.
     * @param assignment The assignment to count.
     * @return The number of rows in the data set.
     */
    public Map<V, Integer> counts(A attribute, Map<A, V> assignment) {

        List<V> vlist = this.values.get(this.attributeLookup.get(attribute)).list;
        Map<V, Integer> result = new HashMap<>(vlist.size());
        for (V value : vlist) {
            Map<A, V> a = new HashMap<>(assignment);
            a.put(attribute, value);
            result.put(value, count(a));
        }

        return result;
    }

    /**
     * Converts to XML.
     *
     * @return The XML document.
     * @throws ParserConfigurationException if something goes wrong
     */
    public Document toXML() throws ParserConfigurationException {
        return toXML(DocumentBuilderFactory.newInstance().newDocumentBuilder());
    }

    /**
     * Converts to XML.
     *
     * @param builder The builder.
     * @return The XML document.
     */
    public Document toXML(DocumentBuilder builder) {
        Document doc = builder.newDocument();

        Element docRoot = doc.createElement("adtree");
        doc.appendChild(docRoot);

        if (null != this.root) {
            Element cNode = doc.createElement("count");
            recursiveXML(doc, cNode, this.root);
            docRoot.appendChild(cNode);
        }

        return doc;
    }

    private void recursiveXML(Document doc, Element cNode, CountNode node) {
        cNode.setAttribute("count", Integer.toString(node.count));
        for (int i = 0; i < node.vary.length; i++) {
            Element vNode = doc.createElement("vary");
            vNode.setAttribute("attribute", this.attributes.get(i).toString());
            cNode.appendChild(vNode);
            for (int j = 0; j < node.vary[i].values.length; j++) {
                Element e;
                if (j == node.vary[i].mcv)
                    e = doc.createElement("mcv");
                else if (null == node.vary[i].values[j])
                    e = doc.createElement("null");
                else {
                    e = doc.createElement("count");
                    recursiveXML(doc, e, node.vary[i].values[j]);
                }
                e.setAttribute("value", this.values.get(i).list.get(j).toString());
                vNode.appendChild(e);
            }
        }
    }

    private class VHelper implements Serializable {
        private final List<V> list;
        private final Map<V, Integer> map;

        private VHelper() {
            this.list = new ArrayList<>();
            this.map = new HashMap<>();
        }

        private VHelper(List<V> list, Map<V, Integer> map) {
            this.list = list;
            this.map = map;
        }
    }
}

