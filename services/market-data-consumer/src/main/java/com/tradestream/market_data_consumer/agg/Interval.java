package com.tradestream.market_data_consumer.agg;

public enum Interval {
    ONE_MIN("1m"), FIVE_MIN("5m"), ONE_HOUR("1h"), ONE_DAY("1d");

    private final String code;
    Interval(String code) { this.code = code; }
    public String code() { return code; }

    public static Interval fromCode(String c) {
        return switch (c) {
            case "1m" -> ONE_MIN;
            case "5m" -> FIVE_MIN;
            case "1h" -> ONE_HOUR;
            case "1d" -> ONE_DAY;
            default -> throw new IllegalArgumentException("Unsupported interval: " + c);
        };
    }
}
