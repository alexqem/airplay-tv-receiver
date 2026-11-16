package com.airplay.android.dlna;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * RFC 1123 dates as UPnP headers want them. {@code SimpleDateFormat} built once per call keeps
 * the helper thread-safe at the cost of an allocation per message, which is negligible here.
 */
final class HttpDate {

    private HttpDate() {
    }

    static String now() {
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'",
                Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return format.format(new Date());
    }
}
