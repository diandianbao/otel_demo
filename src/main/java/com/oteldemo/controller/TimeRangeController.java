package com.oteldemo.controller;

import com.oteldemo.model.TimeRangeRequest;
import com.oteldemo.model.TimeRangeResponse;
import com.oteldemo.service.TimeRangeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/time-range")
public class TimeRangeController {

    private final TimeRangeService timeRangeService;

    public TimeRangeController(TimeRangeService timeRangeService) {
        this.timeRangeService = timeRangeService;
    }

    @PostMapping("/parse")
    public TimeRangeResponse parse(@RequestBody TimeRangeRequest request) {
        return timeRangeService.parse(request);
    }
}
