package com.hrm.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class PasswordRecoverySchemaInitializerTest {
    @Test void initializesSqlServerIdempotently() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("Microsoft SQL Server");
        var initializer = new PasswordRecoverySchemaInitializer(source, jdbc);
        initializer.run(null);
        initializer.run(null);
        verify(jdbc, times(2)).execute(contains("IF OBJECT_ID(N'dbo.password_recovery', N'U') IS NULL"));
        verify(jdbc, times(2)).execute(contains("sp_getapplock"));
        verify(connection, times(2)).close();
    }

    @Test void skipsOtherDatabases() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("H2");
        new PasswordRecoverySchemaInitializer(source, jdbc).run(null);
        verifyNoInteractions(jdbc);
    }
}
