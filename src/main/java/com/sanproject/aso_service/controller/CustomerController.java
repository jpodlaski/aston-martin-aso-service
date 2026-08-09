package com.sanproject.aso_service.controller;

import com.sanproject.aso_service.domain.Customer;
import com.sanproject.aso_service.dto.CreateCustomerRequest;
import com.sanproject.aso_service.security.AuthSupport;
import com.sanproject.aso_service.security.AuthUser;
import com.sanproject.aso_service.service.CustomerService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// Customer listing (management) and self-lookup; clients register via /auth/register.
@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final AuthSupport authSupport;

    public CustomerController(CustomerService customerService, AuthSupport authSupport) {
        this.customerService = customerService;
        this.authSupport = authSupport;
    }

    @GetMapping
    public List<Customer> getAllCustomers() {
        authSupport.requireCanManageWorkers();
        return customerService.getAllCustomers();
    }

    @GetMapping("/me")
    public ResponseEntity<Customer> getMe() {
        AuthUser client = authSupport.requireClient();
        Customer customer = customerService.getCustomerById(client.getId());
        if (customer == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(customer);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomerById(@PathVariable Long id) {
        AuthUser user = authSupport.requireUser();
        if ("CLIENT".equals(user.getRole())) {
            if (!user.getId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot view another customer");
            }
        } else {
            authSupport.requireCanManageWorkers();
        }

        Customer customer = customerService.getCustomerById(id);

        if (customer == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(customer);
    }

    @PostMapping
    public ResponseEntity<Customer> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        authSupport.requireCanManageWorkers();
        Customer createdCustomer = customerService.createCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCustomer);
    }
}
