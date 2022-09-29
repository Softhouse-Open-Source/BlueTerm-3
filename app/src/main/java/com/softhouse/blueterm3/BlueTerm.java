package com.softhouse.blueterm3;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.softhouse.blueterm3.bluetooth.BluetoothSerialService;
import com.softhouse.blueterm3.bluetooth.DeviceListActivity;
import com.softhouse.blueterm3.privacy_policy.PrivacyPolicyActivity;
import com.softhouse.blueterm3.util.PreferenceUtil;
import com.softhouse.blueterm3.util.TermKeyListener;
import com.softhouse.blueterm3.util.TermPreferences;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;


public class BlueTerm extends Activity {
    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    public static final int ORIENTATION_SENSOR = 0;
    public static final int ORIENTATION_PORTRAIT = 1;
    public static final int ORIENTATION_LANDSCAPE = 2;


    // manual keys
    public static final int TAB_KEY_CODE = 011;
    public static final int REMOVE_VALUE_KEY = 010;
    public static final int ESC_VALUE_KEY = 033;


    private static TextView mTitle;

    // Name of the connected device
    private String mConnectedDeviceName = null;

    /**
     * Set to true to add debugging code and logging.
     */
    public static final boolean DEBUG = true;

    /**
     * Set to true to log each character received from the remote process to the
     * android log, which makes it easier to debug some kinds of problems with
     * emulating escape sequences and control codes.
     */
    public static final boolean LOG_CHARACTERS_FLAG = DEBUG && false;

    /**
     * Set to true to log unknown escape sequences.
     */
    public static final boolean LOG_UNKNOWN_ESCAPE_SEQUENCES = DEBUG && false;

    /**
     * The tag we use when logging, so that our messages can be distinguished
     * from other messages in the log. Public because it's used by several
     * classes.
     */
    public static final String LOG_TAG = "BlueTerm";

    // Message types sent from the BluetoothReadService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    private BluetoothAdapter mBluetoothAdapter = null;

    /**
     * Our main view. Displays the emulated terminal screen.
     */
    private EmulatorView mEmulatorView;

    /**
     * A key listener that tracks the modifier keys and allows the full ASCII
     * character set to be entered.
     */
    private TermKeyListener mKeyListener;


    private static BluetoothSerialService mSerialService = null;

    private static InputMethodManager mInputManager;

    private boolean mEnablingBT;
    private boolean mLocalEcho = false;
    private int mFontSize = 9;
    private int mColorId = 2;
    private int mTabKey = 44;
    private int mControlKeyId = 0;
    private boolean mAllowCTIConnections = true;
    private int mIncomingEoL_0D = 0x0D;
    private int mIncomingEoL_0A = 0x0A;
    private int mOutgoingEoL_0D = 0x0D;
    private int mOutgoingEoL_0A = 0x0A;
    private int mScreenOrientation = 0;

    private static final String LOCALECHO_KEY = "localecho";
    private static final String FONTSIZE_KEY = "fontsize";
    private static final String COLOR_KEY = "color";
    private static final String TAB_KEY = "tabkey";
    private static final String CONTROLKEY_KEY = "controlkey";
    private static final String ALLOW_INSECURE_CONNECTIONS_KEY = "allowinsecureconnections";
    private static final String INCOMING_EOL_0D_KEY = "incoming_eol_0D";
    private static final String INCOMING_EOL_0A_KEY = "incoming_eol_0A";
    private static final String OUTGOING_EOL_0D_KEY = "outgoing_eol_0D";
    private static final String OUTGOING_EOL_0A_KEY = "outgoing_eol_0A";
    private static final String SCREENORIENTATION_KEY = "screenorientation";

    public static final int WHITE = 0xffffffff;
    public static final int BLACK = 0xff000000;
    public static final int BLUE = 0xff344ebd;
    public static final int GOLDEN = 0xffd4af37;

    private static final int[][] COLOR_SCHEMES = {
            {BLACK, WHITE}, {WHITE, BLACK}, {WHITE, BLUE}, {GOLDEN, BLACK}};

    private static final int[] CONTROL_KEY_SCHEMES = {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_AT,
            KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_UP
    };

