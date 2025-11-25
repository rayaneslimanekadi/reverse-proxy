import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.io.InputStream;

class Main {
    public static void main(String args[]) {
        int PORT = 8080;
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is listening on port: " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected at: " + clientSocket);
                InputStream clientStream = clientSocket.getInputStream();
                System.out.println(clientStream);
                clientSocket.close();
            }


        } catch(IOException e) {
            System.out.println("Error");
        }
    }
}