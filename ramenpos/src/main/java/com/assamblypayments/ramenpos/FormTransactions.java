package com.assamblypayments.ramenpos;

import com.assemblypayments.spi.model.InitiateTxResult;
import com.assemblypayments.spi.util.RequestIdHelper;

import javax.swing.*;
import java.awt.event.*;

import static com.assamblypayments.ramenpos.FormMain.*;

public class FormTransactions implements WindowListener {
    public JPanel pnlMain;
    public JPanel pnlOtherTransactions;
    public JPanel pnlTransactionalActions;
    public JPanel pnlSettings;
    public JCheckBox cboxReceiptFromEftpos;
    public JCheckBox cboxSignFromEftpos;
    public JPanel pnlStatus;
    public JPanel pnlReceipt;
    public JButton btnSecrets;
    public JLabel lblFlowStatus;
    public JLabel lblStatus;
    public JLabel lblSettings;
    public JButton btnPurchase;
    public JButton btnMoto;
    public JButton btnRefund;
    public JButton bntCashout;
    public JButton btnSettle;
    public JButton btnSettleEnq;
    public JLabel lblTransActions;
    public JLabel lblOtherActions;
    public JButton btnRecovery;
    public JButton btnLastTrans;
    public JLabel lblReceipt;
    public JButton btnTerminalStatus;
    public JCheckBox cboxPrintMerchantCopy;
    public JButton btnEftposPrinting;
    public JTextArea txtAreaReceipt;
    public JButton btnTerminalConfiguration;
    public JButton btnHeaderFooter;
    public JPanel pnlSwitch;
    public JButton btnSettings;

