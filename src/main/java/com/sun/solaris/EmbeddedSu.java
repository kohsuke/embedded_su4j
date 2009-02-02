package com.sun.solaris;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Encapsulates the process launch through <tt>su</tt> (embedded_su(1M) to be exact)
 *
 * @author Kohsuke Kawaguchi
 */
public class EmbeddedSu {
    /**
     * Launches a process as configured by {@link ProcessBuilder}, but under
     * a separate user priviledge by using <tt>su</tt> functionality.
     *
     * @param user
     *      The user name in which the new process runs.
     * @param pwd
     *      The password of the user.
     */
    public static Process startWithSu(String user, String pwd, ProcessBuilder pb) throws IOException {
        // su only invokes a shell, so the argument has to be packed into the -c option
        StringBuilder buf = new StringBuilder("exec ");
        for( String cmd : pb.command() ) {
            buf.append(' ');
            // to prevent shell from interfering with characters like '$' and '`',
            // put everything in single-quotes. When we do this, single-quotes in
            // arguments have to be escaped.
            buf.append('\'').append(cmd.replaceAll("\'","'\''")).append('\'');
        }

        List<String> cmds = pb.command();
        cmds.clear();
        cmds.addAll(Arrays.asList("/usr/lib/embedded_su",user,"-c",buf.toString()));

        // now the command line massage is ready. start a process
        Process proc = pb.start();

        PrintWriter out = new PrintWriter(proc.getOutputStream());
        InputStream in = proc.getInputStream();
        // send initialization block
        LOGGER.fine("Initiating embedded_su conversation");
        out.println(".");

        while(true) {
            String line = readLine(in);
            if(line.startsWith("CONV")) {
                // how many blocks do we expect?
                int n = Integer.parseInt(line.substring(5));
                for( int i=0; i<n; i++) {
                    String header = readLine(in);
                    String textBlock = readTextBlock(in);
                    LOGGER.fine("Got "+header+" : "+textBlock);
                    if(header.startsWith("PAM_PROMPT_ECHO_OFF")) {
                        // this is where we want to send the password
                        // if we are asked the password for the 2nd time, it's rather unlikely that
                        // the same value is expected --- instead, its' probably a retry.
                        out.println(pwd);
                        pwd = null;
                    }
                    if(header.startsWith("PAM_PROMPT_ECHO_ON")) {
                        // embedded_su expects some value but this is probably not where we want to send the password
                        out.println();
                    }
                    // ignore the rest
                }
                continue;
            }
            if(line.startsWith("SUCCESS")) {
                LOGGER.fine("Authentication successful: "+line);
                return proc;
            }
            if(line.startsWith("ERROR")) {
                LOGGER.fine("Authentication faied: "+line);
                throw new SuAuthenticationFailureException(readTextBlock(in));
            }

            LOGGER.fine("Unrecognized response: "+line);
            throw new IOException("Unrecognized response from embedded_su "+line);
        }
    }

    /**
     * Reads a line from the given input stream, without any read ahead.
     *
     * Line end is '\n', as this is for Solaris.
     */
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int ch;

        while((ch=in.read())!=-1) {
            if(ch=='\n')    return buf.toString();
            buf.write(ch);
        }
        throw new EOFException();
    }

    /**
     * Reads the text block.
     */
    private static String readTextBlock(InputStream in) throws IOException {
        StringBuilder buf = new StringBuilder();

        while(true) {
            String line = readLine(in);
            if(line.equals("."))
                return buf.toString(); // end of it

            if(line.startsWith(".."))
                buf.append(line.substring(1));
            else
                buf.append(line);
            buf.append('\n');
        }
    }

    private static final Logger LOGGER = Logger.getLogger(EmbeddedSu.class.getName());
}
