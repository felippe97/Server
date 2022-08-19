package com.example.socket.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Worker extends Thread {
	private static final Logger log = LoggerFactory.getLogger(Worker.class);

	Socket socket;
	String clientRequest;

	public Worker(String req, Socket s) {
		log.info("Request - {}", req);
		socket = s;
		clientRequest = req;
	}

	/**
	 * Začne pracovať po pridelení úloh serverom
	 */
	public void run() {
		try {

			// Lokálna čítačka od klienta

			// Výstupný tok na klienta
			PrintStream printer = new PrintStream(socket.getOutputStream());

			if (!clientRequest.startsWith("GET") || clientRequest.length() < 14
					|| !(clientRequest.endsWith("HTTP/1.0") || clientRequest.endsWith("HTTP/1.1"))) {
				// zlá požiadavka

				String errorPage = buildErrorPage("400", "Bad Request",
						"Your browser sent a request that this server could not understand.");
				printer.println(errorPage);
			} else {
				String req = clientRequest.substring(4, clientRequest.length() - 9).trim();
				if (req.indexOf("..") > -1 || req.indexOf("/.ht") > -1 || req.endsWith("~")) {
					// hackerský útok

					String errorPage = buildErrorPage("403", "Forbidden",
							"You don't have permission to access the requested URL " + req);
					printer.println(errorPage);
				} else {
					if (!req.startsWith("/images/") && !req.endsWith("favicon.ico")) {
						// Vyhnite sa tlači správ/protokolov pre požiadavky na ikony
						// Prijmite požiadavku http get od klienta

					}
					// Dekódovať adresu URL, napr. New%20folder -> New folder
					req = URLDecoder.decode(req, "UTF-8");
					// Odstráňte poslednú lomku, ak existuje
					if (req.endsWith("/")) {
						req = req.substring(0, req.length() - 1);
					}
					// Handle požiadavky
					if (req.indexOf(".") > -1) { // Žiadosť o jeden súbor
						if (req.indexOf(".fake-cgi") > -1) { // CGI žiadosť Common Gateway Interface

							handleCGIRequest(req, printer);
						} else { // Žiadosť o jeden súbor
							if (!req.startsWith("/images/") && !req.startsWith("/favicon.ico")) {

							}
							handleFileRequest(req, printer);
						}
					} else { // Žiadosť o adresár

						handleExploreRequest(req, printer);
					}
				}
			}

			socket.close();
		} catch (IOException ex) {
			// Výnimka
			System.out.println(ex);
		}
	}

	/**
	 * Vybaviť CGI (falošnú) požiadavku požiadavka, získajte požiadavku od klienta
	 */
	private void handleCGIRequest(String req, PrintStream printer) throws UnsupportedEncodingException {
		// Parse the url to key-value pair
		Map<String, String> params = parseUrlParams(req);

		// Try to convert num1 and num2 to integer
		Integer number1 = tryParse(params.get("num1"));
		Integer number2 = tryParse(params.get("num2"));

		// Validate the input params
		if (number1 == null || number2 == null) {
			String errormsg = "Invalid parameter: " + params.get("num1") + " or " + params.get("num2")
					+ ", both must be integer!";

			String errorPage = buildErrorPage("500", "Internal Server Error", errormsg);
			printer.println(errorPage);
		} else {

			/*
			 * StringBuilder objekty sú ako String objekty, až na to, že ich možno
			 * upravovať. Interne sa s týmito objektmi zaobchádza ako s poliami s
			 * premenlivou dĺžkou, ktoré obsahujú sekvenciu znakov. V ktoromkoľvek bode je
			 * možné zmeniť dĺžku a obsah sekvencie prostredníctvom vyvolania metódy.
			 */
			StringBuilder sbContent = new StringBuilder();

			sbContent.append("Dear " + params.get("person") + ", the sum of ");
			sbContent.append(params.get("num1") + " and " + params.get("num2") + " is ");
			sbContent.append(number1 + number2);
			sbContent.append(".");
			String htmlPage = buildHtmlPage(sbContent.toString(), "Fake-CGI: Add Two Numbers");
			String htmlHeader = buildHttpHeader("aa.html", htmlPage.length());
			printer.println(htmlHeader);
			printer.println(htmlPage);
		}
	}

	/**
	 * Spracovanie žiadosti o jeden súbor req, získajte požiadavku od klienta
	 * tlačiareň, výstupná tlačiareň
	 */
	private void handleFileRequest(String req, PrintStream printer) throws FileNotFoundException, IOException {
		// Získajte koreňový priečinok webového servera
		String rootDir = getRootFolder();
		// Získajte skutočnú cestu k súboru
		String path = Paths.get(rootDir, req).toString();
		// Skúste súbor otvoriť
		File file = new File(path);
		if (!file.exists() || !file.isFile()) { // If not exists or not a file
			printer.println("No such resource:" + req);

		} else { // file
			if (!req.startsWith("/images/") && !req.startsWith("/favicon.ico")) {

			}
			// Vypis header html
			String htmlHeader = buildHttpHeader(path, file.length());
			printer.println(htmlHeader);

			// Otvorte file to input stream
			InputStream fs = new FileInputStream(file);
			byte[] buffer = new byte[1000];
			while (fs.available() > 0) {
				printer.write(buffer, 0, fs.read(buffer));
			}
			fs.close();
		}
	}

	/**
	 * Spracovanie žiadosti o preskúmanie súborov a adresárov req, získajte
	 * požiadavku od klienta printer, výstupná printer
	 */
	private void handleExploreRequest(String request, PrintStream printer) {
		// Získa koreňový priečinok webserver
		String rootDir = getRootFolder();
		// Získa skutočnú cestu k súboru
		String path = Paths.get(rootDir, request).toString();
		// Vyskúša otvoriť directory
		File file = new File(path);
		if (!file.exists()) { // Ak adresár directory neexistuje
			printer.println("No such resource:" + request);

		} else { // Keď existuje

			// Získa všetky súbory a adresár v aktuálnom adresári
			File[] files = file.listFiles();
			if (files != null) {
				Arrays.sort(files);
			}

			// Vytvorý štruktúru súborov/adresárov vo formáte html
			StringBuilder sbDirHtml = new StringBuilder(); // StringBuilder v jazyku Java predstavuje meniteľnú
															// sekvenciu znakov
			// Titulný riadok
			sbDirHtml.append("<table>");
			sbDirHtml.append("<tr>");
			sbDirHtml.append("  <th>Name</th>");
			sbDirHtml.append("  <th>Last Modified</th>");
			sbDirHtml.append("  <th>Size(Bytes)</th>");
			sbDirHtml.append("</tr>");

			// Nadradený priečinok, zobrazi ho, ak aktuálny adresár nie je root
			if (!path.equals(rootDir)) {
				String parent = path.substring(0, path.lastIndexOf(File.separator));
				if (parent.equals(rootDir)) { // Prvý level
					parent = "../";
				} else { // Druhá alebo hlbšia úroveň
					parent = parent.replace(rootDir, "");
				}
				// Nahraďte opačnú lomku lomkou
				parent = parent.replace("\\", "/");
				// Rodičovská line
				sbDirHtml.append("<tr>");
				sbDirHtml.append("  <td><img src=\"" + buildImageLink(request, "images/xfile.png")
						+ "\" width=\"20\"></img><a href=\"" + parent + "\">../</a></td>");
				sbDirHtml.append("  <td></td>");
				sbDirHtml.append("  <td></td>");
				sbDirHtml.append("</tr>");
			}

			// Vytvori riadky pre directory
			List<File> folders = getFileByType(files, true);
			for (File folder : folders) {

				sbDirHtml.append("<tr>");
				sbDirHtml.append("  <td><img src=\"" + buildImageLink(request, "images/file.png")
						+ "\" width=\"20\"></img><a href=\"" + buildRelativeLink(request, folder.getName()) + "\">"
						+ folder.getName() + "</a></td>");
				sbDirHtml.append("  <td>" + getFormattedDate(folder.lastModified()) + "</td>");
				sbDirHtml.append("  <td></td>");
				sbDirHtml.append("</tr>");
			}
			// Vytvori riadky pre súbory
			List<File> fileList = getFileByType(files, false);
			for (File f : fileList) {

				sbDirHtml.append("<tr>");
				sbDirHtml.append("  <td><img src=\"" + buildImageLink(request, getFileImage(f.getName()))
						+ "\" width=\"20\"></img><a href=\"" + buildRelativeLink(request, f.getName()) + "\">"
						+ f.getName() + "</a></td>");
				sbDirHtml.append("  <td>" + getFormattedDate(f.lastModified()) + "</td>");
				sbDirHtml.append("  <td>" + f.length() + "</td>");
				sbDirHtml.append("</tr>");
			}

			sbDirHtml.append("</table>");
			String htmlPage = buildHtmlPage(sbDirHtml.toString(), "");
			String htmlHeader = buildHttpHeader(path, htmlPage.length());
			printer.println(htmlHeader);
			printer.println(htmlPage);
		}
	}

	/**
	 * Vytvori http header cesta k request dĺžka obsahu vrati header text
	 */
	private String buildHttpHeader(String path, long length) {
		StringBuilder sbHtml = new StringBuilder();
		sbHtml.append("HTTP/1.1 200 OK");
		sbHtml.append("\r\n");
		sbHtml.append("Content-Length: " + length);
		sbHtml.append("\r\n");
		sbHtml.append("Content-Type: " + getContentType(path));
		sbHtml.append("\r\n");
		return sbHtml.toString();
	}

	/**
	 * Vytvori http page obsah stránky header1, h1 content return page text
	 */
	private String buildHtmlPage(String content, String header) {
		StringBuilder sbHtml = new StringBuilder();
		sbHtml.append("<!DOCTYPE html>");
		sbHtml.append("<html>");
		sbHtml.append("<head>");
		sbHtml.append("<style>");
		sbHtml.append(" table { width:50%; } ");
		sbHtml.append(" th, td { padding: 3px; text-align: left; }");
		sbHtml.append("</style>");
		sbHtml.append("<title>My Web Server</title>");
		sbHtml.append("</head>");
		sbHtml.append("<body>");
		if (header != null && !header.isEmpty()) {
			sbHtml.append("<h1>" + header + "</h1>");
		} else {
			sbHtml.append("<h1>File Explorer in Web Server </h1>");
		}
		sbHtml.append(content);
		sbHtml.append("<hr>");
		sbHtml.append("<p>*This page is returned by Web Server.</p>");
		sbHtml.append("</body>");
		sbHtml.append("</html>");
		return sbHtml.toString();
	}

	/**
	 * Vytvori error stránku pre zlú požiadavku code, http cde: 400, 301, 200 title,
	 * page title msg, error sprava return page text
	 */
	private String buildErrorPage(String code, String title, String msg) {
		StringBuilder sbHtml = new StringBuilder();
		sbHtml.append("HTTP/1.1 " + code + " " + title + "\r\n\r\n");
		sbHtml.append("<!DOCTYPE html>");
		sbHtml.append("<html>");
		sbHtml.append("<head>");
		sbHtml.append("<title>" + code + " " + title + "</title>");
		sbHtml.append("</head>");
		sbHtml.append("<body>");
		sbHtml.append("<h1>" + code + " " + title + "</h1>");
		sbHtml.append("<p>" + msg + "</p>");
		sbHtml.append("<hr>");
		sbHtml.append("<p>*This page is returned by Web Server.</p>");
		sbHtml.append("</body>");
		sbHtml.append("</html>");
		return sbHtml.toString();
	}

	/**
	 * Získa zoznam súborov alebo adresárov zoznam súborov, pôvodný zoznam
	 * súborov/adresárov isfolder, príznak označuje hľadanie súboru alebo zoznamu
	 * adresárov return zoznam súborov/adresárov
	 */
	private List<File> getFileByType(File[] filelist, boolean isfolder) {
		List<File> files = new ArrayList<File>();
		if (filelist == null || filelist.length == 0) {
			return files;
		}

		for (int i = 0; i < filelist.length; i++) {
			if (filelist[i].isDirectory() && isfolder) {
				files.add(filelist[i]);
			} else if (filelist[i].isFile() && !isfolder) {
				files.add(filelist[i]);
			}
		}
		return files;
	}

	/**
	 * Analyzuje parameter z adresy URL do key value pair url od klienta return
	 * zoznam párov
	 */
	private Map<String, String> parseUrlParams(String url) throws UnsupportedEncodingException {
		HashMap<String, String> mapParams = new HashMap<String, String>();
		if (url.indexOf("?") < 0) {
			return mapParams;
		}

		url = url.substring(url.indexOf("?") + 1);
		String[] pairs = url.split("&");
		for (String pair : pairs) {
			int index = pair.indexOf("=");
			mapParams.put(URLDecoder.decode(pair.substring(0, index), "UTF-8"),
					URLDecoder.decode(pair.substring(index + 1), "UTF-8"));
		}
		return mapParams;
	}

	/**
	 * Získa koreňovú cestu return cesta k aktuálnej polohe
	 */
	private String getRootFolder() {
		String root = "";
		try {
			File f = new File(".");
			root = f.getCanonicalPath();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		return root;
	}

	/**
	 * Previesť dátum do určeného formátu lastmodified, dlhá hodnota predstavuje
	 * dátum return formátovaný dátum v reťazci
	 */
	private String getFormattedDate(long lastmodified) {
		if (lastmodified < 0) {
			return "";
		}

		Date lm = new Date(lastmodified);
		String lasmod = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(lm);
		return lasmod;
	}

	/**
	 * Vytvori relatívny odkaz current, aktuálna požiadavka názov súboru, názov
	 * súboru return formátovaný názov súboru
	 */
	private String buildRelativeLink(String req, String filename) {
		if (req == null || req.equals("") || req.equals("/")) {
			return filename;
		} else {
			return req + "/" + filename;
		}
	}

	/**
	 * Vytvori odkaz na obrázok pre ikony current, aktuálna požiadavka názov súboru
	 * return formátovaný názov súboru
	 */
	private String buildImageLink(String req, String filename) {
		if (req == null || req.equals("") || req.equals("/")) {
			return filename;
		} else {
			String imageLink = filename;
			for (int i = 0; i < req.length(); i++) {
				if (req.charAt(i) == '/') {

					imageLink = "../" + imageLink;
				}
			}
			return imageLink;
		}
	}

	/**
	 * Získa ikonu súboru podľa jej prípony cesta, cesta k súboru return cesta k
	 * ikone
	 */
	private static String getFileImage(String path) {
		if (path == null || path.equals("") || path.lastIndexOf(".") < 0) {
			return "images/setings.png";
		}

		String extension = path.substring(path.lastIndexOf("."));
		switch (extension) {
		case ".class":
			return "images/class.png";
		case ".java":
			return "images/java.png";
		case ".txt":
			return "images/text.png";
		case ".xml":
			return "images/xml.png";
		case ".html":
			return "images/html.jpg";
		default:
			return "images/spiderman.png";
		}
	}

	/**
	 * Získa typ MIME podľa prípony súboru cesta, cesta k súboru return typ MIME
	 */
	private static String getContentType(String path) {
		if (path == null || path.equals("") || path.lastIndexOf(".") < 0) {
			return "text/html";
		}

		String extension = path.substring(path.lastIndexOf("."));
		switch (extension) {
		case ".html":
		case ".htm":
			return "text/html";
		case ".txt":
			return "text/plain";
		case ".ico":
			return "image/x-icon .ico";
		case ".wml":
			return "text/html"; // text/vnd.wap.wml
		default:
			return "text/plain";
		}
	}

	/**
	 * Analyzuje reťazec na celé číslo, ak nie je možné konvertovať, vráť hodnotu
	 * null text, hodnota reťazca return celočíselná hodnota
	 */
	private Integer tryParse(String text) {
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException e) {
			return null;
		}
	}

}