    public FormTransactions() {
        cboxReceiptFromEftpos.addItemListener(e -> {
            formMain.spi.getConfig().setPromptForCustomerCopyOnEftpos(cboxReceiptFromEftpos.isSelected());
        });
        cboxSignFromEftpos.addItemListener(e -> {
            formMain.spi.getConfig().setSignatureFlowOnEftpos(cboxSignFromEftpos.isSelected());
        });

        cboxPrintMerchantCopy.addItemListener(e -> {
            formMain.spi.getConfig().setPrintMerchantCopy(cboxPrintMerchantCopy.isSelected());
        });
        btnSecrets.addActionListener(e -> {
            formAction.txtAreaFlow.setText((""));

            if (formMain.spiSecrets != null) {
                formAction.txtAreaFlow.setText("Pos Id:\n");
                formAction.txtAreaFlow.setText(formAction.txtAreaFlow.getText() + formMain.posId + "\n");
                formAction.txtAreaFlow.setText(formAction.txtAreaFlow.getText() + "Eftpos Address:\n");
                formAction.txtAreaFlow.setText(formAction.txtAreaFlow.getText() + formMain.eftposAddress + "\n");
                formAction.txtAreaFlow.setText(formAction.txtAreaFlow.getText() + "Secrets:\n");
                formAction.txtAreaFlow.setText(formAction.txtAreaFlow.getText() + formMain.spiSecrets.getEncKey() + ":" + formMain.spiSecrets.getHmacKey() + "\n");
                formMain.saveSecrets();
            }

            formMain.getOKActionComponents();
            transactionsFrame.setEnabled(false);
            actionDialog.setVisible(true);
            mainFrame.pack();
            transactionsFrame.pack();
            actionDialog.pack();
        });
        btnPurchase.addActionListener(e -> {
            formAction.lblFlowMessage.setText("Please enter the amount you would like to purchase for in cents");
            formAction.btnAction1.setEnabled(true);
            formAction.btnAction1.setVisible(true);
            formAction.btnAction1.setText(ComponentLabels.PURCHASE);
            formAction.btnAction2.setVisible(true);
            formAction.btnAction2.setText(ComponentLabels.CANCEL);
            formAction.btnAction3.setVisible(false);
            formAction.lblAction1.setVisible(true);
            formAction.lblAction1.setText(ComponentLabels.AMOUNT);
            formAction.txtAction1.setVisible(true);
            formAction.txtAction1.setText("0");
            formAction.lblAction2.setVisible(true);
            formAction.lblAction2.setText(ComponentLabels.TIP_AMOUNT);
            formAction.txtAction2.setVisible(true);
            formAction.txtAction2.setText("0");
            formAction.lblAction3.setVisible(true);
            formAction.lblAction3.setText(ComponentLabels.CASHOUT_AMOUNT);
            formAction.txtAction3.setVisible(true);
            formAction.txtAction3.setText("0");
            formAction.lblAction4.setVisible(true);
            formAction.lblAction4.setText(ComponentLabels.SURCHARGE_AMOUNT);
            formAction.txtAction4.setVisible(true);
            formAction.txtAction4.setText("0");
            formAction.cboxAction1.setVisible(true);
            formAction.cboxAction1.setText(ComponentLabels.PROMPT_FOR_CASHOUT);
            actionDialog.setVisible(true);
            actionDialog.pack();
            transactionsFrame.setEnabled(false);
            transactionsFrame.pack();
        });
        btnMoto.addActionListener(e -> {
            formAction.lblFlowMessage.setText("Please enter the amount you would like to moto for in cents");
            formAction.btnAction1.setEnabled(true);
            formAction.btnAction1.setVisible(true);
            formAction.btnAction1.setText(ComponentLabels.MOTO);
            formAction.btnAction2.setVisible(true);
            formAction.btnAction2.setText(ComponentLabels.CANCEL);
            formAction.btnAction3.setVisible(false);
            formAction.lblAction1.setVisible(true);
            formAction.lblAction1.setText(ComponentLabels.AMOUNT);
            formAction.txtAction1.setVisible(true);
            formAction.txtAction1.setText("0");
            formAction.lblAction2.setVisible(true);
            formAction.lblAction2.setText(ComponentLabels.SURCHARGE_AMOUNT);
            formAction.txtAction2.setVisible(true);
            formAction.txtAction2.setText("0");
            formAction.lblAction3.setVisible(false);
            formAction.txtAction3.setVisible(false);
            formAction.lblAction4.setVisible(false);
            formAction.txtAction4.setVisible(false);
            formAction.cboxAction1.setVisible(true);
            formAction.cboxAction1.setText(ComponentLabels.SUPPRESS_MERHCANT_PASSWORD);
            transactionsFrame.setEnabled(false);
            actionDialog.setVisible(true);
            actionDialog.pack();
            transactionsFrame.pack();
        });
        btnRefund.addActionListener(e -> {
            formAction.lblFlowMessage.setText("Please enter the amount you would like to refund for in cents");
            formAction.btnAction1.setEnabled(true);
            formAction.btnAction1.setVisible(true);
            formAction.btnAction1.setText(ComponentLabels.REFUND);
            formAction.btnAction2.setVisible(true);
            formAction.btnAction2.setText(ComponentLabels.CANCEL);
            formAction.btnAction3.setVisible(false);
            formAction.lblAction1.setVisible(true);
            formAction.lblAction1.setText(ComponentLabels.AMOUNT);
            formAction.txtAction1.setVisible(true);
            formAction.txtAction1.setText("0");
            formAction.lblAction2.setVisible(false);
            formAction.txtAction2.setVisible(false);
            formAction.lblAction3.setVisible(false);
            formAction.txtAction3.setVisible(false);
            formAction.lblAction4.setVisible(false);
            formAction.txtAction4.setVisible(false);
            formAction.cboxAction1.setVisible(true);
            formAction.cboxAction1.setText(ComponentLabels.SUPPRESS_MERHCANT_PASSWORD);
            transactionsFrame.setEnabled(false);
            actionDialog.setVisible(true);
            actionDialog.pack();
            transactionsFrame.pack();
        });
        bntCashout.addActionListener(e -> {
            formAction.lblFlowMessage.setText("Please enter the amount you would like to cashout for in cents");
            formAction.btnAction1.setEnabled(true);
            formAction.btnAction1.setVisible(true);
            formAction.btnAction1.setText(ComponentLabels.CASH_OUT);
            formAction.btnAction2.setVisible(true);
            formAction.btnAction2.setText(ComponentLabels.CANCEL);
            formAction.btnAction3.setVisible(false);
            formAction.lblAction1.setVisible(true);
            formAction.lblAction1.setText(ComponentLabels.AMOUNT);
            formAction.txtAction1.setVisible(true);
            formAction.txtAction1.setText("0");
            formAction.lblAction2.setVisible(true);
            formAction.lblAction2.setText(ComponentLabels.SURCHARGE_AMOUNT);
            formAction.txtAction2.setVisible(true);
            formAction.txtAction2.setText("0");
            formAction.lblAction3.setVisible(false);
            formAction.txtAction3.setVisible(false);
            formAction.lblAction4.setVisible(false);
            formAction.txtAction4.setVisible(false);
            formAction.cboxAction1.setVisible(false);
            transactionsFrame.setEnabled(false);
            actionDialog.setVisible(true);
            actionDialog.pack();
            transactionsFrame.pack();
        });
        btnSettle.addActionListener(e -> {
            InitiateTxResult settleRes = formMain.spi.initiateSettleTx(RequestIdHelper.id("settle"), formMain.options);

            if (settleRes.isInitiated()) {
                formAction.txtAreaFlow.setText("# Settle Initiated. Will be updated with Progress." + "\n");
            } else {
                formAction.txtAreaFlow.setText("# Could not initiate settlement: " + settleRes.getMessage() + ". Please Retry." + "\n");
            }
        });
        btnSettleEnq.addActionListener(e -> {
            InitiateTxResult senqRes = formMain.spi.initiateSettlementEnquiry(RequestIdHelper.id("stlenq"), formMain.options);

            if (senqRes.isInitiated()) {
                formAction.txtAreaFlow.setText("# Settle Enquiry Initiated. Will be updated with Progress." + "\n");
            } else {
                formAction.txtAreaFlow.setText("# Could not initiate settlement enquiry: " + senqRes.getMessage() + ". Please Retry." + "\n");
            }
        });
        btnLastTrans.addActionListener(e -> {
            formAction.btnAction1.setEnabled(true);
            formAction.btnAction1.setVisible(true);
            formAction.btnAction1.setText(ComponentLabels.LAST_TX);
            formAction.btnAction2.setVisible(true);
            formAction.btnAction2.setText(ComponentLabels.CANCEL);
            formAction.btnAction3.setVisible(false);
            formAction.lblAction1.setVisible(true);
            formAction.lblAction1.setText(ComponentLabels.REFERENCE);
            formAction.txtAction1.setVisible(true);
            formAction.txtAction1.setText("");
            formAction.lblAction2.setVisible(false);
            formAction.txtAction2.setVisible(false);
            formAction.lblAction3.setVisible(false);
            formAction.txtAction3.setVisible(false);
            formAction.lblAction4.setVisible(false);
            formAction.txtAction4.setVisible(false);
            formAction.cboxAction1.setVisible(false);
            transactionsFrame.setEnabled(false);
            actionDialog.setVisible(true);
            actionDialog.pack();
            transactionsFrame.pack();
        });
        btnRecovery.addActionListener(e -> {
            formAction.lblFlowMessage.setText("Please enter the reference you would like to recovery");
            formAction.btnAction1.setEnabled(true);
            formAction.btnAction1.setVisible(true);
            formAction.btnAction1.setText(ComponentLabels.RECOVERY);
            formAction.btnAction2.setVisible(true);
            formAction.btnAction2.setText(ComponentLabels.CANCEL);
            formAction.btnAction3.setVisible(false);
            formAction.lblAction1.setVisible(true);
            formAction.lblAction1.setText(ComponentLabels.REFERENCE);
            formAction.txtAction1.setVisible(true);
            formAction.txtAction1.setText("");
            formAction.lblAction2.setVisible(false);
            formAction.txtAction2.setVisible(false);
            formAction.lblAction3.setVisible(false);
            formAction.txtAction3.setVisible(false);
            formAction.lblAction4.setVisible(false);
            formAction.txtAction4.setVisible(false);
            formAction.cboxAction1.setVisible(false);
            transactionsFrame.setEnabled(false);
            actionDialog.setVisible(true);
            actionDialog.pack();
            transactionsFrame.pack();
        });
        btnHeaderFooter.addActionListener(e -> {
            formAction.lblFlowMessage.setText("Please enter the receipt header and footer you would like to print");
            formAction.btnAction1.setEnabled(true);
            formAction.btnAction1.setVisible(true);
            formAction.btnAction1.setText(ComponentLabels.SET);
            formAction.btnAction2.setVisible(true);
            formAction.btnAction2.setText(ComponentLabels.CANCEL);
            formAction.btnAction3.setVisible(false);
            formAction.lblAction1.setVisible(true);
            formAction.lblAction1.setText(ComponentLabels.RECEIPT_HEADER);
            formAction.txtAction1.setVisible(true);
            formAction.txtAction1.setText("");
            formAction.lblAction2.setVisible(true);
            formAction.lblAction2.setText(ComponentLabels.RECEIPT_FOOTER);
            formAction.txtAction2.setVisible(true);
            formAction.txtAction2.setText("");
            formAction.lblAction3.setVisible(false);
            formAction.txtAction3.setVisible(false);
            formAction.lblAction4.setVisible(false);
            formAction.txtAction4.setVisible(false);
            formAction.cboxAction1.setVisible(false);
            transactionsFrame.setEnabled(false);
            actionDialog.setVisible(true);
            actionDialog.pack();
            transactionsFrame.pack();
        });
        btnEftposPrinting.addActionListener(e -> {
            formAction.lblFlowMessage.setText("Please enter the print text and key you would like to print receipt");
            formAction.btnAction1.setEnabled(true);
            formAction.btnAction1.setVisible(true);
            formAction.btnAction1.setText(ComponentLabels.PRINT);
            formAction.btnAction2.setVisible(true);
            formAction.btnAction2.setText(ComponentLabels.CANCEL);
            formAction.btnAction3.setVisible(false);
            formAction.lblAction1.setVisible(true);
            formAction.lblAction1.setText(ComponentLabels.KEY);
            formAction.txtAction1.setVisible(true);
            formAction.txtAction1.setText("");
            formAction.lblAction2.setVisible(true);
            formAction.lblAction2.setText(ComponentLabels.PRINT_TEXT);
            formAction.txtAction2.setVisible(true);
            formAction.txtAction2.setText("");
            formAction.lblAction3.setVisible(false);
            formAction.txtAction3.setVisible(false);
            formAction.lblAction4.setVisible(false);
            formAction.txtAction4.setVisible(false);
            formAction.cboxAction1.setVisible(false);
            transactionsFrame.setEnabled(false);
            actionDialog.setVisible(true);
            actionDialog.pack();
            transactionsFrame.pack();
        });
        btnTerminalStatus.addActionListener(e -> formMain.spi.getTerminalStatus());
        btnTerminalConfiguration.addActionListener(e -> formMain.spi.getTerminalConfiguration());
        btnSettings.addActionListener(e -> {
            transactionsFrame.setVisible(false);
            formMain.btnTransactions.setVisible(true);
            mainFrame.setEnabled(true);
            if (formMain.btnAction.getText().equals(ComponentLabels.UN_PAIR)) {
                formMain.btnAction.setEnabled(true);
                formMain.secretsCheckBox.setEnabled(false);
                formMain.autoCheckBox.setEnabled(false);
                formMain.testModeCheckBox.setEnabled(false);
                formMain.btnSave.setEnabled(false);
                formMain.txtPosId.setEnabled(false);
                formMain.txtSerialNumber.setEnabled(false);
                formMain.txtDeviceAddress.setEnabled(false);
                formMain.txtSecrets.setEnabled(false);
            }

            mainFrame.setVisible(true);
            mainFrame.pack();
        });
    }

    @Override
    public void windowOpened(WindowEvent e) {
        mainFrame.setEnabled(false);
        mainFrame.pack();
        mainFrame.setVisible(false);
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
