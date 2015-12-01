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

package edu.cmu.tetrad.util;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.*;

/**
 * Sets up streams for logging via the Java logging API. To add a stream to the
 * logging, call addStream(stream, level). The formatter will be a simple
 * formatter that outputs the text of the logging messages only. To remove the
 * stream, call removeStream(stream).
 *
 * @author Joseph Ramsey
 */
@SuppressWarnings({"UnusedDeclaration"})
public class LogUtils {

    /**
     * Singleton instance.
     */
    private static final LogUtils INSTANCE = new LogUtils();

    /**
     * The logger being used.
     */
    private final Logger logger = Logger.getLogger("tetradlog");

    /**
     * Map from streams to handlers.
     */
    private final Map<OutputStream, StreamHandler> streams
            = new HashMap<>();

    //============================CONSTRUCTORS===========================//

    /**
     * Private constructor.
     */
    private LogUtils() {
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.FINEST);
    }

    /**
     * @return Ibid.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public static LogUtils getInstance() {
        return INSTANCE;
    }

    //===========================PUBLIC METHODS=========================//

    /**
     * Adds the given stream to logging.
     *
     * @param stream The OutputStream to be added to logging.
     * @param level  The level at which logging events will be printed.
     */
    public void add(OutputStream stream, Level level) {
        if (stream == null) {
            throw new NullPointerException();
        }

        if (level == null) {
            throw new NullPointerException();
        }

        SimpleFormatter formatter = new SimpleFormatter() {
            public synchronized String format(LogRecord record) {
                return record.getMessage() +
                        "\n";
            }
        };

        StreamHandler handler = new StreamHandler(stream, formatter);
        handler.setLevel(level);
        streams.put(stream, handler);

        getLogger().addHandler(handler);
    }

    /**
     * Sets the logging level for the given stream.
     *
     * @param stream The OutputStream whose level is to change.
     * @param level  The new level.
     */
    public void setLevel(OutputStream stream, Level level) {
        Handler handler = streams.get(stream);

        if (handler != null) {
            handler.setLevel(level);
        }
    }

    /**
     * Removes the given stream from logging.
     *
     * @param stream Ibid.
     */
    private void remove(OutputStream stream) {
        if (stream == null) {
            return;
        }

        Handler handler = streams.get(stream);

        if (handler == null) {
            return;
        }

        handler.flush();

        if (stream != System.out) {
            handler.close();
        }

        getLogger().removeHandler(handler);
    }

    /**
     * Removes all streams from logging.
     */
    public void clear() {
        for (OutputStream stream : streams.keySet()) {
            remove(stream);
        }
    }

    public void severe(String s) {
        getLogger().severe(s);
    }

    public void warning(String s) {
        getLogger().warning(s);
        flushAll();
    }

    public void config(String s) {
        getLogger().config(s);
        flushAll();
    }

    public void info(String s) {
        getLogger().info(s);
        flushAll();
    }

    public void fine(String s) {
        getLogger().fine(s);
        flushAll();
    }

    public void finer(String s) {
        getLogger().finer(s);
        flushAll();
    }

    public void finest(String s) {
        getLogger().finest(s);
        flushAll();
    }

    private Logger getLogger() {
        return logger;
    }

    private void flushAll() {
        for (OutputStream stream : streams.keySet()) {
            Handler handler = streams.get(stream);
            handler.flush();
        }
    }

}





