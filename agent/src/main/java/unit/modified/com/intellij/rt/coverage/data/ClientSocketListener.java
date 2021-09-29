package unit.modified.com.intellij.rt.coverage.data;

import java.io.*;
import java.net.Socket;

public class ClientSocketListener {
    private Socket clientSocket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public ClientSocketListener(int port) throws IOException {
        clientSocket = new Socket("127.0.0.1", port);
        out = new ObjectOutputStream(clientSocket.getOutputStream());
        in = new ObjectInputStream(clientSocket.getInputStream());
    }

    public void startListening() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Redirector.COVERAGE_INFO.put(Redirector.LAST_SET, Redirector.COVERED_METHODS);
            sendMessage(Redirector.COVERAGE_INFO);
            stopConnection();
        }));
    }

    private void sendMessage(Object msg) {
        try {
            out.writeObject(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Object readMessage() {
        try {
            return in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void stopConnection() {
        try {
            in.close();
            out.close();
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
