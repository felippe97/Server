package com.example.socket.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class WebServer {
	/**
     * Spustite serverový soket na monitorovanie požiadaviek klientov a odosielanie http
     * žiadosť pre HttpWorkers.
     */
    public static void main(String args[]){
    	// Maximálna dĺžka frontu pre prichádzajúce pripojenie
        int queue_len = 6;
     // Číslo portu pre požiadavku http
        int port = 8888;
     // Odkaz na klientsky soket
        Socket socket;
 
        try{
        	// Soket servera
            ServerSocket servsocket = new ServerSocket(port, queue_len);
            System.out.println("Web Server is starting up, listening at port " + port + ".");
            System.out.println("http://localhost:8888");
 
            while(true){
                //Serverový soket čaká na požiadavku klienta
                socket = servsocket.accept();
                // Lokálny reader od klienta
                BufferedReader reader =new BufferedReader(new InputStreamReader(socket.getInputStream()));
 
                // Priraďte požiadavky http k HttpWorker
                String req = "";
                String clientRequest = "";
                while ((clientRequest = reader.readLine()) != null) {
                    if (req.equals("")) {
                        req  = clientRequest;
                    }
                    if (clientRequest.equals("")) { // Ak je koniec http žiadosti, break 
                        break;
                    }
                }
 
                if (req != null && !req.equals("")) {
                    new Worker(req, socket).start();
                }
            }
        }
        catch(IOException ex){
            //Handle exception výnimka
            System.out.println(ex);
        }
        finally {
            System.out.println("Server has been shutdown!");
        }
    }
}