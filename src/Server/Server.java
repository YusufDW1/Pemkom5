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

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server berjalan di port " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Koneksi dari " + socket.getInetAddress());
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class ClientHandler implements Runnable {
    private Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        String clientName = null;

        try (
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream())
        ) {
            String reg = dis.readUTF();
            if (reg.startsWith("REGISTER:")) {
                clientName = reg.substring("REGISTER:".length());
                Server.clients.put(clientName, socket);
                dos.writeUTF("CMDR:READY");
            } else {
                dos.writeUTF("CMDR:ERROR Registration required");
                return;
            }

            while (true) {
                String cmd = dis.readUTF();
                if (cmd.startsWith("LIST")) {
                    String list = String.join(",", Server.clients.keySet());
                    dos.writeUTF("CMDR:LIST:" + list);
                } else if (cmd.startsWith("SEND:")) {
                    String[] parts = cmd.split(":", 4);
                    String target = parts[1];
                    String fileName = parts[2];
                    long fileSize = Long.parseLong(parts[3]);

                    Socket targetSocket = Server.clients.get(target);
                    if (targetSocket != null) {
                        DataOutputStream targetDos = new DataOutputStream(targetSocket.getOutputStream());

                        // Kirim metadata ke target
                        targetDos.writeUTF("FILE_META:" + fileName + ":" + fileSize);

                        // Teruskan isi file
                        byte[] buffer = new byte[4096];
                        long remaining = fileSize;
                        while (remaining > 0) {
                            int bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                            if (bytesRead == -1) break;
                            targetDos.write(buffer, 0, bytesRead);
                            remaining -= bytesRead;
                        }
                        targetDos.flush();

                        dos.writeUTF("CMDR:SENT");
                    } else {
                        dos.writeUTF("CMDR:ERROR Target not found");
                    }
                } else if (cmd.startsWith("EXIT")) {
                    break;
                } else {
                    dos.writeUTF("CMDR:ERROR Unknown command");
                }
            }

        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        } finally {
            if (clientName != null) {
                Server.clients.remove(clientName);
                System.out.println("Client " + clientName + " disconnected.");
            }
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}