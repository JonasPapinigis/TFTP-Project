package org.example;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;

public class Server extends Thread{
    //TFTP default socket
    private final int TID = 69;
    ServerSocket masterSocket;
    Socket slaveSocket;
    //boolean indicating state of server
    //Intereruptable from outside, hence volatile
    private volatile boolean running;
    public Server(){
        try {
            //Master socket instanciated to TID = 69
            masterSocket = new ServerSocket(TID);
            System.out.println("SERVER: Server started successfully");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        running = false;
    }
    public void runServer(){
        //running variable interruptable from outside
        running = true;
        //continue while running
        while(running){
            try {
                //Awaits connection
                slaveSocket = masterSocket.accept();
                System.out.println("SERVER: Connection from " + slaveSocket.getInetAddress() + ", " + slaveSocket.getPort() + " accepted");
            } catch (IOException e) {
                System.out.println("SERVER: Connection from " + slaveSocket.getInetAddress() + ", " + slaveSocket.getPort() + " failed");
            }

            //Starting new ServerThread allows for multiple file transfers at once
            new ServerThread(slaveSocket).start();

        }
    }

    public void exit(){
        running = false;
    }

    public static void main(String[] args){
        //User interface


        Scanner scanner = new Scanner(System.in);
        System.out.println("------TFTP Protocol Server------");
        System.out.println("This is a server client which can accept several Net IO operations over the TFTP protocol");
        System.out.println("Files are stored in the Files folder in the working directory");
        System.out.println("To access please use the TFTP Protocol Client to access information");
        //Take input until a valid one provided
        boolean validInput = false;
        while(!validInput){
            System.out.println("COMMANDS: -start\n          -exit\nInput:");
            //input collected
            String argument = scanner.nextLine();
            //end with no server activation
            if (argument.equals("-exit")){
                validInput = true;
                break;
            }
            else if (argument.equals("-start")){
                //Server activated, outer loop no longer needed
                validInput = true;
                try {
                    //Instanciates server as thread
                    //This is in order to still take user input to allow user to end programme
                    Server server = new Server();
                    Thread serverThread = new Thread(server::runServer);
                    serverThread.start();
                    System.out.println("...Server started");
                    boolean exited = false;
                    //Await -exit command
                    while(!exited){
                        argument = scanner.nextLine();
                        if (argument.equals("-exit")){
                            exited=true;
                            server.exit();
                            //Server thread ended
                            serverThread.join();
                        }
                    }
                } catch (InterruptedException i){
                    System.out.println("SERVER ERROR: Failed to close gracefully");
                    break;
                }
            }
            else{
                System.out.println("SERVER ERROR: Invalid argument");
            }
        }

    }


}
