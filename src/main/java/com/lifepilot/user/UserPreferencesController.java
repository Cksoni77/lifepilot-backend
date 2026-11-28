package com.lifepilot.user;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/preferences")
public class UserPreferencesController {
    private final UserPreferencesService preferencesService;

    public UserPreferencesController(UserPreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    @GetMapping
    public UserPreferencesDtos.PreferencesResponse get() {
        return preferencesService.get();
    }

    @PutMapping
    public UserPreferencesDtos.PreferencesResponse update(
            @Valid @RequestBody UserPreferencesDtos.PreferencesRequest request) {
        return preferencesService.update(request);
    }

    @GetMapping("/profiles")
    public UserPreferencesDtos.ProfilesResponse getProfiles() {
        return new UserPreferencesDtos.ProfilesResponse(preferencesService.getProfiles());
    }

    @PutMapping("/profiles")
    public UserPreferencesDtos.ProfilesResponse updateProfiles(
            @Valid @RequestBody UserPreferencesDtos.ProfilesRequest request) {
        return new UserPreferencesDtos.ProfilesResponse(
                preferencesService.updateProfiles(request.profiles()));
    }
}
