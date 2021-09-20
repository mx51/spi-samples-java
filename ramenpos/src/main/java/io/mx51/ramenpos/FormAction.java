package io.mx51.ramenpos;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import io.mx51.spi.model.InitiateTxResult;
import io.mx51.spi.model.SpiStatus;
import io.mx51.spi.model.TransactionType;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static io.mx51.ramenpos.FormMain.*;

public class FormAction implements WindowListener {
    public JPanel pnlMain;
    public JButton btnAction1;
    public JButton btnAction2;
    public JButton btnAction3;
    public JLabel lblFlowMessage;
    public JLabel lblFlow;
    public JLabel lblFlowStatus;
    public JPanel pnlFlow;
    public JPanel pnlActions;
    public JTextArea txtAreaFlow;
    public JLabel lblAction1;
    public JTextField txtAction1;
    public JLabel lblAction2;
    public JTextField txtAction2;
    public JLabel lblAction3;
    public JTextField txtAction3;
    public JCheckBox cboxAction1;
    public JLabel lblAction4;
    public JTextField txtAction4;
    public JComboBox cmbTransactions;
    public JLabel lblAction5;

    public FormAction() {
        btnAction1.addActionListener(e -> {
            switch (btnAction1.getText()) {
                case ComponentLabels.CONFIRM_CODE:
                    formMain.spi.pairingConfirmCode();
                    break;
                case ComponentLabels.CANCEL_PAIRING:
                    formAction.btnAction1.setEnabled(false);
                    actionDialog.pack();
                    formMain.spi.pairingCancel();
                    break;
                case ComponentLabels.CANCEL:
                    formAction.btnAction1.setEnabled(false);
                    actionDialog.pack();
                    formMain.spi.cancelTransaction();
                    break;
                case ComponentLabels.OK:
                    formMain.spi.ackFlowEndedAndBackToIdle();
                    formMain.printStatusAndActions();
                    mainFrame.setEnabled(true);
                    transactionsFrame.setEnabled(true);
                    actionDialog.setVisible(false);
                    if (formMain.spi.getCurrentStatus() == SpiStatus.PAIRED_CONNECTING) {
                        mainFrame.pack();
                    }
                    break;
                case ComponentLabels.OK_UNPAIRED:
                    formMain.spi.ackFlowEndedAndBackToIdle();
                    formMain.btnAction.setText(ComponentLabels.PAIR);
                    mainFrame.setEnabled(true);
                    formMain.secretsCheckBox.setSelected(false);
                    mainFrame.pack();
                    actionDialog.setVisible(false);
                    transactionsFrame.setVisible(false);
                    mainFrame.setVisible(true);
                    break;
                case ComponentLabels.ACCEPT_SIGNATURE:
                    formMain.spi.acceptSignature(true);
                    break;
                case ComponentLabels.RETRY:
                    formMain.spi.ackFlowEndedAndBackToIdle();
                    txtAreaFlow.setText("");
                    switch (formMain.spi.getCurrentTxFlowState().getType()) {
                        case PURCHASE:
                            doPurchase();
                            break;
                        case REFUND:
                            doRefund();
                            break;
                        case CASHOUT_ONLY:
                            doCashOut();
                            break;
                        case MOTO:
                            doMoto();
                            break;
                        default:
                            lblFlowMessage.setText("Retry by selecting from the options");
                            formMain.printStatusAndActions();
                            break;
                    }
                    break;

                case ComponentLabels.PURCHASE:
                    doPurchase();
                    break;
                case ComponentLabels.REFUND:
                    doRefund();
                    break;
                case ComponentLabels.CASH_OUT:
                    doCashOut();
                    break;
                case ComponentLabels.MOTO:
                    doMoto();
                    break;
                case ComponentLabels.RECOVERY:
                    doRecovery();
                    break;
                case ComponentLabels.SET:
                    doHeaderFooter();
                    break;
                case ComponentLabels.PRINT:
                    formMain.spi.printReport(txtAction1.getText().trim(), sanitizePrintText(txtAction2.getText().trim()));
                    break;
                case ComponentLabels.LAST_TX:
                    doLastTx();
                    break;
                case ComponentLabels.GET_TX:
                    doGetTx();
                    break;
            }
        });

        btnAction2.addActionListener(e -> {
            switch (btnAction2.getText()) {
                case ComponentLabels.CANCEL_PAIRING:
                    formMain.spi.pairingCancel();
                    break;
                case ComponentLabels.DECLINE_SIGNATURE:
                    formMain.spi.acceptSignature(false);
                    break;
                case ComponentLabels.CANCEL:
                    formMain.spi.ackFlowEndedAndBackToIdle();
                    txtAreaFlow.setText("");
                    formMain.printStatusAndActions();
                    transactionsFrame.setEnabled(true);
                    actionDialog.setVisible(false);
                    break;
                default:
                    break;
            }
        });

        btnAction3.addActionListener(e -> {
            if (btnAction3.getText().equals(ComponentLabels.CANCEL)) {
                formMain.spi.cancelTransaction();
            }
        });
    }

