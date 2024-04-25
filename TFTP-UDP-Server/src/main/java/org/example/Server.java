package org.example;

import org.w3c.dom.ranges.Range;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Random;
import java.util.Scanner;
//Class from which child ServerThreads are created
//Handles WRQs and RRQs and passes the requests to be handled
public class Server extends Thread{
    private final static int MAX_LENGTH = 516;
    protected DatagramSocket socket = null;
    private volatile boolean running = false;

    //For addition to RRQs and WRQs for consistency
    private static final byte[] octetBytes = "octet".getBytes(StandardCharsets.UTF_8);
    public Server() throws SocketException{
        //Default TFTP socket
        //New socket detected when connected set up between Client and Server
        socket = new DatagramSocket(69);
        socket.setSoTimeout(5000);
    }

    public void runServer() {
        //Server implements Thread in order to be interruptable in its function by the user
        //Run server is the method run when the server is ready to revieve messages
        running = true;
        byte[] buffer = new byte[MAX_LENGTH];
        while (running) {
            //UI prompt to show server running
            System.out.println("...Listening (type -exit to stop)");
            DatagramPacket pack = new DatagramPacket(buffer, MAX_LENGTH);
            try {
                socket.receive(pack);
                //Data packet length unknown due to filename and (in other implementations) mode
                //Leaves one 0 byte at the end to create regular request packet
                pack = cleanRequest(pack);
            } catch (SocketTimeoutException i) {
                //Not receiving a packet is normal function
                continue;
            } catch (Exception e) {
                //Failing to receive packet is normal function, new connection can be established
                System.out.println("SERVER ERROR: Failed to retrieve packet");
                continue;
            }
            //Checks for WRQ and RRQ validity
            if (isValidRequest(pack)) {
                //WRQ and RRQ have the same instanciation
                createNewThread(pack);
            } else {
                //Packet not following TFTP protocol
                processIncorrectInstantiation(pack);
            }
        }
        //Server shut down when !running
        socket.close();
        System.out.println("Server has been shut down.");
    }
    public void exit(){
        running = false;
    }


    private void createNewThread(DatagramPacket p){
        ServerThread thread = null;
        try{
            //Server thread instanciated by packet and it is handled there
            thread = new ServerThread(p);
            thread.start();
        } catch (Exception e){
            System.out.println("SERVER ERROR: Failed start thread");
        }
    }
    private void processIncorrectInstantiation(DatagramPacket pack){
        //Created error packet
        //Likely too verbose for exam spec
        //Turns error message into bytes (won't be read anyway)
        byte[] errMsg = "Illegal TFTP Operation".getBytes(StandardCharsets.UTF_8);
        //Error message + opcode + errcode + 0 byte
        //Error data made
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
        //Opcode incorrect
        if (data[0] != 0 || (data[1] != 1 && data[1] != 2)){

            return false;
        }
        //Zero bytes after opcode
        //This is done as I had an issue with large amount of 0s appended due to oversized buffer
        int zeroBytes = 0;
        for (int i = 2; i < length; i++){
            if (data[i] == 0){
                zeroBytes++;
            }
        }

        if (zeroBytes != 2){
            return false;
        }

        return true;
    }

    public static void main(String[] args){
        //User interface


        Scanner scanner = new Scanner(System.in);
        System.out.println("------TFTP Protocol Server------");
        System.out.println("This is a server client which can accept several Net IO operations over the TFTP protocol");
        System.out.println("Files are stored in the Files folder in the working directory");
        System.out.println("To access please use the TFTP Protocol Client to access information");
        //Take input until a valid one provided
        boolean validInput = false;
        while(!validInput){
            System.out.println("COMMANDS: -start\n          -exit\nInput:");
            //input collected
            String argument = scanner.nextLine();
            //end with no server activation
            if (argument.equals("-exit")){
                validInput = true;
                break;
            }
            else if (argument.equals("-start")){
                //Server activated, outer loop no longer needed
                validInput = true;
                try {
                    //Instanciates server as thread
                    //This is in order to still take user input to allow user to end programme
                    Server server = new Server();
                    Thread serverThread = new Thread(server::runServer);
                    serverThread.start();
                    System.out.println("...Server started");
                    boolean exited = false;
                    //Await -exit command
                    while(!exited){
                        argument = scanner.nextLine();
                        if (argument.equals("-exit")){
                            exited=true;
                            server.exit();
                            //Server thread ended
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

    //Testing method when looking at packet byte array contents
    private static void printByteArray(byte[] bytes) {
        // Print each byte in the array as a two-digit hexadecimal number
        for (byte b : bytes) {
            System.out.print(String.format("%02X ", b)); // %02X ensures printing at least two digits, padding with zero if necessary
        }
        System.out.println(); // Print a newline after the array
    }

    //Method to quickly extract filename from WRQ or RRQ
    private static String extractFilename(DatagramPacket p) {
        byte[] data = p.getData();
        int length = p.getLength();
        StringBuilder filename = new StringBuilder();

        // Start reading after opcode
        for (int i = 2; i < length; i++) {
            //Stop on first 0 byte
            if (data[i] == 0) {
                break;
            }
            //Cast to char
            filename.append((char) data[i]);
        }

        return filename.toString();
    }

    private DatagramPacket cleanRequest(DatagramPacket p){
        //This method was a solution to the variable length of incoming RRQs and WRQs
        //Takes DP, removes all extra zeros on end of data buffer, reconstructs packet
        DatagramPacket packet = p;
        //Finds space used by filename
        String filename = extractFilename(p);
        byte[] data = p.getData();
        byte[] nameBytes = filename.getBytes(StandardCharsets.UTF_8);
        //Only this amount of bytes needed
        byte[] newData = new byte[nameBytes.length+ octetBytes.length+4];
        //Opcode the same as original packet
        newData[0] = data[0]; newData[1] = data[1];
        //Copy name after opcode (My explaination of System.arraycopy can be found elsewhere)
        System.arraycopy(nameBytes,0,newData,2,nameBytes.length);
        //zero-byte
        newData[nameBytes.length+2] = 0;
        //Copy "octet"
        System.arraycopy(octetBytes,0,newData,nameBytes.length+3,octetBytes.length);
        //Zero-byte
        newData[nameBytes.length+3+octetBytes.length] = 0;
        //Same packet (Address, port etc) different data
        packet.setData(newData);
        return packet;

    }



}