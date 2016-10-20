package com.instamojo.android.network;

import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.instamojo.android.BuildConfig;
import com.instamojo.android.callbacks.JusPayRequestCallback;
import com.instamojo.android.callbacks.OrderRequestCallBack;
import com.instamojo.android.callbacks.UPICallback;
import com.instamojo.android.helpers.CardValidator;
import com.instamojo.android.helpers.Constants;
import com.instamojo.android.helpers.Logger;
import com.instamojo.android.models.Card;
import com.instamojo.android.models.CardOptions;
import com.instamojo.android.models.EMIBank;
import com.instamojo.android.models.EMIOptions;
import com.instamojo.android.models.Errors;
import com.instamojo.android.models.NetBankingOptions;
import com.instamojo.android.models.Order;
import com.instamojo.android.models.UPIOptions;
import com.instamojo.android.models.UPISubmissionResponse;
import com.instamojo.android.models.Wallet;
import com.instamojo.android.models.WalletOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Network Request Class.
 *
 * @author vedhavyas
 * @version 1.0
 * @since 14/03/16
 */

public class Request {

    private Order order;
    private OrderRequestCallBack orderRequestCallBack;
    private JusPayRequestCallback jusPayRequestCallback;
    private UPICallback upiCallback;
    private String virtualPaymentAddress;
    private UPISubmissionResponse upiSubmissionResponse;
    private Card card;
    private MODE mode;

    /**
     * Network Request to create an order ID from Instamojo server.
     *
     * @param order                Order model with all the mandatory fields set.
     * @param orderRequestCallBack Callback interface for the Asynchronous Network Call.
     */
    public Request(@NonNull Order order, @NonNull OrderRequestCallBack orderRequestCallBack) {
        this.mode = MODE.OrderCreate;
        this.order = order;
        this.orderRequestCallBack = orderRequestCallBack;
    }


    /**
     * Network Request to get order details from Juspay for JuspaySafeBrowser.
     *
     * @param order                 Order model with all the mandatory fields set.
     * @param card                  Card with all the proper validations done.
     * @param jusPayRequestCallback Callback for Asynchronous network call.
     */
    public Request(@NonNull Order order, @NonNull Card card, @NonNull JusPayRequestCallback jusPayRequestCallback) {
        this.mode = MODE.Juspay;
        this.card = card;
        this.order = order;
        this.jusPayRequestCallback = jusPayRequestCallback;
    }

    /**
     * Network request for UPISubmission Submission
     *
     * @param order                 {@link Order}
     * @param virtualPaymentAddress String
     * @param upiCallback           {@link UPICallback}
     */
    public Request(@NonNull Order order, @NonNull String virtualPaymentAddress, @NonNull UPICallback upiCallback) {
        this.mode = MODE.UPISubmission;
        this.order = order;
        this.virtualPaymentAddress = virtualPaymentAddress;
        this.upiCallback = upiCallback;
    }

    /**
     * Network Request to check the status of the transaction
     *
     * @param order                 {@link Order}
     * @param upiSubmissionResponse {@link UPISubmissionResponse}
     * @param upiCallback           {@link UPICallback}
     */
    public Request(@NonNull Order order, @NonNull UPISubmissionResponse upiSubmissionResponse, @NonNull UPICallback upiCallback) {
        this.mode = MODE.UPIStatusCheck;
        this.order = order;
        this.upiSubmissionResponse = upiSubmissionResponse;
        this.upiCallback = upiCallback;
    }

    /**
     * Executes the call to the server and calls the callback with  {@link Exception} if failed.
     */
    public void execute() {
        switch (this.mode) {
            case OrderCreate:
                executeInstamojoRequest();
                break;
            case Juspay:
                executeJusPayRequest();
                break;
            case UPISubmission:
                executeUPIRequest();
                break;
            case UPIStatusCheck:
                executeUPIStatusCheck();
                break;
            default:
                throw new RuntimeException("Unknown Mode");
        }
    }

