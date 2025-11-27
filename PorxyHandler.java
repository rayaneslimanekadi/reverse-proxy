import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;


class ProxyHandler {
    public static void main(String args[]) {
        int PORT = 8080;
        byte[] response = new byte[5];
        response[0] = 'H';
        response[1] = 'H';
        response[2] = 'H';
        response[3] = 'H';
        response[4] = 'H';

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is listening on port: " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected at: " + clientSocket);
                InputStream clientStream = clientSocket.getInputStream();
                byte[] request = new byte[64];
                clientStream.read(request);
                String textreq = new String(request, StandardCharsets.UTF_8);
                System.out.println("Request: " + textreq);
                OutputStream outputStream = clientSocket.getOutputStream();
                outputStream.write(response);
                String textres = new String(response, StandardCharsets.UTF_8);
                System.out.println("Response: " + textres);
                clientSocket.close();
            }


        } catch(IOException e) {
            System.out.println("Error");
        }
    }
}