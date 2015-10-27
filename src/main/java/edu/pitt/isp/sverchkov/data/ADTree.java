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

package edu.pitt.isp.sverchkov.data;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.Serializable;
import java.util.*;

/**
 * An implementation of a static AD tree based on Moore & Lee 1998 (mostly)
 *
 * Indexing of the variables works backwards:
 * (a_i can't have children a_i ... a_m)
 * ... not that that difference is visible in the API.
 * @author user
 */
public class ADTree<A,V> extends ADTreeHelper implements Serializable {

    private final Map<A,Integer> attributeLookup;
    private final List<A> attributes;
    private final List<VHelper> values;
    private final CountNode root;

    public ADTree( DataTable<A,V> data ){
        super( data.columnCount() );

        {
            final Map<A, Integer> attrLookup = new HashMap<>();
            final List<A> attrs = new ArrayList<>( data.variables() );

            int i=0;
            for( A attribute : attrs )
                attrLookup.put( attribute, i++ );

            // Set immutable
            attributeLookup = Collections.unmodifiableMap( attrLookup );
            attributes = Collections.unmodifiableList( attrs );
        }

        int[][] array = new int[data.rowCount()][m];

        {
            final List<VHelper> v = new ArrayList<>( m );

            for( int i=0; i<m; i++ )
                v.add( new VHelper() );

            int r = 0;
            for( List<V> row : data ){
                for( int i=0; i<m; i++ ){
                    V value = row.get(i);
                    List<V> vlist = v.get(i).list;
                    Map<V,Integer> vmap = v.get(i).map;
                    if( ! vlist.contains(value) ){
                        vmap.put( value, vlist.size() );
                        vlist.add(value);
                        ++airities[i];
                    }
                    array[r][i] = vmap.get( value );
                }
                ++r;
            }

            // Set immutable
            for( ListIterator<VHelper> iter = v.listIterator(); iter.hasNext(); ){
                final VHelper h = iter.next();
                iter.set( new VHelper(
                        Collections.unmodifiableList( h.list ),
                        Collections.unmodifiableMap( h.map ) ) );
            }

            values = Collections.unmodifiableList( v );
        }

        // Build A-D tree
        root = new CountNode( m, array );
    }

    public List<V> values( A attribute ){
        int index = Objects.requireNonNull( attributeLookup.get( attribute ),
                "Attribute "+attribute.toString()+" not found.");
        return values.get( index ).list;
    }

    public int count( Map<A,V> assignment ){
        final int[] a = new int[m];
        for( int i=0; i<m; i++ ){
            V value = assignment.get( attributes.get(i) );
            if( null != value )
                a[i] = values.get(i).map.get( value );
            else a[i] = -1;
        }
        return count( a, root );
    }

    public Map<V, Integer> counts( A attribute, Map<A,V> assignment ){

        final List<V> vlist = values.get( attributeLookup.get(attribute) ).list;
        final Map<V,Integer> result = new HashMap<>( vlist.size() );
        for( V value : vlist ){
            Map<A,V> a = new HashMap<>( assignment );
            a.put( attribute, value );
            result.put( value, count( a ) );
        }

        return result;
    }

    public Document toXML() throws ParserConfigurationException{
        return toXML( DocumentBuilderFactory.newInstance().newDocumentBuilder() );
    }

    public Document toXML( final DocumentBuilder builder ){
        final Document doc = builder.newDocument();

        final Element docRoot = doc.createElement("adtree");
        doc.appendChild( docRoot );

        if( null != root ){
            final Element cNode = doc.createElement("count");
            recursiveXML( doc, cNode, root );
            docRoot.appendChild( cNode );
        }

        return doc;
    }

    private void recursiveXML(final Document doc, final Element cNode, final CountNode node) {
        cNode.setAttribute("count", Integer.toString( node.count ) );
        for( int i=0; i<node.vary.length; i++ ){
            final Element vNode = doc.createElement("vary");
            vNode.setAttribute("attribute", attributes.get(i).toString() );
            cNode.appendChild(vNode);
            for( int j=0; j<node.vary[i].values.length; j++ ){
                final Element e;
                if( j == node.vary[i].mcv )
                    e = doc.createElement("mcv");
                else if( null == node.vary[i].values[j] )
                    e = doc.createElement("null");
                else{
                    e = doc.createElement("count");
                    recursiveXML( doc, e, node.vary[i].values[j] );
                }
                e.setAttribute("value", values.get(i).list.get(j).toString() );
                vNode.appendChild(e);
            }
        }
    }

    private class VHelper implements Serializable {
        private final List<V> list;
        private final Map<V,Integer> map;
        private VHelper(){
            list = new ArrayList<>();
            map = new HashMap<>();
        }
        private VHelper( List<V> list, Map<V,Integer> map ){
            this.list = list;
            this.map = map;
        }
    }
}

