package org.example;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Random;


import static org.junit.jupiter.api.Assertions.*;

class ServerThreadTest {
    private final static int MAX_PACKET = 516;
    private final static int MAX_DATA_LENGTH = 512;


    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        String filePath = "output.bin";
        File file = new File(filePath);
            if(!file.exists()){

            byte[] bytes = new byte[6144];
            new Random().nextBytes(bytes);


            // Use try-with-resources to ensure that the FileOutputStream is closed after use
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(bytes);
                System.out.println("File written successfully with random data.");
            } catch (IOException e) {
                System.err.println("Error writing to file: " + e.getMessage());
            }
        }
    }

    @org.junit.jupiter.api.Test
    void TestReadDatagram(){
        ServerThread thread = null;
        DatagramSocket sendingSocket = null;
        try {
            String message = "Hello, world!";
            byte[] buffer = message.getBytes();
            InetAddress address = InetAddress.getByName("127.0.0.1");
            int port = 1234;
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
            System.out.println("Packet prepared for address " + address.toString() + " and port " + port);
            thread = new ServerThread(packet);
        } catch (Exception e) {
            System.err.println("Error: Unknown Host.");
        }
        if (thread != null) {
            DatagramSocket socket = thread.getSocket();
            DatagramPacket testPack = thread.getInitPacket();
            System.out.println("TestReadDatagram out socket name: "+ thread.getSocket().getLocalPort());
            assertAll("correctSocketAndPacket",
                    () -> assertEquals(testPack.getPort(), 1234),
                    () -> assertNotNull(testPack.getAddress().toString(), "127.0.0.1"),
                    () -> assertNotNull(socket.getPort())
            );
        } else {
            System.out.println("Thread was not properly instantiated.");
        }
    }

    @org.junit.jupiter.api.Test
    void testWRQACKReception() {
        DatagramSocket clientSocket = null;
        try {
            int serverPort = 9999;
            // Set up the client socket
            clientSocket = new DatagramSocket(serverPort);
            InetAddress serverAddress = InetAddress.getByName("127.0.0.1");
            clientSocket.setSoTimeout(5000);

            // Prepare the WRQ packet
            byte[] wrqData = createPacketData(2, "file.txt", "octet");
            DatagramPacket wrqPacket = new DatagramPacket(wrqData, wrqData.length, serverAddress, 6969);

            // Start the server thread
            ServerThread serverThread = new ServerThread(wrqPacket);
            serverThread.start();

            // Receive ACK for WRQ
            byte[] ackBuffer1 = new byte[4];
            DatagramPacket ack1 = new DatagramPacket(ackBuffer1, ackBuffer1.length);
            clientSocket.receive(ack1);

            // Send first data packet DATA1
            byte[] data1 = createDataPacket(1, true);
            DatagramPacket data1Packet = new DatagramPacket(data1, data1.length, serverAddress, 6969);
            clientSocket.send(data1Packet);

            // Receive ACK for DATA1
            byte[] ackBuffer2 = new byte[4];
            DatagramPacket ack2 = new DatagramPacket(ackBuffer2, ackBuffer2.length);
            clientSocket.receive(ack2);

            // Send second data packet DATA_TERM (terminal data packet)
            byte[] dataTerm = createDataPacket(2, false); // Smaller data size to signify end of data transfer
            DatagramPacket dataTermPacket = new DatagramPacket(dataTerm, dataTerm.length, serverAddress, serverPort);
            clientSocket.send(dataTermPacket);

            // Receive ACK for DATA_TERM
            byte[] ackBuffer3 = new byte[4];
            DatagramPacket ack3 = new DatagramPacket(ackBuffer3, ackBuffer3.length);
            clientSocket.receive(ack3);

            System.out.println("Here");
            // Print all ACKs as a series of 8-bit bytes
            assertAll("CorrectACKs",
                    () -> assertEquals("00000000 00000100 00000000 00000000",bytesToBinaryString(ackBuffer1)),
                    () -> assertEquals("00000000 00000011 00000000 00000001",bytesToBinaryString(ackBuffer2)),
                    () -> assertEquals("00000000 00000011 00000000 00000010",bytesToBinaryString(ackBuffer3))

            );
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        }
    }





    /**
    void TestRecieveRRQ(){

    }

    void TestRecieveDatapacket(){

    }
    void TestRecieveError(){

    }
    void TestRecieveSelfAddress(){

    }
    void TestRecieveRRQNoACK(){

    }
    void TestRecieveWRQNoACK(){

    }

    void TestIncorrectAddress(){

    }
     */
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


}