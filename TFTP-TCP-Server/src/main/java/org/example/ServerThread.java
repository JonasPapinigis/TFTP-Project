package org.example;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ServerThread extends Thread {
    private Socket socket;
    //Symbolic TFTP opcodes
    private final byte[] ACK = {0, 4};
    private final byte[] ERROR = {0, 5};

    public ServerThread(Socket socket) {
        //Socket inherited from Server parent who called the thread
        this.socket = socket;
    }

    //Inherited method from Thread
    @Override
    public void run() {
        try {
            //Setting up input-output streams
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            BufferedInputStream bis = new BufferedInputStream(in);
            BufferedOutputStream bos = new BufferedOutputStream(out);
            // Buffer for initial request
            //Large byte window for name, didn't go above protocol maximum
            byte[] request = new byte[516];
            int requestLength = bis.read(request);
            //Failed read, invalid request
            if (requestLength == -1) return;

            // Determine request opcode
            if (request[0] == 0 && request[1] == 1) {  // RRQ
                processRRQ(request, bos);
            } else if (request[0] == 0 && request[1] == 2) {  // WRQ
                processWRQ(request, bis, bos);
            }
        } catch (IOException e) {
            System.out.println("SERVER THREAD : Failed to process request");
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("SERVER THREAD : Failed to close gracefully");
            }
        }
    }
    //Inherits output stream for socket control and request for filename
    private void processRRQ(byte[] request, BufferedOutputStream bos){
        //Extract filename
        String filename = new String(request, 2, request.length - 2, StandardCharsets.UTF_8).trim() + ".txt";
        File file = new File("Files", filename);

        //FNF
        if (!file.exists()) {
            try {
                System.out.println("SERVER THREAD: File not found");
                bos.write(ERROR);
                bos.flush();
            } catch (IOException e) {
                System.out.println("SERVER THREAD: Client did not receive error");
            }
            return;
        }
        //If file found request granted, ACK sent
        try {
            bos.write(ACK);
            bos.flush();

            try (BufferedInputStream fis = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buffer = new byte[512];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    //Read until out of valid input
                    bos.write(buffer, 0, bytesRead);
                    bos.flush();
                }
            }
        } catch (Exception e){
            System.out.println("SERVER THREAD: Read failed");
        }

    }

    private void processWRQ(byte[] request, BufferedInputStream bis, BufferedOutputStream bos){
        //Extract filename from byte arrays (offset 2)
        String filename = new String(request, 2, request.length - 2, StandardCharsets.UTF_8).trim() + ".txt";
        File file = new File("Files", filename);
        //Create file
        file.getParentFile().mkdirs();
        try (BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(file))) {
            //Valid request, ACK sent
            bos.write(ACK);
            bos.flush();

            byte[] buffer = new byte[512];
            int bytesRead;
            //Read until bis still producing bytes
            while ((bytesRead = bis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        } catch (Exception e){
            System.out.println("SERVER THREAD: Failed to send file");
        }
    }
}
