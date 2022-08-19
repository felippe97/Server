package com.example.socket.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebServer {
	private static final Logger log = LoggerFactory.getLogger(WebServer.class);

	/**
	 * Spustite serverový soket na monitorovanie požiadaviek klientov a odosielanie
	 * http žiadosť pre HttpWorkers.
	 */
	public static void main(String args[]) {

		// Číslo portu pre požiadavku http
		int port = 8888;
		// Odkaz na klientsky soket
		Socket socket;

		try {
			// Soket servera
			ServerSocket servSocket = new ServerSocket(port);
			log.info("Web Server is starting up, listening at port " + port);
			log.info("http://localhost:8888");

			while (true) {
				// Serverový soket čaká na požiadavku klienta
				socket = servSocket.accept();
				// Lokálny reader od klienta
				BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

				// Priraďte požiadavky http k HttpWorker
				String request = "";
				String clientRequest = "";
				while ((clientRequest = reader.readLine()) != null) {
					if (request.equals("")) {
						request = clientRequest;
					}
					if (clientRequest.equals("")) { // Ak je koniec http žiadosti, break
						break;
					}
				}

				if (request != null && !request.equals("")) {
					new Worker(request, socket).start();
				}
			}
		} catch (IOException ex) {
			// Handle exception výnimka
			log.error("Chyba", ex);
		} finally {
			log.info("Server has been shutdown!");
		}
	}
}