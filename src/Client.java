import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class Client {
    private static List<Integer> packetsUniqueNums = Collections.synchronizedList(new LinkedList<>());

    private static void createAndShowGUI(String[] args) throws IOException {
        packetsUniqueNums.add(0);
        AtomicReference<Font> font = new AtomicReference<>(new Font("Arial Black", Font.BOLD, 20));

        JFrame connectingFrame = new JFrame("Tic-Tac-Toe Game");
        connectingFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        connectingFrame.setSize(300, 200);

        JLabel label = new JLabel("Connecting to server...", SwingConstants.CENTER);
        label.setFont(font.get());
        connectingFrame.add(label);
        connectingFrame.setLocationRelativeTo(null);
        connectingFrame.setVisible(true);

        Socket clientSocket = null;
        try {
            clientSocket = new Socket(args[0], Integer.parseInt(args[1]));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Unable to connect to Server! Reason: " + e.getMessage());
            System.exit(0);
        }

        BufferedWriter outToServer = null;
        BufferedReader inFromServer = null;
        try {
            outToServer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Unable to continue! Reason: " + e.getMessage());
        }
        BufferedWriter finalOut = outToServer;
        BufferedReader finalIn = inFromServer;

        String uniqueIndetifier = finalIn.readLine();

        connectingFrame.dispose();

        JFrame menuFrame = new JFrame("Tic-Tac-Toe Game(" + uniqueIndetifier + ")");
        menuFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        menuFrame.setSize(700, 600);
        menuFrame.setLocationRelativeTo(null);
        menuFrame.setLayout(new GridBagLayout());


        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;

        JLabel gameName = new JLabel("Tic-Tac-Toe Game");
        gameName.setPreferredSize(new Dimension(330, 100));
        font.set(new Font("Arial Black", Font.BOLD, 40));
        gameName.setFont(font.get());
        gameName.setHorizontalAlignment(SwingConstants.CENTER);
        c.gridx = 0;
        c.gridy = 0;
        menuFrame.getContentPane().add(gameName, c);

        JPanel mainMenuPanel = new JPanel();
        mainMenuPanel.setLayout(new GridBagLayout());
        mainMenuPanel.setPreferredSize(new Dimension(500, 400));
        mainMenuPanel.setMaximumSize(mainMenuPanel.getPreferredSize());

        font.set(new Font("Arial Black", Font.BOLD, 24));

        JButton listButton = new JButton("LIST");
        listButton.setPreferredSize(new Dimension(200, 50));
        listButton.setForeground(new Color(112, 1, 102));
        listButton.setFont(font.get());

        listButton.addActionListener((e) -> {
            JFrame listFrame = new JFrame("LIST");
            listFrame.setPreferredSize(new Dimension(600, 400));

            JList<String> list = new JList<>();
            list.setFixedCellHeight(50);
            list.setLayoutOrientation(JList.VERTICAL);
            font.set(new Font("Arial Black", Font.BOLD, 20));
            list.setFont(font.get());
            DefaultListModel<String> listModel = new DefaultListModel<>();
            try {
                finalOut.write("LIST");
                finalOut.newLine();
                finalOut.flush();
                String[] elements = finalIn.readLine().split("@");
                for (int i = 0; i < elements.length; i++) {
                    System.out.println(elements[i]);
                    listModel.addElement(elements[i]);
                }

            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null, "Unable to continue! Reason: " + ex.getMessage());
                return;
            }
            list.setModel(listModel);
            JScrollPane highScoresPane = new JScrollPane(list);
            listFrame.add(highScoresPane);
            listFrame.setLocationRelativeTo(null);
            listFrame.pack();
            listFrame.setVisible(true);
        });
        c.gridx = 0;
        c.gridy = 1;
        c.insets = new Insets(50, 0, 0, 0);

        mainMenuPanel.add(listButton, c);

        JButton playButton = new JButton("PLAY");

        JButton viewButton = new JButton("VIEW");
        viewButton.setForeground(new Color(112, 1, 102));
        viewButton.setFont(font.get());
        viewButton.setPreferredSize(new Dimension(200, 50));

        AtomicBoolean isViewer = new AtomicBoolean(false);

        DatagramSocket viewClientSocket = null;
        try {
            viewClientSocket = new DatagramSocket();
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        DatagramSocket finalViewClientSocket = viewClientSocket;
        InetAddress IPAddress = InetAddress.getByName("localhost");
        viewButton.addActionListener((e) -> {
            isViewer.set(true);
            InetAddress finalIPAddress = IPAddress;
            try {
                finalIPAddress = InetAddress.getByName("localhost");
            } catch (UnknownHostException ex) {
                ex.printStackTrace();
            }

            InetAddress finalIPAddress1 = finalIPAddress;



            int res = sendUDPPacket(new byte[100], finalViewClientSocket, finalIPAddress, Integer.parseInt(args[2]));
            if (res == 0) {
                System.out.println("Connected to udp port. Waiting for data");
            } else {
                System.out.println("Unable to connect to UDP port");
            }
            JOptionPane.showMessageDialog(menuFrame,"Waiting for any game to start\nThe info about the games will be displayed!\nPress OK to continue!");
            playButton.setEnabled(false);
            listButton.setEnabled(false);
            viewButton.setEnabled(false);
            final boolean[] isOpened = {true};
            ArrayList<JFrame> frames = new ArrayList<>();
            new Thread(()->{
                while (isOpened[0]) {
                    DatagramPacket datagramPacket = new DatagramPacket(new byte[4096], 4096, IPAddress, Integer.parseInt(args[2]));
                    try {
                        finalViewClientSocket.receive(datagramPacket);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    for (JFrame frame : frames) {
                        frame.dispose();
                    }
                    JFrame viewFrame = new JFrame("VIEW");
                    viewFrame.setPreferredSize(new Dimension(700, 400));
                    JList<String> list = new JList<>();
                    list.setFixedCellHeight(50);
                    list.setLayoutOrientation(JList.VERTICAL);
                    font.set(new Font("Arial Black", Font.BOLD, 12));
                    list.setFont(font.get());

                    viewFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                    DefaultListModel<String> listModel = new DefaultListModel<>();
                    viewFrame.addWindowListener(new WindowAdapter() {
                        @Override
                        public void windowClosing(WindowEvent e) {
                            isOpened[0] = false;
                            byte[] uniqueNum = ByteBuffer.allocate(8).putInt(packetsUniqueNums.get(packetsUniqueNums.size() - 1)).array();
                            packetsUniqueNums.add(packetsUniqueNums.get(packetsUniqueNums.size() - 1) + 1);
                            byte[] sendData = new byte[4096];
                            System.arraycopy(uniqueNum, 0, sendData, 0, 8);
                            System.arraycopy("STOP".getBytes(), 0, sendData, 8, "STOP".getBytes().length);

                            try {
                                DatagramPacket packetToSend = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("localhost"), Integer.parseInt(args[2]));
                                finalViewClientSocket.send(packetToSend);
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                            try {
                                finalOut.write("LOGOUT");
                                finalOut.newLine();
                                finalOut.flush();
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                            System.exit(0);
                        }
                    });


                    String data = new String(Arrays.copyOfRange(datagramPacket.getData(), 8, datagramPacket.getData().length)).trim();
                    viewFrame.setVisible(false);
                    listModel.removeAllElements();
                    if (data.length()>3) {
                        String[] info = data.split("!");
                        for (int i = 0; i < info.length; i++){
                            String[] elements = info[i].split("@");
                            String[] game = elements[1].split(",");
                            for (int k = 0; k < game.length; k++) {
                                if (!(game[k].trim().equals("X") || game[k].trim().equals("O"))) {
                                    game[k] = "-";
                                }
                            }
                            listModel.add(i,elements[0] + ":" + Arrays.toString(game));
                            System.out.println(elements[0] + ":" + Arrays.toString(game));
                        }
                        list.setModel(listModel);
                        JScrollPane highScoresPane = new JScrollPane(list);
                        viewFrame.add(highScoresPane);
                        viewFrame.setLocationRelativeTo(null);
                        viewFrame.pack();
                        viewFrame.setVisible(true);
                        frames.add(viewFrame);
                    }
                }

            }).start();
        });

        playButton.setForeground(new Color(112, 1, 102));
        playButton.setFont(font.get());


        LinkedList<JButton> buttons = new LinkedList<>();
        playButton.addActionListener((e -> {
            JOptionPane.showMessageDialog(null, "You was added to waiting list. \n" +
                    "Logout to stop waiting for players\n" +
                    "Press OK to continue");
            new Thread(() -> {
                try {
                    finalOut.write("PLAY");
                    finalOut.newLine();
                    finalOut.flush();

                    listButton.setEnabled(false);
                    playButton.setEnabled(false);
                    viewButton.setEnabled(false);
                    String answer = finalIn.readLine();
                    System.out.println(answer);

                    Socket socket = new Socket(args[0], Integer.parseInt(answer.split(":")[1]));

                    BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                    answer = br.readLine();

                    boolean isX = false;

                    if (answer.equals("ACKO") || answer.equals("ACKX")) {
                        if (answer.equals("ACKX")) {
                            isX = true;
                        }
                        menuFrame.setVisible(false);
                        JFrame gameFrame = new JFrame(isX ? "Tic-Tac-Toe Game X " : "Tic-Tac-Toe Game O ");
                        gameFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                        gameFrame.setSize(600, 600);
                        gameFrame.setLocationRelativeTo(null);
                        gameFrame.setLayout(new GridLayout(3, 3));

                        for (int i = 0; i < 9; i++) {
                            JButton button = new JButton();
                            button.setSize(200, 200);
                            int finalI1 = i;
                            boolean finalIsX = isX;
                            button.addActionListener((eh) -> {
                                for (JButton but : buttons) {
                                    but.setEnabled(false);
                                }
                                int finalI = finalI1;
                                if (finalIsX) {
                                    button.setIcon(new ImageIcon("src/x.png"));
                                } else {
                                    button.setIcon(new ImageIcon("src/o.png"));
                                }
                                try {
                                    System.out.println("SENDING TO SERVER " + finalI);
                                    if (finalIsX) {
                                        bw.write(finalI + "X");
                                    } else {
                                        bw.write(finalI + "O");
                                    }
                                    bw.newLine();
                                    bw.flush();
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                }
                            });
                            buttons.add(button);
                            gameFrame.getContentPane().add(button);
                        }

                        if (!isX) {
                            for (JButton but : buttons) {
                                but.setEnabled(false);
                            }
                        }

                        gameFrame.setVisible(true);
                        boolean finalIsX1 = isX;
                        new Thread(() -> {
                            ArrayList<Integer> disabledButtons = new ArrayList<>();
                            while (true) {
                                String answer1 = "";
                                try {
                                    answer1 = br.readLine();
                                    System.out.println(answer1);
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                }
                                if (answer1.length() > 9 && answer1.substring(0, 10).equals("FINISH_WIN")) {
                                    JOptionPane.showMessageDialog(gameFrame, "YOU WON :)");
                                    gameFrame.dispose();
                                    menuFrame.setVisible(true);
                                    try {
                                        bw.close();
                                        br.close();
                                        socket.close();
                                    } catch (IOException ex) {
                                        ex.printStackTrace();
                                    }
                                    buttons.clear();
                                    break;
                                } else if (answer1.length() > 9 && answer1.substring(0, 10).equals("FINISH_LOS")) {
                                    int butNum = Integer.parseInt(answer1.substring(10));
                                    if (finalIsX1) {
                                        buttons.get(butNum).setIcon(new ImageIcon("src/o.png"));
                                    } else {
                                        buttons.get(butNum).setIcon(new ImageIcon("src/x.png"));
                                    }
                                    JOptionPane.showMessageDialog(gameFrame, "YOU LOST :(");
                                    try {
                                        bw.close();
                                        br.close();
                                        socket.close();
                                    } catch (IOException ex) {
                                        ex.printStackTrace();
                                    }
                                    buttons.clear();
                                    gameFrame.dispose();
                                    menuFrame.setVisible(true);
                                    break;
                                } else if (answer1.length() > 9 && answer1.substring(0, 10).equals("FINISH_TIE")) {
                                    JOptionPane.showMessageDialog(gameFrame, "TIE :0");
                                    gameFrame.dispose();
                                    menuFrame.setVisible(true);
                                    try {
                                        bw.close();
                                        br.close();
                                        socket.close();
                                    } catch (IOException ex) {
                                        ex.printStackTrace();
                                    }
                                    buttons.clear();
                                    break;
                                }
                                int butNum = Integer.parseInt(answer1);
                                if (finalIsX1) {
                                    buttons.get(butNum).setIcon(new ImageIcon("src/o.png"));
                                } else {
                                    buttons.get(butNum).setIcon(new ImageIcon("src/x.png"));
                                }

                                for (JButton but : buttons) {
                                    but.setEnabled(true);
                                }
                                disabledButtons.add(butNum);
                                for (Integer num : disabledButtons) {
                                    buttons.get(num).setEnabled(false);
                                }
                            }
                            listButton.setEnabled(true);
                            playButton.setEnabled(true);
                            viewButton.setEnabled(true);
                        }).start();
                    }
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(null, "Unable to continue! Reason: " + ex.getMessage());
                }
            }).start();
        }));
        playButton.setPreferredSize(new Dimension(200, 50));
        c.gridx = 0;
        c.gridy = 0;

        mainMenuPanel.add(playButton, c);

        c.gridx = 0;
        c.gridy = 2;
        c.insets = new Insets(50, 0, 0, 0);

        mainMenuPanel.add(viewButton, c);


        JButton logoutButton = new JButton("LOGOUT");
        logoutButton.setForeground(new Color(112, 1, 102));
        font.set(new Font("Arial Black", Font.BOLD, 24));
        logoutButton.setFont(font.get());
        logoutButton.setPreferredSize(new Dimension(200, 50));
        logoutButton.addActionListener((e) -> {
            try {
                byte[] uniqueNum = ByteBuffer.allocate(8).putInt(packetsUniqueNums.get(packetsUniqueNums.size() - 1)).array();
                packetsUniqueNums.add(packetsUniqueNums.get(packetsUniqueNums.size() - 1) + 1);
                byte[] sendData = new byte[4096];
                System.arraycopy(uniqueNum, 0, sendData, 0, 8);
                System.arraycopy("STOP".getBytes(), 0, sendData, 8, "STOP".getBytes().length);

                try {
                    DatagramPacket packetToSend = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("localhost"), Integer.parseInt(args[2]));
                    finalViewClientSocket.send(packetToSend);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                finalOut.write("LOGOUT");
                finalOut.newLine();
                finalOut.flush();
                if (isViewer.get()) {
                    sendUDPPacket("STOP".getBytes(), finalViewClientSocket, IPAddress, Integer.parseInt(args[2]));
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null, "Unable to continue! Reason: " + ex.getMessage());
                return;
            }
            System.exit(0);
        });

        c.gridx = 0;
        c.gridy = 3;
        c.insets = new Insets(50, 0, 0, 0);

        mainMenuPanel.add(logoutButton, c);

        c.gridx = 0;
        c.gridy = 1;
        c.insets = new Insets(10, 0, 0, 0);

        menuFrame.add(mainMenuPanel, c);

        menuFrame.setVisible(true);
    }

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                createAndShowGUI(args);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private synchronized static int sendUDPPacket(byte[] data, DatagramSocket sender, InetAddress receiverAddress,
                                                  int receiverPort) {
        if (data.length > 4088) {
            return -1;
        }
        byte[] uniqueNum = ByteBuffer.allocate(8).putInt(packetsUniqueNums.get(packetsUniqueNums.size() - 1)).array();
        packetsUniqueNums.add(packetsUniqueNums.get(packetsUniqueNums.size() - 1) + 1);
        byte[] sendData = new byte[4096];
        System.arraycopy(uniqueNum, 0, sendData, 0, 8);
        System.arraycopy(data, 0, sendData, 8, data.length);

        DatagramPacket packetToSend = new DatagramPacket(sendData, sendData.length, receiverAddress, receiverPort);
        DatagramPacket packetToReceive = new DatagramPacket(new byte[4096], 4096, receiverAddress, receiverPort);
        try {
            int counter = 0;
            while (counter < 3) {
                sender.send(packetToSend);
                sender.receive(packetToReceive);
                String uniqueNumAnswer = new String(packetToReceive.getData()).trim();
                if (uniqueNumAnswer.equals("ACKFILE" + ByteBuffer.wrap(uniqueNum).getInt())) {
                    return 1;
                } else if (uniqueNumAnswer.equals("ACK" + ByteBuffer.wrap(uniqueNum).getInt())) {
                    return 0;
                }
                counter++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private synchronized static DatagramPacket receiveUDPPacket(DatagramSocket recipient,
                                                                InetAddress sender, int port) {
        DatagramPacket receivePacket = new DatagramPacket(new byte[4096], 4096, sender, port);
        try {
            recipient.receive(receivePacket);

            byte[] uniqueNum = Arrays.copyOfRange(receivePacket.getData(), 0, 8);

            String answer = "ACK" + ByteBuffer.wrap(uniqueNum).getInt();
            ByteBuffer.wrap(uniqueNum).getInt();

            DatagramPacket packetToSend = new DatagramPacket(answer.getBytes(), answer.getBytes().length, receivePacket.getAddress(), receivePacket.getPort());
            recipient.send(packetToSend);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return receivePacket;
    }
}
