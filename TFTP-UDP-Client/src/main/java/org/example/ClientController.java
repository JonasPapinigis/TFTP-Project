package org.example;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Scanner;
import java.net.InetAddress;


public class ClientController {
    private int TID;
    private final static int MAX_READ = 512;
    private final static int DATAPACK_LENGTH = 516;
    private final byte[] octetBytes = "octet".getBytes(StandardCharsets.UTF_8);
    protected DatagramSocket socket = null;
    public ClientController(){
        Random rand = new Random();
        TID = rand.nextInt(10000) + 1000;
        try {
            socket = new DatagramSocket(TID);
            socket.setSoTimeout(5000);
        } catch (SocketException e) {
            System.out.println("CLIENT: Failed to create socket "+ TID);
        }
    }

    public boolean fileRequest(InetAddress targetAddress, String filename){
        int blockNumber = 1;
        int targetTID = 69;
        boolean transferComplete = false;
        File file = new File("Files/", filename);
        if (!file.exists()){
            file.mkdirs();
        }
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))){


            byte[] RRQData = createReqData(filename,1);
            DatagramPacket requestPacket = new DatagramPacket(RRQData,RRQData.length,targetAddress,targetTID);
            socket.send(requestPacket);

            while(!transferComplete){
                DatagramPacket rcvPacket = createDataPacket();
                socket.receive(rcvPacket);
                if (!isValidDataPacket(rcvPacket,blockNumber)){
                    System.out.println("CLIENT: Invalid Data Packet rcv");
                    return false;
                }
                byte[] rcvData = rcvPacket.getData();
                int rcvLength = rcvPacket.getLength();
                if (targetTID == 69){
                    targetTID = rcvPacket.getPort();
                }
                bos.write(rcvData,4, rcvLength - 4);

                byte[] ackBuff = new byte[4];

                ackBuff[0] = 0; ackBuff[1] = 3;
                ackBuff[2] = (byte) (blockNumber >> 8);
                ackBuff[3] = (byte) (blockNumber & 0xFF);

                DatagramPacket ackPacket = new DatagramPacket(ackBuff, ackBuff.length, targetAddress, TID);
                socket.send(ackPacket);

                blockNumber++;

                if (rcvLength - 4 < MAX_READ) {
                    transferComplete = true;
                }

            }
        } catch (Exception e){
            System.out.println("CLIENT: Failed to read from Server");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean fileSend(InetAddress targetAddress, String filename){
        int blockNumber = 0;
        int targetTID = 69;
        int bytesRead = 0;
        boolean transferComplete = false;
        File file = new File("Files", filename+".txt");
        if (!file.exists()){
            System.out.println(file.getPath()+  " does not exist in /Files");
            return false;
        }
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))){

            byte[] WRQData = createReqData(filename,2);
            printByteArray(WRQData);
            DatagramPacket requestPacket = new DatagramPacket(WRQData,WRQData.length,targetAddress,targetTID);
            socket.send(requestPacket);

            byte[] ackBuffer = new byte[4];
            DatagramPacket ackPacket = new DatagramPacket(ackBuffer,ackBuffer.length);
            socket.receive(ackPacket);

            targetTID = ackPacket.getPort();
            System.out.println("New TID: "+targetTID);
            //Validate  WRQ ACK
            byte[] ackData = ackPacket.getData();
            System.out.print("Block:"+blockNumber+" ACK:");
            printByteArray(ackData);

            int receivedBlockNumber = ((ackData[2] & 0xFF) << 8) | (ackData[3] & 0xFF);
            if (ackData.length !=4 || ackData[0] != 0 || ackData[1] != 4 || receivedBlockNumber != blockNumber){
                System.out.println("CLIENT: Invalid ACK: Expct="+blockNumber+ "  Rcvd="+ receivedBlockNumber);
                return false;
            }
            byte[] sendData = new byte[DATAPACK_LENGTH];
            byte[] bufferedRead = new byte[MAX_READ];
            while (!transferComplete) {
                blockNumber++;
                bytesRead = bis.read(bufferedRead);  // Read up to MAX_READ bytes into bufferedRead
                if (bytesRead == -1) {
                    break;  // End of file reached
                }

                sendData[0] = 0; sendData[1] = 3;  // DATA opcode
                sendData[2] = (byte) (blockNumber >> 8);
                sendData[3] = (byte) (blockNumber & 0xFF);
                System.arraycopy(bufferedRead, 0, sendData, 4, bytesRead);

                DatagramPacket sendPacket = new DatagramPacket(sendData, 4 + bytesRead, targetAddress, targetTID);
                socket.send(sendPacket);

                // ACK reception logic with retry and timeout handling
                int retryCount = 0;
                boolean ackReceived = false;
                while (!ackReceived && retryCount < 5) {
                    try {
                        socket.receive(ackPacket);
                        ackBuffer = ackPacket.getData();
                        int ackBlockNumber = ((ackBuffer[2] & 0xFF) << 8) | (ackBuffer[3] & 0xFF);
                        if (ackBuffer[1] == 4 && ackBlockNumber == blockNumber) {
                            ackReceived = true;
                        } else {
                            retryCount++;
                            socket.send(sendPacket);  // Resend the packet
                        }
                    } catch (SocketTimeoutException ste) {
                        retryCount++;
                        if (retryCount < 5) socket.send(sendPacket);  // Resend the packet
                    }
                }

                if (!ackReceived) {
                    System.out.println("CLIENT: ACK not received for block# " + blockNumber);
                    return false;
                }
            }
        } catch (Exception e){
            System.out.println("CLIENT: Failed to read from Server");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public byte[] createReqData(String filename, int mode){
        //Mode: 1 = RRQ, 2 = WRQ
        byte[] nameToBytes = filename.getBytes(StandardCharsets.UTF_8);
        int dataLength = nameToBytes.length + octetBytes.length + 4;
        byte[] buffer = new byte[dataLength];
        buffer[0] = 0;
        if (mode == 1 || mode == 2) {
            buffer[1] = (byte) mode;
        }
        else{
            System.out.println("CLIENT: Invalid mode for request creation");
            return null;
        }
        System.arraycopy(nameToBytes,0,buffer,2,nameToBytes.length);
        buffer[nameToBytes.length+2] = 0;
        System.arraycopy(octetBytes,0,buffer,nameToBytes.length+3,octetBytes.length);
        buffer[dataLength-1] = 0;

        return buffer;
    }

    public DatagramPacket createDataPacket(){
        byte[] buffer = new byte[DATAPACK_LENGTH];
        DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
        return dp;
    }

    private void sendErrPacket(InetAddress address, int TID){
        byte[] errMsg = "ERROR".getBytes(StandardCharsets.UTF_8);
        byte[] buffer = new byte[errMsg.length + 5];
        buffer[0] = 0; buffer[1] = 5; buffer[2] = 0; buffer[3] = 0;
        System.arraycopy(errMsg,0,buffer,4,errMsg.length);
        buffer[errMsg.length + 4] = 0;

        DatagramPacket errPacket = new DatagramPacket(buffer,buffer.length,address,TID);
        try {socket.send(errPacket);}
        catch(IOException i){System.out.println("CLIENT: Failed sending ERR packet to "+ address+ ", Port: "+ TID);}

    }

    private boolean isValidDataPacket(DatagramPacket p, int blockNumber){
        byte[] data = p.getData();

        if (data.length < 4 || data.length > DATAPACK_LENGTH) {
            return false;
        }

        if (data[0] != 0 || data[1] != 3) {return false;}

        int receivedBlockNumber = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);

        return receivedBlockNumber == blockNumber;
    }

    public static void main(String[] args){
        boolean running = true;
        Scanner scanner = new Scanner(System.in);
        InetAddress address = null;
        ClientController client = new ClientController();
        System.out.println("------TFTP Protocol Client------");
        printAll(instructions);

        while(running){

            System.out.print("Input: ");
            String inputLine = scanner.nextLine();
            String[] arguements = inputLine.split("\\s+");
            printAll(arguements);
            if (arguements.length == 1 && arguements[0].equals("-exit")){
                running = false;
            }
            else if (arguements.length == 1 && arguements[0].equals("-help")){
                printAll(instructions);
            }
            else if (arguements.length == 3 && arguements[0].equals("-generate")){
                if (generateFile(arguements[1],Integer.parseInt(arguements[2]))){
                    System.out.println("CLIENT: File generated Successfully");
                }
                else{
                    System.out.println("ERROR: File failed to generate/ Invalid arguments");
                }
            }
            else if (arguements.length == 3 && arguements[0].equals("-send")){
                try {
                    address = InetAddress.getByName(arguements[2]);
                } catch (UnknownHostException e) {
                    System.out.println("ERROR: Invalid host address");
                    continue;
                }
                if (client.fileSend(address,arguements[1])){
                    System.out.println("CLIENT: File sent successfully");
                }
                else {
                    System.out.println("ERROR: Failed to send file");
                }
                continue;
            }
            else if (arguements.length == 3 && arguements[0].equals("-request")){
                try {
                    address = InetAddress.getByName(arguements[2]);
                } catch (UnknownHostException e) {
                    System.out.println("ERROR: Invalid host address");
                    continue;
                }
                if(client.fileRequest(address,arguements[1])){
                    System.out.println("CLIENT: File recieved successfully");
                }
                else {
                    System.out.println("ERROR: Failed to retrieve file");
                }
            }
            else{
                System.out.println("ERROR: Invalid command");
                continue;
            }


        }
    }

    private static String[] instructions = {"COMMANDS: -generate [filename] [num] \n\t\t\t\t\t Will generate a sample .txt file of num length in " +
            "\n\t\t\t\t\t the Files folder in the working directory\n","         -send [filename] [host] \n\t\t\t\t\t If present in Files, will send filename.txt to specified host over" +
            "TFTP protocol \n\t\t\t\t\t use localhost as ip if sending to 127.0.0.1\n","         -request [filename] [host]\n\t\t\t\t\t If available, will copy filename.txt from the host server over TFTP protcol" +
            "\n\t\t\t\t\t use localhost as ip if sending to 127.0.0.1\n\n", "         -exit\n\n","         -help\n\n" };
    private static void printAll(String[] lines){
        for (String str : lines)
        {
            System.out.print(str+" ");
        }
        System.out.println();
    }
    private static boolean isFile(String filename){
        File file = new File("Files/", filename+".txt");
        System.out.println(file.getPath());
        return file.exists();
    }
    public static boolean generateFile(String filename, int len) {
        // Specify the directory and filename.
        File file = new File("Files", filename + ".txt");

        // Ensure the directory exists
        file.getParentFile().mkdirs();

        try (FileOutputStream fos = new FileOutputStream(file)) {
            // Sample data pattern to write to the file
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

    private void printByteArray(byte[] bytes) {
        // Print each byte in the array as a two-digit hexadecimal number
        for (byte b : bytes) {
            System.out.print(String.format("%02X ", b)); // %02X ensures printing at least two digits, padding with zero if necessary
        }
        System.out.println(); // Print a newline after the array
    }

}
