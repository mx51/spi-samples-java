package com.assemblypayments.tablepos;

import com.assemblypayments.spi.Spi;
import com.assemblypayments.spi.SpiPayAtTable;
import com.assemblypayments.spi.model.*;
import com.assemblypayments.spi.util.RequestIdHelper;
import com.assemblypayments.utils.SystemHelper;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.HashMap;
import java.util.Scanner;

/**
 * NOTE: THIS PROJECT USES THE 2.1.x of the SPI Client Library
 * <p>
 * This specific POS shows you how to integrate the pay-at-table features of the SPI protocol.
 * If you're just starting, we recommend you start with KebabPos. It goes through the basics.
 */
public class Pos {

    private static Logger LOG = LogManager.getLogger("spi");

    /**
     * My Bills Store.
     * Key = BillId
     * Value = Bill
     */
    private HashMap<String, Bill> billsStore = new HashMap<>();

    /**
     * Lookup dictionary of table -> current order
     * Key = TableId
     * Value = BillId
     */
    private HashMap<String, String> tableToBillMapping = new HashMap<>();

    /**
     * Assembly Payments Integration asks us to persist some data on their behalf
     * So that the eftpos terminal can recover state.
     * Key = BillId
     * Value = Assembly Payments Bill Data
     */
    private HashMap<String, String> assemblyBillDataStore = new HashMap<>();

    private Spi spi;
    private SpiPayAtTable pat;
    private String posId = "TABLEPOS1";
    private String eftposAddress = "192.168.1.9";
    private Secrets spiSecrets = null;

    public static void main(String[] args) {
        new Pos().start(args);
    }

    private void start(String[] args) {
        LOG.info("Starting TablePos...");
        loadPersistedState(args);

        try {
            // This is how you instantiate SPI while checking for JDK compatibility.
            spi = new Spi(posId, eftposAddress, spiSecrets); // It is ok to not have the secrets yet to start with.
        } catch (Spi.CompatibilityException e) {
            System.out.println("# ");
            System.out.println("# Compatibility check failed: " + e.getCause().getMessage());
            System.out.println("# Please ensure you followed all the configuration steps on your machine.");
            System.out.println("# ");
            return;
        }
        spi.setStatusChangedHandler(new Spi.EventHandler<SpiStatus>() {
            @Override
            public void onEvent(SpiStatus value) {
                onSpiStatusChanged(value);
            }
        });
        spi.setPairingFlowStateChangedHandler(new Spi.EventHandler<PairingFlowState>() {
            @Override
            public void onEvent(PairingFlowState value) {
                onPairingFlowStateChanged(value);
            }
        });
        spi.setSecretsChangedHandler(new Spi.EventHandler<Secrets>() {
            @Override
            public void onEvent(Secrets value) {
                onSecretsChanged(value);
            }
        });
        spi.setTxFlowStateChangedHandler(new Spi.EventHandler<TransactionFlowState>() {
            @Override
            public void onEvent(TransactionFlowState value) {
                onTxFlowStateChanged(value);
            }
        });

        pat = spi.enablePayAtTable();
        pat.getConfig().setLabelTableId("Table Number");
        pat.setGetBillStatusDelegate(new SpiPayAtTable.GetBillStatusDelegate() {
            @Override
            public BillStatusResponse getBillStatus(String billId, String tableId, String operatorId) {
                return payAtTableGetBillDetails(billId, tableId, operatorId);
            }
        });
        pat.setBillPaymentReceivedDelegate(new SpiPayAtTable.BillPaymentReceivedDelegate() {
            @Override
            public BillStatusResponse getBillReceived(BillPayment billPayment, String updatedBillData) {
                return payAtTableBillPaymentReceived(billPayment, updatedBillData);
            }
        });

        spi.start();

        SystemHelper.clearConsole();
        System.out.println("# Welcome to TablePos !");
        printStatusAndActions();
        System.out.print("> ");
        acceptUserInput();

        // Cleanup
        spi.dispose();
    }

    //region Main Spi Callbacks

    private void onTxFlowStateChanged(TransactionFlowState txState) {
        SystemHelper.clearConsole();
        printStatusAndActions();
        System.out.print("> ");
    }

    private void onPairingFlowStateChanged(PairingFlowState pairingFlowState) {
        SystemHelper.clearConsole();
        printStatusAndActions();
        System.out.print("> ");
    }

