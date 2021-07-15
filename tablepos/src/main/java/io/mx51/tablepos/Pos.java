package io.mx51.tablepos;

import io.mx51.spi.Spi;
import io.mx51.spi.SpiPayAtTable;
import io.mx51.spi.model.*;
import io.mx51.spi.util.RequestIdHelper;
import io.mx51.utils.SystemHelper;
import com.google.gson.Gson;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * NOTE: THIS PROJECT USES THE 2.1.x of the SPI Client Library
 * <p>
 * This specific POS shows you how to integrate the pay-at-table features of the SPI protocol.
 * If you're just starting, we recommend you start with KebabPos. It goes through the basics.
 */
public class Pos {

    private static Logger LOG = LogManager.getLogger("spi");

    private static final Gson GSON = new Gson();

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

    List<String> allowedOperatorIdList = new ArrayList<>();

    private Spi spi;
    private SpiPayAtTable pat;
    private String posId = "TABLEPOS1";
    private String eftposAddress = "192.168.1.9";
    private Secrets spiSecrets = null;
    private String serialNumber = "";
    private TransactionOptions options;

    private static final Pattern REGEX_ITEMS_FOR_TABLEID = Pattern.compile("[a-zA-Z0-9]*$");

    public static void main(String[] args) {
        new Pos().start(args);
    }

