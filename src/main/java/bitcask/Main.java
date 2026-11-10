
package bitcask;

import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        Bitcask db = new Bitcask();
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Bitcask Key-Value Store ===");
        System.out.println("Commands: PUT, GET, DELETE, COMPACT, EXIT");

        while (true) {

            System.out.print("bitcask> ");
            String command = scanner.nextLine().trim();

            if (command.isEmpty()) {
                continue;
            }

            if (command.equalsIgnoreCase("EXIT")) {
                System.out.println("Exiting Bitcask...");
                break;
            }

            String[] parts = command.split("\\s+", 3);

            switch (parts[0].toUpperCase()) {

                case "PUT":
                    if (parts.length != 3) {
                        System.out.println("Usage: PUT <key> <value>");
                        break;
                    }

                    db.put(parts[1], parts[2]);
                    System.out.println("Stored successfully!");
                    break;

                case "GET":
                    if (parts.length != 2) {
                        System.out.println("Usage: GET <key>");
                        break;
                    }

                    String value = db.get(parts[1]);
                    System.out.println(value);
                    break;

                case "DELETE":
                    if (parts.length != 2) {
                        System.out.println("Usage: DELETE <key>");
                        break;
                    }

                    db.delete(parts[1]);
                    System.out.println("Delete command completed!");
                    break;

                case "COMPACT":
                    if (parts.length != 1) {
                        System.out.println("Usage: COMPACT");
                        break;
                    }

                    db.compact();
                    break;

                default:
                    System.out.println("Unknown command. Available commands: PUT, GET, DELETE, COMPACT, EXIT");
            }
        }

        scanner.close();
    }
}
