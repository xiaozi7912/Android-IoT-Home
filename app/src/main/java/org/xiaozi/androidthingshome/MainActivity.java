package org.xiaozi.androidthingshome;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;
import com.samgol.driver.bmp180.Bmp180;
import com.samgol.driver.bmp180.Bmp180SensorDriver;
import com.xiaozi.framework.libs.BaseActivity;
import com.xiaozi.framework.libs.utils.Logger;
import com.xiaozi.framework.libs.view.DevInfoView;
import com.xiaozi.framework.libs.view.NetworkInfoView;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends BaseActivity {
    private Button mAndroidSettingsButton = null;
    private Button mGetGPIOInfoButton = null;
    private Button mLedRedButton, mLedGreenButton, mLedBlueButton;
    private TextView mBmp180InfoTextView = null;
    private NetworkInfoView mNetworkInfoView = null;
    private DevInfoView mDevInfoView = null;

    private FirebaseAuth mAuth = null;
    private FirebaseFirestore mDatabase = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private PeripheralManager mService = null;
    private SensorManager mSensorManager = null;

    private Gpio mLedRedGPIO, mLedGreenGPIO, mLedBlueGPIO, mRelayGPIO;
    private Bmp180 mBmp180 = null;
    private Bmp180SensorDriver mBmp180SensorDriver = null;

    private final static int REQUEST_ENABLE_BLUETOOTH = 1001;
    private final static String GPIO_NAME_LED_RED = "BCM5";
    private final static String GPIO_NAME_LED_GREEN = "BCM6";
    private final static String GPIO_NAME_LED_BLUE = "BCM26";
    private final static String GPIO_NAME_RELAY = "BCM4";
    private final static String I2C_PORT = "I2C1";

    private boolean mIsDestroy = false;
    private float mTemperature = 0f;
    private float mPressure = 0f;
    private double mAltitde = 0f;
    private long mLastUpdateTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Logger.init(BuildConfig.SHOW_DEV_INFO);
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseFirestore.getInstance();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mService = PeripheralManager.getInstance();
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Logger.d(LOG_TAG, "onCreate FirebaseInstanceId.getInstance.getId : " + FirebaseInstanceId.getInstance().getId());
        Logger.d(LOG_TAG, "onCreate FirebaseInstanceId.getInstance.getToken : " + FirebaseInstanceId.getInstance().getToken());

        initView();
        initBluetooth();
        initGPIOPins();
        initSensors();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mBroadcastReceiver, intentFilter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mIsDestroy = true;
        closeGPIOPins();
        closeSensors();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Logger.i(LOG_TAG, "onKeyUp");
        Logger.d(LOG_TAG, "onKeyUp keyCode : " + keyCode);
        switch (keyCode) {
            case KeyEvent.KEYCODE_1:
                onLedButtonClick(1);
                break;
            case KeyEvent.KEYCODE_2:
                onLedButtonClick(2);
                break;
            case KeyEvent.KEYCODE_3:
                onLedButtonClick(3);
                break;
            case KeyEvent.KEYCODE_4:
                switchRelay();
                break;
            case KeyEvent.KEYCODE_8:
                readBmp180Data();
                break;
            case KeyEvent.KEYCODE_9:
                searchBTDevices();
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void initView() {
        super.initView();
        mAndroidSettingsButton = findViewById(R.id.main_android_settings_button);
        mGetGPIOInfoButton = findViewById(R.id.main_get_gpio_info_button);
        mLedRedButton = findViewById(R.id.main_led_red_button);
        mLedGreenButton = findViewById(R.id.main_led_green_button);
        mLedBlueButton = findViewById(R.id.main_led_blue_button);
        mBmp180InfoTextView = findViewById(R.id.main_bmp180_info_text);
        mNetworkInfoView = findViewById(R.id.main_network_info_view);
        mDevInfoView = findViewById(R.id.main_dev_info_view);

        mBmp180InfoTextView.setText("None");
        mDevInfoView.setApplicationId(BuildConfig.APPLICATION_ID);
        mDevInfoView.setVersionName(BuildConfig.VERSION_NAME);
        mDevInfoView.setVersionCode(BuildConfig.VERSION_CODE);
        mDevInfoView.updateView();

        mAndroidSettingsButton.setOnClickListener(mOnClickListener);
        mGetGPIOInfoButton.setOnClickListener(mOnClickListener);
        mLedRedButton.setOnClickListener(mOnClickListener);
        mLedGreenButton.setOnClickListener(mOnClickListener);
        mLedBlueButton.setOnClickListener(mOnClickListener);
    }

    private void initBluetooth() {
        Logger.i(LOG_TAG, "initBluetooth");
        if (mBluetoothAdapter != null) {
            Logger.d(LOG_TAG, "initBluetooth mBluetoothAdapter.isEnabled : " + mBluetoothAdapter.isEnabled());
            if (!mBluetoothAdapter.isEnabled()) {
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent, REQUEST_ENABLE_BLUETOOTH);
            }
        } else {
            Toast.makeText(mActivity, "No Bluetooth", Toast.LENGTH_SHORT).show();
        }
    }

    private void initGPIOPins() {
        Logger.i(LOG_TAG, "initGPIOPins");
        try {
            mLedRedGPIO = mService.openGpio(GPIO_NAME_LED_RED);
            mLedRedGPIO.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);

            mLedGreenGPIO = mService.openGpio(GPIO_NAME_LED_GREEN);
            mLedGreenGPIO.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);

            mLedBlueGPIO = mService.openGpio(GPIO_NAME_LED_BLUE);
            mLedBlueGPIO.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);

            mRelayGPIO = mService.openGpio(GPIO_NAME_RELAY);
            mRelayGPIO.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initSensors() {
        Logger.i(LOG_TAG, "initSensors");
//        mSensorManager.registerDynamicSensorCallback(mDynamicSensorCallback);

//        try {
//            mBmp180SensorDriver = new Bmp180SensorDriver(I2C_PORT);
//            mBmp180SensorDriver.registerBarometerSensor();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        mBmp180 = new Bmp180(I2C_PORT);
        startReadBmp180Data();
    }

    private void searchBTDevices() {
        Logger.i(LOG_TAG, "searchBTDevices");
        if (mBluetoothAdapter.startDiscovery()) {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    mBluetoothAdapter.cancelDiscovery();
                }
            }, 10 * 1000);
        }
    }

    private void blinkLed(int index) {
        Logger.i(LOG_TAG, "blinkLed");
        try {
            switch (index) {
                case 1:
                    mLedRedGPIO.setValue(!mLedRedGPIO.getValue());
                    Thread.sleep(500);
                    mLedRedGPIO.setValue(!mLedRedGPIO.getValue());
                    break;
                case 2:
                    mLedGreenGPIO.setValue(!mLedGreenGPIO.getValue());
                    Thread.sleep(500);
                    mLedGreenGPIO.setValue(!mLedGreenGPIO.getValue());
                    break;
                case 3:
                    mLedBlueGPIO.setValue(!mLedBlueGPIO.getValue());
                    Thread.sleep(500);
                    mLedBlueGPIO.setValue(!mLedBlueGPIO.getValue());
                    break;
            }
            Thread.sleep(500);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startReadBmp180Data() {
        Logger.i(LOG_TAG, "startReadBmp180Data");
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!mIsDestroy) {
                    readBmp180Data();

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            String strReadData = String.format("Temperature : %s\nPressure : %s\nAltitude : %.3f",
                                    mTemperature, mPressure, mAltitde);
                            mBmp180InfoTextView.setText(strReadData);
                        }
                    });

                    if (mTemperature > 35) {
                        blinkLed(1);
                    } else if (mTemperature > 30) {
                        blinkLed(2);
                    } else {
                        blinkLed(3);
                    }
                }
            }
        }).start();
    }

    private void readBmp180Data() {
        Logger.i(LOG_TAG, "readBmp180Data");
        try {
            mTemperature = mBmp180.readTemperature();
            mPressure = mBmp180.readPressure();
            mAltitde = (Math.round(mBmp180.readAltitude() * 10) / 10.0F);

            long currentTime = System.currentTimeMillis();
            if ((currentTime - mLastUpdateTime) > (10 * 1000)) {
                Map<String, Object> data = new HashMap<String, Object>();
                data.put("temperaure", mTemperature);
                data.put("pressure", mPressure);
                data.put("altitude", mAltitde);
                data.put("create_time", currentTime);

                addBMP180ToFirebase(currentTime, data);
                mLastUpdateTime = currentTime;
            }
            Logger.d(LOG_TAG, "readBmp180Data mTemperature : " + mTemperature);
            Logger.d(LOG_TAG, "readBmp180Data mPressure : " + mPressure);
            Logger.d(LOG_TAG, "readBmp180Data mAltitde : " + mAltitde);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    private void switchRelay() {
        Logger.i(LOG_TAG, "switchRelay");
        try {
            mRelayGPIO.setValue(!mRelayGPIO.getValue());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeGPIOPins() {
        Logger.i(LOG_TAG, "closeGPIOPins");
        try {
            if (mLedRedGPIO != null) mLedRedGPIO.close();
            if (mLedGreenGPIO != null) mLedGreenGPIO.close();
            if (mLedBlueGPIO != null) mLedBlueGPIO.close();
            if (mRelayGPIO != null) mRelayGPIO.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeSensors() {
        Logger.i(LOG_TAG, "closeSensors");
        if (mBmp180 != null) {
            try {
                mBmp180.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mBmp180 = null;
            }
        }

        if (mBmp180SensorDriver != null) {
            mSensorManager.unregisterDynamicSensorCallback(mDynamicSensorCallback);
            mSensorManager.unregisterListener(mSensorEventListener);
            mBmp180SensorDriver.unregisterTemperatureSensor();
            mBmp180SensorDriver.unregisterPressureSensor();

            try {
                mBmp180SensorDriver.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mBmp180SensorDriver = null;
            }
        }
    }

    private void sendFirebaseMessage(String message) {
        Logger.i(LOG_TAG, "sendFirebaseMessage");
        Logger.d(LOG_TAG, "sendFirebaseMessage message : " + message);
        FirebaseMessaging fm = FirebaseMessaging.getInstance();
        fm.send(new RemoteMessage.Builder("androidthings-4aca2@gcm.googleapis.com")
                .addData("message", message)
                .build());
    }

    private void addBMP180ToFirebase(long currentTime, Map<String, Object> data) {
        Logger.i(LOG_TAG, "addBMP180ToFirebase");
        Logger.d(LOG_TAG, "addBMP180ToFirebase data : " + data);
        String deviceId = mNetworkInfoView.getMACAddress();
        String itemId = String.valueOf(currentTime);
        Map<String, Object> deviceProfile = new HashMap<String, Object>();
        deviceProfile.put("platform", "Raspberry Pi 3");

        mDatabase.collection("devices").document(deviceId).set(deviceProfile).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Logger.i(LOG_TAG, "addBMP180ToFirebase onSuccess");
            }
        });
        mDatabase.collection("devices").document(deviceId).collection("sensor_bmp180").document(itemId).set(data)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Logger.i(LOG_TAG, "addBMP180ToFirebase onSuccess");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Logger.i(LOG_TAG, "addBMP180ToFirebase onFailure");
                    }
                });
    }

    private void addUserToFirebase(Map<String, Object> user) {
        Logger.i(LOG_TAG, "addUserToFirebase");
        Logger.d(LOG_TAG, "addUserToFirebase user : " + user);
        mDatabase.collection("users").add(user).addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
            @Override
            public void onSuccess(DocumentReference documentReference) {
                Logger.i(LOG_TAG, "addUserToFirebase onSuccess");
                Logger.d(LOG_TAG, "addUserToFirebase onSuccess documentReference.getId : " + documentReference.getId());
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Logger.i(LOG_TAG, "addUserToFirebase onFailure");
            }
        });
    }

    private void getUsersFromFirebase() {
        Logger.i(LOG_TAG, "getUsersFromFirebase");
        mDatabase.collection("users").get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                Logger.i(LOG_TAG, "getUsersFromFirebase onComplete");
                Logger.d(LOG_TAG, "getUsersFromFirebase onComplete task.isSuccessful : " + task.isSuccessful());
                if (task.isSuccessful()) {
                    for (DocumentSnapshot document : task.getResult()) {
                        String userId = document.getId();
                        String userName = document.getData().get("name").toString();
                        Logger.d(LOG_TAG, "getUsersFromFirebase onComplete userId : " + userId);
                        Logger.d(LOG_TAG, "getUsersFromFirebase onComplete userName : " + userName);
                    }
                }
            }
        });
    }

    private void onGetGPIOInfoButtonClick() {
        Logger.i(LOG_TAG, "onGetGPIOInfoButtonClick");
        Logger.d(LOG_TAG, "onGetGPIOInfoButtonClick mService.getGpioList : " + mService.getGpioList());
        Logger.d(LOG_TAG, "onGetGPIOInfoButtonClick mService.getI2cBusList : " + mService.getI2cBusList());
//        Logger.d(LOG_TAG, "onGetGPIOInfoButtonClick mService.getI2sDeviceList : " + mService.getI2sDeviceList());
    }

    private void onLedButtonClick(int index) {
        Logger.i(LOG_TAG, "onLedButtonClick");
        try {
            switch (index) {
                case 1:
                    mLedRedGPIO.setValue(!mLedRedGPIO.getValue());
                    break;
                case 2:
                    mLedGreenGPIO.setValue(!mLedGreenGPIO.getValue());
                    break;
                case 3:
                    mLedBlueGPIO.setValue(!mLedBlueGPIO.getValue());
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Logger.i(LOG_TAG, "onReceive");
            Logger.d(LOG_TAG, "onReceive intent : " + intent);
            String action = intent.getAction();
            Logger.d(LOG_TAG, "onReceive action : " + action);

            if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {

            } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {

            } else if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Logger.d(LOG_TAG, "onReceive device.getName : " + device.getName());
                Logger.d(LOG_TAG, "onReceive device.getAddress : " + device.getAddress());
            }
        }
    };

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.main_android_settings_button:
                    Intent intent = new Intent(Settings.ACTION_SETTINGS);
                    startActivity(intent);
                    break;
                case R.id.main_get_gpio_info_button:
                    onGetGPIOInfoButtonClick();
                    break;
                case R.id.main_led_red_button:
                    onLedButtonClick(1);
                    break;
                case R.id.main_led_green_button:
                    onLedButtonClick(2);
                    break;
                case R.id.main_led_blue_button:
                    onLedButtonClick(3);
                    break;
            }
        }
    };

    private SensorManager.DynamicSensorCallback mDynamicSensorCallback = new SensorManager.DynamicSensorCallback() {
        @Override
        public void onDynamicSensorConnected(Sensor sensor) {
            super.onDynamicSensorConnected(sensor);
            Logger.i(LOG_TAG, "initSensors onDynamicSensorConnected");
            if (sensor.getType() == Sensor.TYPE_DEVICE_PRIVATE_BASE) {
                if (sensor.getStringType().equalsIgnoreCase(Bmp180SensorDriver.BAROMETER_SENSOR)) {
                    Logger.i(LOG_TAG, "Barometer sensor connected");
                    mSensorManager.registerListener(mSensorEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
                }
            }
        }

        @Override
        public void onDynamicSensorDisconnected(Sensor sensor) {
            super.onDynamicSensorDisconnected(sensor);
            Logger.i(LOG_TAG, "initSensors onDynamicSensorDisconnected");
        }
    };

    private SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            Logger.i(LOG_TAG, "SensorEventListener onSensorChanged");
            float data[] = Arrays.copyOf(event.values, 3);
            String strReadData = String.format("Temperature : %s Pressure : %s Altitude : %s",
                    data[1], data[0], (Math.round(data[2] * 10) / 10.0F));
            mBmp180InfoTextView.setText(strReadData);
            Logger.d(LOG_TAG, "SensorEventListener onSensorChanged Pressure: " + data[0]);
            Logger.d(LOG_TAG, "SensorEventListener onSensorChanged Temperature: " + data[1]);
            Logger.d(LOG_TAG, "SensorEventListener onSensorChanged Altitude: " + (Math.round(data[2] * 10) / 10.0F));
            onLedButtonClick(1);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Logger.i(LOG_TAG, "SensorEventListener onAccuracyChanged");
            Logger.d(LOG_TAG, "SensorEventListener onAccuracyChanged accuracy : " + accuracy);
        }
    };

    private GpioCallback mGPIOCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            Logger.i(LOG_TAG, "SensorEventListener onGpioEdge");
            return false;
        }

        @Override
        public void onGpioError(Gpio gpio, int error) {
            Logger.i(LOG_TAG, "GpioCallback onGpioEdge");
        }
    };
}
