 <div align="center">

# Bitcask Java

### A Log-Structured Key-Value Storage Engine

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://www.oracle.com/java/)
[![Storage](https://img.shields.io/badge/Storage-Log--Structured-blue)]()
[![Status](https://img.shields.io/badge/Status-Completed-success)]()

A lightweight persistent key-value store inspired by the Bitcask storage model, implemented from scratch using Java 21.

</div>

---

## Overview

Bitcask Java is a log-structured key-value storage engine that stores data in an append-only binary log and maintains an in-memory hash table for efficient key lookups.

Instead of modifying existing records, every update is appended as a new record. An in-memory index called the **KeyDir** tracks the location of the latest value for each key.

The project demonstrates fundamental storage-engine concepts, including append-only logging, indexing, tombstone-based deletion, startup recovery, and log compaction.

## Features

| Feature | Description |
|---|---|
| PUT | Insert or update key-value pairs |
| GET | Retrieve values using the in-memory index |
| DELETE | Delete keys using tombstone records |
| Append-only storage | Store updates as new records rather than overwriting existing data |
| In-memory KeyDir | Maintain key-to-file-position mappings using a HashMap |
| Startup recovery | Rebuild the KeyDir by scanning the data file |
| Crash-tail recovery | Detect and truncate incomplete trailing records |
| Compaction | Remove obsolete records and reclaim disk space |
| CLI | Interact with the storage engine through a command-line interface |
| Benchmarking | Measure PUT and GET throughput across multiple trials |

## Architecture

Bitcask uses an append-only log for persistent storage and an in-memory KeyDir for fast lookups.

```text
                 Client / CLI
                      |
                      v
                Bitcask Engine
                      |
          +-----------+-----------+
          |                       |
          v                       v
       PUT / DELETE              GET
          |                       |
          v                       v
     Append Record           Consult KeyDir
          |                       |
          v                       v
      data.bin              Find Value Position
          |                       |
          v                       v
     Update KeyDir            Read Value
          |
          v
    In-memory HashMap
```

### KeyDir

The KeyDir is an in-memory `HashMap<String, KeyDirEntry>` that maintains the location of the latest value for each key.

Each entry stores:

- Value position in the data file.
- Value size in bytes.

This allows the engine to locate values directly without scanning the entire data file for every GET operation.

HashMap lookups have average-case O(1) time complexity.

### Data File Format

All records are stored in a binary append-only file named `data.bin`.

Each record follows this structure:

```text
+-------------------+
| Key Length (4 B)  |
+-------------------+
| Value Length (4 B)|
+-------------------+
| Key Bytes         |
+-------------------+
| Value Bytes       |
+-------------------+
```

- Key Length: Size of the key in bytes.
- Value Length: Size of the value in bytes.
- Key Bytes: UTF-8 encoded key.
- Value Bytes: UTF-8 encoded value.

The length fields are stored as 4-byte integers.

### Tombstone-Based Deletion

Deleting a key does not physically remove its previous record.

Instead, the engine appends a special tombstone record with a value length of `-1`.

```text
Key Length  | -1 | Key Bytes
```

When the engine encounters a tombstone during recovery, it removes that key from the KeyDir.

This ensures that deleted values do not reappear after restarting the application.

## Core Operations

### PUT

1. Convert the key and value into UTF-8 byte arrays.
2. Append a new record to the data file.
3. Record the value's position and size in the KeyDir.

Updating an existing key appends a new record, leaving the older record untouched.

### GET

1. Look up the key in the KeyDir.
2. Retrieve its stored file position and value size.
3. Seek directly to that position in the data file.
4. Read and return the value.

### DELETE

1. Append a tombstone record to the data file.
2. Remove the key from the in-memory KeyDir.

### Startup Recovery

When the engine starts, it scans the data file sequentially and rebuilds the KeyDir.

For each record, it:

1. Reads the key and value lengths.
2. Validates that the record contains the required bytes.
3. Reads the key.
4. Updates the KeyDir with the latest value position, or removes the key if it encounters a tombstone.

If an incomplete trailing record is detected, the file is truncated from that record's starting position.

### Compaction

Since updates are appended instead of overwriting existing records, the data file can accumulate obsolete versions of keys and deleted records.

Compaction creates a new file containing only the latest active values.

```text
Before Compaction:

data.bin
+------------------+
| name = Rahul     |
| city = Bengaluru |
| name = Prajwal   |
| language = Java  |
| DELETE language  |
+------------------+

After Compaction:

compacted.bin
+------------------+
| name = Prajwal   |
| city = Bengaluru |
+------------------+
```

The compacted file replaces the original data file, and the KeyDir is rebuilt with the new value positions.

## Benchmark Results

A local performance benchmark was conducted to measure PUT and GET throughput.

The benchmark uses JVM warm-up, 10 measured trials, and correctness verification for GET operations.

| Operation | Average Throughput | Median Time |
|---|---:|---:|
| PUT | ~54,811 ops/sec | 0.176 seconds |
| GET | ~105,795 ops/sec | 0.092 seconds |

### Benchmark Configuration

- Records per trial: 10,000
- Warm-up: 2,000 PUT and GET operations
- Measured trials: 10
- Timing mechanism: `System.nanoTime()`
- GET operations: Results verified against expected values
- Runtime: Java 21
- Environment: Ubuntu Linux

*Note: These are local benchmark measurements, not comparisons against established databases. Results depend on the machine, JVM, storage device, and workload. File open/close overhead is included in each operation.*

## Technology Stack

| Technology | Purpose |
|---|---|
| Java 21 | Core implementation |
| HashMap | In-memory KeyDir |
| RandomAccessFile | Binary file operations and random access |
| NIO Files API | File replacement during compaction |
| System.nanoTime() | Benchmark timing |

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
├── out/
├── .gitignore
└── README.md
```

## Getting Started

### Prerequisites

- Java Development Kit (JDK) 21 or later
- Linux, macOS, or Windows

### Clone the Repository

```bash
git clone https://github.com/srishaprajwalg/bitcask-java.git
cd bitcask-java
```

### Compile

```bash
javac -d out src/main/java/bitcask/*.java
```

### Run

```bash
java -cp out bitcask.Main
```

The application stores its data in `data/data.bin`.

## CLI Usage

The command-line interface supports the following operations:

```text
PUT name Prajwal
GET name
DELETE name
COMPACT
EXIT
```

Example:

```text
> PUT name Prajwal
Stored successfully!

> GET name
Prajwal

> DELETE name
Deleted successfully!

> GET name
null

> COMPACT
Compaction successful!
```

## Running the Benchmark

The benchmark should be run from an isolated working directory to avoid modifying the application's normal database.

Compile from the project root:

```bash
javac -d out src/main/java/bitcask/*.java
```

Create a separate benchmark directory:

```bash
mkdir -p /tmp/bitcask-benchmark/data
cd /tmp/bitcask-benchmark
```

Run:

```bash
java -cp ~/bitcask-java/out bitcask.Benchmark
```

## Current Limitations

This project focuses on demonstrating core Bitcask storage concepts rather than production-grade database reliability.

Current limitations include:

- The KeyDir is maintained in memory, so memory usage grows with the number of keys.
- The implementation is designed for single-process, single-writer usage.
- No concurrent write coordination or locking is implemented.
- No record checksums are used for detecting arbitrary data corruption.
- Writes are not explicitly synchronized to disk using `fsync`.
- Compaction does not provide full production-grade crash-safety guarantees.

## Learning Outcomes

Through this project, I explored and implemented:

- Append-only log-based storage.
- In-memory indexing and direct file access.
- Binary serialization and deserialization.
- Tombstone-based logical deletion.
- Startup recovery and incomplete-record handling.
- Log compaction and stale-record removal.
- Basic performance benchmarking in Java.

---

<div align="center">

**Built with Java 21**

Inspired by the [Bitcask paper](https://riak.com/assets/bitcask-intro.pdf).

</div>