    @Override
    public void windowOpened(WindowEvent e) {

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
        lblFlowStatus.setText(formMain.spi.getCurrentFlow().toString());
        mainFrame.setEnabled(false);
        mainFrame.pack();
    }

    @Override
    public void windowDeactivated(WindowEvent e) {

    }

    private void doPurchase() {
        int amount = Integer.parseInt(txtAction1.getText());
        int tipAmount = Integer.parseInt(txtAction2.getText());
        int cashoutAmount = Integer.parseInt(txtAction3.getText());
        int surchargeAmount = Integer.parseInt(txtAction4.getText());

        String posRefId = "kebab-" + new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date());
        InitiateTxResult purchase = formMain.spi.initiatePurchaseTx(posRefId, amount, tipAmount, cashoutAmount, cboxAction1.isSelected(), formMain.options, surchargeAmount);

        if (purchase.isInitiated()) {
            txtAreaFlow.setText("# Purchase Initiated. Will be updated with Progress." + "\n");
        } else {
            txtAreaFlow.setText("# Could not initiate purchase: " + purchase.getMessage() + ". Please Retry." + "\n");
        }
    }

    private void doRefund() {
        int amount = Integer.parseInt(txtAction1.getText());
        InitiateTxResult refund = formMain.spi.initiateRefundTx("rfnd-" + new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date()), amount, cboxAction1.isSelected(), formMain.options);

        if (refund.isInitiated()) {
            txtAreaFlow.setText("# Refund Initiated. Will be updated with Progress." + "\n");
        } else {
            txtAreaFlow.setText("# Could not initiate refund: " + refund.getMessage() + ". Please Retry." + "\n");
        }
    }

    private void doCashOut() {
        int amount = Integer.parseInt(txtAction1.getText());
        int surchargeAmount = Integer.parseInt(txtAction2.getText());
        InitiateTxResult coRes = formMain.spi.initiateCashoutOnlyTx("cshout-" + new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date()), amount, surchargeAmount, formMain.options);

        if (coRes.isInitiated()) {
            txtAreaFlow.setText("# Cashout Initiated. Will be updated with Progress." + "\n");
        } else {
            txtAreaFlow.setText("# Could not initiate cashout: " + coRes.getMessage() + ". Please Retry." + "\n");
        }
    }

    private void doMoto() {
        int amount = Integer.parseInt(txtAction1.getText());
        int surchargeAmount = Integer.parseInt(txtAction2.getText());
        InitiateTxResult motoRes = formMain.spi.initiateMotoPurchaseTx("moto-" + new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date()), amount, surchargeAmount, cboxAction1.isSelected(), formMain.options);

        if (motoRes.isInitiated()) {
            txtAreaFlow.setText("# Moto Initiated. Will be updated with Progress." + "\n");
        } else {
            txtAreaFlow.setText("# Could not initiate moto: " + motoRes.getMessage() + ". Please Retry." + "\n");
        }
    }

    private void doRecovery() {

        if (txtAction1.getText().equals("")) {
            JOptionPane.showMessageDialog(null, "Please enter refence!", "Recovery", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        InitiateTxResult recRes = formMain.spi.initiateRecovery(txtAction1.getText().trim(), TransactionType.PURCHASE);

        if (recRes.isInitiated()) {
            txtAreaFlow.setText("# Recovery Initiated. Will be updated with Progress." + "\n");
        } else {
            txtAreaFlow.setText("# Could not initiate recovery: " + recRes.getMessage() + ". Please Retry." + "\n");
        }
    }

    private void doLastTx() {
        InitiateTxResult coRes = formMain.spi.initiateGetLastTx();

        if (coRes.isInitiated()) {
            txtAreaFlow.setText("# Last Transaction Initiated. Will be updated with Progress." + "\n");
        } else {
            txtAreaFlow.setText("# Could not initiate last transaction: " + coRes.getMessage() + ". Please Retry." + "\n");
        }
    }

    private void doGetTx() {
        String posRefId = "";
        if (txtAction1.getText().equals("") && cmbTransactions.getItemCount() == 0) {
            JOptionPane.showMessageDialog(null, "Please enter reference id!", "Get Transaction", JOptionPane.INFORMATION_MESSAGE);
            return;
        } else if (txtAction1.getText().equals("")) {
            posRefId = (String) cmbTransactions.getSelectedItem();
        } else {
            posRefId = txtAction1.getText().trim();
        }

        InitiateTxResult revRes = formMain.spi.initiateGetTx(posRefId);

        if (revRes.isInitiated()) {
            txtAreaFlow.setText("# Get Transaction Initiated. Will be updated with Progress." + "\n");
        } else {
            txtAreaFlow.setText("# Could not initiate get transaction: " + revRes.getMessage() + ". Please Retry." + "\n");
        }
    }

    private void doHeaderFooter() {
        formMain.options.setCustomerReceiptHeader(sanitizePrintText(txtAction1.getText().trim()));
        formMain.options.setMerchantReceiptHeader(sanitizePrintText(txtAction1.getText().trim()));
        formMain.options.setCustomerReceiptFooter(sanitizePrintText(txtAction2.getText().trim()));
        formMain.options.setMerchantReceiptFooter(sanitizePrintText(txtAction2.getText().trim()));

        lblFlowMessage.setText("# --> Receipt Header and Footer is entered");
        formMain.getOKActionComponents();
    }

    private String sanitizePrintText(String printText) {
        printText = printText.replace("\\r\\n", "\r\n");
        printText = printText.replace("\\n", "\n");
        return printText.replace("\\\\", "\\");
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
        pnlMain.setLayout(new GridLayoutManager(2, 1, new Insets(3, 3, 3, 3), -1, -1));
        pnlFlow = new JPanel();
        pnlFlow.setLayout(new GridLayoutManager(3, 2, new Insets(3, 3, 3, 3), -1, -1));
        pnlMain.add(pnlFlow, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        lblFlow = new JLabel();
        lblFlow.setHorizontalAlignment(4);
        lblFlow.setHorizontalTextPosition(0);
        lblFlow.setText("Flow:");
        pnlFlow.add(lblFlow, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        lblFlowMessage = new JLabel();
        lblFlowMessage.setAutoscrolls(false);
        Font lblFlowMessageFont = this.$$$getFont$$$(null, -1, 12, lblFlowMessage.getFont());
        if (lblFlowMessageFont != null) lblFlowMessage.setFont(lblFlowMessageFont);
        lblFlowMessage.setText("");
        pnlFlow.add(lblFlowMessage, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        lblFlowStatus = new JLabel();
        lblFlowStatus.setForeground(new Color(-4517617));
        lblFlowStatus.setText("Idle");
        pnlFlow.add(lblFlowStatus, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        scrollPane1.setAutoscrolls(true);
        pnlFlow.add(scrollPane1, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(350, 300), new Dimension(350, 300), null, 0, false));
        txtAreaFlow = new JTextArea();
        txtAreaFlow.setEditable(false);
        scrollPane1.setViewportView(txtAreaFlow);
        pnlActions = new JPanel();
        pnlActions.setLayout(new GridLayoutManager(6, 3, new Insets(3, 3, 3, 3), -1, -1));
        pnlMain.add(pnlActions, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        lblAction1 = new JLabel();
        lblAction1.setText("lblAction1");
        pnlActions.add(lblAction1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtAction1 = new JTextField();
        txtAction1.setHorizontalAlignment(4);
        pnlActions.add(txtAction1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        lblAction2 = new JLabel();
        lblAction2.setText("lblAction2");
        pnlActions.add(lblAction2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnAction1 = new JButton();
        btnAction1.setText("");
        pnlActions.add(btnAction1, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(100, -1), null, 0, false));
        txtAction2 = new JTextField();
        txtAction2.setHorizontalAlignment(4);
        pnlActions.add(txtAction2, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        btnAction2 = new JButton();
        btnAction2.setText("");
        pnlActions.add(btnAction2, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(100, -1), null, 0, false));
        lblAction3 = new JLabel();
        lblAction3.setText("lblAction3");
        pnlActions.add(lblAction3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtAction3 = new JTextField();
        txtAction3.setHorizontalAlignment(4);
        pnlActions.add(txtAction3, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        btnAction3 = new JButton();
        btnAction3.setText("");
        pnlActions.add(btnAction3, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(100, -1), null, 0, false));
        cboxAction1 = new JCheckBox();
        cboxAction1.setText("cboxAction1");
        pnlActions.add(cboxAction1, new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        lblAction4 = new JLabel();
        lblAction4.setText("lblAction4");
        pnlActions.add(lblAction4, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtAction4 = new JTextField();
        txtAction4.setHorizontalAlignment(4);
        pnlActions.add(txtAction4, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        cmbTransactions = new JComboBox();
        cmbTransactions.setEnabled(true);
        pnlActions.add(cmbTransactions, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        lblAction5 = new JLabel();
        lblAction5.setEnabled(true);
        lblAction5.setText("lblAction5");
        pnlActions.add(lblAction5, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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
