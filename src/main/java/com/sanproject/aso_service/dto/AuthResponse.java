package com.sanproject.aso_service.dto;

// Returned on login/register; token is the Bearer JWT; role/id/name drive frontend routing.
public class AuthResponse {

    private String token;
    private String role;
    private Long id;
    private String name;

    public AuthResponse() {
    }

    public AuthResponse(String token, String role, Long id, String name) {
        this.token = token;
        this.role = role;
        this.id = id;
        this.name = name;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
