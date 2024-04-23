package org.example;

import org.w3c.dom.ranges.Range;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
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
            } catch (IOException e){
                //FIX
            }
            int opcode = Byte.valueOf(pack.getData()[0]).intValue();
            switch (opcode){
                case 1:
                    if (isValidRequest(pack)){processRRQ(pack);};
                case 2:
                    if (isValidRequest(pack)){processRRQ(pack);};
                case 3:
                case 4:
                case 5:
                    processIncorrectInstantiation(pack);

            }
        }
    }

    private void processRRQ(DatagramPacket pack){
        //Check Files for filename
    }
    private void processWRQ(DatagramPacket pack){

    }
    private void processIncorrectInstantiation(DatagramPacket pack){

    }

    private boolean isValidRequest(DatagramPacket pack){
        byte[] data = pack.getData();
        int length = pack.getLength();

        if (pack.getPort() != 69){
            return false;
        }

        int zeroByteIndex = -1;
        for (int i = 2; i < length; i++) {
            if (data[i] == 0) {
                zeroByteIndex = i;
                break;
            }
        }

        if (zeroByteIndex == -1) {
            return false;
        }

        // Check the mode (should be "octet")
        String mode = new String(data, zeroByteIndex + 1, length - zeroByteIndex - 2); // -2 to remove last zero byte
        if (!mode.equals("octet")) {
            return false;
        }

        return true;
    }

}