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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.GraphGeneratorRandomNumEdges;
import edu.cmu.tetrad.graph.UniformGraphGenerator;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Joseph Ramsey
 */
public final class TestUniformGraphGenerator {

    @Test
    public void testRandomDag1() {
        RandomUtil.getInstance().setSeed(3848283L);

        UniformGraphGenerator generator =
                new UniformGraphGenerator(UniformGraphGenerator.ANY_DAG);
        generator.setNumNodes(20);
        generator.setMaxDegree(3);
        generator.setNumIterations(50000);
        generator.generate();

        assertEquals(19, generator.getDag().getNumEdges());
    }

    @Test
    public void testRandomDag2() {
        RandomUtil.getInstance().setSeed(3848283L);

        UniformGraphGenerator generator =
                new UniformGraphGenerator(UniformGraphGenerator.CONNECTED_DAG);
        generator.setNumNodes(50);
        generator.setMaxDegree(4);
        generator.setMaxEdges(20);
        generator.generate();

        assertEquals(49, generator.getDag().getNumEdges());
    }

    @Test
    public void testRandomDag3() {
        RandomUtil.getInstance().setSeed(3848283L);

        UniformGraphGenerator generator =
                new UniformGraphGenerator(UniformGraphGenerator.CONNECTED_DAG);
        generator.setNumNodes(20);
        generator.setMaxDegree(3);
        generator.setNumIterations(20000);
        generator.generate();

        assertEquals(19, generator.getDag().getNumEdges());
    }

    @Test
    public void testRandomDag4() {
        RandomUtil.getInstance().setSeed(3848283L);

        UniformGraphGenerator generator =
                new UniformGraphGenerator(UniformGraphGenerator.CONNECTED_DAG);
        generator.setNumNodes(20);
        generator.setMaxInDegree(1);
        generator.setMaxDegree(3);
        generator.generate();

        assertEquals(19, generator.getDag().getNumEdges());
    }

    @Test
    public void testRandomDag5() {
        RandomUtil.getInstance().setSeed(3848283L);

        UniformGraphGenerator generator =
                new UniformGraphGenerator(UniformGraphGenerator.CONNECTED_DAG);
        generator.setNumNodes(20);
        generator.setMaxDegree(3);
        generator.generate();

        assertEquals(19, generator.getDag().getNumEdges());
    }

    @Test
    public void testRandomDag6() {
        RandomUtil.getInstance().setSeed(3848283L);

        UniformGraphGenerator generator =
                new UniformGraphGenerator(UniformGraphGenerator.ANY_DAG);
        generator.setNumNodes(40);
        generator.setMaxDegree(39);
        generator.setMaxInDegree(2);

        generator.setMaxEdges(10);
        generator.generate();

        assertEquals(10, generator.getDag().getNumEdges());
    }

    @Test
    public void testRandomDag7() {
        RandomUtil.getInstance().setSeed(3848283L);

        UniformGraphGenerator generator =
                new UniformGraphGenerator(UniformGraphGenerator.CONNECTED_DAG);
        generator.setNumNodes(20);
        generator.setMaxInDegree(2);
        generator.setMaxOutDegree(3);
        generator.generate();

        assertEquals(19, generator.getDag().getNumEdges());
    }

    @Test
    public void testRandomDag8() {
        RandomUtil.getInstance().setSeed(3848283L);

        UniformGraphGenerator generator =
                new UniformGraphGenerator(UniformGraphGenerator.CONNECTED_DAG);
        generator.setNumNodes(15);
        generator.setMaxDegree(14);
        generator.generate();

        assertEquals(14, generator.getDag().getNumEdges());
    }

    /**
     * Tests the second version of the generator that generates random DAGs
     * (unconnected only) with #edges in a given range.
     */
    @Test
    public void testRandomDag9() {
        int N = 25;
        int E = N * (N - 1) / 2;

        GraphGeneratorRandomNumEdges generator =
                new GraphGeneratorRandomNumEdges(UniformGraphGenerator.ANY_DAG);
        generator.setNumNodes(N);
        generator.setMaxEdges(E);
        generator.setMinEdges(E - 5);
        generator.generate();

        assertEquals(295, generator.getDag().getNumEdges());
    }
}