    private void start(String[] args) {
        LOG.info("Starting TablePos...");
        loadPersistedState(args);

        try {
            // This is how you instantiate SPI while checking for JDK compatibility.
            spi = new Spi(posId, serialNumber, eftposAddress, spiSecrets); // It is ok to not have the secrets yet to start with.
            spi.setPosInfo("assembly", "2.6.3");
            options = new TransactionOptions();
        } catch (Spi.CompatibilityException e) {
            System.out.println("# ");
            System.out.println("# Compatibility check failed: " + e.getCause().getMessage());
            System.out.println("# Please ensure you followed all the configuration steps on your machine.");
            System.out.println("# ");
            return;
        } catch (Exception ex) {
            System.out.println("# ");
            System.out.println(ex.getMessage());
            System.out.println("# ");
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
        enablePayAtTableConfig();
        pat.setGetBillStatusDelegate(new SpiPayAtTable.GetBillStatusDelegate() {
            @Override
            public BillStatusResponse getBillStatus(String billId, String tableId, String operatorId, boolean paymentFlowSatarted) {
                return payAtTableGetBillDetails(billId, tableId, operatorId, paymentFlowSatarted);
            }
        });
        pat.setBillPaymentReceivedDelegate(new SpiPayAtTable.BillPaymentReceivedDelegate() {
            @Override
            public BillStatusResponse getBillReceived(BillPayment billPayment, String updatedBillData) {
                return payAtTableBillPaymentReceived(billPayment, updatedBillData);
            }
        });
        pat.setBillPaymentFlowEndedDelegate(new SpiPayAtTable.BillPaymentFlowEndedDelegate() {
            @Override
            public void getBillPaymentFlowEnded(Message message) {
                payAtTableBillPaymentFlowEnded(message);
            }
        });
        pat.setGetOpenTablesDelegate(new SpiPayAtTable.GetOpenTablesDelegate() {
            @Override
            public GetOpenTablesResponse getOpenTables(String operatorId) {
                return payAtTableGetOpenTables(operatorId);
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

    private BillStatusResponse payAtTableGetBillDetails(String billId, String tableId, String operatorId, boolean paymentFlowStarted) {
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

        if (myBill.locked && paymentFlowStarted) {
            System.out.println("# Table is Locked!");
            BillStatusResponse response = new BillStatusResponse();
            response.setResult(BillRetrievalResult.INVALID_TABLE_ID);
            return response;
        }

        myBill.locked = paymentFlowStarted;

        BillStatusResponse response = new BillStatusResponse();
        response.setResult(BillRetrievalResult.SUCCESS);
        response.setBillId(billId);
        response.setTableId(tableId);
        response.setOperatorId(operatorId);
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
        bill.surchargeAmount += billPayment.getSurchargeAmount();

        if (bill.outstandingAmount == 0) {
            bill.locked = false;
        } else {
            bill.locked = true;
        }


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

    private void payAtTableBillPaymentFlowEnded(Message message) {
        BillPaymentFlowEndedResponse billPaymentFlowEndedResponse = new BillPaymentFlowEndedResponse(message);

        if (!billsStore.containsKey(billPaymentFlowEndedResponse.getTableId())) {
            //Incorrect Table Id
            return;
        }

        Bill myBill = billsStore.get(billPaymentFlowEndedResponse.getBillId());
        myBill.locked = false;

        System.out.println("Bill Id: " + billPaymentFlowEndedResponse.getBillId() +
                ", Table Id: " + billPaymentFlowEndedResponse.getTableId() +
                ", Operator Id: " + billPaymentFlowEndedResponse.getOperatorId() +
                ", Bill OutStanding Amount: $" + billPaymentFlowEndedResponse.getBillOutstandingAmount() / 100.0 +
                ", Bill Total Amount: $" + billPaymentFlowEndedResponse.getBillTotalAmount() / 100.0 +
                ", Card Total Count: " + billPaymentFlowEndedResponse.getCardTotalCount() +
                ", Card Total Amount: $" + billPaymentFlowEndedResponse.getCardTotalAmount() / 100.0 +
                ", Cash Total Count: " + billPaymentFlowEndedResponse.getCashTotalCount() +
                ", Cash Total Amount: $" + billPaymentFlowEndedResponse.getCashTotalAmount() / 100.0 +
                ", Locked: " + myBill.locked);
        System.out.println("> ");
    }

    private GetOpenTablesResponse payAtTableGetOpenTables(String operatorId) {
        List<OpenTablesEntry> openTablesEntries = new ArrayList<>();
        boolean isOpenTables = false;

        if (tableToBillMapping.size() > 0) {
            for (Map.Entry<String, String> item : tableToBillMapping.entrySet()) {
                if (billsStore.get(item.getValue()).operatorId.equals(operatorId) && billsStore.get(item.getValue()).outstandingAmount > 0 && !billsStore.get(item.getValue()).locked) {
                    if (!isOpenTables) {
                        System.out.println("#    Open Tables: ");
                        isOpenTables = true;
                    }

                    OpenTablesEntry openTablesEntry = new OpenTablesEntry(item.getKey(), billsStore.get(item.getValue()).label, billsStore.get(item
                            .getValue()).outstandingAmount);

                    System.out.println("Table Id: " + item.getKey() +
                            ", Label: " + billsStore.get(item.getValue()).label +
                            ", Outstanding Amount: $" + billsStore.get(item.getValue()).outstandingAmount / 100.0);

                    openTablesEntries.add(openTablesEntry);
                }
            }
        }

        if (!isOpenTables) {
            // No Open Tables.
        }

        GetOpenTablesResponse response = new GetOpenTablesResponse();
        response.setOpenTablesEntries(openTablesEntries);
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
        System.out.println("# [open:12:3:vip:true/false] - start a new bill for table 12, operator Id 3, Label is vip, Lock is false");
        System.out.println("# [add:12:1000]              - add $10.00 to the bill of table 12");
        System.out.println("# [close:12]                 - close table 12");
        System.out.println("# [lock:12:true/false]       - Lock/Unlock table 12");
        System.out.println("# [tables]                   - list open tables");
        System.out.println("# [table:12]                 - print current bill for table 12");
        System.out.println("# [bill:9876789876]          - print bill with ID 9876789876");
        System.out.println("#");

        if (spi.getCurrentFlow() == SpiFlow.IDLE) {
            System.out.println("# ----------- TABLE CONFIG ------------");
            System.out.println("# [pat_all_enable]                     - enable/disable pay at table");
            System.out.println("# [pat_enabled:true/false]             - enable/disable pay at table");
            System.out.println("# [operatorid_enabled:true/false]      - enable/disable operator id property");
            System.out.println("# [set_allowed_operatorid:2]           - set allowed operator id");
            System.out.println("# [equal_split:true/false]             - enable/disable equal split property");
            System.out.println("# [split_by_amount:true/false]         - enable/disable split by amount property");
            System.out.println("# [tipping:true/false]                 - enable/disable tipping property");
            System.out.println("# [summary_report:true/false]          - enable/disable operator id");
            System.out.println("# [set_label_operatorid:Operator Id]   - set operatorid label");
            System.out.println("# [set_label_tableid:Table Number]     - set tableid label");
            System.out.println("# [set_label_paybutton:Pay At Table]   - set pay button label");
            System.out.println("# [table_retrieval_enabled:true/false] - enable/disable table retrieval button");
            System.out.println("# ----------- OTHER ACTIONS ------------");
            System.out.println("# [purchase:1200]     - quick purchase transaction");
            System.out.println("# [refund:1000:false] - hand out a refund, suppress password is false, $10.00!");
            System.out.println("# [settle]            - initiate settlement");
            System.out.println("# ----------- PRINTING CONFIG ------------");
            System.out.println("# [rcpt_from_eftpos:true/false]     - offer customer receipt from EFTPOS");
            System.out.println("# [sig_flow_from_eftpos:true/false] - signature flow to be handled by EFTPOS");
            System.out.println("# [print_merchant_copy:true/false]  - add printing of footers and headers onto the existing EFTPOS receipt provided by payment application");
            System.out.println("# [receipt_header:myheader]   - set header for the receipt");
            System.out.println("# [receipt_footer:myfooter]   - set footer for the receipt");
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
        System.out.println("# Open Tables   : " + tableToBillMapping.size());
        System.out.println("# Bills in Store: " + billsStore.size());
        System.out.println("# Assembly Bills: " + assemblyBillDataStore.size());
        System.out.println("# -----------------------------------------");
        System.out.println("# SPI: v" + Spi.getVersion());
    }

    private void acceptUserInput() {
        final Scanner scanner = new Scanner(System.in);
        boolean bye = false;
        while (!bye && scanner.hasNextLine()) {
            final String input = scanner.nextLine();
            String[] spInput = input.split(":");
            switch (spInput[0].toLowerCase()) {
                case "open":
                    if (spInput.length != 5) {
                        System.out.print("Missing Parameters!");
                    } else {
                        openTable(spInput[1], spInput[2], spInput[3], Boolean.parseBoolean(spInput[4]));
                    }
                    System.out.print("> ");
                    break;

                case "close":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        closeTable(spInput[1]);
                    }
                    System.out.print("> ");
                    break;

                case "lock":
                    if (spInput.length != 3) {
                        System.out.print("Missing Parameters!");
                    } else {
                        lockTable(spInput[1], Boolean.parseBoolean(spInput[2]));
                    }
                    System.out.print("> ");
                    break;

                case "add":
                    if (spInput.length != 3) {
                        System.out.print("Missing Parameters!");
                    } else {
                        addToTable(spInput[1], Integer.parseInt(spInput[2]));
                    }
                    System.out.print("> ");
                    break;

                case "table":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        printTable(spInput[1]);
                    }
                    System.out.print("> ");
                    break;

                case "bill":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        printBill(spInput[1]);
                    }
                    System.out.print("> ");
                    break;

                case "tables":
                    printTables();
                    System.out.print("> ");
                    break;

                case "purchase":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        InitiateTxResult pRes = spi.initiatePurchaseTx("purchase-" + System.currentTimeMillis(), Integer.parseInt(spInput[1]), 0, 0, false, options);
                        if (!pRes.isInitiated()) {
                            System.out.println("# Could not initiate purchase: " + pRes.getMessage() + ". Please retry.");
                        }
                    }
                    System.out.print("> ");
                    break;

                case "refund":
                    if (spInput.length != 3) {
                        System.out.print("Missing Parameters!");
                    } else {
                        InitiateTxResult yuckRes = spi.initiateRefundTx("yuck-" + System.currentTimeMillis(), Integer.parseInt(spInput[1]), Boolean.parseBoolean(spInput[2]), options);
                        if (!yuckRes.isInitiated()) {
                            System.out.println("# Could not initiate refund: " + yuckRes.getMessage() + ". Please retry.");
                        }
                    }
                    System.out.print("> ");
                    break;

                case "rcpt_from_eftpos":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        spi.getConfig().setPromptForCustomerCopyOnEftpos("true".equalsIgnoreCase(spInput[1]));
                        SystemHelper.clearConsole();
                        spi.ackFlowEndedAndBackToIdle();
                        printStatusAndActions();
                    }
                    System.out.print("> ");
                    break;

                case "sig_flow_from_eftpos":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        spi.getConfig().setSignatureFlowOnEftpos("true".equalsIgnoreCase(spInput[1]));
                        SystemHelper.clearConsole();
                        spi.ackFlowEndedAndBackToIdle();
                        printStatusAndActions();
                    }
                    System.out.print("> ");
                    break;

                case "print_merchant_copy":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        spi.getConfig().setPrintMerchantCopy("true".equalsIgnoreCase(spInput[1]));
                        SystemHelper.clearConsole();
                        spi.ackFlowEndedAndBackToIdle();
                        printStatusAndActions();
                    }
                    System.out.print("> ");
                    break;

                case "receipt_header":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        String inputHeader = spInput[1].replace("\\r\\n", "\r\n");
                        inputHeader = inputHeader.replace("\\\\", "\\");
                        options.setCustomerReceiptHeader(inputHeader);
                        options.setMerchantReceiptHeader(inputHeader);
                        SystemHelper.clearConsole();
                        spi.ackFlowEndedAndBackToIdle();
                        printStatusAndActions();
                    }
                    System.out.print("> ");
                    break;

                case "receipt_footer":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        String inputFooter = spInput[1].replace("\\r\\n", "\r\n");
                        inputFooter = inputFooter.replace("\\\\", "\\");
                        options.setCustomerReceiptFooter(inputFooter);
                        options.setMerchantReceiptFooter(inputFooter);
                        SystemHelper.clearConsole();
                        spi.ackFlowEndedAndBackToIdle();
                        printStatusAndActions();
                    }
                    System.out.print("> ");
                    break;

                case "pos_id":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        SystemHelper.clearConsole();
                        if (spi.setPosId(spInput[1])) {
                            posId = spInput[1];
                            System.out.println("## -> POS ID now set to " + posId);
                        } else {
                            System.out.println("## -> Could not set POS ID");
                        }
                        printStatusAndActions();
                    }
                    System.out.print("> ");
                    break;

