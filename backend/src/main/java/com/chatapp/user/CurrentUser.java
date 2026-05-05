package com.chatapp.user;

public record CurrentUser(String id, String username, String email) {
    public static CurrentUser from(User user) {
        return new CurrentUser(user.getId(), user.getUsername(), user.getEmail());
    }
}