    private void executeJusPayRequest() {
        OkHttpClient client = new OkHttpClient();

        //For maestro, add the default values if empty
        if (CardValidator.maestroCard(card.getCardNumber())) {
            if (card.getDate() == null || card.getDate().isEmpty()) {
                card.setDate("12/49");
            }

            if (card.getCvv() == null || card.getCvv().isEmpty()) {
                card.setDate("111");
            }
        }

        FormBody.Builder body = new FormBody.Builder()
                .add("order_id", order.getCardOptions().getOrderID())
                .add("merchant_id", order.getCardOptions().getMerchantID())
                .add("payment_method_type", "CARD")
                .add("card_number", card.getCardNumber())
                .add("name_on_card", card.getCardHolderName())
                .add("card_exp_month", card.getMonth())
                .add("card_exp_year", card.getYear())
                .add("card_security_code", card.getCvv())
                .add("save_to_locker", card.canSaveCard() ? "true" : "false")
                .add("redirect_after_payment", "true")
                .add("format", "json");
        if (order.getEmiOptions() != null
                && order.getEmiOptions().getSelectedBankCode() != null) {
            Logger.logDebug(this.getClass().getSimpleName(), "emi selected....");
            body.add("is_emi", "true");
            body.add("emi_bank", order.getEmiOptions().getSelectedBankCode());
            body.add("emi_tenure", String.valueOf(order.getEmiOptions().getSelectedTenure()));
        }

        final okhttp3.Request request = new okhttp3.Request.Builder()
                .url(order.getCardOptions().getUrl())
                .post(body.build())
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Logger.logError(this.getClass().getSimpleName(), "Error while making Juspay request - " + e.getMessage());
                jusPayRequestCallback.onFinish(null, new Errors.ConnectionError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) {
                String responseBody;
                try {
                    responseBody = response.body().string();
                    response.body().close();
                    Bundle bundle = parseJusPayResponse(responseBody);
                    jusPayRequestCallback.onFinish(bundle, null);
                } catch (IOException e) {
                    Logger.logError(this.getClass().getSimpleName(), "Error while making Juspay request - " + e.getMessage());
                    jusPayRequestCallback.onFinish(null, new Errors.ServerError(e.getMessage()));
                } catch (JSONException e) {
                    Logger.logError(this.getClass().getSimpleName(), "Error while making Juspay request - " + e.getMessage());
                    jusPayRequestCallback.onFinish(null, e);
                }
            }
        });

    }

    private Bundle parseJusPayResponse(String responseBody) throws JSONException {
        JSONObject responseObject = new JSONObject(responseBody);
        String url = responseObject.getJSONObject("payment").getJSONObject("authentication").getString("url");
        final Bundle args = new Bundle();
        args.putString(Constants.URL, url);
        args.putString(Constants.MERCHANT_ID, order.getCardOptions().getMerchantID());
        args.putString(Constants.ORDER_ID, order.getCardOptions().getOrderID());
        return args;
    }

    private void executeInstamojoRequest() {
        OkHttpClient client = new OkHttpClient();

        FormBody.Builder builder = new FormBody.Builder()
                .add("name", order.getBuyerName())
                .add("email", order.getBuyerEmail())
                .add("amount", order.getAmount())
                .add("description", order.getDescription())
                .add("phone", order.getBuyerPhone())
                .add("currency", order.getCurrency())
                .add("transaction_id", order.getTransactionID())
                .add("redirect_url", order.getRedirectionUrl())
                .add("advanced_payment_options", "true")
                .add("mode", order.getMode());
        if (order.getWebhook() != null) {
            builder.add("webhook_url", order.getWebhook());
        }
        RequestBody body = builder.build();
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", getUserAgent());
        headers.put("Authorization", "Bearer " + order.getAuthToken());

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(Urls.getOrderCreateUrl())
                .removeHeader("User-Agent")
                .headers(Headers.of(headers))
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Logger.logError(this.getClass().getSimpleName(), "Error while making Instamojo request - " + e.getMessage());
                orderRequestCallBack.onFinish(order, new Errors.ConnectionError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response r) {
                String responseBody = "";
                try {
                    responseBody = r.body().string();
                    r.body().close();
                    updateTransactionDetails(responseBody);
                    orderRequestCallBack.onFinish(order, null);
                } catch (IOException e) {
                    Logger.logError(this.getClass().getSimpleName(), "Error while making Instamojo request - " + e.getMessage());
                    orderRequestCallBack.onFinish(order, new Errors.ServerError(e.getMessage()));
                } catch (JSONException e) {
                    Logger.logError(this.getClass().getSimpleName(), "Error while making Instamojo request - " + e.getMessage());
                    orderRequestCallBack.onFinish(order, Errors.getAppropriateError(responseBody));
                }
            }
        });
    }

    private void updateTransactionDetails(String responseBody) throws JSONException {
        JSONObject responseObject = new JSONObject(responseBody);
        JSONObject orderObject = responseObject.getJSONObject("order");
        order.setId(orderObject.getString("id"));
        order.setTransactionID(orderObject.getString("transaction_id"));
        order.setResourceURI(orderObject.getString("resource_uri"));

        JSONObject paymentOptionsObject = responseObject.getJSONObject("payment_options");
        if (paymentOptionsObject.has("card_options") && !paymentOptionsObject.isNull("card_options")) {
            JSONObject cardOptions = paymentOptionsObject.getJSONObject("card_options");
            JSONObject submissionData = cardOptions.getJSONObject("submission_data");
            String merchantID = submissionData.getString("merchant_id");
            String orderID = submissionData.getString("order_id");
            String paymentURL = cardOptions.getString("submission_url");
            order.setCardOptions(new CardOptions(orderID, merchantID, paymentURL));
        }

        if (paymentOptionsObject.has("netbanking_options") && !paymentOptionsObject.isNull("netbanking_options")) {
            JSONObject netbankingOptions = paymentOptionsObject.getJSONObject("netbanking_options");
            String nbURL = netbankingOptions.getString("submission_url");
            JSONArray banksArray = netbankingOptions.getJSONArray("choices");
            HashMap<String, String> banks = new HashMap<>();
            JSONObject bank;
            for (int i = 0; i < banksArray.length(); i++) {
                bank = banksArray.getJSONObject(i);
                banks.put(bank.getString("name"), bank.getString("id"));
            }
            if (banks.size() > 0) {
                order.setNetBankingOptions(new NetBankingOptions(nbURL, banks));
            }
        }

        if (paymentOptionsObject.has("emi_options") && !paymentOptionsObject.isNull("emi_options")) {
            JSONObject emiOptionsRaw = paymentOptionsObject.getJSONObject("emi_options");
            JSONArray emiListRaw = emiOptionsRaw.getJSONArray("emi_list");
            EMIBank emiBank;
            JSONObject emiOptionRaw;
            JSONArray ratesRaw;
            JSONObject rateRaw;
            ArrayList<EMIBank> emis = new ArrayList<>();
            for (int i = 0; i < emiListRaw.length(); i++) {
                emiOptionRaw = emiListRaw.getJSONObject(i);
                String bankName = emiOptionRaw.getString("bank_name");
                String bankCode = emiOptionRaw.getString("bank_code");
                Map<Integer, Integer> rates = new HashMap<>();
                ratesRaw = emiOptionRaw.getJSONArray("rates");
                for (int j = 0; j < ratesRaw.length(); j++) {
                    rateRaw = ratesRaw.getJSONObject(j);
                    int tenure = rateRaw.getInt("tenure");
                    int interest = rateRaw.getInt("interest");
                    rates.put(tenure, interest);
                }
                LinkedList<Map.Entry<Integer, Integer>> ratesList = new LinkedList<>(rates.entrySet());
                Collections.sort(ratesList, new Comparator<Map.Entry<Integer, Integer>>() {
                    @Override
                    public int compare(Map.Entry<Integer, Integer> lhs, Map.Entry<Integer, Integer> rhs) {
                        return lhs.getKey() - rhs.getKey();
                    }
                });
                LinkedHashMap<Integer, Integer> sortedRates = new LinkedHashMap<>();
                for (Map.Entry<Integer, Integer> entry : ratesList) {
                    sortedRates.put(entry.getKey(), entry.getValue());
                }

                if (sortedRates.size() > 0) {
                    emiBank = new EMIBank(bankName, bankCode, sortedRates);
                    emis.add(emiBank);
                }
            }
            String url = emiOptionsRaw.getString("submission_url");
            JSONObject submissionData = emiOptionsRaw.getJSONObject("submission_data");
            String merchantID = submissionData.getString("merchant_id");
            String orderID = submissionData.getString("order_id");
            if (emis.size() > 0) {
                order.setEmiOptions(new EMIOptions(merchantID, orderID, url, emis));
            }
        }

        if (paymentOptionsObject.has("wallet_options") && !paymentOptionsObject.isNull("wallet_options")) {
            JSONObject walletOptionsObject = paymentOptionsObject.getJSONObject("wallet_options");
            JSONArray walletChoices = walletOptionsObject.getJSONArray("choices");
            JSONObject walletObject;
            ArrayList<Wallet> wallets = new ArrayList<>();
            for (int i = 0; i < walletChoices.length(); i++) {
                walletObject = walletChoices.getJSONObject(i);
                String name = walletObject.getString("name");
                String walletID = walletObject.getString("id");
                String walletImage = walletObject.getString("image");
                wallets.add(new Wallet(name, walletImage, walletID));
            }

            String url = walletOptionsObject.getString("submission_url");

            if (wallets.size() > 0) {
                order.setWalletOptions(new WalletOptions(url, wallets));
            }
        }

        if (paymentOptionsObject.has("upi_options") && !paymentOptionsObject.isNull("upi_options")) {
            JSONObject upiOptionsRaw = paymentOptionsObject.getJSONObject("upi_options");
            UPIOptions upiOptions = new UPIOptions(upiOptionsRaw.getString("submission_url"));
            order.setUpiOptions(upiOptions);
        }
    }

    private void executeUPIRequest() {
        OkHttpClient client = new OkHttpClient();

        RequestBody body = new FormBody.Builder()
                .add("virtual_address", this.virtualPaymentAddress)
                .build();

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", getUserAgent());
        headers.put("Authorization", "Bearer " + order.getAuthToken());

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(order.getUpiOptions().getUrl())
                .removeHeader("User-Agent")
                .headers(Headers.of(headers))
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Logger.logError(this.getClass().getSimpleName(),
                        "Error while making UPI Submission request - " + e.getMessage());
                upiCallback.onSubmission(null, e);
            }

            @Override
            public void onResponse(Call call, Response r) {
                String responseBody = "";
                try {
                    responseBody = r.body().string();
                    r.body().close();
                    upiCallback.onSubmission(parseUPIResponse(responseBody), null);
                } catch (IOException | JSONException e) {
                    Logger.logError(this.getClass().getSimpleName(),
                            "Error while making UPI Submission request - " + e.getMessage());
                    upiCallback.onSubmission(null, e);
                }
            }
        });
    }

    private UPISubmissionResponse parseUPIResponse(String responseString) throws JSONException {
        JSONObject responseObject = new JSONObject(responseString);
        String paymentID = responseObject.getString("payment_id");
        int statusCode = responseObject.getInt("status_code");
        String payerVirtualAddress = responseObject.getString("payer_virtual_address");
        String payeeVirtualAddress = responseObject.getString("payee_virtual_address");
        String statusCheckURL = responseObject.getString("status_check_url");
        String upiBank = responseObject.getString("upi_bank");
        String statusMessage = responseObject.getString("status_message");

        return new UPISubmissionResponse(paymentID, statusCode, payerVirtualAddress,
                payeeVirtualAddress, statusCheckURL, upiBank, statusMessage);
    }

    private void executeUPIStatusCheck() {
        OkHttpClient client = new OkHttpClient();

        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", getUserAgent());
        headers.put("Authorization", "Bearer " + order.getAuthToken());

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(upiSubmissionResponse.getStatusCheckURL())
                .removeHeader("User-Agent")
                .headers(Headers.of(headers))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Logger.logError(this.getClass().getSimpleName(),
                        "Error while making UPI Status Check request - " + e.getMessage());
                upiCallback.onStatusCheckComplete(null, false, e);
            }

            @Override
            public void onResponse(Call call, Response r) {
                String responseBody = "";
                try {
                    responseBody = r.body().string();
                    r.body().close();
                    boolean transactionComplete = isTransactionComplete(responseBody);
                    if (!transactionComplete) {
                        upiCallback.onStatusCheckComplete(null, false, null);
                        return;
                    }

                    Bundle bundle = new Bundle();
                    bundle.putString(Constants.ORDER_ID, order.getId());
                    bundle.putString(Constants.TRANSACTION_ID, order.getTransactionID());
                    bundle.putString(Constants.PAYMENT_ID, upiSubmissionResponse.getPaymentID());
                    upiCallback.onStatusCheckComplete(bundle, true, null);
                } catch (IOException | JSONException e) {
                    Logger.logError(this.getClass().getSimpleName(),
                            "Error while making UPI Status Check request - " + e.getMessage());
                    upiCallback.onStatusCheckComplete(null, false, e);
                }
            }
        });
    }

    private boolean isTransactionComplete(String responseBody) throws JSONException {
        JSONObject responseObject = new JSONObject(responseBody);
        return responseObject.getInt("status_code") != Constants.PENDING_PAYMENT;
    }

    private String getUserAgent() {
        return "Instamojo Android SDK;" + Build.MODEL + ";" + Build.BRAND + ";" + Build.VERSION.SDK_INT
                + ";" + BuildConfig.APPLICATION_ID + ";" + BuildConfig.VERSION_NAME + ";" + BuildConfig.VERSION_CODE;
    }

    private static enum MODE {
        OrderCreate,
        Juspay,
        UPISubmission,
        UPIStatusCheck
    }

}
