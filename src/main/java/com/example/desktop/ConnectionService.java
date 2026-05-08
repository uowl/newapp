package com.example.desktop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ConnectionService {

    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.emr-workspace";
    private static final String FILE_PATH = CONFIG_DIR + "/connections.json";
    private static final String ALGO = "AES";
    // In a real app, this would be derived from machine-specific IDs
    private static final byte[] KEY = "EMR-WORKSPACE-SECRET-KEY-1234567".substring(0, 16).getBytes(StandardCharsets.UTF_8);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ConnectionService() {
        File dir = new File(CONFIG_DIR);
        if (!dir.exists()) dir.mkdirs();
    }

    public List<SavedConnection> getConnections() {
        File file = new File(FILE_PATH);
        if (!file.exists()) return new ArrayList<>();
        try {
            List<SavedConnection> list = MAPPER.readValue(file, new TypeReference<List<SavedConnection>>() {});
            return list;
        } catch (IOException e) {
            System.err.println("Failed to read connections: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public void saveConnections(List<SavedConnection> connections) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(FILE_PATH), connections);
        } catch (IOException e) {
            System.err.println("Failed to save connections: " + e.getMessage());
        }
    }

    public String encrypt(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            SecretKeySpec skeySpec = new SecretKeySpec(KEY, ALGO);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
            byte[] encrypted = cipher.doFinal(value.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            ex.printStackTrace();
            return "";
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) return "";
        try {
            SecretKeySpec skeySpec = new SecretKeySpec(KEY, ALGO);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);
            byte[] original = cipher.doFinal(Base64.getDecoder().decode(encrypted));
            return new String(original);
        } catch (Exception ex) {
            return "";
        }
    }

    public static record SavedConnection(
            String id,
            String name,
            String hostname,
            String port,
            String username,
            String encryptedPassword,
            String defaultDatabase
    ) {}
}
