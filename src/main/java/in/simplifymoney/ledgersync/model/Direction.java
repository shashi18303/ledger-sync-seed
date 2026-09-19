package in.simplifymoney.ledgersync.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Direction {
    DEBIT("debit"),
    CREDIT("credit");

    private final String value;

    Direction(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static Direction fromString(String text) {
        if (text == null) return null;
        for (Direction d : Direction.values()) {
            if (d.value.equalsIgnoreCase(text) || d.name().equalsIgnoreCase(text)) {
                return d;
            }
        }
        throw new IllegalArgumentException("Unknown direction: " + text);
    }
}
