package com.cibc.main.sql;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

public final class SqlServerClientWrapper {
    private final SQLServerDataSource dataSource;
    private final String tableName;

    public SqlServerClientWrapper(SQLServerDataSource dataSource, String tableName) {
        this.dataSource = dataSource;
        this.tableName = tableName;
    }

    public boolean testConnection() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(5);
        } catch (SQLException e) {
            System.err.println("Azure SQL connection test failed: " + e.getMessage());
            return false;
        }
    }

    public int insert(long id, String data) throws SQLException {
        String sql = "INSERT INTO " + tableName + " "
                + "(id, data, create_timestamp, create_timestamp_unix, update_timestamp, update_timestamp_unix) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        Instant now = Instant.now();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bindInsert(ps, id, data, now);
            return ps.executeUpdate();
        }
    }

    public int update(long id, String data) throws SQLException {
        String sql = "UPDATE " + tableName
                + " SET data = ?, update_timestamp = ?, update_timestamp_unix = ? WHERE id = ?";
        Instant now = Instant.now();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, data);
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setLong(3, now.toEpochMilli());
            ps.setLong(4, id);
            int rows = ps.executeUpdate();
            if (rows != 1) throw new SQLException("UPDATE expected 1 row for id=" + id + ", got " + rows);
            return rows;
        }
    }

    public int delete(long id) throws SQLException {
        String sql = "DELETE FROM " + tableName + " WHERE id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            int rows = ps.executeUpdate();
            if (rows != 1) throw new SQLException("DELETE expected 1 row for id=" + id + ", got " + rows);
            return rows;
        }
    }

    public void batchInsert(List<SqlTestRecord> records) throws SQLException {
        String sql = "INSERT INTO " + tableName
                + " (id, data, create_timestamp, create_timestamp_unix, update_timestamp, update_timestamp_unix) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (SqlTestRecord record : records) {
                bindInsert(ps, record.id(), record.data(), Instant.now());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void batchUpdate(List<SqlTestRecord> records) throws SQLException {
        String sql = "UPDATE " + tableName
                + " SET data = ?, update_timestamp = ?, update_timestamp_unix = ? WHERE id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (SqlTestRecord record : records) {
                Instant now = Instant.now();
                ps.setString(1, record.data());
                ps.setTimestamp(2, Timestamp.from(now));
                ps.setLong(3, now.toEpochMilli());
                ps.setLong(4, record.id());
                ps.addBatch();
            }
            int[] counts = ps.executeBatch();
            checkBatchCounts(counts, records.size(), "UPDATE");
        }
    }

    public void batchDelete(List<SqlTestRecord> records) throws SQLException {
        String sql = "DELETE FROM " + tableName + " WHERE id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (SqlTestRecord record : records) {
                ps.setLong(1, record.id());
                ps.addBatch();
            }
            int[] counts = ps.executeBatch();
            checkBatchCounts(counts, records.size(), "DELETE");
        }
    }

    private static void bindInsert(PreparedStatement ps, long id, String data, Instant now) throws SQLException {
        ps.setLong(1, id);
        ps.setString(2, data);
        ps.setTimestamp(3, Timestamp.from(now));
        ps.setLong(4, now.toEpochMilli());
        ps.setTimestamp(5, Timestamp.from(now));
        ps.setLong(6, now.toEpochMilli());
    }

    private static void checkBatchCounts(int[] counts, int expected, String operation) throws SQLException {
        if (counts.length != expected) {
            throw new SQLException(operation + " batch returned " + counts.length + " counts; expected " + expected);
        }
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] == 0) throw new SQLException(operation + " batch affected no row at index " + i);
            if (counts[i] < 0 && counts[i] != PreparedStatement.SUCCESS_NO_INFO) {
                throw new SQLException(operation + " batch failed at index " + i + " with code " + counts[i]);
            }
        }
    }

    public record SqlTestRecord(long id, String data) {}
}
