package com.example.socket.server;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class Log {
    private static final String FILE_LOG = "WebServerLogs.txt";
    private static List<String> logs = new LinkedList<String>();
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
 
    /**
     * Zapíšte log do zoznamu miestneho úložiska
     * @param log, obsah denníka
     */
    public static void write(String log) {
        write(log, true);
    }
 
    /**
     * Zapíšte log do zoznamu miestneho úložiska
     * @param log, obsah denníka
     * @param print, print to screen
     */
    public static void write(String log, boolean print) {
        String message = sdf.format(new Date()) + " " + log;
        // Uložte nový log
        logs.add(message);
 
        if(print) {
            // Zapíše log
            System.out.println(message);
        }
    }
 
    /**
     * Uloži protokoly do určeného súboru
     * @param pripojiť, true je pripojiť, false je prepísať
     */
    public static void save(boolean append) {
        try {
            if (logs!=null && logs.size()>0) {
 
                // Otvorí log
                FileWriter fileWriterLog = new FileWriter(FILE_LOG, append);
 
                // Používateľ BufferedWriter na pridanie nového riadku
                BufferedWriter bufferedWriterLog = new BufferedWriter(fileWriterLog);
 
                //Prejdite všetky protokoly a zapíšte ich do súboru
                for (String str : logs) {
                    // Napíšte aktuálny log
                    bufferedWriterLog.write(str);
                    // Jeden log jeden riadok
                    bufferedWriterLog.newLine();
                }
 
                // Vždy zatvorí súbory.
                bufferedWriterLog.close();
            }
        }
        catch(FileNotFoundException ex) {
            System.out.println("Unable to open file '" + FILE_LOG + "'");
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
    }
 
 
    /**
     * Vyčistí log
     */
    public static void clear() {
        logs.clear();
    }
 
}