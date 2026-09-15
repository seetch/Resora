package dev.resora;

import com.google.gson.Gson;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class ResoraClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("resora");
    private static final long POLL_INTERVAL_MS = 500;
    private static final Gson GSON = new Gson();
    private static final Pattern CONNECTING_TO = Pattern.compile("Connecting to (.+), (\\d+)$");

    private static volatile ResoraClient instance;

    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "resora-worker");
        thread.setDaemon(true);
        return thread;
    });

    private Path downloadsDir;
    private Path downloadsLogFile;
    private Path minecraftLogFile;
    private Path outputDir;
    private Path configFile;
    private ResoraConfig config;
    private long downloadsLogPosition;
    private long minecraftLogPosition;
    private volatile String currentServerAddress;
    private volatile DownloadSnapshot latestDownload;
    private volatile String status = "Connect to a server and accept its resource pack.";

    private record FileInfo(String name, long size) {}

    private record DownloadLogEntry(String id, String url, String hash, FileInfo file, String error) {}

    private record DownloadSnapshot(Path source, DownloadLogEntry entry, String serverAddress) {}

    @Override
    public void onInitializeClient() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        downloadsDir = gameDir.resolve("downloads");
        downloadsLogFile = downloadsDir.resolve("log.json");
        minecraftLogFile = gameDir.resolve("logs").resolve("latest.log");
        outputDir = gameDir.resolve("resora-packs");
        configFile = gameDir.resolve("config").resolve("resora.json");
        config = ResoraConfig.load(configFile);
        downloadsLogPosition = fileSize(downloadsLogFile);
        minecraftLogPosition = fileSize(minecraftLogFile);
        instance = this;

        worker.scheduleWithFixedDelay(this::pollSafely, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        LOGGER.info("Resora active, watching {}", downloadsLogFile);
    }

    public static ResoraClient getInstance() {
        return instance;
    }

    public boolean hasLatestPack() {
        return latestDownload != null;
    }

    public String getCurrentServerAddress() {
        return currentServerAddress != null ? currentServerAddress : "Not connected";
    }

    public String getStatus() {
        return status;
    }

    public boolean isAutomaticSaving() {
        return config.automaticSaving;
    }

    public boolean isReplacingExisting() {
        return config.replaceExisting;
    }

    public boolean isNormalizingArchives() {
        return config.normalizeArchives;
    }

    public void saveLatestPack() {
        DownloadSnapshot snapshot = latestDownload;
        if (snapshot == null) {
            status = "No server resource pack is ready yet.";
            return;
        }
        status = "Saving resource pack...";
        worker.execute(() -> copyOut(snapshot));
    }

    public void toggleAutomaticSaving() {
        config.automaticSaving = !config.automaticSaving;
        saveConfigAsync();
    }

    public void toggleReplaceExisting() {
        config.replaceExisting = !config.replaceExisting;
        saveConfigAsync();
    }

    public void toggleNormalizeArchives() {
        config.normalizeArchives = !config.normalizeArchives;
        saveConfigAsync();
    }

    private void saveConfigAsync() {
        worker.execute(() -> config.save(configFile));
    }

    private void pollSafely() {
        try {
            minecraftLogPosition = readNewLines(minecraftLogFile, minecraftLogPosition, line -> {
                String address = extractConnectingTo(line);
                if (address != null) {
                    currentServerAddress = address;
                    latestDownload = null;
                    status = "Waiting for the server resource pack.";
                }
            });
            downloadsLogPosition = readNewLines(downloadsLogFile, downloadsLogPosition, this::handleLine);
        } catch (IOException e) {
            status = "Could not read the Minecraft download log.";
            LOGGER.warn("Could not read log file: {}", e.getMessage());
        }
    }

    private static String extractConnectingTo(String line) {
        Matcher matcher = CONNECTING_TO.matcher(line);
        if (!matcher.find()) return null;
        return matcher.group(1) + ":" + matcher.group(2);
    }

    private static long fileSize(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.size(file) : 0;
        } catch (IOException ignored) {
            return 0;
        }
    }

    private static long readNewLines(Path file, long position, java.util.function.Consumer<String> consumer)
            throws IOException {
        if (!Files.isRegularFile(file)) return 0;

        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            long safePosition = position <= input.length() ? position : 0;
            input.seek(safePosition);
            String rawLine;
            while ((rawLine = input.readLine()) != null) {
                consumer.accept(new String(rawLine.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
            }
            return input.getFilePointer();
        }
    }

    private void handleLine(String line) {
        if (line.isBlank()) return;

        DownloadLogEntry entry;
        try {
            entry = GSON.fromJson(line, DownloadLogEntry.class);
        } catch (Exception e) {
            LOGGER.warn("Could not parse download log line: {}", e.getMessage());
            return;
        }
        if (entry == null || entry.file() == null
                || entry.error() != null && !entry.error().isBlank()) return;

        Path source = downloadsDir.resolve(entry.file().name().replace('\\', '/')).normalize();
        if (!source.startsWith(downloadsDir.normalize())) {
            LOGGER.warn("Ignoring download outside the Minecraft cache: {}", source);
            return;
        }

        DownloadSnapshot snapshot = new DownloadSnapshot(source, entry, currentServerAddress);
        latestDownload = snapshot;
        status = currentServerAddress != null
                ? "Resource pack ready to save."
                : "Pack cached, but the server address was not detected.";

        if (config.automaticSaving && currentServerAddress != null) {
            copyOut(snapshot);
        }
    }

    private void copyOut(DownloadSnapshot snapshot) {
        Path source = snapshot.source();
        String serverAddress = snapshot.serverAddress();
        if (serverAddress == null) {
            status = "Could not determine the server address.";
            return;
        }

        if (!Files.isRegularFile(source)) {
            status = "The cached resource pack is no longer available.";
            return;
        }

        Path target = outputDir.resolve(sanitizeFileName(serverAddress) + ".zip");
        if (Files.exists(target) && !config.replaceExisting) {
            status = "A resource pack for this server is already saved.";
            return;
        }

        try {
            Files.createDirectories(outputDir);
            if (config.normalizeArchives) {
                saveNormalized(source, target);
            } else {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            status = "Saved " + target.getFileName() + ".";
            LOGGER.info("Saved resource pack copy from {} ({}): {}", snapshot.entry().url(), serverAddress, target);
        } catch (IOException e) {
            status = "Could not save the resource pack.";
            LOGGER.warn("Could not copy resource pack {}: {}", source, e.getMessage());
        }
    }

    private void saveNormalized(Path source, Path target) throws IOException {
        Path tmp = Files.createTempFile(target.getParent(), "resora-", ".zip");
        try {
            int entries;
            try (ZipFile zipFile = new ZipFile(source.toFile())) {
                entries = rewriteZip(zipFile, tmp);
            }
            if (entries == 0) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Could not normalize zip structure for {}, saving raw copy: {}", source, e.getMessage());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private int rewriteZip(ZipFile zipFile, Path tmp) throws IOException {
        Map<String, ZipEntry> winners = new LinkedHashMap<>();
        Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
        while (zipEntries.hasMoreElements()) {
            ZipEntry entry = zipEntries.nextElement();
            winners.put(entry.getName(), entry);
        }

        try (ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(tmp))) {
            for (ZipEntry entry : winners.values()) {
                zout.putNextEntry(new ZipEntry(entry.getName()));
                try (InputStream in = zipFile.getInputStream(entry)) {
                    in.transferTo(zout);
                }
                zout.closeEntry();
            }
        }
        return winners.size();
    }

    private static String sanitizeFileName(String address) {
        return address.replaceAll("[^a-zA-Z0-9.\\-]", "_");
    }

    private static final class ResoraConfig {
        private volatile boolean automaticSaving = true;
        private volatile boolean replaceExisting;
        private volatile boolean normalizeArchives = true;

        private static ResoraConfig load(Path file) {
            if (!Files.isRegularFile(file)) return new ResoraConfig();

            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                ResoraConfig loaded = GSON.fromJson(reader, ResoraConfig.class);
                return loaded != null ? loaded : new ResoraConfig();
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Could not load config {}: {}", file, e.getMessage());
                return new ResoraConfig();
            }
        }

        private void save(Path file) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.warn("Could not save config {}: {}", file, e.getMessage());
            }
        }
    }
}
