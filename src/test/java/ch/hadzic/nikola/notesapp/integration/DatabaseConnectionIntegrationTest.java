package ch.hadzic.nikola.notesapp.integration;

import ch.hadzic.nikola.notesapp.data.repository.NoteRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool.PoolInitializationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class DatabaseConnectionIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private NoteRepository noteRepository;

    @Test
    void t001_successfulConnection_allowsSimpleRepositoryCall() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.isValid(2), "Connection should be valid for test profile DB");
        }
        assertDoesNotThrow(() -> noteRepository.findAll(), "Repository should operate on initialized datasource");
    }

    @Test
    void t002_failedConnection_surfacesClearException() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:65432/notesapp");
        config.setUsername("wrong");
        config.setPassword("wrong");
        config.setConnectionTimeout(500);
        config.setInitializationFailTimeout(500);
        assertThrows(PoolInitializationException.class, () -> new HikariDataSource(config),
                "Invalid DB config should fail fast with pool init error");
    }

    @Test
    void t003_poolExhaustion_timesOutWhenLimitReached() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:pooltest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setConnectionTimeout(300);

        try (HikariDataSource pool = new HikariDataSource(config);
             Connection first = pool.getConnection();
             Connection second = pool.getConnection()) {
            SQLTransientConnectionException ex = assertThrows(SQLTransientConnectionException.class, pool::getConnection,
                    "Third connection should time out when pool is exhausted");
            String message = ex.getMessage().toLowerCase();
            assertTrue(message.contains("timeout") || message.contains("timed out") || message.contains("time out"),
                    "Exception message should indicate timeout");
        }
    }
}
