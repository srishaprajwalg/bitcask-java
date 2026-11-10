# Bitcask Java – Persistent Key-Value Storage Engine

A lightweight, persistent key-value storage engine implemented from scratch in **Java 21**, inspired by the [Bitcask paper](https://riak.com/assets/bitcask-intro.pdf).

The engine uses a log-structured storage architecture with an append-only binary log on disk and an in-memory hash index (`KeyDir`). KeyDir lookups have average **O(1)** time complexity, while actual read and write operations also involve disk I/O.

## Architecture Overview

The engine combines in-memory indexing with persistent disk storage.

```text
                  +---------------------------+
                  |          Main CLI         |
                  |     PUT / GET / DELETE     |
                  |         COMPACT            |
                  +-------------+-------------+
                                |
                                v
                  +---------------------------+
                  |      Bitcask Engine       |
                  +-------------+-------------+
                                |
                +---------------+---------------+
                |                               |
                v                               v
       +-------------------+          +----------------------+
       |   In-Memory       |          |   Append-Only Log    |
       |     KeyDir        |          |      data.bin        |
       |                   |          |                      |
       | Key -> Position   |--------->| Binary Records       |
       |       -> Size     |          | Tombstone Records    |
       +-------------------+          +----------------------+
```

### Main Components

1. **Append-Only Log:** All inserts, updates and deletions are appended to `data/data.bin` using Java's `RandomAccessFile`. Existing records are not modified in place.

2. **In-Memory KeyDir:** A `HashMap<String, KeyDirEntry>` stores each active key's value position and size in the data file.

3. **Direct Reads:** GET operations look up the key in KeyDir and seek directly to the corresponding value in the data file rather than scanning the entire log.

## Binary Record Format

Records are stored in binary format using big-endian integers and UTF-8 encoding.

### Standard Record (PUT)

```text
+--------------------+--------------------+------------------+-------------------+
| keyLength (4 bytes)| valueLength (4 B)  | keyBytes         | valueBytes        |
+--------------------+--------------------+------------------+-------------------+
                                          UTF-8              UTF-8
```

### Tombstone Record (DELETE)

```text
+--------------------+--------------------+------------------+
| keyLength (4 bytes)| valueLength = -1    | keyBytes         |
+--------------------+--------------------+------------------+
                                          UTF-8
```

Each record contains:

- **keyLength:** 4-byte integer representing the key's byte length.
- **valueLength:** 4-byte integer representing the value's byte length.
- **keyBytes:** UTF-8 encoded key.
- **valueBytes:** UTF-8 encoded value. Not present in tombstone records.
- **Tombstone:** A special record with `valueLength = -1`, indicating that the key has been deleted.

KeyDir stores the file offset where the value bytes begin, allowing direct retrieval.

## Core Operations

### 1. PUT – Insert or Update

- Converts the key and value into UTF-8 byte arrays.
- Appends a new record to the end of the log.
- Updates KeyDir with the new value position and size.
- If the key already exists, the previous record remains in the log while KeyDir points to the latest record.

### 2. GET – Retrieve

- Looks up the key in KeyDir using a HashMap.
- If the key exists, retrieves its stored file position and value size.
- Seeks directly to that position in the log file.
- Reads and returns the value.
- Returns `null` if the key does not exist.

### 3. DELETE – Remove

- Appends a tombstone record to the log.
- Removes the key from KeyDir.
- Future GET operations return `null` for that key.
- The old record remains on disk until compaction.

### 4. COMPACT – Reclaim Storage

Repeated updates and deletions leave obsolete records in the append-only log.

Compaction reclaims this space by:

1. Creating a temporary compacted file.
2. Reading the latest active values using KeyDir.
3. Writing only active key-value records into the temporary file.
4. Replacing the original data file with the compacted file.
5. Rebuilding KeyDir with the new value positions.

This removes obsolete versions and deleted records from the log.

## Startup Recovery

The in-memory KeyDir is not persisted separately. Instead, it is reconstructed whenever the application starts.

The recovery process:

1. Opens the existing data file.
2. Sequentially scans records from the beginning.
3. Reads each record's header, key and value information.
4. Updates KeyDir when a key-value record is encountered.
5. Removes keys from KeyDir when their tombstones are encountered.
6. Detects incomplete trailing records and truncates the log to the beginning of the incomplete record.

This allows the application to rebuild its in-memory index from the persistent log after restarting.

**Recovery limitation:** The implementation handles incomplete trailing records. It does not provide general protection against arbitrary corruption within earlier records.

## Technology Stack

| Component | Technology |
|---|---|
| Programming Language | Java 21 |
| File I/O | RandomAccessFile |
| In-Memory Index | HashMap |
| Binary Serialization | UTF-8 and Java primitive types |
| File Operations | Java NIO |
| Build | Java compiler (`javac`) |

**Dependencies:** None. The project uses standard Java libraries.

## Project Structure

```text
bitcask-java/
├── src/
│   └── main/
│       └── java/
│           └── bitcask/
│               ├── Main.java
│               ├── Bitcask.java
│               └── KeyDirEntry.java
├── data/
│   └── data.bin
├── out/
├── .gitignore
└── README.md
```

- `Main.java` – Interactive command-line interface.
- `Bitcask.java` – Core storage engine implementation.
- `KeyDirEntry.java` – Stores value position and size information.
- `data/data.bin` – Persistent append-only data file.
- `out/` – Compiled Java classes.

## Setup and Execution

### Prerequisites

- Java Development Kit (JDK) 21 or higher.

Check your Java installation:

```bash
java -version
javac -version
```

### Compile

Run from the project root:

```bash
javac -d out src/main/java/bitcask/*.java
```

### Run

```bash
java -cp out bitcask.Main
```

## Example CLI Session

```text
=== Bitcask Key-Value Store ===
Commands: PUT, GET, DELETE, COMPACT, EXIT

bitcask> PUT username alice
Stored successfully!

bitcask> GET username
alice

bitcask> PUT username alice_v2
Stored successfully!

bitcask> GET username
alice_v2

bitcask> DELETE username
Delete command completed!

bitcask> GET username
null

bitcask> COMPACT
Compaction successful!

bitcask> EXIT
Exiting Bitcask...
```

## Current Limitations

This is an educational implementation focused on understanding the fundamental concepts behind log-structured key-value storage.

1. **Single Log File:** Uses one data file instead of multiple immutable log segments as in the original Bitcask architecture.
2. **RAM-Bounded Key Space:** KeyDir stores all active keys and their positions in memory. Memory usage grows with the number of unique keys.
3. **Single-Process Access:** No synchronization or file locking is implemented for concurrent access from multiple threads or processes.
4. **No Checksums:** Records do not include CRC checksums for detecting arbitrary data corruption.
5. **No Explicit Disk Synchronization:** Writes do not explicitly call `fsync` or `FileDescriptor.sync()`, so strict power-loss durability is not guaranteed.
6. **Compaction Concurrency:** Compaction is designed for single-process use and does not protect against concurrent reads or writes during file replacement.
7. **CLI Input Constraints:** The whitespace-based command parser does not support keys containing spaces.

## Learning Outcomes

Through this project, I explored:

- Append-only and log-structured storage.
- Binary serialization and file I/O in Java.
- In-memory indexing using HashMaps.
- File offsets and random-access reads.
- Tombstone-based deletion.
- Startup recovery and incomplete-record handling.
- Log compaction and storage reclamation.

---

**Project:** Bitcask-inspired Key-Value Storage Engine  
**Language:** Java 21