    private void onSecretsChanged(Secrets secrets) {
        spiSecrets = secrets;
        if (secrets != null) {
            System.out.println("# I have secrets: " + secrets.getEncKey() + secrets.getHmacKey() + ". Persist them securely.");
        } else {
            System.out.println("# I have lost the secrets, i.e. unpaired. Destroy the persisted secrets.");
        }
    }

    private void onSpiStatusChanged(SpiStatus status) {
        SystemHelper.clearConsole();
        System.out.println("# --> SPI status changed: " + status);
        printStatusAndActions();
        System.out.print("> ");
    }

    //endregion

    //region PayAtTable Delegates

    private BillStatusResponse payAtTableGetBillDetails(String billId, String tableId, String operatorId) {
        if (billId == null || StringUtils.isWhitespace(billId)) {
            // We were not given a billId, just a tableId.
            // This means that we are being asked for the bill by its table number.

            // Let's see if we have it.
            if (!tableToBillMapping.containsKey(tableId)) {
                // We didn't find a bill for this table.
                // We just tell the Eftpos that.
                BillStatusResponse response = new BillStatusResponse();
                response.setResult(BillRetrievalResult.INVALID_TABLE_ID);
                return response;
            }

            // We have a billId for this Table.
            // Let's set it so we can retrieve it.
            billId = tableToBillMapping.get(tableId);
        }

        if (!billsStore.containsKey(billId)) {
            // We could not find the billId that was asked for.
            // We just tell the EFTPOS that.
            BillStatusResponse response = new BillStatusResponse();
            response.setResult(BillRetrievalResult.INVALID_BILL_ID);
            return response;
        }

        Bill myBill = billsStore.get(billId);

        BillStatusResponse response = new BillStatusResponse();
        response.setResult(BillRetrievalResult.SUCCESS);
        response.setBillId(billId);
        response.setTableId(tableId);
        response.setTotalAmount(myBill.totalAmount);
        response.setOutstandingAmount(myBill.outstandingAmount);
        String billData = assemblyBillDataStore.get(billId);
        response.setBillData(billData);
        return response;
    }

    private BillStatusResponse payAtTableBillPaymentReceived(BillPayment billPayment, String updatedBillData) {
        if (!billsStore.containsKey(billPayment.getBillId())) {
            // We cannot find this bill.
            BillStatusResponse response = new BillStatusResponse();
            response.setResult(BillRetrievalResult.INVALID_BILL_ID);
            return response;
        }

        System.out.println("# Got a " + billPayment.getPaymentType() + " payment against bill " + billPayment.getBillId() + " for table " + billPayment.getTableId());
        Bill bill = billsStore.get(billPayment.getBillId());
        bill.outstandingAmount -= billPayment.getPurchaseAmount();
        bill.tippedAmount += billPayment.getTipAmount();
        System.out.println("Updated bill: " + bill);
        System.out.print("> ");

        // Here you can access other data that you might want to store from this payment, for example the merchant receipt.
        //billPayment.getPurchaseResponse().getMerchantReceipt();

        // It is important that we persist this data on behalf of assembly.
        assemblyBillDataStore.put(billPayment.getBillId(), updatedBillData);

        BillStatusResponse response = new BillStatusResponse();
        response.setResult(BillRetrievalResult.SUCCESS);
        response.setOutstandingAmount(bill.outstandingAmount);
        response.setTotalAmount(bill.totalAmount);
        return response;
    }

    //endregion

    //region User Interface

    private void printStatusAndActions() {
        printFlowInfo();

        printActions();

        printPairingStatus();
    }

