/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Server;

/**
 *
 * @author User
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private static final int PORT = 5000;
    static Map<String, Socket> clients = Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) throws IOException {
        String ip = getLocalIPAddress();
        System.out.println("Server berjalan di " + ip + ":" + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Koneksi diterima dari: " + socket.getInetAddress());
                new Thread(new ClientHandler(socket)).start();
            }
        }
    }

    private static String getLocalIPAddress() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (iface.isLoopback() || !iface.isUp()) continue;

            Enumeration<InetAddress> addresses = iface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (addr instanceof Inet4Address) {
                    return addr.getHostAddress();
                }
            }
        }
        return "127.0.0.1";
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private String clientName;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            String reg = dis.readUTF();
            if (reg.startsWith("REGISTER:")) {
                clientName = reg.substring("REGISTER:".length());
                Server.clients.put(clientName, socket);
                System.out.println("Client registered: " + clientName);
                dos.writeUTF("CMDR:READY");
            } else {
                dos.writeUTF("CMDR:ERROR: Registration required");
                return;
            }

            while (true) {
                String cmd;
                try {
                    cmd = dis.readUTF();
                } catch (IOException e) {
                    break;
                }

                if (cmd.equals("LIST")) {
                    String list = String.join(",", Server.clients.keySet());
                    dos.writeUTF("CMDR:LIST:" + list);
                } else if (cmd.startsWith("SEND:")) {
                    String[] parts = cmd.split(":", 4);
                    String targetName = parts[1];
                    String fileName = parts[2];
                    long fileSize = Long.parseLong(parts[3]);

                    if (fileSize > 200 * 1024 * 1024) {
                        dos.writeUTF("CMDR:ERROR:File too big (max 200MB)");
                        continue;
                    }

                    Socket targetSocket = Server.clients.get(targetName);
                    if (targetSocket == null) {
                        dos.writeUTF("CMDR:ERROR:Target not found");
                        continue;
                    }

                    try (DataOutputStream targetDos = new DataOutputStream(targetSocket.getOutputStream())) {
                        targetDos.writeUTF("FILE_META:" + fileName + ":" + fileSize);

                        byte[] buffer = new byte[4096];
                        long remaining = fileSize;
                        OutputStream targetOut = targetSocket.getOutputStream();
                        while (remaining > 0) {
                            int read = dis.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                            if (read == -1) break;
                            targetOut.write(buffer, 0, read);
                            remaining -= read;
                        }
                        targetOut.flush();
                        dos.writeUTF("CMDR:SENT");
                    } catch (IOException e) {
                        dos.writeUTF("CMDR:ERROR:Send failed");
                    }
                } else if (cmd.startsWith("EXIT:")) {
                    break;
                } else {
                    dos.writeUTF("CMDR:ERROR:Unknown command");
                }
            }
        } catch (IOException e) {
            System.out.println("Client " + clientName + " error: " + e.getMessage());
        } finally {
            if (clientName != null) {
                Server.clients.remove(clientName);
                System.out.println("Client disconnected: " + clientName);
            }
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
