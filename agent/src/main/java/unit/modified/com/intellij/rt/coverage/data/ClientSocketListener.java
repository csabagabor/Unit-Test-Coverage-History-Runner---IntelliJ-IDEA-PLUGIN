package unit.modified.com.intellij.rt.coverage.data;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientSocketListener {
    private final Socket clientSocket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

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
