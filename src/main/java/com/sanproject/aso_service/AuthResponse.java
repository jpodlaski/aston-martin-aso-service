package com.sanproject.aso_service;

// Returned on login/register; stored in the frontend session (role drives routing).
public class AuthResponse {

    private String role;
    private Long id;
    private String name;

    public AuthResponse() {
    }

    public AuthResponse(String role, Long id, String name) {
        this.role = role;
        this.id = id;
        this.name = name;
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
