package com.softhouse.blueterm3.privacy_policy;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import com.softhouse.blueterm3.BlueTerm;
import com.softhouse.blueterm3.R;

public class PrivacyPolicyActivity extends Activity {

    CheckBox checkBox;
    Button btnContinue;
    WebView privacyPolicyWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_privacy_policy);
        getActionBar().hide();
        createView();
        addListener();
    }

    /**
     *  This function is responsible for creating the view regarding this activity
     */
    private void createView(){
        checkBox=(CheckBox) findViewById(R.id.agreeCheckbox);
        btnContinue=(Button) findViewById(R.id.agreed);
        privacyPolicyWebView=(WebView) findViewById(R.id.privacy_policy_description);

        privacyPolicyWebView.loadDataWithBaseURL(
                null,
                currentPrivacyPolicy(),
                "text/html",
                "utf-8",
                null
        );
    }

    /**
     * This function is responsible for getting the privacy policy HTML content
     */
    private String currentPrivacyPolicy()  {
        return "getString()";
    }


    /**
     * This function is responsible for click listeners regarding this activity
     */
    private void addListener()
    {
        btnContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BlueTerm.preferenceUtil.setAgreePrivacyPolicy(true);
                finish();
            }
        });

        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                btnContinue.setEnabled(isChecked);
            }
        });

    }




}
