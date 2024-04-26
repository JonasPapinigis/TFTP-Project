package org.example;

import sun.awt.image.BufferedImageDevice;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Client {
    private int selfTID;
    //Variable to track running condition in GUI
    public volatile boolean connected = false;
    Socket socket;
    //Symbolic RRQ and WRQ indicators for TFTP
    private final byte[] RRQ = {0, 1};
    private final byte[] WRQ = {0, 2};


    public boolean connect(String address, int TID) {
        try {
            //Connect to new Server using TCP
            socket = new Socket(address, TID);
            connected = true;
            return true;
        } catch (IOException e) {
            System.out.println("Failed to connect to Address: " + address + " Port: " + TID);
            return false;
        }
    }

    public boolean disconnect() {
        try {
            //Close socket in case user wants to establish new connection
            socket.close();
            connected = false;
            //boolean tracker for success of disconnect method
            return true;
        } catch (IOException e) {
            System.out.println("Error disconnecting socket");
            return false;
        }

    }

    public boolean fileSend(String filename) {
        //Does not allow method to happen if TCP connection not present
        if (!connected) {
            System.out.println("CLIENT ERROR: Not connected.");
            return false;
        }
        //Create files directory if not yet present
        File directory = new File("Files");
        if (!directory.isDirectory()) {
            directory.mkdirs();
        }
        //File directory
        File fileToSend = new File(directory, filename + ".txt");

        //fis = Stream from read file, allows us to collect byte[512] arrays
        //out = responses from TCP connection
        //bos = to be able to act on responses
        //in  = what is sent to TCP connection
        //bis = to be able to act on what is sent
        try (BufferedInputStream fis = new BufferedInputStream(new FileInputStream(fileToSend));
             OutputStream out = socket.getOutputStream();
             BufferedOutputStream bos = new BufferedOutputStream(out);
             InputStream in = socket.getInputStream();
             BufferedInputStream bis = new BufferedInputStream(in)) {

            //OPCODE + filename to bytes to be delivered to TCP connection
            byte[] fileBytes = filename.getBytes(StandardCharsets.UTF_8);
            byte[] request = new byte[fileBytes.length + 2];
            request[0] = WRQ[0];
            request[1] = WRQ[1];
            System.arraycopy(fileBytes, 0, request, 2, fileBytes.length);
            //Request sent
            bos.write(request);
            bos.flush();

            // Awaiting ACK
            byte[] response = new byte[2];
            int responseLength = bis.read(response);

            if (responseLength == 2 && response[0] == 0 && response[1] == 4) {
                //ACK received
                byte[] buffer = new byte[512];
                int bytesRead;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    //Read until not more bytes left
                    bos.write(buffer, 0, bytesRead);
                    bos.flush();

                    //Read done when byte[] < 512 as per TFTP
                    if (bytesRead < 512) {
                        break;
                    }
                }

                System.out.println("File sent successfully.");
                return true;
            } else {
                System.out.println("CLIENT ERROR: Failed to get correct ACK from server.");
                return false;
            }
        } catch (FileNotFoundException e) {
            System.out.println("CLIENT ERROR: " + filename + ".txt not found");
            return false;
        } catch (IOException e) {
            System.out.println("CLIENT ERROR: Error during file transfer");
            e.printStackTrace();
            return false;
        }
    }

    public boolean fileRead(String filename) {
        //Does not allow for method to commence without connection
        if (!connected) {
            System.out.println("CLIENT ERROR: Not connected.");
            return false;
        }

        File directory = new File("Files");
        if (!directory.isDirectory()) {
            directory.mkdirs();
        }
        //Create directory + file
        File fileToReceive = new File(directory, filename + ".txt");

        //fos = Stream to write to file, allows us to store byte[512] arrays
        //out = bytes recieved from TCP connection
        //bos = way to manage said bytes
        //in = inputs into TCP connection
        //bis = ways to manage inputs client-side
        try (OutputStream fos = new BufferedOutputStream(new FileOutputStream(fileToReceive));
             OutputStream out = socket.getOutputStream();
             BufferedOutputStream bos = new BufferedOutputStream(out);
             InputStream in = socket.getInputStream();
             BufferedInputStream bis = new BufferedInputStream(in)) {

            //Create request as described above
            byte[] fileBytes = filename.getBytes(StandardCharsets.UTF_8);
            byte[] request = new byte[fileBytes.length + 2];
            //Using RRQ opcode
            request[0] = RRQ[0];
            request[1] = RRQ[1];
            System.arraycopy(fileBytes, 0, request, 2, fileBytes.length);
            //Send request
            bos.write(request);
            bos.flush();

            // Process incoming data
            byte[] buffer = new byte[512];
            int bytesRead;

            //Write from TCP input stream
            while ((bytesRead = bis.read(buffer)) != -1) {
                if (bytesRead == 2 && buffer[0] == 0 && buffer[1] == 5) {
                    System.out.println("CLIENT ERROR: Received error opcode from server.");
                    return false;
                }
                fos.write(buffer, 0, bytesRead);
                fos.flush();

                //End of transfer as per TFTP protocol
                if (bytesRead < 512) {
                    break;
                }
            }

            System.out.println("File received successfully.");
            return true;
        } catch (FileNotFoundException e) {
            System.out.println("CLIENT ERROR: Unable to create file: " + fileToReceive.getAbsolutePath());
            return false;
        } catch (IOException e) {
            System.out.println("CLIENT ERROR: Error during file transfer");
            e.printStackTrace();
            return false;
        }

    }

    //Method used to repring String[] array in main method
    private static void printAll(String[] lines) {
        for (String str : lines) {
            System.out.print(str + " ");
        }
        System.out.println();
    }








    private static String[] instructions = {"COMMANDS: -generate [filename] [num] \n\t\t\t\t\t Will generate a sample .txt file of num length in " +
            "\n\t\t\t\t\t the Files folder in the working directory\n",
            "         -connect [address]\n\t\t\t\t\t Will establish a TCP connection to server at address and port if available\n",
            "         -disconnect\n\t\t\t\t\t Will disconnect current connection if present\n",
            "         -send [filename] \n\t\t\t\t\t If present in Files, will send filename.txt to specified host over" +
                    "TFTP-TCP protocol \n\t\t\t\t\t use localhost as ip if sending to 127.0.0.1\n",
            "         -request [filename] [host]\n\t\t\t\t\t If available, will copy filename.txt from the host server over TFTP protcol" +
                    "\n\t\t\t\t\t use localhost as ip if sending to 127.0.0.1\n\n", "         -exit\n\n", "         -help\n\n"
    };

    //Used for -generate (described in my UDP client source code)
    public static boolean generateFile (String filename,int len){
        // Specify the directory and filename.
        File file = new File("Files", filename + ".txt");

        // Ensure the directory exists
        file.getParentFile().mkdirs();

        try (FileOutputStream fos = new FileOutputStream(file)) {
            // Sample data
            String data = "ABCD";
            byte[] bytes = data.getBytes();

            // Calculate how many full patterns to write
            int fullPatterns = len / bytes.length;
            int remainingBytes = len % bytes.length;

            // Write the full patterns
            for (int i = 0; i < fullPatterns; i++) {
                fos.write(bytes);
            }

            // Write any remaining bytes
            if (remainingBytes > 0) {
                fos.write(bytes, 0, remainingBytes);
            }
        } catch (IOException e) {
            System.out.println("Failed to create and write to file: " + file.getAbsolutePath());
            e.printStackTrace();
            return false;
        }

        System.out.println("File successfully created: " + file.getAbsolutePath() + ", Size: " + len + " bytes");
        return true;
    }
