package com.cibc.main;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerException;

import java.sql.*;
import java.time.Instant;
import java.util.List;

public class SqlServerClientWrapper {

    private final SQLServerDataSource client;

    public SqlServerClientWrapper(SQLServerDataSource client) {
        this.client = client;
    }

    public boolean testConnection() {

        try (Connection connection = client.getConnection()) {
            return connection.isValid(5);

        } catch (SQLException e) {
            System.err.println("Azure SQL connection failed: " + e.getMessage());
            return false;
        }
    }

    // CRUD

    public Long insert(String data) throws SQLException {

        // The end of this SQL statement is partially cut off in the image.
        String sql = "INSERT INTO dbo.RDI_TEST (data, create_timestamp, create_timestamp_unix, update_timestamp, update_timestamp_unix) "
                + "VALUES (?, ?, ?, ?, ?)";

        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);
        Long unixTimestamp = now.getEpochSecond();

        try (Connection connection = client.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, data);
            statement.setTimestamp(2, timestamp);
            statement.setLong(3, unixTimestamp);
            statement.setTimestamp(4, timestamp);
            statement.setLong(5, unixTimestamp);

            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                } else {
                    throw new SQLException(
                            "Creating record failed, no ID obtained."
                    );
                }
            }
        }
    }

    public void update(Long id, String data) throws SQLException {

        String sql = "UPDATE dbo.RDI_TEST SET data = ?, update_timestamp = ?, update_timestamp_unix = ? WHERE id = ?";

        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);
        Long unixTimestamp = now.getEpochSecond();

        try (Connection connection = client.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, data);
            statement.setTimestamp(2, timestamp);
            statement.setLong(3, unixTimestamp);
            statement.setLong(4, id);

            int rows = statement.executeUpdate();

            if (rows == 0) {
                throw new SQLException("No record found for id = " + id);
            }
        }
    }

    public void delete(Long id) throws SQLException {

        String sql = "DELETE FROM dbo.RDI_TEST WHERE id = ?";

        try (Connection connection = client.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, id);

            statement.executeUpdate();
        }
    }

    // The remaining methods are below the visible portion of the images.
}
