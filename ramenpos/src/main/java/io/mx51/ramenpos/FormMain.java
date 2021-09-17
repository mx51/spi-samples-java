package io.mx51.ramenpos;

import com.google.gson.Gson;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import io.mx51.spi.Spi;
import io.mx51.spi.model.*;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static io.mx51.spi.Spi.getVersion;
import static javax.swing.JOptionPane.*;

public class FormMain extends JFrame implements WindowListener {
    public JPanel pnlSettings;
    public JTextField txtPosId;
    public JTextField txtSecrets;
    public JPanel pnlAutoAddress;
    public JCheckBox testModeCheckBox;
    //    public JCheckBox autoCheckBox;
    public JButton btnSave;
    public JPanel pnlSecrets;
    public JCheckBox secretsCheckBox;
    public JPanel pnlMain;
    public JButton btnTransactions;
    public JTextField txtSerialNumber;
    public JTextField txtDeviceAddress;
    public JButton btnAction;
    public JLabel lblPairingStatus;
    public JLabel lblAutoAddress;
    public JLabel lblSecrets;
    public JPanel pnlSwitch;
    public JLabel lblPosId;
    public JLabel lblSerialNumber;
    public JLabel lblDeviceAddress;
    public JLabel lblSettings;
    public JButton btnResolveAddress;
    public JComboBox cmbTenantsList;
    public JLabel lblTenants;
    public JTextField txtOtherTenant;
    public JLabel lblOther;


    private static final Logger LOG = LogManager.getLogger("spi");
    private static final String apiKey = "RamenPosDeviceAddressApiKey"; // this key needs to be requested from Assembly Payments
    private String tenantCode;

    Spi spi;
    String posId = "";
    String eftposAddress = "";
    Secrets spiSecrets = null;
    TransactionOptions options;
    private String serialNumber = "";
    private boolean autoAddressEnabled = true;
    private boolean testMode;

    private final String multilineHtml = "<html><body style='width: 250px'>";
    private static final String transactionsFile = "transactions.txt";

    static FormAction formAction;
    private static FormTransactions formTransactions;
    static FormMain formMain;
    static JFrame transactionsFrame;
    static JFrame mainFrame;
    static JDialog actionDialog;

    private static HashMap<String, String> secretsFile = new HashMap<>();
    private boolean isStartButtonClicked;
    private boolean isAppStarted;

    private HashMap<String, String> tenantsMap = new HashMap<>();
    public static ArrayList<String> lastTransactions;