    private void printFlowInfo() {
        if (spi.getCurrentFlow() == SpiFlow.PAIRING) {
            PairingFlowState pairingState = spi.getCurrentPairingFlowState();
            System.out.println("### PAIRING PROCESS UPDATE ###");
            System.out.println("# " + pairingState.getMessage());
            System.out.println("# Finished? " + pairingState.isFinished());
            System.out.println("# Successful? " + pairingState.isSuccessful());
            System.out.println("# Confirmation code: " + pairingState.getConfirmationCode());
            System.out.println("# Waiting confirm from EFTPOS? " + pairingState.isAwaitingCheckFromEftpos());
            System.out.println("# Waiting confirm from POS? " + pairingState.isAwaitingCheckFromPos());
        }

        if (spi.getCurrentFlow() == SpiFlow.TRANSACTION) {
            TransactionFlowState txState = spi.getCurrentTxFlowState();
            System.out.println("### TX PROCESS UPDATE ###");
            System.out.println("# " + txState.getDisplayMessage());
            System.out.println("# ID: " + txState.getPosRefId());
            System.out.println("# Type: " + txState.getType());
            System.out.println("# Amount: " + (txState.getAmountCents() / 100.0));
            System.out.println("# Waiting for signature: " + txState.isAwaitingSignatureCheck());
            System.out.println("# Attempting to cancel: " + txState.isAttemptingToCancel());
            System.out.println("# Finished: " + txState.isFinished());
            System.out.println("# Success: " + txState.getSuccess());

            if (txState.isFinished()) {
                System.out.println();
                switch (txState.getSuccess()) {
                    case SUCCESS:
                        if (txState.getType() == TransactionType.PURCHASE) {
                            System.out.println("# WOOHOO - WE GOT PAID!");
                            PurchaseResponse purchaseResponse = new PurchaseResponse(txState.getResponse());
                            System.out.println("# Response: " + purchaseResponse.getResponseText());
                            System.out.println("# RRN: " + purchaseResponse.getRRN());
                            System.out.println("# Scheme: " + purchaseResponse.getSchemeName());
                            System.out.println("# Customer receipt:");
                            System.out.println(purchaseResponse.getCustomerReceipt().trim());
                            System.out.println("# PURCHASE: " + purchaseResponse.getPurchaseAmount());
                            System.out.println("# TIP: " + purchaseResponse.getTipAmount());
                            System.out.println("# CASHOUT: " + purchaseResponse.getCashoutAmount());
                            System.out.println("# BANKED NON-CASH AMOUNT: " + purchaseResponse.getBankNonCashAmount());
                            System.out.println("# BANKED CASH AMOUNT: " + purchaseResponse.getBankCashAmount());
                        } else if (txState.getType() == TransactionType.REFUND) {
                            System.out.println("# REFUND GIVEN - OH WELL!");
                            RefundResponse refundResponse = new RefundResponse(txState.getResponse());
                            System.out.println("# Response: " + refundResponse.getResponseText());
                            System.out.println("# RRN: " + refundResponse.getRRN());
                            System.out.println("# Scheme: " + refundResponse.getSchemeName());
                            System.out.println("# Customer receipt:");
                            System.out.println(refundResponse.getCustomerReceipt().trim());
                        } else if (txState.getType() == TransactionType.SETTLE) {
                            System.out.println("# SETTLEMENT SUCCESSFUL!");
                            if (txState.getResponse() != null) {
                                Settlement settleResponse = new Settlement(txState.getResponse());
                                System.out.println("# Response: " + settleResponse.getResponseText());
                                System.out.println("# Merchant receipt:");
                                System.out.println(settleResponse.getMerchantReceipt().trim());
                            }
                        }
                        break;
                    case FAILED:
                        if (txState.getType() == TransactionType.PURCHASE) {
                            System.out.println("# WE DID NOT GET PAID :(");
                            if (txState.getResponse() != null) {
                                PurchaseResponse purchaseResponse = new PurchaseResponse(txState.getResponse());
                                System.out.println("# Error: " + txState.getResponse().getError());
                                System.out.println("# Response: " + purchaseResponse.getResponseText());
                                System.out.println("# RRN: " + purchaseResponse.getRRN());
                                System.out.println("# Scheme: " + purchaseResponse.getSchemeName());
                                System.out.println("# Customer receipt:");
                                System.out.println(purchaseResponse.getCustomerReceipt().trim());
                            }
                        } else if (txState.getType() == TransactionType.REFUND) {
                            System.out.println("# REFUND FAILED!");
                            if (txState.getResponse() != null) {
                                RefundResponse refundResponse = new RefundResponse(txState.getResponse());
                                System.out.println("# Response: " + refundResponse.getResponseText());
                                System.out.println("# RRN: " + refundResponse.getRRN());
                                System.out.println("# Scheme: " + refundResponse.getSchemeName());
                                System.out.println("# Customer receipt:");
                                System.out.println(refundResponse.getCustomerReceipt().trim());
                            }
                        } else if (txState.getType() == TransactionType.SETTLE) {
                            System.out.println("# SETTLEMENT FAILED!");
                            if (txState.getResponse() != null) {
                                Settlement settleResponse = new Settlement(txState.getResponse());
                                System.out.println("# Response: " + settleResponse.getResponseText());
                                System.out.println("# Error: " + txState.getResponse().getError());
                                System.out.println("# Merchant receipt:");
                                System.out.println(settleResponse.getMerchantReceipt().trim());
                            }
                        }

                        break;
                    case UNKNOWN:
                        if (txState.getType() == TransactionType.PURCHASE) {
                            System.out.println("# WE'RE NOT QUITE SURE WHETHER WE GOT PAID OR NOT :/");
                            System.out.println("# CHECK THE LAST TRANSACTION ON THE EFTPOS ITSELF FROM THE APPROPRIATE MENU ITEM.");
                            System.out.println("# IF YOU CONFIRM THAT THE CUSTOMER PAID, CLOSE THE ORDER.");
                            System.out.println("# OTHERWISE, RETRY THE PAYMENT FROM SCRATCH.");
                        } else if (txState.getType() == TransactionType.REFUND) {
                            System.out.println("# WE'RE NOT QUITE SURE WHETHER THE REFUND WENT THROUGH OR NOT :/");
                            System.out.println("# CHECK THE LAST TRANSACTION ON THE EFTPOS ITSELF FROM THE APPROPRIATE MENU ITEM.");
                            System.out.println("# YOU CAN THE TAKE THE APPROPRIATE ACTION.");
                        }
                        break;
                }
            }
        }
        System.out.println();
    }

