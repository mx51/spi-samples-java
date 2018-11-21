package com.assemblypayments.kebabpos;

import com.assemblypayments.spi.Spi;
import com.assemblypayments.spi.model.*;
import com.assemblypayments.spi.util.RequestIdHelper;
import com.assemblypayments.utils.SystemHelper;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

import static com.assemblypayments.spi.model.SpiFlow.TRANSACTION;
import static com.assemblypayments.spi.model.TransactionType.PURCHASE;

/**
 * NOTE: THIS PROJECT USES THE 2.1.x of the SPI Client Library
 * <p>
 * This is your POS. To integrate with SPI, you need to instantiate a Spi Object
 * and interact with it.
 * Primarily you need to implement 3 things.
 * 1. Settings Screen
 * 2. Pairing Flow Screen
 * 3. Transaction Flow screen
 */
public class Pos {

    private static final Logger LOG = LogManager.getLogger("spi");

    private Spi spi;
    private String posId = "KEBABPOS1";
    private String eftposAddress = "192.168.1.1";
    private Secrets spiSecrets = null;
    private TransactionOptions options;

    private String[] lastCmd = new String[0];

    public static void main(String[] args) {
        new Pos().start(args);
    }

    private void start(String[] args) {
        LOG.info("Starting KebabPos...");
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

        try {
            spi.setPosInfo("assembly", "2.3.0");
            options = new TransactionOptions();
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
            spi.start();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return;
        }

        SystemHelper.clearConsole();
        System.out.println("# Welcome to KebabPos !");
        printStatusAndActions();
        System.out.print("> ");
        acceptUserInput();

        // Cleanup
        spi.dispose();
    }

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
        System.out.println("# --> SPI Status Changed: " + status);
        printStatusAndActions();
        System.out.print("> ");
    }

    private void printStatusAndActions() {
        printFlowInfo();

        printActions();

        printPairingStatus();
    }

    private void printFlowInfo() {
        switch (spi.getCurrentFlow()) {
            case PAIRING:
                PairingFlowState pairingState = spi.getCurrentPairingFlowState();
                System.out.println("### PAIRING PROCESS UPDATE ###");
                System.out.println("# " + pairingState.getMessage());
                System.out.println("# Finished? " + pairingState.isFinished());
                System.out.println("# Successful? " + pairingState.isSuccessful());
                System.out.println("# Confirmation code: " + pairingState.getConfirmationCode());
                System.out.println("# Waiting confirm from EFTPOS? " + pairingState.isAwaitingCheckFromEftpos());
                System.out.println("# Waiting confirm from POS? " + pairingState.isAwaitingCheckFromPos());
                break;

            case TRANSACTION:
                TransactionFlowState txState = spi.getCurrentTxFlowState();
                System.out.println("### TX PROCESS UPDATE ###");
                System.out.println("# " + txState.getDisplayMessage());
                System.out.println("# Id: " + txState.getPosRefId());
                System.out.println("# Type: " + txState.getType());
                System.out.println("# Amount: " + (txState.getAmountCents() / 100.0));
                System.out.println("# Waiting for signature: " + txState.isAwaitingSignatureCheck());
                System.out.println("# Attempting to cancel: " + txState.isAttemptingToCancel());
                System.out.println("# Finished: " + txState.isFinished());
                System.out.println("# Success: " + txState.getSuccess());

                if (txState.isAwaitingSignatureCheck()) {
                    // We need to print the receipt for the customer to sign.
                    System.out.println("# RECEIPT TO PRINT FOR SIGNATURE");
                    System.out.println(txState.getSignatureRequiredMessage().getMerchantReceipt().trim());
                }

                if (txState.isAwaitingPhoneForAuth()) {
                    System.out.println("# PHONE FOR AUTH DETAILS:");
                    System.out.println("# CALL: " + txState.getPhoneForAuthRequiredMessage().getPhoneNumber());
                    System.out.println("# QUOTE: Merchant ID: " + txState.getPhoneForAuthRequiredMessage().getMerchantId());
                }

                if (txState.isFinished()) {
                    System.out.println();
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
                            System.out.println("# CAN'T HANDLE TX TYPE: " + txState.getType());
                            break;
                    }
                }
                break;
            case IDLE:
                break;
        }

        System.out.println();
    }

    private void handleFinishedPurchase(TransactionFlowState txState) {
        PurchaseResponse purchaseResponse;
        switch (txState.getSuccess()) {
            case SUCCESS:
                System.out.println("# WOOHOO - WE GOT PAID!");
                purchaseResponse = new PurchaseResponse(txState.getResponse());
                System.out.println("# Response: " + purchaseResponse.getResponseText());
                System.out.println("# RRN: " + purchaseResponse.getRRN());
                System.out.println("# Scheme: " + purchaseResponse.getSchemeName());
                System.out.println("# Customer receipt:");
                System.out.println(!purchaseResponse.wasCustomerReceiptPrinted() ? purchaseResponse.getCustomerReceipt().trim() : "# PRINTED FROM EFTPOS");
                System.out.println("# PURCHASE: " + purchaseResponse.getPurchaseAmount());
                System.out.println("# TIP: " + purchaseResponse.getTipAmount());
                System.out.println("# CASHOUT: " + purchaseResponse.getCashoutAmount());
                System.out.println("# BANKED NON-CASH AMOUNT: " + purchaseResponse.getBankNonCashAmount());
                System.out.println("# BANKED CASH AMOUNT: " + purchaseResponse.getBankCashAmount());
                System.out.println("# BANKED SURCHARGE AMOUNT: " + purchaseResponse.getSurchargeAmount());
                break;
            case FAILED:
                System.out.println("# WE DID NOT GET PAID :(");
                System.out.println("# Error: " + txState.getResponse().getError());
                System.out.println("# Error Detail: " + txState.getResponse().getErrorDetail());
                if (txState.getResponse() != null) {
                    purchaseResponse = new PurchaseResponse(txState.getResponse());
                    System.out.println("# Response: " + purchaseResponse.getResponseText());
                    System.out.println("# RRN: " + purchaseResponse.getRRN());
                    System.out.println("# Scheme: " + purchaseResponse.getSchemeName());
                    System.out.println("# Customer receipt:");
                    System.out.println(!purchaseResponse.wasCustomerReceiptPrinted()
                            ? purchaseResponse.getCustomerReceipt().trim()
                            : "# PRINTED FROM EFTPOS");
                }
                break;
            case UNKNOWN:
                System.out.println("# WE'RE NOT QUITE SURE WHETHER WE GOT PAID OR NOT :/");
                System.out.println("# CHECK THE LAST TRANSACTION ON THE EFTPOS ITSELF FROM THE APPROPRIATE MENU ITEM.");
                System.out.println("# IF YOU CONFIRM THAT THE CUSTOMER PAID, CLOSE THE ORDER.");
                System.out.println("# OTHERWISE, RETRY THE PAYMENT FROM SCRATCH.");
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private void handleFinishedRefund(TransactionFlowState txState) {
        RefundResponse refundResponse;
        switch (txState.getSuccess()) {
            case SUCCESS:
                System.out.println("# REFUND GIVEN- OH WELL!");
                refundResponse = new RefundResponse(txState.getResponse());
                System.out.println("# Response: " + refundResponse.getResponseText());
                System.out.println("# RRN: " + refundResponse.getRRN());
                System.out.println("# Scheme: " + refundResponse.getSchemeName());
                System.out.println("# Customer receipt:");
                System.out.println(!refundResponse.wasCustomerReceiptPrinted() ? refundResponse.getCustomerReceipt().trim() : "# PRINTED FROM EFTPOS");
                System.out.println("# REFUNDED AMOUNT: " + refundResponse.getRefundAmount());
                break;
            case FAILED:
                System.out.println("# REFUND FAILED!");
                System.out.println("# Error: " + txState.getResponse().getError());
                System.out.println("# Error Detail: " + txState.getResponse().getErrorDetail());
                if (txState.getResponse() != null) {
                    refundResponse = new RefundResponse(txState.getResponse());
                    System.out.println("# Response: " + refundResponse.getResponseText());
                    System.out.println("# RRN: " + refundResponse.getRRN());
                    System.out.println("# Scheme: " + refundResponse.getSchemeName());
                    System.out.println("# Customer receipt:");
                    System.out.println(!refundResponse.wasCustomerReceiptPrinted() ? refundResponse.getCustomerReceipt().trim() : "# PRINTED FROM EFTPOS");
                }
                break;
            case UNKNOWN:
                System.out.println("# WE'RE NOT QUITE SURE WHETHER THE REFUND WENT THROUGH OR NOT :/");
                System.out.println("# CHECK THE LAST TRANSACTION ON THE EFTPOS ITSELF FROM THE APPROPRIATE MENU ITEM.");
                System.out.println("# YOU CAN THE TAKE THE APPROPRIATE ACTION.");
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private void handleFinishedCashout(TransactionFlowState txState) {
        CashoutOnlyResponse cashoutResponse;
        switch (txState.getSuccess()) {
            case SUCCESS:
                System.out.println("# CASH-OUT SUCCESSFUL - HAND THEM THE CASH!");
                cashoutResponse = new CashoutOnlyResponse(txState.getResponse());
                System.out.println("# Response: " + cashoutResponse.getResponseText());
                System.out.println("# RRN: " + cashoutResponse.getRRN());
                System.out.println("# Scheme: " + cashoutResponse.getSchemeName());
                System.out.println("# Customer receipt:");
                System.out.println(!cashoutResponse.wasCustomerReceiptPrinted() ? cashoutResponse.getCustomerReceipt().trim() : "# PRINTED FROM EFTPOS");
                System.out.println("# CASHOUT: " + cashoutResponse.getCashoutAmount());
                System.out.println("# BANKED NON-CASH AMOUNT: " + cashoutResponse.getBankNonCashAmount());
                System.out.println("# BANKED CASH AMOUNT: " + cashoutResponse.getBankCashAmount());
                break;
            case FAILED:
                System.out.println("# CASHOUT FAILED!");
                System.out.println("# Error: " + txState.getResponse().getError());
                System.out.println("# Error detail: " + txState.getResponse().getErrorDetail());
                if (txState.getResponse() != null) {
                    cashoutResponse = new CashoutOnlyResponse(txState.getResponse());
                    System.out.println("# Response: " + cashoutResponse.getResponseText());
                    System.out.println("# RRN: " + cashoutResponse.getRRN());
                    System.out.println("# Scheme: " + cashoutResponse.getSchemeName());
                    System.out.println("# Customer receipt:");
                    System.out.println(cashoutResponse.getCustomerReceipt().trim());
                }
                break;
            case UNKNOWN:
                System.out.println("# WE'RE NOT QUITE SURE WHETHER THE CASHOUT WENT THROUGH OR NOT :/");
                System.out.println("# CHECK THE LAST TRANSACTION ON THE EFTPOS ITSELF FROM THE APPROPRIATE MENU ITEM.");
                System.out.println("# YOU CAN THE TAKE THE APPROPRIATE ACTION.");
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
                System.out.println("# WOOHOO - WE GOT MOTO-PAID!");
                motoResponse = new MotoPurchaseResponse(txState.getResponse());
                purchaseResponse = motoResponse.getPurchaseResponse();
                System.out.println("# Response: " + purchaseResponse.getResponseText());
                System.out.println("# RRN: " + purchaseResponse.getRRN());
                System.out.println("# Scheme: " + purchaseResponse.getSchemeName());
                System.out.println("# Card entry: " + purchaseResponse.getCardEntry());
                System.out.println("# Customer receipt:");
                System.out.println(!purchaseResponse.wasCustomerReceiptPrinted() ? purchaseResponse.getCustomerReceipt().trim() : "# PRINTED FROM EFTPOS");
                System.out.println("# PURCHASE: " + purchaseResponse.getPurchaseAmount());
                System.out.println("# BANKED NON-CASH AMOUNT: " + purchaseResponse.getBankNonCashAmount());
                System.out.println("# BANKED CASH AMOUNT: " + purchaseResponse.getBankCashAmount());
                System.out.println("# BANKED SURCHARGE AMOUNT: " + purchaseResponse.getSurchargeAmount());
                break;
            case FAILED:
                System.out.println("# WE DID NOT GET MOTO-PAID :(");
                System.out.println("# Error: " + txState.getResponse().getError());
                System.out.println("# Error detail: " + txState.getResponse().getErrorDetail());
                if (txState.getResponse() != null) {
                    motoResponse = new MotoPurchaseResponse(txState.getResponse());
                    purchaseResponse = motoResponse.getPurchaseResponse();
                    System.out.println("# Response: " + purchaseResponse.getResponseText());
                    System.out.println("# RRN: " + purchaseResponse.getRRN());
                    System.out.println("# Scheme: " + purchaseResponse.getSchemeName());
                    System.out.println("# Customer receipt:");
                    System.out.println(purchaseResponse.getCustomerReceipt().trim());
                }
                break;
            case UNKNOWN:
                System.out.println("# WE'RE NOT QUITE SURE WHETHER THE MOTO WENT THROUGH OR NOT :/");
                System.out.println("# CHECK THE LAST TRANSACTION ON THE EFTPOS ITSELF FROM THE APPROPRIATE MENU ITEM.");
                System.out.println("# YOU CAN THE TAKE THE APPROPRIATE ACTION.");
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private void handleFinishedGetLastTransaction(TransactionFlowState txState) {
        if (txState.getResponse() != null) {
            GetLastTransactionResponse gltResponse = new GetLastTransactionResponse(txState.getResponse());

            if (lastCmd.length > 1) {
                // User specified that he intended to retrieve a specific tx by pos_ref_id
                // This is how you can use a handy function to match it.
                Message.SuccessState success = spi.gltMatch(gltResponse, lastCmd[1]);
                if (success == Message.SuccessState.UNKNOWN) {
                    System.out.println("# Did not retrieve expected transaction. Here is what we got:");
                } else {
                    System.out.println("# Tx matched expected purchase request.");
                }
            }

            PurchaseResponse purchaseResponse = new PurchaseResponse(txState.getResponse());
            System.out.println("# Scheme: " + purchaseResponse.getSchemeName());
            System.out.println("# Response: " + purchaseResponse.getResponseText());
            System.out.println("# RRN: " + purchaseResponse.getRRN());
            System.out.println("# Error: " + txState.getResponse().getError());
            System.out.println("# Customer receipt:");
            System.out.println(purchaseResponse.getCustomerReceipt().trim());
        } else {
            // We did not even get a response, like in the case of a time-out.
            System.out.println("# Could not retrieve last transaction.");
        }
    }

    private static void handleFinishedSettle(TransactionFlowState txState) {
        switch (txState.getSuccess()) {
            case SUCCESS:
                System.out.println("# SETTLEMENT SUCCESSFUL!");
                if (txState.getResponse() != null) {
                    Settlement settleResponse = new Settlement(txState.getResponse());
                    System.out.println("# Response: " + settleResponse.getResponseText());
                    System.out.println("# Merchant receipt:");
                    System.out.println(settleResponse.getMerchantReceipt().trim());
                    System.out.println("# Period start: " + settleResponse.getPeriodStartTime());
                    System.out.println("# Period end: " + settleResponse.getPeriodEndTime());
                    System.out.println("# Settlement time: " + settleResponse.getTriggeredTime());
                    System.out.println("# Transaction range: " + settleResponse.getTransactionRange());
                    System.out.println("# Terminal ID: " + settleResponse.getTerminalId());
                    System.out.println("# Total TX count: " + settleResponse.getTotalCount());
                    System.out.println("# Total TX value: " + (settleResponse.getTotalValue() / 100.0));
                    System.out.println("# By acquirer TX count: " + settleResponse.getSettleByAcquirerCount());
                    System.out.println("# By acquirer TX value: " + (settleResponse.getSettleByAcquirerValue() / 100.0));
                    System.out.println("# SCHEME SETTLEMENTS:");
                    Iterable<SchemeSettlementEntry> schemes = settleResponse.getSchemeSettlementEntries();
                    for (SchemeSettlementEntry s : schemes) {
                        System.out.println("# " + s);
                    }
                }
                break;
            case FAILED:
                System.out.println("# SETTLEMENT FAILED!");
                if (txState.getResponse() != null) {
                    Settlement settleResponse = new Settlement(txState.getResponse());
                    System.out.println("# Response: " + settleResponse.getResponseText());
                    System.out.println("# Error: " + txState.getResponse().getError());
                    System.out.println("# Merchant receipt:");
                    System.out.println(settleResponse.getMerchantReceipt().trim());
                }
                break;
            case UNKNOWN:
                System.out.println("# SETTLEMENT ENQUIRY RESULT UNKNOWN!");
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private static void handleFinishedSettlementEnquiry(TransactionFlowState txState) {
        switch (txState.getSuccess()) {
            case SUCCESS:
                System.out.println("# SETTLEMENT ENQUIRY SUCCESSFUL!");
                if (txState.getResponse() != null) {
                    Settlement settleResponse = new Settlement(txState.getResponse());
                    System.out.println("# Response: " + settleResponse.getResponseText());
                    System.out.println("# Merchant receipt:");
                    System.out.println(settleResponse.getMerchantReceipt().trim());
                    System.out.println("# Period start: " + settleResponse.getPeriodStartTime());
                    System.out.println("# Period end: " + settleResponse.getPeriodEndTime());
                    System.out.println("# Settlement time: " + settleResponse.getTriggeredTime());
                    System.out.println("# Transaction range: " + settleResponse.getTransactionRange());
                    System.out.println("# Terminal ID: " + settleResponse.getTerminalId());
                    System.out.println("# Total TX count: " + settleResponse.getTotalCount());
                    System.out.println("# Total TX value: " + (settleResponse.getTotalValue() / 100.0));
                    System.out.println("# By acquirer TX count: " + (settleResponse.getSettleByAcquirerCount()));
                    System.out.println("# By acquirer TX value: " + (settleResponse.getSettleByAcquirerValue() / 100.0));
                    System.out.println("# SCHEME SETTLEMENTS:");
                    Iterable<SchemeSettlementEntry> schemes = settleResponse.getSchemeSettlementEntries();
                    for (SchemeSettlementEntry s : schemes) {
                        System.out.println("# " + s);
                    }
                }
                break;
            case FAILED:
                System.out.println("# SETTLEMENT ENQUIRY FAILED!");
                if (txState.getResponse() != null) {
                    Settlement settleResponse = new Settlement(txState.getResponse());
                    System.out.println("# Response: " + settleResponse.getResponseText());
                    System.out.println("# Error: " + txState.getResponse().getError());
                    System.out.println("# Merchant receipt:");
                    System.out.println(settleResponse.getMerchantReceipt().trim());
                }
                break;
            case UNKNOWN:
                System.out.println("# SETTLEMENT ENQUIRY RESULT UNKNOWN!");
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private void printActions() {
        System.out.println("# ----------- AVAILABLE ACTIONS ------------");

        if (spi.getCurrentFlow() == SpiFlow.IDLE) {
            System.out.println("# [kebab:1200:100:500:false] - [kebab:price:tip:cashout:promptForCash] charge for kebab with extras!");
            System.out.println("# [13kebab:1300] - MOTO - accept payment over the phone");
            System.out.println("# [yuck:500] - hand out a refund of $5.00!");
            System.out.println("# [cashout:5000] - do a cashout-only transaction");
            System.out.println("# [settle] - initiate settlement");
            System.out.println("# [settle_enq] - settlement enquiry");
            System.out.println("#");
            System.out.println("# [recover:prchs1] - attempt state recovery for pos_ref_id 'prchs1'");
            System.out.println("# [glt:prchs1] - get last transaction - expect it to be pos_ref_id 'prchs1'");
            System.out.println("#");
            System.out.println("# [rcpt_from_eftpos:true] - offer customer receipt from EFTPOS");
            System.out.println("# [sig_flow_from_eftpos:true] - signature flow to be handled by EFTPOS");
            System.out.println("# [print_merchant_copy:true] - add printing of footers and headers onto the existing EFTPOS receipt provided by payment application");
            System.out.println("# [receipt_header:myheader] - set header for the receipt");
            System.out.println("# [receipt_footer:myfooter] - set footer for the receipt");
            System.out.println("#");
        }

        if (spi.getCurrentStatus() == SpiStatus.UNPAIRED && spi.getCurrentFlow() == SpiFlow.IDLE) {
            System.out.println("# [pos_id:CITYKEBAB1] - set the POS ID");
        }

        if (spi.getCurrentStatus() == SpiStatus.UNPAIRED || spi.getCurrentStatus() == SpiStatus.PAIRED_CONNECTING) {
            System.out.println("# [eftpos_address:10.161.104.104] - set the EFTPOS address");
        }

        if (spi.getCurrentStatus() == SpiStatus.UNPAIRED && spi.getCurrentFlow() == SpiFlow.IDLE)
            System.out.println("# [pair] - pair with EFTPOS");

        if (spi.getCurrentStatus() != SpiStatus.UNPAIRED && spi.getCurrentFlow() == SpiFlow.IDLE)
            System.out.println("# [unpair] - unpair and disconnect");

        if (spi.getCurrentFlow() == SpiFlow.PAIRING) {
            if (spi.getCurrentPairingFlowState().isAwaitingCheckFromPos())
                System.out.println("# [pair_confirm] - confirm pairing code");

            if (!spi.getCurrentPairingFlowState().isFinished())
                System.out.println("# [pair_cancel] - cancel pairing");

            if (spi.getCurrentPairingFlowState().isFinished())
                System.out.println("# [ok] - acknowledge final");
        }

        if (spi.getCurrentFlow() == TRANSACTION) {
            TransactionFlowState txState = spi.getCurrentTxFlowState();

            if (txState.isAwaitingSignatureCheck()) {
                System.out.println("# [tx_sign_accept] - accept signature");
                System.out.println("# [tx_sign_decline] - decline signature");
            }

            if (txState.isAwaitingPhoneForAuth()) {
                System.out.println("# [tx_auth_code:123456] - submit phone for auth code");
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
        System.out.println("# SPI CONFIG: " + spi.getConfig());
        System.out.println("# -----------------------------------------");
        System.out.println("# SPI: v" + Spi.getVersion());
    }

    private void acceptUserInput() {
        final Scanner scanner = new Scanner(System.in);
        boolean bye = false;
        while (!bye && scanner.hasNextLine()) {
            final String input = scanner.nextLine();
            if (StringUtils.isEmpty(input)) {
                System.out.print("> ");
                continue;
            }
            final String[] spInput = input.split(":");
            lastCmd = spInput;
            try {
                bye = processInput(spInput);
            } catch (Exception e) {
                System.out.println("Could not process input. " + e.getMessage());
                System.out.println("Try Again.");
                System.out.print("> ");
            }
        }
        System.out.println("# BaBye!");

        // Dump arguments for next session
        System.out.println(posId + ":" + eftposAddress +
                (spiSecrets == null ? "" :
                        ":" + spiSecrets.getEncKey() +
                                ":" + spiSecrets.getHmacKey()));
    }

    private boolean processInput(String[] spInput) {
        switch (spInput[0].toLowerCase()) {
            case "purchase":
            case "kebab":
                int tipAmount = 0;
                if (spInput.length > 2) tipAmount = NumberUtils.toInt(spInput[2], tipAmount);
                int cashoutAmount = 0;
                if (spInput.length > 3) cashoutAmount = NumberUtils.toInt(spInput[3], cashoutAmount);
                boolean promptForCashout = false;
                if (spInput.length > 4) promptForCashout = Boolean.parseBoolean(spInput[4]);
                // posRefId is what you would usually use to identify the order in your own system.
                String posRefId = "kebab-" + new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date());
                InitiateTxResult pres = spi.initiatePurchaseTx(posRefId, Integer.parseInt(spInput[1]), tipAmount, cashoutAmount, promptForCashout, options);
                if (!pres.isInitiated()) {
                    System.out.println("# Could not initiate purchase: " + pres.getMessage() + ". Please retry.");
                }
                break;

            case "refund":
            case "yuck":
                InitiateTxResult yuckRes = spi.initiateRefundTx("yuck-" + new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date()), Integer.parseInt(spInput[1]));
                if (!yuckRes.isInitiated()) {
                    System.out.println("# Could not initiate refund: " + yuckRes.getMessage() + ". Please retry.");
                }
                break;

            case "cashout":
                InitiateTxResult coRes = spi.initiateCashoutOnlyTx("launder-" + new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date()), Integer.parseInt(spInput[1]));
                if (!coRes.isInitiated()) {
                    System.out.println("# Could not initiate cashout: " + coRes.getMessage() + ". Please retry.");
                }
                break;

            case "13kebab":
                InitiateTxResult motoRes = spi.initiateMotoPurchaseTx("kebab-" + new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date()), Integer.parseInt(spInput[1]));
                if (!motoRes.isInitiated()) {
                    System.out.println("# Could not initiate MOTO purchase: " + motoRes.getMessage() + ". Please retry.");
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

            case "tx_auth_code":
                SubmitAuthCodeResult sacRes = spi.submitAuthCode(spInput[1]);
                if (!sacRes.isValidFormat()) {
                    System.out.println("Invalid code format. " + sacRes.getMessage() + ". Try again.");
                }
                break;

            case "settle":
                InitiateTxResult settleRes = spi.initiateSettleTx(RequestIdHelper.id("settle"));
                if (!settleRes.isInitiated()) {
                    System.out.println("# Could not initiate settlement: " + settleRes.getMessage() + ". Please retry.");
                }
                break;

            case "settle_enq":
                InitiateTxResult senqRes = spi.initiateSettlementEnquiry(RequestIdHelper.id("stlenq"));
                if (!senqRes.isInitiated()) {
                    System.out.println("# Could not initiate settlement enquiry: " + senqRes.getMessage() + ". Please retry.");
                }
                break;

            case "rcpt_from_eftpos":
                spi.getConfig().setPromptForCustomerCopyOnEftpos("true".equalsIgnoreCase(spInput[1]));
                SystemHelper.clearConsole();
                spi.ackFlowEndedAndBackToIdle();
                printStatusAndActions();
                System.out.print("> ");
                break;

            case "sig_flow_from_eftpos":
                spi.getConfig().setSignatureFlowOnEftpos("true".equalsIgnoreCase(spInput[1]));
                SystemHelper.clearConsole();
                spi.ackFlowEndedAndBackToIdle();
                printStatusAndActions();
                System.out.print("> ");
                break;

            case "print_merchant_copy":
                spi.getConfig().setPrintMerchantCopy("true".equalsIgnoreCase(spInput[1]));
                SystemHelper.clearConsole();
                spi.ackFlowEndedAndBackToIdle();
                printStatusAndActions();
                System.out.print("> ");
                break;

            case "receipt_header":
                String inputHeader = spInput[1].replace("\\r\\n", "\r\n");
                inputHeader = inputHeader.replace("\\\\", "\\");
                options.setCustomerReceiptHeader(inputHeader);
                options.setMerchantReceiptHeader(inputHeader);
                SystemHelper.clearConsole();
                spi.ackFlowEndedAndBackToIdle();
                printStatusAndActions();
                System.out.print("> ");
                break;

            case "receipt_footer":
                String inputFooter = spInput[1].replace("\\r\\n", "\r\n");
                inputFooter = inputFooter.replace("\\\\", "\\");
                options.setCustomerReceiptFooter(inputFooter);
                options.setMerchantReceiptFooter(inputFooter);
                SystemHelper.clearConsole();
                spi.ackFlowEndedAndBackToIdle();
                printStatusAndActions();
                System.out.print("> ");
                break;

            case "ok":
                SystemHelper.clearConsole();
                spi.ackFlowEndedAndBackToIdle();
                printStatusAndActions();
                System.out.print("> ");
                break;

            case "recover":
                SystemHelper.clearConsole();
                InitiateTxResult rRes = spi.initiateRecovery(spInput[1], PURCHASE);
                if (!rRes.isInitiated()) {
                    System.out.println("# Could not initiate recovery. " + rRes.getMessage() + ". Please retry.");
                }
                break;

            case "glt":
                InitiateTxResult gltRes = spi.initiateGetLastTx();
                System.out.println(gltRes.isInitiated() ?
                        "# GLT Initiated. Will be updated with Progress." :
                        "# Could not initiate GLT: " + gltRes.getMessage() + ". Please Retry.");
                break;

            case "status":
                SystemHelper.clearConsole();
                printStatusAndActions();
                break;

            case "bye":
                return true;

            case "":
                System.out.print("> ");
                break;

            default:
                System.out.println("# I don't understand. Sorry.");
                System.out.print("> ");
                break;
        }
        return false;
    }

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
    }

}
