import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class CCS {

    // --- STATISTICS CLASS ---
    // stores both "lifetime" and "interval" statistics.
    // the "interval" counters are reset every 10 seconds when printing.
    static class Statistics {
        // lifetime counters
        private final AtomicInteger totalConnections = new AtomicInteger(0);
        private final AtomicInteger totalRequests = new AtomicInteger(0);
        private final AtomicInteger totalAdd = new AtomicInteger(0);
        private final AtomicInteger totalSub = new AtomicInteger(0);
        private final AtomicInteger totalMul = new AtomicInteger(0);
        private final AtomicInteger totalDiv = new AtomicInteger(0);
        private final AtomicInteger totalErrors = new AtomicInteger(0);
        private final AtomicLong totalSum = new AtomicLong(0);

        // interval counters
        private final AtomicInteger intervalConnections = new AtomicInteger(0);
        private final AtomicInteger intervalRequests = new AtomicInteger(0);
        private final AtomicInteger intervalAdd = new AtomicInteger(0);
        private final AtomicInteger intervalSub = new AtomicInteger(0);
        private final AtomicInteger intervalMul = new AtomicInteger(0);
        private final AtomicInteger intervalDiv = new AtomicInteger(0);
        private final AtomicInteger intervalErrors = new AtomicInteger(0);
        private final AtomicLong intervalSum = new AtomicLong(0);

        // incrementation methods

        public void increment(AtomicInteger value1, AtomicInteger value2){
            value1.incrementAndGet();
            value2.incrementAndGet();
        }
        public void addToSum(long val) {
            totalSum.addAndGet(val);
            intervalSum.addAndGet(val);
        }

        // print method (prints both totals and intervals)
        public synchronized void printAndResetIntervalStats() {
            System.out.println("======= STATISTICS REPORT =======");

            // lifetime Stats
            System.out.println(" -- Lifetime --");
            System.out.println("Newly connected clients: " + totalConnections.get());
            System.out.println("Total requests: " + totalRequests.get());
            System.out.println("ADD operations: " + totalAdd.get());
            System.out.println("SUB operations: " + totalSub.get());
            System.out.println("MUL operations: " + totalMul.get());
            System.out.println("DIV operations: " + totalDiv.get());
            System.out.println("Errors: " + totalErrors.get());
            System.out.println("Sum of results: " + totalSum.get());

            // interval Stats
            System.out.println(" -- Last 10 seconds --");
            System.out.println("Newly connected clients: " + intervalConnections.get());
            System.out.println("Total requests: " + intervalRequests.get());
            System.out.println("ADD ops: " + intervalAdd.get());
            System.out.println("SUB ops: " + intervalSub.get());
            System.out.println("MUL ops: " + intervalMul.get());
            System.out.println("DIV ops: " + intervalDiv.get());
            System.out.println("Errors: " + intervalErrors.get());
            System.out.println("Sum of results: " + intervalSum.get());
            System.out.println("=================================");
            System.out.println();

            // reset interval counters
            intervalConnections.set(0);
            intervalRequests.set(0);
            intervalAdd.set(0);
            intervalSub.set(0);
            intervalMul.set(0);
            intervalDiv.set(0);
            intervalErrors.set(0);
            intervalSum.set(0);
        }
    }

    // global statistics
    private static final Statistics stats = new Statistics();

    // main method
    public static void main(String[] args) {

        // 1. ceck arguments
        if (args.length != 1) {
            System.err.println("Usage: java -jar CCS.jar <port>");
            System.exit(1);
        }

        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Port must be an integer.");
            System.exit(1);
            return; // unreachable, but keeps compiler happy
        }

        // 2. start UDP discovery thread
        Thread udpThread = new Thread(() -> startUdpDiscovery(port), "UDP-Discovery-Thread");
        udpThread.setDaemon(true);
        udpThread.start();

        // 3. start TCP server in a separate thread
        Thread tcpThread = new Thread(() -> startTcpServer(port), "TCP-Server-Thread");
        tcpThread.setDaemon(true);
        tcpThread.start();

        // 4. start statistics reporting (every 10 seconds)
        Timer statsTimer = new Timer("Stats-Timer", true);
        statsTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                stats.printAndResetIntervalStats();
            }
        }, 10000, 10000);

        // keep main thread alive
        System.out.println("CCS Server started on port " + port + ". Press Ctrl+C to stop.");
        try {
            // wait indefinitely
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.err.println("CCS Server interrupted, shutting down.");
        }
    }

    // SERVICE DISCOVERY (UDP)
    private static void startUdpDiscovery(int port) {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // blocking call

                String received = new String(packet.getData(), 0, packet.getLength()).trim();
                if (received.startsWith("CCS DISCOVER")) {
                    // reply with "CCS FOUND"
                    byte[] responseData = "CCS FOUND".getBytes();
                    DatagramPacket responsePacket = new DatagramPacket(
                            responseData,
                            responseData.length,
                            packet.getAddress(),
                            packet.getPort()
                    );
                    socket.send(responsePacket);
                }
            }
        } catch (IOException e) {
            System.err.println("UDP discovery error: " + e.getMessage());
        }
    }

    // TCP SERVER
    private static void startTcpServer(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept(); // blocking call
                stats.increment(stats.totalConnections, stats.intervalConnections); // a new client connection
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                // handle client in a separate thread
                Thread clientThread = new Thread(() -> handleClient(clientSocket));
                clientThread.start();
            }
        } catch (IOException e) {
            System.err.println("TCP Server error: " + e.getMessage());
        }
    }

    // HANDLE CLIENT REQUESTS
    private static void handleClient(Socket clientSocket) {
        try (
                BufferedReader in  = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out    = new PrintWriter(clientSocket.getOutputStream(), true);
        ) {
            String line;
            while ((line = in.readLine()) != null) {
                // each request is a single line: <OPER> <ARG1> <ARG2>
                stats.increment(stats.totalRequests, stats.intervalRequests);
                String response = processRequest(line);
                out.println(response);
            }
        } catch (IOException e) {
            System.err.println("Client disconnected: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignore) {}
        }
    }

    // PROCESS A SINGLE REQUEST
    private static String processRequest(String requestLine) {
        // print to console the request
        System.out.println("Received request: " + requestLine);

        // parse the request
        String[] parts = requestLine.split("\\s+");
        if (parts.length != 3) {
            stats.increment(stats.totalErrors, stats.intervalErrors);
            System.out.println("Result: ERROR (invalid format)");
            return "ERROR";
        }

        String oper = parts[0];
        int arg1, arg2;
        try {
            arg1 = Integer.parseInt(parts[1]);
            arg2 = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            stats.increment(stats.totalErrors, stats.intervalErrors);
            System.out.println("Result: ERROR (invalid integer arguments)");
            return "ERROR";
        }

        // compute the operation
        long result;
        switch (oper) {
            case "ADD":
                stats.increment(stats.totalAdd, stats.intervalAdd);
                result = (long) arg1 + arg2;
                break;
            case "SUB":
                stats.increment(stats.totalSub, stats.intervalSub);
                result = (long) arg1 - arg2;
                break;
            case "MUL":
                stats.increment(stats.totalMul, stats.intervalMul);
                result = (long) arg1 * arg2;
                break;
            case "DIV":
                stats.increment(stats.totalDiv, stats.intervalDiv);
                if (arg2 == 0) {
                    stats.increment(stats.totalErrors, stats.intervalErrors);
                    System.out.println("Result: ERROR (division by zero)");
                    return "ERROR";
                }
                result = arg1 / arg2; // integer division
                break;
            default:
                stats.increment(stats.totalErrors, stats.intervalErrors);
                System.out.println("Result: ERROR (unknown operation)");
                return "ERROR";
        }

        // valid result
        stats.addToSum(result);
        String resultStr = String.valueOf(result);
        System.out.println("Result: " + resultStr);
        return resultStr;
    }
}
