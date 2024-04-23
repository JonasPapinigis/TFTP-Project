package org.example;

import org.w3c.dom.ranges.Range;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Random;


public class ServerThread extends Thread{
    private volatile boolean running = true;
    private int blockNumber = 0;
    private final static int MAX_PACKET = 516;
    private final static int MAX_DATA_LENGTH = 512;


    private final byte[] errorData = createErrorData();
    DatagramSocket socket;
    private InetAddress destAddress; private int destTID;

    private DatagramPacket initPacket;
    public ServerThread (DatagramPacket packet) throws SocketException{
        Random rand = new Random();

        socket = new DatagramSocket(rand.nextInt(10000) + 1000);
        int localPort = socket.getLocalPort();
        socket.setSoTimeout(10000);

        this.initPacket = packet;
        destAddress = initPacket.getAddress();
        destTID = initPacket.getPort();


    }

    private static byte[] createErrorData(){
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Write the opcode for error
        baos.write(0); // First byte of the opcode
        baos.write(5); // Second byte of the opcode (05 for error)

        // Write the error code
        baos.write(0); // First byte of the error code
        baos.write(1); // Second byte of the error code (01 for "File Not Found")

        // Write the error message
        try {
            baos.write("File Not Found".getBytes(StandardCharsets.UTF_8));
            baos.write(0); // Null byte to terminate the error message
        } catch (Exception e) {
            System.err.println("Error writing the error message.");
        }

        return baos.toByteArray();

    }



    public void run(){
        int opcode = ((initPacket.getData()[0] & 0xFF) << 8) | (initPacket.getData()[1] & 0xFF);

        // Use a switch-case statement to catch specific opcodes
        switch (opcode) {
            case 0x0001:
                processRRQ(initPacket);
                break;
            case 0x0002:
                processWRQ(initPacket);
                break;
            default:
                System.out.println("Unknown opcode.");
                break;
        }
    }

    public DatagramPacket processRRQ(DatagramPacket p){
        String filename = extractFilename(p);
        if (checkFileInFolder(filename)){
            File toRead = new File("Files",filename);
            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(toRead))) {

                socket.setSoTimeout(10000); // Set a timeout of 10 seconds to wait for an ACK

                byte[] readBuffer = new byte[MAX_DATA_LENGTH];
                byte[] sendBuffer = new byte[MAX_PACKET];
                int bytesRead;
                int blockNumber = 1;

                while ((bytesRead = bis.read(readBuffer)) != -1) {
                    sendBuffer[0] = 0;
                    sendBuffer[1] = 3; // Opcode for DATA is 0x0003
                    sendBuffer[2] = (byte) (blockNumber >> 8);
                    sendBuffer[3] = (byte) (blockNumber & 0xFF);

                    System.arraycopy(readBuffer, 0, sendBuffer, 4, bytesRead);

                    DatagramPacket sendPacket = new DatagramPacket(sendBuffer, bytesRead + 4, destAddress, destTID);
                    boolean ackReceived = false;

                    while (!ackReceived) {
                        socket.send(sendPacket);

                        // Prepare to receive the ACK
                        byte[] ackBuffer = new byte[4]; // ACK packet size
                        DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);

                        try {
                            socket.receive(ackPacket);
                            // Check if the ACK is for the correct block and has the right opcode
                            if (ackBuffer[1] == 4 && // Opcode for ACK is 0x0004
                                    ackBuffer[2] == sendBuffer[2] && ackBuffer[3] == sendBuffer[3]) {
                                ackReceived = true; // Correct ACK received
                            }
                        } catch (SocketException e) {
                            System.out.println("Timeout, resending data block " + blockNumber);
                            // Timeout will re-trigger sending the packet
                        }
                    }

                    if (bytesRead < MAX_DATA_LENGTH) {
                        break; // Last packet, end of data transmission
                    }

                    blockNumber++; // Increment block number for the next packet
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        else{
            sendErrAwaitClose();
        }
    }

    public void processWRQ(DatagramPacket p) {
        String filename = extractFilename(p);
        File directory = new File("Files");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        File toWrite = new File(directory, filename);

        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(toWrite))) {
            int blockNumber = 0;
            while (true) {
                byte[] dataBuffer = new byte[MAX_PACKET];
                DatagramPacket receivePacket = new DatagramPacket(dataBuffer, dataBuffer.length);
                socket.receive(receivePacket);
                int length = receivePacket.getLength();
                int receivedBlockNumber = ((dataBuffer[2] & 0xFF) << 8) | (dataBuffer[3] & 0xFF);

                if (dataBuffer[1] == 3 && receivedBlockNumber == blockNumber + 1) {  // Data packet opcode
                    bos.write(dataBuffer, 4, length - 4);
                    blockNumber = receivedBlockNumber;
                }

                // Send ACK
                byte[] ackData = {0, 4, (byte) (blockNumber >> 8), (byte) (blockNumber & 0xFF)};
                DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, destAddress, destTID);
                socket.send(ackPacket);

                if (length < MAX_PACKET) {
                    break;  // Last packet, as it is less than the maximum packet size
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendErrAwaitClose();
        }
    }

    public DatagramPacket processERROR(DatagramPacket p){

    }


    public void writeTofile(byte[] name, byte[] data){

    }

    public byte[] readFromFile(String filename){
        return null;
    }

    public boolean checkFileAvailable(String string){

    }

    private String extractFilename(DatagramPacket p){
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

    private void terminate() {
        this.running = false;
        if (!socket.isClosed()) {
            socket.close();
        }
    }

    private void sendErrAwaitClose() {
        try {
            socket.send(new DatagramPacket(errorData, errorData.length, destAddress, destTID));
            DatagramPacket finalACK = new DatagramPacket(new byte[MAX_PACKET], MAX_PACKET);
            socket.receive(finalACK);  // Waiting to receive final ACK before closing
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            terminate();
        }
    }

    private boolean recieveData(){

    }

    private boolean sendData(){

    }

    public static boolean checkFileInFolder(String fileName) {
        // Define the directory path for the "Files" folder
        String directoryPath = "Files";
        File directory = new File(directoryPath);

        // Check if the "Files" directory exists
        if (!directory.exists() || !directory.isDirectory()) {
            System.out.println("Directory does not exist.");
            return false;
        }

        // Define the file path within the "Files" directory
        File file = new File(directory, fileName);

        // Check if the file exists within the "Files" directory
        if (file.exists() && file.isFile()) {
            System.out.println("File exists.");
            return true;
        } else {
            System.out.println("File does not exist.");
            return false;
        }
    }
}


