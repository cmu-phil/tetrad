/*
 * Copyright (C) 2015 University of Pittsburgh.
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
package edu.cmu.tetrad.cli.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 *
 * Dec 2, 2015 1:43:02 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class ArgsTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    public ArgsTest() {
    }

    /**
     * Test of parseInteger method, of class Args.
     */
    @Test
    public void testParseInteger_String() {
        System.out.println("parseInteger(String value)");

        String value = "10";
        int expResult = Integer.parseInt(value);
        int result = Args.parseInteger(value);
        Assert.assertEquals(expResult, result);
    }

    /**
     * Test of parseInteger method, of class Args.
     */
    @Test
    public void testParseInteger_String_int() {
        System.out.println("parseInteger(String value, int min)");
        String value = "-1";
        int min = -1;
        int expResult = Integer.parseInt(value);
        int result = Args.parseInteger(value, min);
        Assert.assertEquals(expResult, result);
    }

    /**
     * Test of parseInteger method, of class Args.
     */
    @Test
    public void testParseInteger_3args() {
        System.out.println("parseInteger(String value, int min, int max)");
        String value = "2";
        int min = 2;
        int max = 5;
        int expResult = Integer.parseInt(value);
        int result = Args.parseInteger(value, min, max);
        Assert.assertEquals(expResult, result);
    }

    /**
     * Test of parseDouble method, of class Args.
     */
    @Test
    public void testParseDouble() {
        System.out.println("parseDouble(String value)");

        String value = "2.9999";
        double expResult = 2.9999;
        double result = Args.parseDouble(value);
        Assert.assertEquals(expResult, result, 0.0);
    }

    /**
     * Test of getFiles method, of class Args.
     *
     * @throws IOException
     */
    @Test
    public void testGetFiles() throws IOException {
        System.out.println("getFiles(String... files)");

        String dir = tempFolder.getRoot().toString();
        List<String> files = new LinkedList<>();
        List<Path> expResult = new LinkedList<>();
        String[] fileNames = {"data1.txt", "data2.txt", "data3.txt"};
        for (String fileName : fileNames) {
            File file = tempFolder.newFile(fileName);
            files.add(file.toString());
            expResult.add(Paths.get(dir, fileName));
        }

        List<Path> result = Args.getFiles(files.toArray(new String[0]));
        Assert.assertEquals(expResult, result);
    }

    /**
     * Test of getCharacter method, of class Args.
     */
    @Test
    public void testGetCharacter() {
        System.out.println("getCharacter(String character)");

        String character = "\t";
        char expResult = '\t';
        char result = Args.getCharacter(character);
        Assert.assertEquals(expResult, result);
    }

    /**
     * Test of getPathDir method, of class Args.
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    @Test
    public void testGetPathDir() throws FileNotFoundException, IOException {
        System.out.println("getPathDir(String dir, boolean required)");

        String dir = tempFolder.getRoot().toString();

        boolean required = false;
        Path expResult = Paths.get(dir);
        Path result = Args.getPathDir(dir, required);
        Assert.assertEquals(expResult, result);
    }

    /**
     * Test of getPathFile method, of class Args.
     *
     * @throws IOException
     */
    @Test
    public void testGetPathFile() throws IOException {
        System.out.println("getPathFile(String file, boolean requireNotNull)");

        String dir = tempFolder.getRoot().toString();
        String fileName = "twinkle_little_star.txt";

        String file = tempFolder.newFile(fileName).toString();
        Path expResult = Paths.get(dir, fileName);
        Path result = Args.getPathFile(file, true);
        Assert.assertEquals(expResult, result);
    }

}
