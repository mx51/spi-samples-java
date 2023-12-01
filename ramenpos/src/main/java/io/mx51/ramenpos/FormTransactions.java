package io.mx51.ramenpos;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import io.mx51.spi.model.InitiateTxResult;
import io.mx51.spi.util.RequestIdHelper;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.*;
import java.util.Locale;

import static io.mx51.ramenpos.FormMain.*;

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
    public JButton btnGetTrans;
    public JLabel lblReceipt;
    public JButton btnTerminalStatus;
    public JCheckBox cboxPrintMerchantCopy;
    public JButton btnEftposPrinting;
    public JTextArea txtAreaReceipt;
    public JButton btnTerminalConfiguration;
    public JButton btnHeaderFooter;
    public JPanel pnlSwitch;
    public JButton btnSettings;
    public JButton btnReversal;

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
        btnGetTrans.addActionListener(e -> {
            formAction.btnAction1.setEnabled(true);
            formAction.btnAction1.setVisible(true);
            formAction.btnAction1.setText(ComponentLabels.GET_TX);
            formAction.btnAction2.setVisible(true);
            formAction.btnAction2.setText(ComponentLabels.CANCEL);
            formAction.btnAction3.setVisible(false);
            formAction.lblAction1.setVisible(true);
            formAction.lblAction1.setText("Transaction id: ");
            formAction.txtAction1.setVisible(true);
            formAction.txtAction1.setText("");
            formAction.lblAction2.setVisible(false);
            formAction.txtAction2.setVisible(false);
            formAction.lblAction3.setVisible(false);
            formAction.txtAction3.setVisible(false);
            formAction.lblAction4.setVisible(false);
            formAction.txtAction4.setVisible(false);
            formAction.cboxAction1.setVisible(false);
            formAction.cmbTransactions.setVisible(true);
            formAction.lblAction5.setVisible(true);
            formAction.lblAction5.setText("Trasaction id: ");
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
            formAction.cmbTransactions.setVisible(true);
            formAction.lblAction5.setVisible(true);
            formAction.lblAction5.setText("Trasaction id: ");
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
                formMain.cmbTenantsList.setEnabled(false);
                formMain.testModeCheckBox.setEnabled(false);
                formMain.btnSave.setEnabled(false);
                formMain.txtPosId.setEnabled(false);
                formMain.txtSerialNumber.setEnabled(false);
                formMain.txtSecrets.setEnabled(false);
            }

            mainFrame.setVisible(true);
            mainFrame.pack();
        });
        btnReversal.addActionListener(e -> {
            formAction.btnAction1.setEnabled(true);
            formAction.btnAction1.setVisible(true);
            formAction.btnAction1.setText(ComponentLabels.REVERSAL);
            formAction.btnAction2.setVisible(true);
            formAction.btnAction2.setText(ComponentLabels.CANCEL);
            formAction.btnAction3.setVisible(false);
            formAction.lblAction1.setVisible(true);
            formAction.lblAction1.setText("Transaction id: ");
            formAction.txtAction1.setVisible(true);
            formAction.txtAction1.setText("");
            formAction.lblAction2.setVisible(false);
            formAction.txtAction2.setVisible(false);
            formAction.lblAction3.setVisible(false);
            formAction.txtAction3.setVisible(false);
            formAction.lblAction4.setVisible(false);
            formAction.txtAction4.setVisible(false);
            formAction.cboxAction1.setVisible(false);
            formAction.cmbTransactions.setVisible(true);
            formAction.lblAction5.setVisible(true);
            formAction.lblAction5.setText("Trasaction id: ");
            transactionsFrame.setEnabled(false);
            actionDialog.setVisible(true);
            actionDialog.pack();
            transactionsFrame.pack();
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
        pnlMain.setLayout(new GridLayoutManager(5, 2, new Insets(3, 3, 3, 3), -1, -1));
        pnlStatus = new JPanel();
        pnlStatus.setLayout(new GridLayoutManager(2, 1, new Insets(3, 3, 3, 3), -1, -1));
        pnlMain.add(pnlStatus, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        pnlStatus.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        lblFlowStatus = new JLabel();
        lblFlowStatus.setText("Idle");
        pnlStatus.add(lblFlowStatus, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        lblStatus = new JLabel();
        Font lblStatusFont = this.$$$getFont$$$(null, Font.BOLD, 16, lblStatus.getFont());
        if (lblStatusFont != null) lblStatus.setFont(lblStatusFont);
        lblStatus.setHorizontalAlignment(0);
        lblStatus.setHorizontalTextPosition(0);
        lblStatus.setText("Status");
        pnlStatus.add(lblStatus, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        pnlReceipt = new JPanel();
        pnlReceipt.setLayout(new GridLayoutManager(2, 1, new Insets(3, 3, 3, 3), -1, -1));
        pnlMain.add(pnlReceipt, new GridConstraints(0, 1, 5, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        pnlReceipt.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        lblReceipt = new JLabel();
        Font lblReceiptFont = this.$$$getFont$$$(null, Font.BOLD, 16, lblReceipt.getFont());
        if (lblReceiptFont != null) lblReceipt.setFont(lblReceiptFont);
        lblReceipt.setHorizontalAlignment(0);
        lblReceipt.setHorizontalTextPosition(0);
        lblReceipt.setText("Receipt");
        pnlReceipt.add(lblReceipt, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        scrollPane1.setAutoscrolls(true);
        pnlReceipt.add(scrollPane1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(-1, 467), new Dimension(-1, 467), 0, false));
        txtAreaReceipt = new JTextArea();
        txtAreaReceipt.setEditable(false);
        txtAreaReceipt.setText("");
        scrollPane1.setViewportView(txtAreaReceipt);
        pnlOtherTransactions = new JPanel();
        pnlOtherTransactions.setLayout(new GridLayoutManager(3, 2, new Insets(3, 3, 3, 3), -1, -1));
        pnlMain.add(pnlOtherTransactions, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        pnlOtherTransactions.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        lblOtherActions = new JLabel();
        Font lblOtherActionsFont = this.$$$getFont$$$(null, Font.BOLD, 16, lblOtherActions.getFont());
        if (lblOtherActionsFont != null) lblOtherActions.setFont(lblOtherActionsFont);
        lblOtherActions.setHorizontalAlignment(0);
        lblOtherActions.setHorizontalTextPosition(0);
        lblOtherActions.setText("Other Actions");
        pnlOtherTransactions.add(lblOtherActions, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnTerminalConfiguration = new JButton();
        btnTerminalConfiguration.setText("Terminal Configuration");
        pnlOtherTransactions.add(btnTerminalConfiguration, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnEftposPrinting = new JButton();
        btnEftposPrinting.setText("Free Form Printing");
        pnlOtherTransactions.add(btnEftposPrinting, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnTerminalStatus = new JButton();
        btnTerminalStatus.setText("Terminal Status");
        pnlOtherTransactions.add(btnTerminalStatus, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnHeaderFooter = new JButton();
        btnHeaderFooter.setText("Header / Footer");
        pnlOtherTransactions.add(btnHeaderFooter, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        pnlTransactionalActions = new JPanel();
        pnlTransactionalActions.setLayout(new GridLayoutManager(5, 3, new Insets(3, 3, 3, 3), -1, -1));
        pnlMain.add(pnlTransactionalActions, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        pnlTransactionalActions.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, new Color(-4473925)));
        lblTransActions = new JLabel();
        Font lblTransActionsFont = this.$$$getFont$$$(null, Font.BOLD, 16, lblTransActions.getFont());
        if (lblTransActionsFont != null) lblTransActions.setFont(lblTransActionsFont);
        lblTransActions.setHorizontalAlignment(0);
        lblTransActions.setHorizontalTextPosition(0);
        lblTransActions.setText("Transactional Actions");
        pnlTransactionalActions.add(lblTransActions, new GridConstraints(0, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnPurchase = new JButton();
        btnPurchase.setText("Purchase");
        pnlTransactionalActions.add(btnPurchase, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnMoto = new JButton();
        btnMoto.setText("MOTO");
        pnlTransactionalActions.add(btnMoto, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnRefund = new JButton();
        btnRefund.setText("Refund");
        pnlTransactionalActions.add(btnRefund, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        bntCashout = new JButton();
        bntCashout.setText("Cashout");
        pnlTransactionalActions.add(bntCashout, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnSettle = new JButton();
        btnSettle.setText("Settle");
        pnlTransactionalActions.add(btnSettle, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnSettleEnq = new JButton();
        btnSettleEnq.setText("Settle Enq");
        pnlTransactionalActions.add(btnSettleEnq, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnLastTrans = new JButton();
        btnLastTrans.setText("Last Trans");
        pnlTransactionalActions.add(btnLastTrans, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnRecovery = new JButton();
        btnRecovery.setText("Recovery");
        pnlTransactionalActions.add(btnRecovery, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnGetTrans = new JButton();
        btnGetTrans.setText("Get Transaction");
        pnlTransactionalActions.add(btnGetTrans, new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnReversal = new JButton();
        btnReversal.setText("Reversal");
        pnlTransactionalActions.add(btnReversal, new GridConstraints(4, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        pnlSettings = new JPanel();
        pnlSettings.setLayout(new GridLayoutManager(4, 1, new Insets(3, 3, 3, 3), -1, -1));
        pnlMain.add(pnlSettings, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        pnlSettings.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        lblSettings = new JLabel();
        Font lblSettingsFont = this.$$$getFont$$$(null, Font.BOLD, 16, lblSettings.getFont());
        if (lblSettingsFont != null) lblSettings.setFont(lblSettingsFont);
        lblSettings.setHorizontalAlignment(0);
        lblSettings.setHorizontalTextPosition(0);
        lblSettings.setText("Settings");
        pnlSettings.add(lblSettings, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        cboxReceiptFromEftpos = new JCheckBox();
        cboxReceiptFromEftpos.setText("Receipt From Eftpos");
        pnlSettings.add(cboxReceiptFromEftpos, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        cboxSignFromEftpos = new JCheckBox();
        cboxSignFromEftpos.setLabel("Sign From Eftpos");
        cboxSignFromEftpos.setText("Sign From Eftpos");
        pnlSettings.add(cboxSignFromEftpos, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        cboxPrintMerchantCopy = new JCheckBox();
        cboxPrintMerchantCopy.setText("Print Merchant Copy");
        pnlSettings.add(cboxPrintMerchantCopy, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        pnlSwitch = new JPanel();
        pnlSwitch.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        pnlMain.add(pnlSwitch, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        btnSecrets = new JButton();
        btnSecrets.setText("Secrets");
        pnlSwitch.add(btnSecrets, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        pnlSwitch.add(spacer1, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        btnSettings = new JButton();
        btnSettings.setText("Settings");
        pnlSwitch.add(btnSettings, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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
