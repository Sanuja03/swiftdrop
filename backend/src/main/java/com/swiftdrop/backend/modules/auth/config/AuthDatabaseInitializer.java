package com.swiftdrop.backend.modules.auth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthDatabaseInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            // 1. Password reset tokens table
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS password_reset_tokens (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    token VARCHAR(128) NOT NULL,
                    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
                    used BOOLEAN NOT NULL DEFAULT FALSE,
                    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                );
                CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_token ON password_reset_tokens(token);
            """);

            // 2. Add email_verified column to users if not present
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN DEFAULT FALSE;");
            
            // Mark Google OAuth users and existing pre-existing users as verified
            jdbcTemplate.execute("UPDATE users SET email_verified = TRUE WHERE provider = 'GOOGLE' OR email_verified IS NULL;");

            // 3. Email verification tokens table
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS email_verification_tokens (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    token VARCHAR(128) NOT NULL,
                    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
                    verified BOOLEAN NOT NULL DEFAULT FALSE,
                    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                );
                CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_token ON email_verification_tokens(token);
                CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_user ON email_verification_tokens(user_id);
            """);

            log.info("AuthDatabaseInitializer: Verified auth schema, columns, and verification token tables.");
        } catch (Exception e) {
            log.warn("AuthDatabaseInitializer: Note on table check: {}", e.getMessage());
        }
    }
}

