package com.taskflow.controller;

import com.taskflow.dto.AiQueryRequest;
import com.taskflow.dto.AiQueryResponse;
import com.taskflow.service.AiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * Endpoint for the frontend to ask the secure backend to query Gemini.
     */
    @PostMapping("/query")
    public ResponseEntity<AiQueryResponse> askAi(@RequestBody AiQueryRequest request) {
        String answer = aiService.askGemini(request.getQuery());
        return ResponseEntity.ok(new AiQueryResponse(answer));
    }
}
