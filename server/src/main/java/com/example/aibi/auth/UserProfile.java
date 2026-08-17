package com.example.aibi.auth;

import java.util.Set;

public record UserProfile(long id, String username, String displayName, Set<String> roles,
                          Set<String> permissions) {
}