    private void printActions() {
        System.out.println("# ----------- TABLE ACTIONS ------------");
        System.out.println("# [open:12]         - start a new bill for table 12");
        System.out.println("# [add:12:1000]     - add $10.00 to the bill of table 12");
        System.out.println("# [close:12]        - close table 12");
        System.out.println("# [tables]          - list open tables");
        System.out.println("# [table:12]        - print current bill for table 12");
        System.out.println("# [bill:9876789876] - print bill with ID 9876789876");
        System.out.println("#");

        if (spi.getCurrentFlow() == SpiFlow.IDLE) {
            System.out.println("# ----------- OTHER ACTIONS ------------");
            System.out.println("# [purchase:1200] - quick purchase transaction");
            System.out.println("# [yuck]          - hand out a refund!");
            System.out.println("# [settle]        - initiate settlement");
            System.out.println("#");
        }
        System.out.println("# ----------- SETTINGS ACTIONS ------------");
        if (spi.getCurrentStatus() == SpiStatus.UNPAIRED && spi.getCurrentFlow() == SpiFlow.IDLE) {
            System.out.println("# [pos_id:TABLEPOS1] - set the POS ID");
        }
        if (spi.getCurrentStatus() == SpiStatus.UNPAIRED || spi.getCurrentStatus() == SpiStatus.PAIRED_CONNECTING) {
            System.out.println("# [eftpos_address:10.161.104.104] - set the EFTPOS address");
        }

        if (spi.getCurrentStatus() == SpiStatus.UNPAIRED && spi.getCurrentFlow() == SpiFlow.IDLE)
            System.out.println("# [pair] - pair with EFTPOS");

        if (spi.getCurrentStatus() != SpiStatus.UNPAIRED && spi.getCurrentFlow() == SpiFlow.IDLE)
            System.out.println("# [unpair] - unpair and disconnect");
        System.out.println("#");

        System.out.println("# ----------- FLOW ACTIONS ------------");
        if (spi.getCurrentFlow() == SpiFlow.PAIRING) {
            System.out.println("# [pair_cancel] - cancel pairing");

            if (spi.getCurrentPairingFlowState().isAwaitingCheckFromPos())
                System.out.println("# [pair_confirm] - confirm pairing code");

            if (spi.getCurrentPairingFlowState().isFinished())
                System.out.println("# [ok] - acknowledge final");
        }

        if (spi.getCurrentFlow() == SpiFlow.TRANSACTION) {
            TransactionFlowState txState = spi.getCurrentTxFlowState();

            if (txState.isAwaitingSignatureCheck()) {
                System.out.println("# [tx_sign_accept] - accept signature");
                System.out.println("# [tx_sign_decline] - decline signature");
            }

            if (!txState.isFinished() && !txState.isAttemptingToCancel())
                System.out.println("# [tx_cancel] - attempt to cancel transaction");

            if (txState.isFinished())
                System.out.println("# [ok] - acknowledge final");
        }

        System.out.println("# [status] - reprint buttons/status");
        System.out.println("# [bye] - exit");
        System.out.println();
    }

