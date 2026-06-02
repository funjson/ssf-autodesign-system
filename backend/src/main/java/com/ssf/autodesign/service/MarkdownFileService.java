package com.ssf.autodesign.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class MarkdownFileService {
    public String readUtf8(Path path, List<String> warnings) {
        if (!Files.exists(path)) {
            warnings.add("missing: " + path);
            return "";
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            warnings.add("failed to read: " + path + " - " + ex.getMessage());
            return "";
        }
    }

    public List<String> readUtf8Lines(Path path, List<String> warnings) {
        if (!Files.exists(path)) {
            warnings.add("missing: " + path);
            return List.of();
        }
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            warnings.add("failed to read: " + path + " - " + ex.getMessage());
            return List.of();
        }
    }
}