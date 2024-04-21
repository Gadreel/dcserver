package com.samstevens.totp.time;

import com.samstevens.totp.exceptions.TimeProviderException;
import java.time.Instant;

public class SystemTimeProvider implements TimeProvider {
    @Override
    public long getTime() throws TimeProviderException {
        return Instant.now().getEpochSecond();
    }
}
