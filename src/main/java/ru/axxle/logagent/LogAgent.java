package ru.axxle.logagent;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogAgent {
    private static final Pattern CONTENT_LENGTH_PATTERN = Pattern.compile("Content-Length:\\s\\d*");
    private static final Pattern HOST_PATTERN = Pattern.compile("//\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
    private static final Pattern PORT_PATTERN2 = Pattern.compile("\\:\\d{1,6}\\/");
    private static final int DEFAULT_SLEEP = 10000; //10 seconds
    private static final int DEFAULT_PORT = 80;

    static String urlStr;            //спарсить из входных параметров
        static String host;              //спарсить из urlStr
        static int port;                 //спарсить из urlStr
    static int  sleepTimeout;        //спарсить из входных параметров, если нет - то выставить дефолтное значение

    private static void parseArgs (String [] args) {
        sleepTimeout = DEFAULT_SLEEP;
        if (args.length > 0) {
            urlStr = args[0];
            host = parseHost(args[0]);
            port = parsePort(args[0]);
        }
        if (args.length > 1) {
            try {
                sleepTimeout = Integer.parseInt(args[1]);
            } catch (Exception e) {}
        }
    }

    private static String parseHost (String s) {
        Matcher matcher = HOST_PATTERN.matcher(s);
        if(matcher.find()) {
            s = s.substring(matcher.start()+2, matcher.end());
        }
        return s;
    }

    private static int parsePort (String s) {
        Matcher matcher = PORT_PATTERN2.matcher(s);
        if(matcher.find()) {
            s = s.substring(matcher.start()+1, matcher.end()-1);
        }
        int port = DEFAULT_PORT;
        try {
            port = Integer.parseInt(s);
        } catch (Exception e) {}
        return port;
    }

    public static void main(String[] args) throws Exception{
        parseArgs(args);
        int lastNum = Integer.MAX_VALUE;
        Socket socket;
        boolean isInterrupted = false;
        while (!isInterrupted) {
            StringBuilder builder = new StringBuilder();
            Thread.sleep(sleepTimeout);
            socket = new Socket(host, port);
            String headRequest = "HEAD " + urlStr + " HTTP/1.0\r\nCache-Control: max-age=0\r\n\r\n";
            OutputStream os = socket.getOutputStream();
            os.write(headRequest.getBytes());
            os.flush();
            InputStream is = socket.getInputStream();
            int ch;
            while( (ch=is.read())!= -1)
                builder.append((char)ch);
            String s = builder.toString();
            Matcher matcher = CONTENT_LENGTH_PATTERN.matcher(s);
            if(matcher.find()) {
                s = s.substring(matcher.start(), matcher.end());
                Integer num = Integer.parseInt(s.substring(16, s.length()));
                if (num > lastNum) {
                    printNews(lastNum, num-1);
                }
                lastNum = num;
            }
            if (isInterrupted)
                socket.close();
        }
    }

    public static void printNews (int start, int end) throws Exception{
        StringBuilder builder = new StringBuilder();
        Socket socket = new Socket(host, port);
        String request = "GET " + urlStr + " HTTP/1.0" + "\r\nCache-Control: max-age=0" +
                "\r\nRange: bytes=" + start + "-" + end +
                "\r\n\r\n";
        OutputStream os = socket.getOutputStream();
        os.write(request.getBytes());
        os.flush();
        InputStream is = socket.getInputStream();
        int ch;
        while( (ch=is.read())!= -1)
            builder.append((char)ch);
        String s = builder.toString();
        s = s.substring(s.length() - (end - start - 1), s.length()-1);
        System.out.println(s);
        printToLogFile(s);
        socket.close();
    }

    public static void printToLogFile(String s){
        String filePath = "./output.log";
        String text = s;
        try {
            Files.write(Paths.get(filePath), text.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (IOException e) {
            System.out.println(e);
        }
    }
}
