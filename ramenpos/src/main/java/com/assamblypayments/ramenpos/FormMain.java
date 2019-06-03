package com.assamblypayments.ramenpos;

import com.assemblypayments.spi.Spi;
import com.assemblypayments.spi.model.*;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import static com.assemblypayments.spi.Spi.getVersion;
import static javax.swing.JOptionPane.*;

public class FormMain implements WindowListener {
    public JPanel pnlSettings;
    public JTextField txtPosId;
    public JTextField txtSecrets;
    public JPanel pnlAutoAddress;
    public JCheckBox testModeCheckBox;
    public JCheckBox autoCheckBox;
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

    private static final Logger LOG = LogManager.getLogger("spi");
    private static final String apiKey = "RamenPosDeviceAddressApiKey"; // this key needs to be requested from Assembly Payments
    private static final String acquirerCode = "wbc";

    Spi spi;
    String posId = "";
    String eftposAddress = "";
    Secrets spiSecrets = null;
    TransactionOptions options;
    private String serialNumber = "";
    private boolean autoAddressEnabled;

    final String multilineHtml = "<html><body style='width: 250px'>";

    static FormAction formAction;
    static FormTransactions formTransactions;
    static FormMain formMain;
    static JFrame transactionsFrame;
    static JFrame mainFrame;
    static JDialog actionDialog;

    private boolean isStartButtonClicked;

