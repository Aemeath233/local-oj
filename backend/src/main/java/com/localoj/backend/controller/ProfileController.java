package com.localoj.backend.controller;

import com.localoj.backend.api.ApiResponse;
import com.localoj.backend.security.CurrentUser;
import com.localoj.backend.security.SecurityUtils;
import com.localoj.backend.service.ProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ApiResponse<ProfileService.ProfileView> profile() {
        return ApiResponse.ok(profileService.profile(SecurityUtils.currentUser()));
    }

    @GetMapping("/stats")
    public ApiResponse<ProfileService.UserStatsView> stats() {
        return ApiResponse.ok(profileService.getUserStats(SecurityUtils.currentUser()));
    }

    @PutMapping
    public ApiResponse<ProfileService.ProfileView> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(profileService.updateProfile(currentUser, request.toCommand()));
    }

    @PostMapping("/avatar")
    public ApiResponse<ProfileService.ProfileView> uploadAvatar(@RequestParam("file") MultipartFile file) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.ok(profileService.updateAvatar(currentUser, file));
    }

    @GetMapping("/avatar/{userId}/{filename:.+}")
    public ResponseEntity<byte[]> avatar(@PathVariable("userId") Long userId, @PathVariable("filename") String filename) {
        ProfileService.AvatarFile avatar = profileService.readAvatar(userId, filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(avatar.bytes());
    }

    @PostMapping("/password-code")
    public ApiResponse<Object> sendPasswordCode() {
        profileService.sendPasswordCode(SecurityUtils.currentUser());
        return ApiResponse.ok(null);
    }

    @PutMapping("/password")
    public ApiResponse<Object> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(SecurityUtils.currentUser(), request.toCommand());
        return ApiResponse.ok(null);
    }

    @GetMapping("/{userId}/public")
    public ApiResponse<ProfileService.PublicProfileView> publicProfile(@PathVariable("userId") Long userId) {
        return ApiResponse.ok(profileService.getPublicProfile(userId));
    }

    @PostMapping("/email-change-code")
    public ApiResponse<Object> sendEmailChangeCode(@Valid @RequestBody SendEmailChangeCodeRequest request) {
        profileService.sendEmailChangeCode(SecurityUtils.currentUser(), request.newEmail());
        return ApiResponse.ok(null);
    }

    @PutMapping("/email")
    public ApiResponse<ProfileService.ProfileView> changeEmail(@Valid @RequestBody ChangeEmailRequest request) {
        return ApiResponse.ok(profileService.changeEmail(SecurityUtils.currentUser(), request.newEmail(), request.code()));
    }

    public record SendEmailChangeCodeRequest(
            @NotBlank @jakarta.validation.constraints.Email String newEmail
    ) {
    }

    public record UpdateProfileRequest(String username, @NotBlank String displayName, String studentNo, String major) {
        ProfileService.UpdateProfileCommand toCommand() {
            return new ProfileService.UpdateProfileCommand(username, displayName, studentNo, major);
        }
    }

    public record ChangePasswordRequest(@NotBlank String code, @NotBlank String newPassword) {
        ProfileService.ChangePasswordCommand toCommand() {
            return new ProfileService.ChangePasswordCommand(code, newPassword);
        }
    }

    public record ChangeEmailRequest(@NotBlank String newEmail, @NotBlank String code) {}
}
