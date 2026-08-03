package com.sanproject.aso_service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// Customer CRUD; create triggers a registration welcome email.
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerNotificationService notificationService;
    private final PasswordService passwordService;

    public CustomerService(
            CustomerRepository customerRepository,
            CustomerNotificationService notificationService,
            PasswordService passwordService) {
        this.customerRepository = customerRepository;
        this.notificationService = notificationService;
        this.passwordService = passwordService;
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id).orElse(null);
    }

    public Customer createCustomer(CreateCustomerRequest request) {
        String email = request.getEmail().trim();
        if (customerRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is already registered");
        }

        Customer customer = new Customer();
        customer.setFirstName(request.getFirstName().trim());
        customer.setLastName(request.getLastName().trim());
        customer.setEmail(email);
        customer.setPasswordHash(passwordService.hash(request.getPassword()));
        customer.setEmailVerified(true);

        Customer saved = customerRepository.save(customer);
        notificationService.notifyRegistered(saved);
        return saved;
    }
}
