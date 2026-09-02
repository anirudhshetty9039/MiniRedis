package com.miniredis.protocol;

import java.io.IOException;

public final class RespException extends IOException {
    public RespException(String message) {
        super(message);
    }
}
