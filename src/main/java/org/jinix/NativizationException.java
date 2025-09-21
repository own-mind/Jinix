package org.jinix;

import java.io.IOException;

public class NativizationException extends RuntimeException {
    public NativizationException(String message){
        super(message);
    }

    public NativizationException(String message, Exception e) {
        super(message, e);
    }
}
