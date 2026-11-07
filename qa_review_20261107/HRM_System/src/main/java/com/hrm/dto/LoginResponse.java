package com.hrm.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
