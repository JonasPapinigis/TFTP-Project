package org.example;

import org.w3c.dom.ranges.Range;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Random;

public class Server{
    private final static int MAX_LENGTH = 516;
    protected DatagramSocket socket = null;
    private boolean running = false;
    public Server() throws SocketException{

        socket = new DatagramSocket(69);

    }

    public void run(){
        byte[] buffer = new byte[MAX_LENGTH];
        running = true;
        while(running){
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
            System.out.println("SERVER: Recieving packet from "+
                    pack.getAddress()+ ", Port: "+ pack.getPort() +" not a WRQ or RRQ");
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

    }

}