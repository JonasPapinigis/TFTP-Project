package org.example;

import org.w3c.dom.ranges.Range;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Random;
import java.util.Scanner;

public class Server extends Thread{
    private final static int MAX_LENGTH = 516;
    protected DatagramSocket socket = null;
    private volatile boolean running = false;
    private static final byte[] octetBytes = "octet".getBytes(StandardCharsets.UTF_8);
    public Server() throws SocketException{

        socket = new DatagramSocket(69);
        socket.setSoTimeout(5000);

    }

    public void runServer() {
        running = true;
        byte[] buffer = new byte[MAX_LENGTH];
        while (running) {
            System.out.println("...Listening (type -exit to stop)");
            DatagramPacket pack = new DatagramPacket(buffer, MAX_LENGTH);
            try {
                socket.receive(pack);
                pack = cleanRequest(pack);
            } catch (SocketTimeoutException i) {
                continue;  // Just keep listening
            } catch (Exception e) {
                System.out.println("SERVER ERROR: Failed to retrieve packet");
                continue;
            }
            System.out.println("here");
            if (isValidRequest(pack)) {
                createNewThread(pack);
            } else {
                processIncorrectInstantiation(pack);
            }
        }
        socket.close();
        System.out.println("Server has been shut down.");
    }
    public void exit(){
        running = false;
    }


    private void createNewThread(DatagramPacket p){
        ServerThread thread = null;
        try{
            thread = new ServerThread(p);
            thread.start();
        } catch (Exception e){
            System.out.println("SERVER ERROR: Failed start thread");
            e.printStackTrace();
        }
    }
    private void processIncorrectInstantiation(DatagramPacket pack){
        byte[] errMsg = "Illegal TFTP Operation".getBytes(StandardCharsets.UTF_8);
        byte[] errData =  new byte[errMsg.length + 5];
        errData[0] = 0; errData[1] = 5; errData[3] = 0; errData[4] = 4;
        System.arraycopy(errMsg,0,errData,4,errMsg.length);
        errData[errMsg.length + 4] = 0;

        DatagramPacket errPacket = new DatagramPacket(errData, errData.length,
                pack.getAddress(), pack.getPort());
        try{
            socket.send(errPacket);
        } catch (IOException i){
            System.out.println("SERVER: Invalid error packet send to "+ pack.getAddress()+ ", Port: "+ pack.getPort());
        }
    }

    private boolean isValidRequest(DatagramPacket pack){

        byte[] data = pack.getData();
        int length = pack.getLength();
        if (data[0] != 0 || (data[1] != 1 && data[1] != 2)){

            return false;
        }

        int zeroBytes = 0;
        for (int i = 2; i < length; i++){
            if (data[i] == 0){
                zeroBytes++;
                System.out.println("BINGO");
            }
        }

        if (zeroBytes != 2){
            return false;
        }

        return true;
    }

    public static void main(String[] args){
        Scanner scanner = new Scanner(System.in);
        System.out.println("------TFTP Protocol Server------");
        System.out.println("This is a server client which can accept several Net IO operations over the TFTP protocol");
        System.out.println("Files are stored in the Files folder in the working directory");
        System.out.println("To access please use the TFTP Protocol Client to access information");
        boolean validInput = false;
        while(!validInput){
            System.out.println("COMMANDS: -start\n          -exit\nInput:");
            String argument = scanner.nextLine();
            if (argument.equals("-exit")){
                validInput = true;
                break;
            }
            else if (argument.equals("-start")){
                validInput = true;
                try {
                    Server server = new Server();
                    Thread serverThread = new Thread(server::runServer);
                    serverThread.start();
                    System.out.println("...Server started");
                    boolean exited = false;
                    while(!exited){
                        argument = scanner.nextLine();
                        if (argument.equals("-exit")){
                            exited=true;
                            server.exit();
                            serverThread.join();
                        }
                    }
                } catch (SocketException e) {
                    System.out.println("SERVER ERROR: Socket Error, failed to start server");
                    e.printStackTrace();
                    continue;
                } catch (InterruptedException i){
                    System.out.println("SERVER ERROR: Failed to close gracefully");
                    break;
                }
            }
            else{
                System.out.println("SERVER ERROR: Invalid argument");
            }
        }



    }

    private static void printByteArray(byte[] bytes) {
        // Print each byte in the array as a two-digit hexadecimal number
        for (byte b : bytes) {
            System.out.print(String.format("%02X ", b)); // %02X ensures printing at least two digits, padding with zero if necessary
        }
        System.out.println(); // Print a newline after the array
    }
    private static String extractFilename(DatagramPacket p) {
        byte[] data = p.getData();
        int length = p.getLength(); // To ensure we don't read past the actual data
        StringBuilder filename = new StringBuilder();

        // Start reading the filename just after the opcode, hence index = 2
        for (int i = 2; i < length; i++) {
            if (data[i] == 0) {
                break; // Stop on the first null byte, which terminates the filename
            }
            filename.append((char) data[i]); // Cast byte to char and append to the filename
        }

        return filename.toString();
    }

    private DatagramPacket cleanRequest(DatagramPacket p){
        DatagramPacket packet = p;
        String filename = extractFilename(p);
        byte[] data = p.getData();
        byte[] nameBytes = filename.getBytes(StandardCharsets.UTF_8);
        byte[] newData = new byte[nameBytes.length+ octetBytes.length+4];
        newData[0] = data[0]; newData[1] = data[1];
        System.arraycopy(nameBytes,0,newData,2,nameBytes.length);
        newData[nameBytes.length+2] = 0;
        System.arraycopy(octetBytes,0,newData,nameBytes.length+3,octetBytes.length);
        newData[nameBytes.length+3+octetBytes.length] = 0;
        packet.setData(newData);
        return packet;

    }



}