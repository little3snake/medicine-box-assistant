package kz.medbox.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kz.medbox.core.Model.ExtractedFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Appends one JSON line per vision call to logs/vision.jsonl:
 * time, model, latency, HTTP status, whether the schema was used, extracted fields, token usage,
 * and the SHA-256 of the image (so a logged reply can be matched to an eval photo without storing the photo).
 * Never logs the API key. logs/ is in .gitignore.
 */
@Component
public class VisionLog {

    private static final Logger log = LoggerFactory.getLogger(VisionLog.class);

    private final Path file;
    private final ObjectMapper mapper;

    public VisionLog(@Value("${medbox.log-dir:logs}") String dir, ObjectMapper mapper) {
        this.file = Path.of(dir, "vision.jsonl");
        this.mapper = mapper;
    }

    public synchronized void write(byte[] image, String model, long ms, int status, boolean schemaUsed,
                                   ExtractedFields fields, JsonNode usage) {
        try {
            ObjectNode n = mapper.createObjectNode();
            n.put("time", Instant.now().toString());
            n.put("model", model);
            n.put("ms", ms);
            n.put("status", status);
            n.put("schemaUsed", schemaUsed);
            n.put("imageSha256", sha256(image));
            if (fields != null) n.set("fields", mapper.valueToTree(fields));
            if (usage != null && !usage.isMissingNode()) n.set("usage", usage);
            Files.createDirectories(file.getParent());
            Files.writeString(file, mapper.writeValueAsString(n) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Could not write vision log: {}", e.getMessage()); // logging must never break a request
        }
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            return "unavailable";
        }
    }
}
