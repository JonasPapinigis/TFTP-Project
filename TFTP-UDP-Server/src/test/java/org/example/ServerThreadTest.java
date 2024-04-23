package org.example;

import java.io.DataInput;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Random;


import static org.junit.jupiter.api.Assertions.*;

class ServerThreadTest {


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
    void testPacketReception() {
        ServerThread thread = null;
        DatagramSocket receivingSocket = null;
        try {
            receivingSocket = new DatagramSocket(6969);
        } catch (SocketException e) {
            assertTrue(false, "Failed to create receiving socket");
        }

        // Create WRQData
        byte[] filename = "file.bin".getBytes(StandardCharsets.UTF_8);
        byte[] octet = "octet".getBytes(StandardCharsets.UTF_8);
        int length = 3 + filename.length + 1 + octet.length + 1;
        byte[] WRQData = new byte[length];
        int index = 0;
        WRQData[index++] = 0;
        WRQData[index++] = 2;
        System.arraycopy(filename, 0, WRQData, index, filename.length);
        index += filename.length;
        WRQData[index++] = 0;
        System.arraycopy(octet, 0, WRQData, index, octet.length);
        index += octet.length;
        WRQData[index] = 0;

        InetAddress address = null;
        try {
            address = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            assertTrue(false, "Failed to set InetAddress");
        }
        DatagramPacket WRQpacket = new DatagramPacket(WRQData, WRQData.length, address, 6969);

        // Start a new thread to simulate the sender socket
        new Thread(() -> {
            try (DatagramSocket sendingSocket = new DatagramSocket()) {
                sendingSocket.send(WRQpacket);
            } catch (Exception e) {
                e.printStackTrace();
                assertTrue(false, "Failed to send packet");
            }
        }).start();

        // Now wait and receive the packet
        byte[] receivedData = new byte[4]; // Adjust the buffer size accordingly
        DatagramPacket receivedACK = new DatagramPacket(receivedData, receivedData.length);
        try {
            receivingSocket.receive(receivedACK);
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false, "Failed to receive packet");
        }
        // Verify the received packet
        byte[] receivedWRQData = receivedACK.getData();
        for (byte b : receivedWRQData) {
            System.out.print(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0') + " ");
        }
        System.out.println();
        assertAll("correctACK",
                () -> assertEquals(receivedWRQData.length,4),
                () -> assertEquals(0, receivedWRQData[0]),
                () -> assertEquals(4, receivedWRQData[1]),
                () -> assertEquals(0, receivedWRQData[2]),
                () -> assertEquals(0, receivedWRQData[3])
        );

        // Clean up
        receivingSocket.close();
        thread = null;
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
}