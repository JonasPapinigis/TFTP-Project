package org.example;

import org.w3c.dom.ranges.Range;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Random;

public class Server{
    private static int TID;
    private final static int MAX_LENGTH = 516;
    protected DatagramSocket socket = null;
    public Server() throws SocketException{

        socket = new DatagramSocket(69);

    }

    public void run(){
        byte[] buffer = new byte[MAX_LENGTH];
        while(true){
            DatagramPacket pack = new DatagramPacket(buffer,MAX_LENGTH);
            try {
                socket.receive(pack);
            } catch (IOException e) {
                //FIX
            }
            if(isValidRequest(pack)){
                createNewThread(pack);
            }
            else{
                processIncorrectInstantiation(pack);
            }
        }
    }

    private void createNewThread(DatagramPacket p){
        ServerThread thread = null;
        try{
            thread = new ServerThread(p);
        } catch (Exception e){
            e.printStackTrace();
            System.out.println("SERVER: Error handling packet from "
                    + p.getAddress()+ ", Port: " + p.getPort());
            thread.interrupt();
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