/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package logic;

import java.awt.event.ActionEvent;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import view.ClientCardWindow;
import view.LoginPanel;
import view.MainWindow;
import view.UserEncryptionPanel;

/**
 *
 * @author PC
 */
public class Client implements Runnable {

    private String username;
    private UserPrivileges currentPrivileges;
    private Socket client;
    private String serverAddress;
    private ObjectInputStream input;
    private ObjectOutputStream output;
    private final int port = 15465;
    private MainWindow mw;
    private UserEncryptionPanel userEncryptionPanel;
    private JTextField encryptionParameterField;
    private JTextField encryptionResultField;
    private JTextField decryptionParameterField;
    private JTextField decryptionResultField;
    private JPasswordField passwordField;
    private JLabel wrongLoginDetailsLabel;
    private ClientCardWindow cardView;
    private JTextField usernameField;
    private LoginPanel loginPanel;
    private JButton submitButton;
    private JButton decryptButton;
    private JButton encryptButton;

    private void initComponents() {
        mw = new MainWindow();
        cardView = new ClientCardWindow();
        cardView.setVisible(false);

        loginPanel = mw.getLoginPanel();
        userEncryptionPanel = cardView.getUserEncryptionPanel();

        submitButton = loginPanel.getSubmitButton();
        submitButton.addActionListener((ActionEvent evt) -> loginUser());
        usernameField = loginPanel.getUsernameField();
        passwordField = loginPanel.getPasswordField();
        passwordField.addActionListener((ActionEvent evt) -> loginUser());
        username = loginPanel.getName();

        wrongLoginDetailsLabel = loginPanel.getWrongLoginDetailsLabel();

        decryptButton = userEncryptionPanel.getDecryptButton();
        decryptButton.addActionListener(
                (ActionEvent evt) -> decryptButtonActionPerformed(evt));
        encryptButton = userEncryptionPanel.getEncryptButton();
        encryptButton.addActionListener(
                (ActionEvent evt) -> encryptButtonActionPerformed(evt));
        encryptionParameterField
                = userEncryptionPanel.getEncryptionParameterField();
        encryptionParameterField.addActionListener(
                (ActionEvent evt) -> encryptButtonActionPerformed(evt));
        encryptionResultField = userEncryptionPanel.getEncryptionResultField();
        decryptionParameterField
                = userEncryptionPanel.getDecrtyptionParameterField();
        decryptionParameterField.addActionListener(
                (ActionEvent evt) -> decryptButtonActionPerformed(evt));
        decryptionResultField = userEncryptionPanel.getDecryptionResultField();

        mw.setVisible(true);
    }

    private void decryptButtonActionPerformed(ActionEvent evt) {
        String result = sendDecryptionRequest(
                decryptionParameterField.getText());
        if (result != null) {
            decryptionResultField.setText(result);
        }

    }

