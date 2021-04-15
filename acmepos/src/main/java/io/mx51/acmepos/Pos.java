package io.mx51.acmepos;

import io.mx51.spi.Spi;
import io.mx51.spi.model.*;
import io.mx51.spi.util.RequestIdHelper;
import io.mx51.utils.SystemHelper;

import java.util.Date;
import java.util.Scanner;

/**
 * This is your POS. To integrate with SPI, you need to instantiate a {@link Spi} object and interact with it.
 * <p>
 * Primarily, you need to implement 3 things:
 * 1. Settings screen
 * 2. Pairing flow screen
 * 3. Transaction flow screen
 */
public class Pos {

    private String posId = "ACMEPOS";
    private Secrets spiSecrets;
    private String eftposAddress = "emulator-prod.herokuapp.com";

    private Spi spi;

    public static void main(String[] args) {
        new Pos().start(args);
    }

    private void start(String[] args) {
        // This is where you load your state - like the pos_id, eftpos address and
        // secrets - from your file system or database
        loadPersistedState(args);

        //region SPI setup
        try {
            // This is how you instantiate SPI while checking for JDK compatibility.
            spi = new Spi(posId, "TODO: serialNumber", eftposAddress, spiSecrets); // It is ok to not have the secrets yet to start with.
        } catch (Spi.CompatibilityException e) {
            System.out.println("# ");
            System.out.println("# Compatibility check failed: " + e.getCause().getMessage());
            System.out.println("# Please ensure you followed all the configuration steps on your machine.");
            System.out.println("# ");
            return;
        }
        // Called when Status changes between Unpaired, PairedConnected and PairedConnecting
        spi.setStatusChangedHandler(new Spi.EventHandler<SpiStatus>() {
            @Override
            public void onEvent(SpiStatus value) {
                onStatusChanged(value);
            }
        });
        // Called when secrets are set, changed or voided.
        spi.setSecretsChangedHandler(new Spi.EventHandler<Secrets>() {
            @Override
            public void onEvent(Secrets value) {
                onSecretsChanged(value);
            }
        });
        // Called throughout to pairing process to update us with progress.
        spi.setPairingFlowStateChangedHandler(new Spi.EventHandler<PairingFlowState>() {
            @Override
            public void onEvent(PairingFlowState value) {
                onPairingFlowStateChanged(value);
            }
        });
        // Called throughout to transaction process to update us with progress.
        spi.setTxFlowStateChangedHandler(new Spi.EventHandler<TransactionFlowState>() {
            @Override
            public void onEvent(TransactionFlowState value) {
                onTxFlowStateChanged(value);
            }
        });
        spi.start();
        //endregion

        // And now we just accept user input and display to the user what is happening.
        SystemHelper.clearConsole();
        System.out.println("# ");
        System.out.println("# Howdy and welcome to ACME-POS! My name is " + posId + ".");
        System.out.println("# I integrate with SPI.");
        System.out.println("# ");

        printStatusAndActions();
        acceptUserInput();

        // Cleanup
        spi.dispose();
    }

    /**
     * Called when we received a status update, e.g. {@link SpiStatus#UNPAIRED}.
     */
    private void onStatusChanged(SpiStatus spiStatus) {
        if (spi.getCurrentFlow() == SpiFlow.IDLE) SystemHelper.clearConsole();
        System.out.println("# ------- STATUS UPDATE -----------");
        printStatusAndActions();
    }

    /**
     * Called during the pairing process to let us know how it's going.
     * <p>
     * We just update our screen with the information, and provide relevant actions to the user.
     */
    private void onPairingFlowStateChanged(PairingFlowState pairingFlowState) {
        SystemHelper.clearConsole();
        System.out.println("# --------- PAIRING FLOW UPDATE -----------");
        System.out.println("# Message: " + pairingFlowState.getMessage());

        final String confirmationCode = pairingFlowState.getConfirmationCode();
        if (confirmationCode != null && confirmationCode.length() > 0) {
            System.out.println("# Confirmation code: " + pairingFlowState.getConfirmationCode());
        }
        printStatusAndActions();
    }