    private FormMain() {
        btnSave.addActionListener(e -> {
            try {
                if (!areControlsValid(false))
                    return;

                spi.setTestMode(testModeCheckBox.isSelected());
                spi.setAutoAddressResolution(autoAddressEnabled);
                spi.setSerialNumber(txtSerialNumber.getText());
            } catch (Exception ex) {
                LOG.error("Failed while setting values", ex.getMessage());
                showMessageDialog(null, ex.getMessage(), "Error", ERROR_MESSAGE);
            }
        });
        autoCheckBox.addActionListener(e -> {
            btnAction.setEnabled(!autoCheckBox.isSelected());
            btnSave.setEnabled(autoCheckBox.isSelected());
            testModeCheckBox.setSelected(autoCheckBox.isSelected());
            testModeCheckBox.setEnabled(autoCheckBox.isSelected());
            txtDeviceAddress.setEnabled(!autoCheckBox.isSelected());
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
            autoCheckBox.setEnabled(!secretsCheckBox.isSelected());
            testModeCheckBox.setEnabled(!secretsCheckBox.isSelected());
            btnSave.setEnabled(!secretsCheckBox.isSelected());
            btnAction.setEnabled(secretsCheckBox.isSelected());

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

                    spi.setTestMode(testModeCheckBox.isSelected());
                    spi.setAutoAddressResolution(autoAddressEnabled);
                    spi.setSerialNumber(txtSerialNumber.getText());

                    isStartButtonClicked = true;
                    spiSecrets = new Secrets(txtSecrets.getText().split(":")[0].trim(), txtSecrets.getText().split(":")[1].trim());
                    break;
                case ComponentLabels.PAIR:
                    if (!areControlsValid(true))
                        return;

                    try {
                        spi.setPosId(posId);
                        spi.setSerialNumber(serialNumber);
                        spi.setEftposAddress(eftposAddress);
                        mainFrame.setEnabled(false);
                        mainFrame.pack();

                        spi.pair();
                    } catch (Exception ex) {
                        LOG.error(ex.getMessage());
                        showMessageDialog(null, ex.getMessage(), "Error", ERROR_MESSAGE);
                    }
                    break;
                case ComponentLabels.UN_PAIR:
                    formMain.secretsCheckBox.setEnabled(true);
                    formMain.autoCheckBox.setEnabled(true);
                    formMain.testModeCheckBox.setEnabled(true);
                    formMain.btnSave.setEnabled(true);
                    formMain.txtPosId.setEnabled(true);
                    formMain.txtSerialNumber.setEnabled(true);
                    formMain.txtDeviceAddress.setEnabled(true);
                    mainFrame.setEnabled(false);
                    spi.unpair();
                    spi.setSerialNumber("");
                    break;
                default:
                    break;
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

    private boolean areControlsValid(boolean isPairing) {

        autoAddressEnabled = autoCheckBox.isSelected();
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

        if (autoCheckBox.isSelected() && (serialNumber == null || StringUtils.isWhitespace(serialNumber))) {
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
            spi = new Spi(posId, serialNumber, eftposAddress, spiSecrets);
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

        spi.setPosInfo("assembly", "2.4.0");
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

        spi.setAcquirerCode(acquirerCode);
        spi.setDeviceApiKey(apiKey);

        try {
            spi.start();
        } catch (Exception ex) {
            LOG.error(ex.getMessage());
            showMessageDialog(null, ex.getMessage(), "Error", ERROR_MESSAGE);
        }
    }

    private void onDeviceAddressChanged(DeviceAddressStatus deviceAddressStatus) {
        btnAction.setEnabled(false);
        if (spi.getCurrentStatus() == SpiStatus.UNPAIRED) {
            if (deviceAddressStatus.getAddress() != null && !StringUtils.isWhitespace(deviceAddressStatus.getAddress())) {
                txtDeviceAddress.setText(deviceAddressStatus.getAddress());
                btnAction.setEnabled(true);

                if (isStartButtonClicked) {
                    isStartButtonClicked = false;
                    eftposAddress = txtDeviceAddress.getText();
                    Start();
                } else {
                    showMessageDialog(null, "Device Address has been updated to " + deviceAddressStatus.getAddress(), "Info : Device Address Updated", INFORMATION_MESSAGE);
                }
            }
        }
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
                formAction.lblFlowMessage.setText(formMain.multilineHtml + pairingFlowState.getMessage().

                        trim());

                if (!pairingFlowState.getConfirmationCode().

                        equals("")) {
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
                        getUnvisibleActionComponents();
                        break;

                    case PAIRING:
                        if (spi.getCurrentPairingFlowState().isAwaitingCheckFromPos()) {
                            formAction.btnAction1.setEnabled(true);
                            formAction.btnAction1.setVisible(true);
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
                            formAction.btnAction1.setVisible(true);
                            formAction.btnAction1.setText(ComponentLabels.CANCEL);
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
                                    formAction.btnAction1.setEnabled(true);
                                    formAction.btnAction1.setVisible(true);
                                    formAction.btnAction1.setText(ComponentLabels.RETRY);
                                    formAction.btnAction2.setVisible(true);
                                    formAction.btnAction2.setText(ComponentLabels.CANCEL);
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
                            formAction.btnAction1.setVisible(true);
                            formAction.btnAction1.setText(ComponentLabels.CANCEL);
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
                                    formAction.btnAction1.setEnabled(true);
                                    formAction.btnAction1.setVisible(true);
                                    formAction.btnAction1.setText(ComponentLabels.RETRY);
                                    formAction.btnAction2.setVisible(true);
                                    formAction.btnAction2.setText(ComponentLabels.CANCEL);
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
                formAction.txtAreaFlow.append("# Last GLT Response Request Id: " + txState.getLastGltRequestId() + "\n");

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

            // User specified that he intended to retrieve a specific tx by pos_ref_id
            // This is how you can use a handy function to match it.
            Message.SuccessState success = spi.gltMatch(gltResponse, formAction.txtAction1.getText().trim());
            if (success == Message.SuccessState.UNKNOWN) {
                formAction.txtAreaFlow.append("# Did not retrieve expected transaction. Here is what we got:" + "\n");
            } else {
                formAction.txtAreaFlow.append("# Tx matched expected purchase request." + "\n");
            }

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
    }

    @Override
    public void windowOpened(WindowEvent e) {
        btnAction.setText(ComponentLabels.PAIR);
        txtDeviceAddress.setEnabled(false);
        Start();
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
}
