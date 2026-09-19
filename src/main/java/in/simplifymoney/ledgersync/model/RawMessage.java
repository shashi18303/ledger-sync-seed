package in.simplifymoney.ledgersync.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.ZonedDateTime;

public record RawMessage(
    @JsonProperty("message_id") String messageId,
    @JsonProperty("channel") String channel,
    @JsonProperty("sender") String sender,
    @JsonProperty("received_at") ZonedDateTime receivedAt,
    @JsonProperty("device_id") String deviceId,
    @JsonProperty("body") String body
) {}
