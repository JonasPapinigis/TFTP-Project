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
    private boolean running = false;
    public Server() throws SocketException{

        socket = new DatagramSocket(69);
        socket.setSoTimeout(5000);

    }

    public void start(){
        byte[] buffer = new byte[MAX_LENGTH];
        while(true){
            System.out.println("...Listening");
            DatagramPacket pack = new DatagramPacket(buffer,MAX_LENGTH);
            try {
                socket.receive(pack);
            } catch (SocketTimeoutException i) {
                continue;
            } catch (Exception e){
                System.out.print("SERVER ERROR: Failed to retrieve packet");
                continue;
            }
            if(isValidRequest(pack)){
                createNewThread(pack);
            }
            else{
                processIncorrectInstantiation(pack);
            }
        }
    }
    public void exit(){
        running = false;
    }


    private void createNewThread(DatagramPacket p){
        ServerThread thread = null;
        try{
            thread = new ServerThread(p);
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

        if (data[0] != 0 || data[1] != 1 || data[1] != 2){
            return false;
        }
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
                    server.start();
                    boolean exited = false;
                    while(!exited){
                        System.out.println("Type -exit to shut down server");
                        argument = scanner.nextLine();
                        if (argument.equals("-exit")){
                            exited=true;
                            server.interrupt();
                        }
                    }
                } catch (SocketException e) {
                    System.out.println("SERVER ERROR: Socket Error, failed to start server");
                    e.printStackTrace();
                    continue;
                }
            }
            else{
                System.out.println("SERVER ERROR: Invalid arguement");
            }
        }



    }

}