    /**
     * Called during a transaction to let us know how it's going.
     * <p>
     * We just update our screen with the information, and provide relevant actions to the user.
     */
    private void onTxFlowStateChanged(TransactionFlowState txFlowState) {
        SystemHelper.clearConsole();
        System.out.println("# --------- TRANSACTION FLOW UPDATE -----------");
        System.out.println("# Id: " + txFlowState.getId());
        System.out.println("# Type: " + txFlowState.getType());
        System.out.println("# Request sent: " + txFlowState.isRequestSent());
        System.out.println("# Waiting for signature: " + txFlowState.isAwaitingSignatureCheck());
        System.out.println("# Attempting to cancel: " + txFlowState.isAttemptingToCancel());
        System.out.println("# Finished: " + txFlowState.isFinished());
        System.out.println("# Success: " + txFlowState.getSuccess());
        System.out.println("# Display message: " + txFlowState.getDisplayMessage());

        if (txFlowState.isAwaitingSignatureCheck()) {
            // We need to print the receipt for the customer to sign.
            System.out.println(txFlowState.getSignatureRequiredMessage().getMerchantReceipt().trim());
        }

        // If the transaction is finished, we take some extra steps.
        if (txFlowState.isFinished()) {
            if (txFlowState.getSuccess() == Message.SuccessState.UNKNOWN) {
                // TH-4T, TH-4N, TH-2T - This is the case when we can't be sure what happened to the transaction.
                // Invite the merchant to look at the last transaction on the EFTPOS using the documented shortcuts.
                // Now offer your merchant user the options to:
                // A. Retry the transaction from scratch or pay using a different method - if merchant is confident
                // that tx didn't go through.
                // B. Override order as paid in you POS - if merchant is confident that payment went through.
                // C. Cancel out of the order all together - if the customer has left / given up without paying.
                System.out.println("# NOT SURE IF WE GOT PAID OR NOT. CHECK LAST TRANSACTION MANUALLY ON EFTPOS!");
            } else {
                // We have a result...
                switch (txFlowState.getType()) {
                    // Depending on what type of transaction it was, we might act differently or use different data.
                    case PURCHASE:
                        if (txFlowState.getResponse() != null) {
                            final PurchaseResponse purchaseResponse = new PurchaseResponse(txFlowState.getResponse());
                            System.out.println("# Scheme: " + purchaseResponse.getSchemeName());
                            System.out.println("# Response: " + purchaseResponse.getResponseText());
                            System.out.println("# RRN: " + purchaseResponse.getRRN());
                            System.out.println("# Error: " + txFlowState.getResponse().getError());
                            System.out.println("# Customer receipt:");
                            System.out.println(purchaseResponse.getCustomerReceipt().trim());
                            //} else {
                            // We did not even get a response, like in the case of a time-out.
                        }
                        if (txFlowState.getSuccess() == Message.SuccessState.SUCCESS) {
                            // TH-6A
                            System.out.println("# HOORAY WE GOT PAID (TH-7A). CLOSE THE ORDER!");
                        } else {
                            // TH-6E
                            System.out.println("# WE DIDN'T GET PAID. RETRY PAYMENT (TH-5R) OR GIVE UP (TH-5C)!");
                        }
                        break;
                    case REFUND:
                        if (txFlowState.getResponse() != null) {
                            final RefundResponse refundResponse = new RefundResponse(txFlowState.getResponse());
                            System.out.println("# Scheme: " + refundResponse.getSchemeName());
                            System.out.println("# Response: " + refundResponse.getResponseText());
                            System.out.println("# RRN: " + refundResponse.getRRN());
                            System.out.println("# Error: " + txFlowState.getResponse().getError());
                            System.out.println("# Customer receipt:");
                            System.out.println(refundResponse.getCustomerReceipt().trim());
                            //} else {
                            // We did not even get a response, like in the case of a time-out.
                        }
                        break;
                    case SETTLE:
                        if (txFlowState.getResponse() != null) {
                            final Settlement settleResponse = new Settlement(txFlowState.getResponse());
                            System.out.println("# Response: " + settleResponse.getResponseText());
                            System.out.println("# Error: " + txFlowState.getResponse().getError());
                            System.out.println("# Merchant receipt:");
                            System.out.println(settleResponse.getReceipt().trim());
                            //} else {
                            // We did not even get a response, like in the case of a time-out.
                        }
                        break;
                    case GET_LAST_TRANSACTION:
                        if (txFlowState.getResponse() != null) {
                            GetLastTransactionResponse gltResponse = new GetLastTransactionResponse(txFlowState.getResponse());
                            Message.SuccessState success = spi.gltMatch(gltResponse, TransactionType.PURCHASE, 10000, new Date().getTime() - 60000, "MYORDER123");

                            if (success == Message.SuccessState.UNKNOWN) {
                                System.out.println("# Did not retrieve expected transaction of 10000c from a minute ago ;).");
                            } else {
                                System.out.println("# Tx matched expected purchase request.");
                                System.out.println("# Result: " + success);
                                PurchaseResponse purchaseResponse = new PurchaseResponse(txFlowState.getResponse());
                                System.out.println("# Scheme: " + purchaseResponse.getSchemeName());
                                System.out.println("# Response: " + purchaseResponse.getResponseText());
                                System.out.println("# RRN: " + purchaseResponse.getRRN());
                                System.out.println("# Error: " + txFlowState.getResponse().getError());
                                System.out.println("# Customer receipt:");
                                System.out.println(purchaseResponse.getCustomerReceipt().trim());
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        // Let's show the user what options he has at this stage.
        printStatusAndActions();
    }

    /**
     * Called when secrets are set or changed or voided.
     */
    private void onSecretsChanged(Secrets newSecrets) {
        spiSecrets = newSecrets;
        if (spiSecrets != null) {
            System.out.println("\n\n\n# --------- I GOT NEW SECRETS -----------");
            System.out.println("# ---------- PERSIST THEM SAFELY ----------");
            System.out.println("# " + spiSecrets.getEncKey() + ":" + spiSecrets.getHmacKey());
            System.out.println("# -----------------------------------------");
        } else {
            System.out.println("\n\n\n# --------- THE SECRETS HAVE BEEN VOIDED -----------");
            System.out.println("# ---------- CONSIDER ME UNPAIRED ----------");
            System.out.println("# -----------------------------------------");
        }
    }

    /**
     * This method prints the current SPI status and flow and lists available actions to the user.
     */
    private void printStatusAndActions() {
        System.out.println("# ----------- AVAILABLE ACTIONS ------------");

        // Available Actions depend on the current status (Unpaired/PairedConnecting/PairedConnected)
        switch (spi.getCurrentStatus()) {
            case UNPAIRED: // Unpaired...
                switch (spi.getCurrentFlow()) {
                    case IDLE: // Unpaired, Idle
                        System.out.println("# [pos_id:MYPOSNAME] - sets your POS instance ID");
                        System.out.println("# [eftpos_address:10.10.10.10] - sets IP address of target EFTPOS");
                        System.out.println("# [pair] - start pairing");
                        break;

                    case PAIRING: // Unpaired, PairingFlow
                        final PairingFlowState pairingState = spi.getCurrentPairingFlowState();
                        if (pairingState.isAwaitingCheckFromPos()) {
                            System.out.println("# [pair_confirm] - confirm the code matches");
                        }
                        if (!pairingState.isFinished()) {
                            System.out.println("# [pair_cancel] - cancel pairing process");
                        } else {
                            System.out.println("# [ok] - acknowledge");
                        }
                        break;

                    case TRANSACTION: // Unpaired, TransactionFlow - Should never be the case!
                    default:
                        System.out.println("# .. Unexpected Flow .. " + spi.getCurrentFlow());
                        break;
                }
                break;

            case PAIRED_CONNECTED:
                printStatusPairedConnected();
                break;

            case PAIRED_CONNECTING: // This is still considered as a paired kind of state, but...
                // .. we give user the option of changing IP address, just in case the EFTPOS got a new one in the meanwhile
                System.out.println("# [eftpos_address:10.161.110.247] - change IP address of target EFTPOS");
                // .. but otherwise we give the same options as PairedConnected
                printStatusPairedConnected();
                break;

            default:
                System.out.println("# .. Unexpected state .. " + spi.getCurrentStatus());
                break;
        }
        System.out.println("# [status] - reprint buttons/status");
        System.out.println("# [bye] - exit");
        System.out.println();
        System.out.println("# --------------- STATUS ------------------");
        System.out.println("# " + posId + " <--> " + eftposAddress);
        System.out.println("# " + spi.getCurrentStatus() + ":" + spi.getCurrentFlow());
        System.out.println("# -----------------------------------------");
        System.out.print("> ");
    }

    private void printStatusPairedConnected() {
        switch (spi.getCurrentFlow()) {
            case IDLE: // Paired, Idle
                System.out.println("# [purchase:1981] - initiate a payment of $19.81");
                System.out.println("# [refund:1891] - initiate a refund of $18.91");
                System.out.println("# [settle] - initiate settlement");
                System.out.println("# [unpair] - unpair and disconnect");
                break;
            case TRANSACTION: // Paired, Transaction
                if (spi.getCurrentTxFlowState().isAwaitingSignatureCheck()) {
                    System.out.println("# [tx_sign_accept] - accept signature");
                    System.out.println("# [tx_sign_decline] - decline signature");
                }
                if (!spi.getCurrentTxFlowState().isFinished()) {
                    System.out.println("# [tx_cancel] - attempt to cancel transaction");
                } else {
                    System.out.println("# [ok] - acknowledge");
                }
                break;
            case PAIRING: // Paired, Pairing - we have just finished the pairing flow. OK to ack.
                System.out.println("# [ok] - acknowledge");
                break;
            default:
                System.out.println("# .. Unexpected flow .. " + spi.getCurrentFlow());
                break;
        }
    }

    private void acceptUserInput() {
        final Scanner scanner = new Scanner(System.in);
        boolean bye = false;
        while (!bye && scanner.hasNext()) {
            final String input = scanner.next();
            final String[] spInput = input.split(":");
            switch (spInput[0]) {
                case "pos_id":
                    posId = spInput[1];
                    spi.setPosId(posId);
                    SystemHelper.clearConsole();
                    printStatusAndActions();
                    break;
                case "eftpos_address":
                    eftposAddress = spInput[1];
                    spi.setEftposAddress(eftposAddress);
                    SystemHelper.clearConsole();
                    printStatusAndActions();
                    break;
                case "pair":
                    spi.pair();
                    break;
                case "pair_confirm":
                    spi.pairingConfirmCode();
                    break;
                case "pair_cancel":
                    spi.pairingCancel();
                    break;
                case "unpair":
                    spi.unpair();
                    break;
                case "purchase":
                    final InitiateTxResult pRes = spi.initiatePurchaseTx(RequestIdHelper.id("prchs"), Integer.parseInt(spInput[1]));
                    System.out.println(pRes.isInitiated() ?
                            "# Purchase initiated. Will be updated with progress." :
                            "# Could not initiate purchase: " + pRes.getMessage() + ". Please retry.");
                    break;
                case "refund":
                    final InitiateTxResult rRes = spi.initiateRefundTx(RequestIdHelper.id("rfnd"), Integer.parseInt(spInput[1]));
                    System.out.println(rRes.isInitiated() ?
                            "# Refund initiated. Will be updated with progress." :
                            "# Could not initiate refund: " + rRes.getMessage() + ". Please retry.");
                    break;
                case "settle":
                    final InitiateTxResult sres = spi.initiateSettleTx(RequestIdHelper.id("settle"));
                    System.out.println(sres.isInitiated() ?
                            "# Settle initiated. Will be updated with progress." :
                            "# Could not initiate settle: " + sres.getMessage() + ". Please retry.");
                    break;
                case "glt":
                    final InitiateTxResult gltres = spi.initiateGetLastTx();
                    System.out.println(gltres.isInitiated() ?
                            "# GLT initiated. Will be updated with progress." :
                            "# Could not initiate settle: " + gltres.getMessage() + ". Please retry.");
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
                case "ok":
                    spi.ackFlowEndedAndBackToIdle();
                    SystemHelper.clearConsole();
                    printStatusAndActions();
                    break;
                case "status":
                    SystemHelper.clearConsole();
                    printStatusAndActions();
                    break;
                case "bye":
                    bye = true;
                    break;
                case "":
                    break;
                default:
                    SystemHelper.clearConsole();
                    System.out.println("# I don't understand. Sorry.");
                    printStatusAndActions();
                    break;
            }
        }
        System.out.println("# BaBye!");

        // Dump arguments for next session
        System.out.println(posId + ":" + eftposAddress +
                (spiSecrets == null ? "" :
                        ":" + spiSecrets.getEncKey() +
                                ":" + spiSecrets.getHmacKey()));
    }

    /**
     * Just a little function to load state from command-line arguments that looks
     * like PosId:PinPadAddress:EncKey:HmacKey
     * <p>
     * You will need to persist/load these yourself in your own database/filesystem.
     *
     * @param args Arguments from the main method.
     */
    private void loadPersistedState(String[] args) {
        // Let's read cmd line arguments.
        if (args.length < 1) return; // nothing passed in

        // we were given something, at least POS ID and PIN pad address...
        final String[] argSplit = args[0].split(":");
        posId = argSplit[0];
        if (argSplit.length > 1) {
            eftposAddress = argSplit[1];
        }

        // Let's see if we were given existing secrets as well.
        if (argSplit.length > 2) {
            spiSecrets = new Secrets(argSplit[2], argSplit[3]);
        }
    }

}
