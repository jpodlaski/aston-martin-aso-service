-- Allow account-deletion confirmation tokens (email link, same pattern as verify-email).
ALTER TABLE customer_account_token
    DROP CONSTRAINT customer_account_token_purpose_check;

ALTER TABLE customer_account_token
    ADD CONSTRAINT customer_account_token_purpose_check CHECK (
        purpose IN ('PASSWORD_RESET', 'EMAIL_VERIFICATION', 'ACCOUNT_DELETION')
    );
