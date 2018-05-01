package com.assemblypayments.motelpos;

import com.assemblypayments.spi.Spi;
import com.assemblypayments.spi.SpiPreauth;
import com.assemblypayments.spi.model.*;
import com.assemblypayments.spi.util.RequestIdHelper;
import com.assemblypayments.utils.SystemHelper;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

/**
 * NOTE: THIS PROJECT USES THE 2.1.x of the SPI Client Library
 * <p>
 * This specific POS shows you how to integrate the preauth features of the SPI protocol.
 * If you're just starting, we recommend you start with KebabPos. It goes through the basics.
 */
public class Pos {

    private static final Logger LOG = LogManager.getLogger("spi");

    private Spi spi;
    private SpiPreauth spiPreauth;
    private String posId = "MOTELPOS1";
    private String eftposAddress = "192.168.1.6";
    private Secrets spiSecrets = null;

    public static void main(String[] args) {
        new Pos().start(args);
    }

    private void start(String[] args) {
        LOG.info("Starting MotelPos...");
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
        spiPreauth = spi.enablePreauth();
        spi.start();

        SystemHelper.clearConsole();
        System.out.println("# Welcome to MotelPos!");
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
        System.out.println("# --> SPI status changed: " + status);
        printStatusAndActions();
        System.out.print("> ");
    }

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
            System.out.println("# Request amount: " + (txState.getAmountCents() / 100.0));
            System.out.println("# Waiting for signature: " + txState.isAwaitingSignatureCheck());
            System.out.println("# Attempting to cancel: " + txState.isAttemptingToCancel());
            System.out.println("# Finished: " + txState.isFinished());
            System.out.println("# Success: " + txState.getSuccess());

            if (txState.isAwaitingSignatureCheck()) {
                // We need to print the receipt for the customer to sign.
                System.out.println("# RECEIPT TO PRINT FOR SIGNATURE");
                System.out.println(txState.getSignatureRequiredMessage().getMerchantReceipt().trim());
            }