    private void printPairingStatus() {
        System.out.println("# --------------- STATUS ------------------");
        System.out.println("# " + posId + " <-> EFTPOS: " + eftposAddress + " #");
        System.out.println("# SPI STATUS: " + spi.getCurrentStatus() + "     FLOW: " + spi.getCurrentFlow() + " #");
        System.out.println("# ----------------TABLES-------------------");
        System.out.println("#    Open Tables: " + tableToBillMapping.size());
        System.out.println("# Bills in Store: " + billsStore.size());
        System.out.println("# Assembly Bills: " + assemblyBillDataStore.size());
        System.out.println("# -----------------------------------------");
        System.out.println("# SPI: v" + Spi.getVersion());
    }

    private void acceptUserInput() {
        final Scanner scanner = new Scanner(System.in);
        boolean bye = false;
        while (!bye && scanner.hasNext()) {
            final String input = scanner.next();
            String[] spInput = input.split(":");
            switch (spInput[0].toLowerCase()) {
                case "open":
                    openTable(spInput[1]);
                    System.out.print("> ");
                    break;

                case "close":
                    closeTable(spInput[1]);
                    System.out.print("> ");
                    break;

                case "add":
                    addToTable(spInput[1], Integer.parseInt(spInput[2]));
                    System.out.print("> ");
                    break;

                case "table":
                    printTable(spInput[1]);
                    System.out.print("> ");
                    break;

                case "bill":
                    printBill(spInput[1]);
                    System.out.print("> ");
                    break;

                case "tables":
                    printTables();
                    System.out.print("> ");
                    break;

                case "purchase":
                    InitiateTxResult pRes = spi.initiatePurchaseTx("purchase-" + System.currentTimeMillis(), Integer.parseInt(spInput[1]), 0, 0, false);
                    if (!pRes.isInitiated()) {
                        System.out.println("# Could not initiate purchase: " + pRes.getMessage() + ". Please retry.");
                    }
                    break;

                case "refund":
                case "yuck":
                    InitiateTxResult yuckRes = spi.initiateRefundTx("yuck-" + System.currentTimeMillis(), 1000);
                    if (!yuckRes.isInitiated()) {
                        System.out.println("# Could not initiate refund: " + yuckRes.getMessage() + ". Please retry.");
                    }
                    break;

                case "pos_id":
                    SystemHelper.clearConsole();
                    if (spi.setPosId(spInput[1])) {
                        posId = spInput[1];
                        System.out.println("## -> POS ID now set to " + posId);
                    } else {
                        System.out.println("## -> Could not set POS ID");
                    }
                    printStatusAndActions();
                    System.out.print("> ");
                    break;

                case "eftpos_address":
                    SystemHelper.clearConsole();
                    if (spi.setEftposAddress(spInput[1])) {
                        eftposAddress = spInput[1];
                        System.out.println("## -> EFTPOS address now set to " + eftposAddress);
                    } else {
                        System.out.println("## -> Could not set EFTPOS address");
                    }
                    printStatusAndActions();
                    System.out.print("> ");
                    break;

                case "pair":
                    boolean pairingInited = spi.pair();
                    if (!pairingInited) System.out.println("## -> Could not start pairing. Check settings.");
                    break;

                case "pair_cancel":
                    spi.pairingCancel();
                    break;

                case "pair_confirm":
                    spi.pairingConfirmCode();
                    break;

                case "unpair":
                    spi.unpair();
                    break;

                case "tx_sign_accept":
                    spi.acceptSignature(true);
                    break;

                case "tx_sign_decline":
                    spi.acceptSignature(false);
                    break;

                case "tx_cancel":
                    spi.cancelTransaction();
                    break;

                case "settle":
                    InitiateTxResult settleRes = spi.initiateSettleTx(RequestIdHelper.id("settle"));
                    if (!settleRes.isInitiated()) {
                        System.out.println("# Could not initiate settlement: " + settleRes.getMessage() + ". Please retry.");
                    }
                    break;

                case "ok":
                    SystemHelper.clearConsole();
                    spi.ackFlowEndedAndBackToIdle();
                    printStatusAndActions();
                    System.out.print("> ");
                    break;

                case "status":
                    SystemHelper.clearConsole();
                    printStatusAndActions();
                    break;

                case "bye":
                    bye = true;
                    break;

                case "":
                    System.out.print("> ");
                    break;

                default:
                    System.out.println("# I don't understand. Sorry.");
                    System.out.print("> ");
                    break;
            }
        }
        System.out.println("# BaBye!");
        persistState();

        // Dump arguments for next session
        System.out.println(posId + ":" + eftposAddress +
                (spiSecrets == null ? "" :
                        ":" + spiSecrets.getEncKey() +
                                ":" + spiSecrets.getHmacKey()));
    }

