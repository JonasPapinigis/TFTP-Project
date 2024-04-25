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
        //Random TID decided as per spec, above 1000 administrative permissions range
        Random rand = new Random();
        TID = rand.nextInt(10000) + 1000;
        try {
            socket = new DatagramSocket(TID);
            socket.setSoTimeout(5000);
        } catch (SocketException e) {
            System.out.println("CLIENT: Failed to create socket "+ TID);}
    }

    public boolean fileRequest(InetAddress targetAddress, String filename){
        //method returns boolean to indicate correct function
        //Block number starts at 1 as the first response we're waiting for will be a datapacket under normal function
        int blockNumber = 1;
        //Default TFTP TID
        int targetTID = 69;
        boolean transferComplete = false;

        //Creates a matching file in Files directory
        File files = new File("Files");
        if (!files.isDirectory()){
            files.mkdirs();
        }
        File file = new File("Files/", filename);
        if (!file.exists()){
            try {
                file.createNewFile();
            } catch (IOException e) {
                //Crucial error: terminate method
                System.out.println("CLIENT: Failed to make output file");
                return false;
            }
        }
        //Craetes request
        byte[] RRQData = createReqData(filename,1);
        DatagramPacket requestPacket = new DatagramPacket(RRQData,RRQData.length,targetAddress,targetTID);
        try {
            //Sends packe
            socket.send(requestPacket);
        } catch (IOException e) {
            System.out.println("CLEINT: Failed to send request packet");
            return false;
        }
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))){

            while(!transferComplete){
                //New data packet for receipt of DATA
                DatagramPacket rcvPacket = createDataPacket();
                socket.receive(rcvPacket);
                byte[] rcvData = rcvPacket.getData();
                int rcvLength = rcvPacket.getLength();
                //Set TID to new port provided by server as per TFTP protocol
                targetTID = rcvPacket.getPort();
                //Packet validity checks
                if (rcvLength < 4 || rcvLength > DATAPACK_LENGTH) {
                    System.out.println("CLIENT: Data pack wrong length length");
                    return false;
                }

                //Correct opcode
                if (rcvData[0] == 0 || rcvData[1] == 3) {
                    //Check blockNo
                    int receivedBlockNumber = ((rcvData[2] & 0xFF) << 8) | (rcvData[3] & 0xFF);
                    //Correct blockNo found
                    if (receivedBlockNumber == blockNumber){
                        //ACK received data
                        byte[] ackBuff = {0, 4, (byte) (blockNumber >> 8), (byte) (blockNumber & 0xFF)};
                        DatagramPacket ackPacket = new DatagramPacket(ackBuff, ackBuff.length, targetAddress, targetTID);
                        //Write to file before possible Socket exception
                        bos.write(rcvData,4, rcvLength - 4);
                        socket.send(ackPacket);
                        //Increase blockNo after successful send
                        blockNumber++;
                    }
                    else if(receivedBlockNumber == blockNumber-1){
                        //If ACK not received, send ACK with prev. blockNo
                        byte[] ackBuff = {0, 4, (byte) (blockNumber-1 >> 8), (byte) (blockNumber-1 & 0xFF)};
                        DatagramPacket ackPacket = new DatagramPacket(ackBuff, ackBuff.length, targetAddress, targetTID);
                        socket.send(ackPacket);
                    }
                    else{
                        //If blockNo is not the current or previous, outside of TFTP protocol rules
                        //End request
                        System.out.println("CLIENT: Invalid block number");
                        sendErrPacket(targetAddress,targetTID);
                        return false;
                    }
                    //Read complete
                    if (rcvLength - 4 < MAX_READ) {
                        transferComplete = true;
                    }
                }

            }
        } catch (Exception e){
            System.out.println("CLIENT: Failed to read from Server");
            e.printStackTrace();
            return false;
        }
        //File correctly sent
        return true;
    }

    public boolean fileSend(InetAddress targetAddress, String filename){
        //BlockNo starts at 0 as expecting ACK blockNo = 0 to confirm WRQ
        int blockNumber = 0;
        //Default TID
        int targetTID = 69;
        //Tracking where we have written up to
        int bytesRead = 0;
        boolean transferComplete = false;
        //If file does not exist, fatal error, stop write
        File file = new File("Files", filename+".txt");
        if (!file.exists()){
            System.out.println(file.getPath()+  " does not exist in /Files");
            return false;
        }
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))){
            //Create WRQ, send
            byte[] WRQData = createReqData(filename,2);
            DatagramPacket requestPacket = new DatagramPacket(WRQData,WRQData.length,targetAddress,targetTID);
            socket.send(requestPacket);

            //Await ACK
            byte[] ackBuffer = new byte[4];
            DatagramPacket ackPacket = new DatagramPacket(ackBuffer,ackBuffer.length);
            socket.receive(ackPacket);

            //Change TID to new one provided in ACK packet's IP field
            targetTID = ackPacket.getPort();
            byte[] ackData = ackPacket.getData();
            //find blockNo
            int receivedBlockNumber = ((ackData[2] & 0xFF) << 8) | (ackData[3] & 0xFF);

            //Connection not yet started, can terminate upon unsuccessful handshake (receivedblockNo != blockNumber)
            if (ackData.length !=4 || ackData[0] != 0 || ackData[1] != 4 || receivedBlockNumber != blockNumber){
                System.out.println("CLIENT: Invalid ACK: Expct="+blockNumber+ "  Rcvd="+ receivedBlockNumber);
                return false;
            }
            //Data to be send
            byte[] sendData = new byte[DATAPACK_LENGTH];
            //Buffer for reader
            byte[] bufferedRead = new byte[MAX_READ];
            blockNumber = 1;
            while (!transferComplete) {
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


                int retryCount = 0;
                boolean ackReceived = false;
                //Data packet sends limited to 4
                while (!ackReceived && retryCount < 5) {
                    try {
                        socket.receive(ackPacket);
                        ackBuffer = ackPacket.getData();

                        //Get blockNo
                        int ackBlockNumber = ((ackBuffer[2] & 0xFF) << 8) | (ackBuffer[3] & 0xFF);

                        //Correct data ACKed
                        if (ackBuffer[1] == 4 && ackBlockNumber == blockNumber) {
                            blockNumber++;
                            ackReceived = true;
                        }
                        //Previous block still requires data, resend
                        else if (ackBuffer[1] == 4 && ackBlockNumber == blockNumber-1){
                            retryCount++;
                            socket.send(sendPacket);
                        }   else{
                            //Out of range BlockNo
                            System.out.println("CLIENT: Invalid block number");
                            sendErrPacket(targetAddress,targetTID);
                            return false;
                        }
                    } catch (SocketTimeoutException ste) {
                        //Retry process if socket times out waiting for ACK
                         continue;
                    }
                }

                if (!ackReceived) {
                    //Abnormal termination
                    System.out.println("CLIENT: ACK not received for block# " + blockNumber);
                    return false;
                }
            }
        } catch (Exception e){
            //General write failiure, print stack trace for more details
            System.out.println("CLIENT: Failed to read from Server");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public byte[] createReqData(String filename, int mode){
        //Mode: 1 = RRQ, 2 = WRQ
        //Processes similar enough, created method to save time
        //filename -> byte[]
        byte[] nameToBytes = filename.getBytes(StandardCharsets.UTF_8);
        //Variable byte arrays knows,instantiate buffer
        int dataLength = nameToBytes.length + octetBytes.length + 4;
        byte[] buffer = new byte[dataLength];
        buffer[0] = 0;
        //RRQ or WRQ
        if (mode == 1 || mode == 2) {
            buffer[1] = (byte) mode;
        }
        else{
            System.out.println("CLIENT: Invalid mode for request creation");
            return null;
        }

        //Copy strings into byte array with opcodes and zero-bytes
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
        //Not much variability with error packet, hence method
        //Did not add as much detail as server implementation due to it not being needed
        byte[] errMsg = "ERROR".getBytes(StandardCharsets.UTF_8);
        byte[] buffer = new byte[errMsg.length + 5];
        buffer[0] = 0; buffer[1] = 5; buffer[2] = 0; buffer[3] = 0;
        //Copy error message
        System.arraycopy(errMsg,0,buffer,4,errMsg.length);
        buffer[errMsg.length + 4] = 0;
        //New Packet
        DatagramPacket errPacket = new DatagramPacket(buffer,buffer.length,address,TID);
        //Automatically send packet, as none are held onto
        try {socket.send(errPacket);}
        catch(IOException i){System.out.println("CLIENT: Failed sending ERR packet to "+ address+ ", Port: "+ TID);}

    }

    public static void main(String[] args){
        //User interface

        boolean running = true;
        Scanner scanner = new Scanner(System.in);
        InetAddress address = null;
        //New Client
        ClientController client = new ClientController();
        System.out.println("------TFTP Protocol Client------");
        //Instructions as static String[] to be able to bring them up when needed
        printAll(instructions);

        while(running){
            //Get Args
            System.out.print("Input: ");
            String inputLine = scanner.nextLine();
            //split by space
            String[] arguements = inputLine.split("\\s+");

            //Exit
            if (arguements.length == 1 && arguements[0].equals("-exit")){
                running = false;
            }
            //Help: print instrcution String[]
            else if (arguements.length == 1 && arguements[0].equals("-help")){
                printAll(instructions);
            }
            //-Generate: takes filename from arg to, parses 3rd for byte size
            //Creates sample file in Files to be used in exchanges with Server
            else if (arguements.length == 3 && arguements[0].equals("-generate")){
                if (generateFile(arguements[1],Integer.parseInt(arguements[2]))){
                    System.out.println("CLIENT: File generated Successfully");
                }
                else{
                    System.out.println("ERROR: File failed to generate/ Invalid arguments");
                }
            }
            //-Send: checks if filename of 2nd arguement present in Files. Scans 3rd arg for IP (InetAddress obj)
            // Runs client's send function
            else if (arguements.length == 3 && arguements[0].equals("-send")){
                try {
                    address = InetAddress.getByName(arguements[2]);
                } catch (UnknownHostException e) {
                    System.out.println("ERROR: Invalid host address");
                    continue;
                }
                //Uses boolean output of method to indicate correct function
                if (client.fileSend(address,arguements[1])){
                    System.out.println("CLIENT: File sent successfully");
                }
                else {
                    System.out.println("ERROR: Failed to send file");
                }
                //Continues whether successful or not
                continue;
            }
            //-request: same as send, args used to pass filename and InetAddress into ClientController.fileRequest()
            else if (arguements.length == 3 && arguements[0].equals("-request")){
                try {
                    address = InetAddress.getByName(arguements[2]);
                } catch (UnknownHostException e) {
                    System.out.println("ERROR: Invalid host address");
                    continue;
                }
                //Boolean used to judge success of operation
                if(client.fileRequest(address,arguements[1])){
                    System.out.println("CLIENT: File recieved successfully");
                }
                else {
                    System.out.println("ERROR: Failed to retrieve file");
                }
            }
            else{
                System.out.println("ERROR: Invalid command");

            }
            continue;


        }
    }
    //Instruction array
    private static String[] instructions = {"COMMANDS: -generate [filename] [num] \n\t\t\t\t\t Will generate a sample .txt file of num length in " +
            "\n\t\t\t\t\t the Files folder in the working directory\n","         -send [filename] [host] \n\t\t\t\t\t If present in Files, will send filename.txt to specified host over" +
            "TFTP protocol \n\t\t\t\t\t use localhost as ip if sending to 127.0.0.1\n","         -request [filename] [host]\n\t\t\t\t\t If available, will copy filename.txt from the host server over TFTP protcol" +
            "\n\t\t\t\t\t use localhost as ip if sending to 127.0.0.1\n\n", "         -exit\n\n","         -help\n\n" };

    //Used to print all in String[]
    private static void printAll(String[] lines){
        for (String str : lines)
        {
            System.out.print(str+" ");
        }
        System.out.println();
    }

    //Used for -Generate
    public static boolean generateFile(String filename, int len) {
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


    //Test method used to observe contents of packets' data
    private void printByteArray(byte[] bytes) {
        // Print each byte in the array as a two-digit hexadecimal number
        for (byte b : bytes) {
            System.out.print(String.format("%02X ", b)); // %02X ensures printing at least two digits, padding with zero if necessary
        }
        System.out.println(); // Print a newline after the array
    }

}