            if (txState.isFinished()) {
                System.out.println();
                switch (txState.getSuccess()) {
                    case SUCCESS:
                        switch (txState.getType()) {
                            case PREAUTH: {
                                System.out.println("# PREAUTH RESULT - SUCCESS");
                                PreauthResponse preauthResponse = new PreauthResponse(txState.getResponse());
                                System.out.println("# PREAUTH-ID: " + preauthResponse.getPreauthId());
                                System.out.println("# NEW BALANCE AMOUNT: " + preauthResponse.getBalanceAmount());
                                System.out.println("# PREV BALANCE AMOUNT: " + preauthResponse.getPreviousBalanceAmount());
                                System.out.println("# COMPLETION AMOUNT: " + preauthResponse.getCompletionAmount());

                                PurchaseResponse details = preauthResponse.getDetails();
                                System.out.println("# Response: " + details.getResponseText());
                                System.out.println("# RRN: " + details.getRRN());
                                System.out.println("# Scheme: " + details.getSchemeName());
                                System.out.println("# Customer receipt:");
                                System.out.println(details.getCustomerReceipt().trim());
                                break;
                            }
                            case ACCOUNT_VERIFY: {
                                System.out.println("# ACCOUNT VERIFICATION SUCCESS");
                                AccountVerifyResponse acctVerifyResponse = new AccountVerifyResponse(txState.getResponse());
                                PurchaseResponse details = acctVerifyResponse.getDetails();
                                System.out.println("# Response: " + details.getResponseText());
                                System.out.println("# RRN: " + details.getRRN());
                                System.out.println("# Scheme: " + details.getSchemeName());
                                System.out.println("# Merchant receipt:");
                                System.out.println(details.getMerchantReceipt().trim());
                                break;
                            }
                            default:
                                System.out.println("# MOTEL POS DOESN'T KNOW WHAT TO DO WITH THIS TX TYPE WHEN IT SUCCEEDS");
                                break;
                        }
                        break;
                    case FAILED:
                        switch (txState.getType()) {
                            case PREAUTH:
                                System.out.println("# PREAUTH TRANSACTION FAILED :(");
                                System.out.println("# Error: " + txState.getResponse().getError());
                                System.out.println("# Error detail: " + txState.getResponse().getErrorDetail());
                                if (txState.getResponse() != null) {
                                    PurchaseResponse purchaseResponse = new PurchaseResponse(txState.getResponse());
                                    System.out.println("# Response: " + purchaseResponse.getResponseText());
                                    System.out.println("# RRN: " + purchaseResponse.getRRN());
                                    System.out.println("# Scheme: " + purchaseResponse.getSchemeName());
                                    System.out.println("# Customer receipt:");
                                    System.out.println(purchaseResponse.getCustomerReceipt().trim());
                                }
                                break;
                            case ACCOUNT_VERIFY:
                                System.out.println("# ACCOUNT VERIFICATION FAILED :(");
                                System.out.println("# Error: " + txState.getResponse().getError());
                                System.out.println("# Error detail: " + txState.getResponse().getErrorDetail());
                                if (txState.getResponse() != null) {
                                    AccountVerifyResponse acctVerifyResponse = new AccountVerifyResponse(txState.getResponse());
                                    PurchaseResponse details = acctVerifyResponse.getDetails();
                                    System.out.println(details.getCustomerReceipt().trim());
                                }
                                break;
                            default:
                                System.out.println("# MOTEL POS DOESN'T KNOW WHAT TO DO WITH THIS TX TYPE WHEN IT FAILS");
                                break;
                        }
                        break;
                    case UNKNOWN:
                        switch (txState.getType()) {
                            case PREAUTH:
                                System.out.println("# WE'RE NOT QUITE SURE WHETHER PREAUTH TRANSACTION WENT THROUGH OR NOT:/");
                                System.out.println("# CHECK THE LAST TRANSACTION ON THE EFTPOS ITSELF FROM THE APPROPRIATE MENU ITEM.");
                                System.out.println("# IF YOU CONFIRM THAT THE CUSTOMER PAID, CLOSE THE ORDER.");
                                System.out.println("# OTHERWISE, RETRY THE PAYMENT FROM SCRATCH.");
                                break;
                            case ACCOUNT_VERIFY:
                                System.out.println("# WE'RE NOT QUITE SURE WHETHER ACCOUNT VERIFICATION WENT THROUGH OR NOT:/");
                                System.out.println("# CHECK THE LAST TRANSACTION ON THE EFTPOS ITSELF FROM THE APPROPRIATE MENU ITEM.");
                                System.out.println("# IF YOU CONFIRM THAT THE CUSTOMER PAID, CLOSE THE ORDER.");
                                System.out.println("# OTHERWISE, RETRY THE PAYMENT FROM SCRATCH.");
                                break;
                            default:
                                System.out.println("# MOTEL POS DOESN'T KNOW WHAT TO DO WITH THIS TX TYPE WHEN IT's UNKNOWN");
                                break;
                        }
                        break;
                }
            }
        }
        System.out.println();
    }

    private void printActions() {
        System.out.println("# ----------- AVAILABLE ACTIONS ------------");

        if (spi.getCurrentFlow() == SpiFlow.IDLE) {
            System.out.println("# [acct_verify] - verify a customer's account");
            System.out.println("# [preauth_open:10000] - open a new preauth for $100.00");
            System.out.println("# [preauth_topup:12345678:5000] - top up existing preauth 12345678 with $50.00");
            System.out.println("# [preauth_topdown:12345678:5000] - partially cancel existing preauth 12345678 by $50.00");
            System.out.println("# [preauth_extend:12345678] - extend existing preauth 12345678");
            System.out.println("# [preauth_complete:12345678:8000] - complete preauth with ID 12345678 for $80.00");
            System.out.println("# [preauth_cancel:12345678] - cancel preauth with ID 12345678");
        }

        if (spi.getCurrentStatus() == SpiStatus.UNPAIRED && spi.getCurrentFlow() == SpiFlow.IDLE) {
            System.out.println("# [pos_id:MOTEPOS1] - set the POS ID");
        }

        if (spi.getCurrentStatus() == SpiStatus.UNPAIRED || spi.getCurrentStatus() == SpiStatus.PAIRED_CONNECTING) {
            System.out.println("# [eftpos_address:10.161.104.104] - set the EFTPOS address");
        }

        if (spi.getCurrentStatus() == SpiStatus.UNPAIRED && spi.getCurrentFlow() == SpiFlow.IDLE) {
            System.out.println("# [pair] - pair with EFTPOS");
        }

        if (spi.getCurrentStatus() != SpiStatus.UNPAIRED && spi.getCurrentFlow() == SpiFlow.IDLE) {
            System.out.println("# [unpair] - unpair and disconnect");
        }

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
        System.out.println("# CASH ONLY! #");
        System.out.println("# -----------------------------------------");
        System.out.println("# SPI: v" + Spi.getVersion());
    }

    private void acceptUserInput() {
        final Scanner scanner = new Scanner(System.in);
        boolean bye = false;
        while (!bye && scanner.hasNext()) {
            final String input = scanner.next();
            if (StringUtils.isEmpty(input)) {
                System.out.print("> ");
                continue;
            }
            String[] spInput = input.split(":");
            try {
                bye = processInput(spInput);
            } catch (Exception e) {
                System.out.println("Could not process input. " + e.getMessage());
                System.out.println("Try again.");
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
        String preauthId;
        InitiateTxResult initRes;
        switch (spInput[0].toLowerCase()) {
            case "acct_verify":
                initRes = spiPreauth.initiateAccountVerifyTx("actvfy-" + new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date()));
                if (!initRes.isInitiated()) {
                    System.out.println("# Could not initiate account verify request: " + initRes.getMessage() + ". Please retry.");
                }
                break;

            case "preauth_open":
                initRes = spiPreauth.initiateOpenTx("propen-" + new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date()), Integer.parseInt(spInput[1]));
                if (!initRes.isInitiated()) {
                    System.out.println("# Could not initiate preauth request: " + initRes.getMessage() + ". Please retry.");
                }
                break;

            case "preauth_topup":
                preauthId = spInput[1];
                initRes = spiPreauth.initiateTopupTx("prtopup-" + preauthId + "-" + new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date()), preauthId, Integer.parseInt(spInput[2]));
                if (!initRes.isInitiated()) {
                    System.out.println("# Could not initiate preauth request: " + initRes.getMessage() + ". Please retry.");
                }
                break;

            case "preauth_topdown":
                preauthId = spInput[1];
                initRes = spiPreauth.initiatePartialCancellationTx("prtopd-" + preauthId + "-" + new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date()), preauthId, Integer.parseInt(spInput[2]));
                if (!initRes.isInitiated()) {
                    System.out.println("# Could not initiate preauth request: " + initRes.getMessage() + ". Please retry.");
                }
                break;

            case "preauth_extend":
                preauthId = spInput[1];
                initRes = spiPreauth.initiateExtendTx("prtopd-" + preauthId + "-" + new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date()), preauthId);
                if (!initRes.isInitiated()) {
                    System.out.println("# Could not initiate preauth request: " + initRes.getMessage() + ". Please retry.");
                }
                break;

            case "preauth_cancel":
                preauthId = spInput[1];
                initRes = spiPreauth.initiateCancelTx("prtopd-" + preauthId + "-" + new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date()), preauthId);
                if (!initRes.isInitiated()) {
                    System.out.println("# Could not initiate preauth request: " + initRes.getMessage() + ". Please retry.");
                }
                break;

            case "preauth_complete":
                preauthId = spInput[1];
                initRes = spiPreauth.initiateCompletionTx("prcomp-" + preauthId + "-" + new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date()), preauthId, Integer.parseInt(spInput[2]));
                if (!initRes.isInitiated()) {
                    System.out.println("# Could not initiate preauth request: " + initRes.getMessage() + ". Please retry.");
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

            case "glt":
                InitiateTxResult gltRes = spi.initiateGetLastTx();
                System.out.println(gltRes.isInitiated() ?
                        "# GLT initiated. Will be updated with progress." :
                        "# Could not initiate GLT: " + gltRes.getMessage() + ". Please retry.");
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

            case "recover":
                SystemHelper.clearConsole();
                InitiateTxResult rRes = spi.initiateRecovery(spInput[1], TransactionType.PURCHASE);
                if (!rRes.isInitiated()) {
                    System.out.println("# Could not initiate recovery. " + rRes.getMessage() + ". Please retry.");
                    System.out.println("# Could not initiate recovery. " + rRes.getMessage() + ". Please retry.");
                }
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
