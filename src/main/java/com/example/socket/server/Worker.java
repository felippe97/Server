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

class Worker extends Thread {
	 
    Socket socket;
    String clientRequest;
 
    
    public Worker (String req, Socket s)
    {
        socket = s;
        clientRequest = req;
    }
 
    /**
     * Začne pracovať po pridelení úloh serverom
     */
    public void run(){
        try{
            // Vždy vymaže zoznam na spracovanie novej požiadavky
            Log.clear();
          
         // Lokálna čítačka od klienta
            
            // Výstupný tok na klienta
            PrintStream printer = new PrintStream(socket.getOutputStream());
 
            Log.write("");
            Log.write("Http Worker is working...");
            Log.write(clientRequest);
 
            if (!clientRequest.startsWith("GET") || clientRequest.length() < 14 ||
                    !(clientRequest.endsWith("HTTP/1.0") || clientRequest.endsWith("HTTP/1.1"))) {
                // zlá požiadavka
                Log.write("400(Bad Request): " + clientRequest);
                String errorPage = buildErrorPage("400", "Bad Request", "Your browser sent a request that this server could not understand.");
                printer.println(errorPage);
            }
            else {
                String req = clientRequest.substring(4, clientRequest.length()-9).trim();
                if (req.indexOf("..") > -1 || req.indexOf("/.ht") > -1 || req.endsWith("~")) {
                    // hackerský útok
                    Log.write("403(Forbidden): " + req);
                    String errorPage = buildErrorPage("403", "Forbidden", "You don't have permission to access the requested URL " + req);
                    printer.println(errorPage);
                }
                else {
                    if (!req.startsWith("/images/") && !req.endsWith("favicon.ico")) {
                    	// Vyhnite sa tlači správ/protokolov pre požiadavky na ikony
                        // Prijmite požiadavku http get od klienta
                        // String clientRequest = reader.readLine();
                        //LogUtil.write("> Prijatá nová požiadavka: " + clientRequest);
                    }
                    // Dekódovať adresu URL, napr. New%20folder -> New folder
                    req = URLDecoder.decode(req, "UTF-8");
                    // Odstráňte poslednú lomku, ak existuje
                    if (req.endsWith("/")) {
                        req = req.substring(0, req.length() - 1);
                    }
                    // Handle požiadavky
                    if (req.indexOf(".")>-1) { // Žiadosť o jeden súbor
                        if (req.indexOf(".fake-cgi")>-1) { // CGI žiadosť Common Gateway Interface
                            Log.write("> This is a [CGI] request..");
                            handleCGIRequest(req, printer);
                        }
                        else { // Žiadosť o jeden súbor
                            if (!req.startsWith("/images/")&&!req.startsWith("/favicon.ico")) {
                                Log.write("> This is a [SINGLE FILE] request..");
                            }
                            handleFileRequest(req, printer);
                        }
                    }
                    else { // Žiadosť o adresár
                        Log.write("> This is a [DIRECTORY EXPLORE] request..");
                        handleExploreRequest(req, printer);
                    }
                }
            }
            // Uložiť protokoly do súboru
            Log.save(true);
            socket.close();
        }
        catch(IOException ex){
            // Výnimka
            System.out.println(ex);
        }
    }
 
    /**
     * Handle CGI(fake) request
     * @param req, get request from client
     * @param printer, output printer
     */
    private void handleCGIRequest(String req, PrintStream printer) throws UnsupportedEncodingException {
        // Parse the url to key-value pair
        Map<String, String> params = parseUrlParams(req);
 
        // Try to convert num1 and num2 to integer
        Integer number1 = tryParse(params.get("num1"));
        Integer number2 = tryParse(params.get("num2"));
 
        // Validate the input params
        if (number1 == null || number2 == null) {
            String errormsg = "Invalid parameter: " + params.get("num1") + " or " + params.get("num2") + ", both must be integer!";
            Log.write(">> " + errormsg);
            String errorPage = buildErrorPage("500", "Internal Server Error", errormsg);
            printer.println(errorPage);
        }
        else {
            Log.write(">> " + number1 + " + " + number2 + " = " + (number1+number2));
            
			/*
			 * StringBuilder objekty sú ako String objekty, až na to, že ich možno
			 * upravovať. Interne sa s týmito objektmi zaobchádza ako s poliami s
			 * premenlivou dĺžkou, ktoré obsahujú sekvenciu znakov. V ktoromkoľvek bode je
			 * možné zmeniť dĺžku a obsah sekvencie prostredníctvom vyvolania metódy.
			 */ 
            StringBuilder sbContent = new StringBuilder();   

            sbContent.append("Dear " + params.get("person") + ", the sum of ");
            sbContent.append(params.get("num1") + " and " + params.get("num2") + " is ");
            sbContent.append(number1+number2);
            sbContent.append(".");
            String htmlPage = buildHtmlPage(sbContent.toString(), "Fake-CGI: Add Two Numbers");
            String htmlHeader = buildHttpHeader("aa.html", htmlPage.length());
            printer.println(htmlHeader);
            printer.println(htmlPage);
        }
    }
 
