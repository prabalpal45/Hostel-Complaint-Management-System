import server.Server;

import java.io.IOException;

/**
 * Main
 *
 * Primary application entry point. Starts the Core Java HttpServer backend
 * connected to MySQL on port 8080.
 */
public class Main {

    public static void main(String[] args) throws IOException {
        System.out.println("=================================================");
        System.out.println("   HOSTEL COMPLAINT & MAINTENANCE SYSTEM         ");
        System.out.println("=================================================");
        Server.main(args);
    }
}
