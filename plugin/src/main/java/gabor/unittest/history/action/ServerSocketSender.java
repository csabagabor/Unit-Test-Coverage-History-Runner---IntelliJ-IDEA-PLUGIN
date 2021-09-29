package gabor.unittest.history.action;

import gabor.unittest.history.helper.LoggingHelper;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerSocketSender {
    private final int port;
    private final ServerSocket serverSocket;
    private Socket clientSocket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public ServerSocketSender() throws IOException {
        serverSocket = new ServerSocket(0);
        serverSocket.setReuseAddress(true);//close port immediately if process exits
        port = serverSocket.getLocalPort();
    }

    public void startListening() {
        try {
            clientSocket = serverSocket.accept();
            out = new ObjectOutputStream(clientSocket.getOutputStream());
            in = new ObjectInputStream(clientSocket.getInputStream());
        } catch (IOException e) {
            close();
            LoggingHelper.error(e);
        }
    }

    public Object readMessage() {
        try {
            return in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            LoggingHelper.error(e);
            return null;
        }
    }

    public void close() {
        try {
            in.close();
            out.close();
            clientSocket.close();
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getPort() {
        return port;
    }
}
