package com.coderushoj.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class BackupService {
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private final DataSource dataSource;
    private final String dbUrl;
    private final String dbUsername;
    private final String dbPassword;
    private final Path dataRoot;

    public BackupService(
            DataSource dataSource,
            @Value("${spring.datasource.url}") String dbUrl,
            @Value("${spring.datasource.username}") String dbUsername,
            @Value("${spring.datasource.password}") String dbPassword,
            @Value("${app.data-root:/data}") String dataRootStr
    ) {
        this.dataSource = dataSource;
        this.dbUrl = dbUrl;
        this.dbUsername = dbUsername;
        this.dbPassword = dbPassword;
        this.dataRoot = Paths.get(dataRootStr).toAbsolutePath().normalize();
    }

    /**
     * Exports a full-site backup zip file containing the DB dump and test cases/avatars.
     */
    public byte[] exportBackup() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeBackup(baos);
        return baos.toByteArray();
    }

    /**
     * Streams a full-site backup zip file containing the DB dump and test cases/avatars.
     */
    public void writeBackup(OutputStream outputStream) throws IOException {
        Path tempDir = Files.createTempDirectory("oj_backup_export_");
        File sqlFile = tempDir.resolve("db.sql").toFile();

        try {
            // 1. Generate SQL dump
            dumpDatabase(sqlFile);

            // 2. Prepare Zip
            try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
                // Add db.sql
                ZipEntry sqlEntry = new ZipEntry("db.sql");
                zos.putNextEntry(sqlEntry);
                Files.copy(sqlFile.toPath(), zos);
                zos.closeEntry();

                // Add problems/ test cases directory
                Path problemsDir = dataRoot.resolve("problems").normalize();
                if (Files.exists(problemsDir)) {
                    zipDirectory(problemsDir, zos, "problems");
                }

                // Add avatars/ directory
                Path avatarsDir = dataRoot.resolve("avatars").normalize();
                if (Files.exists(avatarsDir)) {
                    zipDirectory(avatarsDir, zos, "avatars");
                }
            }

            log.info("Full site backup zip streamed successfully.");

        } finally {
            // Clean up temp sql file and dir
            if (sqlFile.exists()) {
                sqlFile.delete();
            }
            Files.deleteIfExists(tempDir);
        }
    }

    /**
     * Imports/restores full-site data from an uploaded zip file.
     */
    public void importBackup(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传的备份文件为空");
        }

        Path tempExtractDir = Files.createTempDirectory("oj_backup_import_");

        try {
            // 1. Unzip and validate
            try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    File destFile = newFile(tempExtractDir.toFile(), entry);
                    if (entry.isDirectory()) {
                        destFile.mkdirs();
                    } else {
                        destFile.getParentFile().mkdirs();
                        try (OutputStream os = new FileOutputStream(destFile)) {
                            zis.transferTo(os);
                        }
                    }
                    zis.closeEntry();
                }
            }

            Path sqlFile = tempExtractDir.resolve("db.sql");
            if (!Files.exists(sqlFile)) {
                throw new IllegalArgumentException("备份归档文件中缺失关键数据库定义文件 (db.sql)");
            }

            // 2. Drop and restore database tables (JDBC ScriptUtils execution)
            log.info("Restoring database from backup db.sql...");
            try (Connection conn = dataSource.getConnection()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("SET FOREIGN_KEY_CHECKS=0");
                }
                ScriptUtils.executeSqlScript(conn, new FileSystemResource(sqlFile));
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("SET FOREIGN_KEY_CHECKS=1");
                }
            } catch (Exception e) {
                log.error("Database restore execution failed", e);
                throw new IOException("数据库结构恢复失败: " + e.getMessage(), e);
            }

            // 3. Sync problems directory
            Path backupProblemsDir = tempExtractDir.resolve("problems");
            Path targetProblemsDir = dataRoot.resolve("problems").normalize();
            if (Files.exists(backupProblemsDir)) {
                log.info("Restoring problems directory...");
                deleteDirectoryRecursively(targetProblemsDir);
                copyDirectory(backupProblemsDir, targetProblemsDir);
            }

            // 4. Sync avatars directory
            Path backupAvatarsDir = tempExtractDir.resolve("avatars");
            Path targetAvatarsDir = dataRoot.resolve("avatars").normalize();
            if (Files.exists(backupAvatarsDir)) {
                log.info("Restoring avatars directory...");
                deleteDirectoryRecursively(targetAvatarsDir);
                copyDirectory(backupAvatarsDir, targetAvatarsDir);
            }

            log.info("Full site backup restore completed successfully.");

        } finally {
            // Clean up temporary extracted directory recursively
            deleteDirectoryRecursively(tempExtractDir);
        }
    }

    private void dumpDatabase(File outputFile) throws IOException {
        DbParams params = parseDbUrl(dbUrl);
        String mysqldumpPath = findMysqldumpPath();

        log.info("Executing database dump using: {}, database: {}", mysqldumpPath, params.database);

        ProcessBuilder pb = new ProcessBuilder(
                mysqldumpPath,
                "-h", params.host,
                "-P", String.valueOf(params.port),
                "-u", dbUsername,
                params.database,
                "--result-file=" + outputFile.getAbsolutePath()
        );
        pb.environment().put("MYSQL_PWD", dbPassword);

        try {
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String errorMsg = readStream(process.getErrorStream());
                log.error("mysqldump failed with code {}. Error: {}", exitCode, errorMsg);
                throw new IOException("mysqldump 执行失败，退出码: " + exitCode + ". " + errorMsg);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("数据库备份操作被中断", e);
        }
    }

    private String findMysqldumpPath() {
        if (isCommandAvailable("mysqldump")) {
            return "mysqldump";
        }
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            String[] commonPaths = {
                    "C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysqldump.exe",
                    "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysqldump.exe",
                    "C:\\Program Files\\MySQL\\MySQL Server 5.7\\bin\\mysqldump.exe",
                    "C:\\Program Files (x86)\\MySQL\\MySQL Server 8.0\\bin\\mysqldump.exe"
            };
            for (String path : commonPaths) {
                if (new File(path).exists()) {
                    return path;
                }
            }
        }
        throw new RuntimeException("在系统 PATH 和常见安装目录中均未检测到 'mysqldump'。请确保已正确安装 MySQL 并将其 bin 目录添加至系统 PATH 中。");
    }

    private boolean isCommandAvailable(String cmd) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{cmd, "--version"});
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String readStream(InputStream is) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        } catch (IOException e) {
            return "";
        }
    }

    private void zipDirectory(Path sourceFolder, ZipOutputStream zos, String folderInZip) throws IOException {
        Files.walkFileTree(sourceFolder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String relativePath = sourceFolder.relativize(file).toString().replace('\\', '/');
                String entryName = folderInZip + "/" + relativePath;
                zos.putNextEntry(new ZipEntry(entryName));
                Files.copy(file, zos);
                zos.closeEntry();
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());
        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();
        if (!destFilePath.startsWith(destDirPath + File.separator) && !destFilePath.equals(destDirPath)) {
            throw new IOException("发现非法的 Zip 压缩包路径越界攻击实体: " + zipEntry.getName());
        }
        return destFile;
    }

    private DbParams parseDbUrl(String dbUrl) {
        DbParams params = new DbParams();
        try {
            String cleanUrl = dbUrl.substring(dbUrl.indexOf("//") + 2);
            int slashIdx = cleanUrl.indexOf('/');
            int questionIdx = cleanUrl.indexOf('?');
            if (questionIdx != -1) {
                params.database = cleanUrl.substring(slashIdx + 1, questionIdx);
            } else {
                params.database = cleanUrl.substring(slashIdx + 1);
            }

            String hostAndPort = cleanUrl.substring(0, slashIdx);
            if (hostAndPort.contains(":")) {
                String[] parts = hostAndPort.split(":");
                params.host = parts[0];
                params.port = Integer.parseInt(parts[1]);
            } else {
                params.host = hostAndPort;
                params.port = 3306;
            }
        } catch (Exception e) {
            log.warn("Failed parsing jdbc url: {}", dbUrl, e);
        }
        return params;
    }

    private static class DbParams {
        String host = "localhost";
        int port = 3306;
        String database = "coderush_oj";
    }
}