                case "eftpos_address":
                    if (spInput.length < 2 || spInput.length > 3) {
                        System.out.print("Missing Parameters!");
                    } else {
                        SystemHelper.clearConsole();
                        eftposAddress = spInput[1] + ":" + spInput[2];
                        if (spi.setEftposAddress(eftposAddress)) {
                            System.out.println("## -> EFTPOS address now set to " + eftposAddress);
                        } else {
                            System.out.println("## -> Could not set EFTPOS address");
                        }
                        printStatusAndActions();
                    }
                    System.out.print("> ");
                    break;

                case "pat_enabled":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        pat.getConfig().setPayAtTableEnabled(Boolean.parseBoolean(spInput[1]));
                        pat.pushPayAtTableConfig();
                    }
                    break;

                case "pat_all_enable":
                    enablePayAtTableConfig();
                    pat.pushPayAtTableConfig();
                    break;

                case "operatorid_enabled":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        pat.getConfig().setOperatorIdEnabled(Boolean.parseBoolean(spInput[1]));
                        pat.pushPayAtTableConfig();
                    }
                    break;

                case "equal_split":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        pat.getConfig().setEqualSplitEnabled(Boolean.parseBoolean(spInput[1]));
                        pat.pushPayAtTableConfig();
                    }
                    break;

                case "split_by_amount":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        pat.getConfig().setSplitByAmountEnabled(Boolean.parseBoolean(spInput[1]));
                        pat.pushPayAtTableConfig();
                    }
                    break;

                case "tipping":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        pat.getConfig().setTippingEnabled(Boolean.parseBoolean(spInput[1]));
                        pat.pushPayAtTableConfig();
                    }
                    break;

                case "summary_report":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        pat.getConfig().setSummaryReportEnabled(Boolean.parseBoolean(spInput[1]));
                        pat.pushPayAtTableConfig();
                    }
                    break;

                case "set_allowed_operatorid":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        allowedOperatorIdList.add(spInput[1]);
                        pat.getConfig().setAllowedOperatorIds(allowedOperatorIdList);
                        pat.pushPayAtTableConfig();
                    }
                    break;

                case "set_label_operatorid":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        pat.getConfig().setLabelOperatorId(spInput[1]);
                        pat.pushPayAtTableConfig();
                    }
                    break;

                case "set_label_tableid":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        pat.getConfig().setLabelTableId(spInput[1]);
                        pat.pushPayAtTableConfig();
                    }
                    break;

                case "table_retrieval_enabled":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        pat.getConfig().setTableRetrievalEnabled(Boolean.parseBoolean(spInput[1]));
                        pat.pushPayAtTableConfig();
                    }
                    break;

                case "set_label_paybutton":
                    if (spInput.length != 2) {
                        System.out.print("Missing Parameters!");
                    } else {
                        pat.getConfig().setLabelPayButton(spInput[1]);
                        pat.pushPayAtTableConfig();
                    }
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
                    InitiateTxResult settleRes = spi.initiateSettleTx(RequestIdHelper.id("settle"), options);
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

    private void openTable(String tableId, String operatorId, String label, boolean locked) {
        if (!REGEX_ITEMS_FOR_TABLEID.matcher(tableId).matches()) {
            System.out.println("The Table Id cannot include special characters");
            return;
        }

        if (tableToBillMapping.containsKey(tableId)) {
            System.out.println("Table already open: " + billsStore.get(tableToBillMapping.get(tableId)));
            return;
        }

        Bill newBill = new Bill();
        newBill.billId = newBillId();
        newBill.tableId = tableId;
        newBill.operatorId = operatorId;
        newBill.label = label;
        newBill.locked = locked;
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

        if (bill.locked) {
            System.out.println("Table is Locked!");
            return;
        }

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

        if (bill.locked) {
            System.out.println("Table is Locked!");
            return;
        }

        if (bill.outstandingAmount > 0) {
            System.out.println("Bill not paid yet: " + bill);
            return;
        }
        tableToBillMapping.remove(tableId);
        assemblyBillDataStore.remove(bill.billId);
        System.out.println("Closed: " + bill);
    }

    private void lockTable(String tableId, boolean locked) {
        if (!tableToBillMapping.containsKey(tableId)) {
            System.out.println("Table not open.");
            return;
        }

        Bill bill = billsStore.get(tableToBillMapping.get(tableId));
        bill.locked = locked;

        if (locked) {
            System.out.println("Locked: " + bill);
        } else {
            System.out.println("UnLocked: " + bill);
        }
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

    private void enablePayAtTableConfig() {
        pat.getConfig().setPayAtTableEnabled(true);
        pat.getConfig().setOperatorIdEnabled(true);
        pat.getConfig().setAllowedOperatorIds(new ArrayList<String>());
        pat.getConfig().setEqualSplitEnabled(true);
        pat.getConfig().setSplitByAmountEnabled(true);
        pat.getConfig().setSummaryReportEnabled(true);
        pat.getConfig().setTippingEnabled(true);
        pat.getConfig().setLabelOperatorId("Operator ID");
        pat.getConfig().setLabelPayButton("Pay at Table");
        pat.getConfig().setLabelTableId("Table Number");
        pat.getConfig().setTableRetrievalEnabled(true);
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
        public String operatorId;
        public String label;
        public int totalAmount = 0;
        public int outstandingAmount = 0;
        public int tippedAmount = 0;
        public int surchargeAmount = 0;
        public boolean locked;

        @Override
        public String toString() {
            return String.format("%s - Table:%s Operator Id:%s Label:%s Total:%.2f Outstanding:%.2f Tips:%.2f Surcharge:%.2f Locked:%b",
                    billId, tableId, operatorId, label, totalAmount / 100.0, outstandingAmount / 100.0, tippedAmount / 100.0, surchargeAmount / 100.0, locked);
        }
    }

//endregion

}