    private static String[] CONTROL_KEY_NAME;

    private int mControlKeyCode;

    private SharedPreferences mPrefs;

    private MenuItem mMenuItemConnect;
    private MenuItem mMenuItemStartStopRecording;

    private Dialog mAboutDialog;

    public static PreferenceUtil preferenceUtil;


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        preferenceUtil = PreferenceUtil.getInstance(this);

        if (DEBUG)
            Log.e(LOG_TAG, "+++ ON CREATE +++");

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        readPrefs();

        CONTROL_KEY_NAME = getResources().getStringArray(R.array.entries_controlkey_preference);

        mInputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        // Set up the custom title
        mTitle = (TextView) findViewById(R.id.title_left_text);
        mTitle = (TextView) findViewById(R.id.title_right_text);


        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            finishDialogNoBluetooth();
            return;
        }

        setContentView(R.layout.term_activity);

        mEmulatorView = (EmulatorView) findViewById(R.id.emulatorView);

        mEmulatorView.initialize(this);

        mKeyListener = new TermKeyListener();

        mEmulatorView.setFocusable(true);
        mEmulatorView.setFocusableInTouchMode(true);
        mEmulatorView.requestFocus();
        mEmulatorView.register(mKeyListener);

        mSerialService = new BluetoothSerialService(getApplicationContext(), mHandlerBT, mEmulatorView);

        if (DEBUG)
            Log.e(LOG_TAG, "+++ DONE IN ON CREATE +++");

    }



    @Override
    public void onStart() {
        super.onStart();
        if (DEBUG)
            Log.e(LOG_TAG, "++ ON START ++");

        mEnablingBT = false;
    }

    @Override
    public synchronized void onResume() {
        super.onResume();

        if (DEBUG) {
            Log.e(LOG_TAG, "+ ON RESUME +");
        }

        if(mEmulatorView!=null){
            mEmulatorView.onResume();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);
        }


        if (!mEnablingBT) { // If we are turning on the BT we cannot check if it's enable
            if ((mBluetoothAdapter != null) && (!mBluetoothAdapter.isEnabled())) {

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.alert_dialog_turn_on_bt)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.alert_dialog_warning_title)
                        .setCancelable(false)
                        .setPositiveButton(R.string.alert_dialog_yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                mEnablingBT = true;
                                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                            }
                        })
                        .setNegativeButton(R.string.alert_dialog_no, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                finishDialogNoBluetooth();
                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();
            }

            if (mSerialService != null) {
                // Only if the state is STATE_NONE, do we know that we haven't started already
                if (mSerialService.getState() == BluetoothSerialService.STATE_NONE) {
                    // Start the Bluetooth chat services
                    mSerialService.start();
                }
            }

            if (mBluetoothAdapter != null) {
                readPrefs();
                updatePrefs();
                mEmulatorView.onResume();
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.e(LOG_TAG, "- ON configuration change -");
        super.onConfigurationChanged(newConfig);
        hideKeyboard();


    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        if (DEBUG)
            Log.e(LOG_TAG, "- ON PAUSE -");

        if (mEmulatorView != null) {
            mInputManager.hideSoftInputFromWindow(mEmulatorView.getWindowToken(), 0);
            mEmulatorView.onPause();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (DEBUG)
            Log.e(LOG_TAG, "-- ON STOP --");
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (DEBUG)
            Log.e(LOG_TAG, "--- ON DESTROY ---");

    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        Log.e(LOG_TAG, "OnSaveState");
        super.onSaveInstanceState(savedInstanceState);

    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.e(LOG_TAG, "OnReSaveState");
        onCreate(savedInstanceState);
    }

    private void readPrefs() {
        mLocalEcho = mPrefs.getBoolean(LOCALECHO_KEY, mLocalEcho);
        mFontSize = readIntPref(FONTSIZE_KEY, mFontSize, 20);
        mColorId = readIntPref(COLOR_KEY, mColorId, COLOR_SCHEMES.length - 1);
        mControlKeyId = readIntPref(CONTROLKEY_KEY, mControlKeyId, CONTROL_KEY_SCHEMES.length - 1);
        mAllowCTIConnections = mPrefs.getBoolean(ALLOW_INSECURE_CONNECTIONS_KEY, mAllowCTIConnections);
        mIncomingEoL_0D = readIntPref(INCOMING_EOL_0D_KEY, mIncomingEoL_0D, 0x0D0A);
        mIncomingEoL_0A = readIntPref(INCOMING_EOL_0A_KEY, mIncomingEoL_0A, 0x0D0A);
        mOutgoingEoL_0D = readIntPref(OUTGOING_EOL_0D_KEY, mOutgoingEoL_0D, 0x0D0A);
        mOutgoingEoL_0A = readIntPref(OUTGOING_EOL_0A_KEY, mOutgoingEoL_0A, 0x0D0A);
        mScreenOrientation = readIntPref(SCREENORIENTATION_KEY, mScreenOrientation, 2);
    }

    private void updatePrefs() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mEmulatorView.setTextSize((int) (mFontSize * metrics.density));
        mEmulatorView.setTabKeyCode(readIntPref(TAB_KEY, mTabKey, 63));

        setColors();
        mControlKeyCode = CONTROL_KEY_SCHEMES[mControlKeyId];
        mSerialService.setAllowInsecureConnections(mAllowCTIConnections);

        if (mEmulatorView != null) {
            mEmulatorView.setIncomingEoL_0D(mIncomingEoL_0D);
            mEmulatorView.setIncomingEoL_0A(mIncomingEoL_0A);
        }

        switch (mScreenOrientation) {
            case ORIENTATION_PORTRAIT:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case ORIENTATION_LANDSCAPE:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
            default:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    private int readIntPref(String key, int defaultValue, int maxValue) {
        int val;
        try {
            val = Integer.parseInt(mPrefs.getString(key, Integer.toString(defaultValue)));
        } catch (NumberFormatException e) {
            val = defaultValue;
        }
        val = Math.max(0, Math.min(val, maxValue));
        return val;
    }

    public int getConnectionState() {
        return mSerialService.getState();
    }

    private byte[] handleEndOfLineChars(int outgoingEoL) {
        byte[] out;

        if (outgoingEoL == 0x0D0A) {
            out = new byte[2];
            out[0] = 0x0D;
            out[1] = 0x0A;
        } else {
            if (outgoingEoL == 0x00) {
                out = new byte[0];
            } else {
                out = new byte[1];
                out[0] = (byte) outgoingEoL;
            }
        }
        return out;
    }

    public void send(byte[] out) {

        if (out.length == 1) {

            if (out[0] == 0x0D) {
                out = handleEndOfLineChars(mOutgoingEoL_0D);
            } else {
                if (out[0] == 0x0A) {
                    out = handleEndOfLineChars(mOutgoingEoL_0A);
                }
            }
        }

        if (out.length > 0) {
            mSerialService.write(out);
        }
    }

    public void toggleKeyboard() {
        mInputManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    public int getTitleHeight() {
        return mTitle.getHeight();
    }

    // The Handler that gets information back from the BluetoothService
    private final Handler mHandlerBT = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    if (DEBUG) Log.i(LOG_TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case BluetoothSerialService.STATE_CONNECTED:
                            if (mMenuItemConnect != null) {
                                mMenuItemConnect.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
                                mMenuItemConnect.setTitle(R.string.disconnect);
                            }

                            mInputManager.showSoftInput(mEmulatorView, InputMethodManager.SHOW_IMPLICIT);

                            mTitle.setText(R.string.title_connected_to);
                            mTitle.append(" " + mConnectedDeviceName);
                            break;

                        case BluetoothSerialService.STATE_CONNECTING:
                            mTitle.setText(R.string.title_connecting);
                            break;

                        case BluetoothSerialService.STATE_LISTEN:
                        case BluetoothSerialService.STATE_NONE:
                            if (mMenuItemConnect != null) {
                                mMenuItemConnect.setIcon(android.R.drawable.ic_menu_search);
                                mMenuItemConnect.setTitle(R.string.connect);
                            }

                            mInputManager.hideSoftInputFromWindow(mEmulatorView.getWindowToken(), 0);
                            mTitle.setText(R.string.title_not_connected);
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    if (mLocalEcho) {
                        byte[] writeBuf = (byte[]) msg.obj;
                        mEmulatorView.write(writeBuf, msg.arg1);
                    }

                    break;

                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    mEmulatorView.write(readBuf, msg.arg1);

                    break;

                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), getString(R.string.toast_connected_to) + " "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();

                    getActionBar().setTitle(getString(R.string.toast_connected_to) + " " + mConnectedDeviceName);
                    break;
                case MESSAGE_TOAST:
                    if (msg.getData().getString(TOAST).equals(getString(R.string.toast_connection_lost))) {
                        connectionLostDialogBox();
                    } else {
                        Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };


    public void finishDialogNoBluetooth() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.alert_dialog_no_bt)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(R.string.app_name)
                .setCancelable(false)
                .setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    public void connectionLostDialogBox() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.toast_connection_lost))
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(R.string.app_name)
                .setCancelable(false)
                .setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        getActionBar().setTitle(getString(R.string.app_name));
                        dialog.dismiss();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }


    private void connectionDialogBox(final String address) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        ViewGroup viewGroup = findViewById(android.R.id.content);
        View dialogView = LayoutInflater.from(getApplicationContext()).inflate(R.layout.connection_dialog_box, viewGroup, false);
        TextView normalBluetooth, CTIBluetooth;
        normalBluetooth = (TextView) dialogView.findViewById(R.id.btn_normal_bluetooth);
        CTIBluetooth = (TextView) dialogView.findViewById(R.id.btn_cti);
        builder.setView(dialogView);
        final AlertDialog alertDialog = builder.create();

        normalBluetooth.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mAllowCTIConnections = false;
                updatePrefs();
                connectWithBluetooth(address);
                alertDialog.dismiss();
            }
        });


        CTIBluetooth.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mAllowCTIConnections = true;
                updatePrefs();
                connectWithBluetooth(address);
                alertDialog.dismiss();

            }
        });
        alertDialog.show();
    }

    private void connectWithBluetooth(String address) {
        // Get the BLuetooth Device object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mSerialService.connect(device);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        mEmulatorView.setTabKeyActive(true);
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        mEmulatorView.setTabKeyActive(false);
        if (handleControlKey(keyCode, false)) {
            return true;
        } else if (isSystemKey(keyCode, event)) {
            // Don't intercept the system keys
            return super.onKeyUp(keyCode, event);
        } else if (handleDPad(keyCode, false)) {
            return true;
        }

        mKeyListener.keyUp(keyCode);
        return true;
    }

    private boolean handleControlKey(int keyCode, boolean down) {
        if (keyCode == mControlKeyCode) {
            mKeyListener.handleControlKey(down);
            return true;
        }
        return false;
    }

    /**
     * Handle dpad left-right-up-down events. Don't handle
     * dpad-center, that's our control key.
     *
     * @param keyCode
     * @param down
     */
    private boolean handleDPad(int keyCode, boolean down) {
        byte[] buffer = new byte[1];

        if (keyCode < KeyEvent.KEYCODE_DPAD_UP ||
                keyCode > KeyEvent.KEYCODE_DPAD_CENTER) {
            return false;
        }

        if (down) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                buffer[0] = '\r';
                mSerialService.write(buffer);
                send(buffer);
            } else {
                char code;
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        code = 'A';
                        break;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        code = 'B';
                        break;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        code = 'D';
                        break;
                    default:
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        code = 'C';
                        break;
                }
                buffer[0] = 27; // ESC
                //mSerialService.write( buffer );
                send(buffer);
                if (mEmulatorView.getKeypadApplicationMode()) {
                    buffer[0] = 'O';
                    //mSerialService.write( buffer );
                    send(buffer);
                } else {
                    buffer[0] = '[';
                    //mSerialService.write( buffer );
                    send(buffer);
                }
                buffer[0] = (byte) code;
                //mSerialService.write( buffer );
                send(buffer);
            }
        }
        return true;
    }

    private boolean isSystemKey(int keyCode, KeyEvent event) {
        return event.isSystem();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        mMenuItemConnect = menu.getItem(0);
        mMenuItemStartStopRecording = menu.getItem(3);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.connect:

                if (getConnectionState() == BluetoothSerialService.STATE_NONE) {
                    // Launch the DeviceListActivity to see devices and do scan
                    Intent serverIntent = new Intent(this, DeviceListActivity.class);
                    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                } else if (getConnectionState() == BluetoothSerialService.STATE_CONNECTED) {
                    mSerialService.stop();
                    mSerialService.start();

                }
                return true;
            case R.id.preferences:
                doPreferences();
                return true;
            case R.id.menu_special_keys:
                doDocumentKeys();
                return true;
            case R.id.menu_start_stop_save:
                if (mMenuItemStartStopRecording.getTitle() == getString(R.string.menu_stop_logging)) {
                    doStopRecording();
                } else {
                    doStartRecording();
                }
                return true;
            case R.id.menu_about:
                showAboutDialog();
                return true;
        }
        return false;
    }

    private void doPreferences() {
        startActivity(new Intent(this, TermPreferences.class));
    }

    public void doOpenOptionsMenu() {
        openOptionsMenu();
    }

    private void setColors() {
        int[] scheme = COLOR_SCHEMES[mColorId];
        mEmulatorView.setColors(scheme[0], scheme[1]);
    }

    private void doDocumentKeys() {
        String controlKey = "Volume Up/Down";
        new AlertDialog.Builder(this).
                setTitle(getString(R.string.title_document_key_press) + " \"" + controlKey + "\" " + getString(R.string.title_document_key_rest)).
                setMessage(" Space ==> Control-@ (NUL)\n"
                        + " A..Z ==> Control-A..Z\n"
                        + " i ==> Control-i (TAB)\n"
                        + " 1 ==> Control-[ (ESC)\n"
                        + " 5 ==> Control-_\n"
                        + " . ==> Control-\\\n"
                        + " 0 ==> Control-]\n"
                        + " 6 ==> Control-^").
                show();
    }

    private void doStartRecording() {
        File sdCard = Environment.getExternalStorageDirectory();

        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String currentDateTimeString = format.format(new Date());
        String fileName = sdCard.getAbsolutePath() + "/blueTerm_" + currentDateTimeString + ".log";

        mEmulatorView.setFileNameLog(fileName);
        mEmulatorView.startRecording();

        mMenuItemStartStopRecording.setTitle(R.string.menu_stop_logging);
        Toast.makeText(getApplicationContext(), getString(R.string.menu_logging_started) + "\n\n" + fileName, Toast.LENGTH_LONG).show();
    }

    private void doStopRecording() {
        mEmulatorView.stopRecording();
        mMenuItemStartStopRecording.setTitle(R.string.menu_start_logging);
        Toast.makeText(getApplicationContext(), getString(R.string.menu_logging_stopped), Toast.LENGTH_SHORT).show();
    }

    private void showAboutDialog() {
        mAboutDialog = new Dialog(BlueTerm.this);
        mAboutDialog.setContentView(R.layout.about);
        mAboutDialog.setTitle(getString(R.string.app_name) + " " + getString(R.string.app_version));

        Button buttonOpen = (Button) mAboutDialog.findViewById(R.id.buttonDialog);
        buttonOpen.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                mAboutDialog.dismiss();
            }
        });

        mAboutDialog.show();
    }

    /*
     * on activity result
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (DEBUG) Log.d(LOG_TAG, "onActivityResult " + resultCode);
        switch (requestCode) {

            case REQUEST_CONNECT_DEVICE:

                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    String address = data.getExtras()
                            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);

                    connectWithBluetooth(address);

                }
                break;

            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode != Activity.RESULT_OK) {

                    Log.d(LOG_TAG, "BT not enabled");

                    finishDialogNoBluetooth();
                }
        }
    }

    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        View view = getCurrentFocus();
        if (view == null) {
            view = new View(this);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);

        if (mEmulatorView != null)
            mEmulatorView.updateSize();
    }

}


