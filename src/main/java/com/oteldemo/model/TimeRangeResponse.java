package com.oteldemo.model;

public record TimeRangeResponse(
        String original,
        String startTime,
        String endTime,
        String description
) {
}