public static void main(String[] args) {

        //Method similar to my UI in TFTP-UDP-Client
    boolean running = true;
    Scanner scanner = new Scanner(System.in);
    String address = null;
    //New Client
    Client client = new Client();
    System.out.println("------TFTP Protocol Client------");
    //Instructions as static String[] to be able to bring them up when needed
    printAll(instructions);

    while (running) {
        //Get Args
        System.out.print("Input: ");
        String inputLine = scanner.nextLine();
        //split by space
        String[] arguements = inputLine.split("\\s+");

        //Exit
        if (arguements.length == 1 && arguements[0].equals("-exit")) {
            running = false;
        }
        //Help: print instrcution String[]
        else if (arguements.length == 1 && arguements[0].equals("-help")) {
            printAll(instructions);
        }
        //-Generate: takes filename from arg to, parses 3rd for byte size
        //Creates sample file in Files to be used in exchanges with Server
        else if (arguements.length == 3 && arguements[0].equals("-generate")) {
            if (generateFile(arguements[1], Integer.parseInt(arguements[2]))) {
                System.out.println("CLIENT: File generated Successfully");
            } else {
                System.out.println("ERROR: File failed to generate/ Invalid arguments");
            }
        }
        //Establises TCP connection using client method
        else if(arguements.length == 2 && arguements[0].equals("-connect")) {
            //Does not allow multiple connections per client
            if (!client.connected) {
                if (client.connect(arguements[1], 69)) {
                    System.out.println("CLIENT: Connected successfully");
                } else {
                    System.out.println("CLIENT: Failed to connect");
                }
            } else {
                System.out.println("CLIENT: Connection already established, please use -disconnect");
            }
            continue;
            //-Send: Only has 1 arguement this time as connection established
            // Send file args[1].txt if available
        }
        else if (arguements.length == 2 && arguements[0].equals("-send")) {
            //Stops user if trying to connect again
            if (!client.connected){
                System.out.println("CLIENT: No connection found");
            }
            else{
                if (client.fileSend(arguements[1])){
                    System.out.println("CLIENT: File sent successfully");
                }
                else{
                    System.out.println("CLIENT: Failed to send file");
                }

            }
            continue;
        }
        //Disconnect esablished TCP connection, user is able to create a new one using -connect after doing so
        else if (arguements.length == 1 && arguements[0].equals("-disconnect")) {
            if (client.connected){
                client.disconnect();
                System.out.println("CLIENT: Connection successfully severed");
            }
            else{
                System.out.println("CLIENT: No connection found");
            }
            continue;
        }
        //-request: Only 2 args as connection already known
        //Requests file args[1].txt if available
        else if (arguements.length == 2 && arguements[0].equals("-request")) {
            if (!client.connected){
                System.out.println("CLIENT: No connection found");
            }
            else{
                if (client.fileRead(arguements[1])){
                    System.out.println("CLIENT: File sent successfully");
                }
                else{
                    System.out.println("CLIENT: Failed to send file");
                }

            }
            continue;


        }

    }
}


}