    /**
     * Spracovanie žiadosti o jeden súbor
     *  req, získajte požiadavku od klienta
     *  tlačiareň, výstupná tlačiareň
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
            Log.write(">> No such resource:" + req);
        }
        else { // file
            if (!req.startsWith("/images/")&&!req.startsWith("/favicon.ico")) {
                Log.write(">> Seek the content of file: " + file.getName());
            }
            // Vypis header html
            String htmlHeader = buildHttpHeader(path, file.length());
            printer.println(htmlHeader);
 
            // Otvorte file to input stream
            InputStream fs = new FileInputStream(file);
            byte[] buffer = new byte[1000];
            while (fs.available()>0) {
                printer.write(buffer, 0, fs.read(buffer));
            }
            fs.close();
        }
    }
 
    /**
     * Spracovanie žiadosti o preskúmanie súborov a adresárov
     * req, získajte požiadavku od klienta
     *  printer, výstupná printer
     */
    private void handleExploreRequest(String req, PrintStream printer) {
        // Získa koreňový priečinok webserver
        String rootDir = getRootFolder();
        //Získa skutočnú cestu k súboru
        String path = Paths.get(rootDir, req).toString();
        // Vyskúša otvoriť directory
        File file = new File (path) ;
        if (!file.exists()) { // Ak adresár directory neexistuje
            printer.println("No such resource:" + req);
            Log.write(">> No such resource:" + req);
        }
        else { // Keď existuje
            Log.write(">> Explore the content under folder: " + file.getName());
            // Získa všetky súbory a adresár v aktuálnom adresári
            File[] files = file.listFiles();
            Arrays.sort(files);
 
            //Vytvorý štruktúru súborov/adresárov vo formáte html
            StringBuilder sbDirHtml = new StringBuilder();
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
                if (parent.equals(rootDir)) { // The first level
                    parent = "../";
                }
                else { // Druhá alebo hlbšia úroveň
                    parent = parent.replace(rootDir, "");
                }
                // Replace backslash to slash
                parent = parent.replace("\\", "/");
                // Parent line
                sbDirHtml.append("<tr>");
                sbDirHtml.append("  <td><img src=\""+buildImageLink(req,"images/folder.png")+"\"></img><a href=\"" + parent +"\">../</a></td>");
                sbDirHtml.append("  <td></td>");
                sbDirHtml.append("  <td></td>");
                sbDirHtml.append("</tr>");
            }
 
            // Build lines for directories
            List<File> folders = getFileByType(files, true);
            for (File folder: folders) {
                Log.write(">>> Directory: " + folder.getName());
                sbDirHtml.append("<tr>");
                sbDirHtml.append("  <td><img src=\""+buildImageLink(req,"images/folder.png")+"\"></img><a href=\""+buildRelativeLink(req, folder.getName())+"\">"+folder.getName()+"</a></td>");
                sbDirHtml.append("  <td>" + getFormattedDate(folder.lastModified()) + "</td>");
                sbDirHtml.append("  <td></td>");
                sbDirHtml.append("</tr>");
            }
            // Build lines for files
            List<File> fileList = getFileByType(files, false);
            for (File f: fileList) {
                Log.write(">>> File: " + f.getName());
                sbDirHtml.append("<tr>");
                sbDirHtml.append("  <td><img src=\""+buildImageLink(req, getFileImage(f.getName()))+"\" width=\"16\"></img><a href=\""+buildRelativeLink(req, f.getName())+"\">"+f.getName()+"</a></td>");
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
     * Build http header
     * @param path, path of the request
     * @param length, length of the content
     * @return, header text
     */
    private String buildHttpHeader(String path, long length) {
        StringBuilder sbHtml = new StringBuilder();
        sbHtml.append("HTTP/1.1 200 OK");
        sbHtml.append("\r\n");
        sbHtml.append("Content-Length: " + length);
        sbHtml.append("\r\n");
        sbHtml.append("Content-Type: "+ getContentType(path));
        sbHtml.append("\r\n");
        return sbHtml.toString();
    }
 
    /**
     * Build http page
     * @param content, content of the page
     * @param header1, h1 content
     * @return, page text
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
        }
        else {
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
     * Build error page for bad request
     * @param code, http cde: 400, 301, 200
     * @param title, page title
     * @param msg, error message
     * @return, page text
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
     * Get file or directory list
     * @param filelist, original file/directory list
     * @param isfolder, flag indicates looking for file or directory list
     * @return, file/directory list
     */
    private List<File> getFileByType(File[] filelist, boolean isfolder) {
        List<File> files = new ArrayList<File>();
        if (filelist == null || filelist.length == 0) {
            return files;
        }
 
        for (int i = 0; i < filelist.length; i++) {
            if (filelist[i].isDirectory() && isfolder) {
                files.add(filelist[i]);
            }
            else if (filelist[i].isFile() && !isfolder) {
                files.add(filelist[i]);
            }
        }
        return files;
    }
 
    /**
     * Parse parameter from url to key value pair
     * @param url, url from client
     * @return, pair list
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
            mapParams.put(URLDecoder.decode(pair.substring(0, index), "UTF-8"), URLDecoder.decode(pair.substring(index + 1), "UTF-8"));
        }
        return mapParams;
    }
 
    /**
     * Get root path
     * @return, path of the current location
     */
    private String getRootFolder() {
        String root = "";
        try{
            File f = new File(".");
            root = f.getCanonicalPath();
        }
        catch(IOException ex){
            ex.printStackTrace();
        }
        return root;
    }
 
    /**
     * Convert date to specified format
     * @param lastmodified, long value represents date
     * @return, formatted date in string
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
     * Build relative link
     * @param current, current request
    * @param filename, file name
     * @return, formatted file name
     */
    private String buildRelativeLink(String req, String filename) {
        if (req == null || req.equals("") || req.equals("/")) {
            return filename;
        }
        else {
            return req + "/" +filename;
        }
    }
 
    /**
     * Build image link for icons
     * @param current, current request
     * @param filename, file name
     * @return, formatted file name
     */
    private String buildImageLink(String req, String filename) {
        if (req == null || req.equals("") || req.equals("/")) {
            return filename;
        }
        else {
            String imageLink = filename;
            for(int i = 0; i < req.length(); i++) {
                if (req.charAt(i) == '/') {
                    // For each downstairs level, need a upstairs level path
                    imageLink = "../" + imageLink;
                }
            }
            return imageLink;
        }
    }
 
    /**
     * Get file icon according to its extension
     * @param path, file path
     * @return, icon path
     */
    private static String getFileImage(String path) {
        if (path == null || path.equals("") || path.lastIndexOf(".") < 0) {
            return "images/file.png";
        }
 
        String extension = path.substring(path.lastIndexOf("."));
        switch(extension) {
            case ".class":
                return "images/class.png";
            case ".html":
                return "images/html.png";
            case ".java":
                return "images/java.png";
            case ".txt":
                return "images/text.png";
            case ".xml":
                return "images/xml.png";
            default:
                return "images/file.png";
        }
    }
 
    /**
     * Get MIME type according to file extension
     * @param path, file path
     * @return, MIME type
     */
    private static String getContentType(String path) {
        if (path == null || path.equals("") || path.lastIndexOf(".") < 0) {
            return "text/html";
        }
 
        String extension = path.substring(path.lastIndexOf("."));
        switch(extension) {
            case ".html":
            case ".htm":
                return "text/html";
            case ".txt":
                return "text/plain";
            case ".ico":
                return "image/x-icon .ico";
            case ".wml":
                return "text/html"; //text/vnd.wap.wml
            default:
                return "text/plain";
        }
    }
 
    /**
     * Parse string to integer, return null if unable to convert
     * @param text, string value
     * @return, integer value
     */
    private Integer tryParse(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
 
}