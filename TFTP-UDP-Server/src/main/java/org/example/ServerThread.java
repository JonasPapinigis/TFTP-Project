package org.example;

import com.sun.xml.internal.bind.v2.runtime.reflect.Lister;
import org.w3c.dom.ranges.Range;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.logging.Logger;


public class ServerThread extends Thread {
    //I added the octet mode identifier despite mode being unchanging for TFTP protocol consistency
    private final static byte[] octetBytes = "octet".getBytes(StandardCharsets.UTF_8);
    private volatile boolean running = true;
    private int blockNumber = 0;
    private final static int MAX_PACKET = 516;
    private final static int MAX_DATA_LENGTH = 512;


    private final byte[] errorData = createErrorData();
    DatagramSocket socket;
    private InetAddress destAddress;
    private int destTID;
    private int TID;

    private DatagramPacket initPacket;

    public ServerThread(DatagramPacket packet){
        //Instanciates socket outside of 1000 range to avoid administrative permission
        //Changes socket to not impede on main server function at 69
        //New socket detected from second packet in Client
        Random rand = new Random();
        TID = rand.nextInt(10000) + 1000;
        try {
            socket = new DatagramSocket(TID);
        } catch (SocketException e){
            System.out.println("SERVER THREAD: Failed to instanciate socket: " + TID);
        }

        //Thread is launched via WRQ/RRQ parameter as those are the only ones that lead to normal function
        //Data is extracted from first parameter
        this.initPacket = packet;
        destAddress = initPacket.getAddress();
        destTID = initPacket.getPort();

    }




    public void run() {
        //Not sure why I used this implementation
        int opcode = ((initPacket.getData()[0] & 0xFF) << 8) | (initPacket.getData()[1] & 0xFF);

        //Only correct routes are handling RRQ/WRQ
        switch (opcode) {
            case 0x0001:
                processRRQ(initPacket);
                break;
            case 0x0002:
                processWRQ(initPacket);
                break;
            default:
                System.out.println("Unknown opcode.");
                sendErrAwaitClose();
                break;
        }
    }

