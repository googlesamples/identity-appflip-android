/*
 * Copyright 2019 Google
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.appflip_sample_android;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Formatter;

public class AppFlipActivity extends AppCompatActivity {
  private static final String TAG = "AppFlipActivity";
  private static final String AUTH_CODE = "authcode_from_AppFlipSampleApp";
  private static final String EXTRA_APP_FLIP_CLIENT_ID = "CLIENT_ID";
  private static final String EXTRA_APP_FLIP_SCOPES = "SCOPE";
  private static final String EXTRA_APP_FLIP_REDIRECT_URI = "REDIRECT_URI";
  private static final String EXTRA_APP_FLIP_AUTHORIZATION_CODE = "AUTHORIZATION_CODE";
  private static final String EXTRA_APP_FLIP_ERROR_TYPE = "ERROR_TYPE";
  private static final String EXTRA_APP_FLIP_ERROR_CODE = "ERROR_CODE";
  private static final int APP_FLIP_RESULT_ERROR = -2;
  private static final String EXTRA_APP_FLIP_ERROR_DESCRIPTION = "ERROR_DESCRIPTION";
  private static final int APP_FLIP_RECOVERABLE_ERROR = 1;
  private static final int APP_FLIP_UNRECOVERABLE_ERROR = 2;
  private static final int APP_FLIP_INVALID_REQUEST_ERROR = 3;
  private static final int APP_FLIP_USER_DENIED_3P_CONSENT_ERROR_CODE = 13;
  private static final String SIGNATURE_DIGEST_ALGORITHM = "SHA-256";

  private String callingAppPackageName, callingAppFingerprint;
  private Button submitButton;
  private TextView logTextView, errorCodeLabel;
  private EditText errorCodeInput;
  private String clientId, scopes, redirectUri;
  private RadioGroup radioGroup;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_app_flip);
    submitButton = findViewById(R.id.submitButton);
    logTextView = findViewById(R.id.logTextView);
    radioGroup = findViewById(R.id.radioGroup);
    errorCodeInput = findViewById(R.id.errorCodeEditText);
    errorCodeLabel = findViewById(R.id.errorCodeLabel);
    callingAppPackageName = getString(R.string.calling_app_package_name);
    callingAppFingerprint = getString(R.string.calling_app_fingerprint);

    Intent intent = getIntent();
    final Context context = getApplicationContext();
    ComponentName callingActivity = getCallingActivity();
    logTextView.setText("Checking intent sender cert and package name...\n");
    if (!validateCallingApp(callingActivity)) {
      Toast.makeText(context, "Sender cert or name mismatch!", Toast.LENGTH_LONG).show();
      Log.e(TAG, "Intent sender certificate or package ID mismatch!");
      logTextView.append("Intent sender certificate or package ID mismatch!\n");
      return;
    }
    logTextView.append("Certificate match\n");
    if(intent.hasExtra(EXTRA_APP_FLIP_CLIENT_ID)){
      clientId = intent.getExtras().getString(EXTRA_APP_FLIP_CLIENT_ID);
      scopes = intent.getExtras().getString(EXTRA_APP_FLIP_SCOPES);
      redirectUri = intent.getExtras().getString(EXTRA_APP_FLIP_REDIRECT_URI);
    } else {
      Log.d(TAG, "couldn't find extra " + EXTRA_APP_FLIP_CLIENT_ID);
      Toast.makeText(context, "Did not received clientID", Toast.LENGTH_SHORT).show();
      return;
    }
    logTextView.append("Client ID received: " + clientId + "\n");
    Log.d(TAG, "passed in clientId: " + clientId + "\n");
    logTextView.append("Scopes received: " + scopes + "\n");
    Log.d(TAG, "passed in Scopes: " + scopes + "\n");
    logTextView.append("RedirectUri received: " + redirectUri +"\n");
    Log.d(TAG, "passed in RedirectUri: " + redirectUri + "\n");

    radioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(RadioGroup group, int checkedId) {
        switch(checkedId){
          case R.id.errorUnrecoverableWithCodeRadioButton:
          case R.id.errorRecoverableRadioButton:
          case R.id.invalidRequestRadioButton:
            errorCodeInput.setVisibility(View.VISIBLE);
            errorCodeLabel.setVisibility(View.VISIBLE);
            errorCodeInput.setEnabled(true);
            break;
          default:
            errorCodeInput.setVisibility(View.INVISIBLE);
            errorCodeLabel.setVisibility(View.INVISIBLE);
            errorCodeInput.setEnabled(false);
        }
      }
    });

    submitButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        int selectedId = radioGroup.getCheckedRadioButtonId();
        if (selectedId == -1) {
          Toast.makeText(context, "Nothing selected", Toast.LENGTH_SHORT).show();
          return;
        }
        Intent returnIntent = new Intent();
        String errorCodeString = errorCodeInput.getText().toString();
        int errorCode = 0;
        if(errorCodeInput.isEnabled()){
          try{
            errorCode = Integer.valueOf(errorCodeString);
          } catch (NumberFormatException e){
            Toast.makeText(context, "Invalid error code", Toast.LENGTH_SHORT).show();
            return;
          }
        }
        switch (selectedId){
          case R.id.resultOkWithCodeRadioButton:
            String authCode = AUTH_CODE;
            returnIntent.putExtra(EXTRA_APP_FLIP_AUTHORIZATION_CODE, authCode);
            setResult(Activity.RESULT_OK, returnIntent);
            break;
          case R.id.resultOkEmptyCodeRadioButton:
            returnIntent.putExtra(EXTRA_APP_FLIP_AUTHORIZATION_CODE, "");
            setResult(Activity.RESULT_OK, returnIntent);
            break;
          case R.id.errorUserNotConsentRadioButton:
            returnIntent.putExtra(EXTRA_APP_FLIP_ERROR_TYPE, APP_FLIP_UNRECOVERABLE_ERROR);
            returnIntent.putExtra(
                EXTRA_APP_FLIP_ERROR_CODE, APP_FLIP_USER_DENIED_3P_CONSENT_ERROR_CODE);
            setResult(APP_FLIP_RESULT_ERROR, returnIntent);
            break;
          case R.id.errorRecoverableRadioButton:
            returnIntent.putExtra(EXTRA_APP_FLIP_ERROR_TYPE, APP_FLIP_RECOVERABLE_ERROR);
            returnIntent.putExtra(
                EXTRA_APP_FLIP_ERROR_CODE, errorCode);
            setResult(APP_FLIP_RESULT_ERROR, returnIntent);
            break;
          case R.id.errorUnrecoverableWithCodeRadioButton:
            returnIntent.putExtra(EXTRA_APP_FLIP_ERROR_TYPE, APP_FLIP_UNRECOVERABLE_ERROR);
            returnIntent.putExtra(
                EXTRA_APP_FLIP_ERROR_CODE, errorCode);
            setResult(APP_FLIP_RESULT_ERROR, returnIntent);
            break;
          case R.id.invalidRequestRadioButton:
            returnIntent.putExtra(EXTRA_APP_FLIP_ERROR_TYPE, APP_FLIP_INVALID_REQUEST_ERROR);
            returnIntent.putExtra(
                EXTRA_APP_FLIP_ERROR_CODE, errorCode);
            setResult(APP_FLIP_RESULT_ERROR, returnIntent);
            break;
        }
        finish();

      }
    });
  }

  private boolean validateCallingApp(ComponentName callingActivity) {
    if (callingActivity != null) {
      String packageName = callingActivity.getPackageName();
      if (callingAppPackageName.equalsIgnoreCase(packageName)) {
        try {
          String fingerPrint = getCertificateFingerprint(getApplicationContext(), packageName);
          return callingAppFingerprint.equalsIgnoreCase(fingerPrint);
        } catch (NameNotFoundException e) {
          Log.e(TAG, "No such app is installed", e);
        }
      }
    }
    return false;
  }

  @Nullable
  private String getCertificateFingerprint(Context context, String packageName)
      throws PackageManager.NameNotFoundException {
    PackageManager pm = context.getPackageManager();
    PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
    Signature[] signatures = packageInfo.signatures;
    InputStream input = new ByteArrayInputStream(signatures[0].toByteArray());
    try {
      CertificateFactory certificateFactory = CertificateFactory.getInstance("X509");
      X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(input);
      MessageDigest md = MessageDigest.getInstance(SIGNATURE_DIGEST_ALGORITHM);
      byte[] publicKey = md.digest(certificate.getEncoded());
      return byte2HexFormatted(publicKey);
    } catch (CertificateException | NoSuchAlgorithmException e) {
      Log.e(TAG, "Failed to process the certificate", e);
    }
    return null;
  }

  private String byte2HexFormatted(byte[] byteArray) {
    Formatter formatter = new Formatter();
    for (int i = 0; i < byteArray.length - 1; i++) {
      formatter.format("%02x:", byteArray[i]);
    }
    formatter.format("%02x", byteArray[byteArray.length - 1]);
    return formatter.toString().toUpperCase();
  }
}

