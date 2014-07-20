/**
 *    ||          ____  _ __                           
 * +------+      / __ )(_) /_______________ _____  ___ 
 * | 0xBC |     / __  / / __/ ___/ ___/ __ `/_  / / _ \
 * +------+    / /_/ / / /_/ /__/ /  / /_/ / / /_/  __/
 *  ||  ||    /_____/_/\__/\___/_/   \__,_/ /___/\___/
 *
 * Copyright (C) 2013 Bitcraze AB
 *
 * Crazyflie Nano Quadcopter Client
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */

package se.bitcraze.crazyfliecontrol;

import java.util.Locale;

import se.bitcraze.crazyflie.CrazyflieApp;
import se.bitcraze.crazyfliecontrol.SelectConnectionDialogFragment.SelectCrazyflieDialogListener;
import se.bitcraze.crazyfliecontrollers.*;
import se.bitcraze.crazyflielib.ConnectionListener;
import se.bitcraze.crazyflielib.CrazyradioLink;
import se.bitcraze.crazyflielib.CrazyradioLink.ConnectionData;
import se.bitcraze.crazyflielib.Link;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.Toast;
import android.hardware.SensorManager;

import com.MobileAnarchy.Android.Widgets.Joystick.DualJoystickView;

public class MainActivity extends Activity implements FlyingDataEvent, ConnectionListener {
	private static final String TAG = "Crazyflie.Activity";    