    public void processRRQ(DatagramPacket p) {
        //Check if file exists to read
        File toRead = new File("Files", extractFilename(p));
        if (!toRead.exists()){
            sendErrAwaitClose();
        }
        else {
            //Repeat until no more bytes left
            boolean transferComplete = false;
            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(toRead))) {
                //Read requests responded to at BlockNo 1, increment happens at start in any case
                blockNumber++;
                System.out.println("SERVERTHREAD: Total Number of bytes to send: "+ bis.available());
                //Used for packet
                byte[] dataBuffer = new byte[MAX_DATA_LENGTH];
                //Used for file reading
                byte[] sendBuffer = new byte[MAX_PACKET];
                //Tracking point at which the file-reading has gotten to
                int bytesRead;

                while (!transferComplete ){
                    //Number updated by .read()
                    bytesRead = bis.read(dataBuffer);
                    //If no more is left in file, succesful termination
                    if (bytesRead == -1) {
                        bytesRead = 0;
                        transferComplete = true;
                    }
                    sendBuffer[0] = 0; sendBuffer[1] = 3; //Opcode
                    //BlockNo
                    sendBuffer[2] = (byte) (blockNumber >> 8);
                    sendBuffer[3] = (byte) (blockNumber & 0xFF);
                    //Copy databuffer from pos 0 to send buffer after opcode + blockno (pos 4) at bytes read. bytesRead = 0 if no more to send. Empty DP
                    System.arraycopy(dataBuffer, 0, sendBuffer, 4, bytesRead);
                    DatagramPacket dataPacket = new DatagramPacket(sendBuffer, bytesRead + 4,
                            p.getAddress(), p.getPort());
                    boolean ackReceived = false;
                    int timesSent = 0;

                    //Data sent until ACK received or sent 5 times. Abnormal termination
                    while (!ackReceived && timesSent < 6){
                        socket.send(dataPacket);
                        //new ACK
                        byte[] ackBuffer = new byte[4];
                        DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);

                        try {
                            socket.receive(ackPacket);
                            int ackBlockNumber = ((ackBuffer[2] & 0xFF) << 8) | (ackBuffer[3] & 0xFF);
                            if (ackBuffer[1] == 4 && ackBlockNumber == blockNumber) {  // Check for correct ACK
                                ackReceived = true;
                                blockNumber++;  // Prepare for the next block
                                if (bytesRead < MAX_DATA_LENGTH) {
                                    transferComplete = true;  // Last packet, end of data transmission
                                }
                            }
                            else{
                                System.out.println("TFTP-protocol inconsitent package");
                                break;
                            }
                        } catch (SocketTimeoutException e) {
                            //Increment times sent and repeat if ACK not received
                            System.out.println("Timeout, Resending Packet " + blockNumber);
                            timesSent++;
                        }
                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Failed to read found file");
            }
        }
        System.out.println("File Send Successfully");
    }

    public void processWRQ(DatagramPacket p) {
        //Creates file if not yet found in Files
        String filename = extractFilename(p);
        File directory = new File("Files");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        File toWrite = new File(directory, filename);
        //Create and send response ACK
        byte[] ackData = {0, 4, (byte) (blockNumber >> 8), (byte) (blockNumber & 0xFF)};
        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, destAddress, destTID);

        try {
            socket.send(ackPacket);
        } catch (IOException e) {
            System.out.println("SERVER THREAD: Failed to send ACK");
        }
        //Awaited block num incremented as exchange should be complete
        blockNumber++;
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(toWrite))) {
            int timesSent = 0;
            while (true) {
                //Will be receiving datapackets, max length used
                byte[] dataBuffer = new byte[MAX_PACKET];
                DatagramPacket receivePacket = new DatagramPacket(dataBuffer, dataBuffer.length);
                socket.receive(receivePacket);
                int length = receivePacket.getLength();
                int receivedBlockNumber = ((dataBuffer[2] & 0xFF) << 8) | (dataBuffer[3] & 0xFF);

                if (dataBuffer[1] == 3 && receivedBlockNumber == blockNumber) {
                    bos.write(dataBuffer, 4, length - 4);
                    blockNumber = receivedBlockNumber;
                    ackData[2] = (byte) (blockNumber >> 8);
                    ackData[3] = (byte) (blockNumber & 0xFF);
                    ackPacket = new DatagramPacket(ackData, ackData.length, destAddress, destTID);
                    //Times sent reset as not abnormal packet sending
                    timesSent = 0;
                    socket.send(ackPacket);
                }
                else if (dataBuffer[1] == 3 && receivedBlockNumber == blockNumber -1){
                    socket.send(ackPacket);
                    timesSent++;
                }
                else{
                    System.out.println("SERVER THREAD: Invalid Block Number");
                    sendErrAwaitClose();
                    break;
                }


                if (length < MAX_PACKET) {
                    System.out.println("File Received Successfully");
                    break;  // Last packet, as it is less than the maximum packet size
                }

            }

        } catch (Exception e) {
            System.out.println("Failed to write to file");
        }
    }


    private String extractFilename(DatagramPacket p) {

        //Used to find filename from WRQ/RRQ packet's data byte array
        byte[] data = p.getData();
        int length = p.getLength();
        StringBuilder filename = new StringBuilder();

        // Reading begins after opcode
        for (int i = 2; i < length; i++) {
            if (data[i] == 0) {
                break; // Stop on the first null byte, which terminates the filename
            }
            //Cast byte to char
            filename.append((char) data[i]);
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

    //*
    public static void main(String[] args) {
        //!!!!!!!! THIS IS A TEST USED IN DEVELOPMENT OF SERVERTHREAD BEFORE SERVER OR CLIENT CLASSES!!!!!!!!!!
        //!!!!!!!! NOT FOR ACTUAL EXECUTION !!!!!!!!!!!

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

    //Method used for testing buffer outputs during development
    private static void printByteArrayInHex(byte[] byteArray, int offset) {
        //Offset used to get past opcodes/blockNos
        for (int i = offset; i < byteArray.length; i++) {
            System.out.printf("%02X ", byteArray[i]); // %02X formats the byte as a two-digit hexadecimal number
        }
        System.out.println(); // Print a newline after all bytes are printed
    }

    private static byte[] createErrorData() {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        baos.write(0);baos.write(5); //Opcode
        baos.write(0);baos.write(1); //Errorcode (irrelevant in project spec, added anyway)

        //Error message irrelevant in spec, added anyway
        try {
            baos.write("File Not Found".getBytes(StandardCharsets.UTF_8));
            baos.write(0); // Null byte to terminate the error message
        } catch (Exception e) {
            System.err.println("Error writing the error message.");
        }

        return baos.toByteArray();

    }
    //Test Method used during development to create File data before client was made
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
 * !!!!!!!!!Main Method to test WRQ!!!!!!!!!!
 * !!!!!!!!!Not Part of Actula function!!!!!!!!
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