    private void encryptButtonActionPerformed(ActionEvent evt) {
        if (validate(encryptionParameterField.getText())) {
            String result = sendEncryptRequest(encryptionParameterField.
                    getText());
            if (result != null) {
                encryptionResultField.setText(result);
            }
        } else {
            JOptionPane.showMessageDialog(null,
                    "The card number you have entered is invalid.\n"
                    + "Please check it and proceed", "Wrong Number",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loginUser() {
        if (usernameField.getText().length() > 0
                && passwordField.getPassword().length > 0) {
            if (verifyUser(usernameField.getText(),
                    new String(passwordField.getPassword()))) {
                JOptionPane.showMessageDialog(null, "Welcome "
                        + usernameField.getText() + "!");
                mw.setVisible(false);
                cardView.setVisible(true);
            } else {
                submitButton.setEnabled(false);
                wrongLoginDetailsLabel.setVisible(true);
                return;
            }
        } else {
            JOptionPane.showMessageDialog(null,
                    "You need to enter both a username and a password");
            return;
        }
    }

    private boolean verifyUser(String username, String password) {
        final String sendAccountInfoCode = "___ACC___";
        try {
            output.writeObject(sendAccountInfoCode);
            output.writeObject(username);
            output.writeObject(password);
            output.flush();
            boolean temp = (boolean) input.readObject();
            return temp;
        } catch (IOException ioe) {
            System.out.println("Problem with verification");
            return false;
        } catch (ClassNotFoundException cnfe) {
            System.out.println("This isn't a boolean value, as expected");
            return false;
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(null, message, "Error!",
                JOptionPane.ERROR_MESSAGE);
    }

    private void runClient() {
        try {
            connectToServer();
            getStreams();
        } catch (EOFException eof) {
            showError("Terminated connection...\nExiting client...");
            System.exit(0);
        } catch (IOException ioe) {
            showError("There was a problem with getting the streams.\n"
                    + "Perhaps the server is offline.\n"
                    + "Exiting client...");
            System.exit(0);
        }
    }

    private void connectToServer() throws IOException {
        client = new Socket(InetAddress.getByName(serverAddress), port);
    }

    private boolean validate(String cardNumber) {
        //validates the card using the Luhn formula
        if (checkCardFormat(cardNumber)) {
            cardNumber = cardNumber.replaceAll("\\s+", "");
            int sum = 0;
            for (int a = cardNumber.length() - 1; a >= 0; a -= 2) {
                sum += (int) (cardNumber.charAt(a) - '0');
            }
            for (int a = cardNumber.length() - 2; a >= 0; a -= 2) {
                int temp = 2 * (int) (cardNumber.charAt(a) - '0');
                temp = temp % 10 + temp / 10;
                sum += temp;
            }
            return sum % 10 == 0;
        }
        return false;
    }

    private boolean checkCardFormat(String cardNumber) {
        //visa, mastercard, discover have 16 digit numbers
        Pattern visaMCDiscover = Pattern.compile("([4-6]\\s*)([0-9]\\s*){15}");
        //amex has 15, not 16 igits
        Pattern amEx = Pattern.compile("(3\\s*)([0-9]\\s*){14}");
        Matcher firstMatch = visaMCDiscover.matcher(cardNumber);
        Matcher secondMatch = amEx.matcher(cardNumber);

        return firstMatch.matches() || secondMatch.matches();
    }

    private void getStreams() throws IOException {
        output = new ObjectOutputStream(client.getOutputStream());
        output.flush();

        input = new ObjectInputStream(client.getInputStream());
    }

    private void processConnection() throws IOException {
        String message;
        do {
            try {
                message = (String) input.readObject();
                System.out.println(message);
            } catch (ClassNotFoundException cnfe) {
                System.out.println("Unknown object received");
            }
        } while (true);
    }

//this method can be used in the future
//    private void closeConnection() {
//        System.out.println("Closing connection...");
//        try {
//            output.close();
//            input.close();
//            client.close();
//        } catch (IOException ioe) {
//            JOptionPane.showMessageDialog(null,
//                    "There was a problem with the stream closing process...", "ERROR",
//                    JOptionPane.ERROR_MESSAGE);
//            System.exit(0);
//        }
//    }
    private String sendDecryptionRequest(String cryptedNumber) {
        if (currentPrivileges == UserPrivileges.CANNOT_ENCRYPT) {
            JOptionPane.showMessageDialog(null, "You are not allowed to do this",
                    "Access Restriction", JOptionPane.ERROR_MESSAGE);
            return "";
        } else {
            String result = null;
            try {
                output.writeObject("___DEC___");
                output.writeObject(cryptedNumber);
                output.flush();
                result = (String) input.readObject();
            } catch (IOException ioe) {
                JOptionPane.showMessageDialog(null,
                        "Perhaps the server has shut down, exiting client...");
                System.exit(0);
            } catch (ClassNotFoundException cnfe) {
                System.out.println("not the right object");
            }
            return result;

        }
    }

    private String sendEncryptRequest(String cardNumber) {
        if (currentPrivileges == UserPrivileges.CANNOT_ENCRYPT) {
            JOptionPane.showMessageDialog(null, "You are not allowed to do this",
                    "Access Restriction", JOptionPane.ERROR_MESSAGE);
            return "";
        } else {
            String result = null;
            try {
                if (checkCardFormat(cardNumber) && validate(cardNumber)) {
                    output.writeObject("___CRY___");
                    output.writeObject(cardNumber);
                    output.flush();
                    result = (String) input.readObject();
                }
            } catch (IOException ioe) {
                JOptionPane.showMessageDialog(null,
                        "Perhaps the server has shut down, exiting client...");
                System.exit(0);
            } catch (ClassNotFoundException cnfe) {
                System.out.println("not the right object");

            }
            return result;

        }
    }

    public Client(String host) {
        serverAddress = host;
        initComponents();
        runClient();
    }

    public Client() {
        this("127.0.0.1");
    }

    public void run() {
        runClient();
    }
}
