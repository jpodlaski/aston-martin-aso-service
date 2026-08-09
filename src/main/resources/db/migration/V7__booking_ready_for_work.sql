ALTER TABLE service_booking
    DROP CONSTRAINT IF EXISTS service_booking_status_check;

ALTER TABLE service_booking
    ADD CONSTRAINT service_booking_status_check
        CHECK (status IS NULL OR (status >= 0 AND status <= 4));