    private FormMain() {
        btnSave.addActionListener(e -> {
            if (!areControlsValid(false)) {
                showMessageDialog(null, "Please fill necessary fields ", "Error", ERROR_MESSAGE);
            } else if (spiSecrets != null) {
                saveSecrets();
            }
        });
        btnTransactions.addActionListener(e -> {
            mainFrame.setEnabled(false);
            mainFrame.pack();
            mainFrame.setVisible(false);

            transactionsFrame.pack();
            transactionsFrame.setVisible(true);
        });
        secretsCheckBox.addActionListener(e -> {
            txtSecrets.setEnabled(secretsCheckBox.isSelected());
            testModeCheckBox.setEnabled(!secretsCheckBox.isSelected());
            btnSave.setEnabled(!secretsCheckBox.isSelected());
            btnAction.setEnabled(true);

            if (secretsCheckBox.isSelected()) {
                btnAction.setText(ComponentLabels.START);
            } else {
                btnAction.setText(ComponentLabels.PAIR);
                txtSecrets.setText("");
            }

            mainFrame.pack();
        });
        btnAction.addActionListener(e -> {
            switch (btnAction.getText()) {
                case ComponentLabels.START:
                    if (!areControlsValidForSecrets())
                        return;

                    isAppStarted = false;
                    isStartButtonClicked = true;

                    spiSecrets = new Secrets(txtSecrets.getText().split(":")[0].trim(), txtSecrets.getText().split(":")[1].trim());

                    break;
                case ComponentLabels.PAIR:
                    if (!areControlsValid(true))
                        return;

                    try {
                        posId = txtPosId.getText();
                        eftposAddress = txtDeviceAddress.getText();

                        Start();
                        spi.pair();

                        mainFrame.pack();

                    } catch (Exception ex) {
                        LOG.error(ex.getMessage());
                        showMessageDialog(null, ex.getMessage(), "Error", ERROR_MESSAGE);
                    }
                    break;
                case ComponentLabels.UN_PAIR:
                    formMain.secretsCheckBox.setEnabled(false);
                    formMain.txtSecrets.setText("");
                    formMain.testModeCheckBox.setEnabled(true);
                    formMain.btnSave.setEnabled(true);
                    formMain.txtPosId.setEnabled(true);
                    formMain.txtPosId.setText("");
                    formMain.txtSerialNumber.setEnabled(true);
                    cmbTenantsList.setEnabled(true);
                    formMain.txtSerialNumber.setText("");
                    formMain.txtDeviceAddress.setText("");
                    mainFrame.setEnabled(false);
                    isAppStarted = false;
                    isStartButtonClicked = false;
                    spi.unpair();
                    break;
                default:
                    break;
            }
        });
        btnResolveAddress.addActionListener(e -> {
            if (!StringUtils.isWhitespace(txtSerialNumber.getText())) {
                spi.setSerialNumber(txtSerialNumber.getText());
                spi.setTestMode(testModeCheckBox.isSelected());
            } else {
                showMessageDialog(null, "Please enter Serial number", "Validation", INFORMATION_MESSAGE);
            }
        });
        lastTransactions = new ArrayList<>();
        cmbTenantsList.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                String selectedOption = (String) cmbTenantsList.getSelectedItem();
                if (selectedOption.equals(" "))
                    return;
                if (selectedOption.equals("Other")) {
                    lblOther.setVisible(true);
                    txtOtherTenant.setVisible(true);
                } else {
                    lblOther.setVisible(false);
                    txtOtherTenant.setVisible(false);
                    tenantCode = tenantsMap.get(selectedOption);
                }
            }
        });
        lblOther.setVisible(false);
        txtOtherTenant.setVisible(false);
        txtOtherTenant.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                super.keyReleased(e);
                tenantCode = txtOtherTenant.getText();
            }
        });

        cmbTenantsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                cmbTenantsList.removeItem(" ");
            }
        });
    }

    public static void main(String[] args) throws ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException, IllegalAccessException {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        SwingUtilities.invokeLater(() -> {
            formMain = new FormMain();
            mainFrame = new JFrame("Ramen Pos");
            mainFrame.setContentPane(formMain.pnlMain);
            mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            mainFrame.setResizable(false);
            mainFrame.addWindowListener(formMain);
            mainFrame.pack();
            mainFrame.setVisible(true);

            formTransactions = new FormTransactions();
            transactionsFrame = new JFrame("Transactions");
            transactionsFrame.setContentPane(formTransactions.pnlMain);
            transactionsFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            transactionsFrame.setResizable(false);
            transactionsFrame.addWindowListener(formTransactions);
            transactionsFrame.pack();

            actionDialog = new JDialog();
            actionDialog.setTitle("Actions");
            formAction = new FormAction();
            actionDialog.setContentPane(formAction.pnlMain);
            actionDialog.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            actionDialog.setResizable(false);
            actionDialog.addWindowListener(formAction);
            actionDialog.pack();
        });
    }

    public static <T> void writeToBinaryFile(String filePath, T objectToWrite, boolean append) {
        try {
            FileOutputStream fos = new FileOutputStream(filePath, append);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(objectToWrite);
            oos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveSecrets() {
        secretsFile.put("PosId", posId);
        secretsFile.put("EftposAddress", eftposAddress);
        secretsFile.put("SerialNumber", serialNumber);
        secretsFile.put("TestMode", String.valueOf(testModeCheckBox.isSelected()));
        secretsFile.put("TenantCode", tenantCode);
        secretsFile.put("Secrets", spiSecrets.getEncKey() + ":" + spiSecrets.getHmacKey());
        writeToBinaryFile("Secrets.bin", secretsFile, false);
    }

    private static <T> T readFromBinaryFile(String filePath) {
        try {
            FileInputStream fos = new FileInputStream(filePath);
            ObjectInputStream ois = new ObjectInputStream(fos);
            //noinspection unchecked
            T object = (T) ois.readObject();
            ois.close();
            return object;
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean areControlsValid(boolean isPairing) {
        posId = txtPosId.getText();
        eftposAddress = txtDeviceAddress.getText();
        serialNumber = txtSerialNumber.getText();

        if (isPairing && (eftposAddress == null || StringUtils.isWhitespace(eftposAddress))) {
            showMessageDialog(null, "Please enable auto address resolution or enter a device address", "Error", ERROR_MESSAGE);
            return false;
        }

        if (posId == null || StringUtils.isWhitespace(posId)) {
            showMessageDialog(null, "Please provide a Pos Id", "Error", ERROR_MESSAGE);
            return false;
        }

        if (!isPairing && (serialNumber == null || StringUtils.isWhitespace(serialNumber))) {
            showMessageDialog(null, "Please provide a Serial Number", "Error", ERROR_MESSAGE);
            return false;
        }

        return true;
    }

    private boolean areControlsValidForSecrets() {
        posId = txtPosId.getText();
        eftposAddress = txtDeviceAddress.getText();
        serialNumber = txtSerialNumber.getText();

        if (eftposAddress == null || StringUtils.isWhitespace(eftposAddress)) {
            showMessageDialog(null, "Please provide a Eftpos address", "Error", ERROR_MESSAGE);
            return false;
        }

        if (posId == null || StringUtils.isWhitespace(posId)) {
            showMessageDialog(null, "Please provide a Pos Id", "Error", ERROR_MESSAGE);
            return false;
        }

        if (txtSecrets.getText() == null || StringUtils.isWhitespace(txtSecrets.getText())) {
            showMessageDialog(null, "Please provide a Secrets", "Error", ERROR_MESSAGE);
            return false;
        }

        if (txtSecrets.getText().split(":").length < 2) {
            showMessageDialog(null, "Please provide a valid Secrets", "Error", ERROR_MESSAGE);
            return false;
        }

        return true;
    }

    private void Start() {
        LOG.info("Starting RamenPos...");

        try {
            // This is how you instantiate SPI while checking for JDK compatibility.
            // It is ok to not have the secrets yet to start with.
//            if (!StringUtils.isWhitespace(serialNumber)) {
            spi = new Spi(posId, serialNumber, eftposAddress, spiSecrets);
//            } else {
//                spi = new Spi(posId, eftposAddress, spiSecrets);
//            }
        } catch (Spi.CompatibilityException ex) {
            LOG.error("# ");
            LOG.error("# Compatibility check failed: " + ex.getCause().getMessage());
            LOG.error("# Please ensure you followed all the configuration steps on your machine.");
            LOG.error("# ");

            showMessageDialog(null, ex.getMessage(), "Error", ERROR_MESSAGE);
            return;
        } catch (Exception ex) {
            LOG.error(ex.getMessage());
            showMessageDialog(null, ex.getMessage(), "Error", ERROR_MESSAGE);
        }

        spi.setPosInfo("assembly", "2.6.3");
        options = new TransactionOptions();

        spi.setDeviceAddressChangedHandler(this::onDeviceAddressChanged);
        spi.setStatusChangedHandler(this::onSpiStatusChanged);
        spi.setPairingFlowStateChangedHandler(this::onPairingFlowStateChanged);
        spi.setSecretsChangedHandler(this::onSecretsChanged);
        spi.setTxFlowStateChangedHandler(this::onTxFlowStateChanged);

        spi.setPrintingResponseDelegate(this::handlePrintingResponse);
        spi.setTerminalStatusResponseDelegate(this::handleTerminalStatusResponse);
        spi.setTerminalConfigurationResponseDelegate(this::handleTerminalConfigurationResponse);
        spi.setBatteryLevelChangedDelegate(this::handleBatteryLevelChanged);

        spi.setAcquirerCode(tenantCode);
        spi.setDeviceApiKey(apiKey);
        spi.setTestMode(testModeCheckBox.isSelected());

        try {
            spi.start();
        } catch (Exception ex) {
            LOG.error(ex.getMessage());
            showMessageDialog(null, ex.getMessage(), "Error", ERROR_MESSAGE);
        }

        if (!isAppStarted || spi.getCurrentStatus() != SpiStatus.UNPAIRED) {
            SwingUtilities.invokeLater(this::printStatusAndActions);
        }
    }

    private void onDeviceAddressChanged(DeviceAddressStatus deviceAddressStatus) {
        SwingUtilities.invokeLater(() -> {
            btnAction.setEnabled(false);
            if (deviceAddressStatus != null) {
                eftposAddress = deviceAddressStatus.getAddress() + (tenantCode.equals("gko") ? ":8080" : "");
                switch (spi.getCurrentStatus()) {
                    case UNPAIRED:
                        switch (deviceAddressStatus.getDeviceAddressResponseCode()) {
                            case SUCCESS:
                                txtDeviceAddress.setText(eftposAddress);
                                btnAction.setEnabled(true);

                                if (isStartButtonClicked) {
                                    isStartButtonClicked = false;
                                    Start();
                                } else {
                                    showMessageDialog(null, "Device Address has been updated to " + deviceAddressStatus.getAddress(), "Info : Device Address Updated", INFORMATION_MESSAGE);
                                }
                                break;
                            case INVALID_SERIAL_NUMBER:
                                txtDeviceAddress.setText("");
                                showMessageDialog(null, "The serial number is invalid!", "Error : Device Address Not Updated", ERROR_MESSAGE);
                                break;
                            case DEVICE_SERVICE_ERROR:
                                txtDeviceAddress.setText("");
                                showMessageDialog(null, "Device service is down!", "Error : Device Address Not Updated", ERROR_MESSAGE);
                                break;
                            case ADDRESS_NOT_CHANGED:
                                btnAction.setEnabled(true);
                                showMessageDialog(null, "The IP address have not changed!", "Error : Device Address Not Updated", ERROR_MESSAGE);
                                break;
                            case SERIAL_NUMBER_NOT_CHANGED:
                                btnAction.setEnabled(true);
                                showMessageDialog(null, "The Serial Number have not changed!", "Error : Device Address Not Updated", ERROR_MESSAGE);
                                break;
                            default:
                                showMessageDialog(null, "The IP address have not changed or The serial number is invalid!", "Error : Device Address Not Updated", ERROR_MESSAGE);
                                break;
                        }
                        break;
                    case PAIRED_CONNECTING:
                        txtDeviceAddress.setText(eftposAddress);
                        break;
                    case PAIRED_CONNECTED:
                        //For later use
                        break;
                }
            }

        });

    }

    private void onTxFlowStateChanged(TransactionFlowState txState) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                printStatusAndActions();
            }
        });
    }

    private void onPairingFlowStateChanged(PairingFlowState pairingFlowState) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                formAction.lblFlowMessage.setText(formMain.multilineHtml + pairingFlowState.getMessage().trim());

                if (!pairingFlowState.getConfirmationCode().equals("")) {
                    formAction.lblFlowMessage.setText(formMain.multilineHtml + pairingFlowState.getMessage().trim() + " " + "# Confirmation Code: " + pairingFlowState.getConfirmationCode().trim());
                }

                printStatusAndActions();
            }
        });
    }

    private void onSecretsChanged(Secrets secrets) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                spiSecrets = secrets;
                formTransactions.btnSecrets.doClick();
                printStatusAndActions();
            }
        });
    }

    private void onSpiStatusChanged(SpiStatus status) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                formAction.lblFlowMessage.setText("It's trying to connect");
                LOG.info("# --> SPI Status Changed: " + status);
                printStatusAndActions();
            }
        });
    }

    private void handlePrintingResponse(Message message) {
        formAction.txtAreaFlow.setText("");
        PrintingResponse printingResponse = new PrintingResponse(message);

        if (printingResponse.isSuccess()) {
            formAction.lblFlowMessage.setText("# --> Printing Response: Printing Receipt successful");
        } else {
            formAction.lblFlowMessage.setText("# --> Printing Response:  Printing Receipt failed: reason = " + printingResponse.getErrorReason() + ", detail = " + printingResponse.getErrorDetail());
        }

        spi.ackFlowEndedAndBackToIdle();
        getOKActionComponents();
        transactionsFrame.setEnabled(false);
        actionDialog.setVisible(true);
        actionDialog.pack();
        transactionsFrame.pack();
    }

    private void handleTerminalStatusResponse(Message message) {
        formAction.txtAreaFlow.setText("");
        formAction.lblFlowMessage.setText("# --> Terminal Status Response Successful");
        TerminalStatusResponse terminalStatusResponse = new TerminalStatusResponse(message);
        formAction.txtAreaFlow.append("# Terminal Status Response #" + "\n");
        formAction.txtAreaFlow.append("# Status: " + terminalStatusResponse.getStatus() + "\n");
        formAction.txtAreaFlow.append("# Battery Level: " + terminalStatusResponse.getBatteryLevel().replace("d", "") + "%" + "\n");
        formAction.txtAreaFlow.append("# Charging: " + terminalStatusResponse.isCharging() + "\n");
        spi.ackFlowEndedAndBackToIdle();
        getOKActionComponents();
        transactionsFrame.setEnabled(false);
        actionDialog.setVisible(true);
        actionDialog.pack();
        transactionsFrame.pack();
    }

    private void handleTerminalConfigurationResponse(Message message) {
        formAction.txtAreaFlow.setText("");
        formAction.lblFlowMessage.setText("# --> Terminal Configuration Response Successful");
        TerminalConfigurationResponse terminalConfigurationResponse = new TerminalConfigurationResponse(message);
        txtSerialNumber.setText(serialNumber);
        formAction.txtAreaFlow.append("# Terminal Configuration Response #" + "\n");
        formAction.txtAreaFlow.append("# Comms Selected: " + terminalConfigurationResponse.getCommsSelected() + "\n");
        formAction.txtAreaFlow.append("# Merchant Id: " + terminalConfigurationResponse.getMerchantId() + "\n");
        formAction.txtAreaFlow.append("# PA Version: " + terminalConfigurationResponse.getPAVersion() + "\n");
        formAction.txtAreaFlow.append("# Payment Interface Version: " + terminalConfigurationResponse.getPaymentInterfaceVersion() + "\n");
        formAction.txtAreaFlow.append("# Plugin Version: " + terminalConfigurationResponse.getPluginVersion() + "\n");
        formAction.txtAreaFlow.append("# Serial Number: " + terminalConfigurationResponse.getSerialNumber() + "\n");
        formAction.txtAreaFlow.append("# Terminal Id: " + terminalConfigurationResponse.getTerminalId() + "\n");
        formAction.txtAreaFlow.append("# Terminal Model: " + terminalConfigurationResponse.getTerminalModel() + "\n");

        spi.ackFlowEndedAndBackToIdle();
        getOKActionComponents();
        transactionsFrame.setEnabled(false);
        actionDialog.setVisible(true);
        actionDialog.pack();
        transactionsFrame.pack();
    }

    private void handleBatteryLevelChanged(Message message) {
        if (!actionDialog.isVisible()) {
            formAction.lblFlowMessage.setText("# --> Battery Level Changed Successful");
            TerminalBattery terminalBattery = new TerminalBattery(message);
            formAction.txtAreaFlow.setText("");
            formAction.txtAreaFlow.append("# Battery Level Changed #" + "\n");
            formAction.txtAreaFlow.append("# Battery Level: " + terminalBattery.batteryLevel.replace("d", "") + "%" + "\n");

            spi.ackFlowEndedAndBackToIdle();
            transactionsFrame.setEnabled(false);
            actionDialog.setVisible(true);
            actionDialog.pack();
            transactionsFrame.pack();
        }
    }

    void printStatusAndActions() {
        printFlowInfo();
        printActions();
        printPairingStatus();

        mainFrame.pack();
        actionDialog.toFront();
        actionDialog.pack();
        actionDialog.setVisible(true);
        transactionsFrame.pack();
        transactionsFrame.setEnabled(false);
    }

    private void printActions() {
        formTransactions.lblFlowStatus.setText(spi.getCurrentStatus() + ":" + spi.getCurrentFlow());

        switch (spi.getCurrentStatus()) {
            case UNPAIRED:
                switch (spi.getCurrentFlow()) {
                    case IDLE:
                        formAction.lblFlowMessage.setText("Unpaired");
                        formAction.btnAction1.setEnabled(true);
                        formAction.btnAction1.setVisible(true);
                        formAction.btnAction1.setText(ComponentLabels.OK_UNPAIRED);
                        formAction.btnAction2.setVisible(false);
                        formAction.btnAction3.setVisible(false);
                        btnTransactions.setVisible(false);
                        try {
                            Files.deleteIfExists(Paths.get("Secrets.bin"));
                            Files.deleteIfExists(Paths.get(transactionsFile));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        getUnvisibleActionComponents();
                        break;

                    case PAIRING:
                        if (spi.getCurrentPairingFlowState().isAwaitingCheckFromPos()) {
                            formAction.btnAction1.setEnabled(false);
                            formAction.btnAction1.setVisible(false);
                            formAction.btnAction1.setText(ComponentLabels.CONFIRM_CODE);
                            formAction.btnAction2.setVisible(true);
                            formAction.btnAction2.setText(ComponentLabels.CANCEL_PAIRING);
                            formAction.btnAction3.setVisible(false);
                            getUnvisibleActionComponents();
                            break;
                        } else if (!spi.getCurrentPairingFlowState().isFinished()) {
                            formAction.btnAction1.setVisible(true);
                            formAction.btnAction1.setText(ComponentLabels.CANCEL_PAIRING);
                            formAction.btnAction2.setVisible(false);
                            formAction.btnAction3.setVisible(false);
                            getUnvisibleActionComponents();
                            break;
                        } else {
                            getOKActionComponents();
                            break;
                        }
                    case TRANSACTION:
                        formAction.lblFlowMessage.setText("Unpaired");
                        formAction.btnAction1.setEnabled(true);
                        formAction.btnAction1.setVisible(true);
                        formAction.btnAction1.setText(ComponentLabels.OK_UNPAIRED);
                        formAction.btnAction2.setVisible(false);
                        formAction.btnAction3.setVisible(false);
                        btnTransactions.setVisible(false);
                        getUnvisibleActionComponents();
                        break;

                    default:
                        getOKActionComponents();
                        formAction.txtAreaFlow.append("# .. Unexpected Flow .. " + spi.getCurrentFlow() + "\n");
                        break;
                }
                break;

            case PAIRED_CONNECTING:
                switch (spi.getCurrentFlow()) {
                    case IDLE:
                        btnAction.setText(ComponentLabels.UN_PAIR);
                        formAction.lblFlowMessage.setText("# --> SPI Status Changed: " +
                                spi.getCurrentStatus());
                        getOKActionComponents();
                        break;

                    case TRANSACTION:
                        if (spi.getCurrentTxFlowState().isAwaitingSignatureCheck()) {
                            formAction.btnAction1.setEnabled(true);
                            formAction.btnAction1.setVisible(true);
                            formAction.btnAction1.setText(ComponentLabels.ACCEPT_SIGNATURE);
                            formAction.btnAction2.setVisible(true);
                            formAction.btnAction2.setText(ComponentLabels.DECLINE_SIGNATURE);
                            formAction.btnAction3.setVisible(true);
                            formAction.btnAction3.setText(ComponentLabels.CANCEL);
                            getUnvisibleActionComponents();
                            break;
                        } else if (!spi.getCurrentTxFlowState().isFinished()) {
                            if (spi.getCurrentTxFlowState().getType() != TransactionType.SETTLEMENT_ENQUIRY) {
                                formAction.btnAction1.setText(ComponentLabels.CANCEL);
                                formAction.btnAction1.setVisible(true);
                            } else {
                                formAction.btnAction1.setVisible(false);
                            }
                            formAction.btnAction2.setVisible(false);
                            formAction.btnAction3.setVisible(false);
                            getUnvisibleActionComponents();
                            break;
                        } else {
                            switch (spi.getCurrentTxFlowState().getSuccess()) {
                                case SUCCESS:
                                    getOKActionComponents();
                                    break;
                                case FAILED:
                                    if (spi.getCurrentTxFlowState().getType() != TransactionType.SETTLEMENT_ENQUIRY) {
                                        formAction.btnAction1.setEnabled(true);
                                        formAction.btnAction1.setVisible(true);
                                        formAction.btnAction1.setText(ComponentLabels.RETRY);
                                        formAction.btnAction2.setVisible(true);
                                        formAction.btnAction2.setText(ComponentLabels.CANCEL);
                                    } else {
                                        formAction.btnAction1.setEnabled(true);
                                        formAction.btnAction1.setVisible(true);
                                        formAction.btnAction1.setText(ComponentLabels.OK);
                                        formAction.btnAction2.setVisible(false);
                                    }
                                    formAction.btnAction3.setVisible(false);
                                    getUnvisibleActionComponents();
                                    break;
                                case UNKNOWN:
                                    getOKActionComponents();
                                    formAction.txtAreaFlow.append("# .. Unexpected Flow .. " + spi.getCurrentFlow() + "\n");
                                    break;
                                default:
                                    break;
                            }
                            break;
                        }

                    case PAIRING:
                        getOKActionComponents();
                        break;

                    default:
                        getOKActionComponents();
                        formAction.txtAreaFlow.setText("# .. Unexpected Flow .. " + spi.getCurrentFlow() + "\n");
                        break;
                }
                break;

            case PAIRED_CONNECTED:
                switch (spi.getCurrentFlow()) {
                    case IDLE:
                        formMain.saveSecrets();
                        btnAction.setText(ComponentLabels.UN_PAIR);
                        formAction.lblFlowMessage.setText("# --> SPI Status Changed: " +
                                spi.getCurrentStatus());
                        mainFrame.setVisible(false);
                        transactionsFrame.setVisible(true);
                        getOKActionComponents();
                        break;

                    case PAIRING:
                        getOKActionComponents();
                        break;

                    case TRANSACTION:
                        if (spi.getCurrentTxFlowState().isAwaitingSignatureCheck()) {
                            formAction.btnAction1.setEnabled(true);
                            formAction.btnAction1.setVisible(true);
                            formAction.btnAction1.setText(ComponentLabels.ACCEPT_SIGNATURE);
                            formAction.btnAction2.setVisible(true);
                            formAction.btnAction2.setText(ComponentLabels.DECLINE_SIGNATURE);
                            formAction.btnAction3.setVisible(true);
                            formAction.btnAction3.setText(ComponentLabels.CANCEL);
                            getUnvisibleActionComponents();
                            break;
                        } else if (!spi.getCurrentTxFlowState().isFinished()) {
                            if (spi.getCurrentTxFlowState().getType() != TransactionType.SETTLEMENT_ENQUIRY) {
                                formAction.btnAction1.setText(ComponentLabels.CANCEL);
                                formAction.btnAction1.setVisible(true);
                            } else {
                                formAction.btnAction1.setVisible(false);
                            }
                            formAction.btnAction2.setVisible(false);
                            formAction.btnAction3.setVisible(false);
                            getUnvisibleActionComponents();
                            break;
                        } else {
                            switch (spi.getCurrentTxFlowState().getSuccess()) {
                                case SUCCESS:
                                    getOKActionComponents();
                                    break;
                                case FAILED:
                                    if (spi.getCurrentTxFlowState().getType() != TransactionType.SETTLEMENT_ENQUIRY) {
                                        formAction.btnAction1.setEnabled(true);
                                        formAction.btnAction1.setVisible(true);
                                        formAction.btnAction1.setText(ComponentLabels.RETRY);
                                        formAction.btnAction2.setVisible(true);
                                        formAction.btnAction2.setText(ComponentLabels.CANCEL);
                                    } else {
                                        formAction.btnAction1.setEnabled(true);
                                        formAction.btnAction1.setVisible(true);
                                        formAction.btnAction1.setText(ComponentLabels.OK);
                                        formAction.btnAction2.setVisible(false);
                                    }
                                    formAction.btnAction3.setVisible(false);
                                    getUnvisibleActionComponents();
                                    break;
                                case UNKNOWN:
                                    getOKActionComponents();
                                    formAction.txtAreaFlow.setText("# .. Unexpected Flow .. " + spi.getCurrentFlow() + "\n");
                                    break;
                                default:
                                    break;
                            }
                            break;
                        }

                    default:
                        getOKActionComponents();
                        formAction.txtAreaFlow.setText("# .. Unexpected Flow .. " + spi.getCurrentFlow() + "\n");
                        break;
                }
                break;

            default:
                getOKActionComponents();
                formAction.txtAreaFlow.setText("# .. Unexpected Flow .. " + spi.getCurrentFlow() + "\n");
                break;
        }
    }

    private void printFlowInfo() {
        formAction.txtAreaFlow.setText("");

        switch (spi.getCurrentFlow()) {
            case PAIRING:
                PairingFlowState pairingState = spi.getCurrentPairingFlowState();
                formAction.lblFlowMessage.setText(pairingState.getMessage());
                formAction.txtAreaFlow.append("### PAIRING PROCESS UPDATE ###" + "\n");
                formAction.txtAreaFlow.append("# " + pairingState.getMessage() + "\n");
                formAction.txtAreaFlow.append("# Finished? " + pairingState.isFinished() + "\n");
                formAction.txtAreaFlow.append("# Successful? " + pairingState.isSuccessful() + "\n");
                formAction.txtAreaFlow.append("# Confirmation code: " + pairingState.getConfirmationCode() + "\n");
                formAction.txtAreaFlow.append("# Waiting confirm from EFTPOS? " + pairingState.isAwaitingCheckFromEftpos() + "\n");
                formAction.txtAreaFlow.append("# Waiting confirm from POS? " + pairingState.isAwaitingCheckFromPos() + "\n");
                break;

            case TRANSACTION:
                TransactionFlowState txState = spi.getCurrentTxFlowState();
                formAction.lblFlowMessage.setText(txState.getDisplayMessage());
                formAction.txtAreaFlow.append("### TX PROCESS UPDATE ###" + "\n");
                formAction.txtAreaFlow.append("# " + txState.getDisplayMessage() + "\n");
                formAction.txtAreaFlow.append("# Id: " + txState.getPosRefId() + "\n");
                formAction.txtAreaFlow.append("# Type: " + txState.getType() + "\n");
                formAction.txtAreaFlow.append("# Amount: " + (txState.getAmountCents() / 100.0) + "\n");
                formAction.txtAreaFlow.append("# Waiting for signature: " + txState.isAwaitingSignatureCheck() + "\n");
                formAction.txtAreaFlow.append("# Attempting to cancel: " + txState.isAttemptingToCancel() + "\n");
                formAction.txtAreaFlow.append("# Finished: " + txState.isFinished() + "\n");
                formAction.txtAreaFlow.append("# Success: " + txState.getSuccess() + "\n");
                formAction.txtAreaFlow.append("# GLT Response PosRefId: " + txState.getGltResponsePosRefId() + "\n");
                formAction.txtAreaFlow.append("# Last GLT Response Request Id: " + txState.getGtRequestId() + "\n");

                if (txState.isAwaitingSignatureCheck()) {
                    // We need to print the receipt for the customer to sign.
                    formAction.txtAreaFlow.append("# RECEIPT TO PRINT FOR SIGNATURE" + "\n");
                    formTransactions.txtAreaReceipt.append(txState.getSignatureRequiredMessage().getMerchantReceipt().trim() + "\n");
                }

                if (txState.isAwaitingPhoneForAuth()) {
                    formAction.txtAreaFlow.append("# PHONE FOR AUTH DETAILS:" + "\n");
                    formAction.txtAreaFlow.append("# CALL: " + txState.getPhoneForAuthRequiredMessage().getPhoneNumber() + "\n");
                    formAction.txtAreaFlow.append("# QUOTE: Merchant ID: " + txState.getPhoneForAuthRequiredMessage().getMerchantId() + "\n");
                }

                if (txState.isFinished()) {
                    formAction.txtAreaFlow.setText("");
                    switch (txState.getType()) {
                        case PURCHASE:
                            handleFinishedPurchase(txState);
                            break;
                        case REFUND:
                            handleFinishedRefund(txState);
                            break;
                        case CASHOUT_ONLY:
                            handleFinishedCashout(txState);
                            break;
                        case MOTO:
                            handleFinishedMoto(txState);
                            break;
                        case SETTLE:
                            handleFinishedSettle(txState);
                            break;
                        case SETTLEMENT_ENQUIRY:
                            handleFinishedSettlementEnquiry(txState);
                            break;
                        case GET_LAST_TRANSACTION:
                            handleFinishedGetLastTransaction(txState);
                            break;
                        case GET_TRANSACTION:
                            handleFinishedGetTransaction(txState);
                            break;
                        default:
                            formAction.txtAreaFlow.append("# CAN'T HANDLE TX TYPE: " + txState.getType() + "\n");
                            break;
                    }
                }
                break;
            case IDLE:
                break;
            default:
                throw new IllegalArgumentException();
        }

        formAction.txtAreaFlow.append("# --------------- STATUS ------------------" + "\n");
        formAction.txtAreaFlow.append("# " + posId + " <-> Eftpos: " + eftposAddress + " #" + "\n");
        formAction.txtAreaFlow.append("# SPI STATUS: " + spi.getCurrentStatus() + "     FLOW:" + spi.getCurrentFlow() + " #" + "\n");
        formAction.txtAreaFlow.append("# -----------------------------------------" + "\n");
        formAction.txtAreaFlow.append("# POS: v" + getVersion() + " Spi: v" + getVersion() + "\n");
    }

    private void handleFinishedPurchase(TransactionFlowState txState) {
        PurchaseResponse purchaseResponse;
        switch (txState.getSuccess()) {
            case SUCCESS:
                formAction.txtAreaFlow.append("# WOOHOO - WE GOT PAID!" + "\n");
                purchaseResponse = new PurchaseResponse(txState.getResponse());
                formAction.txtAreaFlow.append("# Response: " + purchaseResponse.getResponseText() + "\n");
                formAction.txtAreaFlow.append("# RRN: " + purchaseResponse.getRRN() + "\n");
                formAction.txtAreaFlow.append("# Scheme: " + purchaseResponse.getSchemeName() + "\n");
                formAction.txtAreaFlow.append("# Customer receipt:" + "\n");
                formTransactions.txtAreaReceipt.append(!purchaseResponse.wasCustomerReceiptPrinted() ? purchaseResponse.getCustomerReceipt().trim() : "# PRINTED FROM EFTPOS" + "\n");

                formAction.txtAreaFlow.append("# PURCHASE: $" + purchaseResponse.getPurchaseAmount() / 100.0 + "\n");
                formAction.txtAreaFlow.append("# TIP: $" + purchaseResponse.getTipAmount() / 100.0 + "\n");
                formAction.txtAreaFlow.append("# CASHOUT: $" + purchaseResponse.getCashoutAmount() / 100.0 + "\n");
                formAction.txtAreaFlow.append("# BANKED NON-CASH AMOUNT: $" + purchaseResponse.getBankNonCashAmount() / 100.0 + "\n");
                formAction.txtAreaFlow.append("# BANKED CASH AMOUNT: $" + purchaseResponse.getBankCashAmount() / 100.0 + "\n");
                formAction.txtAreaFlow.append("# SURCHARGE AMOUNT: $" + purchaseResponse.getSurchargeAmount() / 100.0 + "\n");
                persistLastTransaction(purchaseResponse.getPosRefId());
                break;
            case FAILED:
                formAction.txtAreaFlow.append("# WE DID NOT GET PAID :(" + "\n");
                if (txState.getResponse() != null) {
                    formAction.txtAreaFlow.append("# Error: " + txState.getResponse().getError() + "\n");
                    formAction.txtAreaFlow.append("# Error Detail: " + txState.getResponse().getErrorDetail() + "\n");
                    purchaseResponse = new PurchaseResponse(txState.getResponse());
                    formAction.txtAreaFlow.append("# Response: " + purchaseResponse.getResponseText() + "\n");
                    formAction.txtAreaFlow.append("# RRN: " + purchaseResponse.getRRN() + "\n");
                    formAction.txtAreaFlow.append("# Scheme: " + purchaseResponse.getSchemeName() + "\n");
                    formAction.txtAreaFlow.append("# Customer receipt:" + "\n");
                    formTransactions.txtAreaReceipt.append(!purchaseResponse.wasCustomerReceiptPrinted()
                            ? purchaseResponse.getCustomerReceipt().trim()
                            : "# PRINTED FROM EFTPOS" + "\n");
                }
                break;
            case UNKNOWN:
                formAction.txtAreaFlow.append("# WE'RE NOT QUITE SURE WHETHER WE GOT PAID OR NOT :/" + "\n");
                formAction.txtAreaFlow.append("# CHECK THE LAST TRANSACTION ON THE EFTPOS ITSELF FROM THE APPROPRIATE MENU ITEM." + "\n");
                formAction.txtAreaFlow.append("# IF YOU CONFIRM THAT THE CUSTOMER PAID, CLOSE THE ORDER." + "\n");
                formAction.txtAreaFlow.append("# OTHERWISE, RETRY THE PAYMENT FROM SCRATCH." + "\n");
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private void handleFinishedRefund(TransactionFlowState txState) {
        RefundResponse refundResponse;
        switch (txState.getSuccess()) {
            case SUCCESS:
                formAction.txtAreaFlow.append("# REFUND GIVEN- OH WELL!" + "\n");
                refundResponse = new RefundResponse(txState.getResponse());
                formAction.txtAreaFlow.append("# Response: " + refundResponse.getResponseText() + "\n");
                formAction.txtAreaFlow.append("# RRN: " + refundResponse.getRRN() + "\n");
                formAction.txtAreaFlow.append("# Scheme: " + refundResponse.getSchemeName() + "\n");
                formAction.txtAreaFlow.append("# Customer receipt:" + "\n");
                formTransactions.txtAreaReceipt.append(!refundResponse.wasCustomerReceiptPrinted() ? refundResponse.getCustomerReceipt().trim() : "# PRINTED FROM EFTPOS" + "\n");
                formAction.txtAreaFlow.append("# REFUNDED AMOUNT: $" + refundResponse.getRefundAmount() / 100.0 + "\n");
                break;
            case FAILED:
                formAction.txtAreaFlow.append("# REFUND FAILED!" + "\n");
                if (txState.getResponse() != null) {
                    refundResponse = new RefundResponse(txState.getResponse());
                    formAction.txtAreaFlow.append("# Error: " + txState.getResponse().getError() + "\n");
                    formAction.txtAreaFlow.append("# Error Detail: " + txState.getResponse().getErrorDetail() + "\n");
                    formAction.txtAreaFlow.append("# Response: " + refundResponse.getResponseText() + "\n");
                    formAction.txtAreaFlow.append("# RRN: " + refundResponse.getRRN() + "\n");
                    formAction.txtAreaFlow.append("# Scheme: " + refundResponse.getSchemeName() + "\n");
                    formAction.txtAreaFlow.append("# Customer receipt:" + "\n");
                    formTransactions.txtAreaReceipt.append(!refundResponse.wasCustomerReceiptPrinted() ? refundResponse.getCustomerReceipt().trim() : "# PRINTED FROM EFTPOS" + "\n");
                }
                break;
            case UNKNOWN:
                formAction.txtAreaFlow.append("# WE'RE NOT QUITE SURE WHETHER THE REFUND WENT THROUGH OR NOT :/" + "\n");
                formAction.txtAreaFlow.append("# CHECK THE LAST TRANSACTION ON THE EFTPOS ITSELF FROM THE APPROPRIATE MENU ITEM." + "\n");
                formAction.txtAreaFlow.append("# YOU CAN THE TAKE THE APPROPRIATE ACTION." + "\n");
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private void handleFinishedCashout(TransactionFlowState txState) {
        CashoutOnlyResponse cashoutResponse;
        switch (txState.getSuccess()) {
            case SUCCESS:
                formAction.txtAreaFlow.append("# CASH-OUT SUCCESSFUL - HAND THEM THE CASH!" + "\n");
                cashoutResponse = new CashoutOnlyResponse(txState.getResponse());
                formAction.txtAreaFlow.append("# Response: " + cashoutResponse.getResponseText() + "\n");
                formAction.txtAreaFlow.append("# RRN: " + cashoutResponse.getRRN() + "\n");
                formAction.txtAreaFlow.append("# Scheme: " + cashoutResponse.getSchemeName() + "\n");
                formAction.txtAreaFlow.append("# Customer receipt:" + "\n");
                formTransactions.txtAreaReceipt.append(!cashoutResponse.wasCustomerReceiptPrinted() ? cashoutResponse.getCustomerReceipt().trim() : "# PRINTED FROM EFTPOS" + "\n");
                formAction.txtAreaFlow.append("# CASHOUT: $" + cashoutResponse.getCashoutAmount() / 100.0 + "\n");
                formAction.txtAreaFlow.append("# BANKED NON-CASH AMOUNT: $" + cashoutResponse.getBankNonCashAmount() / 100.0 + "\n");
                formAction.txtAreaFlow.append("# BANKED CASH AMOUNT: $" + cashoutResponse.getBankCashAmount() / 100.0 + "\n");
                formAction.txtAreaFlow.append("# SURCHARGE AMOUNT: $" + cashoutResponse.getSurchargeAmount() / 100.0 + "\n");
                break;
            case FAILED:
                formAction.txtAreaFlow.append("# CASHOUT FAILED!" + "\n");
                if (txState.getResponse() != null) {
                    formAction.txtAreaFlow.append("# Error: " + txState.getResponse().getError() + "\n");
                    formAction.txtAreaFlow.append("# Error detail: " + txState.getResponse().getErrorDetail() + "\n");
                    cashoutResponse = new CashoutOnlyResponse(txState.getResponse());
                    formAction.txtAreaFlow.append("# Response: " + cashoutResponse.getResponseText() + "\n");
                    formAction.txtAreaFlow.append("# RRN: " + cashoutResponse.getRRN() + "\n");
                    formAction.txtAreaFlow.append("# Scheme: " + cashoutResponse.getSchemeName() + "\n");
                    formAction.txtAreaFlow.append("# Customer receipt:" + "\n");
                    formTransactions.txtAreaReceipt.append(!cashoutResponse.wasCustomerReceiptPrinted() ? cashoutResponse.getCustomerReceipt().trim() : "# PRINTED FROM EFTPOS" + "\n");
                }
                break;
            case UNKNOWN:
                formAction.txtAreaFlow.append("# WE'RE NOT QUITE SURE WHETHER THE CASHOUT WENT THROUGH OR NOT :/" + "\n");
                formAction.txtAreaFlow.append("# CHECK THE LAST TRANSACTION ON THE EFTPOS ITSELF FROM THE APPROPRIATE MENU ITEM." + "\n");
                formAction.txtAreaFlow.append("# YOU CAN THE TAKE THE APPROPRIATE ACTION." + "\n");
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private void handleFinishedMoto(TransactionFlowState txState) {
        MotoPurchaseResponse motoResponse;
        PurchaseResponse purchaseResponse;
        switch (txState.getSuccess()) {
            case SUCCESS:
                formAction.txtAreaFlow.append("# WOOHOO - WE GOT MOTO-PAID!" + "\n");
                motoResponse = new MotoPurchaseResponse(txState.getResponse());
                purchaseResponse = motoResponse.getPurchaseResponse();
                formAction.txtAreaFlow.append("# Response: " + purchaseResponse.getResponseText() + "\n");
                formAction.txtAreaFlow.append("# RRN: " + purchaseResponse.getRRN() + "\n");
                formAction.txtAreaFlow.append("# Scheme: " + purchaseResponse.getSchemeName() + "\n");
                formAction.txtAreaFlow.append("# Card entry: " + purchaseResponse.getCardEntry() + "\n");
                formAction.txtAreaFlow.append("# Customer receipt:" + "\n");
                formTransactions.txtAreaReceipt.append(!purchaseResponse.wasCustomerReceiptPrinted() ? purchaseResponse.getCustomerReceipt().trim() : "# PRINTED FROM EFTPOS" + "\n");
                formAction.txtAreaFlow.append("# PURCHASE: $" + purchaseResponse.getPurchaseAmount() / 100.0 + "\n");
                formAction.txtAreaFlow.append("# BANKED NON-CASH AMOUNT: $" + purchaseResponse.getBankNonCashAmount() / 100.0 + "\n");
                formAction.txtAreaFlow.append("# BANKED CASH AMOUNT: $" + purchaseResponse.getBankCashAmount() / 100.0 + "\n");
                formAction.txtAreaFlow.append("# BANKED SURCHARGE AMOUNT: $" + purchaseResponse.getSurchargeAmount() / 100.0 + "\n");
                break;
            case FAILED:
                formAction.txtAreaFlow.append("# WE DID NOT GET MOTO-PAID :(" + "\n");
                if (txState.getResponse() != null) {
                    formAction.txtAreaFlow.append("# Error: " + txState.getResponse().getError() + "\n");
                    formAction.txtAreaFlow.append("# Error detail: " + txState.getResponse().getErrorDetail() + "\n");
                    motoResponse = new MotoPurchaseResponse(txState.getResponse());
                    purchaseResponse = motoResponse.getPurchaseResponse();
                    formAction.txtAreaFlow.append("# Response: " + purchaseResponse.getResponseText() + "\n");
                    formAction.txtAreaFlow.append("# RRN: " + purchaseResponse.getRRN() + "\n");
                    formAction.txtAreaFlow.append("# Scheme: " + purchaseResponse.getSchemeName() + "\n");
                    formAction.txtAreaFlow.append("# Customer receipt:" + "\n");
                    formTransactions.txtAreaReceipt.append(!purchaseResponse.wasCustomerReceiptPrinted() ? purchaseResponse.getCustomerReceipt().trim() : "# PRINTED FROM EFTPOS" + "\n");
                }
                break;
            case UNKNOWN:
                formAction.txtAreaFlow.append("# WE'RE NOT QUITE SURE WHETHER THE MOTO WENT THROUGH OR NOT :/" + "\n");
                formAction.txtAreaFlow.append("# CHECK THE LAST TRANSACTION ON THE EFTPOS ITSELF FROM THE APPROPRIATE MENU ITEM." + "\n");
                formAction.txtAreaFlow.append("# YOU CAN THE TAKE THE APPROPRIATE ACTION." + "\n");
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private void handleFinishedGetLastTransaction(TransactionFlowState txState) {
        if (txState.getResponse() != null) {
            GetLastTransactionResponse gltResponse = new GetLastTransactionResponse(txState.getResponse());

            PurchaseResponse purchaseResponse = new PurchaseResponse(txState.getResponse());
            formAction.txtAreaFlow.append("# Scheme: " + purchaseResponse.getSchemeName() + "\n");
            formAction.txtAreaFlow.append("# Response: " + purchaseResponse.getResponseText() + "\n");
            formAction.txtAreaFlow.append("# RRN: " + purchaseResponse.getRRN() + "\n");
            formAction.txtAreaFlow.append("# Error: " + txState.getResponse().getError() + "\n");
            formAction.txtAreaFlow.append("# Customer receipt:" + "\n");
            formTransactions.txtAreaReceipt.append(purchaseResponse.getCustomerReceipt().trim() + "\n");
        } else {
            // We did not even get a response, like in the case of a time-out.
            formAction.txtAreaFlow.append("# Could not retrieve last transaction." + "\n");
        }
    }

    private void handleFinishedGetTransaction(TransactionFlowState txState) {
        if (txState.getResponse() != null) {
            GetTransactionResponse gtResponse = new GetTransactionResponse(txState.getResponse());

            formAction.txtAreaFlow.append("# Got Successful Get Transaction Response!!! :)" + "\n");
            formAction.txtAreaFlow.append("# Response Message: " + gtResponse.getTxMessage() + "\n");
            formAction.txtAreaFlow.append("# PosRefID: " + gtResponse.getPosRefId() + "\n");

            PurchaseResponse purchaseResponse = new PurchaseResponse(txState.getResponse());
            formAction.txtAreaFlow.append("# Scheme: " + purchaseResponse.getSchemeName() + "\n");
            formAction.txtAreaFlow.append("# Response: " + purchaseResponse.getResponseText() + "\n");
            formAction.txtAreaFlow.append("# RRN: " + purchaseResponse.getRRN() + "\n");
            formAction.txtAreaFlow.append("# Error: " + txState.getResponse().getError() + "\n");
            formAction.txtAreaFlow.append("# Customer receipt:" + "\n");
            formTransactions.txtAreaReceipt.append(purchaseResponse.getCustomerReceipt().trim() + "\n");
        } else {
            // We did not even get a response, like in the case of a time-out.
            formAction.txtAreaFlow.append("# Could not retrieve get transaction." + "\n");
        }
    }

    private void handleFinishedSettle(TransactionFlowState txState) {
        switch (txState.getSuccess()) {
            case SUCCESS:
                formAction.txtAreaFlow.append("# SETTLEMENT SUCCESSFUL!");
                if (txState.getResponse() != null) {
                    Settlement settleResponse = new Settlement(txState.getResponse());
                    formAction.txtAreaFlow.append("# Response: " + settleResponse.getResponseText() + "\n");
                    formAction.txtAreaFlow.append("# Merchant receipt:" + "\n");
                    formAction.txtAreaFlow.append(!settleResponse.wasMerchantReceiptPrinted() ? settleResponse.getMerchantReceipt().trim() : "# PRINTED FROM EFTPOS" + "\n");
                    formAction.txtAreaFlow.append("# Period start: " + settleResponse.getPeriodStartTime() + "\n");
                    formAction.txtAreaFlow.append("# Period end: " + settleResponse.getPeriodEndTime() + "\n");
                    formAction.txtAreaFlow.append("# Settlement time: " + settleResponse.getTriggeredTime() + "\n");
                    formAction.txtAreaFlow.append("# Transaction range: " + settleResponse.getTransactionRange() + "\n");
                    formAction.txtAreaFlow.append("# Terminal ID: " + settleResponse.getTerminalId() + "\n");
                    formAction.txtAreaFlow.append("# Total TX count: " + settleResponse.getTotalCount() + "\n");
                    formAction.txtAreaFlow.append("# Total TX value: $" + (settleResponse.getTotalValue() / 100.0) + "\n");
                    formAction.txtAreaFlow.append("# By acquirer TX count: " + settleResponse.getSettleByAcquirerCount() + "\n");
                    formAction.txtAreaFlow.append("# By acquirer TX value: " + (settleResponse.getSettleByAcquirerValue() / 100.0) + "\n");
                    formAction.txtAreaFlow.append("# SCHEME SETTLEMENTS:" + "\n");
                    Iterable<SchemeSettlementEntry> schemes = settleResponse.getSchemeSettlementEntries();
                    for (SchemeSettlementEntry s : schemes) {
                        formTransactions.txtAreaReceipt.append("# " + s + "\n");
                    }
                }
                break;
            case FAILED:
                formAction.txtAreaFlow.append("# SETTLEMENT FAILED!" + "\n");
                if (txState.getResponse() != null) {
                    Settlement settleResponse = new Settlement(txState.getResponse());
                    formAction.txtAreaFlow.append("# Response: " + settleResponse.getResponseText() + "\n");
                    formAction.txtAreaFlow.append("# Error: " + txState.getResponse().getError() + "\n");
                    formAction.txtAreaFlow.append("# Merchant receipt:" + "\n");
                    formTransactions.txtAreaReceipt.append(!settleResponse.wasMerchantReceiptPrinted() ? settleResponse.getMerchantReceipt().trim() : "# PRINTED FROM EFTPOS" + "\n");
                }
                break;
            case UNKNOWN:
                formAction.txtAreaFlow.append("# SETTLEMENT ENQUIRY RESULT UNKNOWN!" + "\n");
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private void handleFinishedSettlementEnquiry(TransactionFlowState txState) {
        switch (txState.getSuccess()) {
            case SUCCESS:
                formAction.txtAreaFlow.append("# SETTLEMENT ENQUIRY SUCCESSFUL!" + "\n");
                if (txState.getResponse() != null) {
                    Settlement settleResponse = new Settlement(txState.getResponse());
                    formAction.txtAreaFlow.append("# Response: " + settleResponse.getResponseText() + "\n");
                    formAction.txtAreaFlow.append("# Merchant receipt:" + "\n");
                    formAction.txtAreaFlow.append(!settleResponse.wasMerchantReceiptPrinted() ? settleResponse.getMerchantReceipt().trim() : "# PRINTED FROM EFTPOS" + "\n");
                    formAction.txtAreaFlow.append("# Period start: " + settleResponse.getPeriodStartTime() + "\n");
                    formAction.txtAreaFlow.append("# Period end: " + settleResponse.getPeriodEndTime() + "\n");
                    formAction.txtAreaFlow.append("# Settlement time: " + settleResponse.getTriggeredTime() + "\n");
                    formAction.txtAreaFlow.append("# Transaction range: " + settleResponse.getTransactionRange() + "\n");
                    formAction.txtAreaFlow.append("# Terminal ID: " + settleResponse.getTerminalId() + "\n");
                    formAction.txtAreaFlow.append("# Total TX count: " + settleResponse.getTotalCount() + "\n");
                    formAction.txtAreaFlow.append("# Total TX value: " + (settleResponse.getTotalValue() / 100.0) + "\n");
                    formAction.txtAreaFlow.append("# By acquirer TX count: " + (settleResponse.getSettleByAcquirerCount()) + "\n");
                    formAction.txtAreaFlow.append("# By acquirer TX value: " + (settleResponse.getSettleByAcquirerValue() / 100.0) + "\n");
                    formAction.txtAreaFlow.append("# SCHEME SETTLEMENTS:" + "\n");
                    Iterable<SchemeSettlementEntry> schemes = settleResponse.getSchemeSettlementEntries();
                    for (SchemeSettlementEntry s : schemes) {
                        formTransactions.txtAreaReceipt.append("# " + s + "\n");
                    }
                }
                break;
            case FAILED:
                formAction.txtAreaFlow.append("# SETTLEMENT ENQUIRY FAILED!" + "\n");
                if (txState.getResponse() != null) {
                    Settlement settleResponse = new Settlement(txState.getResponse());
                    formAction.txtAreaFlow.append("# Response: " + settleResponse.getResponseText() + "\n");
                    formAction.txtAreaFlow.append("# Error: " + txState.getResponse().getError() + "\n");
                    formAction.txtAreaFlow.append("# Merchant receipt:" + "\n");
                    formTransactions.txtAreaReceipt.append(!settleResponse.wasMerchantReceiptPrinted() ? settleResponse.getMerchantReceipt().trim() : "# PRINTED FROM EFTPOS" + "\n");
                }
                break;
            case UNKNOWN:
                formAction.txtAreaFlow.append("# SETTLEMENT ENQUIRY RESULT UNKNOWN!" + "\n");
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private void printPairingStatus() {
        lblPairingStatus.setText(spi.getCurrentStatus().toString());
    }

    void getOKActionComponents() {
        formAction.btnAction1.setEnabled(true);
        formAction.btnAction1.setVisible(true);
        formAction.btnAction1.setText(ComponentLabels.OK);
        formAction.btnAction2.setVisible(false);
        formAction.btnAction3.setVisible(false);
        getUnvisibleActionComponents();
    }

    private void getUnvisibleActionComponents() {
        formAction.lblAction1.setVisible(false);
        formAction.lblAction2.setVisible(false);
        formAction.lblAction3.setVisible(false);
        formAction.lblAction4.setVisible(false);
        formAction.txtAction1.setVisible(false);
        formAction.txtAction2.setVisible(false);
        formAction.txtAction3.setVisible(false);
        formAction.txtAction4.setVisible(false);
        formAction.cboxAction1.setVisible(false);
        formAction.cmbTransactions.setVisible(false);
        formAction.lblAction5.setVisible(false);
    }

    @Override
    public void windowOpened(WindowEvent e) {
        loadTenants();
        if (new File("Secrets.bin").exists()) {
            secretsFile = readFromBinaryFile("Secrets.bin");
            String secretsString = secretsFile.get("Secrets");
            spiSecrets = new Secrets(secretsString.split(":")[0].trim(), secretsString.split(":")[1].trim());
            eftposAddress = secretsFile.get("EftposAddress");
            formMain.txtDeviceAddress.setText(eftposAddress);
            posId = secretsFile.get("PosId");
            formMain.txtPosId.setText(posId);
            formMain.txtPosId.setEnabled(true);
            serialNumber = secretsFile.get("SerialNumber");
            formMain.txtSerialNumber.setText(serialNumber);
            formMain.txtSerialNumber.setEnabled(false);
            testMode = Boolean.parseBoolean(secretsFile.get("TestMode"));
            tenantCode = secretsFile.get("TenantCode");
            if (!tenantsMap.isEmpty())
                formMain.cmbTenantsList.setSelectedItem(tenantsMap.entrySet().stream().filter(entry -> tenantCode.equals(entry.getValue())).map(Map.Entry::getKey).findFirst().get());
            cmbTenantsList.setEnabled(false);
            formMain.testModeCheckBox.setSelected(testMode);
            formMain.testModeCheckBox.setEnabled(false);
            formMain.txtSecrets.setText(secretsFile.get("Secrets"));
            formMain.txtSecrets.setEnabled(true);
            formMain.btnSave.setEnabled(false);
            formMain.secretsCheckBox.setSelected(true);
            formMain.btnAction.setEnabled(true);
            formMain.btnAction.setText(ComponentLabels.START);
        } else {
            btnAction.setText(ComponentLabels.PAIR);
        }
        readLastTransactions();
        isAppStarted = true;
        Start();
    }

    private void loadTenants() {
        Gson GSON = new Gson();
        Tenants tenants = null;

        try {
            tenants = Spi.getAvailableTenants("assembly", apiKey, "AU");
        } catch (Exception e) {
            LOG.error(String.format("# Error while retrieving Tenants - %s, will try to load from cache", e.getLocalizedMessage()));
        }

        if (tenants == null & new File("tenants.bin").exists()) {
            tenants = GSON.fromJson((String) readFromBinaryFile("tenants.bin"), Tenants.class);
            LOG.info("# Tenants successfully loaded from cache");
        }

        if (tenants != null && tenants.getData().size() > 0) {
            cmbTenantsList.addItem(" ");
            tenants.getData().forEach(tenantDetails -> {
                tenantsMap.put(tenantDetails.getName(), tenantDetails.getCode());
                cmbTenantsList.addItem(tenantDetails.getName());
            });
            writeToBinaryFile("tenants.bin", GSON.toJson(tenants), false);
        }
        cmbTenantsList.addItem("Other");
    }

    private void readLastTransactions() {
        if (new File(transactionsFile).exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(transactionsFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    lastTransactions.add(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                new File(transactionsFile).createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        lastTransactions.forEach(s -> formAction.cmbTransactions.addItem(s));
    }


    private void persistLastTransaction(String posRefId) {
        lastTransactions.add(0, posRefId);
        formAction.cmbTransactions.addItem(posRefId);
        if (lastTransactions.size() > 10) {
            lastTransactions.remove(11);
        }
        try (BufferedWriter wr = new BufferedWriter(new FileWriter(transactionsFile))) {
            lastTransactions.forEach(s -> {
                try {
                    wr.write(s);
                    wr.newLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void windowClosing(WindowEvent e) {
    }

    @Override
    public void windowClosed(WindowEvent e) {

    }

    @Override
    public void windowIconified(WindowEvent e) {

    }

    @Override
    public void windowDeiconified(WindowEvent e) {

    }

    @Override
    public void windowActivated(WindowEvent e) {

    }

    @Override
    public void windowDeactivated(WindowEvent e) {

    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        pnlMain = new JPanel();
        pnlMain.setLayout(new GridLayoutManager(5, 1, new Insets(3, 3, 3, 3), -1, -1));
        pnlMain.setEnabled(false);
        pnlSettings = new JPanel();
        pnlSettings.setLayout(new GridLayoutManager(6, 2, new Insets(3, 3, 3, 3), -1, -1));
        pnlSettings.setEnabled(true);
        pnlMain.add(pnlSettings, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        pnlSettings.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        lblPosId = new JLabel();
        lblPosId.setText("Pos Id");
        pnlSettings.add(lblPosId, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtPosId = new JTextField();
        txtPosId.setText("");
        pnlSettings.add(txtPosId, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        lblSerialNumber = new JLabel();
        lblSerialNumber.setText("Serial Number");
        pnlSettings.add(lblSerialNumber, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtSerialNumber = new JTextField();
        pnlSettings.add(txtSerialNumber, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        lblDeviceAddress = new JLabel();
        lblDeviceAddress.setText("Device Address");
        pnlSettings.add(lblDeviceAddress, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtDeviceAddress = new JTextField();
        pnlSettings.add(txtDeviceAddress, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        lblSettings = new JLabel();
        Font lblSettingsFont = this.$$$getFont$$$(null, Font.BOLD, 16, lblSettings.getFont());
        if (lblSettingsFont != null) lblSettings.setFont(lblSettingsFont);
        lblSettings.setHorizontalAlignment(0);
        lblSettings.setHorizontalTextPosition(0);
        lblSettings.setText("Settings");
        pnlSettings.add(lblSettings, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        cmbTenantsList = new JComboBox();
        pnlSettings.add(cmbTenantsList, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        lblTenants = new JLabel();
        lblTenants.setText("Payment Provider");
        pnlSettings.add(lblTenants, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtOtherTenant = new JTextField();
        pnlSettings.add(txtOtherTenant, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        lblOther = new JLabel();
        lblOther.setText("Other");
        pnlSettings.add(lblOther, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        pnlAutoAddress = new JPanel();
        pnlAutoAddress.setLayout(new GridLayoutManager(3, 3, new Insets(3, 3, 3, 3), -1, -1));
        pnlAutoAddress.setEnabled(true);
        pnlMain.add(pnlAutoAddress, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        pnlAutoAddress.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        lblAutoAddress = new JLabel();
        Font lblAutoAddressFont = this.$$$getFont$$$(null, Font.BOLD, 16, lblAutoAddress.getFont());
        if (lblAutoAddressFont != null) lblAutoAddress.setFont(lblAutoAddressFont);
        lblAutoAddress.setHorizontalAlignment(0);
        lblAutoAddress.setHorizontalTextPosition(0);
        lblAutoAddress.setText("Auto Address Resolution");
        pnlAutoAddress.add(lblAutoAddress, new GridConstraints(0, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        testModeCheckBox = new JCheckBox();
        testModeCheckBox.setSelected(true);
        testModeCheckBox.setText("Test Mode");
        pnlAutoAddress.add(testModeCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnSave = new JButton();
        btnSave.setText("Save");
        pnlAutoAddress.add(btnSave, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnResolveAddress = new JButton();
        btnResolveAddress.setText("Retrieve Address");
        pnlAutoAddress.add(btnResolveAddress, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        pnlSecrets = new JPanel();
        pnlSecrets.setLayout(new GridLayoutManager(3, 2, new Insets(3, 3, 3, 3), -1, -1));
        pnlMain.add(pnlSecrets, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
        pnlSecrets.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        secretsCheckBox = new JCheckBox();
        secretsCheckBox.setText("Secrets");
        pnlSecrets.add(secretsCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        pnlSecrets.add(spacer1, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        txtSecrets = new JTextField();
        txtSecrets.setEnabled(false);
        pnlSecrets.add(txtSecrets, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        lblSecrets = new JLabel();
        Font lblSecretsFont = this.$$$getFont$$$(null, Font.BOLD, 16, lblSecrets.getFont());
        if (lblSecretsFont != null) lblSecrets.setFont(lblSecretsFont);
        lblSecrets.setHorizontalAlignment(0);
        lblSecrets.setText("Secrets");
        pnlSecrets.add(lblSecrets, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        pnlSwitch = new JPanel();
        pnlSwitch.setLayout(new GridLayoutManager(1, 2, new Insets(3, 3, 3, 3), -1, -1));
        pnlMain.add(pnlSwitch, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        btnTransactions = new JButton();
        btnTransactions.setText("Transactions");
        btnTransactions.setVisible(false);
        pnlSwitch.add(btnTransactions, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        pnlSwitch.add(spacer2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        pnlMain.add(panel1, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        lblPairingStatus = new JLabel();
        lblPairingStatus.setText("Unpaired");
        panel1.add(lblPairingStatus, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnAction = new JButton();
        btnAction.setEnabled(true);
        btnAction.setText("btnAction");
        panel1.add(btnAction, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        lblPosId.setLabelFor(txtPosId);
        lblSerialNumber.setLabelFor(txtSerialNumber);
        lblDeviceAddress.setLabelFor(txtDeviceAddress);
    }

    /**
     * @noinspection ALL
     */
    private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                resultName = fontName;
            } else {
                resultName = currentFont.getName();
            }
        }
        Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
        boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
        Font fontWithFallback = isMac ? new Font(font.getFamily(), font.getStyle(), font.getSize()) : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
        return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return pnlMain;
    }

}
