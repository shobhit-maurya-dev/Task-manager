package com.taskflow.controller;

import com.taskflow.dto.UserSettingsDTO;
import com.taskflow.dto.UserSettingsRequest;
import com.taskflow.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    @Autowired
    private SettingsService settingsService;

    @GetMapping
    public ResponseEntity<UserSettingsDTO> getSettings() {
        return ResponseEntity.ok(settingsService.getCurrentUserSettings());
    }

    @PutMapping
    public ResponseEntity<UserSettingsDTO> updateSettings(
            @Valid @RequestBody UserSettingsRequest request) {
        UserSettingsDTO dto = settingsService.updateSettings(
                settingsService.getCurrentUserSettings().getUserId(), request);
        return ResponseEntity.ok(dto);
    }
}
