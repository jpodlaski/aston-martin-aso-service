-- Enforce account uniqueness and require stored password hashes.
-- App-level checks remain for clear API errors; these constraints are the last line of defense.

ALTER TABLE customer
    ALTER COLUMN password_hash SET NOT NULL;

ALTER TABLE employee
    ALTER COLUMN password_hash SET NOT NULL;

-- Exact unique constraints (match JPA @Column(unique = true)).
ALTER TABLE customer
    ADD CONSTRAINT uq_customer_email UNIQUE (email);

ALTER TABLE employee
    ADD CONSTRAINT uq_employee_login UNIQUE (login);

-- Case-insensitive: login/email lookups use IgnoreCase in the repositories.
CREATE UNIQUE INDEX uq_customer_email_ci ON customer (LOWER(email));
CREATE UNIQUE INDEX uq_employee_login_ci ON employee (LOWER(login));
