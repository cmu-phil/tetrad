/*
 * Copyright (C) 2016 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.cli.data.sim;

import org.junit.Test;

/**
 *
 * Mar 29, 2016 1:23:52 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class DiscreteTabularDataTest {

    public DiscreteTabularDataTest() {
    }

    /**
     * Test of main method, of class DiscreteTabularData.
     */
    @Test
    public void testMain() {
        System.out.println("main");

        String[] args = {
            "--variable", "20",
            "--case", "100",
            "--edge", "1.0"
        };
        DiscreteTabularData.main(args);
    }

}
