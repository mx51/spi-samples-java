package com.assamblypayments.ramenpos;

import com.assemblypayments.spi.model.InitiateTxResult;
import com.assemblypayments.spi.model.TransactionType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.assamblypayments.ramenpos.FormMain.*;

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

    public FormAction() {
        btnAction1.addActionListener(e -> {
            switch (btnAction1.getText()) {
                case Enums.ConfirmCode:
                    formMain.spi.pairingConfirmCode();
                    break;
                case Enums.CancelPairing:
                    formAction.btnAction1.setEnabled(false);
                    actionDialog.pack();
                    formMain.spi.pairingCancel();
                    break;
                case Enums.Cancel:
                    formAction.btnAction1.setEnabled(false);
                    actionDialog.pack();
                    formMain.spi.cancelTransaction();
                    break;
                case Enums.OK:
                    formMain.spi.ackFlowEndedAndBackToIdle();
                    formMain.printStatusAndActions();
                    mainFrame.setEnabled(true);
                    transactionsFrame.setEnabled(true);
                    actionDialog.setVisible(false);
                    break;
                case Enums.OKUnpaired:
                    formMain.spi.ackFlowEndedAndBackToIdle();
                    formMain.btnAction.setText(Enums.Pair);
                    mainFrame.setEnabled(true);
                    formMain.secretsCheckBox.setSelected(false);
                    mainFrame.pack();
                    actionDialog.setVisible(false);
                    transactionsFrame.setVisible(false);
                    mainFrame.setVisible(true);
                    break;
                case Enums.AcceptSignature:
                    formMain.spi.acceptSignature(true);
                    break;
                case Enums.Retry:
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

                case Enums.Purchase:
                    doPurchase();
                    break;
                case Enums.Refund:
                    doRefund();
                    break;
                case Enums.CashOut:
                    doCashOut();
                    break;
                case Enums.MOTO:
                    doMoto();
                    break;
                case Enums.Recovery:
                    doRecovery();
                    break;
                case Enums.Set:
                    doHeaderFooter();
                    break;
                case Enums.Print:
                    formMain.spi.printReport(txtAction1.getText().trim(), sanitizePrintText(txtAction2.getText().trim()));
                    break;
                case Enums.LastTx:
                    doLastTx();
                    break;
            }
        });

        btnAction2.addActionListener(e -> {
            switch (btnAction2.getText()) {
                case Enums.CancelPairing:
                    formMain.spi.pairingCancel();
                    break;
                case Enums.DeclineSignature:
                    formMain.spi.acceptSignature(false);
                    break;
                case Enums.Cancel:
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
            if (btnAction3.getText().equals(Enums.Cancel)) {
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
        InitiateTxResult refund = formMain.spi.initiateRefundTx("rfnd-" + new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date()), amount, cboxAction1.isSelected());

        if (refund.isInitiated()) {
            txtAreaFlow.setText("# Refund Initiated. Will be updated with Progress." + "\n");
        } else {
            txtAreaFlow.setText("# Could not initiate refund: " + refund.getMessage() + ". Please Retry." + "\n");
        }
    }

    private void doCashOut() {
        int amount = Integer.parseInt(txtAction1.getText());
        int surchargeAmount = Integer.parseInt(txtAction2.getText());
        InitiateTxResult coRes = formMain.spi.initiateCashoutOnlyTx("cshout-" + new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date()), amount, surchargeAmount);

        if (coRes.isInitiated()) {
            txtAreaFlow.setText("# Cashout Initiated. Will be updated with Progress." + "\n");
        } else {
            txtAreaFlow.setText("# Could not initiate cashout: " + coRes.getMessage() + ". Please Retry." + "\n");
        }
    }

    private void doMoto() {
        int amount = Integer.parseInt(txtAction1.getText());
        int surchargeAmount = Integer.parseInt(txtAction2.getText());
        InitiateTxResult motoRes = formMain.spi.initiateMotoPurchaseTx("moto-" + new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date()), amount, surchargeAmount);

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

    private void doHeaderFooter() {
        formMain.options.setCustomerReceiptHeader(sanitizePrintText(txtAction1.getText().trim()));
        formMain.options.setMerchantReceiptHeader(sanitizePrintText(txtAction1.getText().trim()));
        formMain.options.setCustomerReceiptFooter(sanitizePrintText(txtAction2.getText().trim()));
        formMain.options.setMerchantReceiptFooter(sanitizePrintText(txtAction2.getText().trim()));

        lblFlowMessage.setText("# --> Receipt Header and Footer is entered");
        formMain.getOKActionComponents();
    }

    private String sanitizePrintText(String printText) {
//        printText = printText.replace("\\emphasis", "\emphasis");
//        printText = printText.replace("\\clear", "\clear");
//        //printText = printText.replace("\r\n", );
//        return printText.replace("\n", Environment.NewLine);

        return "";
    }
}
