package com.example.kasa;

import androidx.appcompat.app.AppCompatActivity;


import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    public static final String Error_Detected = "Błąd aplikacji!";
    public static final String Write_Succes = "Zapis pomyślny";
    public static final String Write_Error = "Błąd zapisu!";

    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    private IntentFilter[] writingTagFilters;
    private boolean writeMode;
    private Tag myTag;
    private Context context;
    private TextView nfc_content;
    private TextView balance;
    private EditText amount;
    private Button ActivateButton;

    private Switch switchAction;
    private RadioButton add, sub;

    private TextView listaLogowLabel;
    private Button wyswietlListeLogow;
    private String fileName = "LogFile.txt";
    private File logFile;

    private String[] cardStringSplited;


    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            init();
        } catch (IOException e) {
            e.printStackTrace();
        }
        context = this;

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Toast.makeText(this, "This device does not support NFC", Toast.LENGTH_SHORT).show();
            finish();
        }

        readfromIntent(getIntent());
        pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT);
        writingTagFilters = new IntentFilter[]{tagDetected};
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.ActivateButton) {
            wykonajAkcjeNaKarcie();
        }
        if (v.getId() == R.id.logView) {
            wyświetlListeLogow();
        }
    }

    private void wykonajAkcjeNaKarcie() {
        try {
            if (myTag == null) {
                Toast.makeText(context, Error_Detected, Toast.LENGTH_LONG).show();
            } else {
                //przy odejmowaniu trzeba uwzględnić możliwość wejscia na debet

                int newNumb;

                if (add.isChecked()) {
                    newNumb = Integer.parseInt(cardStringSplited[1]) + Integer.parseInt(String.valueOf(amount.getText()));
                    zapiszNowyLog(cardStringSplited[0] + ": dodano: " + amount + "było: " + cardStringSplited[1] + "po operacji: " + newNumb);
                } else if (sub.isChecked()) {
                    newNumb = Integer.parseInt(cardStringSplited[1]) - Integer.parseInt(String.valueOf(amount.getText()));
                    zapiszNowyLog(cardStringSplited[0] + ": odjęto: " + amount + "było: " + cardStringSplited[1] + "po operacji: " + newNumb);
                    // if (newNumb < 0) {
                    //     Toast.makeText(this, "Odjąłem wiecej niż możesz, ustawiam na 0", Toast.LENGTH_SHORT).show();
                    //     newNumb = 0;
                    // }
                } else {
                    newNumb = Integer.parseInt(cardStringSplited[1]);
                    zapiszNowyLog(cardStringSplited[0] + ": dodano: " + amount + "było: " + cardStringSplited[1] + "po operacji: " + newNumb);
                }

                write(cardStringSplited[0] + ":" + newNumb, myTag);
                Toast.makeText(context, Write_Succes, Toast.LENGTH_LONG).show();
            }
        } catch (IOException | FormatException e) {
            Toast.makeText(context, Write_Error, Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void zapiszNowyLog(String string) throws IOException {
        BufferedWriter out = new BufferedWriter(new FileWriter(fileName));
        out.write(string+"\n");
        out.close();
    }

    private void wyświetlListeLogow() {
        List<String> logsList;
        logsList = getFromFileToList();
        if (logsList.isEmpty()) {
            Toast.makeText(this, "plik logów jest pusty", Toast.LENGTH_LONG).show();
            return;
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (String log : logsList) {
            stringBuilder.append(log).append("\n");
        }
        listaLogowLabel.setText(stringBuilder);
    }

    private List<String> getFromFileToList() {
        List<String> result = new ArrayList<>();
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                result.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private void readfromIntent(Intent intent) {
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] msgs = null;
            if (rawMsgs != null) {
                msgs = new NdefMessage[rawMsgs.length];
                for (int i = 0; i < rawMsgs.length; i++) {
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }
            }
            buildTagViews(msgs);
        }
    }

    private void buildTagViews(NdefMessage[] msgs) {
        if (msgs == null || msgs.length == 0) {
            return;
        }

        byte[] payload = msgs[0].getRecords()[0].getPayload();
        String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";
        int languageCodeLength = payload[0] & 0063;
        String text = "";

        try {
            text = new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
        } catch (UnsupportedEncodingException e) {
            Log.e("UnsupportedEncoding", e.toString());
        }

        cardStringSplited = text.split(":");

        nfc_content.setText(text);
        balance.setText(cardStringSplited[1]);
    }

    private void write(String text, Tag tag) throws IOException, FormatException {
        NdefRecord[] records = {createRecord(text)};
        NdefMessage message = new NdefMessage(records);

        Ndef ndef = Ndef.get(tag);
        ndef.connect();
        ndef.writeNdefMessage(message);
        ndef.close();
    }

    private NdefRecord createRecord(String text) {
        String lang = "en";
        byte[] textBytes = text.getBytes();
        byte[] langBytes = lang.getBytes(StandardCharsets.US_ASCII);
        int langLength = langBytes.length;
        int textLength = textBytes.length;
        byte[] payload = new byte[1 + langLength + textLength];

        payload[0] = (byte) langLength;

        System.arraycopy(langBytes, 0, payload, 1, langLength);
        System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength);

        return new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], payload);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readfromIntent(intent);
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            myTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        WriteModeOff();
    }

    @Override
    public void onResume() {
        super.onResume();
        WriteModeOn();
    }


    private void WriteModeOn() {
        writeMode = true;
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, writingTagFilters, null);
    }

    private void WriteModeOff() {
        writeMode = false;
        nfcAdapter.disableForegroundDispatch(this);
    }


    private void init() throws IOException {
        balance = findViewById(R.id.balance);
        nfc_content = findViewById(R.id.nfc_content);
        amount = findViewById(R.id.amount);
        ActivateButton = findViewById(R.id.ActivateButton);
        switchAction = findViewById(R.id.switch_action);
        add = findViewById(R.id.add);
        sub = findViewById(R.id.sub);
        listaLogowLabel = findViewById(R.id.logList);
        wyswietlListeLogow = findViewById(R.id.logView);

        logFile = new File(fileName);
        logFile.createNewFile();
    }
}
