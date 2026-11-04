package com.hrm.config;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Creates only the recovery table; the rest of the HRM schema remains migration-managed. */
@Component
@ConditionalOnProperty(name = "app.recovery.initialize-schema", havingValue = "true", matchIfMissing = true)
public class PasswordRecoverySchemaInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PasswordRecoverySchemaInitializer.class);
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public PasswordRecoverySchemaInitializer(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("Microsoft SQL Server")) {
                log.info("Bỏ qua khởi tạo bảng khôi phục: database không phải SQL Server.");
                return;
            }
        }
        jdbc.execute("""
                SET XACT_ABORT ON;
                IF DB_NAME() IN (N'master', N'model', N'msdb', N'tempdb')
                    THROW 55140, N'Hãy chọn database HRM.', 1;
                IF OBJECT_ID(N'dbo.accounts', N'U') IS NULL
                    THROW 55141, N'Thiếu bảng accounts.', 1;
                BEGIN TRY
                    BEGIN TRANSACTION;
                    DECLARE @lock_result int;
                    EXEC @lock_result = sp_getapplock @Resource = N'hrm_password_recovery_schema',
                        @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
                    IF @lock_result < 0 THROW 55143, N'Không thể khóa khởi tạo bảng khôi phục.', 1;
                    IF OBJECT_ID(N'dbo.password_recovery', N'U') IS NULL
                    BEGIN
                        CREATE TABLE dbo.password_recovery (
                            account_id bigint NOT NULL PRIMARY KEY REFERENCES dbo.accounts(account_id),
                            code_hash varchar(64) NOT NULL,
                            expires_at datetime2(6) NOT NULL,
                            sent_at datetime2(6) NOT NULL,
                            attempts int NOT NULL DEFAULT 0,
                            token_version int NOT NULL
                        );
                    END;
                    COMMIT TRANSACTION;
                END TRY
                BEGIN CATCH
                    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
                    THROW;
                END CATCH;
                """);
        log.info("Bảng khôi phục mật khẩu đã sẵn sàng.");
    }
}
