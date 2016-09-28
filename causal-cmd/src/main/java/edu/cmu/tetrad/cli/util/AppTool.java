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
package edu.cmu.tetrad.cli.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

/**
 *
 * Sep 9, 2016 7:50:01 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class AppTool {

    private static final DateFormat DF = new SimpleDateFormat("EEE, MMMM dd, yyyy hh:mm:ss a");

    private AppTool() {
    }

    public static String fmtDate(Date date) {
        return DF.format(date);
    }

    public static String fmtDateNow() {
        return fmtDate(new Date(System.currentTimeMillis()));
    }

    public static String jarTitle() {
        return AppTool.class.getPackage().getImplementationTitle();
    }

    public static String jarVersion() {
        return AppTool.class.getPackage().getImplementationVersion();
    }

    public static void showHelp(Options options) {
        String title = jarTitle();
        String version = jarVersion();

        String cmdLineSyntax;
        if (title == null || version == null) {
            cmdLineSyntax = "java -jar causal-cmd.jar";
        } else {
            cmdLineSyntax = String.format("java -jar %s-%s.jar", title, version);
        }

        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(cmdLineSyntax, options, true);
    }

    public static void showHelp(String algorithmName, Options options) {
        String title = jarTitle();
        String version = jarVersion();

        String cmdLineSyntax = (title == null || version == null)
                ? String.format("java -jar causal-cmd.jar --algorithm %s", algorithmName)
                : String.format("java -jar %s-%s.jar --algorithm %s", title, version, algorithmName);

        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(-1);
        formatter.printHelp(cmdLineSyntax, options, true);
    }

}
