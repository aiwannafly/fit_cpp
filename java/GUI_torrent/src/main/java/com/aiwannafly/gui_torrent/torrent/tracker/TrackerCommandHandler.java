package torrent.tracker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;

public class TrackerCommandHandler implements Runnable {
    private final Socket clientSocket;
    private final TrackerServer server;
    private final static int PEER_ID_LENGTH = 20;
    public final static String GET_COMMAND = "get";
    public final static String EXIT_COMMAND = "exit";
    public final static String SET_LISTENING_SOCKET = "listen-port";
    public final static String SHOW_COMMAND = "show";
    private final static String PEERS_LIST = "peers";
    private final static String PEER_ID = "peer_id";
    private final static String WRONG_COMMAND_MSG = "Wrong command";
    private final static String INCOMPLETE_COMMAND_MSG = "Incomplete command";
    private PrintWriter out = null;
    private BufferedReader in = null;

    public TrackerCommandHandler(Socket socket, TrackerServer server) {
        this.clientSocket = socket;
        this.server = server;
    }

    public void run() {
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            while (true) {
                String command = in.readLine();
                if (null == command) {
                    break;
                }
                System.out.printf("Sent from the client: %s\n", command);
                if (command.equals(EXIT_COMMAND)) {
                    synchronized (server) {
                        server.getClients().remove(clientSocket);
                        server.getSeedsInfo().remove(clientSocket);
                    }
                    break;
                }
                String message = getResponse(command);
                out.println(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                    clientSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getResponse(String command) {
        String[] words = command.split(" ");
        switch (words[0]) {
            case GET_COMMAND -> {
                if (words.length < 2) {
                    return INCOMPLETE_COMMAND_MSG;
                }
                if (PEER_ID.equals(words[1])) {
                    return generatePeerId();
                }
                return WRONG_COMMAND_MSG;
            }
            case SHOW_COMMAND -> {
                if (words.length < 2) {
                    return INCOMPLETE_COMMAND_MSG;
                }
                if (PEERS_LIST.equals(words[1])) {
                    if (words.length < 3) {
                        return INCOMPLETE_COMMAND_MSG;
                    }
                    String torrentFileName = words[2];
                    StringBuilder message = new StringBuilder();
                    message.append("Peers: ");
                    synchronized (server) {
                        ArrayList<Socket> fileSeeds = server.getSeedPorts().get(torrentFileName);
                        for (Socket client : fileSeeds) {
                            if (client == clientSocket) {
                                continue;
                            }
                            if (server.getSeedsInfo().containsKey(client)) {
                                int port = server.getSeedsInfo().get(client).port;
                                ArrayList<Integer> availablePieces = server.getSeedsInfo().get(client).availablePieces;
                                message.append(server.getSeedsInfo().get(client).port).append(" ");
                                message.append(availablePieces.size()).append(" ");
                                for (Integer piece: availablePieces) {
                                    message.append(piece).append(" ");
                                }
                            }
                        }
                    }
                    return message.toString();
                }
                return WRONG_COMMAND_MSG;
            }
            case SET_LISTENING_SOCKET -> {
                if (words.length < 3) {
                    return INCOMPLETE_COMMAND_MSG;
                }
                Integer port = Integer.parseInt(words[1]);
                String torrentFileName = words[2];
                ArrayList<Integer> availablePieces = new ArrayList<>();
                try {
                    int piecesCount = Integer.parseInt(words[3]);
                    if (words.length != 1 + 1 + 1 + 1 + piecesCount) {
                        return WRONG_COMMAND_MSG;
                    }
                    for (int i = 0; i < piecesCount; i++) {
                        availablePieces.add(Integer.parseInt(words[4 + i]));
                    }
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    return WRONG_COMMAND_MSG;
                }
                synchronized (server) {
                    server.getSeedPorts().computeIfAbsent(torrentFileName, k -> new ArrayList<>());
                    if (!server.getSeedPorts().get(torrentFileName).contains(clientSocket)) {
                        server.getSeedPorts().get(torrentFileName).add(clientSocket);
                    }
                    TrackerServer.SeedInfo seedInfo = new TrackerServer.SeedInfo();
                    seedInfo.port = port;
                    seedInfo.availablePieces = availablePieces;
                    server.getSeedsInfo().put(clientSocket, seedInfo);
                }
            }
        }
        return WRONG_COMMAND_MSG;
    }

    private String generatePeerId() {
        StringBuilder peerId = new StringBuilder();
        peerId.append("PEER_AIW_");
        synchronized (server) {
            if (!server.getSeedsInfo().containsKey(clientSocket)) {
                return null;
            }
            peerId.append(server.getSeedsInfo().get(clientSocket));
        }
        peerId.append(peerId.toString().hashCode());
        return peerId.substring(0, PEER_ID_LENGTH);
    }
}
