# Bitcask Java — Persistent Key-Value Storage Engine

<div align="center">

  <h3>A lightweight, log-structured key-value storage engine built from scratch using Java 21.</h3>

  <p>
    Inspired by the <a href="https://riak.com/assets/bitcask-intro.pdf">Bitcask paper</a>,
    this project explores append-only storage, in-memory indexing, crash recovery, and log compaction.
  </p>

  <p>
    <img src="https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21"/>
    <img src="https://img.shields.io/badge/Storage-Log--Structured-blue?style=for-the-badge" alt="Log Structured"/>
    <img src="https://img.shields.io/badge/Dependencies-None-success?style=for-the-badge" alt="No Dependencies"/>
  </p>

  <p>
    <strong>Append-only Log</strong> &nbsp;•&nbsp;
    <strong>HashMap Index</strong> &nbsp;•&nbsp;
    <strong>Tombstone Deletion</strong> &nbsp;•&nbsp;
    <strong>Log Compaction</strong>
  </p>

</div>

---

## Overview

**Bitcask Java** is a persistent key-value storage engine implemented from scratch in Java 21.

Instead of modifying values in place, the engine appends every insert, update, and deletion to a binary log file. An in-memory `HashMap`, called the **KeyDir**, maintains the latest location of each active key, allowing efficient lookups without scanning the entire log.

The project demonstrates fundamental concepts behind log-structured storage systems and database internals.

## Features

<table>
  <tr>
    <td width="50%">
      <h3>Append-Only Storage</h3>
      <p>Persists records sequentially in a binary log, avoiding in-place updates.</p>
    </td>
    <td width="50%">
      <h3>In-Memory Indexing</h3>
      <p>Uses a HashMap-based KeyDir for average O(1) key lookups.</p>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <h3>Persistent Operations</h3>
      <p>Supports PUT, GET, and DELETE operations with disk-backed storage.</p>
    </td>
    <td width="50%">
      <h3>Tombstone Deletion</h3>
      <p>Represents deletions using special records in the append-only log.</p>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <h3>Startup Recovery</h3>
      <p>Reconstructs the in-memory index by scanning the log during startup.</p>
    </td>
    <td width="50%">
      <h3>Log Compaction</h3>
      <p>Reclaims disk space by rewriting only the latest active records.</p>
    </td>
  </tr>
</table>

## Architecture

The engine combines an in-memory index with persistent disk storage.

```text
                  +---------------------------+
                  |          Main CLI         |
                  |     PUT / GET / DELETE     |
                  |          COMPACT           |
                  +-------------+-------------+
                                |
                                v
                  +---------------------------+
                  |       Bitcask Engine       |
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

### KeyDir

The KeyDir is an in-memory `HashMap<String, KeyDirEntry>`.

For each active key, it stores:

- `valuePosition` — byte offset where the value begins in the data file.
- `valueSize` — number of bytes occupied by the value.

When a GET operation is performed, the engine looks up the key in KeyDir and seeks directly to its value position in the file.

This avoids sequentially scanning the entire data file for every read.

## Binary Record Format

Records are persisted in binary format using big-endian integers and UTF-8 encoded keys and values.

### Standard Record — PUT

```text
+--------------------+--------------------+------------------+-------------------+
| keyLength (4 bytes)| valueLength (4 B)  | keyBytes         | valueBytes        |
+--------------------+--------------------+------------------+-------------------+
                                          UTF-8              UTF-8
```

### Tombstone Record — DELETE

```text
+--------------------+--------------------+------------------+
| keyLength (4 bytes)| valueLength = -1    | keyBytes         |
+--------------------+--------------------+------------------+
                                          UTF-8