    private FlightDataView mFlightDataView;
    private boolean mDoubleBackToExitPressedOnce = false;    
    private String[] datarateStrings;
    private Controller controller;
    private Controls mControls;    
    protected CrazyflieApp crazyflieApp;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);        
        crazyflieApp = (CrazyflieApp) getApplication();        
        
        datarateStrings = getResources().getStringArray(R.array.radioDatarateEntries);
        mControls = new Controls(this, crazyflieApp.getPreferences());
        mControls.setDefaultPreferenceValues(getResources());

        
        mFlightDataView = (FlightDataView) findViewById(R.id.flightdataview);

    }


    private void checkScreenLock() {
        boolean isScreenLock = crazyflieApp.getPreferences().getBoolean(PreferencesActivity.KEY_PREF_SCREEN_ROTATION_LOCK_BOOL, false);
        if(isScreenLock){
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }else{
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                try {
                	crazyflieApp.linkConnect();                	
                } catch (IllegalStateException e) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.menu_disconnect:
            	crazyflieApp.linkDisconnect();
                break;
            case R.id.menu_radio_scan:
                radioScan();
                break;
            case R.id.preferences:
                Intent intent = new Intent(this, PreferencesActivity.class);
                startActivity(intent);
                break;
        }
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        crazyflieApp.addConnectionListener(this);
        mControls.setControlConfig();        
        Log.d("Chopter: ","in on resume and the mod is"+Integer.toString(mControls.getMode()));
        switch(mControls.getMode()){
            case(0):
                controller = new TouchJoystick1(mControls, (DualJoystickView) findViewById(R.id.joysticks));
                break;
            case(1):
                controller = new TouchJoystick2(mControls, (DualJoystickView) findViewById(R.id.joysticks));
                break;
            case(2):
                controller = new TouchJoystick3(mControls, (DualJoystickView) findViewById(R.id.joysticks));
                break;
            case(3):
                controller = new TouchJoystick4(mControls, (DualJoystickView) findViewById(R.id.joysticks));
                break;
            case(4):
                controller = new Joystick(mControls);
                break;
            case(5):
                controller = new Gyroscope(mControls,  (SensorManager) getSystemService(Context.SENSOR_SERVICE), (DualJoystickView) findViewById(R.id.joysticks));
                break;
        }

        controller.setOnFlyingDataListener(this);        
        crazyflieApp.setController(controller, mControls.getXmode());
        checkScreenLock();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        crazyflieApp.linkDisconnect();    
        crazyflieApp.removeConnectionListener(this);
        controller.disable();
    }
    
    @Override
    public void onBackPressed() {
        if (mDoubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }
        this.mDoubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                mDoubleBackToExitPressedOnce = false;

            }
        }, 2000);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        // Check that the event came from a joystick since a generic motion event could be almost anything.
        if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0 && event.getAction() == MotionEvent.ACTION_MOVE && mControls.getMode() == 4) {
        	((Joystick) controller).dealWithMotionEvent(event);
            return true;
        } else {
            return super.dispatchGenericMotionEvent(event);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // TODO: works for PS3 controller, but does it also work for other controllers?
        // do not call super if key event comes from a gamepad, otherwise the buttons can quit the app
        if (event.getSource() == 1281) {
            mControls.dealWithKeyEvent(event);
            // exception for OUYA controllers
            if (!Build.MODEL.toUpperCase(Locale.getDefault()).contains("OUYA")) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void setRadioChannelAndDatarate(int channel, int datarate) {
        if (channel != -1 && datarate != -1) {
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
            editor.putString(PreferencesActivity.KEY_PREF_RADIO_CHANNEL, String.valueOf(channel));
            editor.putString(PreferencesActivity.KEY_PREF_RADIO_DATARATE, String.valueOf(datarate));
            editor.commit();

            Toast.makeText(this,"Channel: " + channel + " Data rate: " + datarateStrings[datarate] + "\nSetting preferences...", Toast.LENGTH_SHORT).show();
        }
    }   
    
    private void radioScan() {
        new AsyncTask<Void, Void, ConnectionData[]>() {

            private Exception mException = null;
            private ProgressDialog mProgress;
            
            @Override
            protected void onPreExecute() {
                mProgress = ProgressDialog.show(MainActivity.this, "Radio Scan", "Searching for the Crazyflie...", true, false);
            }

            @Override
            protected ConnectionData[] doInBackground(Void... arg0) {
                try {
                    return CrazyradioLink.scanChannels(MainActivity.this);
                } catch(IllegalStateException e) {
                    mException = e;
                    return null;
                }
            }

            @Override
            protected void onPostExecute(ConnectionData[] result) {
                mProgress.dismiss();
                
                if(mException != null) {
                    Toast.makeText(MainActivity.this, mException.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    
                    if (result != null && result.length > 0) {
                        if(result.length > 1){
                            // let user choose connection, if there is more than one Crazyflie 
                            showSelectConnectionDialog(result);
                        }else{
                            // use first channel
                            setRadioChannelAndDatarate(result[0].getChannel(), result[0].getDataRate());
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "No connection found", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }.execute();
    }

    private void showSelectConnectionDialog(final ConnectionData[] result) {
        SelectConnectionDialogFragment selectConnectionDialogFragment = new SelectConnectionDialogFragment();
        //supply list of Crazyflie connections as arguments
        Bundle args = new Bundle();
        String[] crazyflieArray = new String[result.length];
        for(int i = 0; i < result.length; i++){
            crazyflieArray[i] = i + ": Channel " + result[i].getChannel() + ", Data rate " + datarateStrings[result[i].getDataRate()];
        }
        args.putStringArray("connection_array", crazyflieArray);
        selectConnectionDialogFragment.setArguments(args);
        selectConnectionDialogFragment.setListener(new SelectCrazyflieDialogListener(){
            @Override
            public void onClick(int which) {
                setRadioChannelAndDatarate(result[which].getChannel(), result[which].getDataRate());
            }
        });
        selectConnectionDialogFragment.show(getFragmentManager(), "select_crazyflie");
    }


	@Override
	public void flyingDataEvent(float pitch, float roll, float thrust, float yaw) {		
		mFlightDataView.updateFlightData(pitch, roll, thrust, yaw);
	}

	//ConnectionListener
    @Override
    public void connectionSetupFinished(final Link l) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            	Toast.makeText(getApplicationContext(), "Connection Setup finished", Toast.LENGTH_SHORT).show();            
            }
        });
        controller.enable();
    }

    @Override
    public void connectionLost(Link l) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "Connection lost", Toast.LENGTH_SHORT).show();
            }
        });
        controller.disable();
    }

    @Override
    public void connectionFailed(Link l) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "Connection failed", Toast.LENGTH_SHORT).show();
            }
        });
        controller.disable();
    }

    @Override
    public void linkQualityUpdate(Link l, final int quality) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mFlightDataView.setLinkQualityText(quality + "%");
            }
        });
    }

	@Override
	public void connectionInitiated(Link l) {
	
	}

	@Override
	public void disconnected(Link l) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // link quality is not available when there is no active connection
                mFlightDataView.setLinkQualityText("n/a");
            }
        });
        controller.disable();	
	}
}
