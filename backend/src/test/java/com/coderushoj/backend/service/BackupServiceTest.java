package com.coderushoj.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BackupServiceTest {

    @TempDir
    Path tempDir;

    @Test
    public void testExportBackupAndImportRestore() throws Exception {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        Statement mockStatement = mock(Statement.class);
        DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockConnection.getMetaData()).thenReturn(mockMetaData);
        when(mockConnection.getAutoCommit()).thenReturn(true);
        when(mockConnection.isClosed()).thenReturn(false);
        when(mockMetaData.getDatabaseProductName()).thenReturn("MySQL");
        when(mockMetaData.getIdentifierQuoteString()).thenReturn("`");
        when(mockMetaData.getDatabaseProductVersion()).thenReturn("8.0");

        // VERY IMPORTANT: Prevent infinite loops in JDBC driver mock statements
        when(mockStatement.getUpdateCount()).thenReturn(-1);

        Path dataRoot = tempDir.resolve("data_root");
        Files.createDirectories(dataRoot);

        // Create sample problems & avatars
        Path problemsDir = dataRoot.resolve("problems").resolve("1").resolve("cases");
        Files.createDirectories(problemsDir);
        Files.writeString(problemsDir.resolve("1.in"), "input_data");

        Path avatarsDir = dataRoot.resolve("avatars").resolve("42");
        Files.createDirectories(avatarsDir);
        Files.writeString(avatarsDir.resolve("avatar.png"), "avatar_bytes");

        // Mock upload zip file
        Path zipTestPath = tempDir.resolve("import_test.zip");
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zipTestPath))) {
            zos.putNextEntry(new ZipEntry("db.sql"));
            zos.write("CREATE TABLE dummy (id INT);".getBytes());
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("problems/1/cases/1.in"));
            zos.write("new_input_content".getBytes());
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("avatars/42/avatar.png"));
            zos.write("new_avatar_bytes".getBytes());
            zos.closeEntry();
        }

        byte[] zipBytes = Files.readAllBytes(zipTestPath);
        MockMultipartFile mockMultipartFile = new MockMultipartFile("file", "backup.zip", "application/zip", zipBytes);

        BackupService backupService = new BackupService(
                mockDataSource,
                "jdbc:mysql://localhost:3306/coderush_oj",
                "root",
                "123456",
                dataRoot.toString()
        );

        // Execute import
        backupService.importBackup(mockMultipartFile);

        // Verify statements execution
        verify(mockConnection, atLeastOnce()).createStatement();
        verify(mockStatement, atLeastOnce()).execute("SET FOREIGN_KEY_CHECKS=0");
        verify(mockStatement, atLeastOnce()).execute("SET FOREIGN_KEY_CHECKS=1");

        // Verify folders were replaced and unzipped correctly
        Path targetFile = dataRoot.resolve("problems").resolve("1").resolve("cases").resolve("1.in");
        assertTrue(Files.exists(targetFile));
        assertEquals("new_input_content", Files.readString(targetFile));

        Path targetAvatar = dataRoot.resolve("avatars").resolve("42").resolve("avatar.png");
        assertTrue(Files.exists(targetAvatar));
        assertEquals("new_avatar_bytes", Files.readString(targetAvatar));
    }
}