```

Each record contains:

| Field | Description |
|---|---|
| `keyLength` | 4-byte integer representing the key's byte length |
| `valueLength` | 4-byte integer representing the value's byte length |
| `keyBytes` | UTF-8 encoded key |
| `valueBytes` | UTF-8 encoded value |
| Tombstone | Special record with `valueLength = -1` |

Tombstones do not contain value bytes. They indicate that the corresponding key has been deleted.

## Core Operations

### 1. PUT — Insert or Update

- Converts the key and value into UTF-8 byte arrays.
- Appends a new record to the end of the log.
- Updates KeyDir with the latest value position and size.
- Previous versions remain in the log until compaction.

### 2. GET — Retrieve

- Looks up the requested key in KeyDir.
- Retrieves its stored value position and size.
- Seeks directly to the corresponding position in the data file.
- Reads and returns the value.
- Returns `null` if the key does not exist.

### 3. DELETE — Remove

- Appends a tombstone record to the log.
- Removes the key from KeyDir.
- Subsequent GET operations return `null`.
- The old record remains on disk until compaction.

### 4. COMPACT — Reclaim Storage

Repeated updates and deletions leave obsolete records in the append-only log.

Compaction reclaims storage by:

1. Creating a temporary compacted file.
2. Reading the latest active values using KeyDir.
3. Writing only active key-value records into the temporary file.
4. Replacing the original data file with the compacted file.
5. Rebuilding KeyDir with the new value positions.

This eliminates obsolete versions and deleted records from the log.

## Startup Recovery

The in-memory KeyDir is not persisted separately. Instead, it is reconstructed whenever the application starts.

The recovery process:

1. Opens the existing data file.
2. Sequentially scans records from the beginning.
3. Reads each record's header, key, and value information.
4. Updates KeyDir when a key-value record is encountered.
5. Removes keys from KeyDir when their tombstones are encountered.
6. Detects incomplete trailing records and truncates the log to the beginning of the incomplete record.

This allows the engine to rebuild its in-memory index from persistent data after restarting.

<blockquote>
  <strong>Recovery limitation:</strong> The implementation handles incomplete trailing records but does not provide general protection against arbitrary corruption within earlier records.
</blockquote>

## Benchmark Results

A basic performance benchmark was conducted using 10,000 key-value records per trial, with JVM warm-up followed by three measured trials.

<div align="center">

  <h3>Average Performance</h3>

  <table>
    <thead>
      <tr>
        <th>Operation</th>
        <th>Average Time</th>
        <th>Throughput</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td><strong>PUT</strong></td>
        <td>0.190 seconds</td>
        <td><strong>~52,524 ops/sec</strong></td>
      </tr>
      <tr>
        <td><strong>GET</strong></td>
        <td>0.098 seconds</td>
        <td><strong>~101,559 ops/sec</strong></td>
      </tr>
    </tbody>
  </table>

</div>

### Benchmark Configuration

| Parameter | Value |
|---|---|
| Records per trial | 10,000 |
| Measured trials | 3 |
| Warm-up | 2,000 PUT and GET operations |
| Time measurement | `System.nanoTime()` |
| Environment | Java 21 on Ubuntu Linux |

<sub>Results are from a basic local benchmark and may vary depending on hardware, filesystem, JVM behavior, and system conditions. These measurements are not a comparison against other databases.</sub>

## Technology Stack

<div align="center">

  <img src="https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk" alt="Java"/>
  <img src="https://img.shields.io/badge/Java%20NIO-File%20Operations-blue?style=flat-square" alt="Java NIO"/>
  <img src="https://img.shields.io/badge/HashMap-In--Memory%20Index-green?style=flat-square" alt="HashMap"/>
  <img src="https://img.shields.io/badge/Build-javac-lightgrey?style=flat-square" alt="javac"/>

</div>

| Component | Technology |
|---|---|
| Programming Language | Java 21 |
| File I/O | `RandomAccessFile` |
| In-Memory Index | `HashMap` |
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
│               ├── KeyDirEntry.java
│               └── Benchmark.java
├── data/
│   └── data.bin
├── out/
├── .gitignore
└── README.md
```

| File | Responsibility |
|---|---|
| `Main.java` | Interactive command-line interface |
| `Bitcask.java` | Core storage engine implementation |
| `KeyDirEntry.java` | Stores value position and size |
| `Benchmark.java` | Measures PUT and GET performance |
| `data/data.bin` | Persistent append-only data file |

## Getting Started

### Prerequisites

- Java Development Kit (JDK) 21 or higher.

Verify your installation:

```bash
java -version
javac -version
```

### Clone the Repository

```bash
git clone https://github.com/srishaprajwalg/bitcask-java.git
cd bitcask-java
```

### Compile

Run from the project root:

```bash
javac -d out src/main/java/bitcask/*.java
```

### Run the Application

```bash
java -cp out bitcask.Main
```

### Run the Benchmark

```bash
java -cp out bitcask.Benchmark
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
6. **Compaction Concurrency:** Compaction does not protect against concurrent reads or writes during file replacement.
7. **CLI Input Constraints:** The whitespace-based command parser does not support keys containing spaces.

## Learning Outcomes

Through this project, I explored:

- Append-only and log-structured storage architectures.
- Binary serialization and file I/O in Java.
- In-memory indexing using HashMaps.
- File offsets and random-access reads.
- Tombstone-based deletion.
- Startup recovery and incomplete-record handling.
- Log compaction and storage reclamation.
- Basic performance benchmarking.

---

<div align="center">

  <h3>Built with Java 21</h3>

  <p>
    <strong>Bitcask-inspired Persistent Key-Value Storage Engine</strong>
  </p>

  <p>
    <a href="https://github.com/srishaprajwalg/bitcask-java">View Repository</a>
  </p>

</div>