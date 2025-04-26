/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Client;

/**
 *
 * @author User
 */
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;

public class Client {
    public static void main(String[] args) throws InterruptedException {
        Scanner scanner = new Scanner(System.in);
        
        // Meminta input IP server dari pengguna
        System.out.print("IP Server: ");
        String serverIP = scanner.nextLine().trim();

        // Validasi IP server
        while (!isValidIP(serverIP)) {
            System.out.println("IP tidak valid. Silakan coba lagi.");
            System.out.print("IP Server: ");
            serverIP = scanner.nextLine().trim();
        }

        System.out.print("Masukkan Nama Anda: ");
        String name = scanner.nextLine().trim();

        while (true) {
            try (
                Socket socket = new Socket(serverIP, 5000); // Gunakan IP yang dimasukkan pengguna
                DataInputStream dis = new DataInputStream(socket.getInputStream());
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream())
            ) {
                BlockingQueue<String> cmdQueue = new ArrayBlockingQueue<>(10);

                // Registrasi ke server
                dos.writeUTF("REGISTER:" + name);
                dos.flush();

                // Thread receiver untuk menerima file
                new Thread(() -> {
                    try {
                        while (true) {
                            String msg = dis.readUTF();
                            if (msg.startsWith("CMDR:")) {
                                cmdQueue.put(msg.substring(5));
                            } else if (msg.startsWith("FILE_META:")) {
                                String[] parts = msg.split(":", 3);
                                String fileName = parts[1];
                                long fileSize = Long.parseLong(parts[2]);

                                // Menerima file dengan progress
                                receiveFile(dis, fileName, fileSize);
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Receiver berhenti: " + e.getMessage());
                    }
                }).start();

                // Tunggu registrasi sukses
                String regResp = cmdQueue.take();
                System.out.println("Server: " + regResp);

                // Main loop untuk command
                while (true) {
                    System.out.println("Perintah: LIST, SEND <target> <path>, SEND_MULTI <target> <filePaths>, EXIT");
                    System.out.print("Masukkan perintah: ");
                    String input = scanner.nextLine().trim();

                    if (input.equalsIgnoreCase("LIST")) {
                        dos.writeUTF("LIST");
                        dos.flush();
                        String listResp = cmdQueue.take();
                        if (listResp.startsWith("LIST:")) listResp = listResp.substring(5);
                        System.out.println("Client online: " + listResp);

                    } else if (input.toUpperCase().startsWith("SEND ")) {
                        sendFile(dos, cmdQueue, input.split(" ", 3));

                    } else if (input.toUpperCase().startsWith("SEND_MULTI ")) {
                        sendMultipleFiles(dos, cmdQueue, input.split(" ", 3));

                    } else if (input.equalsIgnoreCase("EXIT")) {
                        dos.writeUTF("EXIT:" + name);
                        dos.flush();
                        break;

                    } else {
                        System.out.println("Perintah tidak dikenali.");
                    }
                }

                System.out.println("Client berhenti.");
                break;

            } catch (IOException e) {
                System.out.println("Tidak bisa terhubung ke server. Mencoba lagi...");
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {} // Delay before reconnecting
            }
        }
    }

    // Fungsi untuk memvalidasi format IP
    private static boolean isValidIP(String ip) {
        String regex = "^([0-9]{1,3}\\.){3}[0-9]{1,3}$";
        return ip.matches(regex);
    }

    // Kirim file tunggal
    private static void sendFile(DataOutputStream dos, BlockingQueue<String> cmdQueue, String[] parts) throws IOException, InterruptedException {
        if (parts.length < 3) {
            System.out.println("Format: SEND <target> <path>");
            return;
        }
        String target = parts[1];
        File file = new File(parts[2]);

        if (!file.exists() || !file.isFile()) {
            System.out.println("File tidak ditemukan: " + parts[2]);
            return;
        }

        if (file.length() > 200 * 1024 * 1024) {
            System.out.println("File lebih besar dari 200MB, tidak bisa dikirim.");
            return;
        }

        dos.writeUTF("SEND:" + target + ":" + file.getName() + ":" + file.length());
        dos.flush();

        // Kirim file dengan progress
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytes = file.length();
            long bytesSent = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
                bytesSent += bytesRead;

                // Update progress bar
                int progress = (int) ((bytesSent * 100) / totalBytes);
                System.out.print("\rKirim file: " + progress + "%");
            }
            dos.flush();
        }

        String ack = cmdQueue.take();
        System.out.println("\nServer: " + ack);
    }

    // Kirim beberapa file
    private static void sendMultipleFiles(DataOutputStream dos, BlockingQueue<String> cmdQueue, String[] parts) throws IOException, InterruptedException {
        if (parts.length < 3) {
            System.out.println("Format: SEND_MULTI <target> <filePaths>");
            return;
        }
        String target = parts[1];
        String[] filePaths = parts[2].split(",");

        for (String path : filePaths) {
            File file = new File(path.trim());

            if (!file.exists() || !file.isFile()) {
                System.out.println("File tidak ditemukan: " + path);
                continue;
            }

            if (file.length() > 200 * 1024 * 1024) {
                System.out.println("File " + file.getName() + " lebih besar dari 200MB, tidak bisa dikirim.");
                continue;
            }

            dos.writeUTF("SEND:" + target + ":" + file.getName() + ":" + file.length());
            dos.flush();

            // Kirim file dengan progress
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytes = file.length();
                long bytesSent = 0;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                    bytesSent += bytesRead;

                    // Update progress bar
                    int progress = (int) ((bytesSent * 100) / totalBytes);
                    System.out.print("\rKirim file: " + progress + "%");
                }
                dos.flush();
            }

            String ack = cmdQueue.take();
            System.out.println("\nServer: " + ack);
        }
    }

    // Terima file dengan progress bar
    private static void receiveFile(DataInputStream dis, String fileName, long fileSize) throws IOException {
        String userHome = System.getProperty("user.home");
        File downloads = new File(userHome, "Downloads");
        if (!downloads.exists()) downloads.mkdirs();
        File outFile = new File(downloads, fileName);

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[4096];
            long remaining = fileSize;
            long bytesReceived = 0;

            while (remaining > 0) {
                int read = dis.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                if (read == -1) break;
                fos.write(buffer, 0, read);
                bytesReceived += read;
                remaining -= read;

                // Update progress
                int progress = (int) ((bytesReceived * 100) / fileSize);
                System.out.print("\rMenerima file: " + progress + "%");
            }
            fos.flush();
        }

        System.out.println("\nFile disimpan di: " + outFile.getAbsolutePath());
    }
}