package com.instamojo.android.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.instamojo.android.R;
import com.instamojo.android.activities.PaymentDetailsActivity;
import com.instamojo.android.models.EMIBank;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Authored by vedhavyas on 22/07/16.
 */
public class EMIBankOptionsView extends BaseFragment {

    private PaymentDetailsActivity parentActivity;
    private LinearLayout optionsContainer;
    private EMIBank selectedBank;

    public EMIBankOptionsView() {
    }

    public static EMIBankOptionsView getInstance(EMIBank selectedBank) {
        EMIBankOptionsView optionsView = new EMIBankOptionsView();
        optionsView.setSelectedBank(selectedBank);
        return optionsView;
    }

    private static double getEmiAmount(String totalAmount, int rate, int tenure) {
        double parsedAmount = Double.parseDouble(totalAmount);
        double perRate = ((double) rate) / 1200;
        double emiAmount = parsedAmount * perRate / (1 - Math.pow((1 / (1 + perRate)), tenure));
        return getRoundedValue(emiAmount, 2);
    }

    private static double getTotalAmount(double emiAmount, int tenure) {
        return getRoundedValue(emiAmount * tenure, 2);
    }

    private static double getRoundedValue(double value, int precision) {
        BigDecimal bigDecimal = new BigDecimal(value);
        bigDecimal = bigDecimal.setScale(precision, BigDecimal.ROUND_HALF_DOWN);
        return bigDecimal.doubleValue();
    }

    @SuppressWarnings("unchecked")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_emi_instamojo, container, false);
        parentActivity = (PaymentDetailsActivity) getActivity();
        inflateXML(view);
        loadOptions();
        return view;
    }

    @Override
    public void inflateXML(View view) {
        super.inflateXML(view);
        optionsContainer = (LinearLayout) view.findViewById(R.id.emi_view_container);
    }

    @Override
    public void onResume() {
        super.onResume();
        parentActivity.updateActionBarTitle(R.string.choose_an_emi_option);
    }

    public void setSelectedBank(EMIBank selectedBank) {
        this.selectedBank = selectedBank;
    }

    private void loadOptions() {
        optionsContainer.removeAllViews();
        String orderAmount = parentActivity.getOrder().getAmount();
        for (final Map.Entry<Integer, Integer> option : selectedBank.getRates().entrySet()) {
            View optionView = LayoutInflater.from(getContext()).inflate(R.layout.emi_option_view,
                    optionsContainer, false);
            double emiAmount = getEmiAmount(orderAmount, option.getValue(), option.getKey());
            String emiAmountString = "₹" + emiAmount + " x " + option.getKey() + " Months";
            String finalAmountString = "Total ₹" + getTotalAmount(emiAmount, option.getKey()) + " @ "
                    + option.getValue() + "% pa";
            ((TextView) optionView.findViewById(R.id.emi_amount)).setText(emiAmountString);
            ((TextView) optionView.findViewById(R.id.final_emi_amount)).setText(finalAmountString);
            optionView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    parentActivity.getOrder().getEmiOptions()
                            .setSelectedBankCode(selectedBank.getBankCode());
                    parentActivity.getOrder().getEmiOptions().setSelectedTenure(option.getKey());
                    parentActivity.loadFragment(CardForm.getCardForm(CardForm.Mode.EMI), true);
                }
            });
            optionsContainer.addView(optionView);
        }
    }
}
