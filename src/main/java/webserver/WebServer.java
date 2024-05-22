package webserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;


public class WebServer {
    private static String webRoot = "./webroot";
    private int port;
    private String logsPath;
    private ExecutorService threadPool;
    private AtomicBoolean running;
    private ServerSocket serverSocket;

    public WebServer(String webRoot, String logsPath, int port) {
        WebServer.webRoot = webRoot;
        this.logsPath = logsPath;
        this.port = port;
        this.threadPool = Executors.newCachedThreadPool();
        this.running = new AtomicBoolean(false);
    }

    public void start() {
        running.set(true);
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Web server started on port " + port);
            while (running.get()) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new HttpRequestHandler(clientSocket, logsPath, this));
            }
        } catch (IOException e) {
            if (running.get()) {
                e.printStackTrace();
            }
        } finally {
            stopServer();
        }
    }

    public void stopServer() {
        running.set(false);
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        threadPool.shutdown();
        System.out.println("Web server stopped");
    }

    public boolean isAlive() {
        return running.get();
    }

    public void setLogsPath(String logsPath) {
        this.logsPath = logsPath;
    }

    public List<String> loadAccessLogs() {
        return new HttpRequestHandler(null, logsPath, this).loadAccessLogs();
    }

    public static String getWebRoot() {
        return webRoot;
    }
}