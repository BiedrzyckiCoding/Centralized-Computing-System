import java.io.*;
import java.net.*;
import java.util.Random;

public class Client {

    public static void main(String[] args) {
        // 1. Validate arguments
        if (args.length != 1) {
            System.err.println("Usage: java Client <port>");
            System.exit(1);
        }

        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Error: <port> must be an integer.");
            System.exit(1);
            return;
        }

        // 2. Send a UDP broadcast "CCS DISCOVER" to find the server
        InetAddress serverAddress = discoverServer(port);
        if (serverAddress == null) {
            System.err.println("Server not found (no \"CCS FOUND\" response).");
            return;
        }
        System.out.println("Discovered server IP: " + serverAddress.getHostAddress());

        // 3. Connect to the server via TCP
        try (Socket socket = new Socket(serverAddress, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            System.out.println("Connected to server at " + serverAddress + ":" + port);

            // 4. Cyclically send random requests until terminated
            Random random = new Random();
            String[] operations = { "ADD", "SUB", "MUL", "DIV" };

            while (true) {
                // Pick a random operation
                String oper = operations[random.nextInt(operations.length)];

                // Generate random arguments
                int arg1 = random.nextInt(20) - 10;  // random int in range [-10..9]
                int arg2 = random.nextInt(20) - 10;  // random int in range [-10..9]

                // Avoid DIV by zero
                if ("DIV".equals(oper) && arg2 == 0) {
                    arg2 = 1;
                }

                // Prepare the request line: "<OPER> <ARG1> <ARG2>"
                String request = oper + " " + arg1 + " " + arg2;
                System.out.println("Sending to server: " + request);
                out.println(request);

                // 5. Read response
                String response = in.readLine();
                if (response == null) {
                    System.out.println("Server closed the connection.");
                    break;
                }
                System.out.println("Response: " + response);

                // Random wait (1-3 seconds) before next request
                Thread.sleep((random.nextInt(3) + 1) * 1000L);
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }

    /**
     * Broadcasts "CCS DISCOVER" to the specified port, waits for "CCS FOUND".
     * Returns the server's IP address if found, or null otherwise.
     */
    private static InetAddress discoverServer(int port) {
        DatagramSocket udpSocket = null;
        try {
            // Create a UDP socket, enable broadcast
            udpSocket = new DatagramSocket();
            udpSocket.setBroadcast(true);

            // Prepare the discovery message
            byte[] sendData = "CCS DISCOVER".getBytes();
            DatagramPacket sendPacket = new DatagramPacket(
                    sendData,
                    sendData.length,
                    InetAddress.getByName("255.255.255.255"), // broadcast IP
                    port
            );

            // Send the broadcast
            System.out.println("Broadcasting discovery packet on port " + port + "...");
            udpSocket.send(sendPacket);

            // Set a timeout so we don't block forever waiting for a response
            udpSocket.setSoTimeout(3000);

            // Prepare buffer for incoming packets
            byte[] recvBuffer = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(recvBuffer, recvBuffer.length);

            // Listen for responses
            while (true) {
                try {
                    udpSocket.receive(receivePacket);
                } catch (SocketTimeoutException e) {
                    // Timed out waiting for response
                    return null;
                }

                String message = new String(
                        receivePacket.getData(),
                        0,
                        receivePacket.getLength()
                ).trim();

                // Check if the response is "CCS FOUND"
                if ("CCS FOUND".equals(message)) {
                    // Return the address of the responding server
                    return receivePacket.getAddress();
                }
            }

        } catch (IOException e) {
            System.err.println("UDP discovery error: " + e.getMessage());
            return null;
        } finally {
            if (udpSocket != null) {
                udpSocket.close();
            }
        }
    }
}
