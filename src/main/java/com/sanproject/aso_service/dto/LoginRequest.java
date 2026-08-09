package com.sanproject.aso_service.dto;

import jakarta.validation.constraints.NotBlank;

// Shared login DTO: email for clients, login name for employees.
public class LoginRequest {

    @NotBlank(message = "Login is required")
    private String login;

    @NotBlank(message = "Password is required")
    private String password;

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
