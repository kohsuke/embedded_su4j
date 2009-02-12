package com.sun.solaris;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.IOException;

public class AppTest  extends TestCase
{
    public void testFoo() throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("touch","/xxx'escape'tes\"t");
            EmbeddedSu.startWithSu("bogus", "bogus", pb);
            fail();
        } catch (SuAuthenticationFailureException e) {
            // expected
        }
    }
}
