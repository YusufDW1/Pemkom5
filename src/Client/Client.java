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
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class Client {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Server IP: ");
        String serverIP = scanner.nextLine().trim();
        final int PORT = 5000;

        try (
            Socket socket = new Socket(serverIP, PORT);
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream())
        ) {
            BlockingQueue<String> cmdQueue = new ArrayBlockingQueue<>(10);

            System.out.print("Nama Anda: ");
            String name = scanner.nextLine().trim();
            dos.writeUTF("REGISTER:" + name);
            dos.flush();

            // Receiver thread
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
                            receiveFile(dis, fileName, fileSize);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Receiver berhenti: " + e.getMessage());
                }
            }).start();

            // Tunggu READY
            String regResp = cmdQueue.take();
            System.out.println("Server: " + regResp);

            // Command loop
            while (true) {
                System.out.println("\nPerintah: LIST, SEND <target> <path>, EXIT");
                System.out.print("Masukkan perintah: ");
                String input = scanner.nextLine().trim();

                if (input.equalsIgnoreCase("LIST")) {
                    dos.writeUTF("LIST");
                    dos.flush();
                    String listResp = cmdQueue.take();
                    if (listResp.startsWith("LIST:")) listResp = listResp.substring(5);
                    System.out.println("Client online: " + listResp);

                } else if (input.toUpperCase().startsWith("SEND ")) {
                    String[] parts = input.split(" ", 3);
                    if (parts.length < 3) {
                        System.out.println("Format: SEND <target> <path>");
                        continue;
                    }
                    String target = parts[1];
                    File file = new File(parts[2]);
                    if (!file.exists() || !file.isFile()) {
                        System.out.println("File tidak ditemukan: " + parts[2]);
                        continue;
                    }
                    sendFile(dos, file, target);
                    String ack = cmdQueue.take();
                    System.out.println("Server: " + ack);

                } else if (input.equalsIgnoreCase("EXIT")) {
                    dos.writeUTF("EXIT:" + name);
                    dos.flush();
                    break;

                } else {
                    System.out.println("Perintah tidak dikenali.");
                }
            }

            System.out.println("Client berhenti.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendFile(DataOutputStream dos, File file, String target) throws IOException {
        dos.writeUTF("SEND:" + target + ":" + file.getName() + ":" + file.length());
        dos.flush();

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[4096];
            long total = 0;
            int r;
            while ((r = fis.read(buf)) != -1) {
                dos.write(buf, 0, r);
                total += r;
                showProgress(total, file.length(), "Uploading");
            }
            dos.flush();
        }
        System.out.println();
    }

    private static void receiveFile(DataInputStream dis, String fileName, long fileSize) throws IOException {
        File downloads = new File(System.getProperty("user.home"), "Downloads");
        if (!downloads.exists()) downloads.mkdirs();
        File outFile = new File(downloads, fileName);
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[4096];
            long received = 0;
            while (received < fileSize) {
                int r = dis.read(buffer, 0, (int) Math.min(buffer.length, fileSize - received));
                if (r == -1) throw new EOFException("File transfer ended unexpectedly");
                fos.write(buffer, 0, r);
                received += r;
                showProgress(received, fileSize, "Downloading");
            }
        }
        System.out.println("\nFile diterima di: " + outFile.getAbsolutePath());
    }

    private static void showProgress(long current, long total, String action) {
        int percent = (int) ((current * 100) / total);
        int progress = percent / 5;
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < 20; i++) {
            if (i < progress) bar.append("#");
            else bar.append(".");
        }
        bar.append("] ").append(percent).append("% ").append(action);
        System.out.print("\r" + bar);
    }
}