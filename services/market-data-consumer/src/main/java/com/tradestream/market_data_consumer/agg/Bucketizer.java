package com.tradestream.market_data_consumer.agg;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public final class Bucketizer {
    private Bucketizer() {}

    public static Instant bucketStart(Instant ts, Interval interval) {
        ZonedDateTime z = ts.atZone(ZoneOffset.UTC);
        return switch (interval) {
            case ONE_MIN -> z.withSecond(0).withNano(0).toInstant();
            case FIVE_MIN -> z.withMinute((z.getMinute() / 5) * 5).withSecond(0).withNano(0).toInstant();
            case ONE_HOUR -> z.withMinute(0).withSecond(0).withNano(0).toInstant();
            case ONE_DAY  -> z.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        };
    }
}
