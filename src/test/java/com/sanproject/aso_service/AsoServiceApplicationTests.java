package com.sanproject.aso_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class AsoServiceApplicationTests {

	@MockitoBean
	private BookingNotificationService bookingNotificationService;

	@MockitoBean
	private CustomerNotificationService customerNotificationService;

	@Test
	void contextLoads() {
	}

}
