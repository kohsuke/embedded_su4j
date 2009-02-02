package com.sun.solaris;

import java.io.IOException;

/**
 * 'su' failed to authenticate the given credential.
 *
 * <p>
 * Wrong password, invalid user name, that sort of things.
 *
 * @author Kohsuke Kawaguchi
 */
public class SuAuthenticationFailureException extends IOException {
    public SuAuthenticationFailureException(String message) {
        super(message);
    }
}
