package webserver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.InetAddress;

public class HttpRequestHandler extends Thread {
    private Socket socket; // socket untuk koneksi ke klien
    private String logsPath; // path ke direktori logs yang disetel sesuai dengan GUI nya
    private WebServer webServer;
    private InetAddress inetAddress;

    // Konstruktor untuk HttpRequestHandler
    public HttpRequestHandler(Socket socket, String logsPath, WebServer server) {
        this.socket = socket;
        this.logsPath = logsPath;
        this.webServer = server;
    }

    @Override
    // Ini menangani logika atau alur dari HttpRequestHandler
    public void run() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(socket.getOutputStream())
        ) {
            // Membaca input seperti GET /index.html HTTP/1.1
            String requestLine = in.readLine(); // membaca baris permintaan dari klien
            if (requestLine != null && !requestLine.isEmpty()) {
                String[] tokens = requestLine.split(" "); // dipecah permintaannya jadi beberapa token
                String method = tokens[0]; // mendapatkan metode HTTP-nya (biasanya GET)
                String requestURL = tokens[1]; // mendapatkan URL-nya

                // Memeriksa metode HTTP, jika GET maka diproses nantinya
                if (method.equals("GET")) {
                    serveFile(requestURL, out);
                } else {
                    // Metode selain GET akan direspon dengan not implemented
                    String response = "HTTP/1.1 501 Not Implemented\r\n\r\n";
                    out.writeBytes(response);
                }
                // Mencatat di log-nya
                logAccess(requestURL, socket.getInetAddress().getHostAddress(), requestURL);
            
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Penutupan socket dengan aman
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Metode untuk menangani file yang direquest klien
    private void serveFile(String requestURL, DataOutputStream out) throws IOException {
        try {
            // Mendapatkan path file
            String filePath = Paths.get(WebServer.getWebRoot(), requestURL).toString();
            // Membuat objek file
            File file = new File(filePath);

            if (file.exists()) {
                if (file.isDirectory()) {
                    if (requestURL.endsWith("/")) {
                        // Melayani daftar direktori kalau path berakhir dengan "/"
                        listDirectory(file, out, getParentDirectory(requestURL));
                    } else {
                        // Mengarahkan ke URL dengan akhiran "/" (jika tidak ada "/")
                        String redirectURL = requestURL + "/";
                        String response = "HTTP/1.1 301 Moved Permanently\r\nLocation: " + redirectURL + "\r\n\r\n";
                        out.writeBytes(response);
                    }
                } else {
                    // Menentukan jenis konten berdasarkan ekstensi file
                    String contentType = getContentType(file);

                    // Membaca konten file dan mengirimkannya sebagai respons
                    byte[] fileData = Files.readAllBytes(file.toPath());
                    String response = "HTTP/1.1 200 OK\r\nContent-Length: " + fileData.length +
                            "\r\nContent-Type: " + contentType + "\r\n\r\n";
                    out.writeBytes(response);
                    out.write(fileData);
                }
            } else {
                // Jika file tidak ditemukan
                String response = "HTTP/1.1 404 Not Found\r\n\r\n";
                out.writeBytes(response);
            }
        } catch (IOException e) {
            // Menangani error
            String errorMessage = e.getMessage();
            String response = "HTTP/1.1 500 Internal Server Error\r\n\r\n";
            out.writeBytes(response);

            // Tetap menulis log meskipun terjadi error
            logAccess(requestURL, socket.getInetAddress().getHostAddress(), errorMessage);
        }
    }

    // Mengambil tipe file berdasarkan ekstensi
    private String getContentType(File file) {
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return "text/html";
        } else if (fileName.endsWith(".pdf")) {
            return "application/pdf";
        } else if (fileName.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (fileName.endsWith(".txt")) {
            return "text/plain";
        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        } else if (fileName.endsWith(".gif")) {
            return "image/gif";
        } else if (fileName.endsWith(".css")) {
            return "text/css";
        } else {
            // Default content type untuk file yang tidak dikenal
            return "application/octet-stream";
        }
    }

    // Menyediakan daftar direktori sebagai respon
    private void listDirectory(File directory, DataOutputStream out, String parentDirectory) throws IOException {
        // Mendapatkan daftar file dalam direktori yang diberikan
        File[] files = directory.listFiles();
        // Membangun respons HTML untuk menampilkan daftar file
        
        StringBuilder responseBuilder = new StringBuilder("<html><head>");

        // Menambahkan style untuk background image dengan URL eksternal
       responseBuilder.append("<style>");
    responseBuilder.append("body::before { content: ''; position: fixed; top: 0; left: 0; width: 100%; height: 100%; ");
    responseBuilder.append("background-image: url('https://upload.wikimedia.org/wikipedia/commons/9/98/Logo_udinus1.jpg'); ");
    responseBuilder.append("background-size: 30%; background-repeat: no-repeat; background-position: center; opacity: 0.5; z-index: -1; }");
    responseBuilder.append("</style>");

    responseBuilder.append("</head><body><h1>Directory Listing</h1>");
        
        // Menambahkan tombol "Back" jika tidak berada di direktori root
        if (parentDirectory != null) {
            responseBuilder.append("<button onclick=\"goBack()\">Back</button><br>");
        }

        // Membuat daftar file yang ada dalam direktori dalam bentuk list dan hyperlink
        responseBuilder.append("<ul>");
        if (files != null) {
            for (File file : files) {
                String fileName = file.getName();
                // Memberi hyperlink pada setiap file dalam daftar
                responseBuilder.append("<li><a href=\"").append(fileName).append("\">").append(fileName).append("</a></li>");
            }
        }

        // Menambahkan skrip untuk tombol back
        responseBuilder.append("</ul>");
        responseBuilder.append("<script>");
        responseBuilder.append("function goBack() { window.history.back(); }"); // Skrip JavaScript untuk kembali
        responseBuilder.append("</script>");
        responseBuilder.append("</body></html>");

        // Mengirim respons ke klien
        String response = "HTTP/1.1 200 OK\r\nContent-Length: " + responseBuilder.length() +
                "\r\nContent-Type: text/html\r\n\r\n" + responseBuilder.toString();
        out.writeBytes(response);
    }


    // Mengambil direktori induk dari URL permintaan
    private String getParentDirectory(String requestURL) {
        // Mencari indeks posisi terakhir tanda '/' dalam URL permintaan
        int lastSlashIndex = requestURL.lastIndexOf("/");
        // Memeriksa apakah URL bukan root directory
        if (lastSlashIndex > 0) {
            // Mengembalikan substring URL dari awal hingga sebelum tanda '/'
            return requestURL.substring(0, lastSlashIndex);
        }
        // Jika URL adalah root directory, mengembalikan null
        return null;
    }

    // Metode untuk mencatat akses
    private void logAccess(String requestURL, String ipAddress, String message) {
        // Tanggal saat ini dalam format yyyy-MM-dd
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String logFileName = dateFormat.format(new Date()) + ".log";
        String logFilePath = Paths.get(logsPath, logFileName).toString();

        try {
            // Membuat direktori logs jika belum ada
            File logsDir = new File(logsPath);
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            // Membuat file log jika belum ada
            File logFile = new File(logFilePath);
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
            // Format pesan log dengan tanggal, alamat IP, dan URL permintaan
            String logEntry = String.format("[%s] %s - %s\n", new Date(), ipAddress, requestURL + " : " + message);
            // Tulis log entry ke dalam file log
            Files.write(Paths.get(logFilePath), logEntry.getBytes(), java.nio.file.StandardOpenOption.APPEND);

            // Tambahkan log ke area log pada antarmuka pengguna
            appendToLog(logEntry);
        } catch (IOException e) {
            e.printStackTrace();
    }
}


    // Memuat log akses dari file log
    public List<String> loadAccessLogs() {
        List<String> logs = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String logFileName = dateFormat.format(new Date()) + ".log";
        String logFilePath = Paths.get(logsPath, logFileName).toString(); // Sesuaikan dengan path log Anda

        try (BufferedReader reader = new BufferedReader(new FileReader(logFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logs.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return logs;
    }

    private void appendToLog(String logEntry) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
    
}