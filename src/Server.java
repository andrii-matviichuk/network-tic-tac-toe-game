import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

public class Server {
    private static List<String> players;
    public static List<Integer> freePorts;
    private static Map<String, HashMap<BufferedWriter, BufferedReader>> playersWaiting;
    private static List<String> viewers;
    private static Map<String, String[]> currentGames;
    private static List<Integer> packetsUniqueNums;

    public static void main(String[] args) throws IOException {
        players = Collections.synchronizedList(new ArrayList<>());
        playersWaiting = Collections.synchronizedMap(new HashMap<>());
        freePorts = Collections.synchronizedList(new LinkedList<>());
        viewers = Collections.synchronizedList(new ArrayList<>());
        currentGames = Collections.synchronizedMap(new HashMap<>());
        packetsUniqueNums = Collections.synchronizedList(new LinkedList<>());
        packetsUniqueNums.add(0);

        for (int i = 20000; i < 50000; i++) {
            freePorts.add(i);
        }
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(Integer.parseInt(args[0]));
        } catch (IOException e) {
            log("Unable to create server socket! Reason: " + e.getMessage());
        }
        log("Server socket created!");
        DatagramSocket viewSocket = null;
        try {
            viewSocket = new DatagramSocket(Integer.parseInt(args[1]));
            viewSocket.setSoTimeout(2000);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        DatagramSocket finalViewSocket = viewSocket;
        new Thread(() -> {
            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(new byte[4096], 4096);
                try {
                    finalViewSocket.receive(receivePacket);
                } catch (SocketTimeoutException e) {
                    continue;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                new Thread(() -> {
                    InetAddress clientAddress = receivePacket.getAddress();
                    int clientPort = receivePacket.getPort();
                    byte[] clientData = receivePacket.getData();

                    String message = new String(Arrays.copyOfRange(clientData, 8, clientData.length)).trim();
                    boolean exists = false;
                    for (String v : viewers) {
                        if (v.equals(clientAddress.toString().split("/")[1] + ":" + clientPort)) {
                            exists = true;
                        }
                    }
                    if (!exists) {
                        log("New viewer connected:  " + clientAddress.toString().split("/")[1] + ":" + clientPort);
                        viewers.add(clientAddress.toString().split("/")[1] + ":" + clientPort);
                    }
                    if (exists && message.equals("STOP")) {
                        log("Viewer " + clientAddress.toString().split("/")[1] + ":" + clientPort + " has disconnected");
                        viewers.remove(clientAddress.toString().split("/")[1] + ":" + clientPort);
                    }

                    String ACKAnswer = "ACK" + ByteBuffer.wrap(Arrays.copyOfRange(clientData, 0, 8)).getInt();
                    byte[] ACKData = ACKAnswer.getBytes();
                    DatagramPacket sendPacket = new DatagramPacket(ACKData, ACKData.length, clientAddress, clientPort);
                    try {
                        finalViewSocket.send(sendPacket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        }).start();
        while (true) {
            try {
                log("Listening...");
                Socket clientSocket = serverSocket.accept();
                log("Player" + players.size() + " (" + clientSocket.getRemoteSocketAddress().toString().substring(1) + ") connected");
                new Thread(() -> {
                    Socket connectionSocket = clientSocket;
                    String playerIdentifier = "Player" + players.size() + ": " + connectionSocket.getRemoteSocketAddress().toString().substring(1);
                    players.add(playerIdentifier);
                    try {
                        BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
                        BufferedWriter outToClient = new BufferedWriter(new OutputStreamWriter(connectionSocket.getOutputStream()));
                        outToClient.write(playerIdentifier);
                        outToClient.newLine();
                        outToClient.flush();
                        while (true) {
                            String request = inFromClient.readLine();
                            if (request.equals("LIST")) {
                                log(playerIdentifier + " asked for LIST");
                                synchronized (Server.class) {
                                    log("Sending list to client...");
                                    StringBuilder sb = new StringBuilder();
                                    for (int i = 0; i < players.size(); i++) {
                                        sb.append(players.get(i) + "@");
                                    }
                                    outToClient.write(sb.toString());
                                    outToClient.newLine();
                                    outToClient.flush();
                                    log("The list has been send!");
                                }
                            } else if (request.equals("LOGOUT")) {
                                log(playerIdentifier + " has logged out");
                                players.remove(playerIdentifier);
                                playersWaiting.remove(playerIdentifier);
                                outToClient.close();
                                inFromClient.close();
                                connectionSocket.close();
                                break;
                            } else if (request.equals("PLAY")) {
                                log(playerIdentifier + " send PLAY");
                                if (playersWaiting.size() > 0) {
                                    log(playerIdentifier + " started game");
                                    int rand = (int) (Math.random() * 10);
                                    BufferedWriter toPlayer = null;
                                    BufferedReader fromPlayer = null;
                                    String playerInd = "";
                                    for (Map.Entry<String, HashMap<BufferedWriter, BufferedReader>> entry : playersWaiting.entrySet()) {
                                        for (Map.Entry<BufferedWriter, BufferedReader> entry1 : entry.getValue().entrySet()) {
                                            toPlayer = entry1.getKey();
                                            fromPlayer = entry1.getValue();
                                        }
                                        playerInd = entry.getKey();
                                    }

                                    playersWaiting.remove(playerInd);

                                    ServerSocket gameServer = new ServerSocket(freePorts.get(0));
                                    freePorts.remove(0);
                                    outToClient.write("P:" + gameServer.getLocalPort());
                                    outToClient.newLine();
                                    outToClient.flush();
                                    Socket gameClientSocket = gameServer.accept();
                                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(gameClientSocket.getOutputStream()));
                                    BufferedReader br = new BufferedReader(new InputStreamReader(gameClientSocket.getInputStream()));

                                    boolean player1Starts = false;
                                    if (rand <= 5) {
                                        player1Starts = true;
                                        toPlayer.write("ACKX");
                                        toPlayer.newLine();
                                        toPlayer.flush();

                                        bw.write("ACKO");
                                        bw.newLine();
                                        bw.flush();
                                    } else {
                                        toPlayer.write("ACKO");
                                        toPlayer.newLine();
                                        toPlayer.flush();

                                        bw.write("ACKX");
                                        bw.newLine();
                                        bw.flush();
                                    }

                                    int counter = 0;
                                    String[] currentStateOfGame = new String[9];
                                    for (int i = 0; i < currentStateOfGame.length; i++) {
                                        currentStateOfGame[i] = String.valueOf(i);
                                    }
                                    currentGames.put(playerInd + " vs " + playerIdentifier, currentStateOfGame);
                                    boolean tie = true;
                                    while (counter < 9) {
                                        if (player1Starts) {
                                            try {
                                                log("Waiting for " + playerInd + " turn");
                                                String answer = fromPlayer.readLine();
                                                log(playerInd + " pressed " + answer);
                                                currentStateOfGame[Integer.parseInt(answer.substring(0, 1))] = answer.substring(1, 2);
                                                currentGames.put(playerInd + " vs " + playerIdentifier, currentStateOfGame);
                                                if (isGameOver(currentStateOfGame, Integer.parseInt(answer.substring(0, 1)))) {
                                                    toPlayer.write("FINISH_WIN");
                                                    toPlayer.newLine();
                                                    toPlayer.flush();

                                                    bw.write("FINISH_LOS" + answer.substring(0, 1));
                                                    bw.newLine();
                                                    bw.flush();

                                                    log(playerInd + " WINS!");
                                                    tie = false;
                                                    break;
                                                } else {
                                                    bw.write(answer.substring(0, 1));
                                                    bw.newLine();
                                                    bw.flush();
                                                }
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                            player1Starts = false;
                                        } else {
                                            try {
                                                log("Waiting for " + playerIdentifier + " turn");
                                                String answer = br.readLine();
                                                log(playerIdentifier + " pressed " + answer);
                                                currentStateOfGame[Integer.parseInt(answer.substring(0, 1))] = answer.substring(1, 2);
                                                currentGames.put(playerInd + " vs " + playerIdentifier, currentStateOfGame);
                                                if (isGameOver(currentStateOfGame, Integer.parseInt(answer.substring(0, 1)))) {
                                                    bw.write("FINISH_WIN");
                                                    bw.newLine();
                                                    bw.flush();

                                                    toPlayer.write("FINISH_LOS" + answer.substring(0, 1));
                                                    toPlayer.newLine();
                                                    toPlayer.flush();
                                                    tie = false;
                                                    break;
                                                } else {
                                                    toPlayer.write(answer.substring(0, 1));
                                                    toPlayer.newLine();
                                                    toPlayer.flush();
                                                }
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                            player1Starts = true;
                                        }
                                        for (String v : viewers) {
                                            InetAddress ip = InetAddress.getByName(v.split(":")[0]);
                                            int port = Integer.parseInt(v.split(":")[1]);
                                            byte[] data = new byte[4088];
                                            StringBuilder sb = new StringBuilder();
                                            for (Map.Entry<String,String[]> cur : currentGames.entrySet()) {
                                                sb.append(cur.getKey() + "@"+Arrays.toString(cur.getValue()).substring(1,Arrays.toString(cur.getValue()).length()-1)+"!");
                                            }
                                            data = sb.toString().getBytes();
                                            sendUDPPacket(data, finalViewSocket, ip, port);
                                        }
                                        log("Game state was sent to all viewers");
                                        counter++;
                                    }
                                    currentGames.remove(playerInd + " vs " + playerIdentifier);
                                    if (tie) {
                                        toPlayer.write("FINISH_TIE");
                                        toPlayer.newLine();
                                        toPlayer.flush();

                                        bw.write("FINISH_TIE");
                                        bw.newLine();
                                        bw.flush();
                                    }
                                    toPlayer.close();
                                    bw.close();
                                    gameClientSocket.close();
                                } else {
                                    log(playerIdentifier + " was put in waiting list");
                                    HashMap<BufferedWriter, BufferedReader> map = new HashMap<>();
                                    playersWaiting.put(playerIdentifier, map);
                                    ServerSocket gameServer = new ServerSocket(freePorts.get(0));
                                    freePorts.remove(0);
                                    outToClient.write("P:" + gameServer.getLocalPort());
                                    outToClient.newLine();
                                    outToClient.flush();
                                    Socket gameClientSocket = gameServer.accept();
                                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(gameClientSocket.getOutputStream()));
                                    BufferedReader br = new BufferedReader(new InputStreamReader(gameClientSocket.getInputStream()));
                                    map.put(bw, br);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log("Unable to continue working with " + playerIdentifier + "! Reason: " + e.getMessage());
                        players.remove(playerIdentifier);
                        try {
                            connectionSocket.close();
                        } catch (IOException ex) {
                            ex.getMessage();
                        }
                    }
                }).start();
            } catch (Exception e) {
                log("Unable to connect to client! Reason: " + e.getMessage());
            }
        }
    }

    private synchronized static int sendUDPPacket(byte[] data, DatagramSocket sender, InetAddress receiverAddress, int receiverPort) {
        if (data.length > 4088) {
            log("Can`t send datagram packet. It`s too long!");
            return -1;
        }
        byte[] uniqueNum = ByteBuffer.allocate(8).putInt(packetsUniqueNums.get(packetsUniqueNums.size() - 1)).array();
        packetsUniqueNums.add(packetsUniqueNums.get(packetsUniqueNums.size() - 1) + 1);
        byte[] sendData = new byte[4096];
        System.arraycopy(uniqueNum, 0, sendData, 0, 8);
        System.arraycopy(data, 0, sendData, 8, data.length);

        DatagramPacket packetToSend = new DatagramPacket(sendData, sendData.length, receiverAddress, receiverPort);
        try {
            sender.send(packetToSend);
        } catch (IOException e) {
            log("Socket is not able to sent UDP Packet! Reason: " + e.getMessage());
        }
        return -1;
    }

    public static boolean isGameOver(String[] curState, int n) {
        // 0 1 2
        // 3 4 5
        // 6 7 8
        int row = n - n % 3;
        if (curState[row].equals(curState[row + 1]) && curState[row].equals(curState[row + 2])) {
            return true;
        }
        int column = n % 3;
        if (curState[column].equals(curState[column + 3])) {
            if (curState[column].equals(curState[column + 6])) {
                return true;
            }
        }

        if (n % 2 != 0) {
            return false;
        }
        if (n % 4 == 0) {
            if (curState[0].equals(curState[4]) && curState[0].equals(curState[8])) {
                return true;
            }
            if (n != 4) {
                return false;
            }
        }
        return curState[2].equals(curState[4]) && curState[2].equals(curState[6]);
    }

    public static void log(String message) {
        System.out.println("[S]: " + message);
    }
}
