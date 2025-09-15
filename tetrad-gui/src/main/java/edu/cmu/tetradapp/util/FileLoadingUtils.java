package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.util.Version;

import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

/**
 * Sundry utilities for loading files.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class FileLoadingUtils {
    // Converts the contents of a file into a CharSequence
    // suitable for use by the regex package.

    /**
     * <p>fromFile.</p>
     *
     * @param file a {@link java.io.File} object
     * @return a {@link java.lang.String} object
     * @throws java.io.IOException if any.
     */
    public static String fromFile(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        FileChannel fc = fis.getChannel();

        // Create a read-only CharBuffer on the file
        ByteBuffer bbuf =
                fc.map(FileChannel.MapMode.READ_ONLY, 0, (int) fc.size());
        CharBuffer cbuf = StandardCharsets.ISO_8859_1.newDecoder().decode(bbuf);

        String s = cbuf.toString();
        fis.close();
        return s;
    }

    /**
     * <p>fromResources.</p>
     *
     * @param path a {@link java.lang.String} object
     * @return a {@link java.lang.String} object
     */
    public static String fromResources(String path) {
        try {
            URL url = Version.class.getResource(path);

            if (url == null) {
                throw new RuntimeException(
                        "Could not load resource file: " + path);
            }

            InputStream inStream = url.openStream();
            Reader reader = new InputStreamReader(inStream);
            BufferedReader bufReader = new BufferedReader(reader);

            String line;
            StringBuilder spec = new StringBuilder();
            while ((line = bufReader.readLine()) != null) {
                spec.append(line).append("\n");
            }
            bufReader.close();

            return spec.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }

        throw new RuntimeException("Could not load resource file: " + path);
    }
}




