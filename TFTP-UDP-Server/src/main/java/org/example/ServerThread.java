package org.example;

import com.sun.xml.internal.bind.v2.runtime.reflect.Lister;
import org.w3c.dom.ranges.Range;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.logging.Logger;


public class ServerThread extends Thread {

    private final static byte[] octetBytes = "octet".getBytes(StandardCharsets.UTF_8);
    private volatile boolean running = true;
    private int blockNumber = 0;
    private final static int MAX_PACKET = 516;
    private final static int MAX_DATA_LENGTH = 512;


    private final byte[] errorData = createErrorData();
    DatagramSocket socket;
    private InetAddress destAddress;
    private int destTID;
    private int localPort;

    private DatagramPacket initPacket;

    public ServerThread(DatagramPacket packet) throws SocketException {

        Random rand = new Random();
        socket = new DatagramSocket(rand.nextInt(10000) + 1000);

        int localPort = socket.getLocalPort();
        socket.setSoTimeout(10000);

        this.initPacket = packet;
        destAddress = initPacket.getAddress();
        destTID = initPacket.getPort();


    }

    private static byte[] createErrorData() {
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


    public void run() {
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

    public void processRRQ(DatagramPacket p) {
        createReadFile();
        File toRead = new File("Files", extractFilename(p));
        if (!toRead.exists()){
            sendErrAwaitClose();
        }
        else {
            boolean transferComplete = false;
            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(toRead))) {
                blockNumber++;
                System.out.println("SERVERTHREAD: Total Number of bytes to send: "+ bis.available());
                byte[] dataBuffer = new byte[MAX_DATA_LENGTH];
                byte[] sendBuffer = new byte[MAX_PACKET];
                int bytesRead;
                while (!transferComplete){
                    bytesRead = bis.read(dataBuffer);
                    if (bytesRead == -1) {
                        bytesRead = 0; // No more data to read, send a zero-length block
                        transferComplete = true;
                    }
                    sendBuffer[0] = 0;
                    sendBuffer[1] = 3; // TFTP DATA Opcode is 03
                    sendBuffer[2] = (byte) (blockNumber >> 8);
                    sendBuffer[3] = (byte) (blockNumber & 0xFF);
                    System.arraycopy(dataBuffer, 0, sendBuffer, 4, bytesRead);
                    DatagramPacket dataPacket = new DatagramPacket(sendBuffer, bytesRead + 4,
                            p.getAddress(), p.getPort());
                    boolean ackReceived = false;
                    while (!ackReceived){
                        System.out.println("SERVERTHREAD: Sending Packet " + blockNumber);
                        socket.send(dataPacket);
                        byte[] ackBuffer = new byte[4];  // ACK packet size
                        DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                        try {
                            System.out.println("SERVERTHREAD: Waiting to recieve ACK "+ blockNumber);
                            socket.receive(ackPacket);
                            System.out.println("SERVERTHREAD: ACK "+ blockNumber+ " recieved");
                            int ackBlockNumber = ((ackBuffer[2] & 0xFF) << 8) | (ackBuffer[3] & 0xFF);
                            if (ackBuffer[1] == 4 && ackBlockNumber == blockNumber) {  // Check for correct ACK
                                ackReceived = true;
                                blockNumber++;  // Prepare for the next block
                                if (bytesRead < MAX_DATA_LENGTH) {
                                    transferComplete = true;  // Last packet, end of data transmission
                                }
                            }
                        } catch (SocketTimeoutException e) {
                            System.out.println("Timeout, Resending Packet " + blockNumber);
                        }
                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Failed to read found file");
            }
        }

    }

    public void processWRQ(DatagramPacket p) {
        String filename = extractFilename(p);
        File directory = new File("Files");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        File toWrite = new File(directory, filename);

        byte[] ackData = {0, 4, (byte) (blockNumber >> 8), (byte) (blockNumber & 0xFF)};
        for (byte b : ackData) {
            System.out.print(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0') + " ");
        }
        System.out.println("ACK Data made: "+ destAddress + " - " + destTID);
        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, destAddress, destTID);

        try {
            socket.send(ackPacket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        blockNumber++;
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(toWrite))) {
            while (true) {
                System.out.println("BlockNo: "+blockNumber);
                byte[] dataBuffer = new byte[MAX_PACKET];
                DatagramPacket receivePacket = new DatagramPacket(dataBuffer, dataBuffer.length);
                System.out.println("Waiting to recieve packet");
                socket.receive(receivePacket);
                System.out.println("Recieved Packet");
                int length = receivePacket.getLength();
                int receivedBlockNumber = ((dataBuffer[2] & 0xFF) << 8) | (dataBuffer[3] & 0xFF);

                if (dataBuffer[1] == 3 && receivedBlockNumber == blockNumber + 1) {  // Data packet opcode
                    System.out.println("Recieved packet Contents: "+bytesToBinaryString(dataBuffer));
                    bos.write(dataBuffer, 4, length - 4);
                    blockNumber = receivedBlockNumber;
                }

                // Send ACK
                ackData[2] = (byte) (blockNumber >> 8);
                ackData[3] = (byte) (blockNumber & 0xFF);
                ackPacket = new DatagramPacket(ackData, ackData.length, destAddress, destTID);
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


    private String extractFilename(DatagramPacket p) {
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


    private void sendErrAwaitClose() {
        try {
            socket.send(new DatagramPacket(errorData, errorData.length, destAddress, destTID));
            DatagramPacket finalACK = new DatagramPacket(new byte[MAX_PACKET], MAX_PACKET);
        } catch (Exception e) {
            e.printStackTrace();}
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

    public void makeFiles() {
        File directory = new File("Files", "file.txt");
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }


    //Test Methods
    public int getTID() {
        return localPort;
    }

    public DatagramPacket getInitPacket() {
        return initPacket;
    }

    public DatagramSocket getSocket() {
        return socket;
    }


    public static void main(String[] args) {
        DatagramSocket receivingSocket = null;
        try{
            int serverPort = 9999;
            receivingSocket = new DatagramSocket(serverPort);
            receivingSocket.setSoTimeout(5000);

            //Create RRQ
            byte[] filename = "file.txt".getBytes(StandardCharsets.UTF_8);
            byte[] RRQData = new byte[4 + filename.length + octetBytes.length];
            RRQData[0] = 0; RRQData[1] = 1;
            System.arraycopy(filename,0,RRQData,2,filename.length);
            RRQData[filename.length + 2] = 0;
            System.arraycopy(octetBytes,0,RRQData,filename.length+3,octetBytes.length);
            RRQData[octetBytes.length+ filename.length+3] = 0;

            DatagramPacket RRQPacket = new DatagramPacket(RRQData, RRQData.length,
                    InetAddress.getByName("127.0.0.1"), 6969);
            RRQPacket.setPort(serverPort);


            ServerThread thread = new ServerThread(RRQPacket);
            thread.start();

            byte[] dataBuffer1 = new byte[MAX_PACKET];
            DatagramPacket dataPacket1 = new DatagramPacket(dataBuffer1,dataBuffer1.length);
            receivingSocket.receive(dataPacket1);
            if (dataBuffer1[0] != 0 && dataBuffer1[1] != 3){
                System.out.println("Incorrect Opcode");
            }

            byte[] ackData1 = {0,4,0,1};
            DatagramPacket ackPacket1 = new DatagramPacket(ackData1,ackData1.length,
                    InetAddress.getByName("127.0.0.1"), 6969);
            receivingSocket.send(ackPacket1);
            int blockNo1 = ((dataBuffer1[2] & 0xFF) << 8) | (dataBuffer1[3] & 0xFF);
            System.out.println("Recieved Block Number "+ blockNo1);
            printByteArrayInHex(dataBuffer1,4);

            byte[] dataBuffer2 = new byte[MAX_PACKET];
            DatagramPacket dataPacket2 = new DatagramPacket(dataBuffer1,dataBuffer1.length);
            receivingSocket.receive(dataPacket2);
            if (dataBuffer1[0] != 0 && dataBuffer1[1] != 3){
                System.out.println("Incorrect Opcode");
            }

            byte[] ackData2 = {0,4,0,2};
            DatagramPacket ackPacket2 = new DatagramPacket(ackData1,ackData1.length,
                    InetAddress.getByName("127.0.0.1"), 6969);
            receivingSocket.send(ackPacket1);
            int blockNo2 = ((dataBuffer1[2] & 0xFF) << 8) | (dataBuffer1[3] & 0xFF);
            System.out.println("Recieved Block Number "+ blockNo2);
            printByteArrayInHex(dataBuffer2,4);

            receivingSocket.close();

        } catch (Exception e){
            e.printStackTrace();
        }

    }


    public static String bytesToBinaryString(byte[] bytes) {
        StringBuilder binary = new StringBuilder();
        for (byte b : bytes) {
            binary.append(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0')).append(" ");
        }
        return binary.toString().trim();
    }

    public static byte[] createPacketData(int opcode, String filename, String mode) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0);
        baos.write(opcode); // WRQ is 2
        try {
            baos.write(filename.getBytes(StandardCharsets.UTF_8));
            baos.write(0);
            baos.write(mode.getBytes(StandardCharsets.UTF_8));
            baos.write(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return baos.toByteArray();
    }

    public static byte[] createDataPacket(int blockNumber, boolean fullSize) {
        int size = fullSize ? MAX_PACKET : MAX_PACKET / 2;
        byte[] data = new byte[size];
        data[0] = 0;
        data[1] = 3; // DATA opcode
        data[2] = (byte) (blockNumber >> 8);
        data[3] = (byte) (blockNumber & 0xFF);
        for (int i = 4; i < size; i++) {
            data[i] = (byte) (i % 2); // Filling with sample data
        }
        return data;
    }
    public static void printByteArrayInHex(byte[] byteArray, int offset) {
        for (int i = offset; i < byteArray.length; i++) {
            System.out.printf("%02X ", byteArray[i]); // %02X formats the byte as a two-digit hexadecimal number
        }
        System.out.println(); // Print a newline after all bytes are printed
    }

    public static void createReadFile() {
        String filePath = "Files/file.txt";
        byte[] sampleData = generateSampleData(716);

        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
            bos.write(sampleData);
            System.out.println("File created successfully: " + filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static byte[] generateSampleData(int totalBytes) {
        byte[] sampleData = new byte[totalBytes];
        for (int i = 0; i < totalBytes; i++) {
            // Fill the sample data with a pattern (e.g., ASCII characters)
            sampleData[i] = (byte) ('A' + (i % 26)); // Repeats A-Z pattern
        }
        return sampleData;
    }
}
/**
 *
 * Main Method to test WRQ
 *
 *
 * DatagramSocket clientSocket = null;
 *         try {
 *             int serverPort = 9999;
 *             // Set up the client socket
 *             clientSocket = new DatagramSocket(serverPort);
 *             InetAddress serverAddress = InetAddress.getByName("127.0.0.1");
 *             clientSocket.setSoTimeout(5000);
 *
 *             // Prepare the WRQ packet
 *             byte[] wrqData = createPacketData(2, "file.txt", "octet");
 *             DatagramPacket wrqPacket = new DatagramPacket(wrqData, wrqData.length, serverAddress, serverPort);
 *
 *             // Start the server thread
 *             ServerThread serverThread = new ServerThread(wrqPacket);
 *             serverThread.start();
 *
 *             // Receive ACK for WRQ
 *             byte[] ackBuffer1 = new byte[4];
 *             DatagramPacket ack1 = new DatagramPacket(ackBuffer1, ackBuffer1.length);
 *             clientSocket.receive(ack1);
 *
 *             // Send first data packet DATA1
 *             byte[] data1 = createDataPacket(1, true);
 *             DatagramPacket data1Packet = new DatagramPacket(data1, data1.length, serverAddress, 6969);
 *             clientSocket.send(data1Packet);
 *
 *             // Receive ACK for DATA1
 *             byte[] ackBuffer2 = new byte[4];
 *             DatagramPacket ack2 = new DatagramPacket(ackBuffer2, ackBuffer2.length);
 *             clientSocket.receive(ack2);
 *
 *             // Send second data packet DATA_TERM (terminal data packet)
 *             byte[] dataTerm = createDataPacket(2, false); // Smaller data size to signify end of data transfer
 *             DatagramPacket dataTermPacket = new DatagramPacket(dataTerm, dataTerm.length, serverAddress, 6969);
 *             clientSocket.send(dataTermPacket);
 *
 *             // Receive ACK for DATA_TERM
 *             byte[] ackBuffer3 = new byte[4];
 *             DatagramPacket ack3 = new DatagramPacket(ackBuffer3, ackBuffer3.length);
 *             clientSocket.receive(ack3);
 *
 *             System.out.println(bytesToBinaryString(ackBuffer1));
 *             System.out.println(bytesToBinaryString(ackBuffer2));
 *             System.out.println(bytesToBinaryString(ackBuffer3));
 *
 *
 *             serverThread.join();
 *
 *
 *         } catch (Exception e) {
 *             e.printStackTrace();
 *         } finally {
 *             if (clientSocket != null && !clientSocket.isClosed()) {
 *                 clientSocket.close();
 *             }
 *         }
 *     }
 *
 *
 *
 *
 *
 *
 * */





