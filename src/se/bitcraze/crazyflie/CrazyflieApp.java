package se.bitcraze.crazyflie;

import java.io.IOException;

import se.bitcraze.crazyfliecontrol.MainActivity;
import se.bitcraze.crazyfliecontrol.PreferencesActivity;
import se.bitcraze.crazyfliecontrol.R;
import se.bitcraze.crazyfliecontrollers.Controller;
import se.bitcraze.crazyflielib.CrazyradioLink;
import se.bitcraze.crazyflielib.crtp.CommanderPacket;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class CrazyflieApp extends Application {
	private static final String TAG = "Crazyflie.APP";    

	Context context;
	private CrazyradioLink crazyradioLink;
	private SharedPreferences preferences;
	private Thread sendControlDataThread;
	private Controller controller;
	private boolean xmode = false;
	private boolean hover = false;
	
	@Override
	public void onCreate() {
		super.onCreate();		
		context = getApplicationContext();
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        // Initialize preferences
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        
        // create link  
    	crazyradioLink = new CrazyradioLink();
    	        
	}

	public void addConnectionListener(MainActivity mainActivity) {
		crazyradioLink.addConnectionListener(mainActivity);
	}
	
	public void removeConnectionListener(MainActivity mainActivity) {
		crazyradioLink.removeConnectionListener(mainActivity);
	}
	
	@Override
	public void onTerminate() {
		crazyradioLink.disconnect();
	}
	
	public void crazyradioDetached() {
		crazyradioLink.disconnect();
	}
	
	public void crazyradioAttached() {
		Log.d(TAG,"Attached the usb");		
	}

	public void setController(Controller c, boolean x){
		controller = c;
		xmode = x;
	}
	public void linkConnect() {
		// ensure previous link is disconnected
        linkDisconnect();
        try {       
        	
            int radioChannel = Integer.parseInt(preferences.getString(PreferencesActivity.KEY_PREF_RADIO_CHANNEL, getString(R.string.preferences_radio_channel_defaultValue)));
            int radioDatarate = Integer.parseInt(preferences.getString(PreferencesActivity.KEY_PREF_RADIO_DATARATE, getString(R.string.preferences_radio_datarate_defaultValue)));        	
        	
            // connect and start thread to periodically send commands containing
            // the user input
            crazyradioLink.connect(context, new CrazyradioLink.ConnectionData(radioChannel, radioDatarate));
            sendControlDataThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (crazyradioLink.isConnected()) {
                    	float thrust;
                    	if(!hover) {
                    		thrust = controller.getThrust();
                    	} else {
                    		thrust = 32767;
                    	}
                    	crazyradioLink.send(new CommanderPacket(controller.getRoll(), controller.getPitch(), controller.getYaw(), (char) thrust, xmode));                        
                        try {
                            Thread.sleep(20, 0);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
            });
            sendControlDataThread.start();
            
        } catch (IllegalArgumentException e) {
            Log.d(TAG, e.getMessage());
            Toast.makeText(this, "Crazyradio not attached", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }		
	}
	
	public void linkDisconnect() {        
		crazyradioLink.disconnect();
	}
	
	public SharedPreferences getPreferences(){
		return preferences;
	}

	public boolean isConnected() {
		return crazyradioLink.isConnected();		
	}

	public void setHoverMode(boolean isChecked) {
		crazyradioLink.param.setHoverMode(isChecked);
		hover = isChecked;
	}	
}
