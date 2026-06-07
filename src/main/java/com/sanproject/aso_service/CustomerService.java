package com.sanproject.aso_service;

import org.springframework.stereotype.Service;

import java.util.List;

// Customer CRUD; create triggers a registration welcome email.
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerNotificationService notificationService;

    public CustomerService(
            CustomerRepository customerRepository,
            CustomerNotificationService notificationService) {
        this.customerRepository = customerRepository;
        this.notificationService = notificationService;
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id).orElse(null);
    }

    public Customer createCustomer(Customer customer) {
        Customer saved = customerRepository.save(customer);
        notificationService.notifyRegistered(saved);
        return saved;
    }
}