    //endregion

    //region My Pos Functions

    private void openTable(String tableId) {
        if (tableToBillMapping.containsKey(tableId)) {
            System.out.println("Table already open: " + billsStore.get(tableToBillMapping.get(tableId)));
            return;
        }

        Bill newBill = new Bill();
        newBill.billId = newBillId();
        newBill.tableId = tableId;
        billsStore.put(newBill.billId, newBill);
        tableToBillMapping.put(newBill.tableId, newBill.billId);
        System.out.println("Opened: " + newBill);
    }

    private void addToTable(String tableId, int amountCents) {
        if (!tableToBillMapping.containsKey(tableId)) {
            System.out.println("Table not open.");
            return;
        }
        Bill bill = billsStore.get(tableToBillMapping.get(tableId));
        bill.totalAmount += amountCents;
        bill.outstandingAmount += amountCents;
        System.out.println("Updated: " + bill);
    }

    private void closeTable(String tableId) {
        if (!tableToBillMapping.containsKey(tableId)) {
            System.out.println("Table not open.");
            return;
        }
        Bill bill = billsStore.get(tableToBillMapping.get(tableId));
        if (bill.outstandingAmount > 0) {
            System.out.println("Bill not paid yet: " + bill);
            return;
        }
        tableToBillMapping.remove(tableId);
        assemblyBillDataStore.remove(bill.billId);
        System.out.println("Closed: " + bill);
    }

    private void printTable(String tableId) {
        if (!tableToBillMapping.containsKey(tableId)) {
            System.out.println("Table not open.");
            return;
        }
        printBill(tableToBillMapping.get(tableId));
    }

    private void printTables() {
        if (tableToBillMapping.size() > 0) {
            System.out.println("# Open tables: " + StringUtils.join(tableToBillMapping.keySet(), ","));
        } else {
            System.out.println("# No open tables.");
        }
        if (billsStore.size() > 0) {
            System.out.println("# My bills store: " + StringUtils.join(billsStore.keySet(), ","));
        }
        if (assemblyBillDataStore.size() > 0) {
            System.out.println("# Assembly bills data: " + StringUtils.join(assemblyBillDataStore.keySet(), ","));
        }
    }

    private void printBill(String billId) {
        if (!billsStore.containsKey(billId)) {
            System.out.println("Bill not found.");
            return;
        }
        System.out.println("Bill: " + billsStore.get(billId));
    }

    private String newBillId() {
        return Long.toString(System.currentTimeMillis());
    }

    //endregion

    //region Persistence

    private void loadPersistedState(String[] args) {
        if (args.length < 1) return;

        // We were given something, at least POS ID and PIN pad address...
        final String[] argSplit = args[0].split(":");
        posId = argSplit[0];

        if (argSplit.length < 2) return;
        eftposAddress = argSplit[1];

        // Let's see if we were given existing secrets as well.
        if (argSplit.length < 4) return;
        spiSecrets = new Secrets(argSplit[2], argSplit[3]);

        if (new File("tableToBillMapping.bin").exists()) {
            tableToBillMapping = Pos.readFromBinaryFile("tableToBillMapping.bin");
            billsStore = Pos.readFromBinaryFile("billsStore.bin");
            assemblyBillDataStore = Pos.readFromBinaryFile("assemblyBillDataStore.bin");
        }
    }

    private void persistState() {
        writeToBinaryFile("tableToBillMapping.bin", tableToBillMapping, false);
        writeToBinaryFile("billsStore.bin", billsStore, false);
        writeToBinaryFile("assemblyBillDataStore.bin", assemblyBillDataStore, false);
    }

    private static <T> void writeToBinaryFile(String filePath, T objectToWrite, boolean append) {
        try {
            FileOutputStream fos = new FileOutputStream(filePath, append);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(objectToWrite);
            oos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    //endregion

    //region My Model

    static class Bill implements Serializable {
        public String billId;
        public String tableId;
        public int totalAmount = 0;
        public int outstandingAmount = 0;
        public int tippedAmount = 0;

        @Override
        public String toString() {
            return String.format("%s - Table:%s Total:%.2f Outstanding:%.2f Tips:%.2f",
                    billId, tableId, totalAmount / 100.0, outstandingAmount / 100.0, tippedAmount / 100.0);
        }
    }

    //endregion

}
