package bitcask;

import java.util.HashMap;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class Bitcask {

    private HashMap<String, KeyDirEntry> keyDir = new HashMap<>();

    private static final String DATA_FILE = "data/data.bin";

    public Bitcask() {

        // Rebuild KeyDir by scanning the data file
        try (RandomAccessFile file = new RandomAccessFile(DATA_FILE, "rw")) {

            while (file.getFilePointer() < file.length()) {

                long recordStart = file.getFilePointer();
                long remaining = file.length() - file.getFilePointer();

                if (remaining < 8) {
                    file.setLength(recordStart);
                    break;
                }

                int keyLength = file.readInt();
                int valueLength = file.readInt();

                if (keyLength <= 0 || valueLength < -1) {
                    file.setLength(recordStart);
                    break;
                }

                long bytesAfterHeader = file.length() - file.getFilePointer();

                long requiredBytes = (long) keyLength + (valueLength == -1 ? 0L : (long) valueLength);

                if (bytesAfterHeader < requiredBytes) {
                    file.setLength(recordStart);
                    break;
                }

                byte[] keyBytes = new byte[keyLength];
                file.readFully(keyBytes);

                String key = new String(keyBytes, StandardCharsets.UTF_8);

                if (valueLength == -1) {
                    // Tombstone: remove deleted key
                    keyDir.remove(key);

                } else {
                    // Remember where the value starts
                    long valuePosition = file.getFilePointer();

                    keyDir.put(
                        key,
                        new KeyDirEntry(valuePosition, valueLength)
                    );

                    // Skip value bytes to reach the next record
                    file.seek(valuePosition + valueLength);
                }
            }

        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    // Store a key-value pair
    public void put(String key, String value) {

        try (RandomAccessFile file = new RandomAccessFile(DATA_FILE, "rw")) {

            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);

            file.seek(file.length());

            file.writeInt(keyBytes.length);
            file.writeInt(valueBytes.length);

            file.write(keyBytes);

            long valuePosition = file.getFilePointer();

            file.write(valueBytes);

            keyDir.put(
                key,
                new KeyDirEntry(valuePosition, valueBytes.length)
            );

        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    // Retrieve a value using its position in the file
    public String get(String key) {

        KeyDirEntry entry = keyDir.get(key);

        if (entry == null) {
            return null;
        }

        try (RandomAccessFile file = new RandomAccessFile(DATA_FILE, "r")) {

            file.seek(entry.getValuePosition());

            byte[] valueBytes = new byte[entry.getValueSize()];

            file.readFully(valueBytes);

            return new String(valueBytes, StandardCharsets.UTF_8);

        } catch (IOException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    // Delete a key using a tombstone
    public void delete(String key) {

        try (RandomAccessFile file = new RandomAccessFile(DATA_FILE, "rw")) {

            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);

            file.seek(file.length());

            file.writeInt(keyBytes.length);
            file.writeInt(-1);

            file.write(keyBytes);

            keyDir.remove(key);

        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    
    public void compact() {

        String tempFile = "data/compacted.bin";
        HashMap<String, KeyDirEntry> newKeyDir = new HashMap<>();

        try {

            // Open both files
            try (
                RandomAccessFile compacted =
                    new RandomAccessFile(tempFile, "rw");

                RandomAccessFile original =
                    new RandomAccessFile(DATA_FILE, "r")
            ) {

                compacted.setLength(0);

                // Copy only the latest records
                for (String key : keyDir.keySet()) {

                    KeyDirEntry entry = keyDir.get(key);

                    original.seek(entry.getValuePosition());

                    byte[] valueBytes = new byte[entry.getValueSize()];

                    original.readFully(valueBytes);

                    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);

                    compacted.writeInt(keyBytes.length);
                    compacted.writeInt(valueBytes.length);

                    compacted.write(keyBytes);

                    long newValuePosition = compacted.getFilePointer();

                    compacted.write(valueBytes);

                    newKeyDir.put(key, new KeyDirEntry(newValuePosition,valueBytes.length));
                }

            } // Files are closed here

            // Replace original file after successful copying
            Path source = Path.of(tempFile);
            Path destination = Path.of(DATA_FILE);

            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);

            // Update KeyDir with new positions
            keyDir.clear();
            keyDir.putAll(newKeyDir);

            System.out.println("Compaction successful!");

        } catch (IOException e) {
            System.out.println("Compaction failed: " + e.getMessage());
        }
    }
}