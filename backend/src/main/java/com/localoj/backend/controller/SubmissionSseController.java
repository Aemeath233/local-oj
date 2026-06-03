package com.localoj.backend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/submissions/live")
public class SubmissionSseController {
    private static final Logger log = LoggerFactory.getLogger(SubmissionSseController.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final com.localoj.backend.service.LeaderboardService leaderboardService;
    private final com.localoj.backend.service.SubmissionService submissionService;

    public SubmissionSseController(
            com.localoj.backend.service.LeaderboardService leaderboardService,
            com.localoj.backend.service.SubmissionService submissionService
    ) {
        this.leaderboardService = leaderboardService;
        this.submissionService = submissionService;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(120_000L); // 2 minutes timeout
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((ex) -> emitters.remove(emitter));

        try {
            // Send connection established event
            emitter.send(SseEmitter.event().name("init").data("connected"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    public void handleRedisMessage(String message) {
        try {
            if (message != null) {
                // Clear submission list cache FIRST so that clients get fresh data when they refresh
                submissionService.evictLatestSubmissionsCache();

                String[] parts = message.split(",");
                if (parts.length >= 3) {
                    String status = parts[1];
                    String verdict = parts[2];
                    if ("FINISHED".equalsIgnoreCase(status) && "AC".equalsIgnoreCase(verdict)) {
                        leaderboardService.evictCache();
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to process pubsub submission status", ex);
        }

        broadcast(message);
    }

    public void broadcast(String data) {
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("update").data(data));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }
}
